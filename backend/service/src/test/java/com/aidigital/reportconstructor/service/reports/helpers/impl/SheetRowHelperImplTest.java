package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.common.time.CurrentTime;
import com.aidigital.reportconstructor.service.common.time.CurrentTimeImpl;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SheetRowHelperImplTest {

	private final SheetRowHelperImpl helper = new SheetRowHelperImpl(new CurrentTimeImpl());

	@Test
	void shouldCollectDistinctColumnValuesBelowHeaderSkippingSectionRowTest() {
		// Given: a grid where the header is followed by a section-title row, then repeated line-item values
		List<List<String>> rows = List.of(
				List.of("Flight", "Geo", "Media"),
				List.of("Evergreen", "", ""),
				List.of("2026-06-08", "Texas", "Programmatic Display"),
				List.of("2026-06-08", "Oklahoma", "Google SEM"),
				List.of("2026-06-08", "Texas", "Meta"));

		// When: the geo column is collected
		List<String> values = helper.collectColumnValuesBelow(rows, Set.of("geo"));

		// Then: distinct values are returned in first-seen order, ignoring the empty section-row cell
		assertThat(values).containsExactly("Texas", "Oklahoma");
	}

	@Test
	void shouldStopColumnCollectionAtTotalsFooterRowTest() {
		// Given: a totals row sits after the data and before unrelated trailing rows
		List<List<String>> rows = List.of(
				List.of("Goal", "Cost"),
				List.of("Awareness", "100"),
				List.of("Totals:", "100"),
				List.of("Consideration", "999"));

		// When: the goal column is collected
		List<String> values = helper.collectColumnValuesBelow(rows, Set.of("goal"));

		// Then: collection stops at the totals row and ignores rows beyond it
		assertThat(values).containsExactly("Awareness");
	}

	@Test
	void shouldMatchHeaderSynonymIgnoringCaseAndPunctuationTest() {
		// Given: the header cell uses mixed case and a line break
		List<List<String>> rows = List.of(
				List.of("Targeted\nLocations"),
				List.of("Dallas-Fort Worth"));

		// When: matched against the normalised synonym
		List<String> values = helper.collectColumnValuesBelow(rows, Set.of("targeted locations"));

		// Then: the column value is collected
		assertThat(values).containsExactly("Dallas-Fort Worth");
	}

	@Test
	void shouldReturnEmptyWhenNoHeaderMatchesTest() {
		// Given: a grid with no matching header
		List<List<String>> rows = List.of(List.of("Campaign:", "Spring"));

		// When-Then: no column is found
		assertThat(helper.collectColumnValuesBelow(rows, Set.of("geo"))).isEmpty();
	}

	@Test
	void shouldReportGenericSeeTabPointerAsGeoTabReferenceTest() {
		// Given-When-Then: both the classic and generic "see ... tab/sheet" pointers are detected
		assertThat(helper.referencesGeoTab("See Geo Tab")).isTrue();
		assertThat(helper.referencesGeoTab("see locations sheet")).isTrue();
		assertThat(helper.referencesGeoTab("Texas, Oklahoma")).isFalse();
		assertThat(helper.referencesGeoTab(null)).isFalse();
	}

	@Test
	void shouldDetectGeoSheetPointerAndRejectSeeWithoutTabOrSheetTest() {
		// Given:
		SheetRowHelperImpl target = new SheetRowHelperImpl(new CurrentTimeImpl());

		// When-Then:
		assertThat(target.referencesGeoTab("Geo-Sheet")).isTrue();
		assertThat(target.referencesGeoTab("See targeting tab")).isTrue();
		assertThat(target.referencesGeoTab("see the plan")).isFalse();
	}

	@Test
	void shouldFindValueRightOfLabelSkippingNullRowsTest() {
		// Given: a null row, a row with a null cell, and the label row
		SheetRowHelperImpl target = new SheetRowHelperImpl(new CurrentTimeImpl());
		List<List<String>> rows = Arrays.asList(
				null,
				Arrays.asList(null, "x"),
				List.of(" Client: ", " Acme "));

		// When:
		String value = target.findLabelValue(rows, "client:");

		// Then:
		assertThat(value).isEqualTo("Acme");
	}

	@Test
	void shouldReturnNullLabelValueWhenRowsMissingOrLabelIsLastCellTest() {
		// Given:
		SheetRowHelperImpl target = new SheetRowHelperImpl(new CurrentTimeImpl());
		List<List<String>> rows = List.of(List.of("a", "Client:"));

		// When-Then:
		assertThat(target.findLabelValue(null, "Client:")).isNull();
		assertThat(target.findLabelValue(rows, "Client:")).isNull();
		assertThat(target.findLabelValue(rows, "Budget:")).isNull();
	}

	@Test
	void shouldFindValueBelowLabelSkippingNullRowsTest() {
		// Given:
		SheetRowHelperImpl target = new SheetRowHelperImpl(new CurrentTimeImpl());
		List<List<String>> rows = Arrays.asList(
				null,
				List.of("x", "Budget"),
				List.of("y", " $500 "));

		// When:
		String value = target.findLabelValueBelow(rows, "budget");

		// Then:
		assertThat(value).isEqualTo("$500");
	}

	@Test
	void shouldReturnNullBelowLabelWhenNoUsableNextRowTest() {
		// Given: the label on the last row, a label above a null row, and a label above a too-short row
		SheetRowHelperImpl target = new SheetRowHelperImpl(new CurrentTimeImpl());
		List<List<String>> lastRow = List.of(List.of("Budget"));
		List<List<String>> nullBelow = Arrays.asList(List.of("Budget"), null);
		List<List<String>> shortBelow = List.of(List.of("x", "Budget"), List.of("y"));

		// When-Then:
		assertThat(target.findLabelValueBelow(null, "Budget")).isNull();
		assertThat(target.findLabelValueBelow(lastRow, "Budget")).isNull();
		assertThat(target.findLabelValueBelow(nullBelow, "Budget")).isNull();
		assertThat(target.findLabelValueBelow(shortBelow, "Budget")).isNull();
		assertThat(target.findLabelValueBelow(shortBelow, "Goal")).isNull();
	}

	@Test
	void shouldReturnNoColumnValuesForMissingRowsOrSynonymsTest() {
		// Given:
		SheetRowHelperImpl target = new SheetRowHelperImpl(new CurrentTimeImpl());
		List<List<String>> rows = List.of(List.of("Geo"), List.of("Texas"));

		// When-Then:
		assertThat(target.collectColumnValuesBelow(null, Set.of("geo"))).isEmpty();
		assertThat(target.collectColumnValuesBelow(rows, null)).isEmpty();
		assertThat(target.collectColumnValuesBelow(rows, Set.of())).isEmpty();
	}

	@Test
	void shouldSkipNullRowsAroundHeaderAndDedupeCaseInsensitivelyTest() {
		// Given: null rows before and after the header, and the same value in two casings
		SheetRowHelperImpl target = new SheetRowHelperImpl(new CurrentTimeImpl());
		List<List<String>> rows = Arrays.asList(
				null,
				List.of("Geo"),
				null,
				List.of("Texas"),
				List.of("TEXAS"),
				List.of("Ohio"));

		// When:
		List<String> values = target.collectColumnValuesBelow(rows, Set.of("geo"));

		// Then:
		assertThat(values).containsExactly("Texas", "Ohio");
	}

	@Test
	void shouldFindHeaderColumnOrMinusOneTest() {
		// Given:
		SheetRowHelperImpl target = new SheetRowHelperImpl(new CurrentTimeImpl());
		List<String> header = List.of("Name", "Line #");

		// When-Then:
		assertThat(target.findHeaderColumn(header, Set.of("line"))).isEqualTo(1);
		assertThat(target.findHeaderColumn(header, Set.of("geo"))).isEqualTo(-1);
		assertThat(target.findHeaderColumn(null, Set.of("line"))).isEqualTo(-1);
		assertThat(target.findHeaderColumn(header, null)).isEqualTo(-1);
	}

	@Test
	void shouldDetectDataDateRangeAcrossFormatsSkippingUnusableRowsTest() {
		// Given: partial header candidates, then the full header, then null/empty/unparseable/valid date rows
		SheetRowHelperImpl target = new SheetRowHelperImpl(new CurrentTimeImpl());
		List<List<String>> rows = Arrays.asList(
				null,
				List.of("Date", "Other"),
				List.of("Date", "Channel"),
				List.of("Date", "Channel", "Cost"),
				List.of("Channel", "Cost", "Impressions"),
				List.of("Date", "Channel", "Cost", "Impressions"),
				null,
				List.of("", "Display"),
				List.of("not a date", "Display"),
				List.of("Mar 5, 2026", "Display"),
				List.of("2026-01-15", "Display"),
				List.of("03/20/2026", "Display"),
				List.of("02/01/2026", "Display"));

		// When:
		FlightDates range = target.detectDataDateRange(rows);

		// Then:
		assertThat(range).isEqualTo(new FlightDates(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 3, 20)));
	}

	@Test
	void shouldReturnNoDataDateRangeWithoutHeaderOrDatesTest() {
		// Given:
		SheetRowHelperImpl target = new SheetRowHelperImpl(new CurrentTimeImpl());
		List<List<String>> noHeader = List.of(List.of("Date", "Cost"), List.of("2026-01-01", "1"));
		List<List<String>> noDates = List.of(List.of("Date", "Channel", "Cost", "Impressions"), List.of("", "x"));

		// When-Then:
		assertThat(target.detectDataDateRange(null)).isNull();
		assertThat(target.detectDataDateRange(noHeader)).isNull();
		assertThat(target.detectDataDateRange(noDates)).isNull();
	}

	@Test
	void shouldFormatFlightDatesForSingleDaySameYearAndCrossYearTest() {
		// Given:
		SheetRowHelperImpl target = new SheetRowHelperImpl(new CurrentTimeImpl());
		LocalDate start = LocalDate.of(2025, 12, 1);

		// When-Then:
		assertThat(target.formatFlightDates(start, null)).isEqualTo("Dec 1, 2025");
		assertThat(target.formatFlightDates(start, start)).isEqualTo("Dec 1, 2025");
		assertThat(target.formatFlightDates(start, LocalDate.of(2025, 12, 31))).isEqualTo("Dec 1 – Dec 31, 2025");
		assertThat(target.formatFlightDates(start, LocalDate.of(2026, 1, 5)))
				.isEqualTo("Dec 1, 2025 – Jan 5, 2026");
	}

	@Test
	void shouldParseDatesInSheetFormatsAndAssumeCurrentYearWhenMissingTest() {
		// Given: a clock in 2031
		CurrentTime currentTime = mock(CurrentTime.class);
		when(currentTime.nowLocalDate()).thenReturn(LocalDate.of(2031, 6, 1));
		SheetRowHelperImpl target = new SheetRowHelperImpl(currentTime);

		// When-Then:
		assertThat(target.parseDate(null)).isNull();
		assertThat(target.parseDate("   ")).isNull();
		assertThat(target.parseDate("march 5, 2026")).isEqualTo(LocalDate.of(2026, 3, 5));
		assertThat(target.parseDate("5 Mar 2026")).isEqualTo(LocalDate.of(2026, 3, 5));
		assertThat(target.parseDate("3/5/26")).isEqualTo(LocalDate.of(2026, 3, 5));
		assertThat(target.parseDate("Mar 5")).isEqualTo(LocalDate.of(2031, 3, 5));
		assertThat(target.parseDate("5 March")).isEqualTo(LocalDate.of(2031, 3, 5));
		assertThat(target.parseDate("someday")).isNull();
	}

	@Test
	void shouldReadCellsSafelyForNullRowsCellsAndOutOfRangeIndexesTest() {
		// Given:
		SheetRowHelperImpl target = new SheetRowHelperImpl(new CurrentTimeImpl());
		List<String> row = Arrays.asList(" a ", null);

		// When-Then:
		assertThat(target.cellAt(row, 0)).isEqualTo("a");
		assertThat(target.cellAt(row, 1)).isEmpty();
		assertThat(target.cellAt(null, 0)).isEmpty();
		assertThat(target.cellAt(row, -1)).isEmpty();
		assertThat(target.cellAt(row, 2)).isEmpty();
	}

	@Test
	void shouldJoinLeadingCellsLowerCasedUpToRowLengthTest() {
		// Given:
		SheetRowHelperImpl target = new SheetRowHelperImpl(new CurrentTimeImpl());
		List<String> row = Arrays.asList(" Total ", null, "SPEND");

		// When-Then:
		assertThat(target.joinLower(row, 5)).isEqualTo("total  spend");
		assertThat(target.joinLower(row, 1)).isEqualTo("total");
		assertThat(target.joinLower(row, 0)).isEmpty();
	}
}
