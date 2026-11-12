package com.aidigital.reportconstructor.externalservices.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NarrativeGroundingTest {

	@Test
	void shouldPutTheRulesBeforeTheCacheBreakpointTest() {
		// Given: a prompt whose static instructions end at a cache breakpoint, followed by the data
		NarrativeGrounding grounding = new NarrativeGrounding(new AnthropicProperties());
		String prompt = "Instructions." + AnthropicMessagesClient.CACHE_BREAKPOINT + "DATA: Imps 1,000";

		// When: the rules are added
		String grounded = grounding.withRules(prompt);

		// Then: they sit with the cached instructions, ahead of the data, and nothing else moves
		assertThat(grounded).isEqualTo("Instructions." + NarrativeGrounding.RULES + "\n"
				+ AnthropicMessagesClient.CACHE_BREAKPOINT + "DATA: Imps 1,000");
	}

	@Test
	void shouldAppendTheRulesWhenThereIsNoBreakpointTest() {
		// Given: a prompt with no cache breakpoint
		NarrativeGrounding grounding = new NarrativeGrounding(new AnthropicProperties());

		// When: the rules are added
		String grounded = grounding.withRules("Write the copy.");

		// Then: they are appended
		assertThat(grounded).isEqualTo("Write the copy." + NarrativeGrounding.RULES);
		assertThat(grounding.withRules(null)).isNull();
	}

	@Test
	void shouldAcceptFiguresThatMatchTheDataAtTheirWrittenPrecisionTest() {
		// Given: data with exact figures, and a reply that rounds them
		NarrativeGrounding grounding = new NarrativeGrounding(new AnthropicProperties());
		String prompt = "Video: Imps 1,831,381 | VCR 97.40% | Spend $20,769.00";
		JsonNode reply = new ObjectMapper().valueToTree(Map.of(
				"overview", "Video delivered 1.83M impressions at a 97% VCR for $20.8K."));

		// When: the reply is checked
		List<String> unsupported = grounding.unsupportedFigures(prompt, reply);

		// Then: every rounded figure is traced back to its source
		assertThat(unsupported).isEmpty();
	}

	@Test
	void shouldAcceptDifferencesSharesAndPercentChangesOfDataFiguresTest() {
		// Given: plan and delivery figures, and a reply that compares them
		NarrativeGrounding grounding = new NarrativeGrounding(new AnthropicProperties());
		String prompt = "Live Sports: VCR 97.40% vs plan 80.00% | Imps 1,831,381 of plan 5,882,353 | "
				+ "Top creative imps 775,607";
		JsonNode reply = new ObjectMapper().valueToTree(Map.of(
				"field_1", "VCR beat plan by 17.4pp while delivery ran 68.9% under plan.",
				"field_2", "The top creative carried 42.4% of volume."));

		// When: the reply is checked
		List<String> unsupported = grounding.unsupportedFigures(prompt, reply);

		// Then: the difference, the percent change and the share all derive from the data
		assertThat(unsupported).isEmpty();
	}

	@Test
	void shouldFlagFiguresTheDataDoesNotSupportTest() {
		// Given: data with two figures, and a reply that invents a CTR and a spend
		NarrativeGrounding grounding = new NarrativeGrounding(new AnthropicProperties());
		String prompt = "Display: Imps 500,000 | Clicks 2,000";
		JsonNode reply = new ObjectMapper().valueToTree(Map.of(
				"overview", "Display reached a 3.7% CTR on $12,400 of spend from 500,000 impressions."));

		// When: the reply is checked
		List<String> unsupported = grounding.unsupportedFigures(prompt, reply);

		// Then: both invented figures are named as written; the real one is not
		assertThat(unsupported).containsExactly("3.7%", "$12,400");
	}

	@Test
	void shouldIgnoreCountsYearsDatesAndClockTimesTest() {
		// Given: data with no figures at all, and a reply that only uses non-metric numbers
		NarrativeGrounding grounding = new NarrativeGrounding(new AnthropicProperties());
		JsonNode reply = new ObjectMapper().valueToTree(List.of(
				"Across 4 tactics in 2026, delivery peaked on Mar 15 between 6 PM and 9 PM."));

		// When: the reply is checked
		List<String> unsupported = grounding.unsupportedFigures("No figures here.", reply);

		// Then: none of those numbers is treated as a figure to verify
		assertThat(unsupported).isEmpty();
	}

	@Test
	void shouldNameTheFiguresInTheRetryNoteAndWarningTest() {
		// Given: two unsupported figures
		NarrativeGrounding grounding = new NarrativeGrounding(new AnthropicProperties());
		List<String> unsupported = List.of("3.7%", "$12,400");

		// When: the retry note and the report-card warning are built
		String note = grounding.retryNote(unsupported);
		String warning = grounding.warning(unsupported);

		// Then: both name exactly those figures
		assertThat(note).contains("cannot be derived from it: 3.7%, $12,400.");
		assertThat(warning).isEqualTo("the AI copy cites figures not found in the report data (3.7%, $12,400) "
				+ "— check them before sending.");
	}

	@Test
	void shouldReportTheCheckSwitchFromConfigurationTest() {
		// Given: properties with the figure check switched off
		AnthropicProperties properties = new AnthropicProperties();
		properties.setFigureCheck(false);

		// When-Then: the grounding reports it off, and on by default
		assertThat(new NarrativeGrounding(properties).isCheckEnabled()).isFalse();
		assertThat(new NarrativeGrounding(new AnthropicProperties()).isCheckEnabled()).isTrue();
	}

	@Test
	void shouldRecogniseDigestRepliesThatAskForTheBriefTest() {
		// Given: the two refusal replies seen in generated workbooks, and a genuine digest
		NarrativeGrounding grounding = new NarrativeGrounding(new AnthropicProperties());
		String acSupply = "Please provide the actual campaign brief content. The text you've shared "
				+ "(\"Mediagistic/AC Supply Geo/\") appears to be only a file path or label, not the brief itself.";
		String apple = "Please provide the full campaign brief content. The text currently reads only \"Apple "
				+ "Federal Credit Union 2026,\" which does not contain the facts needed to produce a faithful digest.";
		String digest = "Apple Federal Credit Union: drive awareness of local banking among families, Mar 1 to May 11.";

		// When-Then: the requests are refusals, the digest is not
		assertThat(grounding.isRefusal(acSupply)).isTrue();
		assertThat(grounding.isRefusal(apple)).isTrue();
		assertThat(grounding.isRefusal(digest)).isFalse();
		assertThat(grounding.isRefusal(null)).isFalse();
	}

	@Test
	void shouldTellEveryNarrativeCallToAnswerInJsonWithoutRefusingTest() {
		// Given: the shared rules block

		// When-Then: it demands JSON only and forbids refusing over thin input such as a label-only brief
		assertThat(NarrativeGrounding.RULES).contains(
				"return ONLY the JSON object asked for",
				"Never refuse and never flag thin, missing or odd input");
	}

	@Test
	void shouldCarryTheRulesThatAddressTheReportedFailuresTest() {
		// Given: the shared rules block

		// When-Then: it forbids the specific failures account managers reported
		assertThat(NarrativeGrounding.RULES).contains(
				"invalid traffic (IVT)",
				"One creative in the data means no creative comparison",
				"Never claim an optimisation or action that the change log does not record",
				"lead with what the campaign achieved",
				"Never recommend pausing, removing or excluding a whole tactic, channel, device or market",
				"no click-based advice for CTV");
	}
}
