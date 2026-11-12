package com.aidigital.reportconstructor.externalservices.bridge;

import com.aidigital.reportconstructor.externalservices.google.charts.SlidesChartClient;
import com.aidigital.reportconstructor.externalservices.google.charts.model.TacticChartsRequest;
import com.aidigital.reportconstructor.externalservices.google.deck.model.TacticPacing;
import com.aidigital.reportconstructor.service.reports.engine.TacticPivotAssembler;
import com.aidigital.reportconstructor.service.reports.model.TacticPivots;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartRequest;
import com.aidigital.reportconstructor.service.reports.ports.ChartProvider;
import com.aidigital.reportconstructor.service.reports.ports.ChartRequest;
import com.aidigital.reportconstructor.service.reports.ports.PaidChartRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Implements the service's {@link ChartProvider} port on top of the Google integration's
 * {@link SlidesChartClient} (real or stub). For the BigQuery flow it pivots the export through the service's
 * {@link TacticPivotAssembler} first, so the Google code only draws the result; the sheet flow's pivots, read
 * back from the reviewed workbook, are passed through as they are.
 */
@Component
@RequiredArgsConstructor
public class ChartProviderBridge implements ChartProvider {

	private final SlidesChartClient charts;
	private final TacticPivotAssembler pivots;
	private final ChartModelMapper mapper;

	@Override
	public boolean isLive() {
		return charts.isLive();
	}

	@Override
	public List<String> buildCharts(ChartRequest request) {
		return charts.buildCharts(new TacticChartsRequest(
				request.presentationId(), request.tacticCount(), request.campaignTitle(),
				request.distTacticNames(), request.distTacticImps(), request.distTotalImps(),
				request.tacticKpiTypes(), request.userGoogleAccessToken(), pacing(request)));
	}

	@Override
	public List<String> buildBreakdownCharts(BreakdownChartRequest request) {
		return charts.buildBreakdownCharts(mapper.breakdownCharts(request));
	}

	@Override
	public List<String> buildPaidCharts(PaidChartRequest request) {
		return charts.buildPaidCharts(mapper.paidCharts(request));
	}

	/**
	 * Resolves every tactic's daily and monthly pivots: the sheet flow's own pivots when the request carries
	 * them, otherwise pivots of the BigQuery export over the flight window (both granularities).
	 *
	 * @param request the chart request
	 * @return the pacing the chart client draws
	 */
	TacticPacing pacing(ChartRequest request) {
		boolean dailyFromSheet = request.dailyPivots() != null;
		boolean monthlyFromSheet = request.monthlyPivots() != null;
		if (dailyFromSheet && monthlyFromSheet) {
			return new TacticPacing(true, Map.of(), mapper.series(true, request.dailyPivots()),
					mapper.series(true, request.monthlyPivots()));
		}
		TacticPivots computed = pivots.assemble(request.bqRows(), request.lineItemMapping(), request.tacticCount(),
				request.flightTs(), request.flightTs());
		return new TacticPacing(computed.columnsFound(), computed.lineItemIds(),
				dailyFromSheet ? mapper.series(true, request.dailyPivots()) : mapper.series(false, computed.daily()),
				monthlyFromSheet
						? mapper.series(true, request.monthlyPivots()) : mapper.series(false, computed.monthly()));
	}
}
