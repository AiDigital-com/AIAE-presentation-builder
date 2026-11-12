package com.aidigital.reportconstructor.service.reports.model;

import com.aidigital.reportconstructor.service.reports.engine.Pivot;

import java.util.List;
import java.util.Map;

/**
 * Every tactic's daily and monthly pacing pivots, computed from the BigQuery export.
 *
 * @param columnsFound whether the export carried the Date and Impressions columns; when it did not, both pivot
 *                     maps are empty
 * @param lineItemIds  1-based tactic number → the line item ids its rows were filtered by
 * @param daily        1-based tactic number → daily pivot, for every tactic 1..tacticCount
 * @param monthly      1-based tactic number → monthly pivot, for every tactic 1..tacticCount
 */
public record TacticPivots(
		boolean columnsFound,
		Map<Integer, List<String>> lineItemIds,
		Map<Integer, Pivot> daily,
		Map<Integer, Pivot> monthly) {
}
