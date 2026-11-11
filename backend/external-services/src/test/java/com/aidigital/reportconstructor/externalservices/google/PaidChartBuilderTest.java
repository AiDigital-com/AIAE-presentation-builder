package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.observability.external.ExternalCallTimer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.aidigital.reportconstructor.service.reports.ports.PaidChartRequest;
import com.google.api.services.sheets.v4.model.AddChartResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.EmbeddedChart;
import com.google.api.services.sheets.v4.model.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PaidChartBuilderTest {

	@Test
	void shouldTakeTheChartIdFromTheAddChartReplyTest() {
		// Given: theme and data replies are empty, the chart reply is last
		PaidChartBuilder builder = new PaidChartBuilder(mock(DriveCopier.class), mock(ChartFileSharer.class),
				mock(ChartErrorTranslator.class), new PaidChartSheetRequests(), new PaidChartSlideRequests(), new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));
		BatchUpdateSpreadsheetResponse reply = new BatchUpdateSpreadsheetResponse().setReplies(List.of(
				new Response(), new Response(),
				new Response().setAddChart(new AddChartResponse().setChart(new EmbeddedChart().setChartId(913)))));

		// When:
		Integer chartId = builder.chartId(reply);

		// Then:
		assertThat(chartId).isEqualTo(913);
		assertThat(builder.chartId(new BatchUpdateSpreadsheetResponse())).isNull();
	}

	@Test
	void shouldDoNothingWithoutChartsTest() {
		// Given:
		DriveCopier driveCopier = mock(DriveCopier.class);
		PaidChartBuilder builder = new PaidChartBuilder(driveCopier, mock(ChartFileSharer.class),
				mock(ChartErrorTranslator.class), new PaidChartSheetRequests(), new PaidChartSlideRequests(), new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));

		// When:
		List<String> errors = builder.buildPaidCharts(new ChartClients(null, null, null),
				new PaidChartRequest("deck", "Acme", List.of(), null));

		// Then:
		assertThat(errors).isEmpty();
		verifyNoInteractions(driveCopier);
	}
}
