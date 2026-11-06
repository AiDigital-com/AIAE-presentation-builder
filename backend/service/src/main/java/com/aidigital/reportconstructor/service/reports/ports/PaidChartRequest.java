package com.aidigital.reportconstructor.service.reports.ports;

import com.aidigital.reportconstructor.service.reports.model.PaidSlideChart;

import java.util.List;

/**
 * Inputs for {@link ChartProvider#buildPaidCharts(PaidChartRequest)}: the Paid Search / Paid Social charts of an MPO
 * deck, linked in place of the template's sample chart images once the paid slides are final.
 *
 * @param presentationId        the deck whose sample chart images are replaced
 * @param campaignTitle         deck title, used to name the chart spreadsheets and their Drive folder
 * @param charts                the charts of every channel with an export; a chart without data deletes its image
 * @param userGoogleAccessToken optional signed-in user token; when present the chart spreadsheets are created
 *                              as that user, matching where the deck was created
 */
public record PaidChartRequest(
		String presentationId,
		String campaignTitle,
		List<PaidSlideChart> charts,
		String userGoogleAccessToken) {
}
