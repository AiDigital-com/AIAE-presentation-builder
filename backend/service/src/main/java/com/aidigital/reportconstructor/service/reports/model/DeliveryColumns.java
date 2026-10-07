package com.aidigital.reportconstructor.service.reports.model;

/**
 * Where each metric lives in the delivery export's grid, resolved once from its header row.
 *
 * <p>The export is produced per client and the column order is not guaranteed, so every read goes
 * through an index found by header name rather than by position. {@code -1} means the column is
 * absent, which is a normal state rather than an error: a display campaign has no completions, and
 * an export without a line-item id falls back to the Level 1 naming column.
 *
 * @param headerRow     index of the header row within the export, or {@code -1} when no row carried
 *                      the required date/channel/cost/impressions set
 * @param date          delivery date column
 * @param channel       channel column
 * @param cost          cost column
 * @param impressions   impressions column
 * @param clicks        clicks column, or {@code -1}
 * @param completions   completions column, or {@code -1}; its absence is what makes VCR unreportable
 * @param dayOfWeek     pre-computed day-of-week column, or {@code -1} to derive it from the date
 * @param lineItemId    line-item id column, or {@code -1}
 * @param creative      creative-name column, or {@code -1}
 * @param level1Naming  fallback column used to derive a line-item id when {@code lineItemId} is -1
 */
public record DeliveryColumns(
		int headerRow,
		int date,
		int channel,
		int cost,
		int impressions,
		int clicks,
		int completions,
		int dayOfWeek,
		int lineItemId,
		int creative,
		int level1Naming
) {

	/**
	 * Reports whether a usable header row was found at all.
	 *
	 * @return true when the export carried the required date/channel/cost/impressions columns
	 */
	public boolean found() {
		return headerRow >= 0;
	}
}
