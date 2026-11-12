package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.diagnostics.ClaudeFailureLog;
import com.aidigital.reportconstructor.service.reports.diagnostics.ClaudeFailureScope;
import com.aidigital.reportconstructor.service.reports.dto.AudienceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownBullets;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSectionBundle;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSectionInputs;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownValues;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeNarrative;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTakeawayInput;
import com.aidigital.reportconstructor.service.reports.dto.DeviceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.GeoInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.PublisherObservationInput;
import com.aidigital.reportconstructor.service.reports.dto.SheetChartData;
import com.aidigital.reportconstructor.service.reports.dto.TacticConclusion;
import com.aidigital.reportconstructor.service.reports.dto.TacticNarrativeDigest;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughts;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughtsInput;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.aidigital.reportconstructor.service.reports.enums.NarrativeCopyLimitsResolver;
import com.aidigital.reportconstructor.service.reports.model.PaidChannelData;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClientFlavors;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import com.aidigital.reportconstructor.service.reports.services.PlaceholderResolverService;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageScope;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Step 2 of the sheet-as-source flow: builds the deck from a previously generated, user-reviewed workbook — reads it
 * back, writes only the narrative the sheet never carried, and fills the deck from the sheet's own numbers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlidesFromSheetWorkflow {

	private static final String CLIENT_NAME_TOKEN = "{{client_name}}";

	private static final String CHANGE_LOG_TOKEN = "{{change log}}";

	/** Sheet field carrying the campaign context: step 1 writes Claude's brief digest here. */
	private static final String RFP_INFO_TOKEN = "{{RFP info}}";

	/** Report type whose deck ships without the frequency slide (and therefore without its narrative). */
	private static final String EOM_REPORT_TYPE = "EOM";

	/** Max tactics the report template carries; the derived tactic count is clamped to this. */
	private static final int MAX_TACTICS = 28;

	private final ReportJobProgressHelper jobProgress;
	private final ReportGenerationWarningsHelper warnings;
	private final ReportGenerationChartHelper chartHelper;
	private final ReportSheetHelper sheetHelper;
	private final SheetPlaceholderReader placeholderReader;
	private final SheetCampaignReader sheetCampaign;
	private final PlaceholderResolverService placeholders;
	private final ClaudeClientFlavors claudeClients;
	private final SlidesProvider slides;
	private final ObjectProvider<UserGoogleTokenProvider> userGoogleTokens;
	private final ReportClaudeDefaults claudeDefaults;
	private final ReportFileNamer fileNamer;
	private final ClaudeUsageTracker usageTracker;
	private final ClaudeFailureLog failureLog;
	private final TacticConclusionAssembler conclusionAssembler;
	private final ImpressionContributionHelper contributions;
	private final SheetTacticCountHelper tacticCounter;
	private final DeckStyleTokenHelper deckStyleTokens;
	private final NarrativeCopyLimitsResolver copyLimits;
	private final ReportTokenAssembler tokens;
	private final ClaudeCallDispatcher dispatcher;
	private final MpoDeckTokenWriter deckSections;
	private final BreakdownSectionWorkflow breakdowns;
	private final ReportRunRecorder recorder;

	/**
	 * Builds a slide deck from a previously generated, user-edited Google Sheet. Reads the sheet grid
	 * into the placeholder map, generates only the narrative copy the sheet never carried (Batch A
	 * strategic + Batch C executive), overlays the sheet's own values on top so every reviewed field
	 * wins, then fills the deck and renders the pacing/distribution charts from the sheet's numbers.
	 *
	 * <p>No source grid is re-collected and the sheet's own numbers, audience copy and per-tactic
	 * daypart (Batch B) are only read — never recomputed — so no Claude work is duplicated: Batch A/C
	 * are requested here for the first time because step 1 never produced them.
	 *
	 * @param jobId       id of the queued job to build and update
	 * @param payload     generation request carrying the source {@code sheetUrl} and report type
	 * @param clerkUserId Clerk identity used to fetch the Google access token for sheet/deck access
	 * @param userEmail   email of the triggering user, used to name the generated deck
	 */
	public void runSlidesFromSheet(Long jobId, GeneratePayload payload, String clerkUserId, String userEmail) {
		jobProgress.markJobRunningAtStep(jobId, 1, "Reading sheet data");
		UserGoogleTokenProvider clerk = userGoogleTokens.getIfAvailable();
		String userGoogleToken = clerk == null ? null : clerk.googleAccessToken(clerkUserId);

		List<List<String>> grid = sheetHelper.readSheetGrid(payload.sheetUrl(), userGoogleToken);
		Map<String, String> sheetValues = placeholderReader.readPlaceholders(grid);
		int tacticCount = deriveTacticCount(sheetValues);

		jobProgress.markJobRunningAtStep(jobId, 3, "Claude — narrative");
		// Both halves of the campaign context — the brief digest in {{RFP info}} and the change-log digest in
		// {{change log}} — are read back from the reviewed sheet, so any edit the user made there wins and the
		// two steps run on exactly the same text. The payload's raw values are the fallback when the sheet
		// carries neither (older sheet, or Claude stubbed when step 1 ran).
		String sheetBrief = sheetValues.get(RFP_INFO_TOKEN);
		String briefContext = sheetBrief == null || sheetBrief.isBlank() ? payload.brief() : sheetBrief;
		String sheetChangeLog = sheetValues.get(CHANGE_LOG_TOKEN);
		String changeLogContext = sheetChangeLog == null || sheetChangeLog.isBlank()
				? payload.changeLog() : sheetChangeLog;
		String briefSource = tokens.combineBriefWithChangeLog(briefContext, changeLogContext);
		// The sheet has no date window of its own: the request's filter is what dated step 1, so it is what
		// dates the cover here too — without it every EOM pacing and cover token rebuilds as a dash.
		CampaignData data = sheetCampaign.read(sheetValues, tacticCount, payload.reportType(),
				payload.dateFilter() == null ? null : payload.dateFilter().toWindow());
		// Frequencies are reconstructed from the reviewed sheet — never the raw media plan — and without a
		// fresh random reach uplift, so the Claude frequency narrative and the deck's frequency figures both
		// match exactly what the user sees in the sheet.
		CampaignFrequencies frequencies = sheetCampaign.readFrequencies(sheetValues);
		if (log.isInfoEnabled()) {
			log.info("[report] job {} slides-from-sheet context tacticCount={} tactics={}",
					jobId, tacticCount, tokens.describeTactics(data));
		}
		// EOC and EOM send different prompt text for the same calls; the report type picks which, and the deck
		// design picks how long the campaign-level copy may run.
		ClaudeClient claude = claudeClients.forReport(payload.reportType(), copyLimits.forDeck(
				slides.carriesMpoNarrative(payload.reportType(), payload.templateVariant())));
		boolean live = claude.isLive();
		// Every call below repeats this text as context, so it is bounded to the digest budget before any of them
		// sees it. The sheet's {{RFP info}} normally already holds step 1's digest, but it is a user-editable cell,
		// the change log above is appended raw, and an older sheet falls back to the payload's raw brief — this is
		// where those three paths are prevented from pushing the full brief into every prompt of the run.
		String brief = live ? claude.digestBriefIfOversized(briefSource) : briefSource;
		// Batch A: strategic narrative only (proposal + insights). Audience already lives in the sheet from the
		// sheet-build step, so this flow never regenerates it — no duplicate Claude work across the two steps.
		// The pacing blocks the user reviewed, read straight off the same grid the charts are built from.
		// An EOM deck's strategic insights are asked for as month-over-month movements and its per-tactic
		// overviews argue the month's pacing off the daily curve — neither of which the month's own totals
		// can express; the EOC wording ignores both series and is unaffected.
		SheetChartData pacing = live
				? breakdowns.readPacing(grid, data, tacticCount) : new SheetChartData(Map.of(), Map.of());
		ClaudeStrategic ccA = live
				? claude.batchStrategicNarrative(data, brief, pacing.monthlyPivots())
				: claudeDefaults.emptyStrategic();

		// Seed map for the breakdown data reads (tactic names, KPI types, gender split). The results copy is
		// intentionally empty here: the per-tactic overviews come from Step 2 and the campaign results from
		// Step 4; only the deck-filling map rebuilt at the end carries them.
		Map<String, String> prelim = buildSheetFlatReplacements(
				payload, data, ccA, claudeDefaults.emptyResults(), frequencies, tacticCount, sheetValues);

		// Step 2 (data) — read every breakdown section's tables and per-tactic Claude inputs. These are pure
		// Google-Sheet reads with no Claude call, so they run concurrently on the shared virtual-thread
		// executor; each helper catches its own failures, so one section failing cannot abort the others.
		ClaudeUsageScope usageScope = usageTracker.current();
		BreakdownSectionBundle sections = breakdowns.readBreakdownSections(payload, prelim, usageScope,
				userGoogleToken);
		BreakdownSectionInputs<PublisherObservationInput> pub = sections.publisher();
		BreakdownSectionInputs<CreativeTakeawayInput> cre = sections.creative();
		BreakdownSectionInputs<GeoInsightInput> geo = sections.geo();
		BreakdownSectionInputs<AudienceInsightInput> aud = sections.audience();
		BreakdownSectionInputs<DeviceInsightInput> dev = sections.device();

		// Step 2 (Claude) — the per-tactic conclusions call, which writes each tactic's overview and nothing
		// else. Every tactic on the reviewed sheet is included so its overview (and the whole downstream results
		// narrative) is always written from the EOC sheet figures + brief, whether or not it ran a breakdown.
		List<Integer> conclusionTactics = breakdowns.conclusionTacticNums(data, pub, cre, geo, aud, dev);
		List<TacticConclusion> conclusions = live && !conclusionTactics.isEmpty()
				? claude.batchTacticConclusions(data, conclusionTactics, brief, pacing.dailyPivots()) : List.of();
		BreakdownBullets bullets = sectionBullets(claude, live, data, sections, usageScope, brief);

		// Breakdown token map: the section data tokens first, then the Claude section tokens written from the
		// per-section calls. Each section only emits tokens for its own tactics, so the merge cannot collide.
		// The warnings collect every tactic whose section shipped without its Claude copy.
		Map<String, String> breakdownValues = breakdowns.mergeSectionDataValues(sections);
		List<String> jobWarnings = new ArrayList<>(
				breakdowns.writeBreakdownInsights(breakdownValues, pub, cre, geo, aud, dev, bullets, prelim));

		// Step 3 — per-tactic "thoughts on tactic performance" for the tactics with more than two breakdowns
		// (the same gate the thoughts slide uses). One call per tactic, all dispatched at once so they run in
		// parallel like Step 2's section calls. Written into the breakdown token map so the slide picks them up
		// when it is duplicated.
		Set<Integer> qualifying = breakdowns.thoughtsTactics(payload, tacticCount);
		Map<Integer, String> namesByTactic = tokens.tacticNames(prelim, tacticCount);
		List<TacticThoughts> thoughts = List.of();
		if (live && !qualifying.isEmpty()) {
			List<TacticThoughtsInput> thoughtsInputs = conclusionAssembler.toThoughtsInputs(conclusions, namesByTactic,
					qualifying, bullets, slides.carriesMpoNarrative(payload.reportType(), payload.templateVariant()));
			thoughts = dispatcher.joinThoughts(dispatcher.dispatchThoughts(claude, thoughtsInputs, usageScope, brief));
		}
		tokens.writeThoughtsTokens(breakdownValues, qualifying, thoughts, prelim);

		// Step 4 — campaign-level results (results overviews, performance thoughts, recommendations, frequency)
		// from the per-tactic digests; the Step-2 overviews are merged back in for the tactic-overview slides.
		List<TacticNarrativeDigest> digests =
				conclusionAssembler.toCampaignDigests(conclusions, namesByTactic, thoughts, bullets);
		ClaudeResults ccC = buildCampaignResults(
				jobId, payload, data, claude, live, brief, frequencies, digests, conclusions, jobWarnings);

		// Step 5 — final campaign narrative alignment: reconcile Batch A and the campaign results into one
		// storyline faithful to the brief, informed by a read-only digest of the breakdown conclusions. Purely
		// additive: on any failure the originals are returned, so the deck is never worse than before this ran.
		ClaudeNarrative aligned = alignCampaignNarrative(claude, live, ccA, ccC, breakdownValues, brief, data);
		if (aligned != null) {
			ccA = aligned.strategic();
			ccC = aligned.results();
		}

		// Rebuild the narrative map from the aligned copy. The sheet overlay still wins for every numeric anchor,
		// so a missing sheet value renders blank (visible) rather than a stale raw value (silent).
		Map<String, String> flatReplacements = buildSheetFlatReplacements(
				payload, data, ccA, ccC, frequencies, tacticCount, sheetValues);
		// Funnel stages are inferred here, from the reviewed per-tactic goals, rather than from a scan of the
		// source workbook. A value the user already put on the sheet wins and costs no call at all.
		tokens.fillFunnelStages(claude, flatReplacements, tacticCount, live);
		// The tactic slide's contribution legend, derived last so it reads the same impressions the deck
		// prints — which on this flow are the ones the user reviewed on the sheet, not the parsed originals.
		contributions.fillContributions(flatReplacements, tacticCount);
		deckStyleTokens.fillDeckStyleTokens(flatReplacements, tacticCount);
		// The EOM channel slides' key takeaways, written last of all: they reason over the METRIC table the
		// map now carries, so the copy cannot contradict the figures it sits on.
		deckSections.fillPacingNarrative(claude, flatReplacements, tacticCount, payload.reportType(),
				payload.templateVariant(),
				live, usageScope, brief);
		// The MPO-only paid figures and copy, last for the same reason: the copy quotes the reviewed figures.
		List<PaidChannelData> paid = deckSections.fillMpoDeckTokens(claude, flatReplacements, tacticCount, payload,
				live, brief);

		buildDeckFromSheet(
				jobId, payload, userEmail, grid, flatReplacements, breakdownValues, tacticCount, paid,
				userGoogleToken, jobWarnings);
	}

	/**
	 * Produces the breakdown slide copy: every section by its own dedicated per-tactic call. All of them are
	 * dispatched up front (across all sections and tactics) so they run maximally in parallel under the shared
	 * Claude concurrency limit, then joined.
	 *
	 * @param claude     the run's report-type-specific Claude client
	 * @param live       false when running against stubbed copy, in which case every section comes back empty
	 * @param data       the campaign figures each call reasons over
	 * @param sections   the breakdown sections read from the workbook
	 * @param usageScope the usage scope every call is billed to
	 * @param brief      free-text campaign brief passed through to every call
	 * @return the per-section, per-tactic replies; empty maps when not live
	 */
	BreakdownBullets sectionBullets(
			ClaudeClient claude, boolean live, CampaignData data, BreakdownSectionBundle sections,
			ClaudeUsageScope usageScope, String brief) {
		if (!live) {
			return new BreakdownBullets(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
		}
		var pubC = dispatcher.dispatchSection(sections.publisher().inputs(), usageScope,
				in -> claude.publisherSection(data, in, brief));
		var creC = dispatcher.dispatchSection(sections.creative().inputs(), usageScope,
				in -> claude.creativeSection(data, in, brief));
		var geoC = dispatcher.dispatchSection(sections.geo().inputs(), usageScope,
				in -> claude.geoSection(data, in, brief));
		var audC = dispatcher.dispatchSection(sections.audience().inputs(), usageScope,
				in -> claude.audienceSection(data, in, brief));
		var devC = dispatcher.dispatchSection(sections.device().inputs(), usageScope,
				in -> claude.deviceSection(data, in, brief));
		return new BreakdownBullets(dispatcher.joinSection(pubC), dispatcher.joinSection(creC),
				dispatcher.joinSection(geoC), dispatcher.joinSection(audC), dispatcher.joinSection(devC));
	}

	/**
	 * Reconciles Batch A and the campaign results into one storyline faithful to the brief.
	 *
	 * <p>Purely additive: {@code null} is returned on any failure, and the caller keeps the originals,
	 * so the deck is never worse than it was before this ran.
	 *
	 * @param claude          the Claude client
	 * @param live            false when running against stubbed copy, in which case nothing is aligned
	 * @param strategic       the strategic copy from Batch A
	 * @param results         the campaign results
	 * @param breakdownValues the breakdown tokens, read only, as context for the alignment
	 * @param brief           free-text campaign brief the copy must stay faithful to
	 * @param data            the collected campaign figures, for the flight dates
	 * @return the aligned narrative, or {@code null} when nothing was aligned
	 */
	public ClaudeNarrative alignCampaignNarrative(
			ClaudeClient claude, boolean live, ClaudeStrategic strategic, ClaudeResults results,
			Map<String, String> breakdownValues, String brief, CampaignData data) {
		if (!live) {
			return null;
		}
		List<String> breakdownDigest =
				tokens.buildBreakdownDigest(List.of(new BreakdownValues(breakdownValues, List.of())));
		return claude.batchAlignCampaign(strategic, results, breakdownDigest, brief, data.flightDates());
	}

	/**
	 * Produces the campaign-level results copy from the per-tactic digests.
	 *
	 * <p>An empty result despite real digests means Batch C degraded to the empty DTO — a timeout, a
	 * parse failure, a non-200 — and the results overview, performance thoughts and recommendations
	 * would all render as dashes. That is surfaced as a job warning rather than left to look like an
	 * intentionally empty report.
	 *
	 * @param jobId       the running job, for the log line
	 * @param payload     the generation request
	 * @param data        the collected campaign figures
	 * @param claude      the Claude client
	 * @param live        false when running against stubbed copy
	 * @param brief       free-text campaign brief the copy must stay faithful to
	 * @param frequencies the campaign frequency figures
	 * @param digests     the per-tactic narrative digests Batch C reasons over
	 * @param conclusions the per-tactic conclusions whose overviews are merged back in
	 * @param jobWarnings the accumulating job warnings, appended to in place
	 * @return the campaign results with the per-tactic overviews merged in
	 */
	public ClaudeResults buildCampaignResults(
			Long jobId, GeneratePayload payload, CampaignData data, ClaudeClient claude, boolean live,
			String brief, CampaignFrequencies frequencies, List<TacticNarrativeDigest> digests,
			List<TacticConclusion> conclusions, List<String> jobWarnings) {
		// EOM decks drop the frequency slide entirely, so the frequency narrative ({{f_oppartunity}} /
		// {{f_fact}} / {{f_storytelling}}) has nowhere to land: passing no frequencies keeps those three
		// fields out of the prompt and out of the reply, instead of paying for copy that is deleted.
		CampaignFrequencies resultsFrequencies =
				EOM_REPORT_TYPE.equals(payload.reportType()) ? null : frequencies;
		ClaudeResults campaign = live
				? claude.batchCampaignResults(data, brief, resultsFrequencies, digests)
				: claudeDefaults.emptyResults();
		if (live && !digests.isEmpty() && tokens.isCampaignResultsEmpty(campaign)) {
			log.warn("[report] job {} campaign results came back empty for {} tactic digest(s); "
					+ "results overview / performance thoughts / recommendations will be blank", jobId, digests.size());
			jobWarnings.add("Campaign-level results (results overview, performance thoughts, recommendations) came "
					+ "back empty from Claude despite " + digests.size() + " tactic conclusion(s); those sections "
					+ "will show dashes. Usually a Claude timeout or parse failure — re-running the report fixes it.");
		}
		return tokens.mergeTacticOverviews(campaign, conclusions);
	}

	/**
	 * Builds and finishes the deck for the Slides-from-Sheet flow.
	 *
	 * <p>The order here is load-bearing: tactic slides are duplicated from the master before the
	 * breakdowns, which anchor their own copies after each tactic's main slide, and before the trim,
	 * which would otherwise have tactic slides left to delete. The masters are removed only once every
	 * copy has finished duplicating from them.
	 *
	 * @param jobId            the running job
	 * @param payload          the generation request
	 * @param userEmail        the caller, used to name the file
	 * @param grid             the reviewed workbook's first tab, read for the charts
	 * @param flatReplacements the resolved token map
	 * @param breakdownValues  the breakdown slides' own tokens
	 * @param tacticCount      how many tactic slots the report covers
	 * @param paidChannels     the parsed paid-media channels whose tokens were written (empty for a standard deck)
	 * @param userGoogleToken  the caller's Google OAuth token, or null to use the service account
	 * @param jobWarnings      the accumulating job warnings, appended to in place
	 */
	public void buildDeckFromSheet(
			Long jobId, GeneratePayload payload, String userEmail, List<List<String>> grid,
			Map<String, String> flatReplacements, Map<String, String> breakdownValues, int tacticCount,
			List<PaidChannelData> paidChannels, String userGoogleToken, List<String> jobWarnings) {
		jobProgress.markJobRunningAtStep(jobId, 6, "Building slide deck");
		String fileName = fileNamer.buildFileName(
				payload.reportType(), flatReplacements.get(CLIENT_NAME_TOKEN), userEmail);
		String slideUrl = slides.createDeck(
				String.valueOf(jobId), fileName, flatReplacements, payload.reportType(),
				payload.templateVariant(), userGoogleToken);
		// Main tactic slides: on a master-model template the deck arrives with one generic tactic slide, which
		// is duplicated into exactly `tacticCount` filled slides here. Runs before the breakdowns, which anchor
		// their copies after each tactic's main slide, and before the trim, which then has no tactic slides
		// left to delete. A no-op on the legacy 28-slot template.
		jobWarnings.addAll(chartHelper.addTacticSlides(
				slideUrl, tacticCount, flatReplacements, payload.reportType(), payload.templateVariant(),
				userGoogleToken));
		chartHelper.trimUnusedTactics(
				slideUrl, tacticCount, payload.reportType(), payload.templateVariant(), userGoogleToken);
		// Per-tactic breakdown + thoughts slides: duplicate the selected masters, fill their tokens (already in
		// breakdownValues, including the {{thoughts on tactic n performance}} tokens for the > 2-breakdown
		// tactics), and place them after the tactic's main slide. Non-fatal — the deck still ships on failure.
		chartHelper.addBreakdownSlides(slideUrl, payload, tacticCount,
				breakdowns.withDeckFallback(breakdownValues, flatReplacements), userGoogleToken);
		// Give each audience/device breakdown slide its own live chart: the master's embedded chart is
		// duplicated pointing at a shared, empty source workbook, so this relinks every copy to a per-tactic
		// copy of that workbook filled with the tactic's impressions. Runs after the slides exist; non-fatal.
		List<String> breakdownChartWarnings = chartHelper.buildBreakdownCharts(
				slideUrl, payload, tacticCount, flatReplacements, userGoogleToken);
		// Remove the breakdown/thoughts master template slides. Unconditional and independent of whether any
		// breakdown slides were inserted — the masters must never ship, even when Step 3 selected no
		// breakdowns. Runs after the charts so every copy has finished duplicating from the masters.
		chartHelper.deleteMasterSlides(slideUrl, payload.reportType(), payload.templateVariant(), userGoogleToken);
		// Slides an EOM deck must never ship (the frequency & velocity play, the awareness / market-share
		// slide): the EOM template is a copy of the EOC one, so they arrive with it and are deleted here.
		chartHelper.deleteReportTypeSlides(slideUrl, payload.reportType(), userGoogleToken);
		// The MPO deck's paid tables, written by row and column, and the slides of a channel without an export.
		deckSections.finishPaidSlides(slideUrl, paidChannels, payload, userGoogleToken);

		jobProgress.markJobRunningAtStep(jobId, 7, "Building charts");
		List<String> chartWarnings = chartHelper.buildChartsFromSheet(
				slideUrl, grid, flatReplacements, tacticCount, userGoogleToken);

		jobWarnings.addAll(breakdownChartWarnings);
		jobWarnings.addAll(chartWarnings);
		jobWarnings.addAll(deckSections.buildPaidCharts(slideUrl, paidChannels, payload, flatReplacements,
				userGoogleToken));
		// Last pass over the finished deck: any token still standing had no value anywhere in the pipeline,
		// and shipping its own name is worse than shipping a dash.
		jobWarnings.addAll(chartHelper.dashUnresolvedTokens(
				slideUrl, payload.reportType(), payload.templateVariant(), userGoogleToken));

		// Every reply Claude sent that the pipeline could not use, in the order it happened: the parse failure
		// or the wrong item count that left a slide blank, verbatim enough to act on without the server log.
		ClaudeFailureScope failures = failureLog.current();
		if (failures != null) {
			jobWarnings.addAll(failures.snapshot());
		}

		jobProgress.recordArtifact(jobId, fileName, payload.sheetUrl());
		recorder.recordSlideCount(jobId, slideUrl, userGoogleToken);
		jobProgress.markJobDone(jobId, slideUrl, warnings.serializeWarnings(jobWarnings));
	}

	/**
	 * Derives the active tactic count from a sheet-read placeholder map: the number of leading
	 * {@code {{tactic n}}} name tokens that carry a non-blank value, clamped to 1..28. Counting stops
	 * at the first missing or blank slot so trailing gaps never inflate the count.
	 *
	 * @param flatReplacements the placeholder map read back from the sheet
	 * @return the active tactic count (1..28)
	 */
	public int deriveTacticCount(Map<String, String> flatReplacements) {
		// A deck is always built for at least one tactic: this flow only runs on a workbook that
		// already passed review, so an unnamed first tactic means a bad read, not an empty report.
		return Math.clamp(tacticCounter.countFromPlaceholders(flatReplacements), 1, MAX_TACTICS);
	}

	/**
	 * Assembles the slides-from-sheet placeholder map: the Claude-authored narrative first (resolved from a
	 * grid-stripped payload so numbers cannot come from the raw plan), then the reviewed-sheet overlay — which
	 * wins for every numeric anchor — then the sheet-token aliases. Extracted because the flow builds this map
	 * twice: once to seed the breakdown batches, then again from the Batch D-aligned narrative.
	 *
	 * @param payload     the inbound generation payload
	 * @param data        the campaign data read back from the reviewed sheet
	 * @param ccA         the (possibly aligned) Batch A strategic narrative
	 * @param ccC         the (possibly aligned) Batch C results copy
	 * @param frequencies the frequencies reconstructed from the reviewed sheet
	 * @param tacticCount the active tactic count
	 * @param sheetValues the reviewed-sheet placeholder values overlaid on top of the narrative
	 * @return the merged placeholder map, sheet values winning over narrative, with aliases applied
	 */
	public Map<String, String> buildSheetFlatReplacements(
			GeneratePayload payload, CampaignData data, ClaudeStrategic ccA, ClaudeResults ccC,
			CampaignFrequencies frequencies, int tacticCount, Map<String, String> sheetValues) {
		GeneratePayload narrativePayload = tokens.narrativeOnly(payload);
		Map<String, String> narrative = placeholders.buildFlatReplacements(
				narrativePayload, data, ccA, claudeDefaults.emptyTactical(), ccC, null, null, null, null, null,
				frequencies, tacticCount);
		Map<String, String> flat = new LinkedHashMap<>(narrative);
		flat.putAll(sheetValues);
		tokens.aliasSheetTokens(flat, tacticCount, payload.reportType());
		return flat;
	}
}
