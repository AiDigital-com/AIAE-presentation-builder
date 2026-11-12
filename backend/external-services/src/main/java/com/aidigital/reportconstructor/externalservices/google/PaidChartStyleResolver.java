package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.externalservices.google.charts.PaidChartKind;
import org.springframework.stereotype.Component;

/**
 * Maps each paid chart of the MPO template to the {@link PaidChartStyle} it is drawn with. A bean rather than a
 * static factory on the enum, so callers can replace it in tests.
 */
@Component
public class PaidChartStyleResolver {

	/**
	 * Returns the style a paid chart is drawn with.
	 *
	 * @param marker a paid chart of the template
	 * @return the style that chart is drawn with
	 */
	public PaidChartStyle of(PaidChartKind marker) {
		return switch (marker) {
			case SEARCH_CLICKS_CTR, SOCIAL_CLICKS_CTR -> PaidChartStyle.CLICKS_CTR;
			case SEARCH_SPEND, SOCIAL_SPEND -> PaidChartStyle.SPEND;
			case SEARCH_CONVERSION_DONUT, SOCIAL_AUDIENCE_DONUT -> PaidChartStyle.OVERVIEW_DONUT;
			case SOCIAL_PLACEMENT_DONUT -> PaidChartStyle.PLACEMENT_DONUT;
		};
	}
}
