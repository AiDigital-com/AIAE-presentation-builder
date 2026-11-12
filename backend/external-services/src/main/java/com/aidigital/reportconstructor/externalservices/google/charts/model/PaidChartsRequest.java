package com.aidigital.reportconstructor.externalservices.google.charts.model;


import java.util.List;

/**
 * Inputs for {@code SlidesChartClient#buildPaidCharts}: the Paid Search / Paid Social charts of an MPO deck,
 * linked in place of the template's sample chart images once the paid slides are final.
 *
 * <p>The Google integration's copy of the service type of the same shape; the bridge maps between
 * them and {@code ChartModelMapperTest} pins the values.
 *
 * @param presentationId        the deck whose sample chart images are replaced
 * @param campaignTitle         deck title, used to name the chart spreadsheets and their Drive folder
 * @param charts                the charts of every channel with an export; a chart without data deletes its image
 * @param userGoogleAccessToken optional signed-in user token; when present the chart spreadsheets are created
 *                              as that user, matching where the deck was created
 */
public record PaidChartsRequest(
		String presentationId,
		String campaignTitle,
		List<PaidChart> charts,
		String userGoogleAccessToken) {
}
