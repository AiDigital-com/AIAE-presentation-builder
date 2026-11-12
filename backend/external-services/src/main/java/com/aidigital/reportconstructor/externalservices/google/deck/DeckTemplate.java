package com.aidigital.reportconstructor.externalservices.google.deck;

/**
 * Which template design a deck is cloned from, in the Google integration's own terms. The bridge maps the
 * service's template variant onto this enum by constant name.
 */
public enum DeckTemplate {

	/** The standard EOC/EOM template. */
	STANDARD,
	/** The MPO template (falls back to the standard one when none is configured for the report type). */
	MPO
}
