package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchView;
import com.aidigital.reportconstructor.service.reports.dto.TacticMapping;
import com.aidigital.reportconstructor.service.reports.services.LineItemMeta;
import com.aidigital.reportconstructor.service.reports.services.MatchResult;
import com.aidigital.reportconstructor.service.reports.services.TacticSuggestion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a raw {@link MatchResult} into the {@link LineItemMatchView} the wizard's mapping table shows:
 * line-item metadata indexed by id, ids grouped by channel, and one numbered row per tactic.
 */
@Component
public class LineItemMatchViewAssembler {

	/** Confidence value of an automatic (confident) match. */
	private static final String AUTO_CONFIDENCE = "auto";

	/**
	 * Builds the mapping-table view of a match result.
	 *
	 * @param result the matcher's raw result
	 * @return the view, with rows numbered from 1 in plan order
	 */
	public LineItemMatchView assemble(MatchResult result) {
		Map<String, LineItemMeta> idNamings = new LinkedHashMap<>();
		Map<String, List<String>> channelToIds = new LinkedHashMap<>();
		for (LineItemMeta meta : result.lineItems()) {
			idNamings.put(meta.id(), meta);
			if (meta.channel() != null && !meta.channel().isBlank()) {
				channelToIds.computeIfAbsent(meta.channel(), k -> new ArrayList<>()).add(meta.id());
			}
		}

		List<TacticMapping> mapping = new ArrayList<>();
		int autoCount = 0;
		int idx = 1;
		for (TacticSuggestion s : result.tactics()) {
			boolean auto = AUTO_CONFIDENCE.equalsIgnoreCase(s.confidence());
			if (auto) {
				autoCount++;
			}
			mapping.add(toRow(idx, s, auto, idNamings));
			idx++;
		}
		return new LineItemMatchView(mapping, result.uniqueIds(), idNamings, channelToIds, autoCount > 0, autoCount);
	}

	/**
	 * Builds one tactic row. Nothing has been excluded yet, so the report slot and the media-plan position
	 * coincide; the UI keeps the plan position pinned while renumbering the slot as the user drops rows.
	 *
	 * @param idx       1-based position of the tactic
	 * @param s         the matcher's suggestion for the tactic
	 * @param auto      whether the suggestion is an automatic match
	 * @param idNamings line-item metadata by id, for the naming sample
	 * @return the row
	 */
	TacticMapping toRow(int idx, TacticSuggestion s, boolean auto, Map<String, LineItemMeta> idNamings) {
		boolean hasId = s.lineItemId() != null && !s.lineItemId().isBlank();
		LineItemMeta meta = hasId ? idNamings.get(s.lineItemId()) : null;
		return new TacticMapping(
				idx, idx, s.tactic(), hasId ? s.lineItemId() : null, meta == null ? null : meta.naming(), auto);
	}
}
