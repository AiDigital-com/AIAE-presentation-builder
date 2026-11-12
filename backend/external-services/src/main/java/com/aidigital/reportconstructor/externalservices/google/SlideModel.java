package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.externalservices.google.deck.DeckBreakdown;

import java.util.Map;

/**
 * The object ids a deck's slide passes work against: which slides are trimmed, which are duplicated per
 * tactic and which masters are deleted at the end. Ids belong to one template, so each template design
 * carries its own model — applying the standard template's ids to an MPO copy would delete nothing it
 * should and fail on the tactic master it cannot find.
 *
 * <p>All values are already normalized: maps are never {@code null}, ids are trimmed, blank means unset.
 *
 * @param summaryTableObjectIds 1-based tactic group → that group's summary table
 * @param summarySlideObjectIds 1-based tactic group → that group's summary slide
 * @param resultsSlideObjectIds 1-based tactic group → that group's "Our results" slide
 * @param tacticSlideObjectIds  1-based tactic number → its drawn slide (legacy 28-slot model only)
 * @param breakdownMasterIds    breakdown section → its generic master slide
 * @param thoughtsMasterId      the "Thoughts on tactic performance" master slide, blank when unset
 * @param tacticMasterId        the generic main tactic slide, blank under the legacy 28-slot model
 */
public record SlideModel(
		Map<Integer, String> summaryTableObjectIds,
		Map<Integer, String> summarySlideObjectIds,
		Map<Integer, String> resultsSlideObjectIds,
		Map<Integer, String> tacticSlideObjectIds,
		Map<DeckBreakdown, String> breakdownMasterIds,
		String thoughtsMasterId,
		String tacticMasterId) {

	/**
	 * Whether the deck is built on the master tactic-slide model (one generic slide duplicated per tactic)
	 * rather than the legacy model of 28 drawn tactic slots that are trimmed down.
	 *
	 * @return {@code true} when a master tactic slide is configured
	 */
	public boolean masterTacticModel() {
		return !tacticMasterId.isBlank();
	}

	/**
	 * Whether the model names any master slide that has to be removed from a finished deck.
	 *
	 * @return {@code true} when a breakdown, thoughts or tactic master is configured
	 */
	public boolean hasMasters() {
		return !breakdownMasterIds.isEmpty() || !thoughtsMasterId.isBlank() || !tacticMasterId.isBlank();
	}
}
