package com.aidigital.reportconstructor.externalservices.google.deck;

/**
 * Markers that placeholder values carry for the deck's markup pass. The values must equal the service's
 * {@code DeckMarkup} constants, which write them; {@code DeckModelMapperTest} pins that.
 */
public final class DeckMarkers {

	/** A shape whose whole text is this marker is removed (an optional chip with nothing to show). */
	public static final String HIDE_SHAPE = "⁣";

	/** Opens a lead phrase that is rendered bold. */
	public static final String LEAD_OPEN = "[[";

	/** Closes a lead phrase that is rendered bold. */
	public static final String LEAD_CLOSE = "]]";

	private DeckMarkers() {
	}
}
