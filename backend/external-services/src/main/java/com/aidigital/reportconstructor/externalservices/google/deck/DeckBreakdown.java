package com.aidigital.reportconstructor.externalservices.google.deck;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The per-tactic breakdown sections a deck or workbook can carry, in the Google integration's own terms.
 * Declaration order is significant: breakdown slides follow it, and the workbook locates its blocks by
 * ordinal. The bridge maps the service's breakdown type onto this enum by constant name.
 */
public enum DeckBreakdown {

	/** Top publishers table. */
	TOP_PUBLISHERS("tp", "Top Publishers"),
	/** Creative analysis table. */
	CREATIVE("ca", "Creative analysis"),
	/** Geo analysis table. */
	GEO("geo", "Geo analysis"),
	/** Audience analysis table. */
	AUDIENCE("aud", "Audience analysis"),
	/** Device breakdown table. */
	DEVICE("dev", "Device breakdown");

	/** Lookup by wire code ({@code "tp"}, {@code "ca"}, …), as used in configuration keys. */
	public static final Map<String, DeckBreakdown> BY_CODE =
			Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(DeckBreakdown::code, type -> type));

	private final String code;
	private final String anchorLabel;

	/**
	 * Declares a breakdown section.
	 *
	 * @param code        short wire code used in slide ids and configuration keys
	 * @param anchorLabel label that anchors the section's block in the workbook
	 */
	DeckBreakdown(String code, String anchorLabel) {
		this.code = code;
		this.anchorLabel = anchorLabel;
	}

	/**
	 * Returns the short wire code used in slide ids and configuration keys.
	 *
	 * @return the wire code
	 */
	public String code() {
		return code;
	}

	/**
	 * Returns the label that anchors this section's block in the workbook ({@code "<label> <n>"}).
	 *
	 * @return the anchor label
	 */
	public String anchorLabel() {
		return anchorLabel;
	}
}
