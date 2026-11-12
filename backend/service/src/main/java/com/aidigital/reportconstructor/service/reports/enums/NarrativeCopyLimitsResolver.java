package com.aidigital.reportconstructor.service.reports.enums;

import org.springframework.stereotype.Component;

/**
 * Picks the {@link NarrativeCopyLimits} profile of a run from the deck it builds. A bean rather than a static
 * factory on the enum, so callers can replace it in tests.
 */
@Component
public class NarrativeCopyLimitsResolver {

	/**
	 * Picks the profile for a run.
	 *
	 * @param mpoDeck whether the run builds its deck on an MPO template
	 * @return {@link NarrativeCopyLimits#COMPACT} for an MPO deck, {@link NarrativeCopyLimits#STANDARD} otherwise
	 */
	public NarrativeCopyLimits forDeck(boolean mpoDeck) {
		return mpoDeck ? NarrativeCopyLimits.COMPACT : NarrativeCopyLimits.STANDARD;
	}
}
