package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.ports.DeckMarkup;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeckStyleTokenHelperImplTest {

	private DeckStyleTokenHelperImpl newHelper() {
		return new DeckStyleTokenHelperImpl(new SheetRowHelperImpl());
	}

	@Test
	void shouldSplitTheAudienceSegmentsIntoOneChipEachTest() {
		// Given: two segments, the second itself a pair joined by an ampersand
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{audience_segments}}", "Luxury Travel, Honeymoons & Romantic Getaways.");

		// When:
		newHelper().fillDeckStyleTokens(flat, 0);

		// Then: one chip per segment, the ampersand kept inside its chip, the unused third chip hidden
		assertThat(flat).containsEntry("{{audience segment 1}}", "Luxury Travel")
				.containsEntry("{{audience segment 2}}", "Honeymoons & Romantic Getaways")
				.containsEntry("{{audience segment 3}}", DeckMarkup.HIDE_SHAPE);
	}

	@Test
	void shouldHideEverySegmentChipWithoutSegmentsTest() {
		// Given: the line resolved to a dash
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{audience_segments}}", "—");

		// When:
		newHelper().fillDeckStyleTokens(flat, 0);

		// Then:
		assertThat(flat).containsEntry("{{audience segment 1}}", DeckMarkup.HIDE_SHAPE)
				.containsEntry("{{audience segment 3}}", DeckMarkup.HIDE_SHAPE);
	}

	@Test
	void shouldHideTheThoughtsParagraphsThatHaveNoCopyTest() {
		// Given: two written thoughts, a third the sheet printed as a dash, a blank fourth and no fifth at all
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{thoughts on the performance 1}}", "CTV/OTT led pacing with a 98.24% VCR.");
		flat.put("{{thoughts on the performance 2}}", "Video and audio beat plan.");
		flat.put("{{thoughts on the performance 3}}", "—");
		flat.put("{{thoughts on the performance 4}}", " ");

		// When:
		newHelper().fillDeckStyleTokens(flat, 0);

		// Then: the written ones stay, every empty slot removes its box rather than showing a lone dash
		assertThat(flat).containsEntry("{{thoughts on the performance 1}}", "CTV/OTT led pacing with a 98.24% VCR.")
				.containsEntry("{{thoughts on the performance 2}}", "Video and audio beat plan.")
				.containsEntry("{{thoughts on the performance 3}}", DeckMarkup.HIDE_SHAPE)
				.containsEntry("{{thoughts on the performance 4}}", DeckMarkup.HIDE_SHAPE)
				.containsEntry("{{thoughts on the performance 5}}", DeckMarkup.HIDE_SHAPE);
	}

	@Test
	void campaignPeriod_shouldPairTwoQuartersOfOneYearTest() {
		// Given: the deck's own flight format, whose start omits the year
		String flight = "Jul 1 – Dec 31, 2025";

		// When:
		String period = newHelper().campaignPeriod(flight);

		// Then:
		assertThat(period).isEqualTo("Q3 & Q4 2025");
	}

	@Test
	void campaignPeriod_shouldNameASingleQuarterTest() {
		// Given:
		String flight = "Oct 1 – Dec 31, 2025";

		// When:
		String period = newHelper().campaignPeriod(flight);

		// Then:
		assertThat(period).isEqualTo("Q4 2025");
	}

	@Test
	void campaignPeriod_shouldWriteThreeQuartersAsARangeTest() {
		// Given:
		String flight = "Feb 1 – Sep 30, 2026";

		// When:
		String period = newHelper().campaignPeriod(flight);

		// Then:
		assertThat(period).isEqualTo("Q1 – Q3 2026");
	}

	@Test
	void campaignPeriod_shouldNameBothYearsWhenTheFlightCrossesNewYearTest() {
		// Given: a start that carries its own year
		String flight = "Nov 1, 2025 – Feb 28, 2026";

		// When:
		String period = newHelper().campaignPeriod(flight);

		// Then:
		assertThat(period).isEqualTo("Q4 2025 & Q1 2026");
	}

	@Test
	void campaignPeriod_shouldTakeThePreviousYearForAYearlessStartAfterTheEndTest() {
		// Given: "Dec 1" before an end in January can only be the year before
		String flight = "Dec 1 – Jan 31, 2026";

		// When:
		String period = newHelper().campaignPeriod(flight);

		// Then:
		assertThat(period).isEqualTo("Q4 2025 & Q1 2026");
	}

	@Test
	void campaignPeriod_shouldDashAnUnreadableFlightTest() {
		// Given:
		String flight = "TBD";

		// When:
		String period = newHelper().campaignPeriod(flight);

		// Then:
		assertThat(period).isEqualTo("—");
	}

	@Test
	void geoSummary_shouldCountTheMarketsOfAListTest() {
		// Given: the sample's market list, joined by commas and a closing ampersand
		String geo = "NYC, ATL, MIA, CLT, NJ, PHI, BOS, DAL & CHI.";

		// When:
		String summary = newHelper().geoSummary(geo);

		// Then:
		assertThat(summary).isEqualTo("9 priority markets");
	}

	@Test
	void geoSummary_shouldKeepASingleMarketAsWrittenTest() {
		// Given:
		String geo = "National";

		// When:
		String summary = newHelper().geoSummary(geo);

		// Then:
		assertThat(summary).isEqualTo("National");
	}

	@Test
	void fillDeckStyleTokens_shouldShowClicksForAClickLedTacticTest() {
		// Given:
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{tactic 1 KPI type}}", "CTR");
		flat.put("{{tactic 1 top creative clicks}}", "584");
		flat.put("{{tactic 1 top creative completions}}", "0");

		// When:
		newHelper().fillDeckStyleTokens(flat, 1);

		// Then:
		assertThat(flat.get("{{tactic 1 top creative kpi label}}")).isEqualTo("Clicks");
		assertThat(flat.get("{{tactic 1 top creative kpi}}")).isEqualTo("584");
	}

	@Test
	void fillDeckStyleTokens_shouldShowCompletedViewsForACompletionLedTacticTest() {
		// Given:
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{tactic 1 KPI type}}", "VCR");
		flat.put("{{tactic 1 top creative clicks}}", "1,012");
		flat.put("{{tactic 1 top creative completions}}", "457,614");

		// When:
		newHelper().fillDeckStyleTokens(flat, 1);

		// Then:
		assertThat(flat.get("{{tactic 1 top creative kpi label}}")).isEqualTo("Completed Views");
		assertThat(flat.get("{{tactic 1 top creative kpi}}")).isEqualTo("457,614");
	}

	@Test
	void fillDeckStyleTokens_shouldDashMissingCompletionsRatherThanFallBackToClicksTest() {
		// Given: a video tactic whose workbook carries no completed views for its top creative
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{tactic 1 KPI type}}", "VCR");
		flat.put("{{tactic 1 top creative clicks}}", "1,012");

		// When:
		newHelper().fillDeckStyleTokens(flat, 1);

		// Then:
		assertThat(flat.get("{{tactic 1 top creative kpi}}")).isEqualTo("—");
	}

	@Test
	void fillDeckStyleTokens_shouldKeepAValueTheUserEnteredTest() {
		// Given: a campaign period typed into the workbook, and the flight it would otherwise be derived from
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{campaign_period}}", "Holiday 2025");
		flat.put("{{flight_dates}}", "Oct 1 – Dec 31, 2025");
		flat.put("{{geo_locations}}", "NYC, ATL");

		// When:
		newHelper().fillDeckStyleTokens(flat, 0);

		// Then: the typed value wins; the untouched geo summary is derived
		assertThat(flat.get("{{campaign_period}}")).isEqualTo("Holiday 2025");
		assertThat(flat.get("{{geo_summary}}")).isEqualTo("2 priority markets");
	}
}
