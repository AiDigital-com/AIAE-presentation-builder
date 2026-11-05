package com.aidigital.reportconstructor.service.reports.model;

import com.aidigital.reportconstructor.service.reports.enums.PaidChannelKind;

import java.util.List;
import java.util.Set;

/**
 * What the deck pass does to an MPO deck's Paid Search / Paid Social section once its tokens are written.
 *
 * @param tables         the data tables of the channels that have an export, written cell by cell
 * @param absentChannels the channels without a usable export, whose slides are deleted from the deck
 */
public record PaidSlidesFill(List<PaidSlideTable> tables, Set<PaidChannelKind> absentChannels) {

	/**
	 * @return {@code true} when the pass has nothing to write and nothing to delete
	 */
	public boolean isEmpty() {
		return tables.isEmpty() && absentChannels.isEmpty();
	}
}
