package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.model.PaidChannelData;
import com.aidigital.reportconstructor.service.reports.model.PaidSlideChart;

import java.util.List;

/**
 * Turns the parsed paid-media exports into the data of the Paid Search / Paid Social charts: the monthly clicks
 * &amp; CTR and spend series of each overview slide, and the conversion splits of the donuts.
 */
public interface PaidSlideChartAssembler {

	/**
	 * Builds every chart of every channel present. A chart whose source tab is missing or empty is still returned,
	 * without data, so the chart pass deletes its sample image instead of shipping the sample.
	 *
	 * @param channels the parsed channels, as the token helper returns them
	 * @return the charts, in {@link com.aidigital.reportconstructor.service.reports.enums.PaidChartMarker} order
	 *         per channel
	 */
	List<PaidSlideChart> assemble(List<PaidChannelData> channels);
}
