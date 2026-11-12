package com.aidigital.reportconstructor.externalservices.google.deck.model;

import com.aidigital.reportconstructor.externalservices.google.deck.PaidDeckChannel;

import java.util.List;
import java.util.Set;

/**
 * What to write into an MPO deck's paid section: the marker tables to fill and the channels whose slides
 * are to be deleted because the report has no export for them.
 *
 * @param tables         tables to write
 * @param absentChannels channels whose slides are deleted
 */
public record PaidDeckFill(List<PaidDeckTable> tables, Set<PaidDeckChannel> absentChannels) {

	/**
	 * Tells whether there is nothing to write or delete.
	 *
	 * @return {@code true} when both lists are empty
	 */
	public boolean isEmpty() {
		return tables.isEmpty() && absentChannels.isEmpty();
	}
}
