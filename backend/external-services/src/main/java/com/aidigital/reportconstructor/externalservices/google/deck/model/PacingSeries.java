package com.aidigital.reportconstructor.externalservices.google.deck.model;

import java.util.Map;

/**
 * One granularity (daily or monthly) of every tactic's pacing pivot, already computed by the service.
 *
 * @param fromSheet {@code true} when the pivots were read back from the reviewed workbook rather than computed
 *                  from the BigQuery export; only changes how an empty tactic is worded
 * @param byTactic  1-based tactic number → its pivot; a tactic without an entry is treated as empty
 */
public record PacingSeries(boolean fromSheet, Map<Integer, PacingPivot> byTactic) {
}
