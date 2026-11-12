package com.aidigital.reportconstructor.externalservices.google.sheets.model;

import java.util.List;

/**
 * One tactic's hand-entered "Geo analysis" block on the generated workbook's "Breakdowns" tab:
 * the three summary cells above the table plus the table's filled rows.
 *
 * <p>The summary cells are read as typed rather than derived from {@link #rows()} — the user may
 * scope them differently from the top-eight table (markets activated counts every market, not just
 * the eight shown), so recomputing them here would contradict the sheet the user reviewed.
 *
 * <p>The Google integration's copy of the service type of the same shape; the bridge maps between
 * them and {@code WorkbookModelMapperTest} pins the components.
 *
 * @param marketsActivated the {@code MARKETS ACTIVATED} count
 * @param topGeo           the {@code TOP GEO} name
 * @param topKpi           the {@code MOST EFFICIENT} geo's KPI value, whichever KPI the tactic is led by
 * @param rows             the table's filled rows, in sheet order; never padded with empty rows
 */
public record WorkbookGeoTable(String marketsActivated, String topGeo, String topKpi, List<WorkbookGeoRow> rows) {

	/**
	 * The empty table, used for a tactic whose block is missing from the sheet or was left entirely
	 * blank, so callers never have to null-check the block itself. Immutable and safely shared.
	 */
	public static final WorkbookGeoTable EMPTY = new WorkbookGeoTable("", "", "", List.of());

	/**
	 * Reports whether the user filled in nothing at all for this tactic. Such a tactic still gets its
	 * slide (the toggle was on) but is never sent to Claude — there would be nothing to observe.
	 *
	 * @return true when every summary cell is blank and the table carries no rows
	 */
	public boolean isEmpty() {
		return rows.isEmpty()
				&& (marketsActivated == null || marketsActivated.isBlank())
				&& (topGeo == null || topGeo.isBlank())
				&& (topKpi == null || topKpi.isBlank());
	}
}
