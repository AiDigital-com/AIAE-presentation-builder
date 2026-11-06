package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.enums.PaidChannelKind;
import com.aidigital.reportconstructor.service.reports.enums.PaidExportColumn;
import com.aidigital.reportconstructor.service.reports.enums.PaidExportTab;
import com.aidigital.reportconstructor.service.reports.model.PaidAd;
import com.aidigital.reportconstructor.service.reports.model.PaidChannelData;
import com.aidigital.reportconstructor.service.reports.model.PaidMetrics;
import com.aidigital.reportconstructor.service.reports.model.PaidMonth;
import com.aidigital.reportconstructor.service.reports.model.PaidRow;
import com.aidigital.reportconstructor.service.reports.model.PaidTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Turns one paid-media export into the {@link PaidChannelData} its Paid Search / Paid Social slides print.
 *
 * <p>Totals, campaigns (Search), creatives (Social) and the monthly series are summed from the daily FULL-FLIGHT
 * rows inside the report window, so the campaign table's TOTAL always equals the headline figures. The breakdown
 * tabs are read as the export aggregated them; rows sharing a label (a placement across platforms, an age band
 * across ad sets) are summed into one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaidExportCollector {

	/** Highest conversions first, then highest clicks: the order every ranked paid table uses. */
	private static final Comparator<PaidMetrics> RANKING = Comparator
			.comparingDouble(PaidMetrics::conversions)
			.thenComparingDouble(PaidMetrics::clicks)
			.reversed();

	private final PaidExportTableReader reader;

	/**
	 * Parses one channel's export.
	 *
	 * @param kind   the channel the export belongs to
	 * @param tabs   the export's tabs keyed by tab name (may be {@code null} or empty when not connected)
	 * @param window the report's inclusive date window, or {@code null} for the export's whole range
	 * @return the channel's data, or empty when nothing was connected or its FULL-FLIGHT tab is unusable
	 */
	public Optional<PaidChannelData> collect(
			PaidChannelKind kind, Map<String, List<List<String>>> tabs, FlightDates window) {
		if (tabs == null || tabs.isEmpty()) {
			return Optional.empty();
		}
		Optional<PaidTable> fullFlight = reader.read(tabs, PaidExportTab.FULL_FLIGHT);
		if (fullFlight.isEmpty()) {
			log.warn("[paid] {} export has no usable {} tab — its slides are left out", kind,
					PaidExportTab.FULL_FLIGHT.tabName());
			return Optional.empty();
		}
		PaidTable daily = fullFlight.get();
		List<List<String>> inWindow = rowsInWindow(daily, window);
		List<PaidExportTab> missing = kind.breakdownTabs().stream()
				.filter(t -> reader.read(tabs, t).isEmpty())
				.toList();
		if (!missing.isEmpty()) {
			log.warn("[paid] {} export lacks breakdown tabs {} — the slides built from them stay empty", kind,
					missing.stream().map(PaidExportTab::tabName).toList());
		}
		boolean search = kind == PaidChannelKind.SEARCH;
		return Optional.of(new PaidChannelData(
				kind,
				sum(daily, inWindow),
				previousMonth(daily, window),
				months(daily, inWindow),
				search ? groupDaily(daily, inWindow, PaidExportColumn.LEVEL_1_NAMING) : List.of(),
				search ? List.of() : groupDaily(daily, inWindow, PaidExportColumn.CREATIVE),
				search ? breakdown(tabs, PaidExportTab.GROUP_BREAKDOWN, PaidExportColumn.CAMPAIGN, true) : List.of(),
				search ? breakdown(tabs, PaidExportTab.KEYWORD_BREAKDOWN, PaidExportColumn.CAMPAIGN, true) : List.of(),
				search ? breakdown(tabs, PaidExportTab.PMAX_PLACEMENT_BREAKDOWN, null, true) : List.of(),
				breakdown(tabs, PaidExportTab.AGE_DISTRIBUTION, null, false),
				breakdown(tabs, PaidExportTab.GENDER_SPLIT, null, false),
				search ? ads(tabs) : List.of(),
				search ? List.of() : breakdown(tabs, PaidExportTab.AUDIENCE_TARGETING_BREAKDOWN, null, true),
				search ? List.of() : breakdown(tabs, PaidExportTab.PLACEMENT_BREAKDOWN, null, true),
				missing));
	}

	/**
	 * Keeps the dated FULL-FLIGHT rows that fall inside the window; the undated zero row under the header and any
	 * row whose Date does not parse are dropped.
	 *
	 * @param daily  the FULL-FLIGHT table
	 * @param window the inclusive window, or {@code null} for every dated row
	 * @return the rows to sum
	 */
	List<List<String>> rowsInWindow(PaidTable daily, FlightDates window) {
		List<List<String>> kept = new ArrayList<>();
		for (List<String> row : daily.rows()) {
			LocalDate d = reader.date(daily, row);
			if (d != null && (window == null || !d.isBefore(window.start()) && !d.isAfter(window.end()))) {
				kept.add(row);
			}
		}
		return kept;
	}

	/**
	 * @param table the table the rows belong to
	 * @param rows  the rows to sum
	 * @return the rows' summed delivery
	 */
	PaidMetrics sum(PaidTable table, List<List<String>> rows) {
		PaidMetrics total = PaidMetrics.ZERO;
		for (List<String> row : rows) {
			total = total.plus(reader.metrics(table, row));
		}
		return total;
	}

	/**
	 * Sums the FULL-FLIGHT rows of the calendar month before the window starts, for the EOM month-on-month line.
	 *
	 * @param daily  the FULL-FLIGHT table
	 * @param window the report window, or {@code null}
	 * @return the previous month's delivery, or {@code null} without a window or without rows that month
	 */
	PaidMetrics previousMonth(PaidTable daily, FlightDates window) {
		if (window == null) {
			return null;
		}
		YearMonth prev = YearMonth.from(window.start()).minusMonths(1);
		List<List<String>> rows = rowsInWindow(daily, new FlightDates(prev.atDay(1), prev.atEndOfMonth()));
		return rows.isEmpty() ? null : sum(daily, rows);
	}

	/**
	 * @param daily the FULL-FLIGHT table
	 * @param rows  the dated rows inside the window
	 * @return the delivery per calendar month, in date order
	 */
	List<PaidMonth> months(PaidTable daily, List<List<String>> rows) {
		Map<YearMonth, PaidMetrics> byMonth = new TreeMap<>();
		for (List<String> row : rows) {
			byMonth.merge(YearMonth.from(reader.date(daily, row)), reader.metrics(daily, row), PaidMetrics::plus);
		}
		return byMonth.entrySet().stream().map(e -> new PaidMonth(e.getKey(), e.getValue())).toList();
	}

	/**
	 * Sums the FULL-FLIGHT rows by one label column (campaign type or creative), ranked.
	 *
	 * @param daily the FULL-FLIGHT table
	 * @param rows  the dated rows inside the window
	 * @param by    the label column
	 * @return one row per distinct non-blank label, ranked; empty when the export has no such column
	 */
	List<PaidRow> groupDaily(PaidTable daily, List<List<String>> rows, PaidExportColumn by) {
		if (!daily.has(by)) {
			return List.of();
		}
		return group(daily, rows, by, null, true);
	}

	/**
	 * Reads one breakdown tab into rows, summing rows that share a label (and parent).
	 *
	 * @param tabs   the export's tabs
	 * @param tab    the breakdown tab
	 * @param parent the column naming each row's campaign, or {@code null} when the breakdown has none
	 * @param ranked whether to rank the rows ({@code false} keeps the export's order, e.g. age bands)
	 * @return the rows; empty when the tab is missing
	 */
	List<PaidRow> breakdown(
			Map<String, List<List<String>>> tabs, PaidExportTab tab, PaidExportColumn parent, boolean ranked) {
		return reader.read(tabs, tab)
				.map(t -> group(t, t.rows(), tab.keyColumn(), parent, ranked))
				.orElse(List.of());
	}

	/**
	 * Sums rows by label (and parent), keeping first-seen order unless ranked.
	 *
	 * @param table  the table the rows belong to
	 * @param rows   the rows to group
	 * @param by     the label column
	 * @param parent the parent column, or {@code null}
	 * @param ranked whether to rank the result
	 * @return one row per distinct non-blank label (and parent)
	 */
	List<PaidRow> group(
			PaidTable table, List<List<String>> rows, PaidExportColumn by, PaidExportColumn parent, boolean ranked) {
		Map<List<String>, PaidMetrics> grouped = new LinkedHashMap<>();
		for (List<String> row : rows) {
			String name = reader.text(table, row, by);
			if (name.isEmpty()) {
				continue;
			}
			String owner = parent == null ? "" : reader.text(table, row, parent);
			grouped.merge(List.of(name, owner), reader.metrics(table, row), PaidMetrics::plus);
		}
		Stream<PaidRow> out = grouped.entrySet().stream()
				.map(e -> new PaidRow(e.getKey().get(0), e.getKey().get(1), e.getValue()));
		return ranked ? rank(out, PaidRow::metrics) : out.toList();
	}

	/**
	 * Reads the AD BREAKDOWN tab into ads, ranked.
	 *
	 * @param tabs the export's tabs
	 * @return the ads; empty when the tab is missing
	 */
	List<PaidAd> ads(Map<String, List<List<String>>> tabs) {
		return reader.read(tabs, PaidExportTab.AD_BREAKDOWN)
				.map(t -> rank(t.rows().stream().map(row -> new PaidAd(
						reader.text(t, row, PaidExportColumn.CAMPAIGN),
						reader.text(t, row, PaidExportColumn.GROUP),
						nonBlank(t, row, PaidExportColumn.HEADLINE_1, PaidExportColumn.HEADLINE_2,
								PaidExportColumn.HEADLINE_3),
						nonBlank(t, row, PaidExportColumn.DESCRIPTION_1, PaidExportColumn.DESCRIPTION_2),
						reader.text(t, row, PaidExportColumn.LANDING_PAGE),
						reader.metrics(t, row))), PaidAd::metrics))
				.orElse(List.of());
	}

	/**
	 * @param table   the table the row belongs to
	 * @param row     the data row
	 * @param columns the columns to read, in order
	 * @return the non-blank cell texts, in column order
	 */
	List<String> nonBlank(PaidTable table, List<String> row, PaidExportColumn... columns) {
		return Stream.of(columns).map(c -> reader.text(table, row, c)).filter(s -> !s.isEmpty()).toList();
	}

	/**
	 * @param items   the items to rank
	 * @param metrics how to get an item's delivery
	 * @param <T>     the item type
	 * @return the items, highest conversions then clicks first (stable for ties)
	 */
	<T> List<T> rank(Stream<T> items, Function<T, PaidMetrics> metrics) {
		return items.sorted(Comparator.comparing(metrics, RANKING)).toList();
	}
}
