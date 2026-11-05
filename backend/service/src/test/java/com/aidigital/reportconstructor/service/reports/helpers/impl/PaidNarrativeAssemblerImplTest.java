package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.PaidCopyField;
import com.aidigital.reportconstructor.service.reports.dto.PaidNarrativeInput;
import com.aidigital.reportconstructor.service.reports.engine.PaidValueFormatter;
import com.aidigital.reportconstructor.service.reports.enums.PaidChannelKind;
import com.aidigital.reportconstructor.service.reports.enums.PaidExportTab;
import com.aidigital.reportconstructor.service.reports.model.PaidChannelData;
import com.aidigital.reportconstructor.service.reports.model.PaidMetrics;
import com.aidigital.reportconstructor.service.reports.model.PaidRow;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaidNarrativeAssemblerImplTest {

	@Test
	void shouldListEveryEocSearchCopyTokenTest() {
		// Given: an EOC deck's Paid Search section
		PaidNarrativeAssemblerImpl assembler = new PaidNarrativeAssemblerImpl(new PaidValueFormatter());

		// When:
		List<PaidCopyField> fields = assembler.fields(PaidChannelKind.SEARCH, false);

		// Then: single, numbered and titled slots are flattened to the deck's token names; EOM-only slots are left out
		assertThat(fields).extracting(PaidCopyField::key)
				.contains("verdict", "camp_takeaway_1", "camp_takeaway_3", "camp_reco", "grp_callout_2",
						"kw_takeaway_3", "pmax_take_1_title", "pmax_take_4", "aud_takeaway_1", "aud_reco",
						"highlight_4_title", "highlight_4", "reco_1_title", "reco_4")
				.doesNotContain("eom_worked", "verdict_1", "camp_reco_1");
		assertThat(fields).filteredOn(f -> f.key().equals("pmax_take_1_title"))
				.extracting(PaidCopyField::limit).containsExactly(24);
	}

	@Test
	void shouldListTheEomSocialCopyTokensWithoutTheEocOnlySlidesTest() {
		// Given: an EOM deck's Paid Social section
		PaidNarrativeAssemblerImpl assembler = new PaidNarrativeAssemblerImpl(new PaidValueFormatter());

		// When:
		List<PaidCopyField> fields = assembler.fields(PaidChannelKind.SOCIAL, true);

		// Then:
		assertThat(fields).extracting(PaidCopyField::key)
				.contains("verdict", "cr_reco", "aud_take_4_title", "pl_summary", "eom_worked", "eom_top_creative")
				.doesNotContain("highlight_1", "reco_1_title");
	}

	@Test
	void shouldRenderTheExportFiguresAsPromptLinesTest() {
		// Given: a Search export with one campaign type, one keyword and a missing PMax tab
		PaidNarrativeAssemblerImpl assembler = new PaidNarrativeAssemblerImpl(new PaidValueFormatter());
		PaidChannelData data = new PaidChannelData(PaidChannelKind.SEARCH, new PaidMetrics(104_000, 1_200, 1_000, 100),
				null, List.of(), List.of(new PaidRow("Brand", "", new PaidMetrics(1_000, 100, 500, 40))), List.of(),
				List.of(), List.of(new PaidRow("velmora sale", "Brand", new PaidMetrics(500, 50, 100, 20))),
				List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(PaidExportTab.PMAX_PLACEMENT_BREAKDOWN));

		// When:
		PaidNarrativeInput input = assembler.toInput(data, false, "the full flight");

		// Then: totals first, rows named with their parent, and the missing tab called out
		assertThat(input.channelName()).isEqualTo("Paid Search (Google Ads)");
		assertThat(input.periodLabel()).isEqualTo("the full flight");
		assertThat(input.facts().getFirst()).isEqualTo("TOTAL: impressions 104K, clicks 1.2K, CTR 1.2%, CPC $0.83, "
				+ "conversions 100, CPA $10.00, CR 8.3%, spend $1.0K");
		assertThat(input.facts()).anyMatch(l -> l.startsWith("CAMPAIGN TYPE Brand: "))
				.anyMatch(l -> l.startsWith("KEYWORD velmora sale (Brand): "))
				.anyMatch(l -> l.startsWith("MISSING FROM THE EXPORT") && l.contains("PMAX PLACEMENT BREAKDOWN"));
		assertThat(input.fields()).isEqualTo(assembler.fields(PaidChannelKind.SEARCH, false));
	}

	@Test
	void shouldDashEveryCopyTokenTheReplyLeftOutTest() {
		// Given: a reply with the verdict and one blank field
		PaidNarrativeAssemblerImpl assembler = new PaidNarrativeAssemblerImpl(new PaidValueFormatter());
		Map<String, String> flat = new HashMap<>();
		Map<String, String> copy = Map.of("verdict", "Meta Ads delivered 300 conversions.", "pl_summary", " ");

		// When:
		assembler.write(flat, PaidChannelKind.SOCIAL, false, copy);

		// Then:
		assertThat(flat)
				.containsEntry("{{pso_verdict}}", "Meta Ads delivered 300 conversions.")
				.containsEntry("{{pso_pl_summary}}", "—")
				.containsEntry("{{pso_aud_take_1_title}}", "—")
				.containsEntry("{{pso_reco_4}}", "—")
				.hasSize(assembler.fields(PaidChannelKind.SOCIAL, false).size());
	}

	@Test
	void shouldDropTheSlideLabelAnEomTakeawayRepeatsTest() {
		// Given: EOM replies that start with the label the metric slide already prints above each box
		PaidNarrativeAssemblerImpl assembler = new PaidNarrativeAssemblerImpl(new PaidValueFormatter());
		Map<String, String> flat = new HashMap<>();
		Map<String, String> copy = Map.of(
				"eom_worked", "What worked: August delivered 50 conversions at a $30.01 CPA.",
				"eom_top_creative", "Top Creative — Aviation - Carousel earned 38 conversions.",
				"eom_optimise", "October's $450.02 CPA; cut Auto - Single Post.",
				"eom_next_month", "next month: scale Retargeting.");

		// When:
		assembler.write(flat, PaidChannelKind.SOCIAL, true, copy);

		// Then: the label goes, the rest is capitalised, and copy without the label is left alone
		assertThat(flat)
				.containsEntry("{{pso_eom_worked}}", "August delivered 50 conversions at a $30.01 CPA.")
				.containsEntry("{{pso_eom_top_creative}}", "Aviation - Carousel earned 38 conversions.")
				.containsEntry("{{pso_eom_optimise}}", "October's $450.02 CPA; cut Auto - Single Post.")
				.containsEntry("{{pso_eom_next_month}}", "Scale Retargeting.");
	}

	@Test
	void shouldTellThePromptNotToRepeatTheSlideLabelTest() {
		// Given:
		PaidNarrativeAssemblerImpl assembler = new PaidNarrativeAssemblerImpl(new PaidValueFormatter());

		// When:
		List<PaidCopyField> fields = assembler.fields(PaidChannelKind.SEARCH, true);

		// Then: the labelled EOM boxes name their label; a box without one gets no such rule
		assertThat(fields).filteredOn(f -> f.key().equals("eom_worked")).singleElement()
				.extracting(PaidCopyField::guidance).asString()
				.contains("already prints the label \"What worked\"");
		assertThat(fields).filteredOn(f -> f.key().equals("verdict")).singleElement()
				.extracting(PaidCopyField::guidance).asString()
				.doesNotContain("already prints the label");
	}
}
