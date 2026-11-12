package com.aidigital.reportconstructor.externalservices.google.charts.model;

import com.aidigital.reportconstructor.externalservices.google.charts.PaidChartKind;


import java.util.List;

/**
 * The data of one Paid Search / Paid Social chart, in the order the chart draws it.
 *
 * <p>The Google integration's copy of the service type of the same shape; the bridge maps between
 * them and {@code ChartModelMapperTest} pins the values.
 *
 * @param marker      which chart of the template this is
 * @param labels      the category of each point: month labels ({@code "Oct"}) or donut slice names
 * @param values      the column / slice value of each point: clicks, spend or conversions
 * @param lineValues  for the clicks &amp; CTR chart, the CTR of each month as a fraction ({@code 0.046}); empty
 *                    otherwise
 * @param centerValue for a donut, the formatted total printed in its hole ({@code "1,248"}); {@code null} otherwise
 */
public record PaidChart(
		PaidChartKind marker,
		List<String> labels,
		List<Double> values,
		List<Double> lineValues,
		String centerValue) {

	/**
	 * @return whether the chart has anything to draw: at least one point with a positive value
	 */
	public boolean hasData() {
		return values.stream().anyMatch(v -> v != null && v > 0);
	}
}
