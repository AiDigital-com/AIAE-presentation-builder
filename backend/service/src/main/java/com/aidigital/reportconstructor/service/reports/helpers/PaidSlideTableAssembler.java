package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.model.PaidChannelData;
import com.aidigital.reportconstructor.service.reports.model.PaidSlidesFill;

import java.util.List;

/**
 * Turns the parsed paid-media exports into what the deck pass does to the MPO deck's Paid Search / Paid Social
 * section: the cell values of every marker table, and the channels whose slides are deleted for want of an export.
 */
public interface PaidSlideTableAssembler {

	/**
	 * @param channels the parsed channels whose tokens were written, as the token helper returns them
	 * @param eom      whether the deck is an EOM report, which adds the metric master tables
	 * @return the tables to write and the channels to delete
	 */
	PaidSlidesFill assemble(List<PaidChannelData> channels, boolean eom);
}
