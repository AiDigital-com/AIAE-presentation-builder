package com.aidigital.reportconstructor.service.reports.helpers;

import java.util.Map;

/**
 * Fills the tokens the MPO-style EOC template adds on top of the values the deck already carries: the
 * strategic framework's campaign period, the media plan slide's geo summary, and the tactic slide's
 * top-content KPI (clicks for a click-led tactic, completed views for a completion-led one).
 *
 * <p>Every one of them is a re-reading of another token, so they are derived from the resolved map and
 * derived last: on the "Slides from Sheet" flow that map carries what the user reviewed in the workbook, and
 * reading it is what keeps the new tokens agreeing with the figures printed beside them. A template without
 * these slots simply never prints them.
 */
public interface DeckStyleTokenHelper {

	/**
	 * Writes {@code {{campaign_period}}}, {@code {{geo_summary}}} and, for tactics {@code 1..tacticCount},
	 * {@code {{tactic N top creative kpi label}}} and {@code {{tactic N top creative kpi}}} into the map.
	 *
	 * <p>A token that already carries a value is left alone, so a value the user entered in the workbook
	 * keeps winning over the derived one.
	 *
	 * @param flatReplacements resolved token → value map, mutated in place
	 * @param tacticCount      number of active tactics
	 */
	void fillDeckStyleTokens(Map<String, String> flatReplacements, int tacticCount);
}
