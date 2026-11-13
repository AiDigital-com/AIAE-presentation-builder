package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.observability.external.ExternalCallTimer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PacingPivot;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChartSheetWriterTest {

	private final ChartSheetWriter writer = new ChartSheetWriter(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));

	@Test
	void dailyComboRateRatio_isDecimalNotPercentPoints() {
		assertThat(writer.dailyComboRateRatio(1000, 25)).isEqualTo(0.025);
		double legacyBugPercentPoints = (25.0 / 1000.0) * 100.0;
		assertThat(writer.dailyComboRateRatio(1000, 25)).isNotEqualTo(legacyBugPercentPoints);
	}

	@Test
	void shouldWriteDailyRateColumn_falseLeavesColumnCToTemplateFormula() {
		assertThat(writer.shouldWriteDailyRateColumn()).isFalse();
	}

	@Test
	void normalizeBreakdownLabel_foldsCtvSpellingsAndStripsCaseAndSpaceTest() {
		// Given-Then: the "Breakdowns" tab writes "CTV" while the chart source uses "Connected TV" — both
		// must resolve to the same key, and age buckets pass through unchanged
		assertThat(writer.normalizeBreakdownLabel("CTV")).isEqualTo("connectedtv");
		assertThat(writer.normalizeBreakdownLabel("Connected TV")).isEqualTo("connectedtv");
		assertThat(writer.normalizeBreakdownLabel("Mobile")).isEqualTo("mobile");
		assertThat(writer.normalizeBreakdownLabel(" 25-34 ")).isEqualTo("25-34");
		assertThat(writer.normalizeBreakdownLabel(null)).isEmpty();
	}

	@Test
	void shouldWriteMatchedDevicesAndClearTheSampleOnesTest() {
		// Given: the device workbook's column A — header, three sample devices, a gap, then a stray cell — and
		// a tactic that served mobile and desktop only
		List<List<Object>> columnA = List.of(
				List.of("Device"), List.of("Mobile"), List.of("Desktop"), List.of("Tablet"), List.of(),
				List.of("#REF!"));
		Map<String, Long> imps = Map.of("mobile", 48_215L, "desktop", 32_144L);

		// When:
		List<ValueRange> updates = writer.breakdownImpressionUpdates(columnA, "Sheet1", imps);

		// Then: the two matches get their impressions, the tablet sample is blanked, the header and the cell
		// below the block stay as they are
		assertThat(updates).extracting(ValueRange::getRange)
				.containsExactly("Sheet1!B2", "Sheet1!B3", "Sheet1!B4");
		assertThat(updates).extracting(vr -> vr.getValues().getFirst().getFirst())
				.containsExactly(48_215L, 32_144L, "");
	}

	@Test
	void shouldPlanOnlyBlanksWhenNoCategoryMatchesTest() {
		// Given: age buckets offered to a workbook that lists devices
		List<List<Object>> columnA = List.of(List.of("Device"), List.of("Mobile"), List.of("Desktop"));
		Map<String, Long> imps = Map.of("25-34", 1_000L);

		// When:
		List<ValueRange> updates = writer.breakdownImpressionUpdates(columnA, "Sheet1", imps);

		// Then: nothing carries a value, which is how the writer knows to skip the write and report no match
		assertThat(updates).extracting(vr -> vr.getValues().getFirst().getFirst()).containsOnly("");
	}

	@Test
	void rateColumnValuesForPivot_emptyWhenTemplateOwnsFormula() {
		PacingPivot pivot = pivotRow(1000, 25, 0);
		assertThat(writer.rateColumnValuesForPivot(pivot, true)).isEmpty();
	}

	@Test
	void rateColumnValuesForPivot_whenForcedWriteUsesRatioNotTimes100() {
		PacingPivot pivot = pivotRow(1000, 25, 0);
		Optional<List<List<Object>>> rates = writer.rateColumnValuesForPivot(pivot, true, true);
		assertThat(rates).isPresent();
		assertThat(rates.get().getFirst().getFirst()).isEqualTo(0.025);
	}

	@Test
	void shouldReturnNoRatesWhenForcedPivotIsEmptyTest() {
		// Given:
		PacingPivot pivot = new PacingPivot(new LinkedHashMap<>(), true, false);

		// When:
		Optional<List<List<Object>>> rates = writer.rateColumnValuesForPivot(pivot, true, true);

		// Then:
		assertThat(rates).isEmpty();
	}

	@Test
	void shouldUseCompletionsAndZeroImpressionsGuardForForcedRatesTest() {
		// Given: a VCR series with one zero-impression day
		LinkedHashMap<String, double[]> data = new LinkedHashMap<>();
		data.put("2026-01-01", new double[]{3000, 10, 1000});
		data.put("2026-01-02", new double[]{0, 0, 5});
		PacingPivot pivot = new PacingPivot(data, true, true);

		// When:
		Optional<List<List<Object>>> rates = writer.rateColumnValuesForPivot(pivot, false, true);

		// Then: completions / impressions rounded to 4 decimals, 0 when there are no impressions
		assertThat(rates).contains(List.of(List.of(0.3333), List.of(0.0)));
	}

	@Test
	void shouldConvertColumnIndexesToA1LettersTest() {
		// Given-When-Then:
		assertThat(writer.colLetter(0)).isEqualTo("A");
		assertThat(writer.colLetter(25)).isEqualTo("Z");
		assertThat(writer.colLetter(26)).isEqualTo("AA");
		assertThat(writer.colLetter(51)).isEqualTo("AZ");
		assertThat(writer.colLetter(701)).isEqualTo("ZZ");
		assertThat(writer.colLetter(702)).isEqualTo("AAA");
	}

	@Test
	void shouldNotTouchTheSheetWhenPivotIsEmptyTest() throws IOException {
		// Given:
		Sheets sheets = mock(Sheets.class);
		PacingPivot pivot = new PacingPivot(new LinkedHashMap<>(), true, true);

		// When:
		writer.writePivot(sheets, "ss-1", "Daily", pivot, "ctr");

		// Then:
		verifyNoInteractions(sheets);
	}

	@Test
	void shouldWriteCompletionsUnderPlaceholdersAndRelabelHeaderForVcrTacticTest() throws IOException {
		// Given: the KPI header sits on row 1 above the placeholder row; the pivot carries clicks and completions
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		List<List<Object>> template = List.of(
				List.of("Date", "Impressions", "CTR", "Amount"),
				List.of("{{tactic 1 date}}", "{{tactic 1 impressions}}", "=D2/B2", "{{tactic 1 amount}}"));
		when(sheets.spreadsheets().values().get("ss-1", "Daily!A1:ZZ3").execute())
				.thenReturn(new ValueRange().setValues(template));
		LinkedHashMap<String, double[]> data = new LinkedHashMap<>();
		data.put("2026-01-01", new double[]{1000.4, 25, 600.6});
		data.put("2026-01-02", new double[]{2000, 40, 1200.2});
		PacingPivot pivot = new PacingPivot(data, true, true);
		ArgumentCaptor<ValueRange> header = ArgumentCaptor.forClass(ValueRange.class);
		ArgumentCaptor<BatchUpdateValuesRequest> body = ArgumentCaptor.forClass(BatchUpdateValuesRequest.class);

		// When:
		writer.writePivot(sheets, "ss-1", "Daily", pivot, "VCR");

		// Then:
		verify(sheets.spreadsheets().values()).update(eq("ss-1"), eq("Daily!C1"), header.capture());
		assertThat(header.getValue().getValues()).isEqualTo(List.of(List.of("VCR")));
		verify(sheets.spreadsheets().values()).batchUpdate(eq("ss-1"), body.capture());
		assertThat(body.getValue().getValueInputOption()).isEqualTo("RAW");
		assertThat(body.getValue().getData()).extracting(ValueRange::getRange)
				.containsExactly("Daily!A2:A3", "Daily!B2:B3", "Daily!D2:D3");
		assertThat(body.getValue().getData()).extracting(ValueRange::getValues).containsExactly(
				List.of(List.of("2026-01-01"), List.of("2026-01-02")),
				List.of(List.of(1000L), List.of(2000L)),
				List.of(List.of(601L), List.of(1200L)));
	}

	@Test
	void shouldDeriveClicksFromPivotAndStopScanningAfterThePlaceholderRowTest() throws IOException {
		// Given: offset placeholders under a KPI-type token, and a stray token on the next row that must be ignored
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		List<List<Object>> template = List.of(
				Arrays.asList("Monthly", null, "", "", "Month", "{{Tactic 2 KPI Type}}"),
				List.of("", "", "", "", "{{tactic 2 date}}", "{{tactic 2 impressions}}", "", "{{tactic 2 clicks}}"),
				List.of("{{tactic 9 impressions}}"));
		when(sheets.spreadsheets().values().get("ss-1", "Monthly!A1:ZZ3").execute())
				.thenReturn(new ValueRange().setValues(template));
		LinkedHashMap<String, double[]> data = new LinkedHashMap<>();
		data.put("2026-01", new double[]{5000, 75.5, 0});
		PacingPivot pivot = new PacingPivot(data, true, false);
		ArgumentCaptor<ValueRange> header = ArgumentCaptor.forClass(ValueRange.class);
		ArgumentCaptor<BatchUpdateValuesRequest> body = ArgumentCaptor.forClass(BatchUpdateValuesRequest.class);

		// When:
		writer.writePivot(sheets, "ss-1", "Monthly", pivot, null);

		// Then:
		verify(sheets.spreadsheets().values()).update(eq("ss-1"), eq("Monthly!F1"), header.capture());
		assertThat(header.getValue().getValues()).isEqualTo(List.of(List.of("CTR")));
		verify(sheets.spreadsheets().values()).batchUpdate(eq("ss-1"), body.capture());
		assertThat(body.getValue().getData()).extracting(ValueRange::getRange)
				.containsExactly("Monthly!E2:E2", "Monthly!F2:F2", "Monthly!H2:H2");
		assertThat(body.getValue().getData()).extracting(ValueRange::getValues).containsExactly(
				List.of(List.of("2026-01")), List.of(List.of(5000L)), List.of(List.of(76L)));
	}

	@Test
	void shouldLabelHeaderAcrAndWriteCompletionsForAcrTacticTest() throws IOException {
		// Given: a legacy literal VCR header with completions placeholders
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		List<List<Object>> template = List.of(
				List.of("Date", "Impressions", "VCR", "Completions"),
				List.of("{{tactic 1 date}}", "{{tactic 1 impression}}", "", "{{tactic 1 completions}}"));
		when(sheets.spreadsheets().values().get("ss-1", "Daily!A1:ZZ3").execute())
				.thenReturn(new ValueRange().setValues(template));
		LinkedHashMap<String, double[]> data = new LinkedHashMap<>();
		data.put("2026-01-01", new double[]{800, 3, 410});
		PacingPivot pivot = new PacingPivot(data, false, true);
		ArgumentCaptor<ValueRange> header = ArgumentCaptor.forClass(ValueRange.class);
		ArgumentCaptor<BatchUpdateValuesRequest> body = ArgumentCaptor.forClass(BatchUpdateValuesRequest.class);

		// When:
		writer.writePivot(sheets, "ss-1", "Daily", pivot, "acr");

		// Then:
		verify(sheets.spreadsheets().values()).update(eq("ss-1"), eq("Daily!C1"), header.capture());
		assertThat(header.getValue().getValues()).isEqualTo(List.of(List.of("ACR")));
		verify(sheets.spreadsheets().values()).batchUpdate(eq("ss-1"), body.capture());
		assertThat(body.getValue().getData()).extracting(ValueRange::getRange)
				.containsExactly("Daily!A2:A2", "Daily!B2:B2", "Daily!D2:D2");
		assertThat(body.getValue().getData().get(2).getValues()).isEqualTo(List.of(List.of(410L)));
	}

	@Test
	void shouldRelabelLiteralAcrHeaderAndDefaultDateColumnWhenDatePlaceholderIsMissingTest() throws IOException {
		// Given: a literal ACR header and impressions/amount placeholders, but no date placeholder
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		List<List<Object>> template = List.of(
				List.of("Date", "Impressions", "ACR", "Amount"),
				List.of("", "{{tactic 1 impressions}}", "", "{{tactic 1 amount}}"));
		when(sheets.spreadsheets().values().get("ss-1", "Daily!A1:ZZ3").execute())
				.thenReturn(new ValueRange().setValues(template));
		LinkedHashMap<String, double[]> data = new LinkedHashMap<>();
		data.put("2026-01-01", new double[]{500, 7, 0});
		PacingPivot pivot = new PacingPivot(data, true, false);
		ArgumentCaptor<ValueRange> header = ArgumentCaptor.forClass(ValueRange.class);
		ArgumentCaptor<BatchUpdateValuesRequest> body = ArgumentCaptor.forClass(BatchUpdateValuesRequest.class);

		// When:
		writer.writePivot(sheets, "ss-1", "Daily", pivot, "ctr");

		// Then: the header becomes CTR and dates fall back to column A on the placeholder row
		verify(sheets.spreadsheets().values()).update(eq("ss-1"), eq("Daily!C1"), header.capture());
		assertThat(header.getValue().getValues()).isEqualTo(List.of(List.of("CTR")));
		verify(sheets.spreadsheets().values()).batchUpdate(eq("ss-1"), body.capture());
		assertThat(body.getValue().getData()).extracting(ValueRange::getRange)
				.containsExactly("Daily!A2:A2", "Daily!B2:B2", "Daily!D2:D2");
		assertThat(body.getValue().getData()).extracting(ValueRange::getValues).containsExactly(
				List.of(List.of("2026-01-01")), List.of(List.of(500L)), List.of(List.of(7L)));
	}

	@Test
	void shouldFallBackToDefaultColumnsWithoutHeaderWhenTabIsEmptyTest() throws IOException {
		// Given: the read returns no values at all
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		when(sheets.spreadsheets().values().get("ss-1", "Daily!A1:ZZ3").execute()).thenReturn(new ValueRange());
		LinkedHashMap<String, double[]> data = new LinkedHashMap<>();
		data.put("2026-01-01", new double[]{1200, 30, 0});
		PacingPivot pivot = new PacingPivot(data, true, false);
		ArgumentCaptor<BatchUpdateValuesRequest> body = ArgumentCaptor.forClass(BatchUpdateValuesRequest.class);

		// When:
		writer.writePivot(sheets, "ss-1", "Daily", pivot, "ctr");

		// Then: date A, impressions B, clicks D from row 2, and no header write
		verify(sheets.spreadsheets().values()).batchUpdate(eq("ss-1"), body.capture());
		assertThat(body.getValue().getData()).extracting(ValueRange::getRange)
				.containsExactly("Daily!A2:A2", "Daily!B2:B2", "Daily!D2:D2");
		assertThat(body.getValue().getData().get(2).getValues()).isEqualTo(List.of(List.of(30L)));
		assertThat(mockingDetails(sheets.spreadsheets().values()).getInvocations())
				.extracting(invocation -> invocation.getMethod().getName())
				.doesNotContain("update");
	}

	@Test
	void shouldDeleteMetricColumnsAndSkipHeaderWhenPivotHasNoClicksOrCompletionsTest() throws IOException {
		// Given: an impressions-only pivot; the tab is the third sheet of the workbook
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		List<List<Object>> template = List.of(
				List.of("Date", "Impressions", "CTR", "Clicks"),
				List.of("{{tactic 1 date}}", "{{tactic 1 impressions}}", "", "{{tactic 1 clicks}}"));
		when(sheets.spreadsheets().values().get("ss-1", "Daily!A1:ZZ3").execute())
				.thenReturn(new ValueRange().setValues(template));
		Spreadsheet workbook = new Spreadsheet().setSheets(List.of(
				new Sheet(),
				new Sheet().setProperties(new SheetProperties().setTitle("Other").setSheetId(7)),
				new Sheet().setProperties(new SheetProperties().setTitle("Daily").setSheetId(42))));
		when(sheets.spreadsheets().get("ss-1").setIncludeGridData(false)
				.setFields("sheets.properties(sheetId,title)").execute()).thenReturn(workbook);
		LinkedHashMap<String, double[]> data = new LinkedHashMap<>();
		data.put("2026-01-01", new double[]{900, 0, 0});
		PacingPivot pivot = new PacingPivot(data, false, false);
		ArgumentCaptor<BatchUpdateSpreadsheetRequest> delete =
				ArgumentCaptor.forClass(BatchUpdateSpreadsheetRequest.class);
		ArgumentCaptor<BatchUpdateValuesRequest> body = ArgumentCaptor.forClass(BatchUpdateValuesRequest.class);

		// When:
		writer.writePivot(sheets, "ss-1", "Daily", pivot, null);

		// Then: column D then column C are deleted on sheet 42, and only dates and impressions are written
		verify(sheets.spreadsheets()).batchUpdate(eq("ss-1"), delete.capture());
		assertThat(delete.getValue().getRequests()).extracting(r -> r.getDeleteDimension().getRange())
				.containsExactly(
						new DimensionRange().setSheetId(42).setDimension("COLUMNS").setStartIndex(3).setEndIndex(4),
						new DimensionRange().setSheetId(42).setDimension("COLUMNS").setStartIndex(2).setEndIndex(3));
		verify(sheets.spreadsheets().values()).batchUpdate(eq("ss-1"), body.capture());
		assertThat(body.getValue().getData()).extracting(ValueRange::getRange)
				.containsExactly("Daily!A2:A2", "Daily!B2:B2");
		assertThat(mockingDetails(sheets.spreadsheets().values()).getInvocations())
				.extracting(invocation -> invocation.getMethod().getName())
				.doesNotContain("update");
	}

	@Test
	void shouldResolveSheetIdZeroWhenTabIsMissingOrHasNoIdTest() throws IOException {
		// Given: one workbook without sheets, one whose matching tab has no id, one without the tab
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		String fields = "sheets.properties(sheetId,title)";
		when(sheets.spreadsheets().get("no-sheets").setIncludeGridData(false).setFields(fields).execute())
				.thenReturn(new Spreadsheet());
		when(sheets.spreadsheets().get("no-id").setIncludeGridData(false).setFields(fields).execute())
				.thenReturn(new Spreadsheet().setSheets(List.of(
						new Sheet().setProperties(new SheetProperties().setTitle("Daily")))));
		when(sheets.spreadsheets().get("no-tab").setIncludeGridData(false).setFields(fields).execute())
				.thenReturn(new Spreadsheet().setSheets(List.of(
						new Sheet().setProperties(new SheetProperties().setTitle("Other").setSheetId(5)))));

		// When:
		int noSheets = writer.sheetIdForTab(sheets, "no-sheets", "Daily");
		int noId = writer.sheetIdForTab(sheets, "no-id", "Daily");
		int noTab = writer.sheetIdForTab(sheets, "no-tab", "Daily");

		// Then:
		assertThat(noSheets).isZero();
		assertThat(noId).isZero();
		assertThat(noTab).isZero();
	}

	@Test
	void shouldWriteDistributionToPlaceholderAndTotalRowsTest() throws IOException {
		// Given: a header, a blank row, the tactic placeholder on row 3 and the total on row 4
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		List<List<Object>> template = List.of(
				List.of("Distribution", "Impressions"), List.of(), List.of("{{Tactic 1}}", 10), List.of("Total", 90));
		when(sheets.spreadsheets().values().get("ss-1", "Sheet1!A1:B5").execute())
				.thenReturn(new ValueRange().setValues(template));
		ArgumentCaptor<BatchUpdateValuesRequest> body = ArgumentCaptor.forClass(BatchUpdateValuesRequest.class);

		// When:
		writer.writeDistribution(sheets, "ss-1", "Sheet1", "Display Prospecting", 1234.6, 8765.4);

		// Then:
		verify(sheets.spreadsheets().values()).batchUpdate(eq("ss-1"), body.capture());
		assertThat(body.getValue().getValueInputOption()).isEqualTo("RAW");
		assertThat(body.getValue().getData()).extracting(ValueRange::getRange)
				.containsExactly("Sheet1!A3:B3", "Sheet1!A4:B4");
		assertThat(body.getValue().getData()).extracting(ValueRange::getValues).containsExactly(
				List.of(List.of("Display Prospecting", 1235L)), List.of(List.of("Other", 8765L)));
	}

	@Test
	void shouldLocateOtherRowLabelledOtherOrRestTest() throws IOException {
		// Given: the tactic placeholder on row 2 and an "Other" label on row 4
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		when(sheets.spreadsheets().values().get("ss-1", "Sheet1!A1:B5").execute())
				.thenReturn(new ValueRange().setValues(
						List.of(List.of("Header"), List.of("{{tactic 3}}"), List.of("x"), List.of(" Other "))));
		when(sheets.spreadsheets().values().get("ss-2", "Sheet1!A1:B5").execute())
				.thenReturn(new ValueRange().setValues(List.of(List.of("Header"), List.of("REST"))));
		ArgumentCaptor<BatchUpdateValuesRequest> first = ArgumentCaptor.forClass(BatchUpdateValuesRequest.class);
		ArgumentCaptor<BatchUpdateValuesRequest> second = ArgumentCaptor.forClass(BatchUpdateValuesRequest.class);

		// When:
		writer.writeDistribution(sheets, "ss-1", "Sheet1", "CTV", 10, 20);
		writer.writeDistribution(sheets, "ss-2", "Sheet1", "CTV", 10, 20);

		// Then: "Rest" on row 2 moves the other slice there while the tactic keeps the default row 2
		verify(sheets.spreadsheets().values()).batchUpdate(eq("ss-1"), first.capture());
		assertThat(first.getValue().getData()).extracting(ValueRange::getRange)
				.containsExactly("Sheet1!A2:B2", "Sheet1!A4:B4");
		verify(sheets.spreadsheets().values()).batchUpdate(eq("ss-2"), second.capture());
		assertThat(second.getValue().getData()).extracting(ValueRange::getRange)
				.containsExactly("Sheet1!A2:B2", "Sheet1!A2:B2");
	}

	@Test
	void shouldUseDefaultDistributionRowsWhenTabIsEmptyTest() throws IOException {
		// Given:
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		when(sheets.spreadsheets().values().get("ss-1", "Sheet1!A1:B5").execute()).thenReturn(new ValueRange());
		ArgumentCaptor<BatchUpdateValuesRequest> body = ArgumentCaptor.forClass(BatchUpdateValuesRequest.class);

		// When:
		writer.writeDistribution(sheets, "ss-1", "Sheet1", "Search", 5, 15);

		// Then:
		verify(sheets.spreadsheets().values()).batchUpdate(eq("ss-1"), body.capture());
		assertThat(body.getValue().getData()).extracting(ValueRange::getRange)
				.containsExactly("Sheet1!A2:B2", "Sheet1!A3:B3");
		assertThat(body.getValue().getData()).extracting(ValueRange::getValues).containsExactly(
				List.of(List.of("Search", 5L)), List.of(List.of("Other", 15L)));
	}

	@Test
	void shouldSkipBreakdownImpressionsWhenNoSliceIsSuppliedTest() throws IOException {
		// Given:
		Sheets sheets = mock(Sheets.class);

		// When:
		int written = writer.writeBreakdownImpressions(sheets, "ss-1", "Sheet1", Map.of());

		// Then:
		assertThat(written).isZero();
		verifyNoInteractions(sheets);
	}

	@Test
	void shouldNotWriteBreakdownImpressionsWhenNoCategoryMatchesTest() throws IOException {
		// Given: age buckets offered to a device workbook
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		when(sheets.spreadsheets().values().get("ss-1", "Sheet1!A1:A50").execute())
				.thenReturn(new ValueRange().setValues(List.of(List.of("Device"), List.of("Mobile"))));

		// When:
		int written = writer.writeBreakdownImpressions(sheets, "ss-1", "Sheet1", Map.of("25-34", 100L));

		// Then:
		assertThat(written).isZero();
		assertThat(mockingDetails(sheets.spreadsheets().values()).getInvocations())
				.extracting(invocation -> invocation.getMethod().getName())
				.contains("get")
				.doesNotContain("batchUpdate");
	}

	@Test
	void shouldWriteMatchedBreakdownImpressionsAndClearUnmatchedSamplesTest() throws IOException {
		// Given:
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		when(sheets.spreadsheets().values().get("ss-1", "Sheet1!A1:A50").execute())
				.thenReturn(new ValueRange().setValues(List.of(
						List.of("Device"), List.of("Mobile"), List.of("Connected TV"), List.of("Tablet"))));
		ArgumentCaptor<BatchUpdateValuesRequest> body = ArgumentCaptor.forClass(BatchUpdateValuesRequest.class);

		// When:
		int written = writer.writeBreakdownImpressions(sheets, "ss-1", "Sheet1",
				Map.of("mobile", 48_215L, "connectedtv", 9_001L));

		// Then:
		assertThat(written).isEqualTo(2);
		verify(sheets.spreadsheets().values()).batchUpdate(eq("ss-1"), body.capture());
		assertThat(body.getValue().getValueInputOption()).isEqualTo("RAW");
		assertThat(body.getValue().getData()).extracting(ValueRange::getRange)
				.containsExactly("Sheet1!B2", "Sheet1!B3", "Sheet1!B4");
		assertThat(body.getValue().getData()).extracting(vr -> vr.getValues().getFirst().getFirst())
				.containsExactly(48_215L, 9_001L, "");
	}

	@Test
	void shouldKeepBlockOpenAfterBlankHeaderAndWriteMatchesBelowTheBlockTest() {
		// Given: a blank header row, an unmatched block row, a gap, then a matched row below the block
		List<List<Object>> columnA = List.of(
				List.of(""), List.of("Tablet"), List.of(), List.of("Desktop"), List.of("Other"));

		// When:
		List<ValueRange> updates = writer.breakdownImpressionUpdates(columnA, "Sheet1",
				Map.of("desktop", 700L));

		// Then:
		assertThat(updates).extracting(ValueRange::getRange).containsExactly("Sheet1!B2", "Sheet1!B4");
		assertThat(updates).extracting(vr -> vr.getValues().getFirst().getFirst()).containsExactly("", 700L);
	}

	@Test
	void shouldSkipBreakdownSeriesWhenNoValuesAreSuppliedTest() throws IOException {
		// Given:
		Sheets sheets = mock(Sheets.class);

		// When:
		int written = writer.writeBreakdownSeries(sheets, "ss-1", "Sheet1", Map.of(), 2);

		// Then:
		assertThat(written).isZero();
		verifyNoInteractions(sheets);
	}

	@Test
	void shouldBlankLeftoverSampleRowsInsideTheBlockTest() throws IOException {
		// Given: a four-row sample block ending at a blank label, and two campaign segments
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		when(sheets.spreadsheets().values().get("ss-1", "Sheet1!A2:A50").execute())
				.thenReturn(new ValueRange().setValues(List.of(
						List.of("Segment A"), List.of("Segment B"), List.of("Segment C"), List.of("Segment D"),
						List.of(" "), List.of("stray"))));
		LinkedHashMap<String, Double> values = new LinkedHashMap<>();
		values.put("Sports Fans", 0.42);
		values.put("Gamers", 0.18);
		ArgumentCaptor<BatchUpdateValuesRequest> body = ArgumentCaptor.forClass(BatchUpdateValuesRequest.class);

		// When:
		int written = writer.writeBreakdownSeries(sheets, "ss-1", "Sheet1", values, 2);

		// Then:
		assertThat(written).isEqualTo(2);
		verify(sheets.spreadsheets().values()).batchUpdate(eq("ss-1"), body.capture());
		assertThat(body.getValue().getValueInputOption()).isEqualTo("RAW");
		assertThat(body.getValue().getData()).hasSize(1);
		assertThat(body.getValue().getData().getFirst().getRange()).isEqualTo("Sheet1!A2:B5");
		assertThat(body.getValue().getData().getFirst().getValues()).isEqualTo(List.of(
				List.of("Sports Fans", 0.42), List.of("Gamers", 0.18), List.of("", ""), List.of("", "")));
	}

	@Test
	void shouldWriteEverySliceWhenBlockIsShorterThanTheSeriesTest() throws IOException {
		// Given: a one-row block terminated by an empty row, and two segments
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		when(sheets.spreadsheets().values().get("ss-1", "Sheet1!A5:A50").execute())
				.thenReturn(new ValueRange().setValues(List.of(List.of("Segment A"), List.of(), List.of("x"))));
		LinkedHashMap<String, Double> values = new LinkedHashMap<>();
		values.put("Travelers", 12.0);
		values.put("Parents", 8.0);
		ArgumentCaptor<BatchUpdateValuesRequest> body = ArgumentCaptor.forClass(BatchUpdateValuesRequest.class);

		// When:
		int written = writer.writeBreakdownSeries(sheets, "ss-1", "Sheet1", values, 5);

		// Then:
		assertThat(written).isEqualTo(2);
		verify(sheets.spreadsheets().values()).batchUpdate(eq("ss-1"), body.capture());
		assertThat(body.getValue().getData().getFirst().getRange()).isEqualTo("Sheet1!A5:B6");
		assertThat(body.getValue().getData().getFirst().getValues())
				.isEqualTo(List.of(List.of("Travelers", 12.0), List.of("Parents", 8.0)));
	}

	@Test
	void shouldWriteSlicesWhenBlockCannotBeReadTest() throws IOException {
		// Given:
		Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		when(sheets.spreadsheets().values().get("ss-1", "Sheet1!A2:A50").execute()).thenReturn(new ValueRange());
		LinkedHashMap<String, Double> values = new LinkedHashMap<>();
		values.put("Travelers", 3.0);
		ArgumentCaptor<BatchUpdateValuesRequest> body = ArgumentCaptor.forClass(BatchUpdateValuesRequest.class);

		// When:
		int written = writer.writeBreakdownSeries(sheets, "ss-1", "Sheet1", values, 2);

		// Then:
		assertThat(written).isEqualTo(1);
		verify(sheets.spreadsheets().values()).batchUpdate(eq("ss-1"), body.capture());
		assertThat(body.getValue().getData().getFirst().getRange()).isEqualTo("Sheet1!A2:B2");
		assertThat(body.getValue().getData().getFirst().getValues()).isEqualTo(List.of(List.of("Travelers", 3.0)));
	}

	@Test
	void shouldRenderNullCellAsEmptyStringTest() {
		// Given-When-Then:
		assertThat(writer.str(null)).isEmpty();
		assertThat(writer.str(42)).isEqualTo("42");
	}

	private static PacingPivot pivotRow(double imps, double clicks, double completions) {
		LinkedHashMap<String, double[]> data = new LinkedHashMap<>();
		data.put("2026-01-01", new double[]{imps, clicks, completions});
		return new PacingPivot(data, clicks > 0, completions > 0);
	}
}
