package com.aidigital.reportconstructor.service.reports.enums;

import java.util.List;

/**
 * The two MPO performance channels fed by a paid-media export, with the token prefix of their slides and the
 * breakdown tabs those slides read (FULL-FLIGHT is common to both and not listed).
 */
public enum PaidChannelKind {

	/** Google Ads export, feeding the Paid Search section ({@code {{ps_…}}} tokens). */
	SEARCH("ps_", "Paid Search (Google Ads)", "PAID SEARCH CAMPAIGN RESULTS", List.of(PaidExportTab.GROUP_BREAKDOWN,
			PaidExportTab.KEYWORD_BREAKDOWN, PaidExportTab.PMAX_PLACEMENT_BREAKDOWN, PaidExportTab.AGE_DISTRIBUTION,
			PaidExportTab.GENDER_SPLIT, PaidExportTab.AD_BREAKDOWN)),
	/** Meta export, feeding the Paid Social section ({@code {{pso_…}}} tokens). */
	SOCIAL("pso_", "Paid Social (Meta)", "PAID SOCIAL CAMPAIGN RESULTS", List.of(PaidExportTab.PLACEMENT_BREAKDOWN,
			PaidExportTab.AUDIENCE_TARGETING_BREAKDOWN,
			PaidExportTab.AGE_DISTRIBUTION, PaidExportTab.GENDER_SPLIT));

	private final String tokenPrefix;
	private final String displayName;
	private final String dividerTitle;
	private final List<PaidExportTab> breakdownTabs;

	/**
	 * @param tokenPrefix   the prefix of this channel's slide tokens
	 * @param displayName   the channel as the deck and the prompts name it
	 * @param dividerTitle  the title of the section's divider slide
	 * @param breakdownTabs the breakdown tabs this channel's slides read
	 */
	PaidChannelKind(String tokenPrefix, String displayName, String dividerTitle, List<PaidExportTab> breakdownTabs) {
		this.tokenPrefix = tokenPrefix;
		this.displayName = displayName;
		this.dividerTitle = dividerTitle;
		this.breakdownTabs = breakdownTabs;
	}

	/**
	 * @return the channel as the deck and the prompts name it, e.g. {@code "Paid Search (Google Ads)"}
	 */
	public String displayName() {
		return displayName;
	}

	/**
	 * @return the title of the section's divider slide, e.g. {@code "PAID SEARCH CAMPAIGN RESULTS"}; the EOM divider
	 *         carries no token, so a deck without this channel finds that slide by its title
	 */
	public String dividerTitle() {
		return dividerTitle;
	}

	/**
	 * @return the prefix of this channel's slide tokens, e.g. {@code "ps_"}
	 */
	public String tokenPrefix() {
		return tokenPrefix;
	}

	/**
	 * @return the breakdown tabs this channel's slides read, in slide order
	 */
	public List<PaidExportTab> breakdownTabs() {
		return breakdownTabs;
	}
}
