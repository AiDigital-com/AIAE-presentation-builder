package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChartPivotTest {

	private ChartPivot pivot;

	@BeforeEach
	void setUp() {
		pivot = ReportsEngineTestSupport.chartPivot();
	}

	@Test
	void parseBqHeaders_findsDateImpsClicksL1Naming() {
		List<List<String>> rows = List.of(
				List.of("foo", "bar"),
				List.of("Date", "Impressions", "Clicks", "Level 1 Naming")
		);
		Headers h = pivot.parseBqHeaders(rows);
		assertThat(h.valid()).isTrue();
		assertThat(h.dateCol()).isZero();
		assertThat(h.impsCol()).isEqualTo(1);
		assertThat(h.clicksCol()).isEqualTo(2);
		assertThat(h.l1NamingCol()).isEqualTo(3);
	}

	@Test
	void extractLiIdFromL1Naming_usesNinthSegment() {
		String naming = "a_b_c_d_e_f_g_h_12345_x";
		assertThat(pivot.extractLiIdFromL1Naming(naming)).isEqualTo("12345");
		assertThat(pivot.extractLiIdFromL1Naming("a_b_c")).isEmpty();
	}

	@Test
	void buildDailyPivot_filtersByLineItemAndFlightWindow() {
		List<List<String>> rows = List.of(
				List.of("Date", "Impressions", "Clicks", "Level 1 Naming"),
				List.of("2026-03-01", "100", "5", "x_x_x_x_x_x_x_x_11111_y"),
				List.of("2026-03-02", "200", "10", "x_x_x_x_x_x_x_x_22222_y"),
				List.of("2026-03-15", "999", "99", "x_x_x_x_x_x_x_x_11111_y")
		);
		Headers h = pivot.parseBqHeaders(rows);
		FlightDates flight = new FlightDates(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10));
		Pivot daily = pivot.buildDailyPivot(rows, List.of("11111"), h, flight);

		assertThat(daily.hasClicks()).isTrue();
		assertThat(daily.data()).hasSize(1);
		assertThat(daily.data().get("2026-03-01")[0]).isEqualTo(100.0);
		assertThat(daily.data().get("2026-03-01")[1]).isEqualTo(5.0);
	}

	@Test
	void buildMonthlyPivot_multiYearLabelsIncludeYear() {
		List<List<String>> rows = List.of(
				List.of("Date", "Impressions", "Completions", "Level 1 Naming"),
				List.of("2025-12-15", "50", "2", "x_x_x_x_x_x_x_x_99_y"),
				List.of("2026-01-10", "60", "3", "x_x_x_x_x_x_x_x_99_y")
		);
		Headers h = pivot.parseBqHeaders(rows);
		Pivot monthly = pivot.buildMonthlyPivot(rows, List.of("99"), h, null, true);

		assertThat(monthly.hasCompletions()).isTrue();
		assertThat(monthly.data().keySet()).containsExactly("December 2025", "January 2026");
	}

	@Test
	void isMultiYear_usesFlightDatesWhenPresent() {
		FlightDates flight = new FlightDates(LocalDate.of(2025, 11, 1), LocalDate.of(2026, 2, 1));
		assertThat(pivot.isMultiYear(List.of(), new Headers(0, 1, -1, -1, -1), flight)).isTrue();
	}

	@Test
	void shouldReturnInvalidHeadersWhenThereAreNoRowsTest() {
		// Given:
		ChartPivot chartPivot = ReportsEngineTestSupport.chartPivot();

		// When:
		Headers headers = chartPivot.parseBqHeaders(null);

		// Then:
		assertThat(headers).isEqualTo(new Headers(-1, -1, -1, -1, -1));
		assertThat(headers.valid()).isFalse();
	}

	@Test
	void shouldSkipNullRowsAndReadHeaderSynonymsUpToTheDateRowTest() {
		// Given: a null row, a preamble row without a Date column, then the real header row
		ChartPivot chartPivot = ReportsEngineTestSupport.chartPivot();
		List<List<String>> rows = Arrays.asList(
				null,
				List.of("Account", "imps"),
				List.of("DATE", "imps", "click", "complete", "l1_naming", "Spend"),
				List.of("level 1 naming", "Impressions"));

		// When:
		Headers headers = chartPivot.parseBqHeaders(rows);

		// Then: scanning stops at the Date row, so the row after it is never read
		assertThat(headers).isEqualTo(new Headers(0, 1, 2, 3, 4));
	}

	@Test
	void shouldReportMissingDateColumnWhenNoRowCarriesOneTest() {
		// Given: an export whose rows never name a Date column
		ChartPivot chartPivot = ReportsEngineTestSupport.chartPivot();
		List<List<String>> rows = List.of(
				List.of("Impressions", "Clicks"),
				List.of("100", "5"));

		// When:
		Headers headers = chartPivot.parseBqHeaders(rows);

		// Then:
		assertThat(headers).isEqualTo(new Headers(-1, 0, 1, -1, -1));
		assertThat(headers.valid()).isFalse();
	}

	@Test
	void shouldIgnoreLineItemIdsInTheMonthlyPivotWithoutANamingColumnTest() {
		// Given: a header row without a Level 1 Naming column, and line-item ids that cannot be matched
		ChartPivot chartPivot = ReportsEngineTestSupport.chartPivot();
		List<List<String>> rows = List.of(
				List.of("Date", "Impressions", "Completions"),
				List.of("2026-05-03", "40", "4"),
				List.of("2026-05-20", "60", "6"));
		Headers headers = chartPivot.parseBqHeaders(rows);

		// When:
		Pivot monthly = chartPivot.buildMonthlyPivot(rows, List.of("11111"), headers, null, true);

		// Then: nothing is filtered out and the label carries the year
		assertThat(monthly.data().keySet()).containsExactly("May 2026");
		assertThat(monthly.data().get("May 2026")).containsExactly(100.0, 0.0, 10.0);
		assertThat(monthly.hasClicks()).isFalse();
		assertThat(monthly.hasCompletions()).isTrue();
	}

	@Test
	void shouldBuildAnEmptyDailyPivotWhenThereAreNoRowsTest() {
		// Given:
		ChartPivot chartPivot = ReportsEngineTestSupport.chartPivot();

		// When:
		Pivot daily = chartPivot.buildDailyPivot(null, List.of(), new Headers(0, 1, 2, 3, -1), null);

		// Then:
		assertThat(daily.isEmpty()).isTrue();
		assertThat(daily.hasClicks()).isFalse();
		assertThat(daily.hasCompletions()).isFalse();
	}

	@Test
	void shouldPivotEveryDatedRowDailyWhenNoLineItemOrFlightFilterAppliesTest() {
		// Given: a completions-only export with a blank date and an unparseable date, and no id filter
		ChartPivot chartPivot = ReportsEngineTestSupport.chartPivot();
		List<List<String>> rows = List.of(
				List.of("Date", "Impressions", "Completions"),
				List.of("", "5", "1"),
				List.of("not a date", "7", "1"),
				List.of("2026-03-01", "100", "10"),
				List.of("2026-03-01", "50", "5"),
				List.of("2026-03-02", "20", "0"));
		Headers headers = chartPivot.parseBqHeaders(rows);

		// When:
		Pivot daily = chartPivot.buildDailyPivot(rows, null, headers, null);

		// Then: same-day rows are summed and no clicks column means no CTR series
		assertThat(daily.data().keySet()).containsExactly("2026-03-01", "2026-03-02");
		assertThat(daily.data().get("2026-03-01")).containsExactly(150.0, 0.0, 15.0);
		assertThat(daily.data().get("2026-03-02")).containsExactly(20.0, 0.0, 0.0);
		assertThat(daily.hasClicks()).isFalse();
		assertThat(daily.hasCompletions()).isTrue();
	}

	@Test
	void shouldIgnoreLineItemIdsWhenTheExportHasNoNamingColumnTest() {
		// Given: data rows without a header row and no Level 1 Naming column
		ChartPivot chartPivot = ReportsEngineTestSupport.chartPivot();
		List<List<String>> rows = List.of(
				List.of("2026-03-01", "100", "4"),
				List.of("2026-03-02", "200", "6"));
		Headers headers = new Headers(0, 1, 2, -1, -1);

		// When:
		Pivot daily = chartPivot.buildDailyPivot(rows, List.of("11111"), headers, null);

		// Then: the first row is data, not a header, and nothing is filtered out
		assertThat(daily.data().keySet()).containsExactly("2026-03-01", "2026-03-02");
		assertThat(daily.data().get("2026-03-02")).containsExactly(200.0, 6.0, 0.0);
		assertThat(daily.hasClicks()).isTrue();
		assertThat(daily.hasCompletions()).isFalse();
	}

	@Test
	void shouldDropDailyRowsWithoutALineItemIdOrOutsideTheFlightTest() {
		// Given: rows with a malformed naming, one before and one after the flight, one inside it
		ChartPivot chartPivot = ReportsEngineTestSupport.chartPivot();
		List<List<String>> rows = List.of(
				List.of("Date", "Impressions", "Clicks", "Level 1 Naming"),
				List.of("2026-03-05", "999", "9", "malformed"),
				List.of("2026-02-28", "888", "8", "x_x_x_x_x_x_x_x_11111_y"),
				List.of("2026-03-11", "777", "7", "x_x_x_x_x_x_x_x_11111_y"),
				List.of("2026-03-05", "100", "5", "x_x_x_x_x_x_x_x_11111_y"));
		Headers headers = chartPivot.parseBqHeaders(rows);
		FlightDates flight = new FlightDates(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10));

		// When:
		Pivot daily = chartPivot.buildDailyPivot(rows, List.of("11111"), headers, flight);

		// Then:
		assertThat(daily.data().keySet()).containsExactly("2026-03-05");
		assertThat(daily.data().get("2026-03-05")).containsExactly(100.0, 5.0, 0.0);
	}

	@Test
	void shouldBuildAnEmptyMonthlyPivotWhenThereAreNoRowsTest() {
		// Given:
		ChartPivot chartPivot = ReportsEngineTestSupport.chartPivot();

		// When:
		Pivot monthly = chartPivot.buildMonthlyPivot(null, null, new Headers(0, 1, 2, 3, 4), null, false);

		// Then:
		assertThat(monthly.isEmpty()).isTrue();
		assertThat(monthly.hasClicks()).isFalse();
		assertThat(monthly.hasCompletions()).isFalse();
	}

	@Test
	void shouldPivotMonthlyWithPlainMonthNamesWhenNotFilteredTest() {
		// Given: data rows without a header row, a blank and an unparseable date, and no naming column
		ChartPivot chartPivot = ReportsEngineTestSupport.chartPivot();
		List<List<String>> rows = List.of(
				List.of("2026-03-01", "100", "4"),
				List.of("", "5", "5"),
				List.of("soon", "6", "6"),
				List.of("2026-03-20", "50", "1"),
				List.of("2026-04-02", "30", "0"));
		Headers headers = new Headers(0, 1, 2, -1, -1);

		// When:
		Pivot monthly = chartPivot.buildMonthlyPivot(rows, List.of(), headers, null, false);

		// Then:
		assertThat(monthly.data().keySet()).containsExactly("March", "April");
		assertThat(monthly.data().get("March")).containsExactly(150.0, 5.0, 0.0);
		assertThat(monthly.data().get("April")).containsExactly(30.0, 0.0, 0.0);
		assertThat(monthly.hasClicks()).isTrue();
		assertThat(monthly.hasCompletions()).isFalse();
	}

	@Test
	void shouldDropMonthlyRowsOfOtherLineItemsOrOutsideTheFlightTest() {
		// Given: a foreign line item, a malformed naming, rows before/after the flight and one inside it
		ChartPivot chartPivot = ReportsEngineTestSupport.chartPivot();
		List<List<String>> rows = List.of(
				List.of("Date", "Impressions", "Clicks", "Level 1 Naming"),
				List.of("2026-03-05", "999", "9", "x_x_x_x_x_x_x_x_22222_y"),
				List.of("2026-03-05", "998", "9", "malformed"),
				List.of("2026-02-28", "888", "8", "x_x_x_x_x_x_x_x_11111_y"),
				List.of("2026-04-01", "777", "7", "x_x_x_x_x_x_x_x_11111_y"),
				List.of("2026-03-05", "100", "5", "x_x_x_x_x_x_x_x_11111_y"));
		Headers headers = chartPivot.parseBqHeaders(rows);
		FlightDates flight = new FlightDates(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

		// When:
		Pivot monthly = chartPivot.buildMonthlyPivot(rows, List.of("11111"), headers, flight, false);

		// Then:
		assertThat(monthly.data().keySet()).containsExactly("March");
		assertThat(monthly.data().get("March")).containsExactly(100.0, 5.0, 0.0);
	}

	@Test
	void shouldTreatASingleYearFlightAsNotMultiYearTest() {
		// Given:
		ChartPivot chartPivot = ReportsEngineTestSupport.chartPivot();
		FlightDates flight = new FlightDates(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

		// When:
		boolean multiYear = chartPivot.isMultiYear(null, new Headers(0, 1, -1, -1, -1), flight);

		// Then:
		assertThat(multiYear).isFalse();
	}

	@Test
	void shouldTreatMissingRowsAsNotMultiYearTest() {
		// Given:
		ChartPivot chartPivot = ReportsEngineTestSupport.chartPivot();

		// When:
		boolean multiYear = chartPivot.isMultiYear(null, new Headers(0, 1, -1, -1, -1), null);

		// Then:
		assertThat(multiYear).isFalse();
	}

	@Test
	void shouldDetectASingleYearFromTheRowsWhenThereIsNoFlightTest() {
		// Given: a header row, a blank date, an unparseable date and two dates in the same year
		ChartPivot chartPivot = ReportsEngineTestSupport.chartPivot();
		List<List<String>> rows = List.of(
				List.of("Date", "Impressions"),
				List.of("", "1"),
				List.of("n/a", "1"),
				List.of("2025-01-05", "1"),
				List.of("2025-12-30", "1"));

		// When:
		boolean multiYear = chartPivot.isMultiYear(rows, new Headers(0, 1, -1, -1, -1), null);

		// Then:
		assertThat(multiYear).isFalse();
	}

	@Test
	void shouldDetectMultipleYearsFromTheRowsWhenThereIsNoFlightTest() {
		// Given: data rows without a header row spanning a year boundary
		ChartPivot chartPivot = ReportsEngineTestSupport.chartPivot();
		List<List<String>> rows = List.of(
				List.of("2025-12-30", "1"),
				List.of("2026-01-02", "1"));

		// When:
		boolean multiYear = chartPivot.isMultiYear(rows, new Headers(0, 1, -1, -1, -1), null);

		// Then:
		assertThat(multiYear).isTrue();
	}

	@Test
	void shouldLabelAMonthWithOrWithoutItsYearTest() {
		// Given:
		ChartPivot chartPivot = ReportsEngineTestSupport.chartPivot();
		LocalDate date = LocalDate.of(2026, 3, 14);

		// When:
		String plain = chartPivot.monthLabel(date, false);
		String withYear = chartPivot.monthLabel(date, true);

		// Then:
		assertThat(plain).isEqualTo("March");
		assertThat(withYear).isEqualTo("March 2026");
	}
}
