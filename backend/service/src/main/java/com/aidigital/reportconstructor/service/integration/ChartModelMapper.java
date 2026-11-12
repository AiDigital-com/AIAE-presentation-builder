package com.aidigital.reportconstructor.service.integration;

import com.aidigital.reportconstructor.externalservices.google.charts.PaidChartKind;
import com.aidigital.reportconstructor.externalservices.google.charts.model.BreakdownChart;
import com.aidigital.reportconstructor.externalservices.google.charts.model.BreakdownChartsRequest;
import com.aidigital.reportconstructor.externalservices.google.charts.model.ChartSlice;
import com.aidigital.reportconstructor.externalservices.google.charts.model.PaidChart;
import com.aidigital.reportconstructor.externalservices.google.charts.model.PaidChartsRequest;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PacingPivot;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PacingSeries;
import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import com.aidigital.reportconstructor.service.reports.model.PaidSlideChart;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartJob;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartRequest;
import com.aidigital.reportconstructor.service.reports.ports.PaidChartRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps the service's chart and pacing types onto the Google integration's copies. Enums are matched by
 * constant name; {@code ChartModelMapperTest} pins that both sides declare the same constants with the same
 * values. Map order is kept.
 */
@Component
public class ChartModelMapper {

	/**
	 * Maps one pacing pivot. The data map is copied; its value arrays are shared, as neither side mutates them.
	 *
	 * @param pivot the service's pivot
	 * @return the same pivot in Google terms
	 */
	public PacingPivot pivot(Pivot pivot) {
		return new PacingPivot(new LinkedHashMap<>(pivot.data()), pivot.hasClicks(), pivot.hasCompletions());
	}

	/**
	 * Maps one granularity of every tactic's pivots.
	 *
	 * @param fromSheet whether the pivots were read back from the reviewed workbook
	 * @param byTactic  tactic number → pivot; may be {@code null} (treated as empty)
	 * @return the series in Google terms
	 */
	public PacingSeries series(boolean fromSheet, Map<Integer, Pivot> byTactic) {
		Map<Integer, PacingPivot> mapped = new LinkedHashMap<>();
		if (byTactic != null) {
			byTactic.forEach((tacticNum, pivot) -> mapped.put(tacticNum, pivot(pivot)));
		}
		return new PacingSeries(fromSheet, mapped);
	}

	/**
	 * Maps a breakdown chart request.
	 *
	 * @param request the service's request
	 * @return the same request in Google terms
	 */
	public BreakdownChartsRequest breakdownCharts(BreakdownChartRequest request) {
		return new BreakdownChartsRequest(request.presentationId(), request.campaignTitle(),
				request.jobs().stream().map(this::breakdownChart).toList(), request.userGoogleAccessToken());
	}

	/**
	 * Maps one breakdown chart job.
	 *
	 * @param job the service's job
	 * @return the same job in Google terms
	 */
	public BreakdownChart breakdownChart(BreakdownChartJob job) {
		return new BreakdownChart(job.seriesCode(), job.tacticNum(),
				job.slices().stream().map(slice -> new ChartSlice(slice.label(), slice.value())).toList());
	}

	/**
	 * Maps a paid chart request.
	 *
	 * @param request the service's request
	 * @return the same request in Google terms
	 */
	public PaidChartsRequest paidCharts(PaidChartRequest request) {
		return new PaidChartsRequest(request.presentationId(), request.campaignTitle(),
				request.charts().stream().map(this::paidChart).toList(), request.userGoogleAccessToken());
	}

	/**
	 * Maps one paid chart.
	 *
	 * @param chart the service's chart
	 * @return the same chart in Google terms
	 */
	public PaidChart paidChart(PaidSlideChart chart) {
		return new PaidChart(PaidChartKind.valueOf(chart.marker().name()), chart.labels(), chart.values(),
				chart.lineValues(), chart.centerValue());
	}
}
