package com.aidigital.reportconstructor.service.reports.enums;

/**
 * Which report types a deck element appears in: the EOC deck, the EOM deck, or both.
 */
public enum ReportScope {

	EOC(true, false),
	EOM(false, true),
	BOTH(true, true);

	private final boolean inEoc;
	private final boolean inEom;

	ReportScope(boolean inEoc, boolean inEom) {
		this.inEoc = inEoc;
		this.inEom = inEom;
	}

	/**
	 * Whether the element appears in a deck of the given report type.
	 *
	 * @param eom whether the deck is an EOM report
	 * @return {@code true} when the element belongs in that deck
	 */
	public boolean includes(boolean eom) {
		return eom ? inEom : inEoc;
	}
}
