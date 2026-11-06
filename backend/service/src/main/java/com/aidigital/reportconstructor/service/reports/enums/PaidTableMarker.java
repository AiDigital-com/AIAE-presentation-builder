package com.aidigital.reportconstructor.service.reports.enums;

/**
 * The data tables of the Paid Search / Paid Social slides. The template does not tokenise them cell by cell: each
 * table carries its marker as its alt-text title, and the deck pass finds the table by that title, writes every
 * data cell by row and column, and deletes the rows the export has no data for.
 */
public enum PaidTableMarker {

	/** Search Campaign Breakdown: name, impr, clicks, CTR, CPC, conv, CPA, spend; TOTAL row. */
	SEARCH_CAMPAIGNS("ps_camp_table", PaidChannelKind.SEARCH),
	/** Search Group Breakdown: group, impr, clicks, CTR, CPC, conv, CPA, spend. */
	SEARCH_GROUPS("ps_grp_table", PaidChannelKind.SEARCH),
	/** Search Top 10 Keywords: rank, keyword, impr, clicks, CTR, CPC, conv, CPA, spend. */
	SEARCH_KEYWORDS("ps_kw_table", PaidChannelKind.SEARCH),
	/** PMax Placement Breakdown: placement, impr, clicks, CTR, CPC, conv, CPA, spend, spend share; TOTAL row. */
	SEARCH_PMAX("ps_pmax_table", PaidChannelKind.SEARCH),
	/** EOM Search metric master: metric, CTD goal, CTD actual, vs goal, EOC goal (Clicks, CTR, CPC). */
	SEARCH_EOM_METRICS("ps_eom_metric_table", PaidChannelKind.SEARCH),
	/** Social Creative Performance: creative, impr, clicks, CTR, CPC, spend, conv, CPA; TOTAL row. */
	SOCIAL_CREATIVES("pso_cr_table", PaidChannelKind.SOCIAL),
	/** Social Audience Targeting: audience, impr, clicks, CTR, CPC, conv, CPA, spend, spend share; TOTAL row. */
	SOCIAL_AUDIENCES("pso_aud_table", PaidChannelKind.SOCIAL),
	/** Social Placement Breakdown: placement, impr, clicks, CTR, CPC, conv, CPA, spend; TOTAL row. */
	SOCIAL_PLACEMENTS("pso_pl_table", PaidChannelKind.SOCIAL),
	/** EOM Social metric master: metric, CTD goal, CTD actual, vs goal, EOC goal (Clicks, CTR, CPC). */
	SOCIAL_EOM_METRICS("pso_eom_metric_table", PaidChannelKind.SOCIAL);

	private final String marker;
	private final PaidChannelKind channel;

	/**
	 * @param marker  the table's alt-text title in the template
	 * @param channel the channel whose slide carries the table
	 */
	PaidTableMarker(String marker, PaidChannelKind channel) {
		this.marker = marker;
		this.channel = channel;
	}

	/**
	 * @return the table's alt-text title in the template, e.g. {@code "ps_grp_table"}
	 */
	public String marker() {
		return marker;
	}

	/**
	 * @return the channel whose slide carries the table
	 */
	public PaidChannelKind channel() {
		return channel;
	}
}
