package com.aidigital.reportconstructor.externalservices.google;

import java.util.List;

/**
 * How each Paid Search / Paid Social chart is drawn, read off the MPO template's sample charts: the colours (series
 * colours for the monthly charts, slice colours in legend order for the donuts, which take them from the chart
 * spreadsheet's theme accents) and how the chart sits on the slide.
 */
public enum PaidChartStyle {

	/** Monthly clicks &amp; CTR: lavender columns, pink line; drawn in the sample image's box. */
	CLICKS_CTR(List.of("#D2D3F8", "#FF7CF5"), 0, false),
	/** Monthly spend: lavender columns; drawn in the sample image's box. */
	SPEND(List.of("#D2D3F8"), 0, false),
	/**
	 * Overview donuts (Search campaign type, Social audience type). The sample image is wider than the donut, which
	 * it draws centred, and hangs past the card's left edge, so the chart is a square centred on the image, slightly
	 * smaller than the image's height so it stays inside the card; the sample's total in the hole was part of the
	 * image, so a text box puts it back.
	 */
	OVERVIEW_DONUT(List.of("#0009DC", "#AEF33E", "#FF7CF5"), 0.9, true),
	/** Social placement donut: a square image; its legend sits below and its hole label is a shape on the slide. */
	PLACEMENT_DONUT(List.of("#F36CE8", "#AEF33E", "#7DDA1F", "#62C91C", "#C9F78D"), 1, false);

	private final List<String> colors;
	private final double squareShare;
	private final boolean centerText;

	/**
	 * @param colors      hex colours: series colours for the monthly charts, slice colours in legend order for donuts
	 * @param squareShare 0 to draw the chart in the sample image's box; otherwise the chart is a square centred on
	 *                    the image, this share of the image's shorter side
	 * @param centerText  whether a text box with the total is laid over the donut's hole
	 */
	PaidChartStyle(List<String> colors, double squareShare, boolean centerText) {
		this.colors = colors;
		this.squareShare = squareShare;
		this.centerText = centerText;
	}

	/**
	 * @return hex colours: series colours for the monthly charts, slice colours in legend order for donuts
	 */
	public List<String> colors() {
		return colors;
	}

	/**
	 * @return whether the chart is drawn as a square centred on the sample image
	 */
	public boolean square() {
		return squareShare > 0;
	}

	/**
	 * @return the square's side as a share of the sample image's shorter side; 0 when the chart keeps the image's box
	 */
	public double squareShare() {
		return squareShare;
	}

	/**
	 * @return whether a text box with the total is laid over the donut's hole
	 */
	public boolean centerText() {
		return centerText;
	}
}
