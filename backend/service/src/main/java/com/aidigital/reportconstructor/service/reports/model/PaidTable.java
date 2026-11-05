package com.aidigital.reportconstructor.service.reports.model;

import com.aidigital.reportconstructor.service.reports.enums.PaidExportColumn;

import java.util.List;
import java.util.Map;

/**
 * A paid-media export tab cut down to its data: the columns found in its header row and the rows beneath it,
 * without blank rows and without the export's own TOTAL rows.
 *
 * @param columns the zero-based index of each column the header row carries
 * @param rows    the data rows, in sheet order
 */
public record PaidTable(Map<PaidExportColumn, Integer> columns, List<List<String>> rows) {

	/**
	 * @param column the column to look for
	 * @return {@code true} when the header row carries the column
	 */
	public boolean has(PaidExportColumn column) {
		return columns.containsKey(column);
	}
}
