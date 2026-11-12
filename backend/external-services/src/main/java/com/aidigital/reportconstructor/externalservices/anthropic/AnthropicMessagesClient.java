package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.externalservices.common.http.PooledRestClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

/**
 * Low-level Anthropic Messages API transport and JSON response parsing for Claude batches.
 */
@Slf4j
@Component
@ConditionalOnExpression("'${external.anthropic.api-key:}' != ''")
public class AnthropicMessagesClient implements AnthropicClient {

	/** Low-cardinality client name used for metrics and pooled-client diagnostics. */
	static final String CLIENT_NAME = "anthropic";
	static final String API_BASE = "https://api.anthropic.com";
	static final String MESSAGES_PATH = "/v1/messages";
	static final String API_KEY_HEADER = "x-api-key";
	static final String VERSION_HEADER = "anthropic-version";
	private static final String VERSION = "2023-06-01";
	private static final int HTTP_OK = 200;
	private static final Pattern FENCE_OPEN = Pattern.compile("^```(?:json)?\\s*", Pattern.CASE_INSENSITIVE);
	private static final Pattern FENCE_CLOSE = Pattern.compile("\\s*```$");

	/** Floor on the configured reply snippet length, so a misconfigured value still logs something usable. */
	private static final int MIN_REPLY_SNIPPET_LIMIT = 80;

	/** Floor on the configured output-token headroom: a multiplier below 1.0 would shrink callers' budgets. */
	private static final double MIN_OUTPUT_TOKEN_HEADROOM = 1.0;

	/**
	 * Thinking configuration sent with every request. Sonnet 5 runs adaptive thinking whenever the field is
	 * omitted, and {@code max_tokens} caps thinking plus reply text together — so on the tight reply budgets
	 * these prompts use, leaving it out would spend the budget on reasoning and truncate the JSON. Every
	 * prompt here asks for one short schema-exact object, which is not work that benefits from thinking, so
	 * it is turned off explicitly. Accepted only at effort {@code high} or below, which is the default.
	 */
	private static final Map<String, Object> THINKING_DISABLED = Map.of("type", "disabled");

	/**
	 * HTTP statuses treated as transient and retried: 408 request timeout, 429 rate limit, 500/502/503/504
	 * server and gateway errors, Cloudflare edge timeouts 522/524, and 529 (Anthropic "overloaded"). Any other
	 * non-200 — a 400 bad request, a 401/403 auth failure — is permanent and fails fast without a retry.
	 */
	private static final Set<Integer> TRANSIENT_STATUS_CODES = Set.of(408, 429, 500, 502, 503, 504, 522, 524, 529);

	/**
	 * Marker a prompt builder may embed to split the prompt into a cacheable prefix — everything before the
	 * marker — and a variable body — everything after. {@link #callRaw} turns the two sides into separate
	 * content blocks and puts an ephemeral {@code cache_control} breakpoint on the prefix, so a large
	 * instruction preamble that repeats across chunked per-tactic calls is billed once at cache-write rates
	 * and re-read at roughly a tenth of the price on the following calls (Anthropic prompt caching is a
	 * prefix match, so only byte-identical prefixes hit). A NUL-delimited token keeps the marker from ever
	 * colliding with real prompt text, and it is stripped before the request is sent so the model never sees
	 * it. When a prompt carries no marker it is sent as one uncached block, exactly as before.
	 */
	static final String CACHE_BREAKPOINT = "\u0000CACHE_BREAKPOINT\u0000";

	private final String apiKey;
	private final String model;
	/** Builds the pooled HTTP clients; see {@link #restClientFor(int)}. */
	private final PooledRestClientFactory httpClients;

	/** Pooled client per per-call response timeout in seconds. */
	private final ConcurrentMap<Integer, RestClient> clientsByTimeout = new ConcurrentHashMap<>();

	/**
	 * Reads replies leniently about raw control characters: the model sometimes breaks a long string with a
	 * literal newline instead of {@code \n}, which strict JSON rejects even though the meaning is unambiguous.
	 */
	private final ObjectMapper json = JsonMapper.builder()
			.enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
			.build();
	/** Receives every call's token usage and why a reply was rejected (the run's accounting and result card). */
	private final AnthropicCallListener listener;
	private final PromptTokenEstimator tokenEstimator;

	/** Extra attempts after the first send when a transient upstream failure is retryable; at least 0. */
	private final int maxRetries;

	/** Base linear backoff between retries in milliseconds, scaled by attempt number; at least 0. */
	private final long retryBackoffMillis;

	/**
	 * Multiplier applied to every caller's {@code max_tokens} before the request is sent, compensating for
	 * Sonnet 5 tokenizing the same reply into more tokens than the budgets in this integration were tuned
	 * against; at least {@link #MIN_OUTPUT_TOKEN_HEADROOM}.
	 */
	private final double outputTokenHeadroom;

	/**
	 * Characters of an unparseable reply written to its WARN line; at least {@link #MIN_REPLY_SNIPPET_LIMIT}.
	 * Configurable because the default head of a reply often stops short of the defect that broke the parse.
	 */
	private final int replySnippetLimit;

	/**
	 * Caps Claude HTTP calls in flight at once across the whole process. The restructured slides-from-sheet
	 * flow fans out many small per-tactic calls in parallel, so every send acquires a permit here first and
	 * releases it when the call returns, keeping concurrency inside the account rate limit.
	 */
	private final Semaphore callLimiter;

	/**
	 * Creates the client, capturing the configured API key and target Claude model. HTTP clients come from
	 * the shared pooled factory, one per distinct per-call response timeout, created on first use.
	 *
	 * @param httpClients  shared factory building pooled, instrumented and logged HTTP clients
	 * @param props        Anthropic configuration supplying the API key and model identifier
	 * @param listener       receives every call's token usage and the reasons replies were rejected
	 * @param tokenEstimator local prompt-size estimate, used to book a call whose reply never arrived
	 */
	public AnthropicMessagesClient(
			PooledRestClientFactory httpClients,
			AnthropicProperties props,
			AnthropicCallListener listener,
			PromptTokenEstimator tokenEstimator) {
		this.apiKey = props.getApiKey();
		this.model = props.getModel();
		this.listener = listener;
		this.tokenEstimator = tokenEstimator;
		this.httpClients = httpClients;
		this.callLimiter = new Semaphore(Math.max(1, props.getMaxConcurrentCalls()));
		this.maxRetries = Math.max(0, props.getMaxRetries());
		this.retryBackoffMillis = Math.max(0, props.getRetryBackoffMillis());
		this.outputTokenHeadroom =
				Math.max(MIN_OUTPUT_TOKEN_HEADROOM, props.getOutputTokenHeadroom());
		this.replySnippetLimit = Math.max(MIN_REPLY_SNIPPET_LIMIT, props.getReplySnippetLimit());
	}

	/**
	 * Sends a prompt and parses the model's JSON object from the text response, stripping any
	 * Markdown code fences and optionally attempting a best-effort repair of truncated output.
	 *
	 * @param prompt       the full user prompt sent as the single message to Claude
	 * @param maxTokens    cap on tokens the model may generate in its reply
	 * @param timeoutSec   per-request HTTP timeout in seconds
	 * @param label        short tag identifying this call in log messages
	 * @param allowPartial when {@code true}, accepts {@code max_tokens}-truncated output and tries to
	 *                     repair the trailing JSON by closing open braces
	 * @return parsed object node, or {@code null} on failure
	 */
	@Override
	public JsonNode callJsonObject(
			String prompt, int maxTokens, int timeoutSec, String label, boolean allowPartial) {
		JsonNode resp = callRaw(prompt, maxTokens, timeoutSec, label);
		if (resp == null) {
			return null;
		}
		boolean truncated = "max_tokens".equals(resp.path("stop_reason").asText(""));
		if (truncated && !allowPartial) {
			log.warn("[claude:{}] truncated by max_tokens", label);
			return null;
		}
		if (truncated) {
			noteSalvagedTruncation(label, effectiveMaxTokens(maxTokens));
		}
		String text = replyText(resp);
		if (text == null || text.isBlank()) {
			return null;
		}
		text = FENCE_OPEN.matcher(text.trim()).replaceFirst("");
		text = FENCE_CLOSE.matcher(text).replaceFirst("").trim();
		JsonNode node = parseJsonObject(text, allowPartial);
		if (node != null) {
			return node;
		}
		// The reply carried no object we can use — a prose preamble with no JSON, a refusal, or an essay
		// that ran to max_tokens. Logging the head of it turns "JSON parse failed" from a dead end into a
		// diagnosable line without dumping the whole (often large) reply.
		logUnparseableReply(label, "object", text);
		return null;
	}

	/**
	 * Sends a prompt expecting a bare top-level JSON array reply and returns the parsed array node, or
	 * {@code null} on any failure. The per-section pilot calls ask for exactly this — a keyless array of a
	 * fixed length — so there is no object key the model can drift on: the reply is either a usable array or
	 * discarded. The caller still enforces the item count on what comes back, so a repaired array that lost its
	 * unfinished last item is rejected there and retried rather than shipped short.
	 *
	 * @param prompt       the full user prompt sent as the single message to Claude
	 * @param maxTokens    cap on tokens the model may generate in its reply
	 * @param timeoutSec   per-request HTTP timeout in seconds
	 * @param label        short tag identifying this call in log messages
	 * @param allowPartial when {@code true}, accepts {@code max_tokens}-truncated output and tries to repair the
	 *                     trailing JSON by closing open brackets — the same salvage the object path has always
	 *                     had, and the only way a reply that merely forgot its final {@code ]} survives
	 * @return the parsed array node, or {@code null} when no usable array could be recovered
	 */
	@Override
	public JsonNode callJsonArray(
			String prompt, int maxTokens, int timeoutSec, String label, boolean allowPartial) {
		JsonNode resp = callRaw(prompt, maxTokens, timeoutSec, label);
		if (resp == null) {
			return null;
		}
		boolean truncated = "max_tokens".equals(resp.path("stop_reason").asText(""));
		if (truncated && !allowPartial) {
			log.warn("[claude:{}] truncated by max_tokens", label);
			return null;
		}
		if (truncated) {
			noteSalvagedTruncation(label, effectiveMaxTokens(maxTokens));
		}
		String text = replyText(resp);
		if (text == null || text.isBlank()) {
			return null;
		}
		text = FENCE_OPEN.matcher(text.trim()).replaceFirst("");
		text = FENCE_CLOSE.matcher(text).replaceFirst("").trim();
		JsonNode node = parseJsonArray(text, allowPartial);
		if (node != null) {
			return node;
		}
		logUnparseableReply(label, "array", text);
		return null;
	}

	/**
	 * Parses the model's JSON array out of the reply text, tolerating the same ways a reply can carry usable
	 * items that a plain parse still rejects as {@link #parseJsonObject} does: a tail the model never closed —
	 * a run into {@code max_tokens}, or the missing final <code>]</code> of an accidentally nested
	 * <code>[[…]]</code> reply — repaired by {@link #repairTruncatedJson}, and prose wrapped around the array,
	 * salvaged by parsing from the first <code>[</code> to the last <code>]</code>. A reply that carries no
	 * array yields {@code null}.
	 *
	 * @param text         the reply text, with any Markdown code fences already stripped
	 * @param allowPartial whether to attempt truncation repair as well as a clean parse
	 * @return the parsed array node, or {@code null} when no usable array could be recovered
	 */
	JsonNode parseJsonArray(String text, boolean allowPartial) {
		JsonNode node = readArray(text);
		if (node != null) {
			return node;
		}
		if (allowPartial) {
			node = readArray(repairTruncatedJson(text));
			if (node != null) {
				return node;
			}
		}
		int first = text.indexOf('[');
		if (first < 0) {
			return null;
		}
		String fromFirst = text.substring(first);
		int last = fromFirst.lastIndexOf(']');
		if (last > 0) {
			node = readArray(fromFirst.substring(0, last + 1));
			if (node != null) {
				return node;
			}
		}
		return allowPartial ? readArray(repairTruncatedJson(fromFirst)) : null;
	}

	/**
	 * Reads one JSON array from text, returning {@code null} rather than throwing when the text is blank,
	 * unparseable, or parses to something that is not an array.
	 *
	 * @param text candidate JSON text
	 * @return the array node, or {@code null}
	 */
	JsonNode readArray(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		try {
			JsonNode node = json.readTree(text);
			return node != null && node.isArray() ? node : null;
		} catch (Exception ignored) {
			return null;
		}
	}

	/**
	 * Parses the model's JSON object out of the reply text, tolerating the ways a reply can carry a complete
	 * object that a plain parse still rejects: a tail truncated by {@code max_tokens} (repaired by
	 * {@link #repairTruncatedJson}) and prose wrapped around the object — a preamble such as "Here are the
	 * observations:" or trailing commentary — salvaged by parsing from the first <code>{</code>. A reply that
	 * is not an object at all (a bare array, or free text with no object) yields {@code null}.
	 *
	 * @param text         the reply text, with any Markdown code fences already stripped
	 * @param allowPartial whether to attempt truncation repair as well as a clean parse
	 * @return the parsed object node, or {@code null} when no usable object could be recovered
	 */
	JsonNode parseJsonObject(String text, boolean allowPartial) {
		JsonNode node = readObject(text);
		if (node != null) {
			return node;
		}
		if (allowPartial) {
			node = readObject(repairTruncatedJson(text));
			if (node != null) {
				return node;
			}
		}
		int first = text.indexOf('{');
		if (first < 0) {
			return null;
		}
		String fromFirst = text.substring(first);
		int last = fromFirst.lastIndexOf('}');
		if (last >= 0) {
			node = readObject(fromFirst.substring(0, last + 1));
			if (node != null) {
				return node;
			}
			// Last resort for a complete object: a quotation inside the prose ("the "Netflix" line") that the
			// model did not escape ends the string early and breaks the whole reply.
			node = readObject(escapeStrayQuotes(fromFirst.substring(0, last + 1)));
			if (node != null) {
				return node;
			}
		}
		return allowPartial ? readObject(repairTruncatedJson(fromFirst)) : null;
	}

	/**
	 * Escapes the double quotes inside JSON string values that the model forgot to escape. A quote inside a
	 * string is taken to close it only when the next non-blank character is one that can follow a string in
	 * JSON ({@code , : } ]}) or the text ends; any other quote is a quotation mark in the prose and is escaped.
	 * Already-escaped characters are copied as they are.
	 *
	 * @param text the candidate JSON text
	 * @return the text with stray inner quotes escaped; unchanged when it had none
	 */
	String escapeStrayQuotes(String text) {
		StringBuilder out = new StringBuilder(text.length() + 16);
		boolean inString = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (inString && c == '\\' && i + 1 < text.length()) {
				out.append(c).append(text.charAt(++i));
				continue;
			}
			if (c != '"') {
				out.append(c);
				continue;
			}
			if (!inString) {
				inString = true;
				out.append(c);
				continue;
			}
			if (closesString(text, i)) {
				inString = false;
				out.append(c);
			} else {
				out.append("\\\"");
			}
		}
		return out.toString();
	}

	/**
	 * Tells whether the quote at {@code quote}, met inside a string, ends that string. It does when the text ends
	 * after it or the next non-blank character is {@code :}, {@code \}} or {@code ]}. A comma is ambiguous: prose
	 * often quotes a name right before one ({@code Scale "LAL", the top audience}), so a comma only ends the string
	 * when what follows it can start the next entry: a quote, a closing bracket, or the end of the text.
	 *
	 * @param text  the candidate JSON text
	 * @param quote index of a {@code "} met inside a string
	 * @return {@code true} when the quote closes the string, {@code false} when it is a quotation mark in the prose
	 */
	boolean closesString(String text, int quote) {
		int next = skipBlanks(text, quote + 1);
		if (next >= text.length() || ":}]".indexOf(text.charAt(next)) >= 0) {
			return true;
		}
		if (text.charAt(next) != ',') {
			return false;
		}
		int afterComma = skipBlanks(text, next + 1);
		return afterComma >= text.length() || "\"}]".indexOf(text.charAt(afterComma)) >= 0;
	}

	/**
	 * @param text  any text
	 * @param from  the index to start at
	 * @return the index of the first non-blank character at or after {@code from}, or the text's length
	 */
	int skipBlanks(String text, int from) {
		int i = from;
		while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
			i++;
		}
		return i;
	}

	/**
	 * Reads one JSON object from text, returning {@code null} rather than throwing when the text is blank,
	 * unparseable, or parses to something that is not an object.
	 *
	 * @param text candidate JSON text
	 * @return the object node, or {@code null}
	 */
	JsonNode readObject(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		try {
			JsonNode node = json.readTree(text);
			return node != null && node.isObject() ? node : null;
		} catch (Exception ignored) {
			return null;
		}
	}

	/**
	 * Shortens reply text to a single-line head for a log line: whitespace collapsed, capped at the configured
	 * {@code external.anthropic.reply-snippet-limit} characters with an ellipsis when longer.
	 *
	 * @param text the reply text
	 * @return the trimmed, length-capped snippet
	 */
	String snippet(String text) {
		String flat = text.replaceAll("\\s+", " ").trim();
		return flat.length() <= replySnippetLimit ? flat : flat.substring(0, replySnippetLimit) + "…";
	}

	/**
	 * Reports a reply no parse path could use. The WARN line carries the reply's head — long enough to name a
	 * cause, short enough to keep the log readable — and the whole reply goes to DEBUG, verbatim and unflattened,
	 * so the defect that broke the parse (a raw newline inside a string, a missing closing bracket, prose after
	 * the JSON) can be read off a deployed run by turning on DEBUG for this logger alone.
	 *
	 * @param label short tag identifying this call in log messages
	 * @param shape the expected top-level JSON shape, named in the message (e.g. {@code "object"})
	 * @param text  the reply text, with any Markdown code fences already stripped
	 */
	void logUnparseableReply(String label, String shape, String text) {
		log.warn("[claude:{}] JSON {} parse failed ({} chars); reply began: {}",
				label, shape, text.length(), snippet(text));
		log.debug("[claude:{}] full unparseable reply:\n{}", label, text);
		// The head of the reply is the one piece of evidence that says what actually broke the parse, and the
		// person whose report degraded cannot open the server log — so it goes on their result card too.
		listener.replyProblem(label, "reply was not valid JSON (" + shape + ", " + text.length()
				+ " chars); it began: " + snippet(text));
	}

	/**
	 * Records a reply the model stopped writing because it ran out of its output budget but which
	 * {@code allowPartial} salvaged anyway.
	 *
	 * <p>Worth its own line precisely because the salvage succeeds: the report still builds, so the only
	 * symptom is that the fields the reply never reached silently kept their earlier values — indistinguishable
	 * from Claude having decided they were already fine. Naming the budget it overran is what turns a puzzling
	 * half-applied pass into "raise this call's max tokens".
	 *
	 * @param label     batch tag the call was logged under
	 * @param maxTokens the output budget the reply ran out of
	 */
	void noteSalvagedTruncation(String label, int maxTokens) {
		log.warn("[claude:{}] reply ran out of its {}-token output budget; salvaged the part that arrived, so "
				+ "later fields kept their previous values", label, maxTokens);
		listener.replyProblem(label, "reply ran out of its " + maxTokens + "-token output budget; the fields it "
				+ "never reached kept their previous values");
	}

	/**
	 * Rebuilds a parseable JSON object out of a reply the model stopped writing mid-way: drops the trailing
	 * incomplete value and closes every structure still open, so the entries that did arrive survive instead
	 * of the whole reply being thrown away.
	 *
	 * <p>Walks the text once tracking string state (honouring backslash escapes, so a quote inside a value
	 * does not read as the string's end) and the stack of open {@code {}/[]}. Only a structural boundary —
	 * a comma or a closing bracket outside a string — counts as a cut point, because a closing quote alone
	 * may well be a key still waiting for its value ({@code {"a":1,"b"}} does not parse). The bracket stack
	 * is snapshotted at each cut point rather than read at the end of the text, since brackets opened after
	 * the cut are about to be discarded and must not be closed.
	 *
	 * @param text the raw, unparseable reply text with its code fences already stripped
	 * @return JSON text holding the reply's complete entries; empty when nothing survived the cut, which the
	 * caller's parse then rejects
	 */
	String repairTruncatedJson(String text) {
		Deque<Character> open = new ArrayDeque<>();
		Deque<Character> openAtCut = new ArrayDeque<>();
		boolean inString = false;
		boolean escaped = false;
		// Last index (exclusive) at which the document was structurally complete enough to close off.
		int cut = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (inString) {
				if (escaped) {
					escaped = false;
				} else if (c == '\\') {
					escaped = true;
				} else if (c == '"') {
					inString = false;
				}
				continue;
			}
			switch (c) {
				case '"' -> inString = true;
				case '{', '[' -> open.push(c);
				case '}', ']' -> {
					if (!open.isEmpty()) {
						open.pop();
					}
					cut = i + 1;
					openAtCut = new ArrayDeque<>(open);
				}
				case ',' -> {
					cut = i + 1;
					openAtCut = new ArrayDeque<>(open);
				}
				default -> {
					// ':', digits, literals and whitespace leave the cut point where it was.
				}
			}
		}
		StringBuilder repaired = new StringBuilder(text.substring(0, cut));
		while (!repaired.isEmpty()) {
			char last = repaired.charAt(repaired.length() - 1);
			if (last == ',' || Character.isWhitespace(last)) {
				repaired.setLength(repaired.length() - 1);
			} else {
				break;
			}
		}
		if (repaired.isEmpty()) {
			return "";
		}
		while (!openAtCut.isEmpty()) {
			repaired.append(openAtCut.pop() == '{' ? '}' : ']');
		}
		return repaired.toString();
	}

	/**
	 * Turns a prompt into the {@code content} value of the single user message, honouring an optional
	 * {@link #CACHE_BREAKPOINT}. Without the marker the whole prompt is one plain text block, identical to the
	 * previous behaviour. With it, the text before the marker becomes a cached block carrying an ephemeral
	 * {@code cache_control} breakpoint (so a preamble repeated across chunked calls is billed once and re-read
	 * cheaply) and the text after it becomes a second, uncached block. The marker itself is never sent to the
	 * model. A marker at position zero, or one whose prefix is blank, is treated as no marker so an all-cache
	 * or empty-prefix block is never requested.
	 *
	 * @param prompt the assembled prompt, optionally carrying one {@link #CACHE_BREAKPOINT}
	 * @return either the plain prompt string, or a two-element list of content blocks with a cache breakpoint
	 * on the first
	 */
	Object buildUserContent(String prompt) {
		int marker = prompt.indexOf(CACHE_BREAKPOINT);
		String cachedPrefix = marker > 0 ? prompt.substring(0, marker) : "";
		if (marker <= 0 || cachedPrefix.isBlank()) {
			return prompt.replace(CACHE_BREAKPOINT, "");
		}
		String variableBody = prompt.substring(marker + CACHE_BREAKPOINT.length()).replace(CACHE_BREAKPOINT, "");
		List<Map<String, Object>> blocks = new ArrayList<>(2);
		blocks.add(Map.of(
				"type", "text",
				"text", cachedPrefix,
				"cache_control", Map.of("type", "ephemeral")));
		if (!variableBody.isEmpty()) {
			blocks.add(Map.of("type", "text", "text", variableBody));
		}
		return blocks;
	}

	/**
	 * Builds the request's message list: the single user turn carrying the prompt.
	 *
	 * <p>A pre-filled assistant turn is deliberately never sent. Assistant-turn prefill is rejected with a 400
	 * by every current Claude model (Sonnet 4.6 included), so a prefilled request never reaches the model at
	 * all — the reply shape is fixed by the prompt's output rules instead.
	 *
	 * @param prompt the full user prompt, cache marker included
	 * @return the messages array in the shape the Messages API expects
	 */
	List<Map<String, Object>> buildMessages(String prompt) {
		List<Map<String, Object>> messages = new ArrayList<>(1);
		messages.add(Map.of("role", "user", "content", buildUserContent(prompt)));
		return messages;
	}

	/**
	 * Turns a caller's reply budget into the {@code max_tokens} actually sent, applying
	 * {@link #outputTokenHeadroom}. Exposed so the truncation warnings report the budget the reply really
	 * ran out of rather than the pre-headroom figure the caller asked for.
	 *
	 * @param maxTokens the caller's requested reply budget
	 * @return the budget sent to the API, never below the caller's figure
	 */
	int effectiveMaxTokens(int maxTokens) {
		return (int) Math.ceil(maxTokens * outputTokenHeadroom);
	}

	/**
	 * Sends a prompt as a single user message to the Anthropic Messages API and returns the raw
	 * parsed JSON response body, or {@code null} on a non-200 status or transport failure.
	 *
	 * @param prompt     the full user prompt sent as the single message to Claude
	 * @param maxTokens  cap on tokens the model may generate in its reply
	 * @param timeoutSec per-request HTTP timeout in seconds
	 * @param label      short tag identifying this call in log messages
	 * @return the full Messages API response as a JSON tree, or {@code null} on failure
	 */
	@Override
	public JsonNode callRaw(String prompt, int maxTokens, int timeoutSec, String label) {
		RestClient client;
		byte[] requestBody;
		try {
			// No sampling parameters: Sonnet 5 rejects temperature, top_p and top_k outright, and the
			// schema discipline they used to buy is carried by the prompts themselves, which state the
			// exact object and the hard character budget of every field.
			Map<String, Object> body = Map.of(
					"model", model,
					"max_tokens", effectiveMaxTokens(maxTokens),
					"thinking", THINKING_DISABLED,
					"messages", buildMessages(prompt)
			);
			requestBody = json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
			client = restClientFor(timeoutSec);
		} catch (Exception ex) {
			// Nothing left the process, so nothing was billed and nothing is recorded.
			log.error("[claude:{}] request could not be built: {}", label, ex.getMessage());
			return null;
		}
		// One permit per in-flight call caps how many requests hit Anthropic at once; a virtual thread parks
		// cheaply here while it waits, so blocking is fine. The permit is held across the whole retry sequence
		// (including its backoff) so a retrying call never lifts total concurrency above the configured cap.
		// Released in the finally so a permit is never leaked on a timeout or dropped connection.
		callLimiter.acquireUninterruptibly();
		try {
			return sendWithRetry(client, requestBody, prompt, label);
		} finally {
			callLimiter.release();
		}
	}

	/**
	 * Returns the pooled client for a per-call response timeout, creating it on first use. Callers use a
	 * handful of fixed timeouts (30, 60 and 120 s plus a few derived ones), so this stays a small bounded set,
	 * each with its own bounded pool from {@link PooledRestClientFactory}.
	 *
	 * @param timeoutSec per-request response timeout in seconds
	 * @return the client whose requests time out after {@code timeoutSec}
	 */
	RestClient restClientFor(int timeoutSec) {
		return clientsByTimeout.computeIfAbsent(timeoutSec,
				seconds -> httpClients.createClient(CLIENT_NAME, API_BASE, Duration.ofSeconds(seconds)));
	}

	/**
	 * Sends one Messages API request and returns its status and body without throwing on a non-2xx status,
	 * so {@link #sendWithRetry} keeps deciding between retry and fail-fast. Transport failures (connect or
	 * read timeout, dropped connection) propagate as exceptions.
	 *
	 * @param client      pooled client carrying this call's response timeout
	 * @param requestBody the serialized Messages API request body (UTF-8 JSON)
	 * @return the response status and body text
	 */
	ResponseEntity<String> exchange(RestClient client, byte[] requestBody) {
		return client.post()
				.uri(MESSAGES_PATH)
				.header(API_KEY_HEADER, apiKey)
				.header(VERSION_HEADER, VERSION)
				.contentType(MediaType.APPLICATION_JSON)
				.body(requestBody)
				.exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
						.body(StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8)));
	}

	/**
	 * Sends the prepared request, retrying transient upstream failures up to {@link #maxRetries} extra
	 * attempts with a linear backoff before giving up. A single transient failure — a retryable HTTP status
	 * such as a Cloudflare 522 or an Anthropic 529 overload, or a transport error like a dropped connection or
	 * read timeout — otherwise discards a whole batch and blanks its slide tokens, so it is worth a few cheap
	 * re-sends. A permanent non-200 (bad request, auth failure) fails fast without retrying, since it would
	 * only fail again.
	 *
	 * <p>Usage is booked exactly once. A billed 200 records real usage; a transport failure only books an
	 * estimate on the final attempt. A request that a non-final attempt sent but lost may still have been
	 * billed by Anthropic without being recorded here — the same understatement-on-failure the single-shot
	 * path already accepted, now confined to a call that ultimately succeeds or times out on its last try.
	 *
	 * @param client      pooled client carrying this call's response timeout
	 * @param requestBody the serialized Messages API request body (UTF-8 JSON)
	 * @param prompt the prompt text, used only to estimate spend when no reply arrives
	 * @param label  short tag identifying this call in log messages
	 * @return the parsed Messages API response, or {@code null} once the status is permanent or retries run out
	 */
	JsonNode sendWithRetry(RestClient client, byte[] requestBody, String prompt, String label) {
		int attempts = maxRetries + 1;
		for (int attempt = 1; attempt <= attempts; attempt++) {
			boolean lastAttempt = attempt == attempts;
			try {
				ResponseEntity<String> res = exchange(client, requestBody);
				int statusCode = res.getStatusCode().value();
				if (statusCode == HTTP_OK) {
					JsonNode parsed = json.readTree(res.getBody());
					recordUsage(parsed, label);
					return parsed;
				}
				// Rejected requests — rate limits, overloads, bad requests — are not billed, so they are
				// deliberately not recorded as spend. A permanent status, or the last attempt, ends here.
				if (lastAttempt || !isTransientStatus(statusCode)) {
					log.error("[claude:{}] error {} body={}", label, statusCode, res.getBody());
					return null;
				}
				log.warn("[claude:{}] transient error {} (attempt {}/{}) — retrying",
						label, statusCode, attempt, attempts);
			} catch (Exception ex) {
				// The request went out and Claude may well have answered it — a read timeout or a dropped
				// connection loses the reply, not the charge. On the final attempt the call is booked with an
				// estimate of what it cost and flagged as such rather than dropped.
				if (lastAttempt) {
					log.error("[claude:{}] request failed after sending: {}", label, ex.getMessage());
					listener.estimatedUsage(label, tokenEstimator.estimateTokens(prompt), model);
					return null;
				}
				log.warn("[claude:{}] request failed after sending (attempt {}/{}) — retrying: {}",
						label, attempt, attempts, ex.getMessage());
			}
			backoffBeforeRetry(attempt, label);
		}
		return null;
	}

	/**
	 * Reports whether a non-200 HTTP status is worth retrying — a transient upstream condition (request
	 * timeout, rate limit, server or gateway error, Cloudflare edge timeout, Anthropic overload) rather than a
	 * permanent client error such as a bad request or auth failure that would only fail again.
	 *
	 * @param statusCode the HTTP status returned by the Messages API
	 * @return {@code true} if the status is transient and the request should be retried
	 */
	boolean isTransientStatus(int statusCode) {
		return TRANSIENT_STATUS_CODES.contains(statusCode);
	}

	/**
	 * Sleeps for a linear backoff before the next retry: {@link #retryBackoffMillis} times the just-failed
	 * attempt number. Restores the interrupt flag and returns early if interrupted so a shutdown still
	 * propagates; a zero base delay skips the sleep entirely.
	 *
	 * @param attempt the 1-based number of the attempt that just failed
	 * @param label   short tag identifying this call in log messages
	 */
	void backoffBeforeRetry(int attempt, String label) {
		long delay = retryBackoffMillis * attempt;
		if (delay <= 0) {
			return;
		}
		try {
			Thread.sleep(delay);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			log.warn("[claude:{}] retry backoff interrupted", label);
		}
	}

	/**
	 * Adds a successful reply's {@code usage} block to the run's token accounting. Runs on every
	 * 200 response — including ones whose JSON later fails to parse, because those tokens were billed
	 * all the same and leaving them out would understate the cost of exactly the runs that went wrong.
	 * Accounting never breaks a request: a reply with no usage block, or an unexpected shape, is
	 * skipped silently.
	 *
	 * @param response the parsed Messages API response body
	 * @param label    short tag identifying this call in log messages
	 */
	void recordUsage(JsonNode response, String label) {
		JsonNode usage = response == null ? null : response.get("usage");
		if (usage == null || !usage.isObject()) {
			return;
		}
		long input = usage.path("input_tokens").asLong(0);
		long output = usage.path("output_tokens").asLong(0);
		long cacheWrite = usage.path("cache_creation_input_tokens").asLong(0);
		long cacheRead = usage.path("cache_read_input_tokens").asLong(0);
		listener.usage(label, input, output, cacheWrite, cacheRead, response.path("model").asText(model));
		log.debug("[claude:{}] usage in={} out={} cacheWrite={} cacheRead={}",
				label, input, output, cacheWrite, cacheRead);
	}

	@Override
	public String replyText(JsonNode response) {
		JsonNode content = response.path("content");
		if (!content.isArray() || content.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (JsonNode part : content) {
			if ("text".equals(part.path("type").asText())) {
				sb.append(part.path("text").asText());
			}
		}
		return sb.toString();
	}
}
