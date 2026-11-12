package com.aidigital.reportconstructor.externalservices.google.charts;

/**
 * The shapes of chart the Paid Search / Paid Social slides carry.
 *
 * <p>The Google integration's copy of the service type of the same shape; the bridge maps between
 * them and {@code ChartModelMapperTest} pins the values.
 */
public enum PaidChartShape {

	/** Monthly clicks as columns with the monthly CTR as a line on a second axis. */
	CLICKS_CTR,
	/** Monthly spend as columns. */
	SPEND,
	/** Conversion split as a donut. */
	DONUT
}
