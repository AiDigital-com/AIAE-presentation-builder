package com.aidigital.reportconstructor.externalservices.google.sheets.model;

import com.aidigital.reportconstructor.externalservices.google.deck.model.TacticPacing;

import java.util.Map;

/**
 * Inputs for the workbook's Daily pacing / Monthly pacing / Channel Distribution tables.
 *
 * @param tacticCount           number of active tactics (1-based, inclusive)
 * @param pacing                every tactic's daily and monthly pivots, computed by the service
 * @param distTacticNames       tactic number → label of its distribution slice
 * @param distTacticImps        tactic number → its impressions
 * @param distTotalImps         total impressions across tactics; "Other" is the remainder
 * @param tacticKpiTypes        tactic number → KPI series token ({@code "ctr"}/{@code "vcr"}/{@code "acr"})
 * @param userGoogleAccessToken signed-in user's Google token, or {@code null}/blank for the service account
 */
public record WorkbookPacingRequest(
		int tacticCount,
		TacticPacing pacing,
		Map<Integer, String> distTacticNames,
		Map<Integer, Double> distTacticImps,
		double distTotalImps,
		Map<Integer, String> tacticKpiTypes,
		String userGoogleAccessToken) {
}
