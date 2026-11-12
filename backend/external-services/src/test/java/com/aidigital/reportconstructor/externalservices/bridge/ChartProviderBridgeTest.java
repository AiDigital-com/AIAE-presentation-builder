package com.aidigital.reportconstructor.externalservices.bridge;

import com.aidigital.reportconstructor.externalservices.google.charts.SlidesChartClient;
import com.aidigital.reportconstructor.externalservices.google.charts.model.TacticChartsRequest;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import com.aidigital.reportconstructor.service.reports.engine.TacticPivotAssembler;
import com.aidigital.reportconstructor.service.reports.model.TacticPivots;
import com.aidigital.reportconstructor.service.reports.ports.ChartRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChartProviderBridgeTest {

	@Test
	void shouldPivotTheExportOverTheFlightWindowForTheBigQueryFlowTest() {
		// Given:
		SlidesChartClient client = mock(SlidesChartClient.class);
		TacticPivotAssembler assembler = mock(TacticPivotAssembler.class);
		FlightDates flight = new FlightDates(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
		List<List<String>> rows = List.of(List.of("Date", "Impressions"));
		List<LineItemMapping> mapping = List.of(new LineItemMapping("Display", "li-1", 1));
		Pivot pivot = new Pivot(new LinkedHashMap<>(Map.of("2026-01-01", new double[] {10, 0, 2})), false, true);
		when(assembler.assemble(rows, mapping, 1, flight, flight)).thenReturn(
				new TacticPivots(true, Map.of(1, List.of("li-1")), Map.of(1, pivot), Map.of(1, pivot)));
		ChartRequest request = new ChartRequest("deck-1", rows, mapping, flight, 1, "Acme", Map.of(1, "Display"),
				Map.of(1, 10.0), 10.0, Map.of(1, "vcr"), "tok", null, null);
		ChartProviderBridge bridge = new ChartProviderBridge(client, assembler, new ChartModelMapper());

		// When:
		bridge.buildCharts(request);

		// Then: the chart client draws the computed pivots and is told where they came from
		ArgumentCaptor<TacticChartsRequest> sent = ArgumentCaptor.forClass(TacticChartsRequest.class);
		verify(client).buildCharts(sent.capture());
		assertThat(sent.getValue().pacing().bqColumnsFound()).isTrue();
		assertThat(sent.getValue().pacing().lineItemIds()).isEqualTo(Map.of(1, List.of("li-1")));
		assertThat(sent.getValue().pacing().daily().fromSheet()).isFalse();
		assertThat(sent.getValue().pacing().daily().byTactic().get(1).hasCompletions()).isTrue();
		assertThat(sent.getValue().pacing().monthly().fromSheet()).isFalse();
		assertThat(sent.getValue().presentationId()).isEqualTo("deck-1");
		assertThat(sent.getValue().tacticKpiTypes()).isEqualTo(Map.of(1, "vcr"));
		assertThat(sent.getValue().userGoogleAccessToken()).isEqualTo("tok");
	}

	@Test
	void shouldPassTheSheetFlowsPivotsThroughWithoutPivotingTest() {
		// Given: the sheet flow carries pivots read back from the reviewed workbook and no export
		SlidesChartClient client = mock(SlidesChartClient.class);
		TacticPivotAssembler assembler = mock(TacticPivotAssembler.class);
		Pivot pivot = new Pivot(new LinkedHashMap<>(Map.of("March", new double[] {7, 1, 0})), true, false);
		ChartRequest request = new ChartRequest("deck-1", List.of(), List.of(), null, 1, "Acme", Map.of(),
				Map.of(), 0, Map.of(), null, Map.of(1, pivot), Map.of(1, pivot));
		ChartProviderBridge bridge = new ChartProviderBridge(client, assembler, new ChartModelMapper());

		// When:
		bridge.buildCharts(request);

		// Then:
		ArgumentCaptor<TacticChartsRequest> sent = ArgumentCaptor.forClass(TacticChartsRequest.class);
		verify(client).buildCharts(sent.capture());
		assertThat(sent.getValue().pacing().daily().fromSheet()).isTrue();
		assertThat(sent.getValue().pacing().monthly().fromSheet()).isTrue();
		assertThat(sent.getValue().pacing().monthly().byTactic().get(1).data()).containsOnlyKeys("March");
		verifyNoInteractions(assembler);
	}
}
