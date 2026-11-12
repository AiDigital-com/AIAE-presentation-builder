package com.aidigital.reportconstructor.externalservices.google.charts.model;

import com.aidigital.reportconstructor.externalservices.google.deck.model.TacticPacing;

import java.util.Map;

/**
 * Inputs for the per-tactic daily, monthly and distribution charts.
 *
 * @param presentationId        the deck whose placeholder charts are replaced
 * @param tacticCount           number of active tactics (1-based, inclusive)
 * @param campaignTitle         campaign name used in the chart copies' file names
 * @param distTacticNames       tactic number → label of its distribution slice
 * @param distTacticImps        tactic number → its impressions, for the distribution chart
 * @param distTotalImps         total impressions across tactics; the "Other" slice is the remainder
 * @param tacticKpiTypes        tactic number → KPI series token ({@code "ctr"}/{@code "vcr"}/{@code "acr"})
 * @param userGoogleAccessToken signed-in user's Google token, or {@code null}/blank for the service account
 * @param pacing                every tactic's daily and monthly pivots, computed by the service
 */
public record TacticChartsRequest(
		String presentationId,
		int tacticCount,
		String campaignTitle,
		Map<Integer, String> distTacticNames,
		Map<Integer, Double> distTacticImps,
		double distTotalImps,
		Map<Integer, String> tacticKpiTypes,
		String userGoogleAccessToken,
		TacticPacing pacing) {
}
