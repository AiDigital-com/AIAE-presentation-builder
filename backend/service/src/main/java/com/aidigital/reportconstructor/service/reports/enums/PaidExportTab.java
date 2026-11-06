package com.aidigital.reportconstructor.service.reports.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * A tab of a paid-media export workbook that feeds the MPO deck's Paid Search / Paid Social slides. The UI sends
 * the tabs keyed by the workbook's own spelling; they are looked up by {@link #tabName()} trimmed and
 * case-insensitively. A tab is usable only when one of its top rows carries every {@link #requiredColumns()}.
 */
public enum PaidExportTab {

	/**
	 * The daily delivery table every export carries, one row per date. Both channels require it: the window totals,
	 * the previous-month comparison, the per-month delivery, and the Paid Search campaign-type and Paid Social
	 * creative rankings are all summed from it. Without a usable FULL-FLIGHT tab the channel's slides are left out.
	 */
	FULL_FLIGHT("FULL-FLIGHT PERFORMANCE", PaidExportColumn.DATE),

	/**
	 * Paid Search only: delivery per ad group, each row tied to its campaign; ranked by delivery.
	 */
	GROUP_BREAKDOWN("GROUP BREAKDOWN", PaidExportColumn.GROUP),

	/**
	 * Paid Search only: delivery per keyword, each row tied to its campaign; ranked by delivery.
	 */
	KEYWORD_BREAKDOWN("KEYWORD BREAKDOWN", PaidExportColumn.KEYWORD),

	/**
	 * Paid Search only: Performance Max delivery per placement channel; ranked by delivery.
	 */
	PMAX_PLACEMENT_BREAKDOWN("PMAX PLACEMENT BREAKDOWN", PaidExportColumn.CHANNEL),

	/**
	 * Both channels: delivery per age band, kept in the export's own order so the bands stay chronological.
	 */
	AGE_DISTRIBUTION("AGE DISTRIBUTION", PaidExportColumn.AGE),

	/**
	 * Both channels: delivery per gender, kept in the export's own order.
	 */
	GENDER_SPLIT("GENDER SPLIT", PaidExportColumn.GENDER),

	/**
	 * Paid Search only: one row per responsive search ad with its campaign, ad group, headlines, descriptions and
	 * landing page; read into ranked ads rather than a plain breakdown. Keyed by Headline 1, the column every ad row
	 * carries.
	 */
	AD_BREAKDOWN("AD BREAKDOWN", PaidExportColumn.HEADLINE_1),

	/**
	 * Paid Social only: Meta delivery per placement; ranked by delivery.
	 */
	PLACEMENT_BREAKDOWN("PLACEMENT BREAKDOWN", PaidExportColumn.PLACEMENT),

	/**
	 * Paid Social only: delivery per ad set, i.e. per targeted audience; ranked by delivery.
	 */
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
