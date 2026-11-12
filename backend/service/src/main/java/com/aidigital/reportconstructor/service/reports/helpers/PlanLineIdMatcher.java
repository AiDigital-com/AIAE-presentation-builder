package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.PlanTactic;
import com.aidigital.reportconstructor.service.reports.services.LineItemMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Line-item matching pass 0: matches tactics by the media plan's line ID, which the campaign name builder
 * writes into the 3rd segment of every Level 1 naming.
 */
@Component
@RequiredArgsConstructor
public class PlanLineIdMatcher {

	/** Confidence recorded for a match this pass makes. */
	private static final String AUTO = "auto";

	private final LineItemNamingHelper lineItemNaming;
	private final TacticChannelCatalog channelCatalog;

	/**
	 * A tactic takes the one unassigned line item whose naming carries its plan line ID; when several do
	 * (the same ID reused across channels), only those in the tactic's expected channel are kept, and it
	 * matches only if exactly one remains. Tactics without a plan line ID, or still ambiguous, are left for
	 * the later passes. Mutates {@code matchedIds} and {@code confidences} in place.
	 *
	 * @param tactics     the extracted tactics (parallel to the id/confidence lists)
	 * @param matchedIds  per-tactic matched line item ID ("" when unmatched), mutated in place
	 * @param confidences per-tactic confidence ("none"/"auto"), mutated in place
	 * @param byId        line item ID &rarr; its metadata, in first-seen order
	 */
	public void apply(
			List<PlanTactic> tactics,
			List<String> matchedIds,
			List<String> confidences,
			Map<String, LineItemMeta> byId) {

		Map<String, List<String>> planLineToIds = new LinkedHashMap<>();
		for (LineItemMeta meta : byId.values()) {
			String planLineId = lineItemNaming.extractPlanLineId(meta.naming());
			if (planLineId != null) {
				planLineToIds.computeIfAbsent(planLineId.toLowerCase(Locale.ROOT), k -> new ArrayList<>())
						.add(meta.id());
			}
		}
		if (planLineToIds.isEmpty()) {
			return;
		}

		Set<String> assigned = new HashSet<>(matchedIds);
		assigned.remove("");
		for (int i = 0; i < tactics.size(); i++) {
			PlanTactic tactic = tactics.get(i);
			String planLineId = tactic.planLineId() == null ? "" : tactic.planLineId().trim();
			if (planLineId.isEmpty() || !matchedIds.get(i).isEmpty()) {
				continue;
			}
			List<String> candidates = planLineToIds.getOrDefault(planLineId.toLowerCase(Locale.ROOT), List.of())
					.stream()
					.filter(id -> !assigned.contains(id))
					.toList();
			if (candidates.size() > 1) {
				List<String> channels = channelCatalog.channelsFor(tactic.name());
				candidates = channels == null ? List.of() : candidates.stream()
						.filter(id -> channels.contains(byId.get(id).channel()))
						.toList();
			}
			if (candidates.size() == 1) {
				matchedIds.set(i, candidates.get(0));
				confidences.set(i, AUTO);
				assigned.add(candidates.get(0));
			}
		}
	}
}
