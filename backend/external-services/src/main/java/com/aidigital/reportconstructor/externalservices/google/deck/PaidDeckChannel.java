package com.aidigital.reportconstructor.externalservices.google.deck;

/**
 * A paid-media channel section of an MPO deck, in the Google integration's own terms. The bridge maps the
 * service's paid channel kind onto this enum by constant name; {@code DeckModelMapperTest} pins the values.
 */
public enum PaidDeckChannel {

	/** Paid Search (Google Ads). */
	SEARCH("ps_", "Paid Search (Google Ads)", "PAID SEARCH CAMPAIGN RESULTS"),
	/** Paid Social (Meta). */
	SOCIAL("pso_", "Paid Social (Meta)", "PAID SOCIAL CAMPAIGN RESULTS");

	private final String tokenPrefix;
	private final String displayName;
	private final String dividerTitle;

	/**
	 * Declares a paid channel section.
	 *
	 * @param tokenPrefix  prefix every token of this channel starts with
	 * @param displayName  human-readable name, used in log lines
	 * @param dividerTitle title of the section's divider slide
	 */
	PaidDeckChannel(String tokenPrefix, String displayName, String dividerTitle) {
		this.tokenPrefix = tokenPrefix;
		this.displayName = displayName;
		this.dividerTitle = dividerTitle;
	}

	/**
	 * Returns the prefix every token of this channel starts with, right after the opening double brace.
	 *
	 * @return the token prefix
	 */
	public String tokenPrefix() {
		return tokenPrefix;
	}

	/**
	 * Returns the channel's human-readable name, used in log lines.
	 *
	 * @return the display name
	 */
	public String displayName() {
		return displayName;
	}

	/**
	 * Returns the title of the channel's divider slide.
	 *
	 * @return the divider title
	 */
	public String dividerTitle() {
		return dividerTitle;
	}
}
