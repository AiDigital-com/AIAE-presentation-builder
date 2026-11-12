package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.externalservices.google.BreakdownSlideNaming;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.CreativeRow;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownSelectionResolver;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contract between the service's creative-breakdown values and the Creative analysis master slide: every value
 * must be keyed by exactly the token a tactic's duplicated slide carries, as the Google deck code renumbers it.
 */
class CreativeBreakdownSlideTokenContractTest {

	@Test
	void shouldKeyEveryValueByTheTokenTheDuplicatedSlideCarriesTest() {
		// Given: the Creative analysis master's real tokens, and the token->value map the helper hands to
		// addBreakdownSlides for the same tactic. The two are written in different modules from the same
		// spec, so nothing but this test stops them drifting: a helper key that does not match the master's
		// renumbered token leaves the slide either blank or showing a raw token.
		BreakdownSlideNaming naming = new BreakdownSlideNaming();
		ReportSheetHelper sheetHelper = mock(ReportSheetHelper.class);
		BreakdownSelectionResolver resolver = mock(BreakdownSelectionResolver.class);
		CreativeBreakdownHelperImpl helper = new CreativeBreakdownHelperImpl(sheetHelper, resolver);

		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("ca")));
		Map<String, String> flatReplacements = Map.of("{{tactic 1}}", "Display", "{{tactic 1 KPI type}}", "CTR");
		when(resolver.resolve(selections)).thenReturn(Map.of(1,
				EnumSet.of(BreakdownType.CREATIVE)));
		when(sheetHelper.readCreativeTables("sheet", Set.of(1), "token")).thenReturn(Map.of(
				1, new CreativeTable("6", "11.04", "2.09", "CH-Ad-320-50-1B", List.of(
						new CreativeRow("CH-Ad-320-50-1B", "144,070", "1.77%", "", "$861.81")))));

		// The data read fills the table/stat tokens; the combined call's takeaways are written on top, exactly
		// as the orchestrator does — the union is the same map the old buildCreativeValues produced.
		var read = helper.readCreativeInputs("sheet", selections, flatReplacements, "token");
		Map<String, String> values = new LinkedHashMap<>(read.dataValues());
		helper.writeCreativeTakeaways(
				values, read.tactics(), read.inputs().keySet(),
				Map.of(1, List.of("t1", "t2", "t3", "t4")), flatReplacements);

		// When: every master token is renumbered exactly as buildBreakdownRequests renumbers it
		List<String> masterTokens = List.of(
				"{{tactic n}}", "{{tactic n KPI type}}",
				"{{cr_live_n}}", "{{cr_bKPI_n}}",
				"{{tactic n top creative name}}",
				"{{tactic n top creative name n.1}}", "{{tactic n.1 top creative imps}}",
				"{{tactic n.1 top creative ctr}}", "{{tactic n.1 top creative vcr}}",
				"{{tactic n.1 top creative spend}}",
				"{{tactic n top creative name n.5}}", "{{tactic n.5 top creative spend}}",
				"{{cr_takeaway_tactic n_1}}", "{{cr_takeaway_tactic n_2}}",
				"{{cr_takeaway_tactic n_3}}", "{{cr_takeaway_tactic n_4}}");

		// Then: every one of them resolves to a key the helper actually produced, so none can fall through
		// to the renumber-only path and ship raw
		for (String token : masterTokens) {
			String concrete = naming.renumberToken(token, 1);
			assertThat(values)
					.as("master token %s renumbers to %s, which the helper must have a value for",
							token, concrete)
					.containsKey(concrete);
		}

		// Then: the takeaway tokens specifically carry Claude's copy rather than an empty string
		assertThat(values.get(naming.renumberToken("{{cr_takeaway_tactic n_1}}", 1))).isEqualTo("t1");
		assertThat(values.get(naming.renumberToken("{{cr_takeaway_tactic n_4}}", 1))).isEqualTo("t4");

		// Then: the stat tiles and the row carry the sheet's values
		assertThat(values.get(naming.renumberToken("{{cr_live_n}}", 1))).isEqualTo("6");
		assertThat(values.get(naming.renumberToken("{{tactic n.1 top creative imps}}", 1))).isEqualTo("144,070");
	}
}
