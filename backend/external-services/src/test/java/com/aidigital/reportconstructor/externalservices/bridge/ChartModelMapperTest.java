package com.aidigital.reportconstructor.externalservices.bridge;

import com.aidigital.reportconstructor.externalservices.google.charts.DeckBreakdownSeries;
import com.aidigital.reportconstructor.externalservices.google.charts.PaidChartKind;
import com.aidigital.reportconstructor.externalservices.google.charts.PaidChartShape;
import com.aidigital.reportconstructor.externalservices.google.charts.model.BreakdownChart;
import com.aidigital.reportconstructor.externalservices.google.charts.model.BreakdownChartsRequest;
import com.aidigital.reportconstructor.externalservices.google.charts.model.ChartSlice;
import com.aidigital.reportconstructor.externalservices.google.charts.model.PaidChart;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PacingPivot;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PacingSeries;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownChartSeries;
import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import com.aidigital.reportconstructor.service.reports.enums.PaidChartMarker;
import com.aidigital.reportconstructor.service.reports.enums.PaidChartType;
import com.aidigital.reportconstructor.service.reports.model.PaidSlideChart;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartJob;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartRequest;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartSlice;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChartModelMapperTest {

	@Test
	void shouldDeclareTheSameBreakdownSeriesTest() {
		// Given: the chart builder looks series up by code and needs their section and labelling rule

		// When-Then:
		assertThat(Arrays.stream(DeckBreakdownSeries.values()).map(Enum::name))
				.containsExactly(Arrays.stream(BreakdownChartSeries.values()).map(Enum::name).toArray(String[]::new));
		for (BreakdownChartSeries series : BreakdownChartSeries.values()) {
			DeckBreakdownSeries deck = DeckBreakdownSeries.valueOf(series.name());
			assertThat(deck.code()).isEqualTo(series.code());
			assertThat(deck.section().name()).isEqualTo(series.section().name());
			assertThat(deck.labelsFromData()).isEqualTo(series.labelsFromData());
		}
	}

	@Test
	void shouldDeclareTheSamePaidChartsTest() {
		// Given: the paid chart pass finds each sample image by its marker

		// When-Then:
		assertThat(Arrays.stream(PaidChartShape.values()).map(Enum::name))
				.containsExactly(Arrays.stream(PaidChartType.values()).map(Enum::name).toArray(String[]::new));
		assertThat(Arrays.stream(PaidChartKind.values()).map(Enum::name))
				.containsExactly(Arrays.stream(PaidChartMarker.values()).map(Enum::name).toArray(String[]::new));
		for (PaidChartMarker marker : PaidChartMarker.values()) {
			PaidChartKind deck = PaidChartKind.valueOf(marker.name());
			assertThat(deck.marker()).isEqualTo(marker.marker());
			assertThat(deck.channel().name()).isEqualTo(marker.channel().name());
			assertThat(deck.type().name()).isEqualTo(marker.type().name());
			assertThat(deck.legendSlots()).isEqualTo(marker.legendSlots());
		}
	}

	@Test
	void shouldMapPivotsKeepingTacticAndDateOrderTest() {
		// Given:
		ChartModelMapper mapper = new ChartModelMapper();
		LinkedHashMap<String, double[]> data = new LinkedHashMap<>();
		data.put("2026-01-02", new double[] {5, 1, 0});
		data.put("2026-01-01", new double[] {3, 0, 0});
		Map<Integer, Pivot> byTactic = new LinkedHashMap<>();
		byTactic.put(2, new Pivot(data, true, false));
		byTactic.put(1, new Pivot(new LinkedHashMap<>(), false, false));

		// When:
		PacingSeries series = mapper.series(true, byTactic);

		// Then:
		assertThat(series.fromSheet()).isTrue();
		assertThat(series.byTactic().keySet()).containsExactly(2, 1);
		PacingPivot pivot = series.byTactic().get(2);
		assertThat(pivot.data().keySet()).containsExactly("2026-01-02", "2026-01-01");
		assertThat(pivot.hasClicks()).isTrue();
		assertThat(pivot.hasCompletions()).isFalse();
		assertThat(mapper.series(false, null).byTactic()).isEmpty();
	}

	@Test
	void shouldMapBreakdownAndPaidChartsTest() {
		// Given:
		ChartModelMapper mapper = new ChartModelMapper();
		BreakdownChartRequest breakdown = new BreakdownChartRequest("deck-1", "Acme",
				List.of(new BreakdownChartJob("dev", 2, List.of(new BreakdownChartSlice("Mobile", 61.0)))), "tok");
		PaidSlideChart paid = new PaidSlideChart(PaidChartMarker.SEARCH_SPEND, List.of("Jan"), List.of(10.0),
				List.of(), "");

		// When:
		BreakdownChartsRequest mappedBreakdown = mapper.breakdownCharts(breakdown);
		PaidChart mappedPaid = mapper.paidChart(paid);

		// Then:
		assertThat(mappedBreakdown).isEqualTo(new BreakdownChartsRequest("deck-1", "Acme",
				List.of(new BreakdownChart("dev", 2, List.of(new ChartSlice("Mobile", 61.0)))), "tok"));
		assertThat(mappedPaid).isEqualTo(new PaidChart(PaidChartKind.SEARCH_SPEND, List.of("Jan"), List.of(10.0),
				List.of(), ""));
	}
}
