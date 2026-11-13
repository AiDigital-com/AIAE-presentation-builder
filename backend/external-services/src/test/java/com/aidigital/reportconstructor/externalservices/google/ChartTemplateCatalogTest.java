package com.aidigital.reportconstructor.externalservices.google;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChartTemplateCatalogTest {

	@Test
	void shouldTrimSingleTemplateIdsAndNormalizeNullToBlankTest() {
		// Given:
		ChartTemplateCatalog catalog = new ChartTemplateCatalog();

		// When:
		catalog.setDailyTemplateSheetId("  daily-wb  ");
		catalog.setMonthlyTemplateSheetId(null);
		catalog.setDistTemplateSheetId(" dist-wb");

		// Then:
		assertThat(catalog.getDailyTemplateSheetId()).isEqualTo("daily-wb");
		assertThat(catalog.getMonthlyTemplateSheetId()).isEmpty();
		assertThat(catalog.getDistTemplateSheetId()).isEqualTo("dist-wb");
	}

	@Test
	void shouldNormalizeNullTemplateIdsForDailyAndDistToBlankTest() {
		// Given:
		ChartTemplateCatalog catalog = new ChartTemplateCatalog();
		catalog.setMonthlyTemplateSheetId(" monthly-wb ");

		// When:
		catalog.setDailyTemplateSheetId(null);
		catalog.setDistTemplateSheetId(null);

		// Then:
		assertThat(catalog.getDailyTemplateSheetId()).isEmpty();
		assertThat(catalog.getDistTemplateSheetId()).isEmpty();
		assertThat(catalog.getMonthlyTemplateSheetId()).isEqualTo("monthly-wb");
	}

	@Test
	void shouldPreferSingleWorkbookOverPerSlotMapTest() {
		// Given: both the master-model workbook and the legacy per-slot maps are configured
		ChartTemplateCatalog catalog = new ChartTemplateCatalog();
		catalog.setDailyTemplateSheetId("daily-wb");
		catalog.setMonthlyTemplateSheetId("monthly-wb");
		catalog.setDistTemplateSheetId("dist-wb");
		catalog.setDailyTemplateSheetIds(Map.of(2, "daily-slot-2"));
		catalog.setMonthlyTemplateSheetIds(Map.of(2, "monthly-slot-2"));
		catalog.setDistTemplateSheetIds(Map.of(2, "dist-slot-2"));

		// When:
		String daily = catalog.dailyTemplateFor(2);
		String monthly = catalog.monthlyTemplateFor(2);
		String dist = catalog.distTemplateFor(2);

		// Then:
		assertThat(daily).isEqualTo("daily-wb");
		assertThat(monthly).isEqualTo("monthly-wb");
		assertThat(dist).isEqualTo("dist-wb");
	}

	@Test
	void shouldFallBackToPerSlotMapWhenSingleWorkbookIsBlankTest() {
		// Given:
		ChartTemplateCatalog catalog = new ChartTemplateCatalog();
		catalog.setDailyTemplateSheetIds(Map.of(2, "daily-slot-2"));
		catalog.setMonthlyTemplateSheetIds(Map.of(2, "monthly-slot-2"));
		catalog.setDistTemplateSheetIds(Map.of(2, "dist-slot-2"));

		// When:
		String daily = catalog.dailyTemplateFor(2);
		String monthly = catalog.monthlyTemplateFor(2);
		String dist = catalog.distTemplateFor(2);
		String unconfigured = catalog.dailyTemplateFor(5);

		// Then:
		assertThat(daily).isEqualTo("daily-slot-2");
		assertThat(monthly).isEqualTo("monthly-slot-2");
		assertThat(dist).isEqualTo("dist-slot-2");
		assertThat(unconfigured).isNull();
	}

	@Test
	void shouldDefaultEveryNullMapToEmptyTest() {
		// Given:
		ChartTemplateCatalog catalog = new ChartTemplateCatalog();

		// When:
		catalog.setDailyTemplateSheetIds(null);
		catalog.setDailySlideObjectIds(null);
		catalog.setMonthlyTemplateSheetIds(null);
		catalog.setMonthlySlideObjectIds(null);
		catalog.setDistTemplateSheetIds(null);
		catalog.setDistSlideObjectIds(null);

		// Then:
		assertThat(catalog.getDailyTemplateSheetIds()).isEmpty();
		assertThat(catalog.getDailySlideObjectIds()).isEmpty();
		assertThat(catalog.getMonthlyTemplateSheetIds()).isEmpty();
		assertThat(catalog.getMonthlySlideObjectIds()).isEmpty();
		assertThat(catalog.getDistTemplateSheetIds()).isEmpty();
		assertThat(catalog.getDistSlideObjectIds()).isEmpty();
	}

	@Test
	void shouldKeepConfiguredMapsTest() {
		// Given:
		ChartTemplateCatalog catalog = new ChartTemplateCatalog();
		Map<Integer, String> dailySheets = Map.of(1, "daily-1");
		Map<Integer, String> dailySlides = Map.of(1, "slide-daily-1");
		Map<Integer, String> monthlySheets = Map.of(1, "monthly-1");
		Map<Integer, String> monthlySlides = Map.of(1, "slide-monthly-1");
		Map<Integer, String> distSheets = Map.of(1, "dist-1");
		Map<Integer, String> distSlides = Map.of(1, "slide-dist-1");

		// When:
		catalog.setDailyTemplateSheetIds(dailySheets);
		catalog.setDailySlideObjectIds(dailySlides);
		catalog.setMonthlyTemplateSheetIds(monthlySheets);
		catalog.setMonthlySlideObjectIds(monthlySlides);
		catalog.setDistTemplateSheetIds(distSheets);
		catalog.setDistSlideObjectIds(distSlides);

		// Then:
		assertThat(catalog.getDailyTemplateSheetIds()).isEqualTo(Map.of(1, "daily-1"));
		assertThat(catalog.getDailySlideObjectIds()).isEqualTo(Map.of(1, "slide-daily-1"));
		assertThat(catalog.getMonthlyTemplateSheetIds()).isEqualTo(Map.of(1, "monthly-1"));
		assertThat(catalog.getMonthlySlideObjectIds()).isEqualTo(Map.of(1, "slide-monthly-1"));
		assertThat(catalog.getDistTemplateSheetIds()).isEqualTo(Map.of(1, "dist-1"));
		assertThat(catalog.getDistSlideObjectIds()).isEqualTo(Map.of(1, "slide-dist-1"));
	}

	@Test
	void shouldUseSharedChartIdWhenNoPerTypeIdIsConfiguredTest() {
		// Given:
		ChartTemplateCatalog catalog = new ChartTemplateCatalog();
		catalog.setChartIdInSheet(1_234);

		// When:
		int daily = catalog.dailyChartId();
		int monthly = catalog.monthlyChartId();
		int dist = catalog.distChartId();

		// Then:
		assertThat(catalog.getChartIdInSheet()).isEqualTo(1_234);
		assertThat(catalog.getDailyChartIdInSheet()).isNull();
		assertThat(catalog.getMonthlyChartIdInSheet()).isNull();
		assertThat(catalog.getDistChartIdInSheet()).isNull();
		assertThat(daily).isEqualTo(1_234);
		assertThat(monthly).isEqualTo(1_234);
		assertThat(dist).isEqualTo(1_234);
	}

	@Test
	void shouldPreferPerTypeChartIdsOverSharedIdTest() {
		// Given:
		ChartTemplateCatalog catalog = new ChartTemplateCatalog();
		catalog.setChartIdInSheet(1_234);
		catalog.setDailyChartIdInSheet(11);
		catalog.setMonthlyChartIdInSheet(22);
		catalog.setDistChartIdInSheet(33);

		// When:
		int daily = catalog.dailyChartId();
		int monthly = catalog.monthlyChartId();
		int dist = catalog.distChartId();

		// Then:
		assertThat(daily).isEqualTo(11);
		assertThat(monthly).isEqualTo(22);
		assertThat(dist).isEqualTo(33);
		assertThat(catalog.getDailyChartIdInSheet()).isEqualTo(11);
		assertThat(catalog.getMonthlyChartIdInSheet()).isEqualTo(22);
		assertThat(catalog.getDistChartIdInSheet()).isEqualTo(33);
	}

	@Test
	void shouldUseBrandDefaultComboColorsWhenUnconfiguredTest() {
		// Given:
		ChartTemplateCatalog catalog = new ChartTemplateCatalog();

		// When:
		double[] column = catalog.comboColumnColorComponents();
		double[] line = catalog.comboLineColorComponents();

		// Then: #0009DB columns and #AFF23F line
		assertThat(catalog.getComboColumnColor()).isNull();
		assertThat(catalog.getComboLineColor()).isNull();
		assertThat(column).containsExactly(0.0, 0.035294, 0.858824);
		assertThat(line).containsExactly(0.686275, 0.949020, 0.247059);
	}

	@Test
	void shouldUseConfiguredComboColorsTest() {
		// Given:
		ChartTemplateCatalog catalog = new ChartTemplateCatalog();
		RgbColor columnColor = new RgbColor();
		columnColor.setRed(0.1);
		columnColor.setGreen(0.2);
		columnColor.setBlue(0.3);
		RgbColor lineColor = new RgbColor();
		lineColor.setRed(0.7);
		lineColor.setGreen(0.8);
		lineColor.setBlue(0.9);
		catalog.setComboColumnColor(columnColor);
		catalog.setComboLineColor(lineColor);

		// When:
		double[] column = catalog.comboColumnColorComponents();
		double[] line = catalog.comboLineColorComponents();

		// Then:
		assertThat(catalog.getComboColumnColor()).isSameAs(columnColor);
		assertThat(catalog.getComboLineColor()).isSameAs(lineColor);
		assertThat(column).containsExactly(0.1, 0.2, 0.3);
		assertThat(line).containsExactly(0.7, 0.8, 0.9);
	}
}
