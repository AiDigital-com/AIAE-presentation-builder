package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.TemplateVariant;
import com.aidigital.reportconstructor.service.reports.ports.ChartRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TacticChartSourcesTest {

	@Test
	void shouldUseTheMpoWorkbooksForADeckClonedFromTheMpoTemplateTest() {
		// Given: a standard catalog with per-slot workbooks, and an MPO template with its own pacing workbooks
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setChartIdInSheet(111);
		templates.setDailyTemplateSheetIds(Map.of(1, "legacy_daily_1", 2, "legacy_daily_2"));
		templates.setMonthlyTemplateSheetIds(Map.of(1, "legacy_monthly_1"));
		MpoDeckProperties props = new MpoDeckProperties();
		props.setEocSlidesTemplateId("mpo-eoc");
		props.getTacticCharts().setDailyTemplateSheetId(" mpo_daily ");
		props.getTacticCharts().setDailyChartIdInSheet(222);
		props.getTacticCharts().setMonthlyTemplateSheetId("mpo_monthly");
		props.getTacticCharts().setDistTemplateSheetId("mpo_dist");
		props.getTacticCharts().setDistChartIdInSheet(333);
		TacticChartSources sources = new TacticChartSources(templates, new MpoDeckPolicy(props));
		ChartRequest mpoEoc = new ChartRequest("deck", List.of(), List.of(), null, 2, "Campaign", Map.of(),
				Map.of(), 0.0, Map.of(), null, null, null, TemplateVariant.MPO, "EOC");

		// When:
		String dailyTactic1 = sources.dailyTemplateFor(mpoEoc, 1);
		String dailyTactic2 = sources.dailyTemplateFor(mpoEoc, 2);
		String monthlyTactic2 = sources.monthlyTemplateFor(mpoEoc, 2);
		String distTactic2 = sources.distTemplateFor(mpoEoc, 2);
		int dailyChartId = sources.dailyChartId(mpoEoc);
		int monthlyChartId = sources.monthlyChartId(mpoEoc);
		int distChartId = sources.distChartId(mpoEoc);

		// Then: every tactic copy is keyed by the one workbook its master links to; an unset MPO chart id
		// falls back to the standard one
		assertThat(dailyTactic1).isEqualTo("mpo_daily");
		assertThat(dailyTactic2).isEqualTo("mpo_daily");
		assertThat(monthlyTactic2).isEqualTo("mpo_monthly");
		assertThat(distTactic2).isEqualTo("mpo_dist");
		assertThat(dailyChartId).isEqualTo(222);
		assertThat(monthlyChartId).isEqualTo(111);
		assertThat(distChartId).isEqualTo(333);
	}

	@Test
	void shouldKeepTheStandardCatalogForAStandardDeckTest() {
		// Given: MPO workbooks are configured, but the deck is on the standard design
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setChartIdInSheet(111);
		templates.setDailyTemplateSheetIds(Map.of(1, "legacy_daily_1"));
		templates.setMonthlyTemplateSheetIds(Map.of(1, "legacy_monthly_1"));
		templates.setDistTemplateSheetIds(Map.of(1, "legacy_dist_1"));
		MpoDeckProperties props = new MpoDeckProperties();
		props.setEocSlidesTemplateId("mpo-eoc");
		props.getTacticCharts().setDistTemplateSheetId("mpo_dist");
		props.getTacticCharts().setDailyTemplateSheetId("mpo_daily");
		props.getTacticCharts().setDailyChartIdInSheet(222);
		props.getTacticCharts().setMonthlyTemplateSheetId("mpo_monthly");
		TacticChartSources sources = new TacticChartSources(templates, new MpoDeckPolicy(props));
		ChartRequest standard = new ChartRequest("deck", List.of(), List.of(), null, 1, "Campaign", Map.of(),
				Map.of(), 0.0, Map.of(), null, null, null, TemplateVariant.STANDARD, "EOC");

		// When-Then: the MPO settings cannot reach a standard deck
		assertThat(sources.dailyTemplateFor(standard, 1)).isEqualTo("legacy_daily_1");
		assertThat(sources.monthlyTemplateFor(standard, 1)).isEqualTo("legacy_monthly_1");
		assertThat(sources.distTemplateFor(standard, 1)).isEqualTo("legacy_dist_1");
		assertThat(sources.dailyChartId(standard)).isEqualTo(111);
	}

	@Test
	void shouldFallBackToTheStandardCatalogWhenTheMpoWorkbookIsBlankTest() {
		// Given: an MPO deck whose MPO pacing workbooks were left unset
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setChartIdInSheet(111);
		templates.setDailyTemplateSheetId("book_daily");
		MpoDeckProperties props = new MpoDeckProperties();
		props.setEocSlidesTemplateId("mpo-eoc");
		props.getTacticCharts().setDailyChartIdInSheet(222);
		TacticChartSources sources = new TacticChartSources(templates, new MpoDeckPolicy(props));
		ChartRequest mpoEoc = new ChartRequest("deck", List.of(), List.of(), null, 1, "Campaign", Map.of(),
				Map.of(), 0.0, Map.of(), null, null, null, TemplateVariant.MPO, "EOC");

		// When-Then: the standard workbook and chart id answer, the orphaned MPO chart id is ignored
		assertThat(sources.dailyTemplateFor(mpoEoc, 1)).isEqualTo("book_daily");
		assertThat(sources.dailyChartId(mpoEoc)).isEqualTo(111);
	}
}
