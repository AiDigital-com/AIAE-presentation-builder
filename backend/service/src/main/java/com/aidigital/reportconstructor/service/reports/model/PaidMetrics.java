package com.aidigital.reportconstructor.service.reports.model;

/**
 * Delivery totals of one paid-media row or group of rows, plus the rates derived from them. A rate whose
 * denominator is zero is {@code null}, so the deck can print an em-dash instead of a misleading {@code 0}.
 *
 * @param impressions impressions served
 * @param clicks      clicks
 * @param cost        spend in the export's currency
 * @param conversions conversions (fractional under Google Ads data-driven attribution)
 */
public record PaidMetrics(double impressions, double clicks, double cost, double conversions) {

	/** No delivery; the identity of {@link #plus(PaidMetrics)}. */
	public static final PaidMetrics ZERO = new PaidMetrics(0, 0, 0, 0);

	private static final double PERCENT = 100d;

	/**
	 * Adds another row's delivery to this one.
	 *
	 * @param other the delivery to add
	 * @return the summed delivery
	 */
	public PaidMetrics plus(PaidMetrics other) {
		return new PaidMetrics(impressions + other.impressions, clicks + other.clicks, cost + other.cost,
				conversions + other.conversions);
	}

	/**
	 * @return click-through rate in percent (clicks / impressions x 100), or {@code null} without impressions
	 */
	public Double ctr() {
		return impressions > 0 ? clicks / impressions * PERCENT : null;
	}

	/**
	 * @return cost per click, or {@code null} without clicks
	 */
	public Double cpc() {
		return clicks > 0 ? cost / clicks : null;
	}

	/**
	 * @return cost per conversion, or {@code null} without conversions
	 */
	public Double cpa() {
		return conversions > 0 ? cost / conversions : null;
	}

	/**
	 * @return conversion rate in percent (conversions / clicks x 100), or {@code null} without clicks
	 */
	public Double cr() {
		return clicks > 0 ? conversions / clicks * PERCENT : null;
	}
}
