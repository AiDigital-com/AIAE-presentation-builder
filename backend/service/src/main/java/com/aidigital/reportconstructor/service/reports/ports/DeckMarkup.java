package com.aidigital.reportconstructor.service.reports.ports;

/**
 * Markers a placeholder value can carry for the deck's final markup pass
 * ({@link SlidesProvider#applyDeckMarkup}) to act on once every value is in place.
 */
public final class DeckMarkup {

	/**
	 * A value made of this character alone removes its whole shape from the deck — an optional chip with
	 * nothing to show. It is U+2063 INVISIBLE SEPARATOR, so a deck the pass never reached still reads as an
	 * empty chip; written as an escape so the value is visible in source and survives any editor or encoding.
	 */
	public static final String HIDE_SHAPE = "⁣";

	/**
	 * Not instantiable: this class only holds constants.
	 */
	private DeckMarkup() {
	}
}
