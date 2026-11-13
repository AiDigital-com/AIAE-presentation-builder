package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.observability.external.ExternalCallTimer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PacingPivot;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PacingSeries;
import com.aidigital.reportconstructor.externalservices.google.deck.model.TacticPacing;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookPacingRequest;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SheetPacingTableWriterTest {

	private final SheetPacingTableWriter writer = new SheetPacingTableWriter(
			new ChartSheetWriter(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
			new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));

	@Test
	void shouldSkipEveryTableWhenTheExportLacksTheRequiredColumnsTest() throws Exception {
		// Given: the service found no Date/Impressions column, so it pivoted nothing
		WorkbookPacingRequest request = new WorkbookPacingRequest(2,
				new TacticPacing(false, Map.of(), new PacingSeries(false, Map.of()), new PacingSeries(false, Map.of())),
				Map.of(), Map.of(), 0, Map.of(), null);

		// When: no Sheets client is needed, because nothing is read or written
		List<String> errors = writer.writeAll(null, "wb-1", "Report", request);

		// Then:
		assertThat(errors).containsExactly("Pacing tables skipped — BQ sheet Date or Impressions column not found");
	}

	@Test
	void findNumberedAnchorsTest_locatesEachTacticsOwnAnchorCell() {
		// Given: a grid with two tactics' "Daily pacing N" anchors on the same row
		List<List<Object>> grid = gridOf(
				List.of("", "Daily pacing 1", "", "Daily pacing 2"),
				List.of("Date", "Impressions", "Date", "Impressions"));

		// When:
		Map<Integer, int[]> anchors = writer.findNumberedAnchors(grid, SheetPacingTableWriter.DAILY_ANCHOR);

		// Then:
		assertThat(anchors.get(1)).containsExactly(0, 1);
		assertThat(anchors.get(2)).containsExactly(0, 3);
	}

	@Test
	void findPacingColumnsTest_resolvesOnlyTheRequestedTacticsColumnsWhenTwoTacticsShareARow() {
		// Given: tactic 1's daily block sits left of tactic 2's, both on row 3 (0-based)
		List<List<Object>> grid = gridOf(
				List.of("Daily pacing 1", "", "", "", "Daily pacing 2"),
				List.of("Date", "Impressions", "", "Date", "Impressions"),
				List.of("Date", "Impressions", "", "Date", "Impressions"),
				List.of("{{tactic 1 date}}", "{{tactic 1 impressions}}", "{{tactic 1 amount}}",
						"{{tactic 2 date}}", "{{tactic 2 impressions}}"));
		int[] anchor1 = {0, 0};
		int[] anchor2 = {0, 4};

		// When:
		PacingColumns cols1 = writer.findPacingColumns(grid, anchor1, 1, false);
		PacingColumns cols2 = writer.findPacingColumns(grid, anchor2, 2, false);

		// Then: tactic 1 resolves to its own columns, unaffected by tactic 2's tokens on the same row
		assertThat(cols1.dateCol()).isEqualTo(0);
		assertThat(cols1.impsCol()).isEqualTo(1);
		assertThat(cols1.metricCol()).isEqualTo(2);
		assertThat(cols1.dataStartRow()).isEqualTo(3);
		assertThat(cols2.dateCol()).isEqualTo(3);
		assertThat(cols2.impsCol()).isEqualTo(4);
	}

	@Test
	void findPacingColumnsTest_monthlyTokensDoNotMatchPlainDailyPattern() {
		// Given: only a "date mon" token is present
		List<List<Object>> grid = gridOf(
				List.of("Monthly pacing 1"),
				List.of("{{tactic 1 date mon}}"));
		int[] anchor = {0, 0};

		// When:
		PacingColumns daily = writer.findPacingColumns(grid, anchor, 1, false);
		PacingColumns monthly = writer.findPacingColumns(grid, anchor, 1, true);

		// Then:
		assertThat(daily.dateCol()).isEqualTo(-1);
		assertThat(monthly.dateCol()).isEqualTo(0);
	}

	@Test
	void findDistributionColumnsTest_skipsTheSectionTitleAndFindsTheSliceRowBelowTheAnchor() {
		// Given: the same {{tactic 1}} token appears both as the section title (row 0) and the
		// distribution block's slice label (row 3), directly below the "Channel Distribution 1" anchor
		List<List<Object>> grid = gridOf(
				List.of("{{tactic 1}}"),
				List.of("Channel Distribution 1"),
				List.of("Distribution", "Impressions"),
				List.of("{{tactic 1}}"),
				List.of("Total"));
		int[] anchor = {1, 0};

		// When:
		DistributionColumns cols = writer.findDistributionColumns(grid, anchor, 1, "Display Prospecting");

		// Then:
		assertThat(cols.tacticRow()).isEqualTo(3);
		assertThat(cols.otherRow()).isEqualTo(4);
		assertThat(cols.labelCol()).isEqualTo(0);
	}

	@Test
	void findDistributionColumnsTest_matchesTheSliceRowByResolvedTacticNameWhenTheTokenWasAlreadyReplaced() {
		// Given: on the SHEET target the earlier find/replace pass already swapped {{tactic 1}}
		// for the tactic name, so the slice label cell now holds the resolved name, not the token
		List<List<Object>> grid = gridOf(
				List.of("Channel Distribution 1"),
				List.of("Distribution", "Impressions"),
				List.of("Display Prospecting"),
				List.of("Total"));
		int[] anchor = {0, 0};

		// When:
		DistributionColumns cols = writer.findDistributionColumns(grid, anchor, 1, "Display Prospecting");

		// Then:
		assertThat(cols.tacticRow()).isEqualTo(2);
		assertThat(cols.otherRow()).isEqualTo(3);
		assertThat(cols.labelCol()).isEqualTo(0);
	}

	@Test
	void collectDistributionBlockTest_recordsAnErrorWhenTheAnchorIsMissing() {
		// Given:
		List<java.util.List<Object>> grid = gridOf(List.of("nothing here"));
		List<com.google.api.services.sheets.v4.model.ValueRange> data = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		// When:
		writer.collectDistributionBlock(data, grid, null, "Sheet1", 3, "Tactic 3", 100.0, 900.0, errors);

		// Then:
		assertThat(data).isEmpty();
		assertThat(errors).containsExactly("Channel Distribution 3: anchor not found");
	}

	@Test
	void shouldWriteEveryBlockOfATacticInOneBatchAndReportTheMissingOnesTest() throws Exception {
		// Given: a tab carrying tactic 1's daily, monthly and distribution blocks side by side, and no block at
		// all for tactic 2
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		ChartSheetWriter cells = spy(new ChartSheetWriter(googleCalls));
		SheetPacingTableWriter pacingWriter = new SheetPacingTableWriter(cells, googleCalls);
		Sheets sheets = mock(Sheets.class);
		Sheets.Spreadsheets spreadsheets = mock(Sheets.Spreadsheets.class);
		Sheets.Spreadsheets.Values values = mock(Sheets.Spreadsheets.Values.class);
		when(sheets.spreadsheets()).thenReturn(spreadsheets);
		when(spreadsheets.values()).thenReturn(values);
		List<List<Object>> grid = List.of(
				List.of("Daily pacing 1", "", "", "Monthly pacing 1", "", "Channel Distribution 1"),
				List.of("Date", "Impressions", "{{tactic 1 kpi type}}", "Month", "Impressions", "Distribution"),
				List.of("{{tactic 1 date}}", "{{tactic 1 impressions}}", "{{tactic 1 clicks}}",
						"{{tactic 1 date mon}}", "{{tactic 1 impressions mon}}", "{{tactic 1}}"),
				List.of("", "", "", "", "", "Other"));
		doReturn(grid).when(cells).readValues(sheets, "wb-1", "Report!A1:ZZ");
		LinkedHashMap<String, double[]> daily = new LinkedHashMap<>();
		daily.put("2026-10-01", new double[] {1000.4, 12.6, 0});
		daily.put("2026-10-02", new double[] {2000, 30, 0});
		LinkedHashMap<String, double[]> monthly = new LinkedHashMap<>();
		monthly.put("Oct 2026", new double[] {3000, 42, 0});
		TacticPacing pacing = new TacticPacing(true, Map.of(),
				new PacingSeries(false, Map.of(1, new PacingPivot(daily, true, false))),
				new PacingSeries(false, Map.of(1, new PacingPivot(monthly, true, false))));
		WorkbookPacingRequest request = new WorkbookPacingRequest(2, pacing, Map.of(1, "Display"),
				Map.of(1, 3000.0), 10_000, Map.of(1, "ctr"), null);

		// When:
		List<String> errors = pacingWriter.writeAll(sheets, "wb-1", "Report", request);

		// Then: tactic 2's three blocks are reported, tactic 1's are written in one RAW batch
		assertThat(errors).containsExactly("Daily pacing 2: anchor not found", "Monthly pacing 2: anchor not found",
				"Channel Distribution 2: anchor not found");
		ArgumentCaptor<BatchUpdateValuesRequest> batch = ArgumentCaptor.forClass(BatchUpdateValuesRequest.class);
		verify(values).batchUpdate(eq("wb-1"), batch.capture());
		assertThat(batch.getValue().getValueInputOption()).isEqualTo("RAW");
		List<ValueRange> data = batch.getValue().getData();
		assertThat(data).extracting(ValueRange::getRange).containsExactly(
				"Report!A3:A4", "Report!B3:B4", "Report!C3:C4", "Report!C2:C2",
				"Report!D3:D3", "Report!E3:E3", "Report!F3:G3", "Report!F4:G4");
		assertThat(data.get(0).getValues()).isEqualTo(List.of(List.of("2026-10-01"), List.of("2026-10-02")));
		assertThat(data.get(1).getValues()).isEqualTo(List.of(List.of(1000L), List.of(2000L)));
		assertThat(data.get(2).getValues()).isEqualTo(List.of(List.of(13L), List.of(30L)));
		assertThat(data.get(3).getValues()).isEqualTo(List.of(List.of("CTR")));
		assertThat(data.get(4).getValues()).isEqualTo(List.of(List.of("Oct 2026")));
		assertThat(data.get(5).getValues()).isEqualTo(List.of(List.of(3000L)));
		assertThat(data.get(6).getValues()).isEqualTo(List.of(List.of("Display", 3000L)));
		assertThat(data.get(7).getValues()).isEqualTo(List.of(List.of("Other", 7000L)));
	}

	@Test
	void shouldWriteNothingWhenNoBlockOfAnyTacticResolvesTest() throws Exception {
		// Given: a daily block with no data for the tactic, a monthly block without tokens and a distribution
		// block without the tactic's slice row
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		ChartSheetWriter cells = spy(new ChartSheetWriter(googleCalls));
		SheetPacingTableWriter pacingWriter = new SheetPacingTableWriter(cells, googleCalls);
		Sheets sheets = mock(Sheets.class);
		List<List<Object>> grid = List.of(
				List.of("Daily pacing 1", "Monthly pacing 1", "Channel Distribution 1"),
				List.of("{{tactic 1 date}}", "Month", "Distribution"));
		doReturn(grid).when(cells).readValues(sheets, "wb-1", "Report!A1:ZZ");
		LinkedHashMap<String, double[]> monthly = new LinkedHashMap<>();
		monthly.put("Oct 2026", new double[] {3000, 0, 0});
		TacticPacing pacing = new TacticPacing(true, Map.of(), new PacingSeries(false, Map.of()),
				new PacingSeries(false, Map.of(1, new PacingPivot(monthly, false, false))));
		WorkbookPacingRequest request = new WorkbookPacingRequest(1, pacing, Map.of(), Map.of(), 0, null, null);

		// When:
		List<String> errors = pacingWriter.writeAll(sheets, "wb-1", "Report", request);

		// Then: each block says why it was skipped, and no batch is sent
		assertThat(errors).containsExactly("Daily pacing 1: no BQ data for tactic 1",
				"Monthly pacing 1: date/impressions tokens not found near anchor",
				"Channel Distribution 1: {{tactic 1}} slice row not found");
		verifyNoInteractions(sheets);
	}

	@Test
	void shouldWriteTheCompletionSeriesWithAVcrHeaderWhenTheTacticHasNoClicksTest() {
		// Given: a daily block whose kpi header sits right of the anchor, and a completion-only pivot with no
		// configured KPI type
		List<List<Object>> grid = List.of(
				List.of("Daily pacing 3", "{{tactic 3 kpi type}}"),
				List.of("{{tactic 3 date}}", "{{tactic 3 impression}}", "{{tactic 3 completions}}"));
		LinkedHashMap<String, double[]> daily = new LinkedHashMap<>();
		daily.put("2026-10-01", new double[] {500, 0, 250.6});
		PacingPivot pivot = new PacingPivot(daily, false, true);
		List<ValueRange> data = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		// When:
		writer.collectPacingBlock(data, grid, new int[] {0, 0}, "Sheet1", 3, false, () -> pivot, null,
				"Daily pacing 3", errors);

		// Then:
		assertThat(errors).isEmpty();
		assertThat(data).extracting(ValueRange::getRange)
				.containsExactly("Sheet1!A2:A2", "Sheet1!B2:B2", "Sheet1!C2:C2", "Sheet1!B1:B1");
		assertThat(data.get(2).getValues()).isEqualTo(List.of(List.of(251L)));
		assertThat(data.get(3).getValues()).isEqualTo(List.of(List.of("VCR")));
	}

	@Test
	void shouldSkipTheMetricColumnWhenThePivotCarriesNeitherClicksNorCompletionsTest() {
		// Given: a block with a metric token but a pivot that only counted impressions
		List<List<Object>> grid = List.of(
				List.of("Daily pacing 1"),
				List.of("{{tactic 1 date}}", "{{tactic 1 impressions}}", "{{tactic 1 amount}}"));
		LinkedHashMap<String, double[]> daily = new LinkedHashMap<>();
		daily.put("2026-10-01", new double[] {500, 0, 0});
		PacingPivot pivot = new PacingPivot(daily, false, false);
		List<ValueRange> data = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		// When:
		writer.collectPacingBlock(data, grid, new int[] {0, 0}, "Sheet1", 1, false, () -> pivot, "vcr",
				"Daily pacing 1", errors);

		// Then: only the dates and impressions go in, and no kpi header is written without a token
		assertThat(errors).isEmpty();
		assertThat(data).extracting(ValueRange::getRange).containsExactly("Sheet1!A2:A2", "Sheet1!B2:B2");
	}

	@Test
	void shouldReportABlockMissingItsImpressionsTokenTest() {
		// Given: a block whose impressions token sits above the date row, then a date without impressions
		List<List<Object>> grid = List.of(
				List.of("Daily pacing 1"),
				List.of("{{tactic 1 clicks}}"),
				List.of("{{tactic 1 date}}"));
		LinkedHashMap<String, double[]> daily = new LinkedHashMap<>();
		daily.put("2026-10-01", new double[] {500, 5, 0});
		PacingPivot pivot = new PacingPivot(daily, true, false);
		List<ValueRange> data = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		// When:
		writer.collectPacingBlock(data, grid, new int[] {0, 0}, "Sheet1", 1, false, () -> pivot, "ctr",
				"Daily pacing 1", errors);

		// Then:
		assertThat(data).isEmpty();
		assertThat(errors).containsExactly("Daily pacing 1: date/impressions tokens not found near anchor");
	}

	@Test
	void shouldLabelTheKpiHeaderByTheTacticsSeriesTest() {
		// When-Then: clicks win, an audio tactic reads ACR, any other completion series reads VCR
		assertThat(writer.kpiHeaderLabel(true, "vcr")).isEqualTo("CTR");
		assertThat(writer.kpiHeaderLabel(false, "ACR")).isEqualTo("ACR");
		assertThat(writer.kpiHeaderLabel(false, "vcr")).isEqualTo("VCR");
		assertThat(writer.kpiHeaderLabel(false, null)).isEqualTo("VCR");
	}

	@Test
	void shouldFindTheSliceAndRestRowsOnShortRowsWithoutATacticNameTest() {
		// Given: a distribution block with an empty row, a note between the slice and its "rest" row, and no
		// resolved tactic name
		List<List<Object>> grid = List.of(
				List.of("Channel Distribution 1"),
				List.of(),
				List.of("{{TACTIC 1}}"),
				List.of("Notes"),
				List.of("rest"));

		// When:
		DistributionColumns cols = writer.findDistributionColumns(grid, new int[] {0, 0}, 1, null);

		// Then:
		assertThat(cols.tacticRow()).isEqualTo(2);
		assertThat(cols.otherRow()).isEqualTo(4);
	}

	@Test
	void shouldWriteOnlyTheSliceRowWhenTheBlockHasNoOtherRowTest() {
		// Given:
		List<List<Object>> grid = List.of(
				List.of("Channel Distribution 2"),
				List.of("Video"));
		List<ValueRange> data = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		// When:
		writer.collectDistributionBlock(data, grid, new int[] {0, 0}, "Sheet1", 2, "Video", 1_234.5, 10.0, errors);

		// Then:
		assertThat(errors).isEmpty();
		assertThat(data).extracting(ValueRange::getRange).containsExactly("Sheet1!A2:B2");
		assertThat(data.get(0).getValues()).isEqualTo(List.of(List.of("Video", 1235L)));
	}

	@Test
	void shouldSkipEmptyCellsWhileLookingForAnchorsTest() {
		// Given: a row carrying an unset cell next to an anchor with surrounding blanks
		List<List<Object>> grid = new ArrayList<>();
		grid.add(Arrays.<Object>asList(null, "  Daily Pacing 4 "));

		// When:
		Map<Integer, int[]> anchors = writer.findNumberedAnchors(grid, SheetPacingTableWriter.DAILY_ANCHOR);

		// Then: the null cell reads as empty and the padded, differently cased label still anchors tactic 4
		assertThat(anchors).containsOnlyKeys(4);
		assertThat(anchors.get(4)).containsExactly(0, 1);
		assertThat(writer.str(null)).isEmpty();
	}

	@SafeVarargs
	private static List<List<Object>> gridOf(List<String>... rows) {
		List<List<Object>> grid = new ArrayList<>();
		for (List<String> row : rows) {
			grid.add(new ArrayList<>(row));
		}
		return grid;
	}
}
