package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.usage.mappers.ClaudeUsageEventEntityMapperImpl;
import com.aidigital.reportconstructor.service.common.time.CurrentTimeImpl;
import com.aidigital.reportconstructor.externalservices.common.http.PooledRestClientFactory;
import com.aidigital.reportconstructor.service.reports.diagnostics.impl.ClaudeFailureLogImpl;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageEventService;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageTracker;
import com.aidigital.reportconstructor.service.reports.usage.impl.ClaudeUsageTrackerImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AnthropicMessagesClientTest {

	@Test
	void shouldKeepTheCompleteBulletsOfATruncatedPerTacticReplyTest() {
		// Given: a per-tactic bullet reply the model stopped writing part-way through the fourth bullet —
		// the shape that used to blank a whole breakdown slide
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());
		String truncated = "{\"tactic_1\": [\"First bullet.\", \"Second bullet.\", \"Third bullet.\", \"Fourth bul";

		// When:
		String repaired = client.repairTruncatedJson(truncated);

		// Then: the three finished bullets survive and the unfinished one is dropped
		assertThat(repaired)
				.isEqualTo("{\"tactic_1\": [\"First bullet.\", \"Second bullet.\", \"Third bullet.\"]}");
	}

	@Test
	void shouldParseAReplyWhoseProseQuotesAreNotEscapedTest() {
		// Given: a campaign reply that quotes a line name without escaping the quotes
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());
		String reply = "{\"thoughts_on_performance\": [\"The \"Netflix\" line never activated.\", \"Video beat plan.\"]}";

		// When:
		JsonNode parsed = client.parseJsonObject(reply, true);

		// Then: both thoughts survive, the quotation kept as text
		assertThat(parsed.get("thoughts_on_performance").get(0).asText())
				.isEqualTo("The \"Netflix\" line never activated.");
		assertThat(parsed.get("thoughts_on_performance").get(1).asText()).isEqualTo("Video beat plan.");
	}

	@Test
	void shouldParseAReplyWhoseUnescapedQuotationIsFollowedByACommaTest() {
		// Given: a paid narrative reply quoting an audience name right before a comma inside the prose
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());
		String reply = "{\"aud_take_1\": \"Scale \"Noventra Meta - LAL\", the top converter at $93.80.\", "
				+ "\"aud_take_2\": \"Retargeting held a $65.14 CPA.\"}";

		// When:
		JsonNode parsed = client.parseJsonObject(reply, true);

		// Then: the quotation stays text and the next field still parses
		assertThat(parsed.get("aud_take_1").asText())
				.isEqualTo("Scale \"Noventra Meta - LAL\", the top converter at $93.80.");
		assertThat(parsed.get("aud_take_2").asText()).isEqualTo("Retargeting held a $65.14 CPA.");
	}

	@Test
	void shouldParseAReplyWithARawLineBreakInsideAStringTest() {
		// Given: a thought the model wrapped with a literal newline instead of \n
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());
		String reply = "{\"results_overviews\": {\"1\": \"Six tactics delivered 4.47M impressions.\nNetflix never activated.\"}}";

		// When:
		JsonNode parsed = client.parseJsonObject(reply, true);

		// Then:
		assertThat(parsed.get("results_overviews").get("1").asText())
				.isEqualTo("Six tactics delivered 4.47M impressions.\nNetflix never activated.");
	}

	@Test
	void shouldNotCloseAKeyThatNeverGotItsValueTest() {
		// Given: a reply truncated right after a key — closing it as-is would produce {"a":1,"b"}, which
		// does not parse
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());

		// When:
		String repaired = client.repairTruncatedJson("{\"tactic_1\": [\"Done.\"], \"tactic_2\"");

		// Then: the dangling key is cut away with its comma, leaving the complete entry
		assertThat(repaired).isEqualTo("{\"tactic_1\": [\"Done.\"]}");
	}

	@Test
	void shouldCloseNestedStructuresOpenedAfterTheCutOnlyOnceTest() {
		// Given: a reply truncated inside a nested object, so the brackets open at the end of the text are
		// not the brackets open at the point the document is cut
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());

		// When:
		String repaired = client.repairTruncatedJson("{\"a\": [1,2], \"b\": {\"c\": 1");

		// Then: only the outer object is closed — the discarded inner one is not
		assertThat(repaired).isEqualTo("{\"a\": [1,2]}");
	}

	@Test
	void shouldTreatBracesInsideAStringAsTextTest() {
		// Given: a bullet quoting a placeholder token, whose braces must not be read as JSON structure
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());

		// When:
		String repaired = client.repairTruncatedJson("{\"tactic_1\": [\"Cell still reads {{cr_live_n}}.\", \"Sec");

		// Then: the finished bullet survives with its braces intact
		assertThat(repaired).isEqualTo("{\"tactic_1\": [\"Cell still reads {{cr_live_n}}.\"]}");
	}

	@Test
	void shouldReturnNothingWhenNoEntryEverCompletedTest() {
		// Given: a reply that was cut before its first entry closed — there is nothing to salvage
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());

		// When:
		String repaired = client.repairTruncatedJson("{\"tactic_1\": [\"Unfinished bul");

		// Then: the caller's parse is left to reject it rather than being handed a lie
		assertThat(repaired).isEmpty();
	}

	@Test
	void shouldRecoverAnObjectWrappedInProseTest() {
		// Given: a reply that put a preamble and trailing commentary around the object — the BatchPublishers
		// failure mode, where "Return ONLY a JSON object" was ignored and a plain parse rejected the whole
		// reply, blanking the slide and triggering the retry storm.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());
		String wrapped = "Here are the observations:\n\n{\"tactic_1\": [\"First.\", \"Second.\"]}\n\nThese show the mix.";

		// When:
		JsonNode node = client.parseJsonObject(wrapped, true);

		// Then: the object is salvaged from inside the prose.
		assertThat(node).isNotNull();
		assertThat(node.get("tactic_1").get(0).asText()).isEqualTo("First.");
	}

	@Test
	void shouldRecoverAProseWrappedObjectWhoseTailWasTruncatedTest() {
		// Given: a preamble, then an object the model stopped writing part-way through its last bullet.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());
		String wrapped = "Sure! {\"tactic_1\": [\"First.\", \"Second.\", \"Third.\", \"Fourth bul";

		// When:
		JsonNode node = client.parseJsonObject(wrapped, true);

		// Then: the three finished bullets survive.
		assertThat(node).isNotNull();
		assertThat(node.get("tactic_1")).hasSize(3);
	}

	@Test
	void shouldRejectABareArrayReplyTest() {
		// Given: a reply that is a JSON array rather than the keyed object the caller needs.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());

		// When-Then: a non-object never masquerades as a usable reply.
		assertThat(client.parseJsonObject("[\"a\", \"b\"]", true)).isNull();
	}

	@Test
	void shouldReturnNullForFreeTextWithNoObjectTest() {
		// Given: a reply with no JSON object at all — a refusal or a plain-prose answer.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());

		// When-Then:
		assertThat(client.parseJsonObject("I can't help with that request.", true)).isNull();
	}

	@Test
	void shouldFlattenAndCapTheReplySnippetTest() {
		// Given: a multi-line reply longer than the snippet cap.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());
		String reply = "line one\n\n   line two".repeat(60);

		// When:
		String snippet = client.snippet(reply);

		// Then: whitespace is collapsed to single spaces and the head is capped with an ellipsis.
		assertThat(snippet).doesNotContain("\n");
		assertThat(snippet).endsWith("…");
		assertThat(snippet.length()).isEqualTo(401);
	}

	@Test
	void shouldRecoverASectionArrayThatNeverGotItsClosingBracketTest() {
		// Given: the shape that blanked three breakdown sections on job 184 — a section reply nested one level
		// deep that closed the inner array and stopped, leaving the outer one open
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());
		String unclosed = "[[\"Modrinth anchored reach.\", \"Five destinations held 25%.\", "
				+ "\"Brand-safe gaming mix.\", \"We steered weight to strong publishers.\"]";

		// When:
		JsonNode parsed = client.parseJsonArray(unclosed, true);

		// Then: the bullets survive as the wrapper array the caller then unwraps, instead of the whole reply
		// being thrown away and re-sent
		assertThat(parsed).isNotNull();
		assertThat(parsed.isArray()).isTrue();
		assertThat(parsed.size()).isEqualTo(1);
		assertThat(parsed.get(0).size()).isEqualTo(4);
		assertThat(parsed.get(0).get(0).asText()).isEqualTo("Modrinth anchored reach.");
	}

	@Test
	void shouldKeepTheCompleteItemsOfAnArrayCutMidItemTest() {
		// Given: a section reply the model stopped writing part-way through its last bullet
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());

		// When:
		JsonNode parsed = client.parseJsonArray("[\"First.\", \"Second.\", \"Thi", true);

		// Then: the finished items are recovered; the caller's exact-count check is what decides to retry
		assertThat(parsed).isNotNull();
		assertThat(parsed.size()).isEqualTo(2);
	}

	@Test
	void shouldNotRepairAnArrayWhenPartialsAreNotAllowedTest() {
		// Given: the same unclosed reply, on a caller that has not opted into salvage
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());

		// When-Then: strict parsing is unchanged — nothing repaired slips into a caller that did not ask for it
		assertThat(client.parseJsonArray("[\"First.\", \"Second.\", \"Thi", false)).isNull();
	}

	@Test
	void shouldStillReadAnArrayWrappedInProseTest() {
		// Given: a reply with a preamble and a trailing comment around the array
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());

		// When:
		JsonNode parsed = client.parseJsonArray("Here you go:\n[\"First.\", \"Second.\"]\nLet me know.", true);

		// Then: the array is still found either side of the prose
		assertThat(parsed).isNotNull();
		assertThat(parsed.size()).isEqualTo(2);
	}

	@Test
	void shouldWidenTheReplySnippetWhenConfiguredToTest() {
		// Given: a client configured to log a wider head of a failed reply — the setting used to reach a parse
		// defect that sits past the default 400 characters on a deployed run
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		props.setReplySnippetLimit(1200);
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());

		// When:
		String snippet = client.snippet("x".repeat(2000));

		// Then: the configured cap is what the snippet honours, not the former fixed 400
		assertThat(snippet.length()).isEqualTo(1201);
	}

	@Test
	void shouldRetryTheTransientStatusesThatBlankedABatchTest() {
		// Given: the client. The 522 that wiped the PrimaryKpis fact columns is a Cloudflare edge timeout,
		// alongside the other transient upstream conditions worth another send.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());

		// When-Then: every transient upstream status is retried.
		assertThat(client.isTransientStatus(522)).isTrue();
		assertThat(client.isTransientStatus(529)).isTrue();
		assertThat(client.isTransientStatus(429)).isTrue();
		assertThat(client.isTransientStatus(500)).isTrue();
		assertThat(client.isTransientStatus(503)).isTrue();
		assertThat(client.isTransientStatus(504)).isTrue();
	}

	@Test
	void shouldNotRetryPermanentClientErrorsTest() {
		// Given: the client. A bad request or auth failure would only fail again, so it must fail fast.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());

		// When-Then: permanent statuses are never retried.
		assertThat(client.isTransientStatus(400)).isFalse();
		assertThat(client.isTransientStatus(401)).isFalse();
		assertThat(client.isTransientStatus(403)).isFalse();
		assertThat(client.isTransientStatus(404)).isFalse();
	}

	@Test
	void shouldSkipTheBackoffSleepWhenTheBaseDelayIsZeroTest() {
		// Given: a client configured with a zero base backoff, so a retry incurs no wait.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		props.setRetryBackoffMillis(0);
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(), new ClaudeFailureLogImpl());

		// When: the backoff for a failed attempt is applied.
		long start = System.nanoTime();
		client.backoffBeforeRetry(3, "PrimaryKpis");
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

		// Then: it returns immediately rather than sleeping.
		assertThat(elapsedMillis).isLessThan(50);
	}

	@Test
	void shouldRecordTheUsageBlockOfAReplyAgainstTheRunTest() throws Exception {
		// Given: a run's accounting scope, and a Messages API reply carrying a usage block
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		ClaudeUsageTrackerImpl tracker = new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl());
		var scope = tracker.begin(7L, null, null);
		AnthropicMessagesClient client =
				new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, new ClaudeResponseNormalizer(), tracker, new PromptTokenEstimator(), new ClaudeFailureLogImpl());
		JsonNode response = new ObjectMapper().readTree("""
				{"model": "claude-sonnet-4-6", "usage": {"input_tokens": 1200, "output_tokens": 340,
				"cache_creation_input_tokens": 90, "cache_read_input_tokens": 8000}}""");

		// When:
		client.recordUsage(response, "BatchC");

		// Then: every token class lands in the run's totals, kept apart for pricing
		var usage = scope.snapshot();
		assertThat(usage.inputTokens()).isEqualTo(1200);
		assertThat(usage.outputTokens()).isEqualTo(340);
		assertThat(usage.cacheWriteTokens()).isEqualTo(90);
		assertThat(usage.cacheReadTokens()).isEqualTo(8000);
		assertThat(usage.calls()).isEqualTo(1);
		assertThat(usage.model()).isEqualTo("claude-sonnet-4-6");
	}

	@Test
	void shouldSendOnlyTheUserTurnAndNeverAnAssistantPrefillTest() {
		// Given: a prompt with no cache marker, sent the way any call sends it
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class),
				props, new ClaudeResponseNormalizer(),
				new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(),
				new ClaudeFailureLogImpl());

		// When: the request's message list is built
		var messages = client.buildMessages("Return four observations.");

		// Then: exactly one user turn goes out — an assistant prefill would be rejected with a 400 by the model
		assertThat(messages).hasSize(1);
		assertThat(messages.getFirst()).containsEntry("role", "user");
	}

	@Test
	void shouldIgnoreAReplyThatCarriesNoUsageBlockTest() throws Exception {
		// Given: a reply with no usage block — accounting must never break a request
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		ClaudeUsageTrackerImpl tracker = new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl());
		var scope = tracker.begin(7L, null, null);
		AnthropicMessagesClient client =
				new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, new ClaudeResponseNormalizer(), tracker, new PromptTokenEstimator(), new ClaudeFailureLogImpl());
		JsonNode response = new ObjectMapper().readTree("{\"content\": []}");

		// When:
		client.recordUsage(response, "BatchC");

		// Then:
		assertThat(scope.snapshot().calls()).isZero();
	}

	@Test
	void shouldSendTheMessagesRequestAndReturnARejectedStatusWithoutThrowingTest() {
		// Given: a RestClient bound to a mock server that answers with an Anthropic overload
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props,
				new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()),
				new PromptTokenEstimator(), new ClaudeFailureLogImpl());
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.anthropic.com");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://api.anthropic.com/v1/messages"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("x-api-key", "key"))
				.andExpect(header("anthropic-version", "2023-06-01"))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(content().string("{\"model\":\"m\"}"))
				.andRespond(withStatus(HttpStatusCode.valueOf(529)).body("overloaded"));
		byte[] body = "{\"model\":\"m\"}".getBytes(StandardCharsets.UTF_8);

		// When:
		ResponseEntity<String> response = client.exchange(builder.build(), body);

		// Then: the status reaches the retry logic instead of surfacing as an exception
		assertThat(response.getStatusCode().value()).isEqualTo(529);
		assertThat(response.getBody()).isEqualTo("overloaded");
		server.verify();
	}

	@Test
	void shouldRetryATransientStatusThenReturnTheParsedReplyTest() {
		// Given: one 529 overload followed by a billed 200
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		props.setMaxRetries(2);
		props.setRetryBackoffMillis(0);
		AnthropicMessagesClient client = spy(new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props,
				new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()),
				new PromptTokenEstimator(), new ClaudeFailureLogImpl()));
		RestClient restClient = mock(RestClient.class);
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		doReturn(ResponseEntity.status(529).body("overloaded"))
				.doReturn(ResponseEntity.ok("{\"content\": []}"))
				.when(client).exchange(restClient, body);

		// When:
		JsonNode reply = client.sendWithRetry(restClient, body, "prompt", "BatchB");

		// Then:
		assertThat(reply.has("content")).isTrue();
		verify(client, times(2)).exchange(restClient, body);
	}

	@Test
	void shouldFailFastOnAPermanentStatusTest() {
		// Given: a 400 bad request, which would only fail again
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		props.setMaxRetries(3);
		props.setRetryBackoffMillis(0);
		AnthropicMessagesClient client = spy(new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props,
				new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()),
				new PromptTokenEstimator(), new ClaudeFailureLogImpl()));
		RestClient restClient = mock(RestClient.class);
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		doReturn(ResponseEntity.status(400).body("bad request")).when(client).exchange(restClient, body);

		// When:
		JsonNode reply = client.sendWithRetry(restClient, body, "prompt", "BatchB");

		// Then:
		assertThat(reply).isNull();
		verify(client, times(1)).exchange(restClient, body);
	}

	@Test
	void shouldBookAnEstimateWhenTheLastAttemptFailsInTransportTest() {
		// Given: every attempt times out after the request went out
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		props.setMaxRetries(1);
		props.setRetryBackoffMillis(0);
		ClaudeUsageTracker tracker = mock(ClaudeUsageTracker.class);
		PromptTokenEstimator estimator = new PromptTokenEstimator();
		AnthropicMessagesClient client = spy(new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props,
				new ClaudeResponseNormalizer(), tracker, estimator, new ClaudeFailureLogImpl()));
		RestClient restClient = mock(RestClient.class);
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		doThrow(new ResourceAccessException("Read timed out")).when(client).exchange(restClient, body);

		// When:
		JsonNode reply = client.sendWithRetry(restClient, body, "Summarise the campaign.", "BatchB");

		// Then: both attempts were made and the lost call is booked once, as an estimate
		assertThat(reply).isNull();
		verify(client, times(2)).exchange(restClient, body);
		verify(tracker).recordEstimated("BatchB", estimator.estimateTokens("Summarise the campaign."),
				props.getModel());
	}

	@Test
	void shouldBuildOnePooledClientPerResponseTimeoutTest() {
		// Given: a factory handing out one client per timeout
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		PooledRestClientFactory factory = mock(PooledRestClientFactory.class);
		RestClient thirty = mock(RestClient.class);
		RestClient sixty = mock(RestClient.class);
		when(factory.createClient("anthropic", "https://api.anthropic.com", Duration.ofSeconds(30))).thenReturn(thirty);
		when(factory.createClient("anthropic", "https://api.anthropic.com", Duration.ofSeconds(60))).thenReturn(sixty);
		AnthropicMessagesClient client = new AnthropicMessagesClient(factory, props, new ClaudeResponseNormalizer(),
				new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class), new CurrentTimeImpl(), new ClaudeUsageEventEntityMapperImpl()), new PromptTokenEstimator(),
				new ClaudeFailureLogImpl());

		// When:
		RestClient first = client.restClientFor(60);
		RestClient again = client.restClientFor(60);
		RestClient shorter = client.restClientFor(30);

		// Then: the 60 s client is built once and reused; 30 s gets its own
		assertThat(first).isSameAs(sixty).isSameAs(again);
		assertThat(shorter).isSameAs(thirty);
		verify(factory, times(1)).createClient("anthropic", "https://api.anthropic.com", Duration.ofSeconds(60));
	}
}
