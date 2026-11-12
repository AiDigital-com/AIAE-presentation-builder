package com.aidigital.reportconstructor.externalservices.google.deck.model;

import com.aidigital.reportconstructor.externalservices.google.deck.PaidDeckTableMarker;

import java.util.List;

/**
 * One marker table's content to write into a deck.
 *
 * @param marker   the table to write
 * @param bodyRows body rows, each a list of cell texts
 * @param totalRow the total row's cell texts, or {@code null} when the table has none
 */
public record PaidDeckTable(PaidDeckTableMarker marker, List<List<String>> bodyRows, List<String> totalRow) {

	/**
	 * Tells whether a total row is to be written.
	 *
	 * @return {@code true} when {@link #totalRow()} is present
	 */
	public boolean hasTotalRow() {
		return totalRow != null;
	}
}
