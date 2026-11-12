package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.externalservices.google.charts.PaidChartKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaidChartStyleResolverTest {

	@Test
	void shouldDrawEachPaidChartWithItsTemplateStyleTest() {
		// Given:
		PaidChartStyleResolver resolver = new PaidChartStyleResolver();

		// When-Then: search and social share the monthly styles; each donut family has its own
		assertThat(resolver.of(PaidChartKind.SEARCH_CLICKS_CTR)).isEqualTo(PaidChartStyle.CLICKS_CTR);
		assertThat(resolver.of(PaidChartKind.SOCIAL_CLICKS_CTR)).isEqualTo(PaidChartStyle.CLICKS_CTR);
		assertThat(resolver.of(PaidChartKind.SEARCH_SPEND)).isEqualTo(PaidChartStyle.SPEND);
		assertThat(resolver.of(PaidChartKind.SOCIAL_SPEND)).isEqualTo(PaidChartStyle.SPEND);
		assertThat(resolver.of(PaidChartKind.SEARCH_CONVERSION_DONUT)).isEqualTo(PaidChartStyle.OVERVIEW_DONUT);
		assertThat(resolver.of(PaidChartKind.SOCIAL_AUDIENCE_DONUT)).isEqualTo(PaidChartStyle.OVERVIEW_DONUT);
		assertThat(resolver.of(PaidChartKind.SOCIAL_PLACEMENT_DONUT)).isEqualTo(PaidChartStyle.PLACEMENT_DONUT);
	}
}
