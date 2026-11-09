package com.aidigital.reportconstructor.service.reports.dto;

/**
 * A tactic row pulled from the Media column together with the surrounding context used
 * to disambiguate duplicate tactic names.
 *
 * @param name       the tactic name as it appears in the Media column (original casing)
 * @param context    section/group label plus the row's other cells (comments, targeting, goal…), joined
 * @param planLineId the row's value in the plan's line-ID column ({@code ""} when the plan has none or the
 *                   cell is blank); the same ID sits in the 3rd segment of the line item's Level 1 naming
 */
public record PlanTactic(String name, String context, String planLineId) {

	/**
	 * Creates a tactic without a plan line ID, for plans that carry no line-ID column.
	 *
	 * @param name    the tactic name as it appears in the Media column (original casing)
	 * @param context section/group label plus the row's other cells, joined
	 */
	public PlanTactic(String name, String context) {
		this(name, context, "");
	}
}
