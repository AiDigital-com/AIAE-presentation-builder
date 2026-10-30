package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.MpoNarrative;
import com.aidigital.reportconstructor.service.reports.dto.MpoNarrativeInput;

import java.util.Map;

/**
 * Assembles the input of the MPO-only narrative call from the resolved placeholder map, and writes the reply
 * back onto it.
 *
 * <p>Both directions go through the placeholder map for the same reason as {@link PacingNarrativeAssembler}:
 * it holds the figures the deck will actually print — on the two-step flow, the ones the user reviewed in the
 * workbook — so the funnel's evidence lines quote numbers that agree with the rest of the deck.
 */
public interface MpoNarrativeAssembler {

	/**
	 * Builds the call's context from the resolved placeholder map: the campaign-level figures and strategy copy,
	 * and one line per tactic. Values that are missing, blank or a dash are left out rather than quoted.
	 *
	 * @param flat        the resolved placeholder map, read only
	 * @param tacticCount number of real tactics in the campaign
	 * @return the call's input, never {@code null}
	 */
	MpoNarrativeInput toInput(Map<String, String> flat, int tacticCount);

	/**
	 * Writes every MPO narrative token: the reply's copy where there is one, a dash where there is not, and
	 * whatever the map already carried where the user supplied it themselves.
	 *
	 * @param flat      the placeholder map to fill, mutated in place
	 * @param narrative the reply, or {@code null} when the call was skipped or produced nothing usable
	 */
	void write(Map<String, String> flat, MpoNarrative narrative);
}
