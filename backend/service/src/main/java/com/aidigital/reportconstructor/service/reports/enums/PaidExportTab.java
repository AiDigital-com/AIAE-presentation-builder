package com.aidigital.reportconstructor.service.reports.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * A tab of a paid-media export workbook that feeds the MPO deck's Paid Search / Paid Social slides. The UI sends
 * the tabs keyed by the workbook's own spelling; they are looked up by {@link #tabName()} trimmed and
 * case-insensitively. A tab is usable only when one of its top rows carries every {@link #requiredColumns()}.
 */
public enum PaidExportTab {

	/** The daily delivery table every export carries; totals, campaigns, creatives and months come from it. */
	FULL_FLIGHT("FULL-FLIGHT PERFORMANCE", PaidExportColumn.DATE),
	GROUP_BREAKDOWN("GROUP BREAKDOWN", PaidExportColumn.GROUP),
	KEYWORD_BREAKDOWN("KEYWORD BREAKDOWN", PaidExportColumn.KEYWORD),
	PMAX_PLACEMENT_BREAKDOWN("PMAX PLACEMENT BREAKDOWN", PaidExportColumn.CHANNEL),
	AGE_DISTRIBUTION("AGE DISTRIBUTION", PaidExportColumn.AGE),
	GENDER_SPLIT("GENDER SPLIT", PaidExportColumn.GENDER),
	AD_BREAKDOWN("AD BREAKDOWN", PaidExportColumn.HEADLINE_1),
	PLACEMENT_BREAKDOWN("PLACEMENT BREAKDOWN", PaidExportColumn.PLACEMENT),
	AUDIENCE_TARGETING_BREAKDOWN("AUDIENCE TARGETING BREAKDOWN", PaidExportColumn.AD_SET);

	private final String tabName;
	private final PaidExportColumn keyColumn;

	/**
	 * @param tabName   the tab name as the exports spell it
	 * @param keyColumn the column each row of the tab is about
	 */
	PaidExportTab(String tabName, PaidExportColumn keyColumn) {
		this.tabName = tabName;
		this.keyColumn = keyColumn;
	}

	/**
	 * @return the tab name as the exports spell it
	 */
	public String tabName() {
		return tabName;
	}

	/**
	 * @return the column each row of this tab is about (the date for FULL-FLIGHT, the breakdown dimension otherwise)
	 */
	public PaidExportColumn keyColumn() {
		return keyColumn;
	}

	/**
	 * @return the key column plus the four delivery metrics, all of which the header row must carry
	 */
	public Set<PaidExportColumn> requiredColumns() {
		return EnumSet.of(keyColumn, PaidExportColumn.IMPRESSIONS, PaidExportColumn.CLICKS, PaidExportColumn.COST,
				PaidExportColumn.CONVERSIONS);
	}
}
