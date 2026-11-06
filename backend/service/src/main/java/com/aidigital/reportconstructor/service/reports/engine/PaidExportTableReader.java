package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.enums.PaidExportColumn;
import com.aidigital.reportconstructor.service.reports.enums.PaidExportTab;
import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import com.aidigital.reportconstructor.service.reports.model.PaidMetrics;
import com.aidigital.reportconstructor.service.reports.model.PaidTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the tabs of a paid-media export (Google Ads / Meta "raw data for automation") into {@link PaidTable}s.
 *
 * <p>The exports lay their tabs out loosely: FULL-FLIGHT has its header in row 1 and a zero totals row under it;
 * the breakdown tabs carry a title in row 1, the header in row 2 and the export's own TOTAL row last; every tab may
 * be padded with blank rows. So the header row is searched for among the top rows, columns are found by header
 * text, and blank and TOTAL rows are dropped.
 */
@Component
@RequiredArgsConstructor
public class PaidExportTableReader {

	/** How many top rows are searched for the header row. */
	private static final int HEADER_SCAN_ROWS = 5;

	/** Last word of the export's own total rows ({@code "GOOGLE TOTAL"}, {@code "META TOTAL"}, {@code "TOTAL"}). */
	private static final String TOTAL_WORD = "total";

	/** Day zero of Google Sheets date serial numbers, for a Date cell that comes through unformatted. */
	private static final LocalDate SHEETS_EPOCH = LocalDate.of(1899, 12, 30);

	/** A bare date serial (e.g. {@code 46302} for 2026-10-07), optionally with a time fraction. */
	private static final Pattern DATE_SERIAL = Pattern.compile("\\d{5}(\\.\\d+)?");

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private final ReportNumberParser numbers;
	private final SheetRowHelper sheetRows;

	/**
	 * Reads one export tab.
	 *
	 * @param tabs the export's tabs keyed by tab name, as the UI sent them (may be {@code null})
	 * @param tab  the tab to read
	 * @return the tab's data, or empty when the export has no such tab or no top row carries its required columns
	 */
	public Optional<PaidTable> read(Map<String, List<List<String>>> tabs, PaidExportTab tab) {
		List<List<String>> rows = findTab(tabs, tab.tabName());
		if (rows == null) {
			return Optional.empty();
		}
		int headerRow = findHeaderRow(rows, tab.requiredColumns());
		if (headerRow < 0) {
			return Optional.empty();
		}
		List<List<String>> data = new ArrayList<>();
		for (List<String> row : rows.subList(headerRow + 1, rows.size())) {
			if (row != null && !isBlank(row) && !isTotalRow(row)) {
				data.add(row);
			}
		}
		return Optional.of(new PaidTable(mapColumns(rows.get(headerRow)), data));
	}

	/**
	 * Finds a tab by name, trimmed and case-insensitively.
	 *
	 * @param tabs    the export's tabs (may be {@code null})
	 * @param tabName the tab name to look for
	 * @return the tab's grid, or {@code null} when absent
	 */
	private List<List<String>> findTab(Map<String, List<List<String>>> tabs, String tabName) {
		if (tabs == null) {
			return null;
		}
		String wanted = normalise(tabName);
		for (Map.Entry<String, List<List<String>>> e : tabs.entrySet()) {
			if (normalise(e.getKey()).equals(wanted)) {
				return e.getValue();
			}
		}
		return null;
	}

	/**
	 * Finds the header row: the first of the top rows that carries every required column.
	 *
	 * @param rows     the tab grid
	 * @param required the columns the header must carry
	 * @return the header row index, or {@code -1} when no top row has them all
	 */
	private int findHeaderRow(List<List<String>> rows, Set<PaidExportColumn> required) {
		int limit = Math.min(rows.size(), HEADER_SCAN_ROWS);
		for (int i = 0; i < limit; i++) {
			List<String> row = rows.get(i);
			if (row != null && mapColumns(row).keySet().containsAll(required)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Maps a header row's cells to export columns; the first cell naming a column wins.
	 *
	 * @param header the header row
	 * @return each recognised column's zero-based index
	 */
	private Map<PaidExportColumn, Integer> mapColumns(List<String> header) {
		Map<PaidExportColumn, Integer> columns = new EnumMap<>(PaidExportColumn.class);
		for (int i = 0; i < header.size(); i++) {
			String cell = normalise(header.get(i));
			for (PaidExportColumn column : PaidExportColumn.values()) {
				if (column.matches(cell)) {
					columns.putIfAbsent(column, i);
				}
			}
		}
		return columns;
	}

	/**
	 * @param row a data row
	 * @return {@code true} when every cell is blank
	 */
	boolean isBlank(List<String> row) {
		return row.stream().allMatch(c -> normalise(c).isEmpty());
	}

	/**
	 * Reports whether a row is the export's own total row: its first non-blank cell is {@code "TOTAL"} or ends
	 * with the word (e.g. {@code "GOOGLE TOTAL"}, {@code "META TOTAL"}).
	 *
	 * @param row a data row
	 * @return {@code true} for a total row
	 */
	boolean isTotalRow(List<String> row) {
		for (String cell : row) {
			String c = normalise(cell);
			if (!c.isEmpty()) {
				return c.equals(TOTAL_WORD) || c.endsWith(" " + TOTAL_WORD);
			}
		}
		return false;
	}

	/**
	 * Reads a text cell.
	 *
	 * @param table  the table the row belongs to
	 * @param row    the data row
	 * @param column the column to read
	 * @return the trimmed cell text, or {@code ""} when the column or cell is absent
	 */
	public String text(PaidTable table, List<String> row, PaidExportColumn column) {
		Integer idx = table.columns().get(column);
		return idx == null ? "" : sheetRows.cellAt(row, idx);
	}

	/**
	 * Reads a numeric cell, tolerating currency signs, percent signs and thousands separators.
	 *
	 * @param table  the table the row belongs to
	 * @param row    the data row
	 * @param column the column to read
	 * @return the number, or {@code 0} when the cell is absent, blank or not numeric
	 */
	public double number(PaidTable table, List<String> row, PaidExportColumn column) {
		return numbers.parseReportNumber(text(table, row, column));
	}

	/**
	 * Reads a row's four delivery metrics.
	 *
	 * @param table the table the row belongs to
	 * @param row   the data row
	 * @return the row's impressions, clicks, cost and conversions
	 */
	public PaidMetrics metrics(PaidTable table, List<String> row) {
		return new PaidMetrics(
				number(table, row, PaidExportColumn.IMPRESSIONS),
				number(table, row, PaidExportColumn.CLICKS),
				number(table, row, PaidExportColumn.COST),
				number(table, row, PaidExportColumn.CONVERSIONS));
	}

	/**
	 * Reads a row's date: a formatted date ({@code 2026-10-07}, {@code Oct 7, 2026}...) or a bare sheet serial.
	 *
	 * @param table the table the row belongs to
	 * @param row   the data row
	 * @return the date, or {@code null} when the cell is blank or not a date (e.g. the FULL-FLIGHT zero row)
	 */
	public LocalDate date(PaidTable table, List<String> row) {
		String raw = text(table, row, PaidExportColumn.DATE);
		if (DATE_SERIAL.matcher(raw).matches()) {
			return SHEETS_EPOCH.plusDays((long) Double.parseDouble(raw));
		}
		return sheetRows.parseDate(raw);
	}

	/**
	 * @param s a header or tab name (may be {@code null})
	 * @return the text trimmed, lower-cased and with whitespace runs collapsed to one space
	 */
	String normalise(String s) {
		return s == null ? "" : WHITESPACE.matcher(s.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
	}
}
