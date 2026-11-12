package com.aidigital.reportconstructor.externalservices.google.deck;

/**
 * A marker table on an MPO deck's paid slides, found by its alt-text title. The bridge maps the service's
 * paid table marker onto this enum by constant name; {@code DeckModelMapperTest} pins the values.
 */
public enum PaidDeckTableMarker {

	/** Paid Search campaigns table. */
	SEARCH_CAMPAIGNS("ps_camp_table", PaidDeckChannel.SEARCH),
	/** Paid Search ad groups table. */
	SEARCH_GROUPS("ps_grp_table", PaidDeckChannel.SEARCH),
	/** Paid Search keywords table. */
	SEARCH_KEYWORDS("ps_kw_table", PaidDeckChannel.SEARCH),
	/** Paid Search Performance Max table. */
	SEARCH_PMAX("ps_pmax_table", PaidDeckChannel.SEARCH),
	/** Paid Search EOM metrics table. */
	SEARCH_EOM_METRICS("ps_eom_metric_table", PaidDeckChannel.SEARCH),
	/** Paid Social creatives table. */
	SOCIAL_CREATIVES("pso_cr_table", PaidDeckChannel.SOCIAL),
	/** Paid Social audiences table. */
	SOCIAL_AUDIENCES("pso_aud_table", PaidDeckChannel.SOCIAL),
	/** Paid Social placements table. */
	SOCIAL_PLACEMENTS("pso_pl_table", PaidDeckChannel.SOCIAL),
	/** Paid Social EOM metrics table. */
	SOCIAL_EOM_METRICS("pso_eom_metric_table", PaidDeckChannel.SOCIAL);

	private final String marker;
	private final PaidDeckChannel channel;

	/**
	 * Declares a marker table.
	 *
	 * @param marker  the table's alt-text title in the template
	 * @param channel the channel whose slide carries the table
	 */
	PaidDeckTableMarker(String marker, PaidDeckChannel channel) {
		this.marker = marker;
		this.channel = channel;
	}

	/**
	 * Returns the table's alt-text title in the template.
	 *
	 * @return the marker text
	 */
	public String marker() {
		return marker;
	}

	/**
	 * Returns the channel the table belongs to.
	 *
	 * @return the channel
	 */
	public PaidDeckChannel channel() {
		return channel;
	}
}
