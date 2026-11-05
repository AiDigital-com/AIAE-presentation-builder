package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * Context for one paid channel's narrative call: the figures its slides print, one {@code "Label: value"} line
 * each, and the copy fields to write.
 *
 * @param channelName the channel as the deck names it, e.g. {@code "Paid Search (Google Ads)"}
 * @param periodLabel the period the figures cover, e.g. {@code "the full flight"} or {@code "October 2026"}
 * @param facts       the channel's figures (totals, months and breakdown rows), never {@code null}
 * @param fields      the copy fields to write, in slide order, never {@code null}
 */
public record PaidNarrativeInput(
		String channelName,
		String periodLabel,
		List<String> facts,
		List<PaidCopyField> fields
) {
}
