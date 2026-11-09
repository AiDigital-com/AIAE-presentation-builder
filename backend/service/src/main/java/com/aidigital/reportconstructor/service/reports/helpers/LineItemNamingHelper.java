package com.aidigital.reportconstructor.service.reports.helpers;

/**
 * Extracts numeric line item IDs from BigQuery Level 1 Naming strings.
 */
public interface LineItemNamingHelper {

	/**
	 * Parses the 9th underscore-delimited segment as a numeric line item ID.
	 *
	 * @param naming Level 1 Naming cell value (may be {@code null})
	 * @return the numeric ID, or {@code null} when absent or invalid
	 */
	String extractLineItemId(String naming);

	/**
	 * Chart pivot variant that returns an empty string instead of {@code null}.
	 *
	 * @param naming Level 1 Naming cell value (may be {@code null})
	 * @return the numeric ID, or {@code ""} when absent or invalid
	 */
	String extractLineItemIdOrBlank(String naming);

	/**
	 * Parses the 3rd underscore-delimited segment — the media plan's line ID, written there by the
	 * campaign name builder (e.g. {@code "24"} in {@code GCVB_Grapevine…_24_Main Street Fest 2026_…}).
	 * Only well-formed namings (the same 9+ segments a line item ID needs) are read.
	 *
	 * @param naming Level 1 Naming cell value (may be {@code null})
	 * @return the trimmed plan line ID, or {@code null} when the naming is malformed or the segment is blank/"-"
	 */
	String extractPlanLineId(String naming);
}
