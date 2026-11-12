package com.aidigital.reportconstructor.externalservices.google.deck.model;

import java.util.List;
import java.util.Map;

/**
 * Every tactic's daily and monthly pacing, as the charts and the workbook's pacing tables consume it.
 *
 * @param bqColumnsFound whether the BigQuery export carried the Date and Impressions columns; when it did not,
 *                       a series that is not read from the workbook cannot be drawn and is reported instead
 * @param lineItemIds    1-based tactic number → the line item ids its BigQuery rows were filtered by, quoted in the
 *                       "no data" message; empty when the series come from the workbook
 * @param daily          the daily pivots
 * @param monthly        the monthly pivots
 */
public record TacticPacing(
		boolean bqColumnsFound, Map<Integer, List<String>> lineItemIds, PacingSeries daily, PacingSeries monthly) {
}
