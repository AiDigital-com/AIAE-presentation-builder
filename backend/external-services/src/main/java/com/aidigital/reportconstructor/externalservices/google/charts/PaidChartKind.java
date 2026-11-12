package com.aidigital.reportconstructor.externalservices.google.charts;

import com.aidigital.reportconstructor.externalservices.google.deck.PaidDeckChannel;

/**
 * The charts of the Paid Search / Paid Social slides. In the template each one is a static sample image whose
 * alt-text title is the marker; the chart pass replaces that image with a chart linked to the report's data, or
 * deletes it when the export has no data for the chart.
 *
 * <p>The Google integration's copy of the service type of the same shape; the bridge maps between
 * them and {@code ChartModelMapperTest} pins the values.
 */
public enum PaidChartKind {

	/** Search overview: monthly clicks (columns) and CTR (line). */
	SEARCH_CLICKS_CTR("ps_chart_clicks_ctr", PaidDeckChannel.SEARCH, PaidChartShape.CLICKS_CTR, 0),
	/** Search overview: monthly spend. */
	SEARCH_SPEND("ps_chart_spend", PaidDeckChannel.SEARCH, PaidChartShape.SPEND, 0),
	/** Search overview: conversions by campaign type; legend {@code {{ps_donut_1..3}}}. */
	SEARCH_CONVERSION_DONUT("ps_chart_conv_donut", PaidDeckChannel.SEARCH, PaidChartShape.DONUT, 3),
	/** Social overview: monthly clicks (columns) and CTR (line). */
	SOCIAL_CLICKS_CTR("pso_chart_clicks_ctr", PaidDeckChannel.SOCIAL, PaidChartShape.CLICKS_CTR, 0),
	/** Social overview: monthly spend. */
	SOCIAL_SPEND("pso_chart_spend", PaidDeckChannel.SOCIAL, PaidChartShape.SPEND, 0),
	/** Social overview: conversions by audience type; legend {@code {{pso_donut_1..3}}}. */
	SOCIAL_AUDIENCE_DONUT("pso_chart_audience_donut", PaidDeckChannel.SOCIAL, PaidChartShape.DONUT, 3),
	/** Social placement breakdown: conversion mix by placement; legend {@code {{pso_pl_mix_1..5}}}. */
	SOCIAL_PLACEMENT_DONUT("pso_chart_placement_donut", PaidDeckChannel.SOCIAL, PaidChartShape.DONUT, 5);

	private final String marker;
	private final PaidDeckChannel channel;
	private final PaidChartShape type;
	private final int legendSlots;

	/**
	 * @param marker      the sample image's alt-text title in the template
	 * @param channel     the channel whose slide carries the chart
	 * @param type        the chart's shape
	 * @param legendSlots for a donut, how many legend entries the slide prints; slices past them are summed into
	 *                    one "Other" slice. 0 for the monthly charts
	 */
	PaidChartKind(String marker, PaidDeckChannel channel, PaidChartShape type, int legendSlots) {
		this.marker = marker;
		this.channel = channel;
		this.type = type;
		this.legendSlots = legendSlots;
	}

	/**
	 * @return the sample image's alt-text title in the template, e.g. {@code "ps_chart_spend"}
	 */
	public String marker() {
		return marker;
	}

	/**
	 * @return the channel whose slide carries the chart
	 */
	public PaidDeckChannel channel() {
		return channel;
	}

	/**
	 * @return the chart's shape
	 */
	public PaidChartShape type() {
		return type;
	}

	/**
	 * @return for a donut, the number of legend entries on the slide; 0 for the monthly charts
	 */
	public int legendSlots() {
		return legendSlots;
	}
}
