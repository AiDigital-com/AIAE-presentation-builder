package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownValues;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.TacticConclusion;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughts;
import com.aidigital.reportconstructor.service.reports.engine.EomDashboardResolver;
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.aidigital.reportconstructor.service.reports.ports.DeckMarkup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Placeholder-map and Claude-input shaping shared by the report pipelines: tactic names and counts,
 * thoughts tokens, sheet-token aliases, the brief/change-log context, the breakdown digest and the
 * funnel-stages fill.
 */
@Component
@RequiredArgsConstructor
public class ReportTokenAssembler {

	/** Deck/sheet field carrying the campaign's marketing funnel stages. */
	private static final String FUNNEL_STAGES_TOKEN = "{{funnel_stages}}";

	/** Value an unresolved placeholder flattens to; treated as "no value" when filling the funnel stages. */
	private static final String DASH = "—";

	/** Report type whose deck ships without the frequency slide (and therefore without its narrative). */
	private static final String EOM_REPORT_TYPE = "EOM";

	/** Max tactics the report template carries; the derived tactic count is clamped to this. */
	private static final int MAX_TACTICS = 28;

	/**
	 * Tokens on the per-tactic "thoughts on tactic performance" slide: four analytical thoughts plus the
	 * closing story in slot 5, which Claude writes in the same Step-3 call and the client returns as the
	 * last entry of the tactic's thoughts list.
	 */
	private static final int THOUGHTS_ON_TACTIC_COUNT = 5;

	/** Max breakdown-conclusion lines fed to Batch D as read-only alignment context, bounding its input size. */
	private static final int BREAKDOWN_DIGEST_MAX_LINES = 80;

	/** Per-line character cap for the Batch D breakdown digest; long slide copy is truncated for context only. */
	private static final int BREAKDOWN_DIGEST_LINE_MAX = 160;

	private final Fmt fmt;
	private final ReportNumberParser reportNumbers;
	private final EomDashboardResolver eomDashboards;

	/**
	 * Reads each tactic's display name back out of the seed placeholder map ({@code {{tactic n}}}), for the
	 * Step-3 thoughts inputs. A blank slot is skipped.
	 *
	 * @param flatReplacements the seed placeholder map
	 * @param tacticCount      the active tactic count
	 * @return 1-based tactic number → display name, for the tactics that carry a name
	 */
	public Map<Integer, String> tacticNames(Map<String, String> flatReplacements, int tacticCount) {
		Map<Integer, String> names = new LinkedHashMap<>();
		for (int n = 1; n <= tacticCount; n++) {
			String name = flatReplacements.get("{{tactic " + n + "}}");
			if (name != null && !name.isBlank()) {
				names.put(n, name.trim());
			}
		}
		return names;
	}

	/**
	 * Writes the five {@code {{thoughts on tactic n performance 1..5}}} tokens for every qualifying tactic from
	 * its Step-3 thoughts, blanking a qualifying tactic that produced none so its (inserted) slide never ships a
	 * raw token. Also carries the tactic's {@code {{tactic n}}} display name into the map, so the thoughts slide
	 * title renumbers to the name the same way the breakdown slides do (their helpers copy the name token in).
	 *
	 * @param breakdownValues the breakdown token map to write the thoughts tokens into
	 * @param qualifying      the tactics that passed the "&gt; 2 breakdowns" gate (and get the thoughts slide)
	 * @param thoughts        the Step-3 thoughts that were produced
	 * @param flatReplacements the seed placeholder map, read for each tactic's {@code {{tactic n}}} display name
	 */
	public void writeThoughtsTokens(
			Map<String, String> breakdownValues, Set<Integer> qualifying, List<TacticThoughts> thoughts,
			Map<String, String> flatReplacements) {
		Map<Integer, List<String>> byTactic = new LinkedHashMap<>();
		for (TacticThoughts t : thoughts) {
			if (t != null && t.thoughts() != null) {
				byTactic.put(t.tacticNum(), t.thoughts());
			}
		}
		for (Integer n : qualifying) {
			String name = flatReplacements.get("{{tactic " + n + "}}");
			if (name != null && !name.isBlank()) {
				breakdownValues.put("{{tactic " + n + "}}", name);
			}
			List<String> tacticThoughts = byTactic.getOrDefault(n, List.of());
			for (int i = 1; i <= THOUGHTS_ON_TACTIC_COUNT; i++) {
				String value = i <= tacticThoughts.size() ? tacticThoughts.get(i - 1) : null;
				breakdownValues.put(
						"{{thoughts on tactic " + n + " performance " + i + "}}", value == null ? "" : value);
			}
		}
	}

	/**
	 * Rebuilds the campaign results with the Step-2 per-tactic overviews merged into their (empty) slot, so the
	 * tactic-overview slides fill while the campaign-level copy stays as Step 4 (and the Step-5 alignment)
	 * produced it.
	 *
	 * @param campaign    the Step-4 campaign results, carrying an empty tactic-overview map
	 * @param conclusions the Step-2 conclusions, source of the per-tactic overviews
	 * @return the campaign results with the per-tactic overviews filled in
	 */
	public ClaudeResults mergeTacticOverviews(ClaudeResults campaign, List<TacticConclusion> conclusions) {
		Map<Integer, String> overviews = new LinkedHashMap<>();
		for (TacticConclusion c : conclusions) {
			if (c.overview() != null && !c.overview().isBlank()) {
				overviews.put(c.tacticNum(), c.overview());
			}
		}
		return new ClaudeResults(
				campaign.resultsOverviews(), campaign.thoughtsOnPerformance(), overviews,
				campaign.recommendations(), campaign.fOpportunity(), campaign.fFact(), campaign.fStorytelling());
	}

	/**
	 * Reports whether a campaign-results batch carries no campaign-level narrative at all, i.e. it degraded to the
	 * {@code ReportClaudeDefaults#emptyResults()} shape (empty results overviews, performance thoughts and
	 * recommendations). The per-tactic {@code tacticOverviews} and the frequency copy are deliberately excluded:
	 * the overviews are merged in from the Step-2 conclusions afterwards, and the frequency copy is legitimately
	 * {@code null} when the sheet has no reach data, so neither indicates a Batch C failure.
	 *
	 * @param campaign the raw {@link ClaudeResults} returned by {@code batchCampaignResults}, before overview merge
	 * @return {@code true} when the results overviews, performance thoughts and recommendations are all empty
	 */
	public boolean isCampaignResultsEmpty(ClaudeResults campaign) {
		return campaign.resultsOverviews().isEmpty()
				&& campaign.thoughtsOnPerformance().isEmpty()
				&& campaign.recommendations().isEmpty();
	}

	/**
	 * Returns the highest tactic number the collector recognised in the campaign data, clamped to
	 * 1..{@link #MAX_TACTICS}. The collector's {@code data.tactics()} keys already exclude unnamed slots, so
	 * this is the real tactic count used to bound the Slides placeholder map to tactics the campaign has.
	 *
	 * @param data the aggregated campaign snapshot whose tactic keys bound the count
	 * @return the real tactic count (1..28)
	 */
	public int maxTacticNumber(CampaignData data) {
		int max = 0;
		if (data != null && data.tactics() != null) {
			for (Integer n : data.tactics().keySet()) {
				if (n != null && n > max) {
					max = n;
				}
			}
		}
		return Math.clamp(max, 1, MAX_TACTICS);
	}

	/**
	 * Returns a copy of the payload with every raw source grid stripped (brief, report type, date filter
	 * and sheet URL are kept). The slides-from-sheet flow feeds this to the placeholder builder so the
	 * Claude-authored narrative still resolves while no per-tactic or total number can be recomputed from
	 * the raw media plan — the sheet overlay is the sole source of numeric placeholders.
	 *
	 * @param payload the inbound generation payload
	 * @return a narrative-only payload carrying no raw media-plan/adjustment/estimate grids
	 */
	public GeneratePayload narrativeOnly(GeneratePayload payload) {
		return new GeneratePayload(
				payload.brief(), payload.reportType(), null,
				List.of(), List.of(), List.of(), List.of(), List.of(),
				null, null, null, payload.dateFilter(), payload.sheetUrl(),
				payload.changeLog(), payload.estimateDaypartGender());
	}

	/**
	 * Flattens the per-tactic breakdown conclusions into a compact, bounded list of {@code token: text} lines
	 * for Batch D to read as read-only alignment context. Blank values are skipped, each line is capped at
	 * {@link #BREAKDOWN_DIGEST_LINE_MAX} characters, and the whole digest is capped at
	 * {@link #BREAKDOWN_DIGEST_MAX_LINES} lines so a large, many-tactic deck cannot bloat the alignment prompt.
	 *
	 * @param sections the built breakdown-value bundles (publisher/creative/geo/audience/device), any of which
	 *                 may be empty when its section was not selected
	 * @return one short line per non-blank breakdown conclusion, bounded in both line length and count
	 */
	public List<String> buildBreakdownDigest(List<BreakdownValues> sections) {
		List<String> digest = new ArrayList<>();
		for (BreakdownValues section : sections) {
			if (section == null || section.values() == null) {
				continue;
			}
			for (Map.Entry<String, String> entry : section.values().entrySet()) {
				String value = entry.getValue();
				if (value == null || value.isBlank()) {
					continue;
				}
				String tag = entry.getKey().replace("{{", "").replace("}}", "").trim();
				// The lead-phrase markers are slide styling, not copy: the alignment call must not echo them.
				String line = tag + ": "
						+ value.replace(DeckMarkup.LEAD_OPEN, "").replace(DeckMarkup.LEAD_CLOSE, "").trim();
				if (line.length() > BREAKDOWN_DIGEST_LINE_MAX) {
					line = line.substring(0, BREAKDOWN_DIGEST_LINE_MAX);
				}
				digest.add(line);
				if (digest.size() >= BREAKDOWN_DIGEST_MAX_LINES) {
					return digest;
				}
			}
		}
		return digest;
	}

	/**
	 * Fills {@code {{funnel_stages}}} from the campaign's per-tactic goals when nothing else resolved it.
	 *
	 * <p>Replaces the old whole-workbook scan: that call flattened every tab of the media plan into one
	 * prompt — megabytes on a large client plan, enough on its own to overrun the model's context window —
	 * to produce a single ≤60-character line. The per-tactic goals already carry exactly the signal the
	 * stages are inferred from, they are a dozen short strings, and in the sheet flow the user has seen and
	 * can correct them before this runs.
	 *
	 * <p>A manual or media-plan value always wins: the map is only touched when the token is missing, blank
	 * or a dash, so a reviewed value is never overwritten and costs no request at all.
	 *
	 * @param claude           the run's report-type-specific Claude client
	 * @param flatReplacements the placeholder map to fill, mutated in place
	 * @param tacticCount      number of real tactics whose {@code {{tactic n goal}}} values are read
	 * @param live             whether a live Claude client is configured; no call is made when it is not
	 */
	public void fillFunnelStages(
			ClaudeClient claude, Map<String, String> flatReplacements, int tacticCount, boolean live) {
		if (!live) {
			return;
		}
		String current = flatReplacements.get(FUNNEL_STAGES_TOKEN);
		if (current != null && !current.isBlank() && !DASH.equals(current.trim())) {
			return;
		}
		List<String> goals = new ArrayList<>();
		for (int n = 1; n <= tacticCount; n++) {
			String goal = flatReplacements.get("{{tactic " + n + " goal}}");
			if (goal != null && !goal.isBlank() && !DASH.equals(goal.trim())) {
				goals.add(goal.trim());
			}
		}
		if (goals.isEmpty()) {
			return;
		}
		String stages = claude.summarizeFunnelStages(goals);
		if (stages != null && !stages.isBlank()) {
			flatReplacements.put(FUNNEL_STAGES_TOKEN, stages.trim());
		}
	}

	/**
	 * Merges the required campaign brief with the optional change log into a single context string for the
	 * Claude batches, appending the change log under its own {@code === MID-FLIGHT CHANGES / CHANGE LOG ===}
	 * header so Claude reads it as a distinct, clearly-labelled section. Returns the brief unchanged when the
	 * change log is null or blank.
	 *
	 * @param brief     the required free-text campaign brief
	 * @param changeLog the optional change-log text (from the payload for the direct flow, or read back from the
	 *                  reviewed sheet for the slides-from-sheet flow); {@code null}/blank leaves the brief untouched
	 * @return the brief, optionally followed by a labelled change-log section
	 */
	public String combineBriefWithChangeLog(String brief, String changeLog) {
		String safeBrief = brief == null ? "" : brief;
		if (changeLog == null || changeLog.isBlank()) {
			return safeBrief;
		}
		return safeBrief + "\n\n=== MID-FLIGHT CHANGES / CHANGE LOG ===\n" + changeLog.trim();
	}

	/**
	 * Copies sheet-read values onto the deck's alternately-spelled placeholder tokens so a token the sheet
	 * reader never emits under that exact spelling still renders the reviewed value instead of a blank:
	 * {@code {{total spend}}} mirrors {@code {{total_investment}}} and the correctly-spelled
	 * {@code {{tactic n completions}}} mirrors the sheet's {@code {{tactic n complitions}}}.
	 *
	 * <p>The sheet reader loads {@code {{reach_f}}} with the summary Frequency total purely so
	 * {@link SheetCampaignReader#readFrequencies} can read the reviewed frequency; here — after the
	 * frequency has been read — that token is reclaimed for its deck meaning ("reach fact", the
	 * "Market Captured" figure). The sheet carries a single Reach column, so the actual reach is that
	 * same reach, and the presentation-short reach tokens ({@code {{reach_p}}}, {@code {{reach_f_pres}}})
	 * render it abbreviated (e.g. {@code "70k"}) — matching the compaction the BigQuery flow applies —
	 * rather than as the raw grouped figure.
	 *
	 * @param flat        the assembled placeholder map (sheet values already overlaid)
	 * @param tacticCount the active tactic count driving the per-tactic aliases
	 * @param reportType  report template code, deciding whether the EOM-only dashboard tokens are derived
	 */
	public void aliasSheetTokens(Map<String, String> flat, int tacticCount, String reportType) {
		copyToken(flat, "{{total_investment}}", "{{total spend}}");
		copyToken(flat, "{{reach}}", "{{reach_f}}");
		compactToken(flat, "{{reach}}", "{{reach_p}}");
		compactToken(flat, "{{reach}}", "{{reach_f_pres}}");
		for (int n = 1; n <= tacticCount; n++) {
			copyToken(flat, "{{tactic " + n + " complitions}}", "{{tactic " + n + " completions}}");
		}
		fillEomDashboards(flat, tacticCount, reportType);
	}

	/**
	 * Derives the EOM dashboard figures once the reviewed sheet values are in the map, so slides 3–10 print
	 * the budgets, impressions and KPIs the user signed off on rather than a recomputed second opinion.
	 *
	 * <p>End-of-month only: the EOC template carries none of these slots, and every token added to the map
	 * costs one more find-replace request on a deck write that has timed out before when the batch grew too
	 * large.
	 *
	 * @param flat        the assembled placeholder map, mutated in place
	 * @param tacticCount the active tactic count
	 * @param reportType  report template code ({@code "EOC"}/{@code "EOM"}), may be {@code null}
	 */
	public void fillEomDashboards(Map<String, String> flat, int tacticCount, String reportType) {
		if (EOM_REPORT_TYPE.equals(reportType)) {
			eomDashboards.fill(flat, tacticCount);
		}
	}

	/**
	 * Writes the compact abbreviation (e.g. {@code "70k"}, {@code "1.2M"}) of a source token's numeric
	 * value onto a destination token, leaving the destination untouched when the source is absent or blank.
	 *
	 * @param flat the placeholder map to update
	 * @param from the source token key whose value is parsed and abbreviated
	 * @param to   the destination token key receiving the compact string
	 */
	public void compactToken(Map<String, String> flat, String from, String to) {
		String value = flat.get(from);
		if (value == null || value.isBlank()) {
			return;
		}
		flat.put(to, fmt.compact(reportNumbers.parseReportNumber(value)));
	}

	/**
	 * Copies a non-blank source token value onto a destination token, leaving the destination untouched
	 * when the source is absent or blank.
	 *
	 * @param flat the placeholder map to update
	 * @param from the source token key
	 * @param to   the destination token key
	 */
	public void copyToken(Map<String, String> flat, String from, String to) {
		String value = flat.get(from);
		if (value != null && !value.isBlank()) {
			flat.put(to, value);
		}
	}

	/**
	 * Renders the sheet-reconstructed per-tactic name/spend/impressions as a compact one-line string for the
	 * diagnostic log, so a run can be checked against the edited sheet to confirm the reviewed numbers were
	 * read back (and reach Claude) rather than silently lost.
	 *
	 * @param data the campaign data reconstructed from the sheet
	 * @return a {@code "n=name spend=… imps=…"} summary, joined by {@code "; "}
	 */
	public String describeTactics(CampaignData data) {
		if (data == null || data.tactics() == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (var e : data.tactics().entrySet()) {
			var t = e.getValue();
			if (sb.length() > 0) {
				sb.append("; ");
			}
			sb.append(e.getKey()).append('=').append(t.name())
					.append(" spend=").append(t.spend())
					.append(" imps=").append(t.imps());
		}
		return sb.toString();
	}
}
