package com.aidigital.reportconstructor.service.reports.dto;

import com.aidigital.reportconstructor.service.reports.services.LineItemMeta;

import java.util.List;
import java.util.Map;

/**
 * The line-item matching result as the wizard's mapping table shows it.
 *
 * @param mapping        one row per media-plan tactic, in plan order
 * @param uniqueIds      every distinct line-item id found in the delivery rows
 * @param idNamings      line-item metadata indexed by id, in first-seen order
 * @param channelToIds   line-item ids grouped by channel, in first-seen order
 * @param autoMatched    whether at least one tactic was matched automatically
 * @param autoMatchCount number of automatically matched tactics
 */
public record LineItemMatchView(
		List<TacticMapping> mapping,
		List<String> uniqueIds,
		Map<String, LineItemMeta> idNamings,
		Map<String, List<String>> channelToIds,
		boolean autoMatched,
		int autoMatchCount) {
}
