package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.diagnostics.ClaudeFailureLog;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealClaudeClientGroundingTest {

	@Mock
	AnthropicMessagesClient messagesClient;
	@Mock
	ClaudeCompressionService compressionService;
	@Mock
	ClaudeFailureLog failureLog;

	@Test
	void shouldSendTheRulesAndKeepAReplyWhoseFiguresAreSupportedTest() {
		// Given: a reply citing only a figure the prompt carries
		NarrativeGrounding grounding = new NarrativeGrounding(new AnthropicProperties());
		RealClaudeClient client = new RealClaudeClient(messagesClient,
				new ClaudeBatchPromptBuilder(new ClaudeResponseNormalizer(), new Fmt()), new ClaudeResponseNormalizer(),
				compressionService, new ReportClaudeDefaults(), new WorkbookGeoFilter(), new PromptTokenEstimator(),
				failureLog, new TacticThoughtsCompleteness(), grounding, new AnthropicProperties());
		String prompt = "Display: Imps 500,000";
		JsonNode reply = new ObjectMapper().valueToTree(Map.of("overview", "Display delivered 500,000 impressions."));
		when(messagesClient.callJsonObject(eq(grounding.withRules(prompt)), eq(900), eq(60), eq("BatchCampaign"),
				eq(true))).thenReturn(reply);

		// When: the call is made
		JsonNode result = client.callGrounded(prompt, 900, 60, "BatchCampaign", true);

		// Then: the reply is used as is, in one call, with nothing flagged
		assertThat(result).isSameAs(reply);
		verify(messagesClient, times(1)).callJsonObject(eq(grounding.withRules(prompt)), eq(900), eq(60),
				eq("BatchCampaign"), eq(true));
		verifyNoInteractions(failureLog);
	}

	@Test
	void shouldRetryOnceNamingTheInventedFiguresAndKeepTheCorrectedReplyTest() {
		// Given: a first reply inventing a CTR, and a retry that drops it
		NarrativeGrounding grounding = new NarrativeGrounding(new AnthropicProperties());
		RealClaudeClient client = new RealClaudeClient(messagesClient,
				new ClaudeBatchPromptBuilder(new ClaudeResponseNormalizer(), new Fmt()), new ClaudeResponseNormalizer(),
				compressionService, new ReportClaudeDefaults(), new WorkbookGeoFilter(), new PromptTokenEstimator(),
				failureLog, new TacticThoughtsCompleteness(), grounding, new AnthropicProperties());
		String prompt = "Display: Imps 500,000 | Clicks 2,000";
		String grounded = grounding.withRules(prompt);
		JsonNode invented = new ObjectMapper().valueToTree(Map.of("overview", "Display hit a 3.7% CTR."));
		JsonNode corrected = new ObjectMapper().valueToTree(Map.of("overview", "Display drew 2,000 clicks."));
		when(messagesClient.callJsonObject(eq(grounded), eq(900), eq(60), eq("BatchCampaign"), eq(true)))
				.thenReturn(invented);
		when(messagesClient.callJsonObject(eq(grounded + grounding.retryNote(List.of("3.7%"))), eq(900), eq(60),
				eq("BatchCampaign"), eq(true))).thenReturn(corrected);

		// When: the call is made
		JsonNode result = client.callGrounded(prompt, 900, 60, "BatchCampaign", true);

		// Then: the corrected reply ships and nothing is left to flag
		assertThat(result).isSameAs(corrected);
		verifyNoInteractions(failureLog);
	}

	@Test
	void shouldFlagFiguresThatSurviveTheRetryOnTheReportCardTest() {
		// Given: both replies citing the same invented spend
		NarrativeGrounding grounding = new NarrativeGrounding(new AnthropicProperties());
		RealClaudeClient client = new RealClaudeClient(messagesClient,
				new ClaudeBatchPromptBuilder(new ClaudeResponseNormalizer(), new Fmt()), new ClaudeResponseNormalizer(),
				compressionService, new ReportClaudeDefaults(), new WorkbookGeoFilter(), new PromptTokenEstimator(),
				failureLog, new TacticThoughtsCompleteness(), grounding, new AnthropicProperties());
		String prompt = "Display: Imps 500,000";
		String grounded = grounding.withRules(prompt);
		JsonNode first = new ObjectMapper().valueToTree(Map.of("overview", "Display spent $12,400."));
		JsonNode second = new ObjectMapper().valueToTree(Map.of("overview", "Display used $12,400 of budget."));
		when(messagesClient.callJsonObject(eq(grounded), eq(900), eq(60), eq("BatchCampaign"), eq(true)))
				.thenReturn(first);
		when(messagesClient.callJsonObject(eq(grounded + grounding.retryNote(List.of("$12,400"))), eq(900),
				eq(60), eq("BatchCampaign"), eq(true))).thenReturn(second);

		// When: the call is made
		JsonNode result = client.callGrounded(prompt, 900, 60, "BatchCampaign", true);

		// Then: the retry is kept (no worse) and the figure is named on the report card for a human to check
		assertThat(result).isSameAs(second);
		verify(failureLog).record(eq("BatchCampaign"), eq(grounding.warning(List.of("$12,400"))));
	}

	@Test
	void shouldSkipTheCheckWhenItIsSwitchedOffTest() {
		// Given: the figure check switched off, and a reply with an invented figure
		AnthropicProperties properties = new AnthropicProperties();
		properties.setFigureCheck(false);
		NarrativeGrounding grounding = new NarrativeGrounding(properties);
		RealClaudeClient client = new RealClaudeClient(messagesClient,
				new ClaudeBatchPromptBuilder(new ClaudeResponseNormalizer(), new Fmt()), new ClaudeResponseNormalizer(),
				compressionService, new ReportClaudeDefaults(), new WorkbookGeoFilter(), new PromptTokenEstimator(),
				failureLog, new TacticThoughtsCompleteness(), grounding, new AnthropicProperties());
		String prompt = "Display: Imps 500,000";
		JsonNode reply = new ObjectMapper().valueToTree(Map.of("overview", "Display hit a 3.7% CTR."));
		when(messagesClient.callJsonObject(eq(grounding.withRules(prompt)), eq(900), eq(60), eq("BatchCampaign"),
				eq(true))).thenReturn(reply);

		// When: the call is made
		JsonNode result = client.callGrounded(prompt, 900, 60, "BatchCampaign", true);

		// Then: the rules are still sent, but the reply ships after a single call
		assertThat(result).isSameAs(reply);
		verify(messagesClient, times(1)).callJsonObject(eq(grounding.withRules(prompt)), eq(900), eq(60),
				eq("BatchCampaign"), eq(true));
	}

	@Test
	void shouldDropABriefDigestThatAsksForTheBriefInsteadOfSummarisingItTest() {
		// Given: a brief that is only a path, and a digest reply asking for the real brief
		NarrativeGrounding grounding = new NarrativeGrounding(new AnthropicProperties());
		ClaudeBatchPromptBuilder builder = new ClaudeBatchPromptBuilder(new ClaudeResponseNormalizer(), new Fmt());
		RealClaudeClient client = new RealClaudeClient(messagesClient, builder, new ClaudeResponseNormalizer(),
				compressionService, new ReportClaudeDefaults(), new WorkbookGeoFilter(), new PromptTokenEstimator(),
				failureLog, new TacticThoughtsCompleteness(), grounding, new AnthropicProperties());
		String brief = "Mediagistic/AC Supply Geo/";
		JsonNode refusal = new ObjectMapper().valueToTree(Map.of("content", List.of(Map.of("type", "text",
				"text", "Please provide the actual campaign brief content. The text you've shared appears to be "
						+ "only a file path or label, not the brief itself."))));
		when(messagesClient.callRaw(eq(builder.buildBriefDigestPrompt(brief, 2000).orElseThrow()), eq(1200),
				eq(60), eq("BriefDigest"))).thenReturn(refusal);

		// When: the brief is digested
		String digest = client.digestBrief(brief);

		// Then: no digest is returned, so the raw brief is used, and the report card says why
		assertThat(digest).isNull();
		verify(failureLog).record(eq("BriefDigest"), eq("the campaign brief was too thin to summarise, so the RFP "
				+ "field shows the brief as entered; add the real campaign brief for better AI copy."));
	}

	@Test
	void shouldKeepARealBriefDigestTest() {
		// Given: a digest reply that actually summarises the brief
		NarrativeGrounding grounding = new NarrativeGrounding(new AnthropicProperties());
		ClaudeBatchPromptBuilder builder = new ClaudeBatchPromptBuilder(new ClaudeResponseNormalizer(), new Fmt());
		RealClaudeClient client = new RealClaudeClient(messagesClient, builder, new ClaudeResponseNormalizer(),
				compressionService, new ReportClaudeDefaults(), new WorkbookGeoFilter(), new PromptTokenEstimator(),
				failureLog, new TacticThoughtsCompleteness(), grounding, new AnthropicProperties());
		String brief = "AC Supply wants HVAC contractors in Morgan City, LA to visit its branch, Jan 23 to May 12.";
		JsonNode summary = new ObjectMapper().valueToTree(Map.of("content", List.of(Map.of("type", "text",
				"text", "AC Supply: drive HVAC contractors in Morgan City, LA to the branch; Jan 23 to May 12."))));
		when(messagesClient.callRaw(eq(builder.buildBriefDigestPrompt(brief, 2000).orElseThrow()), eq(1200),
				eq(60), eq("BriefDigest"))).thenReturn(summary);

		// When: the brief is digested
		String digest = client.digestBrief(brief);

		// Then: the summary is kept and nothing is flagged
		assertThat(digest).startsWith("AC Supply: drive HVAC contractors");
		verifyNoInteractions(failureLog);
	}

	@Test
	void shouldFlagEmptyCampaignResultsOnTheReportCardTest() {
		// Given: a campaign-results prompt whose calls both fail (the transport returns nothing)
		NarrativeGrounding grounding = new NarrativeGrounding(new AnthropicProperties());
		ClaudeBatchPromptBuilder builder = mock(ClaudeBatchPromptBuilder.class);
		RealClaudeClient client = new RealClaudeClient(messagesClient, builder, new ClaudeResponseNormalizer(),
				compressionService, new ReportClaudeDefaults(), new WorkbookGeoFilter(), new PromptTokenEstimator(),
				failureLog, new TacticThoughtsCompleteness(), grounding, new AnthropicProperties());
		CampaignData data = new CampaignData("", "", "", "", "", new FlightDates(null, null), "", "", "", "", "",
				new Totals(0, 0, 0, 0, null, null), Map.of(), "");
		CampaignFrequencies frequencies = new CampaignFrequencies(null, null, null, null);
		when(builder.buildCampaignResultsPrompt(eq(data), eq("brief"), eq(frequencies), eq(List.of())))
				.thenReturn(Optional.of("Campaign prompt"));

		// When: the campaign results are requested
		ClaudeResults results = client.batchCampaignResults(data, "brief", frequencies, List.of());

		// Then: the empty results ship, and the report card explains the blank slides
		assertThat(results.thoughtsOnPerformance()).isEmpty();
		verify(failureLog).record(eq("BatchCampaign"), eq(RealClaudeClient.CAMPAIGN_RESULTS_EMPTY_WARNING));
	}
}
