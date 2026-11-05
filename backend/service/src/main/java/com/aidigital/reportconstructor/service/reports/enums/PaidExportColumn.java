package com.aidigital.reportconstructor.service.reports.enums;

import java.util.List;

/**
 * A column of a paid-media export tab (Google Ads / Meta "raw data for automation"), found by its header text
 * rather than its position: the Search and Social exports order their columns differently. Aliases are compared
 * with the header normalised (trimmed, lower case, runs of whitespace collapsed) and must match it exactly, so
 * {@code "Channel"} never picks up {@code "Channel_Short_Name"} nor {@code "Conversions"} {@code "CPA_Conversions"}.
 */
public enum PaidExportColumn {

	DATE(List.of("date")),
	LEVEL_1_NAMING(List.of("level 1 naming")),
	CREATIVE(List.of("creative")),
	CAMPAIGN(List.of("campaign")),
	GROUP(List.of("group", "ad group")),
	KEYWORD(List.of("keyword")),
	CHANNEL(List.of("channel")),
	PLACEMENT(List.of("placement")),
	AGE(List.of("age")),
	GENDER(List.of("gender")),
	AD_SET(List.of("ad set")),
	HEADLINE_1(List.of("headline 1")),
	HEADLINE_2(List.of("headline 2")),
	HEADLINE_3(List.of("headline 3")),
	DESCRIPTION_1(List.of("description 1")),
	DESCRIPTION_2(List.of("description 2")),
	LANDING_PAGE(List.of("landing page", "final url")),
	IMPRESSIONS(List.of("impressions", "impr")),
	CLICKS(List.of("clicks")),
	COST(List.of("cost", "spend", "amount spent")),
	CONVERSIONS(List.of("conversions", "conversion"));

	private final List<String> aliases;

	/**
	 * @param aliases the normalised header texts that name this column
	 */
	PaidExportColumn(List<String> aliases) {
		this.aliases = aliases;
	}

	/**
	 * Reports whether a normalised header cell names this column.
	 *
	 * @param normalisedHeader the header text, trimmed, lower-cased and whitespace-collapsed
	 * @return {@code true} when the header is one of this column's aliases
	 */
	public boolean matches(String normalisedHeader) {
		return aliases.contains(normalisedHeader);
	}
}
