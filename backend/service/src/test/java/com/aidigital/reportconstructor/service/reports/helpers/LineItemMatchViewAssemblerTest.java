package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchView;
import com.aidigital.reportconstructor.service.reports.dto.TacticMapping;
import com.aidigital.reportconstructor.service.reports.services.LineItemMeta;
import com.aidigital.reportconstructor.service.reports.services.MatchResult;
import com.aidigital.reportconstructor.service.reports.services.TacticSuggestion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LineItemMatchViewAssemblerTest {

	private final LineItemMatchViewAssembler assembler = new LineItemMatchViewAssembler();

	@Test
	void shouldIndexLineItemsGroupByChannelAndNumberTacticRowsTest() {
		// Given: two line items on one channel, one without a channel, and three tactics
		LineItemMeta a = new LineItemMeta("A1", "Display_Prospecting", "Display", "Prospecting");
		LineItemMeta b = new LineItemMeta("B2", "Display_Retargeting", "Display", "Retargeting");
		LineItemMeta c = new LineItemMeta("C3", "Unlabelled", " ", null);
		MatchResult result = new MatchResult(
				List.of(
						new TacticSuggestion("Prospecting", "A1", "AUTO"),
						new TacticSuggestion("Retargeting", "B2", "suggested"),
						new TacticSuggestion("CTV", " ", "none")),
				List.of(a, b, c),
				List.of("A1", "B2", "C3"));

		// When:
		LineItemMatchView view = assembler.assemble(result);

		// Then: metadata indexed by id, blank channels left out of the grouping
		assertThat(view.idNamings()).containsOnlyKeys("A1", "B2", "C3");
		assertThat(view.channelToIds()).containsOnlyKeys("Display");
		assertThat(view.channelToIds().get("Display")).containsExactly("A1", "B2");
		assertThat(view.uniqueIds()).containsExactly("A1", "B2", "C3");

		// Then: rows numbered from 1, plan position equal to the slot, auto matches counted case-insensitively
		assertThat(view.mapping()).containsExactly(
				new TacticMapping(1, 1, "Prospecting", "A1", "Display_Prospecting", true),
				new TacticMapping(2, 2, "Retargeting", "B2", "Display_Retargeting", false),
				new TacticMapping(3, 3, "CTV", null, null, false));
		assertThat(view.autoMatched()).isTrue();
		assertThat(view.autoMatchCount()).isEqualTo(1);
	}

	@Test
	void shouldReportNoAutoMatchWhenNoTacticWasMatchedConfidentlyTest() {
		// Given:
		MatchResult result = new MatchResult(
				List.of(new TacticSuggestion("CTV", null, "none")), List.of(), List.of());

		// When:
		LineItemMatchView view = assembler.assemble(result);

		// Then:
		assertThat(view.autoMatched()).isFalse();
		assertThat(view.autoMatchCount()).isZero();
		assertThat(view.mapping()).containsExactly(new TacticMapping(1, 1, "CTV", null, null, false));
	}
}
