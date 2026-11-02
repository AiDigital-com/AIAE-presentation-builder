package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.MpoFunnelStage;
import com.aidigital.reportconstructor.service.reports.dto.MpoNarrative;
import com.aidigital.reportconstructor.service.reports.dto.MpoNarrativeInput;
import com.aidigital.reportconstructor.service.reports.dto.MpoProposalPoint;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MpoNarrativeAssemblerImplTest {

	@Test
	void shouldBuildTheInputFromTheFiguresTheDeckPrintsTest() {
		// Given: a resolved map with campaign figures, strategy copy and one tactic, plus values that carry nothing
		MpoNarrativeAssemblerImpl assembler = new MpoNarrativeAssemblerImpl();
		Map<String, String> flat = new HashMap<>();
		flat.put("{{client_name}}", "Azure Cove");
		flat.put("{{total imps}}", "2,093,156");
		flat.put("{{total vcr}}", "—");
		flat.put("{{audience_segments}}", "{{audience_segments}}");
		flat.put("{{Strategic point 1}}", "Drive Traffic");
		flat.put("{{Strategic overview 1}}", "Fuel the booking funnel.");
		flat.put("{{recommendation 2 text}}", "Rotate new units.");
		flat.put("{{tactic 1}}", "CTV");
		flat.put("{{tactic 1 goal}}", "Awareness");
		flat.put("{{tactic 1 imps}}", "1.06M");
		flat.put("{{tactic 1 KPI type}}", "VCR");
		flat.put("{{tactic 1 KPI}}", "98.23%");

		// When:
		MpoNarrativeInput input = assembler.toInput(flat, 2);

		// Then: present values are quoted with their labels; a dash or a still-raw token is left out
		assertThat(input.campaignFacts()).containsExactly(
				"Client: Azure Cove",
				"Total impressions: 2,093,156",
				"Strategic insight 1: Drive Traffic: Fuel the booking funnel.",
				"Recommendation 2: Rotate new units.");
		// And: one line per tactic with figures; tactic 2 has none and is skipped
		assertThat(input.tacticFacts()).containsExactly(
				"Tactic 1 — CTV: funnel goal Awareness; impressions 1.06M; KPI VCR 98.23%");
	}

	@Test
	void shouldWriteTheReplyAndDashWhatItLeftEmptyTest() {
		// Given: a reply with one proposal card and one funnel stage, the second stage missing its story
		MpoNarrativeAssemblerImpl assembler = new MpoNarrativeAssemblerImpl();
		Map<String, String> flat = new HashMap<>();
		MpoNarrative narrative = new MpoNarrative(null,
				List.of(new MpoProposalPoint("Long-Lead Awareness", "Stay first choice for travel planning.")),
				List.of(
						new MpoFunnelStage("THE DREAM", "98% VCR", "Build the universe.", "1.06M CTV impressions.",
								"The story was heard in full."),
						new MpoFunnelStage("THE CONSIDERATION", "Reach", "Capture the market.", "2.27M reach.", " ")),
				"TURNING CURIOSITY INTO BOOKINGS", List.of());

		// When:
		assembler.write(flat, narrative);

		// Then:
		assertThat(flat)
				.containsEntry("{{proposal point 1}}", "Long-Lead Awareness")
				.containsEntry("{{proposal overview 1}}", "Stay first choice for travel planning.")
				.containsEntry("{{funnel stage 2}}", "THE CONSIDERATION")
				.containsEntry("{{recommendations headline}}", "TURNING CURIOSITY INTO BOOKINGS");
		// And: no HHI stated, a blank story, a missing card and missing stages all read as "no figure"
		assertThat(flat)
				.containsEntry("{{audience_hhi}}", "—")
				.containsEntry("{{funnel story 2}}", "—")
				.containsEntry("{{proposal point 2}}", "—")
				.containsEntry("{{funnel title 4}}", "—");
	}

	@Test
	void shouldKeepAValueTheUserAlreadySuppliedTest() {
		// Given: an HHI the user typed into the reviewed workbook
		MpoNarrativeAssemblerImpl assembler = new MpoNarrativeAssemblerImpl();
		Map<String, String> flat = new HashMap<>(Map.of("{{audience_hhi}}", "$200K+"));
		MpoNarrative narrative = new MpoNarrative("$150K+", List.of(), List.of(), null, List.of());

		// When:
		assembler.write(flat, narrative);

		// Then: their wording outranks the generated line
		assertThat(flat).containsEntry("{{audience_hhi}}", "$200K+");
	}

	@Test
	void shouldReplaceTheResultsOverviewWithItsTightenedRewriteTest() {
		// Given: group 1 carries the standard-length overview, group 2 one the call did not rewrite
		MpoNarrativeAssemblerImpl assembler = new MpoNarrativeAssemblerImpl();
		Map<String, String> flat = new HashMap<>(Map.of(
				"{{Our results overview 1}}", "A long overview written for the standard template box.",
				"{{Our results overview 2}}", "Group two as generated."));
		MpoNarrative narrative = new MpoNarrative(null, List.of(), List.of(), null,
				java.util.Arrays.asList("Short overview.", null, null, null));

		// When:
		MpoNarrativeInput input = assembler.toInput(flat, 0);
		assembler.write(flat, narrative);

		// Then: both overviews were handed to the call, the rewrite wins, and an unrewritten one is kept
		assertThat(input.resultsOverviews()).containsExactly(
				"A long overview written for the standard template box.", "Group two as generated.", null, null);
		assertThat(flat)
				.containsEntry("{{Our results overview 1}}", "Short overview.")
				.containsEntry("{{Our results overview 2}}", "Group two as generated.");
	}

	@Test
	void shouldDashEverySlotWhenThereIsNoReplyTest() {
		// Given:
		MpoNarrativeAssemblerImpl assembler = new MpoNarrativeAssemblerImpl();
		Map<String, String> flat = new HashMap<>();

		// When:
		assembler.write(flat, null);

		// Then: 2 + 2×2 card tokens + 4×5 funnel tokens, none left raw
		assertThat(flat).hasSize(26).allSatisfy((token, value) -> assertThat(value).isEqualTo("—"));
	}
}
