package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.engine.PaidValueFormatter;
import com.aidigital.reportconstructor.service.reports.enums.PaidChannelKind;
import com.aidigital.reportconstructor.service.reports.enums.PaidChartMarker;
import com.aidigital.reportconstructor.service.reports.model.PaidChannelData;
import com.aidigital.reportconstructor.service.reports.model.PaidMetrics;
import com.aidigital.reportconstructor.service.reports.model.PaidMonth;
import com.aidigital.reportconstructor.service.reports.model.PaidRow;
import com.aidigital.reportconstructor.service.reports.model.PaidSlideChart;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PaidSlideChartAssemblerImplTest {

	private final PaidSlideChartAssemblerImpl assembler = new PaidSlideChartAssemblerImpl(new PaidValueFormatter());

	@Test
	void shouldBuildTheSearchMonthlyChartsAndCampaignDonutTest() {
		// Given: a Search export with two months and two campaign types
		PaidMetrics oct = new PaidMetrics(10_000, 400, 1_000, 20);
		PaidMetrics nov = new PaidMetrics(20_000, 500, 1_500, 30);
		PaidChannelData search = new PaidChannelData(PaidChannelKind.SEARCH, oct.plus(nov), null,
				List.of(new PaidMonth(YearMonth.of(2026, 10), oct), new PaidMonth(YearMonth.of(2026, 11), nov)),
				List.of(new PaidRow("Brand", "", nov), new PaidRow("NonBrand", "", oct)),
				List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of());

		// When:
		Map<PaidChartMarker, PaidSlideChart> charts = byMarker(assembler.assemble(List.of(search)));

		// Then: only the Search charts; CTR is a fraction for the chart's percent axis
		assertThat(charts).containsOnlyKeys(PaidChartMarker.SEARCH_CLICKS_CTR, PaidChartMarker.SEARCH_SPEND,
				PaidChartMarker.SEARCH_CONVERSION_DONUT);
		PaidSlideChart clicks = charts.get(PaidChartMarker.SEARCH_CLICKS_CTR);
		assertThat(clicks.labels()).containsExactly("Oct", "Nov");
		assertThat(clicks.values()).containsExactly(400d, 500d);
		assertThat(clicks.lineValues().get(0)).isCloseTo(0.04, within(1e-9));
		assertThat(clicks.lineValues().get(1)).isCloseTo(0.025, within(1e-9));
		assertThat(charts.get(PaidChartMarker.SEARCH_SPEND).values()).containsExactly(1_000d, 1_500d);
		PaidSlideChart donut = charts.get(PaidChartMarker.SEARCH_CONVERSION_DONUT);
		assertThat(donut.labels()).containsExactly("Brand", "NonBrand");
		assertThat(donut.values()).containsExactly(30d, 20d);
		assertThat(donut.centerValue()).isEqualTo("50");
		assertThat(donut.hasData()).isTrue();
	}

	@Test
	void shouldSumPlacementsPastTheLegendIntoOtherAndLeaveEmptyChartsWithoutDataTest() {
		// Given: a Social export with six placements (the legend lists five) and no months or audiences
		List<PaidRow> placements = List.of(
				new PaidRow("Reels", "", new PaidMetrics(0, 0, 0, 60)),
				new PaidRow("Feed", "", new PaidMetrics(0, 0, 0, 20)),
				new PaidRow("Stories", "", new PaidMetrics(0, 0, 0, 10)),
				new PaidRow("Explore", "", new PaidMetrics(0, 0, 0, 5)),
				new PaidRow("Audience Network", "", new PaidMetrics(0, 0, 0, 3)),
				new PaidRow("Messenger", "", new PaidMetrics(0, 0, 0, 2)));
		PaidChannelData social = new PaidChannelData(PaidChannelKind.SOCIAL, PaidMetrics.ZERO, null, List.of(),
				List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
				placements, List.of());

		// When:
		Map<PaidChartMarker, PaidSlideChart> charts = byMarker(assembler.assemble(List.of(social)));

		// Then:
		PaidSlideChart placement = charts.get(PaidChartMarker.SOCIAL_PLACEMENT_DONUT);
		assertThat(placement.labels())
				.containsExactly("Reels", "Feed", "Stories", "Explore", "Audience Network", "Other");
		assertThat(placement.values()).containsExactly(60d, 20d, 10d, 5d, 3d, 2d);
		assertThat(placement.centerValue()).isEqualTo("100");
		assertThat(charts.get(PaidChartMarker.SOCIAL_CLICKS_CTR).hasData()).isFalse();
		assertThat(charts.get(PaidChartMarker.SOCIAL_AUDIENCE_DONUT).hasData()).isFalse();
	}

	@Test
	void shouldAddTheYearToMonthLabelsAcrossAYearBoundaryTest() {
		// Given:
		List<PaidMonth> months = List.of(new PaidMonth(YearMonth.of(2025, 12), PaidMetrics.ZERO),
				new PaidMonth(YearMonth.of(2026, 1), PaidMetrics.ZERO));

		// When:
		List<String> labels = assembler.monthLabels(months);

		// Then:
		assertThat(labels).containsExactly("Dec 25", "Jan 26");
	}

	private Map<PaidChartMarker, PaidSlideChart> byMarker(List<PaidSlideChart> charts) {
		return charts.stream().collect(Collectors.toMap(PaidSlideChart::marker, Function.identity()));
	}
}
