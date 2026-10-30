package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * Context for the MPO-only narrative call: the campaign-level and per-tactic figures the deck prints, each as
 * one {@code "Label: value"} line. Assembled from the placeholder map after every other value is resolved, so
 * the copy is written against exactly the numbers that sit beside it on the slides.
 *
 * @param campaignFacts campaign-level lines (client, flight, budget, totals, strategy copy), never {@code null}
 * @param tacticFacts   one line per tactic in slide order, never {@code null}
 * @param resultsOverviews the "Our results" overview of each tactic group, group 1 first, to be tightened to the
 *                      MPO box; an entry is {@code null} for a group that has none. Never {@code null}
 */
public record MpoNarrativeInput(
		List<String> campaignFacts,
		List<String> tacticFacts,
		List<String> resultsOverviews
) {
}
