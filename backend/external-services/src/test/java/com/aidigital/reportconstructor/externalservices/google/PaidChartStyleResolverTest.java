package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.enums.PaidChartMarker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaidChartStyleResolverTest {

	@Test
	void shouldDrawEachPaidChartWithItsTemplateStyleTest() {
		// Given:
		PaidChartStyleResolver resolver = new PaidChartStyleResolver();

		// When-Then: search and social share the monthly styles; each donut family has its own
		assertThat(resolver.of(PaidChartMarker.SEARCH_CLICKS_CTR)).isEqualTo(PaidChartStyle.CLICKS_CTR);
		assertThat(resolver.of(PaidChartMarker.SOCIAL_CLICKS_CTR)).isEqualTo(PaidChartStyle.CLICKS_CTR);
		assertThat(resolver.of(PaidChartMarker.SEARCH_SPEND)).isEqualTo(PaidChartStyle.SPEND);
		assertThat(resolver.of(PaidChartMarker.SOCIAL_SPEND)).isEqualTo(PaidChartStyle.SPEND);
		assertThat(resolver.of(PaidChartMarker.SEARCH_CONVERSION_DONUT)).isEqualTo(PaidChartStyle.OVERVIEW_DONUT);
		assertThat(resolver.of(PaidChartMarker.SOCIAL_AUDIENCE_DONUT)).isEqualTo(PaidChartStyle.OVERVIEW_DONUT);
		assertThat(resolver.of(PaidChartMarker.SOCIAL_PLACEMENT_DONUT)).isEqualTo(PaidChartStyle.PLACEMENT_DONUT);
	}
}
