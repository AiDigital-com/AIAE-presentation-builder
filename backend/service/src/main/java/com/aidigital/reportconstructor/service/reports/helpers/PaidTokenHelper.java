package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.model.PaidChannelData;

import java.util.List;
import java.util.Map;

/**
 * Fills the MPO deck's Paid Search ({@code {{ps_…}}}) and Paid Social ({@code {{pso_…}}}) data tokens from the
 * paid-media exports the request carries. Only figures are written here; the Claude-authored copy (verdicts,
 * takeaways, highlights, recommendations) and the table cells are filled elsewhere.
 */
public interface PaidTokenHelper {

	/**
	 * Parses each connected export and writes its channel's tokens into the map. A channel without an export,
	 * or whose export has no usable FULL-FLIGHT tab, writes nothing.
	 *
	 * @param flat    the resolved placeholder map, mutated in place
	 * @param payload the generation request: its exports, report type (EOC/EOM) and date window
	 * @return the parsed channels whose tokens were written, Search first; empty when no export is usable
	 */
	List<PaidChannelData> fillPaidTokens(Map<String, String> flat, GeneratePayload payload);
}
