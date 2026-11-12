package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchOption;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchTactic;
import com.aidigital.reportconstructor.service.reports.dto.PlanTactic;
import com.aidigital.reportconstructor.service.reports.ports.LineItemMatchAssistant;
import com.aidigital.reportconstructor.service.reports.services.LineItemMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Line-item matching pass 2: asks the AI matcher to break ties the unique-ID rule left ambiguous
 * (several tactics and several line item IDs sharing one channel).
 */
@Component
@RequiredArgsConstructor
public class LineItemAiDisambiguator {

	/** Confidence recorded for a match this pass makes. */
	private static final String AUTO = "auto";

	private final LineItemMatchAssistant matchAssistant;
	private final TacticChannelCatalog channelCatalog;

	/**
	 * Runs the AI matcher over tactics still unmatched whose expected channel holds more than one line item
	 * ID, then applies each returned assignment defensively: the ID must be unassigned and belong to the
	 * tactic's expected channel. Mutates {@code matchedIds} and {@code confidences} in place; a no-op when
	 * nothing is ambiguous or the assistant is stubbed.
	 *
	 * @param tactics      the extracted tactics (parallel to the id/confidence lists)
	 * @param matchedIds   per-tactic matched line item ID ("" when unmatched), mutated in place
	 * @param confidences  per-tactic confidence ("none"/"auto"), mutated in place
	 * @param channelToIds channel &rarr; its distinct line item IDs
	 * @param byId         line item ID &rarr; its metadata (used for the channel guard)
	 */
	public void apply(
			List<PlanTactic> tactics,
			List<String> matchedIds,
			List<String> confidences,
			Map<String, List<String>> channelToIds,
			Map<String, LineItemMeta> byId) {

		Set<String> assigned = new HashSet<>(matchedIds);
		assigned.remove("");

		List<LineItemMatchTactic> ambiguous = new ArrayList<>();
		Set<String> wantedChannels = new HashSet<>();
		for (int i = 0; i < tactics.size(); i++) {
			if (!matchedIds.get(i).isEmpty()) {
				continue;
			}
			List<String> channels = channelCatalog.channelsFor(tactics.get(i).name());
			if (channels == null) {
				continue;
			}
			boolean multi = channels.stream().anyMatch(ch -> channelToIds.getOrDefault(ch, List.of()).size() > 1);
			if (!multi) {
				continue;
			}
			String channel = channels.get(0);
			wantedChannels.add(channel);
			ambiguous.add(new LineItemMatchTactic(i + 1, tactics.get(i).name(), channel, tactics.get(i).context()));
		}
		if (ambiguous.isEmpty()) {
			return;
		}

		List<LineItemMatchOption> options = optionsFor(wantedChannels, channelToIds, byId, assigned);
		if (options.isEmpty()) {
			return;
		}

		Map<Integer, String> picks = matchAssistant.match(ambiguous, options);
		for (Map.Entry<Integer, String> pick : picks.entrySet()) {
			int idx = pick.getKey() - 1;
			String id = pick.getValue();
			if (idx < 0 || idx >= tactics.size() || !matchedIds.get(idx).isEmpty() || assigned.contains(id)) {
				continue;
			}
			List<String> channels = channelCatalog.channelsFor(tactics.get(idx).name());
			LineItemMeta meta = byId.get(id);
			if (channels == null || meta == null || !channels.contains(meta.channel())) {
				continue;
			}
			matchedIds.set(idx, id);
			confidences.set(idx, AUTO);
			assigned.add(id);
		}
	}

	/**
	 * Lists the still-unassigned line items of the channels the ambiguous tactics expect.
	 *
	 * @param wantedChannels channels at least one ambiguous tactic expects
	 * @param channelToIds   channel &rarr; its distinct line item IDs
	 * @param byId           line item ID &rarr; its metadata
	 * @param assigned       IDs already matched to a tactic
	 * @return the options offered to the AI matcher
	 */
	List<LineItemMatchOption> optionsFor(
			Set<String> wantedChannels,
			Map<String, List<String>> channelToIds,
			Map<String, LineItemMeta> byId,
			Set<String> assigned) {
		List<LineItemMatchOption> options = new ArrayList<>();
		for (String channel : wantedChannels) {
			for (String id : channelToIds.getOrDefault(channel, List.of())) {
				if (assigned.contains(id)) {
					continue;
				}
				LineItemMeta meta = byId.get(id);
				options.add(new LineItemMatchOption(id, channel, meta == null ? "" : meta.naming()));
			}
		}
		return options;
	}
}
