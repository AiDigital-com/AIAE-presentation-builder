package com.aidigital.reportconstructor.service.reports.model;

import com.aidigital.reportconstructor.service.reports.enums.PaidTableMarker;

import java.util.List;

/**
 * The cell values of one Paid Search / Paid Social slide table, formatted for the deck, in the template's column
 * order. The template draws a fixed number of data rows; rows beyond {@code bodyRows} are deleted, and body rows
 * beyond the template's are dropped (the rows are ranked, so the cut keeps the best).
 *
 * @param marker   the table, found in the deck by its alt-text title
 * @param bodyRows the data rows, one value per cell; empty when the export has no data for the table
 * @param totalRow the TOTAL row written into the table's last row, or {@code null} for a table without one
 */
public record PaidSlideTable(PaidTableMarker marker, List<List<String>> bodyRows, List<String> totalRow) {

	/**
	 * @return {@code true} when the table's last row is a TOTAL row
	 */
	public boolean hasTotalRow() {
		return totalRow != null;
	}
}
