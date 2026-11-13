package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.externalservices.google.drive.DriveCopier;
import com.aidigital.reportconstructor.externalservices.google.drive.ChartFileSharer;
import com.aidigital.reportconstructor.observability.external.ExternalCallTimer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Size;
import com.aidigital.reportconstructor.externalservices.google.charts.model.TacticChartsRequest;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PacingPivot;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PacingSeries;
import com.aidigital.reportconstructor.externalservices.google.deck.model.TacticPacing;
import com.google.api.services.drive.Drive;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ChartSpec;
import com.google.api.services.slides.v1.Slides;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TacticChartBuilderTest {

	private TacticChartBuilder newBuilder(ChartTemplateCatalog templates) {
		return new TacticChartBuilder(
				Mockito.mock(ChartSheetWriter.class),
				Mockito.mock(ChartSpecBuilder.class),
				Mockito.mock(SlideChartSwapper.class),
				Mockito.mock(DriveCopier.class),
				Mockito.mock(ChartErrorTranslator.class),
				templates,
				Mockito.mock(ChartFileSharer.class),
				new TacticChartLocator(new BreakdownSlideNaming(), new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))));
	}

	@Test
	void resolveTarget_shouldUseTheScannedChartOnTheDuplicatedTacticSlideTest() {
		// Given: a master-model deck whose tactic 2 copy was scanned, and a catalog with one workbook per type
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setChartIdInSheet(555);
		templates.setDailyTemplateSheetId("book_daily");
		TacticChartBuilder builder = newBuilder(templates);
		ElementTransform xform = new ElementTransform(new Size(), new AffineTransform(), "tct_2");
		Map<Integer, Map<String, ChartElementRef>> scanned =
				Map.of(2, Map.of("book_daily", new ChartElementRef("el_daily_2", xform)));

		// When: resolving the daily target for tactic 2
		ChartTarget target = builder.resolveTarget(scanned, Map.of(), "legacy_daily_2", 2, "book_daily", 555);

		// Then: the scanned element wins over the configured legacy id, and brings its captured geometry
		assertThat(target.objectId()).isEqualTo("el_daily_2");
		assertThat(target.transform()).isSameAs(xform);
		assertThat(target.chartIdInSheet()).isEqualTo(555);
	}

	@Test
	void resolveTarget_shouldReturnNullWhenTheCopyCarriesNoChartLinkedToThatWorkbookTest() {
		// Given: a scanned tactic slide whose charts point at other workbooks
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setDistTemplateSheetId("book_dist");
		TacticChartBuilder builder = newBuilder(templates);
		Map<Integer, Map<String, ChartElementRef>> scanned = Map.of(
				1, Map.of("book_daily", new ChartElementRef("el_daily_1", null)));

		// When-Then: no target rather than a wrong one, so the caller reports the chart instead of
		// overwriting the pacing chart with a pie
		assertThat(builder.resolveTarget(scanned, Map.of(), "legacy_dist_1", 1, "book_dist", 1)).isNull();
	}

	@Test
	void resolveTarget_shouldFallBackToTheConfiguredSlotAndItsTransformOnALegacyDeckTest() {
		// Given: a legacy deck — nothing was scanned, so the placeholder ids come from configuration
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setChartIdInSheet(777);
		TacticChartBuilder builder = newBuilder(templates);
		ElementTransform xform = new ElementTransform(new Size(), new AffineTransform(), "p7");
		Map<String, ElementTransform> transforms = Map.of("legacy_daily_1", xform);

		// When:
		ChartTarget target = builder.resolveTarget(Map.of(), transforms, "legacy_daily_1", 1, "book_daily", 777);

		// Then: the configured id is used with its transform from the deck-wide read
		assertThat(target.objectId()).isEqualTo("legacy_daily_1");
		assertThat(target.transform()).isSameAs(xform);
		assertThat(target.chartIdInSheet()).isEqualTo(777);

		// And: an unconfigured slot resolves to nothing
		assertThat(builder.resolveTarget(Map.of(), transforms, null, 1, "book_daily", 777)).isNull();
	}

	@Test
	void shouldReportAMonthlyPlaceholderOnlyWhileTheDeckStillCarriesOneTest() {
		// Given: a legacy deck whose monthly slots are configured, read before and after the EOM drop removed
		// the "Monthly Impressions Distribution" cards; and a master-model deck scanned with a monthly chart
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setMonthlySlideObjectIds(Map.of(1, "monthly_1", 2, "monthly_2"));
		templates.setMonthlyTemplateSheetId("book_monthly");
		TacticChartBuilder builder = newBuilder(templates);
		ElementTransform xform = new ElementTransform(new Size(), new AffineTransform(), "p7");
		Map<String, ElementTransform> eocDeck = Map.of("daily_1", xform, "monthly_2", xform);
		Map<String, ElementTransform> eomDeck = Map.of("daily_1", xform, "daily_2", xform);
		Map<Integer, Map<String, ChartElementRef>> scanned =
				Map.of(1, Map.of("book_monthly", new ChartElementRef("el_monthly_1", xform)));

		// When:
		boolean onEocDeck = builder.monthlyPlaceholderOnDeck(Map.of(), eocDeck, 2);
		boolean onEomDeck = builder.monthlyPlaceholderOnDeck(Map.of(), eomDeck, 2);
		boolean onScannedDeck = builder.monthlyPlaceholderOnDeck(scanned, Map.of(), 2);

		// Then: one surviving placeholder keeps the pass running; a deck with none skips it
		assertThat(onEocDeck).isTrue();
		assertThat(onEomDeck).isFalse();
		assertThat(onScannedDeck).isTrue();
	}

	@Test
	void templateFor_shouldPreferTheSingleMasterWorkbookOverThePerSlotMapTest() {
		// Given: a catalog carrying both the master-model workbook and the legacy per-slot ids
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setDailyTemplateSheetIds(Map.of(1, "legacy_book_1"));
		templates.setMonthlyTemplateSheetIds(Map.of(1, "legacy_monthly_1"));
		templates.setMonthlyTemplateSheetId("book_monthly");

		// When-Then: the single workbook wins where configured, the per-slot map still answers elsewhere
		assertThat(templates.monthlyTemplateFor(1)).isEqualTo("book_monthly");
		assertThat(templates.monthlyTemplateFor(28)).isEqualTo("book_monthly");
		assertThat(templates.dailyTemplateFor(1)).isEqualTo("legacy_book_1");
		assertThat(templates.dailyTemplateFor(2)).isNull();
	}

	@Test
	void shouldReportMissingExportColumnsInsteadOfDrawingDailyChartsTest() {
		// Given: a BigQuery-sourced request whose export had no Date/Impressions column
		TacticChartBuilder builder = newBuilder(new ChartTemplateCatalog());
		TacticChartsRequest request = new TacticChartsRequest("deck-1", 2, "Acme", Map.of(), Map.of(), 0, Map.of(),
				null, new TacticPacing(false, Map.of(), new PacingSeries(false, Map.of()),
				new PacingSeries(false, Map.of())));

		// When: no Google client is needed, because the pass stops before touching the deck
		List<String> errors = builder.buildDailyCharts(null, request, null, Map.of());

		// Then:
		assertThat(errors).containsExactly("Daily: BQ sheet — Date or Impressions column not found");
	}

	@Test
	void shouldSkipATacticWhosePivotIsEmptyAndSayWhereItCameFromTest() {
		// Given: tactic 1's daily pivot came from the BigQuery export (empty for its line items) on a scanned
		// master-model deck, so no transform read is needed
		TacticChartBuilder builder = newBuilder(new ChartTemplateCatalog());
		PacingPivot empty = new PacingPivot(new LinkedHashMap<>(), false, false);
		TacticChartsRequest request = new TacticChartsRequest("deck-1", 1, "Acme", Map.of(), Map.of(), 0, Map.of(),
				null, new TacticPacing(true, Map.of(1, List.of("li-1", "li-2")),
				new PacingSeries(false, Map.of(1, empty)), new PacingSeries(false, Map.of())));

		// When:
		List<String> errors = builder.buildDailyCharts(null, request, null, Map.of(1, Map.of()));

		// Then: the message names the line items the export was filtered by
		assertThat(errors).containsExactly("Tactic 1: no pacing data (line item ids: li-1,li-2)");
	}

	@Test
	void shouldDrawAndSwapAllThreeChartsOfATacticOnAMasterModelDeckTest() throws IOException {
		// Given: a master-model deck whose tactic 1 copy links one chart to each of the three source workbooks
		ChartSheetWriter writer = mock(ChartSheetWriter.class);
		ChartSpecBuilder specs = mock(ChartSpecBuilder.class);
		SlideChartSwapper swapper = mock(SlideChartSwapper.class);
		DriveCopier driveCopier = mock(DriveCopier.class);
		ChartFileSharer sharer = mock(ChartFileSharer.class);
		TacticChartLocator locator = mock(TacticChartLocator.class);
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setChartIdInSheet(555);
		templates.setDistChartIdInSheet(777);
		templates.setDailyTemplateSheetId("book_daily");
		templates.setMonthlyTemplateSheetId("book_monthly");
		templates.setDistTemplateSheetId("book_dist");
		TacticChartBuilder builder = new TacticChartBuilder(writer, specs, swapper, driveCopier,
				new ChartErrorTranslator(), templates, sharer, locator);
		Drive drive = mock(Drive.class);
		Sheets sheets = mock(Sheets.class);
		Slides slides = mock(Slides.class);
		ElementTransform dailyAt = new ElementTransform(new Size(), new AffineTransform(), "tct_1");
		ElementTransform monthlyAt = new ElementTransform(new Size(), new AffineTransform(), "tct_1");
		ElementTransform distAt = new ElementTransform(new Size(), new AffineTransform(), "tct_1");
		when(locator.load(same(slides), eq("deck-1"), eq(1), eq(List.of()))).thenReturn(Map.of(1, Map.of(
				"book_daily", new ChartElementRef("el_daily", dailyAt),
				"book_monthly", new ChartElementRef("el_monthly", monthlyAt),
				"book_dist", new ChartElementRef("el_dist", distAt))));
		when(driveCopier.createFolder(same(drive), eq("Charts — Acme"))).thenReturn("folder-1");
		when(driveCopier.copyFile(same(drive), eq("book_daily"), eq("Chart Tactic 1 — Acme"), eq("folder-1")))
				.thenReturn("copy-d");
		when(driveCopier.copyFile(same(drive), eq("book_monthly"), eq("Monthly Chart Tactic 1 — Acme"),
				eq("folder-1"))).thenReturn("copy-m");
		when(driveCopier.copyFile(same(drive), eq("book_dist"), eq("Distribution Chart Tactic 1 — Acme"),
				eq("folder-1"))).thenReturn("copy-p");
		ChartSpec dailySpec = new ChartSpec().setTitle("daily");
		when(specs.readChartSpec(same(sheets), eq("book_daily"))).thenReturn(dailySpec);
		when(specs.findDataTab(same(sheets), eq("copy-d"))).thenReturn("Daily");
		when(specs.findDataTab(same(sheets), eq("copy-m"))).thenReturn("Monthly");
		when(specs.findDataTab(same(sheets), eq("copy-p"))).thenReturn("Dist");
		when(writer.sheetIdForTab(same(sheets), eq("copy-d"), eq("Daily"))).thenReturn(42);
		LinkedHashMap<String, double[]> rows = new LinkedHashMap<>();
		rows.put("2026-01-01", new double[] {100, 5});
		PacingPivot daily = new PacingPivot(rows, true, false);
		PacingPivot monthly = new PacingPivot(rows, false, false);
		TacticChartsRequest request = new TacticChartsRequest("deck-1", 1, "Acme", Map.of(1, "Display"),
				Map.of(1, 300.0), 1000.0, Map.of(1, "CTR"), null,
				new TacticPacing(false, Map.of(), new PacingSeries(true, Map.of(1, daily)),
						new PacingSeries(true, Map.of(1, monthly))));

		// When:
		List<String> errors = builder.buildAllCharts(new ChartClients(drive, sheets, slides), request);

		// Then: every chart lands, each copy shared from the deck's folder
		assertThat(errors).isEmpty();
		verify(sharer).shareFolder(same(drive), eq("folder-1"));
		verify(sharer).shareLooseCopy(same(drive), eq("folder-1"), eq("copy-d"));
		verify(sharer).shareLooseCopy(same(drive), eq("folder-1"), eq("copy-m"));
		verify(sharer).shareLooseCopy(same(drive), eq("folder-1"), eq("copy-p"));

		// And: the daily pivot is written with the tactic's KPI and its spec re-applied with a rate series
		verify(writer).writePivot(same(sheets), eq("copy-d"), eq("Daily"), same(daily), eq("CTR"));
		verify(specs).injectComboSeries(same(dailySpec), eq(42), eq(true));
		verify(specs).applyChartSpec(same(sheets), eq("copy-d"), same(dailySpec));

		// And: the monthly template has no spec, so the pivot is written and the chart placed without a re-apply
		verify(writer).writePivot(same(sheets), eq("copy-m"), eq("Monthly"), same(monthly), eq("CTR"));
		verify(writer, never()).sheetIdForTab(same(sheets), eq("copy-m"), eq("Monthly"));

		// And: the pie gets the tactic's share against the rest of the campaign
		verify(writer).writeDistribution(same(sheets), eq("copy-p"), eq("Dist"), eq("Display"), eq(300.0),
				eq(700.0));

		// And: each scanned chart element is swapped with the right copy and in-sheet chart id, without any
		// deck-wide transform read
		verify(swapper).replaceChartOnSlide(same(slides), eq("deck-1"), eq("el_daily"), eq("copy-d"),
				same(dailyAt), eq(555));
		verify(swapper).replaceChartOnSlide(same(slides), eq("deck-1"), eq("el_monthly"), eq("copy-m"),
				same(monthlyAt), eq(555));
		verify(swapper).replaceChartOnSlide(same(slides), eq("deck-1"), eq("el_dist"), eq("copy-p"),
				same(distAt), eq(777));
		verifyNoMoreInteractions(swapper);
	}

	@Test
	void shouldReportEveryUnplaceableChartOnALegacyDeckTest() throws IOException {
		// Given: a legacy deck (nothing scanned) and a folder that cannot be created; tactic 1 has a daily slot,
		// tactic 2 none; the monthly slot of tactic 1 has no workbook; no distribution slot is configured and only
		// tactic 1 has a distribution workbook
		ChartSheetWriter writer = mock(ChartSheetWriter.class);
		ChartSpecBuilder specs = mock(ChartSpecBuilder.class);
		SlideChartSwapper swapper = mock(SlideChartSwapper.class);
		DriveCopier driveCopier = mock(DriveCopier.class);
		ChartFileSharer sharer = mock(ChartFileSharer.class);
		TacticChartLocator locator = mock(TacticChartLocator.class);
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setChartIdInSheet(555);
		templates.setDailyTemplateSheetIds(Map.of(1, "book_d1", 2, "book_d2"));
		templates.setDailySlideObjectIds(Map.of(1, "leg_d1"));
		templates.setMonthlySlideObjectIds(Map.of(1, "leg_m1"));
		templates.setDistTemplateSheetIds(Map.of(1, "book_p1"));
		TacticChartBuilder builder = new TacticChartBuilder(writer, specs, swapper, driveCopier,
				new ChartErrorTranslator(), templates, sharer, locator);
		Drive drive = mock(Drive.class);
		Sheets sheets = mock(Sheets.class);
		Slides slides = mock(Slides.class);
		when(locator.load(same(slides), eq("deck-1"), eq(2), eq(List.of()))).thenReturn(Map.of());
		when(driveCopier.createFolder(same(drive), eq("Charts — Acme"))).thenThrow(new IOException("no quota"));
		ElementTransform dailyAt = new ElementTransform(new Size(), new AffineTransform(), "p3");
		Map<String, ElementTransform> deckLayout =
				Map.of("leg_d1", dailyAt, "leg_m1", new ElementTransform(new Size(), new AffineTransform(), "p4"));
		when(swapper.loadTransforms(same(slides), eq("deck-1"), eq(List.of()), eq("Daily"))).thenReturn(deckLayout);
		when(swapper.loadTransforms(same(slides), eq("deck-1"), eq(List.of()), eq("Monthly"))).thenReturn(deckLayout);
		when(swapper.loadTransforms(same(slides), eq("deck-1"), eq(List.of()), eq("Distribution")))
				.thenReturn(deckLayout);
		when(driveCopier.copyFile(same(drive), eq("book_d1"), eq("Chart Tactic 1 — Acme"), isNull()))
				.thenReturn("copy-1");
		ChartSpec spec = new ChartSpec().setTitle("daily");
		when(specs.readChartSpec(same(sheets), eq("book_d1"))).thenReturn(spec);
		when(specs.findDataTab(same(sheets), eq("copy-1"))).thenReturn("Sheet1");
		when(writer.sheetIdForTab(same(sheets), eq("copy-1"), eq("Sheet1"))).thenThrow(new IOException("gone"));
		LinkedHashMap<String, double[]> rows = new LinkedHashMap<>();
		rows.put("2026-01-01", new double[] {100, 5});
		PacingPivot pivot = new PacingPivot(rows, false, true);
		TacticChartsRequest request = new TacticChartsRequest("deck-1", 2, "Acme", Map.of(), Map.of(), 0, null,
				null, new TacticPacing(true, Map.of(2, List.of("li-9")),
						new PacingSeries(false, Map.of(1, pivot, 2, pivot)),
						new PacingSeries(false, Map.of(1, pivot))));

		// When:
		List<String> errors = builder.buildAllCharts(new ChartClients(drive, sheets, slides), request);

		// Then: each chart that cannot be placed is reported, in pass order
		assertThat(errors).containsExactly(
				"Tactic 2: no slide chart placeholder found (neither a configured object id nor a chart linked to "
						+ "book_d2 on the tactic slide)",
				"Monthly Tactic 1: no chart-template spreadsheet id configured",
				"Monthly Tactic 2: no data (line item ids: li-9)",
				"Distribution Tactic 1: no slide chart placeholder found (neither a configured object id nor a "
						+ "chart linked to book_p1 on the tactic slide)",
				"Distribution Tactic 2: no chart-template spreadsheet id configured");

		// And: the copies go to the root, and a failed spec re-apply still places the daily chart at its slot
		verify(sharer).shareFolder(same(drive), isNull());
		verify(writer).writePivot(same(sheets), eq("copy-1"), eq("Sheet1"), same(pivot), isNull());
		verify(specs, never()).applyChartSpec(same(sheets), eq("copy-1"), same(spec));
		verify(swapper).replaceChartOnSlide(same(slides), eq("deck-1"), eq("leg_d1"), eq("copy-1"), same(dailyAt),
				eq(555));
	}

	@Test
	void shouldSkipTheMonthlyPassWhenTheDeckCarriesNoMonthlyPlaceholderTest() {
		// Given: a legacy deck whose configured monthly slot was dropped from the deck
		SlideChartSwapper swapper = mock(SlideChartSwapper.class);
		DriveCopier driveCopier = mock(DriveCopier.class);
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setMonthlySlideObjectIds(Map.of(1, "leg_m1"));
		TacticChartBuilder builder = new TacticChartBuilder(mock(ChartSheetWriter.class), mock(ChartSpecBuilder.class),
				swapper, driveCopier, new ChartErrorTranslator(), templates, mock(ChartFileSharer.class),
				mock(TacticChartLocator.class));
		Slides slides = mock(Slides.class);
		when(swapper.loadTransforms(same(slides), eq("deck-1"), eq(List.of()), eq("Monthly")))
				.thenReturn(Map.of("leg_d1", new ElementTransform(new Size(), new AffineTransform(), "p3")));
		TacticChartsRequest request = new TacticChartsRequest("deck-1", 1, "Acme", Map.of(), Map.of(), 0, null,
				null, new TacticPacing(false, Map.of(), new PacingSeries(false, Map.of()),
				new PacingSeries(false, Map.of())));

		// When:
		List<String> errors = builder.buildMonthlyCharts(new ChartClients(null, null, slides), request, null, Map.of());

		// Then: no BigQuery complaint and no copy for a block removed on purpose
		assertThat(errors).isEmpty();
		verifyNoInteractions(driveCopier);
	}

	@Test
	void shouldReportMissingExportColumnsForTheMonthlyPassTest() {
		// Given: a master-model deck with a monthly chart, fed by a BigQuery export lacking Date/Impressions
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setMonthlyTemplateSheetId("book_monthly");
		TacticChartBuilder builder = new TacticChartBuilder(mock(ChartSheetWriter.class), mock(ChartSpecBuilder.class),
				mock(SlideChartSwapper.class), mock(DriveCopier.class), new ChartErrorTranslator(), templates,
				mock(ChartFileSharer.class), mock(TacticChartLocator.class));
		TacticChartsRequest request = new TacticChartsRequest("deck-1", 1, "Acme", Map.of(), Map.of(), 0, null,
				null, new TacticPacing(false, Map.of(), new PacingSeries(false, Map.of()),
				new PacingSeries(false, Map.of())));
		Map<Integer, Map<String, ChartElementRef>> scanned =
				Map.of(1, Map.of("book_monthly", new ChartElementRef("el_m", null)));

		// When: no client is needed, the scanned deck needs no transform read
		List<String> errors = builder.buildMonthlyCharts(null, request, null, scanned);

		// Then:
		assertThat(errors).containsExactly("Monthly: BQ sheet — Date or Impressions column not found");
	}

	@Test
	void shouldCollectPerChartFailuresAndDefaultTheDistributionSliceTest() throws IOException {
		// Given: a scanned two-tactic deck reading pacing from the sheet; tactic 1's copies all fail, tactic 2
		// has no pacing data and no distribution name or impressions
		ChartSheetWriter writer = mock(ChartSheetWriter.class);
		ChartSpecBuilder specs = mock(ChartSpecBuilder.class);
		SlideChartSwapper swapper = mock(SlideChartSwapper.class);
		DriveCopier driveCopier = mock(DriveCopier.class);
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setChartIdInSheet(555);
		templates.setDailyTemplateSheetId("book_daily");
		templates.setMonthlyTemplateSheetId("book_monthly");
		templates.setDistTemplateSheetId("book_dist");
		TacticChartBuilder builder = new TacticChartBuilder(writer, specs, swapper, driveCopier,
				new ChartErrorTranslator(), templates, mock(ChartFileSharer.class), mock(TacticChartLocator.class));
		Drive drive = mock(Drive.class);
		Sheets sheets = mock(Sheets.class);
		Slides slides = mock(Slides.class);
		ElementTransform at = new ElementTransform(new Size(), new AffineTransform(), "tct_2");
		Map<Integer, Map<String, ChartElementRef>> scanned = Map.of(
				1, Map.of("book_daily", new ChartElementRef("el_d1", at), "book_monthly",
						new ChartElementRef("el_m1", at), "book_dist", new ChartElementRef("el_p1", at)),
				2, Map.of("book_monthly", new ChartElementRef("el_m2", at), "book_dist",
						new ChartElementRef("el_p2", at)));
		when(driveCopier.copyFile(same(drive), eq("book_daily"), eq("Chart Tactic 1 — Acme"), eq("folder-1")))
				.thenThrow(new IOException("copy refused"));
		when(driveCopier.copyFile(same(drive), eq("book_monthly"), eq("Monthly Chart Tactic 1 — Acme"),
				eq("folder-1"))).thenThrow(new IllegalStateException("bad monthly"));
		when(driveCopier.copyFile(same(drive), eq("book_dist"), eq("Distribution Chart Tactic 1 — Acme"),
				eq("folder-1"))).thenThrow(new IOException("dist refused"));
		when(driveCopier.copyFile(same(drive), eq("book_dist"), eq("Distribution Chart Tactic 2 — Acme"),
				eq("folder-1"))).thenReturn("copy-p2");
		when(specs.findDataTab(same(sheets), eq("copy-p2"))).thenReturn("Dist");
		LinkedHashMap<String, double[]> rows = new LinkedHashMap<>();
		rows.put("2026-01", new double[] {100});
		PacingPivot pivot = new PacingPivot(rows, false, false);
		TacticChartsRequest request = new TacticChartsRequest("deck-1", 2, "Acme", Map.of(1, "Video"),
				Map.of(1, 400.0), 1000.0, Map.of(), null,
				new TacticPacing(false, Map.of(), new PacingSeries(true, Map.of(1, pivot)),
						new PacingSeries(true, Map.of(1, pivot))));
		ChartClients clients = new ChartClients(drive, sheets, slides);

		// When:
		List<String> daily = builder.buildDailyCharts(clients, request, "folder-1", scanned);
		List<String> monthly = builder.buildMonthlyCharts(clients, request, "folder-1", scanned);
		List<String> dist = builder.buildDistributionCharts(clients, request, "folder-1", scanned);

		// Then: each failure is reported under its chart's tag and the remaining tactics still run
		assertThat(daily).containsExactly("Tactic 1: copy refused", "Tactic 2: no pacing data in sheet");
		assertThat(monthly).containsExactly("Monthly Tactic 1: bad monthly", "Monthly Tactic 2: no data in sheet");
		assertThat(dist).containsExactly("Distribution Tactic 1: dist refused");

		// And: tactic 2's pie falls back to its default name and zero own impressions, linking the shared
		// in-sheet chart id since no distribution-specific one is configured
		verify(writer).writeDistribution(same(sheets), eq("copy-p2"), eq("Dist"), eq("Tactic 2"), eq(0.0),
				eq(1000.0));
		verify(swapper).replaceChartOnSlide(same(slides), eq("deck-1"), eq("el_p2"), eq("copy-p2"), same(at),
				eq(555));
	}

	@Test
	void shouldFindNoMonthlyPlaceholderForUnconfiguredOrUnscannedTacticsTest() {
		// Given: a legacy catalog configuring only tactic 2's monthly slot, and a scan whose tactics carry only
		// daily charts
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setMonthlySlideObjectIds(Map.of(2, "monthly_2"));
		templates.setMonthlyTemplateSheetId("book_monthly");
		TacticChartBuilder builder = new TacticChartBuilder(mock(ChartSheetWriter.class), mock(ChartSpecBuilder.class),
				mock(SlideChartSwapper.class), mock(DriveCopier.class), new ChartErrorTranslator(), templates,
				mock(ChartFileSharer.class), mock(TacticChartLocator.class));
		ElementTransform xform = new ElementTransform(new Size(), new AffineTransform(), "p7");
		Map<Integer, Map<String, ChartElementRef>> scanned =
				Map.of(1, Map.of("book_daily", new ChartElementRef("el_daily_1", xform)));

		// When-Then: tactic 1 has no configured slot, and no scanned tactic links the monthly workbook
		assertThat(builder.monthlyPlaceholderOnDeck(Map.of(), Map.of("daily_1", xform), 1)).isFalse();
		assertThat(builder.monthlyPlaceholderOnDeck(scanned, Map.of(), 2)).isFalse();
	}

	@Test
	void shouldReapplyTheSpecWithoutARateSeriesForAnImpressionsOnlyPivotTest() throws IOException {
		// Given: a pivot with neither clicks nor completions and a template carrying a chart spec
		ChartSheetWriter writer = mock(ChartSheetWriter.class);
		ChartSpecBuilder specs = mock(ChartSpecBuilder.class);
		SlideChartSwapper swapper = mock(SlideChartSwapper.class);
		DriveCopier driveCopier = mock(DriveCopier.class);
		TacticChartBuilder builder = new TacticChartBuilder(writer, specs, swapper, driveCopier,
				new ChartErrorTranslator(), new ChartTemplateCatalog(), mock(ChartFileSharer.class),
				mock(TacticChartLocator.class));
		Drive drive = mock(Drive.class);
		Sheets sheets = mock(Sheets.class);
		Slides slides = mock(Slides.class);
		ChartSpec spec = new ChartSpec().setTitle("monthly");
		when(driveCopier.copyFile(same(drive), eq("book"), eq("copy name"), eq("folder-1"))).thenReturn("copy-1");
		when(specs.readChartSpec(same(sheets), eq("book"))).thenReturn(spec);
		when(specs.findDataTab(same(sheets), eq("copy-1"))).thenReturn("Data");
		when(writer.sheetIdForTab(same(sheets), eq("copy-1"), eq("Data"))).thenReturn(8);
		LinkedHashMap<String, double[]> rows = new LinkedHashMap<>();
		rows.put("2026-01", new double[] {100});
		PacingPivot pivot = new PacingPivot(rows, false, false);
		ElementTransform at = new ElementTransform(new Size(), new AffineTransform(), "p2");
		List<String> errors = new ArrayList<>();

		// When:
		builder.renderComboChart(new ChartClients(drive, sheets, slides), "deck-1", "folder-1", errors,
				new ComboChartJob("book", new ChartTarget("el_1", at, 31), "copy name", pivot, "Tactic 1", "VCR"));

		// Then:
		assertThat(errors).isEmpty();
		verify(writer).writePivot(same(sheets), eq("copy-1"), eq("Data"), same(pivot), eq("VCR"));
		verify(specs).injectComboSeries(same(spec), eq(8), eq(false));
		verify(specs).applyChartSpec(same(sheets), eq("copy-1"), same(spec));
		verify(swapper).replaceChartOnSlide(same(slides), eq("deck-1"), eq("el_1"), eq("copy-1"), same(at), eq(31));
	}

	@Test
	void shouldReportAComboChartWithoutTemplateOrTargetWithoutCopyingTest() throws IOException {
		// Given:
		DriveCopier driveCopier = mock(DriveCopier.class);
		TacticChartBuilder builder = new TacticChartBuilder(mock(ChartSheetWriter.class), mock(ChartSpecBuilder.class),
				mock(SlideChartSwapper.class), driveCopier, new ChartErrorTranslator(), new ChartTemplateCatalog(),
				mock(ChartFileSharer.class), mock(TacticChartLocator.class));
		PacingPivot pivot = new PacingPivot(new LinkedHashMap<>(), false, false);
		List<String> errors = new ArrayList<>();

		// When:
		builder.renderComboChart(null, "deck-1", null, errors,
				new ComboChartJob(null, new ChartTarget("el", null, 1), "copy", pivot, "Tactic 3", null));
		builder.renderComboChart(null, "deck-1", null, errors,
				new ComboChartJob("book", null, "copy", pivot, "Tactic 4", null));

		// Then:
		assertThat(errors).containsExactly(
				"Tactic 3: no chart-template spreadsheet id configured",
				"Tactic 4: no slide chart placeholder found (neither a configured object id nor a chart linked to "
						+ "book on the tactic slide)");
		verifyNoInteractions(driveCopier);
	}
}
