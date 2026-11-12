package com.aidigital.reportconstructor.externalservices.google.charts;

import com.aidigital.reportconstructor.externalservices.google.charts.model.BreakdownChartsRequest;
import com.aidigital.reportconstructor.externalservices.google.charts.model.PaidChartsRequest;
import com.aidigital.reportconstructor.externalservices.google.charts.model.TacticChartsRequest;

import java.util.List;

/**
 * Chart generation for the deck, in the Google integration's own terms (the service's {@code ChartProvider}
 * port is implemented on top of this by {@code bridge.ChartProviderBridge}). The real client copies the helper
 * chart-template spreadsheets, writes the already-pivoted actuals it is given, applies the saved chart spec and
 * swaps the placeholder charts on the deck slides for live linked charts; the stub is a no-op for offline
 * demos. Pivoting the BigQuery export is the service's job, not this client's.
 *
 * <p>Bean selection mirrors the Slides deck client: when
 * {@code GOOGLE_SERVICE_ACCOUNT_JSON} is present the real provider wins via
 * {@code @Primary}, otherwise the stub is the only candidate.
 */
public interface SlidesChartClient {

	/**
	 * @return true when the provider is talking to the real Google APIs.
	 */
	boolean isLive();

	/**
	 * Builds the three charts (daily pacing, monthly distribution, weighted
	 * impression contribution) for every active tactic. Per-chart failures are
	 * collected and returned without aborting the rest of the deck.
	 *
	 * @param request the chart-generation inputs
	 * @return human-readable error strings (empty when everything succeeded)
	 */
	List<String> buildCharts(TacticChartsRequest request);

	/**
	 * Renders the per-tactic audience/device breakdown charts and links them onto the already-duplicated
	 * breakdown slides. For each job: copies the section's chart-source template spreadsheet, writes the
	 * tactic's impression slices into it (share of voice stays a live formula), then swaps the breakdown
	 * slide's duplicated placeholder chart for a fresh chart linked to that copy. Per-chart failures are
	 * collected and returned without aborting the rest.
	 *
	 * @param request the breakdown chart-generation inputs
	 * @return human-readable error strings (empty when everything succeeded)
	 */
	List<String> buildBreakdownCharts(BreakdownChartsRequest request);

	/**
	 * Replaces the MPO deck's Paid Search / Paid Social sample chart images with charts linked to the report's
	 * data: each chart gets its own spreadsheet, styled like the sample, and is linked in the sample image's place.
	 * A chart without data deletes its image; a chart whose image is not on the deck (the EOM deck has no overview
	 * slides) is skipped. Per-chart failures are collected and returned without aborting the rest.
	 *
	 * @param request the paid chart inputs
	 * @return human-readable error strings (empty when everything succeeded)
	 */
	List<String> buildPaidCharts(PaidChartsRequest request);
}
