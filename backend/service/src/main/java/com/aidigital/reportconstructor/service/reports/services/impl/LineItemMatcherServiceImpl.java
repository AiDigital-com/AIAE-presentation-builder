package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.common.security.AppUser;
import com.aidigital.reportconstructor.service.reports.dto.PlanTactic;
import com.aidigital.reportconstructor.service.reports.helpers.LineItemAiDisambiguator;
import com.aidigital.reportconstructor.service.reports.helpers.LineItemNamingHelper;
import com.aidigital.reportconstructor.service.reports.helpers.MediaPlanTacticExtractor;
import com.aidigital.reportconstructor.service.reports.helpers.PlanLineIdMatcher;
import com.aidigital.reportconstructor.service.reports.helpers.TacticChannelCatalog;
import com.aidigital.reportconstructor.service.reports.services.LineItemMatcherService;
import com.aidigital.reportconstructor.service.reports.services.LineItemMeta;
import com.aidigital.reportconstructor.service.reports.services.MatchResult;
import com.aidigital.reportconstructor.service.reports.services.TacticSuggestion;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reads the "Level 1 Naming" column from a BigQuery export, derives the line item ID from the 9th
 * underscore-delimited segment, then maps Media-Plan tactics to those IDs in three passes:
 * <ol>
 *   <li>the plan's own line ID, written into every naming ({@link PlanLineIdMatcher});</li>
 *   <li>the "unique ID" rule — a tactic auto-matches when its expected channel
 *       ({@link TacticChannelCatalog}) holds exactly one line item ID;</li>
 *   <li>AI disambiguation for channels still ambiguous ({@link LineItemAiDisambiguator}).</li>
 * </ol>
 *
 * <p>Tactic extraction is delegated to {@link MediaPlanTacticExtractor} so the tactic numbering always
 * matches the collector's; tactics are emitted in Media-column order and the response index (tactic
 * number) lines up with the collector's tactic numbering.
 */
@Service
@RequiredArgsConstructor
public class LineItemMatcherServiceImpl implements LineItemMatcherService {

	/** Confidence of a confident (automatic) match. */
	private static final String AUTO = "auto";

	/** Confidence of a tactic left for manual matching. */
	private static final String NONE = "none";

	private final LineItemNamingHelper lineItemNaming;
	private final MediaPlanTacticExtractor tacticExtractor;
	private final ClaudeUsageTracker usageTracker;
	private final TacticChannelCatalog channelCatalog;
	private final PlanLineIdMatcher planLineIdMatcher;
	private final LineItemAiDisambiguator aiDisambiguator;

	@Override
	public MatchResult match(List<List<String>> bqRows, List<List<String>> planRows, AppUser caller) {
		if (bqRows == null || bqRows.isEmpty()) {
			throw new AppException(ErrorReason.C002, "BQ rows are required");
		}
		// This match may call Claude to break ties, and it runs in a web request rather than in a report
		// job, so nothing else would attribute that spend. The scope carries no job id — by design, the
		// tokens belong to the user, not to a report — and is cleared below so the request thread, which
		// is pooled, does not carry it into the next caller's request.
		usageTracker.begin(null, caller == null ? null : caller.userId(), caller == null ? null : caller.email());
		try {
			return matchInScope(bqRows, planRows);
		} finally {
			usageTracker.clear();
		}
	}

	/**
	 * Runs the match itself, with the caller's usage scope already bound.
	 *
	 * @param bqRows   BigQuery export rows with the header on the first row
	 * @param planRows Media Plan rows from which whitelisted tactic names are extracted
	 * @return the tactic suggestions together with all distinct line item metadata and IDs
	 */
	MatchResult matchInScope(List<List<String>> bqRows, List<List<String>> planRows) {
		List<String> bqHeaders = bqRows.get(0);
		int l1ColIdx = indexOfHeader(bqHeaders, h -> h.toLowerCase(Locale.ROOT).contains("level 1 naming"));
		if (l1ColIdx < 0) {
			throw new AppException(ErrorReason.C002, "Column 'Level 1 Naming' not found in BigQuery export");
		}
		int tacticColIdx = indexOfHeader(bqHeaders, h -> h.toLowerCase(Locale.ROOT).equals("tactic"));
		int channelColIdx = indexOfHeader(bqHeaders, h -> h.toLowerCase(Locale.ROOT).contains("channel"));

		// Line item metadata (first occurrence wins) + Channel -> [ids] lookup
		// (exact case, dedup, first-seen order) — both in a single pass over BQ.
		Map<String, LineItemMeta> byId = new LinkedHashMap<>();
		Map<String, List<String>> channelToIds = new LinkedHashMap<>();
		for (int i = 1; i < bqRows.size(); i++) {
			List<String> row = bqRows.get(i);
			String naming = cell(row, l1ColIdx).trim();
			String id = naming.isEmpty() ? null : lineItemNaming.extractLineItemId(naming);
			if (id == null) {
				continue;
			}
			String channel = cell(row, channelColIdx).trim();
			byId.computeIfAbsent(id, key -> new LineItemMeta(key, naming, channel, cell(row, tacticColIdx).trim()));
			if (!channel.isEmpty()) {
				List<String> ids = channelToIds.computeIfAbsent(channel, k -> new ArrayList<>());
				if (!ids.contains(id)) {
					ids.add(id);
				}
			}
		}

		// IDs are pure digits, so order numerically.
		List<String> uniqueIds = byId.keySet().stream().sorted(Comparator.comparing(BigInteger::new)).toList();
		List<LineItemMeta> lineItems = uniqueIds.stream().map(byId::get).toList();

		List<PlanTactic> tactics = tacticExtractor.extract(planRows);
		int size = tactics.size();
		List<String> matchedIds = new ArrayList<>(Collections.nCopies(size, ""));
		List<String> confidences = new ArrayList<>(Collections.nCopies(size, NONE));

		// Pass 0 — plan line ID.
		planLineIdMatcher.apply(tactics, matchedIds, confidences, byId);
		// Pass 1 — deterministic unique-ID rule.
		applyUniqueIdRule(tactics, matchedIds, confidences, channelToIds);
		// Pass 2 — AI disambiguation for channels the unique-ID rule left ambiguous.
		aiDisambiguator.apply(tactics, matchedIds, confidences, channelToIds, byId);

		List<TacticSuggestion> suggestions = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			suggestions.add(new TacticSuggestion(tactics.get(i).name(), matchedIds.get(i), confidences.get(i)));
		}
		return new MatchResult(suggestions, lineItems, uniqueIds);
	}

	/**
	 * Pass 1: a still-unmatched tactic auto-matches when its expected channel holds exactly one line item
	 * ID that pass 0 has not already given to another tactic. Mutates the lists in place.
	 *
	 * @param tactics      the extracted tactics (parallel to the id/confidence lists)
	 * @param matchedIds   per-tactic matched line item ID ("" when unmatched), mutated in place
	 * @param confidences  per-tactic confidence ("none"/"auto"), mutated in place
	 * @param channelToIds channel &rarr; its distinct line item IDs
	 */
	void applyUniqueIdRule(
			List<PlanTactic> tactics,
			List<String> matchedIds,
			List<String> confidences,
			Map<String, List<String>> channelToIds) {
		Set<String> claimedByPlanLineId = new HashSet<>(matchedIds);
		claimedByPlanLineId.remove("");
		for (int i = 0; i < tactics.size(); i++) {
			if (!matchedIds.get(i).isEmpty()) {
				continue;
			}
			List<String> channels = channelCatalog.channelsFor(tactics.get(i).name());
			if (channels == null) {
				continue;
			}
			for (String ch : channels) {
				List<String> ids = channelToIds.getOrDefault(ch, List.of());
				if (ids.size() == 1 && !claimedByPlanLineId.contains(ids.get(0))) {
					matchedIds.set(i, ids.get(0));
					confidences.set(i, AUTO);
					break;
				}
			}
		}
	}

	/**
	 * Finds the first header column satisfying the predicate, so a column can be located by its name
	 * rather than by a fixed position.
	 *
	 * @param headers the header row
	 * @param match   the predicate applied to each header, with a null header passed as empty
	 * @return the zero-based column index, or {@code -1} when no header matches
	 */
	int indexOfHeader(List<String> headers, Predicate<String> match) {
		for (int i = 0; i < headers.size(); i++) {
			if (match.test(headers.get(i) == null ? "" : headers.get(i))) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Reads one cell by index, returning an empty string when the row is null or too short.
	 *
	 * @param row the sheet row
	 * @param idx the zero-based column index
	 * @return the cell text, or an empty string when the cell is absent
	 */
	String cell(List<String> row, int idx) {
		if (idx < 0 || row == null || idx >= row.size()) {
			return "";
		}
		return row.get(idx) == null ? "" : row.get(idx);
	}
}
