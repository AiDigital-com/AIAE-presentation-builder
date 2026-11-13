package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.externalservices.common.http.PooledRestClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AnthropicMessagesClientTest {

	@Test
	void shouldKeepTheCompleteBulletsOfATruncatedPerTacticReplyTest() {
		// Given: a per-tactic bullet reply the model stopped writing part-way through the fourth bullet —
		// the shape that used to blank a whole breakdown slide
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));
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
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));
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
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));
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
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));
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
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

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
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

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
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

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
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

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
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));
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
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));
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
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

		// When-Then: a non-object never masquerades as a usable reply.
		assertThat(client.parseJsonObject("[\"a\", \"b\"]", true)).isNull();
	}

	@Test
	void shouldReturnNullForFreeTextWithNoObjectTest() {
		// Given: a reply with no JSON object at all — a refusal or a plain-prose answer.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

		// When-Then:
		assertThat(client.parseJsonObject("I can't help with that request.", true)).isNull();
	}

	@Test
	void shouldFlattenAndCapTheReplySnippetTest() {
		// Given: a multi-line reply longer than the snippet cap.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));
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
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));
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
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

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
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

		// When-Then: strict parsing is unchanged — nothing repaired slips into a caller that did not ask for it
		assertThat(client.parseJsonArray("[\"First.\", \"Second.\", \"Thi", false)).isNull();
	}

	@Test
	void shouldStillReadAnArrayWrappedInProseTest() {
		// Given: a reply with a preamble and a trailing comment around the array
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

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
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

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
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

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
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

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
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

		// When: the backoff for a failed attempt is applied.
		long start = System.nanoTime();
		client.backoffBeforeRetry(3, "PrimaryKpis");
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

		// Then: it returns immediately rather than sleeping.
		assertThat(elapsedMillis).isLessThan(50);
	}

	@Test
	void shouldRecordTheUsageBlockOfAReplyAgainstTheRunTest() throws Exception {
		// Given: a Messages API reply carrying a usage block
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicCallListener listener = mock(AnthropicCallListener.class);
		AnthropicMessagesClient client =
				new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, listener);
		JsonNode response = new ObjectMapper().readTree("""
				{"model": "claude-sonnet-4-6", "usage": {"input_tokens": 1200, "output_tokens": 340,
				"cache_creation_input_tokens": 90, "cache_read_input_tokens": 8000}}""");

		// When:
		client.recordUsage(response, "BatchC");

		// Then: every token class is reported, kept apart for pricing, with the model that answered
		verify(listener).usage("BatchC", 1200, 340, 90, 8000, "claude-sonnet-4-6");
	}

	@Test
	void shouldSendOnlyTheUserTurnAndNeverAnAssistantPrefillTest() {
		// Given: a prompt with no cache marker, sent the way any call sends it
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

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
		AnthropicCallListener listener = mock(AnthropicCallListener.class);
		AnthropicMessagesClient client =
				new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, listener);
		JsonNode response = new ObjectMapper().readTree("{\"content\": []}");

		// When:
		client.recordUsage(response, "BatchC");

		// Then:
		verifyNoInteractions(listener);
	}

	@Test
	void shouldSendTheMessagesRequestAndReturnARejectedStatusWithoutThrowingTest() {
		// Given: a RestClient bound to a mock server that answers with an Anthropic overload
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));
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
		AnthropicMessagesClient client = spy(new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class)));
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
		AnthropicMessagesClient client = spy(new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class)));
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
		AnthropicCallListener listener = mock(AnthropicCallListener.class);
		AnthropicMessagesClient client = spy(new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, listener));
		RestClient restClient = mock(RestClient.class);
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		doThrow(new ResourceAccessException("Read timed out")).when(client).exchange(restClient, body);

		// When:
		JsonNode reply = client.sendWithRetry(restClient, body, "Summarise the campaign.", "BatchB");

		// Then: both attempts were made and the lost call is booked once, as an estimate
		assertThat(reply).isNull();
		verify(client, times(2)).exchange(restClient, body);
		verify(listener).unansweredCall("BatchB", "Summarise the campaign.", props.getModel());
	}

	@Test
	void shouldConcatenateTheTextBlocksOfAReplyTest() throws Exception {
		// Given: a reply split over two text blocks and a non-text block
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));
		JsonNode response = new ObjectMapper().readTree("""
				{"content":[{"type":"text","text":"hello "},{"type":"tool_use"},{"type":"text","text":"world"}]}""");

		// When:
		String text = client.replyText(response);

		// Then: an empty reply reads as no text at all
		assertThat(text).isEqualTo("hello world");
		assertThat(client.replyText(new ObjectMapper().readTree("{\"content\": []}"))).isNull();
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
		AnthropicMessagesClient client = new AnthropicMessagesClient(factory, props, mock(AnthropicCallListener.class));

		// When:
		RestClient first = client.restClientFor(60);
		RestClient again = client.restClientFor(60);
		RestClient shorter = client.restClientFor(30);

		// Then: the 60 s client is built once and reused; 30 s gets its own
		assertThat(first).isSameAs(sixty).isSameAs(again);
		assertThat(shorter).isSameAs(thirty);
		verify(factory, times(1)).createClient("anthropic", "https://api.anthropic.com", Duration.ofSeconds(60));
	}

	@Test
	void shouldReturnNothingFromEitherJsonCallWhenNoReplyArrivesTest() {
		// Given: a call that got no usable response at all
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicCallListener listener = mock(AnthropicCallListener.class);
		AnthropicMessagesClient client =
				spy(new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, listener));
		doReturn(null).when(client).callRaw("prompt", 100, 30, "BatchA");

		// When:
		JsonNode object = client.callJsonObject("prompt", 100, 30, "BatchA", true);
		JsonNode array = client.callJsonArray("prompt", 100, 30, "BatchA", true);

		// Then: nothing is parsed and nothing is reported as a reply problem
		assertThat(object).isNull();
		assertThat(array).isNull();
		verifyNoInteractions(listener);
	}

	@Test
	void shouldDropATruncatedReplyWhenPartialsAreNotAllowedTest() throws Exception {
		// Given: a reply the model stopped writing at its token budget
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicCallListener listener = mock(AnthropicCallListener.class);
		AnthropicMessagesClient client =
				spy(new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, listener));
		JsonNode truncated = new ObjectMapper().readTree("""
				{"stop_reason": "max_tokens", "content": [{"type": "text", "text": "{\\"a\\": 1, \\"b"}]}""");
		doReturn(truncated).when(client).callRaw("prompt", 100, 30, "BatchA");

		// When:
		JsonNode object = client.callJsonObject("prompt", 100, 30, "BatchA", false);
		JsonNode array = client.callJsonArray("prompt", 100, 30, "BatchA", false);

		// Then: a strict caller never receives a half reply, and no salvage is reported
		assertThat(object).isNull();
		assertThat(array).isNull();
		verifyNoInteractions(listener);
	}

	@Test
	void shouldSalvageATruncatedFencedObjectAndReportTheBudgetItRanOutOfTest() throws Exception {
		// Given: a fenced object cut part-way through its second field, on a client doubling every budget
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		props.setOutputTokenHeadroom(2.0);
		AnthropicCallListener listener = mock(AnthropicCallListener.class);
		AnthropicMessagesClient client =
				spy(new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, listener));
		JsonNode truncated = new ObjectMapper().readTree("""
				{"stop_reason": "max_tokens",
				"content": [{"type": "text", "text": "```json\\n{\\"a\\": 1, \\"b\\": \\"unfin"}]}""");
		doReturn(truncated).when(client).callRaw("prompt", 100, 30, "BatchA");

		// When:
		JsonNode object = client.callJsonObject("prompt", 100, 30, "BatchA", true);

		// Then: the finished field survives, and the result card names the budget actually sent
		assertThat(object.get("a").asInt()).isEqualTo(1);
		assertThat(object.has("b")).isFalse();
		verify(listener).replyProblem("BatchA", "reply ran out of its 200-token output budget; the fields it "
				+ "never reached kept their previous values");
	}

	@Test
	void shouldReturnNothingForAReplyWithoutTextTest() throws Exception {
		// Given: one reply with no content blocks and one whose only text is blank
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicCallListener listener = mock(AnthropicCallListener.class);
		AnthropicMessagesClient client =
				spy(new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, listener));
		ObjectMapper mapper = new ObjectMapper();
		doReturn(mapper.readTree("{\"stop_reason\": \"end_turn\", \"content\": []}"))
				.when(client).callRaw("empty", 100, 30, "BatchA");
		doReturn(mapper.readTree("{\"content\": [{\"type\": \"text\", \"text\": \"   \"}]}"))
				.when(client).callRaw("blank", 100, 30, "BatchA");

		// When:
		JsonNode emptyObject = client.callJsonObject("empty", 100, 30, "BatchA", true);
		JsonNode blankObject = client.callJsonObject("blank", 100, 30, "BatchA", true);
		JsonNode emptyArray = client.callJsonArray("empty", 100, 30, "BatchA", true);
		JsonNode blankArray = client.callJsonArray("blank", 100, 30, "BatchA", true);

		// Then: there is nothing to parse, and nothing worth flagging as a malformed reply
		assertThat(emptyObject).isNull();
		assertThat(blankObject).isNull();
		assertThat(emptyArray).isNull();
		assertThat(blankArray).isNull();
		verifyNoInteractions(listener);
	}

	@Test
	void shouldReportAnObjectReplyThatCarriesNoJsonTest() throws Exception {
		// Given: a refusal in place of the requested object
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicCallListener listener = mock(AnthropicCallListener.class);
		AnthropicMessagesClient client =
				spy(new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, listener));
		doReturn(new ObjectMapper().readTree("{\"content\": [{\"type\": \"text\", \"text\": \"I cannot do that.\"}]}"))
				.when(client).callRaw("prompt", 100, 30, "BatchA");

		// When:
		JsonNode object = client.callJsonObject("prompt", 100, 30, "BatchA", true);

		// Then: the head of the reply reaches the result card
		assertThat(object).isNull();
		verify(listener).replyProblem("BatchA",
				"reply was not valid JSON (object, 17 chars); it began: I cannot do that.");
	}

	@Test
	void shouldParseAFencedArrayReplyTest() throws Exception {
		// Given: a complete array wrapped in a plain code fence
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicCallListener listener = mock(AnthropicCallListener.class);
		AnthropicMessagesClient client =
				spy(new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, listener));
		doReturn(new ObjectMapper().readTree("""
				{"stop_reason": "end_turn",
				"content": [{"type": "text", "text": "```\\n[\\"a\\", \\"b\\"]\\n```"}]}"""))
				.when(client).callRaw("prompt", 100, 30, "BatchA");

		// When:
		JsonNode array = client.callJsonArray("prompt", 100, 30, "BatchA", false);

		// Then:
		assertThat(array).hasSize(2);
		assertThat(array.get(1).asText()).isEqualTo("b");
		verifyNoInteractions(listener);
	}

	@Test
	void shouldSalvageATruncatedArrayAndReportTheBudgetItRanOutOfTest() throws Exception {
		// Given: an array cut part-way through its third item, on a client sending budgets unscaled
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		props.setOutputTokenHeadroom(1.0);
		AnthropicCallListener listener = mock(AnthropicCallListener.class);
		AnthropicMessagesClient client =
				spy(new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, listener));
		doReturn(new ObjectMapper().readTree("""
				{"stop_reason": "max_tokens", "content": [{"type": "text", "text": "[\\"a\\", \\"b\\", \\"c"}]}"""))
				.when(client).callRaw("prompt", 300, 30, "BatchA");

		// When:
		JsonNode array = client.callJsonArray("prompt", 300, 30, "BatchA", true);

		// Then:
		assertThat(array).hasSize(2);
		verify(listener).replyProblem("BatchA", "reply ran out of its 300-token output budget; the fields it "
				+ "never reached kept their previous values");
	}

	@Test
	void shouldReportAnArrayReplyThatCarriesNoJsonTest() throws Exception {
		// Given: prose in place of the requested array
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicCallListener listener = mock(AnthropicCallListener.class);
		AnthropicMessagesClient client =
				spy(new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, listener));
		doReturn(new ObjectMapper().readTree("{\"content\": [{\"type\": \"text\", \"text\": \"no list here\"}]}"))
				.when(client).callRaw("prompt", 100, 30, "BatchA");

		// When:
		JsonNode array = client.callJsonArray("prompt", 100, 30, "BatchA", true);

		// Then:
		assertThat(array).isNull();
		verify(listener).replyProblem("BatchA", "reply was not valid JSON (array, 12 chars); it began: no list here");
	}

	@Test
	void shouldNotSalvageAnArrayStrictlyWhenItsBracketsDoNotParseTest() {
		// Given:
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

		// When-Then: a clean array parses directly; prose with no bracket, an unclosed bracket and a bracketed
		// span that is not JSON all yield nothing on a strict caller
		assertThat(client.parseJsonArray("[1, 2]", false)).hasSize(2);
		assertThat(client.parseJsonArray("nothing to see", false)).isNull();
		assertThat(client.parseJsonArray("list: [1, 2", false)).isNull();
		assertThat(client.parseJsonArray("Note [x] then", false)).isNull();
	}

	@Test
	void shouldRepairAProseWrappedArrayCutBeforeItsClosingBracketTest() {
		// Given: a preamble, then an array cut part-way through its second item
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

		// When:
		JsonNode array = client.parseJsonArray("Sure: [\"a\", \"b", true);

		// Then: the repair runs from the first bracket, keeping the finished item
		assertThat(array).hasSize(1);
		assertThat(array.get(0).asText()).isEqualTo("a");
	}

	@Test
	void shouldReadOnlyTheExpectedJsonShapeTest() {
		// Given:
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

		// When-Then: null, blank, malformed and wrong-shaped text read as nothing instead of throwing
		assertThat(client.readArray(null)).isNull();
		assertThat(client.readArray("  ")).isNull();
		assertThat(client.readArray("{\"a\": 1}")).isNull();
		assertThat(client.readArray("[1,")).isNull();
		assertThat(client.readObject(null)).isNull();
		assertThat(client.readObject(" ")).isNull();
		assertThat(client.readObject("[1]")).isNull();
		assertThat(client.readObject("{\"a\":")).isNull();
		assertThat(client.readObject("{\"a\": 1}").get("a").asInt()).isEqualTo(1);
	}

	@Test
	void shouldRepairAnObjectCutAfterADanglingKeyOnlyWhenPartialsAreAllowedTest() {
		// Given: an object truncated right after its second key
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));
		String truncated = "{\"a\": 1, \"b\"";

		// When:
		JsonNode lenient = client.parseJsonObject(truncated, true);
		JsonNode strict = client.parseJsonObject(truncated, false);

		// Then:
		assertThat(lenient.get("a").asInt()).isEqualTo(1);
		assertThat(lenient.size()).isEqualTo(1);
		assertThat(strict).isNull();
	}

	@Test
	void shouldGiveUpOnAWrappedObjectThatStaysInvalidAfterQuoteEscapingTest() {
		// Given: prose around an object whose values are missing a comma — no quote escaping can fix it
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

		// When-Then:
		assertThat(client.parseJsonObject("Result: {\"a\": 1 2} end", false)).isNull();
	}

	@Test
	void shouldKeepAlreadyEscapedQuotesWhileEscapingStrayOnesTest() {
		// Given: a value mixing a properly escaped quotation with an unescaped one
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));
		String text = "{\"a\": \"say \\\"hi\\\" and \"bye\" now\"}";

		// When:
		String escaped = client.escapeStrayQuotes(text);

		// Then: the escaped pair is copied as it is, only the stray pair gains backslashes
		assertThat(escaped).isEqualTo("{\"a\": \"say \\\"hi\\\" and \\\"bye\\\" now\"}");
	}

	@Test
	void shouldDecideWhetherAQuoteClosesItsStringFromWhatFollowsTest() {
		// Given:
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

		// When-Then: the end of the text, a colon after blanks and a comma ending the text close the string;
		// a comma followed by prose, or any other character, does not
		assertThat(client.closesString("\"abc\"", 4)).isTrue();
		assertThat(client.closesString("\"a\"  : 1", 2)).isTrue();
		assertThat(client.closesString("\"a\",", 2)).isTrue();
		assertThat(client.closesString("\"a\",  \"b\"", 2)).isTrue();
		assertThat(client.closesString("\"a\", the top", 2)).isFalse();
		assertThat(client.closesString("\"a\" b", 2)).isFalse();
	}

	@Test
	void shouldKeepAShortReplySnippetAsItIsTest() {
		// Given:
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		props.setReplySnippetLimit(10);
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

		// When:
		String snippet = client.snippet("  short\n reply ");

		// Then: a limit below the floor is raised to it, so the short reply is only flattened, never cut
		assertThat(snippet).isEqualTo("short reply");
		assertThat(client.snippet("y".repeat(100))).hasSize(81);
	}

	@Test
	void shouldHonourEscapesAndStrayClosersWhenRepairingTest() {
		// Given:
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

		// When:
		String escaped = client.repairTruncatedJson("[\"a\\\"b\", \"c");
		String strayCloser = client.repairTruncatedJson("[1]]");
		String trailingBlank = client.repairTruncatedJson("[1 ,  2");

		// Then: an escaped quote does not end the string, a closer with nothing open is kept as the cut, and
		// blanks before the dropped comma are trimmed
		assertThat(escaped).isEqualTo("[\"a\\\"b\"]");
		assertThat(strayCloser).isEqualTo("[1]]");
		assertThat(trailingBlank).isEqualTo("[1]");
	}

	@Test
	void shouldSplitAPromptAtItsCacheBreakpointTest() {
		// Given:
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));
		String marker = AnthropicMessagesClient.CACHE_BREAKPOINT;

		// When:
		Object split = client.buildUserContent("Rules." + marker + "Data." + marker + "More.");

		// Then: the prefix is cached, the rest is one uncached block with any further marker stripped
		assertThat(split).isEqualTo(List.of(
				Map.of("type", "text", "text", "Rules.", "cache_control", Map.of("type", "ephemeral")),
				Map.of("type", "text", "text", "Data.More.")));
	}

	@Test
	void shouldSendOnlyTheCachedBlockWhenNothingFollowsTheBreakpointTest() {
		// Given:
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

		// When:
		Object content = client.buildUserContent("Rules." + AnthropicMessagesClient.CACHE_BREAKPOINT);

		// Then:
		assertThat(content).isEqualTo(List.of(
				Map.of("type", "text", "text", "Rules.", "cache_control", Map.of("type", "ephemeral"))));
	}

	@Test
	void shouldSendAPlainPromptWhenTheBreakpointHasNoUsablePrefixTest() {
		// Given:
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));
		String marker = AnthropicMessagesClient.CACHE_BREAKPOINT;

		// When-Then: no marker, a marker at the start and a marker after blanks all send one plain string,
		// never carrying the marker itself
		assertThat(client.buildUserContent("Plain prompt.")).isEqualTo("Plain prompt.");
		assertThat(client.buildUserContent(marker + "Body.")).isEqualTo("Body.");
		assertThat(client.buildUserContent("  " + marker + "Body.")).isEqualTo("  Body.");
	}

	@Test
	void shouldNeverShrinkTheCallersTokenBudgetTest() {
		// Given: one client with a headroom below 1.0 and one with 1.5
		AnthropicProperties low = new AnthropicProperties();
		low.setApiKey("key");
		low.setOutputTokenHeadroom(0.5);
		AnthropicProperties high = new AnthropicProperties();
		high.setApiKey("key");
		high.setOutputTokenHeadroom(1.5);
		AnthropicMessagesClient lowClient = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), low, mock(AnthropicCallListener.class));
		AnthropicMessagesClient highClient = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), high, mock(AnthropicCallListener.class));

		// When-Then:
		assertThat(lowClient.effectiveMaxTokens(100)).isEqualTo(100);
		assertThat(highClient.effectiveMaxTokens(101)).isEqualTo(152);
	}

	@Test
	void shouldSendTheMessagesRequestWithItsCacheBlocksAndRecordTheUsageTest() throws Exception {
		// Given: a pooled client bound to a mock server answering with a billed reply
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		props.setModel("claude-test");
		props.setOutputTokenHeadroom(1.5);
		AnthropicCallListener listener = mock(AnthropicCallListener.class);
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.anthropic.com");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AtomicReference<String> sent = new AtomicReference<>();
		server.expect(requestTo("https://api.anthropic.com/v1/messages"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(request -> sent.set(((MockClientHttpRequest) request).getBodyAsString()))
				.andRespond(withSuccess("""
						{"model": "claude-test", "content": [{"type": "text", "text": "hi"}],
						"usage": {"input_tokens": 10, "output_tokens": 2}}""", MediaType.APPLICATION_JSON));
		PooledRestClientFactory factory = mock(PooledRestClientFactory.class);
		when(factory.createClient("anthropic", "https://api.anthropic.com", Duration.ofSeconds(45)))
				.thenReturn(builder.build());
		AnthropicMessagesClient client = new AnthropicMessagesClient(factory, props, listener);

		// When:
		JsonNode reply = client.callRaw("Rules." + AnthropicMessagesClient.CACHE_BREAKPOINT + "Data.", 100, 45,
				"BatchA");

		// Then: the body carries the model, the headroom-scaled budget, disabled thinking and the split prompt
		JsonNode body = new ObjectMapper().readTree(sent.get());
		assertThat(body.get("model").asText()).isEqualTo("claude-test");
		assertThat(body.get("max_tokens").asInt()).isEqualTo(150);
		assertThat(body.get("thinking").get("type").asText()).isEqualTo("disabled");
		assertThat(body.get("messages")).hasSize(1);
		JsonNode content = body.get("messages").get(0).get("content");
		assertThat(body.get("messages").get(0).get("role").asText()).isEqualTo("user");
		assertThat(content.get(0).get("text").asText()).isEqualTo("Rules.");
		assertThat(content.get(0).get("cache_control").get("type").asText()).isEqualTo("ephemeral");
		assertThat(content.get(1).get("text").asText()).isEqualTo("Data.");
		assertThat(content.get(1).has("cache_control")).isFalse();
		assertThat(client.replyText(reply)).isEqualTo("hi");
		verify(listener).usage("BatchA", 10, 2, 0, 0, "claude-test");
		server.verify();
	}

	@Test
	void shouldReturnNothingWhenTheRequestCannotBeBuiltTest() {
		// Given: a pool that cannot hand out a client
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicCallListener listener = mock(AnthropicCallListener.class);
		PooledRestClientFactory factory = mock(PooledRestClientFactory.class);
		when(factory.createClient("anthropic", "https://api.anthropic.com", Duration.ofSeconds(30)))
				.thenThrow(new IllegalStateException("pool closed"));
		AnthropicMessagesClient client = new AnthropicMessagesClient(factory, props, listener);

		// When:
		JsonNode reply = client.callRaw("prompt", 100, 30, "BatchA");

		// Then: nothing left the process, so nothing is booked
		assertThat(reply).isNull();
		verifyNoInteractions(listener);
	}

	@Test
	void shouldGiveUpOnATransientStatusWhenNoRetryIsLeftTest() {
		// Given: a negative retry count, clamped to a single attempt, meeting an overload
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		props.setMaxRetries(-3);
		AnthropicCallListener listener = mock(AnthropicCallListener.class);
		AnthropicMessagesClient client =
				spy(new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, listener));
		RestClient restClient = mock(RestClient.class);
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		doReturn(ResponseEntity.status(529).body("overloaded")).when(client).exchange(restClient, body);

		// When:
		JsonNode reply = client.sendWithRetry(restClient, body, "prompt", "BatchB");

		// Then: the rejected request is neither retried nor booked
		assertThat(reply).isNull();
		verify(client, times(1)).exchange(restClient, body);
		verifyNoInteractions(listener);
	}

	@Test
	void shouldWaitTheBackoffScaledByTheFailedAttemptTest() {
		// Given: a 5 ms base backoff
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		props.setRetryBackoffMillis(5);
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

		// When: the backoff after the second failed attempt is applied
		long start = System.nanoTime();
		client.backoffBeforeRetry(2, "BatchB");
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

		// Then: it waits at least twice the base delay and leaves the thread uninterrupted
		assertThat(elapsedMillis).isGreaterThanOrEqualTo(10);
		assertThat(Thread.currentThread().isInterrupted()).isFalse();
	}

	@Test
	void shouldCopyATrailingBackslashInsideAStringAsItIsTest() {
		// Given: a reply cut right after a backslash inside a string value
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));
		String text = "{\"a\": \"x\\";

		// When-Then: there is no escaped character to copy, so the text is left unchanged rather than overrun
		assertThat(client.escapeStrayQuotes(text)).isEqualTo(text);
	}

	@Test
	void shouldRestoreTheInterruptFlagWhenTheBackoffIsInterruptedTest() {
		// Given: a long backoff on a thread that is already being shut down
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		props.setRetryBackoffMillis(60_000);
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));
		Thread.currentThread().interrupt();

		// When:
		long start = System.nanoTime();
		client.backoffBeforeRetry(1, "BatchB");
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

		// Then: the wait is abandoned at once and the interrupt still propagates (Thread.interrupted clears it)
		assertThat(Thread.interrupted()).isTrue();
		assertThat(elapsedMillis).isLessThan(5_000);
	}

	@Test
	void shouldIgnoreAMissingResponseOrAUsageThatIsNotAnObjectTest() throws Exception {
		// Given:
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicCallListener listener = mock(AnthropicCallListener.class);
		AnthropicMessagesClient client =
				new AnthropicMessagesClient(mock(PooledRestClientFactory.class), props, listener);

		// When:
		client.recordUsage(null, "BatchC");
		client.recordUsage(new ObjectMapper().readTree("{\"usage\": 5}"), "BatchC");

		// Then:
		verifyNoInteractions(listener);
	}

	@Test
	void shouldReadNoTextFromAReplyWithoutAContentArrayTest() throws Exception {
		// Given:
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				mock(PooledRestClientFactory.class), props, mock(AnthropicCallListener.class));

		// When-Then:
		assertThat(client.replyText(new ObjectMapper().readTree("{\"content\": \"text\"}"))).isNull();
		assertThat(client.replyText(new ObjectMapper().readTree("{}"))).isNull();
	}
}
