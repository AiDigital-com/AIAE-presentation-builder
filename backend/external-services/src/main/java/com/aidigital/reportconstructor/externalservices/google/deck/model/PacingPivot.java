package com.aidigital.reportconstructor.externalservices.google.deck.model;

import java.util.LinkedHashMap;

/**
 * PacingPivot result for one tactic.
 *
 * <p>The Google integration's copy of the service type of the same shape; the bridge maps between
 * them and {@code ChartModelMapperTest} pins the values.
 *
 * @param data           insertion-ordered (chronological) map of label &rarr; {@code {imps, clicks, completions}}
 * @param hasClicks      {@code true} when any row carried clicks (display/social &rarr; CTR line series)
 * @param hasCompletions {@code true} when any row carried completions (video/CTV &rarr; VCR line series)
 */
public record PacingPivot(LinkedHashMap<String, double[]> data, boolean hasClicks, boolean hasCompletions) {

	/**
	 * @return {@code true} when no rows were pivoted (no chart should be written).
	 */
	public boolean isEmpty() {
		return data.isEmpty();
	}
}
