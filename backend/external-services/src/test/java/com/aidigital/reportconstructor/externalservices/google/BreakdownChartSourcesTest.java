package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.TemplateVariant;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BreakdownChartSourcesTest {

	@Test
	void shouldUseTheMpoWorkbooksForADeckClonedFromTheMpoTemplateTest() {
		// Given: standard breakdown workbooks, and MPO workbooks for the age and segment series only
		BreakdownChartCatalog catalog = new BreakdownChartCatalog();
		catalog.setSourceSheetIds(Map.of("aud", "std_aud", "dev", "std_dev"));
		catalog.setChartIdInSheet(Map.of("aud", "11", "dev", "12"));
		MpoDeckProperties props = new MpoDeckProperties();
		props.setEocSlidesTemplateId("mpo-eoc");
		props.getBreakdownCharts().setSourceSheetIds(Map.of("aud", " mpo_aud ", "aud-seg", "mpo_seg"));
		props.getBreakdownCharts().setChartIdInSheet(Map.of("aud", "21", "aud-seg", "22"));
		BreakdownChartSources sources = new BreakdownChartSources(catalog, new MpoDeckPolicy(props));
		BreakdownChartRequest mpoEoc =
				new BreakdownChartRequest("deck", "Campaign", List.of(), null, TemplateVariant.MPO, "EOC");

		// When-Then: the MPO workbook and its own chart id win where configured; an unconfigured MPO series
		// keeps the standard workbook together with the standard chart id
		assertThat(sources.sourceSheetIdFor(mpoEoc, "aud")).isEqualTo("mpo_aud");
		assertThat(sources.chartIdInSheetFor(mpoEoc, "aud")).isEqualTo("21");
		assertThat(sources.sourceSheetIdFor(mpoEoc, "aud-seg")).isEqualTo("mpo_seg");
		assertThat(sources.chartIdInSheetFor(mpoEoc, "aud-seg")).isEqualTo("22");
		assertThat(sources.sourceSheetIdFor(mpoEoc, "dev")).isEqualTo("std_dev");
		assertThat(sources.chartIdInSheetFor(mpoEoc, "dev")).isEqualTo("12");
	}

	@Test
	void shouldKeepTheStandardWorkbooksForAStandardDeckTest() {
		// Given: MPO breakdown workbooks are configured, but the deck is on the standard design
		BreakdownChartCatalog catalog = new BreakdownChartCatalog();
		catalog.setSourceSheetIds(Map.of("aud", "std_aud"));
		catalog.setChartIdInSheet(Map.of("aud", "11"));
		MpoDeckProperties props = new MpoDeckProperties();
		props.setEocSlidesTemplateId("mpo-eoc");
		props.getBreakdownCharts().setSourceSheetIds(Map.of("aud", "mpo_aud", "aud-seg", "mpo_seg"));
		props.getBreakdownCharts().setChartIdInSheet(Map.of("aud", "21", "aud-seg", "22"));
		BreakdownChartSources sources = new BreakdownChartSources(catalog, new MpoDeckPolicy(props));
		BreakdownChartRequest standard =
				new BreakdownChartRequest("deck", "Campaign", List.of(), null, TemplateVariant.STANDARD, "EOC");

		// When-Then: the MPO settings cannot reach a standard deck, so a standard deck still has no segment chart
		assertThat(sources.sourceSheetIdFor(standard, "aud")).isEqualTo("std_aud");
		assertThat(sources.chartIdInSheetFor(standard, "aud")).isEqualTo("11");
		assertThat(sources.sourceSheetIdFor(standard, "aud-seg")).isNull();
	}
}
