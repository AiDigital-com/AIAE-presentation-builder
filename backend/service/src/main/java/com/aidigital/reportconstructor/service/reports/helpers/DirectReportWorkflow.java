package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeSheetBatch;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.GenerationTarget;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.aidigital.reportconstructor.service.reports.enums.NarrativeCopyLimitsResolver;
import com.aidigital.reportconstructor.service.reports.model.PaidChannelData;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClientFlavors;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import com.aidigital.reportconstructor.service.reports.services.PlaceholderResolverService;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageScope;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds a report straight from the request's source grids: collects the campaign data, runs the Claude batches the
 * target needs, resolves the placeholder map, then builds the workbook (SHEET) or the deck (SLIDES) and finishes the
 * job on it.
 */
@Component
@RequiredArgsConstructor
public class DirectReportWorkflow {

	private static final String CLIENT_NAME_TOKEN = "{{client_name}}";

	private final ReportJobProgressHelper jobProgress;
	private final ReportGenerationWarningsHelper warnings;
	private final ReportGenerationChartHelper chartHelper;
	private final ReportSheetHelper sheetHelper;
	private final PlaceholderResolverService placeholders;
	private final ClaudeClientFlavors claudeClients;
	private final SlidesProvider slides;
	private final ObjectProvider<UserGoogleTokenProvider> userGoogleTokens;
	private final ReportClaudeDefaults claudeDefaults;
	private final ReportFileNamer fileNamer;
	private final ImpressionContributionHelper contributions;
	private final ReportResumeStateHelper resumeState;
	private final DeckStyleTokenHelper deckStyleTokens;
	private final NarrativeCopyLimitsResolver copyLimits;
	private final ReportTokenAssembler tokens;
	private final MpoDeckTokenWriter deckSections;
	private final ReportRunRecorder recorder;

	/**
	 * Builds a report straight from the request's source grids: collects the campaign data, runs the Claude
	 * batches the target needs, resolves the placeholder map, then builds either the workbook (SHEET) or the
	 * deck (SLIDES) and finishes the job on it.
	 *
	 * @param jobId       id of the queued job to build and update
	 * @param payload     generation request carrying the source grids
	 * @param clerkUserId Clerk identity used to fetch the Google access token
	 * @param userEmail   email of the triggering user, used to name the artifact
	 * @param target      SHEET or SLIDES
	 * @param usageScope  the run's Claude usage scope, which per-tactic calls are billed to
	 */
	public void runDirect(Long jobId, GeneratePayload payload, String clerkUserId, String userEmail,
			GenerationTarget target, ClaudeUsageScope usageScope) {
		jobProgress.markJobRunningAtStep(jobId, 1, "Reading sheet data");
		CampaignData data = placeholders.collectData(payload);

		jobProgress.markJobRunningAtStep(jobId, 2, "Resolving placeholders");

		// EOC and EOM send different prompt text for the same calls; the report type picks which, and the
		// deck design picks how long the campaign-level copy may run.
		ClaudeClient claude = claudeClients.forReport(payload.reportType(), copyLimits.forDeck(
				slides.carriesMpoNarrative(payload.reportType(), payload.templateVariant())));
		boolean live = claude.isLive();
		// The brief is user-pasted and unbounded, and every batch below repeats it as context. Digest it
		// once here and feed the digest everywhere instead: the campaign facts the copy must stay faithful
		// to survive, the token cost of the raw text is paid a single time, and the digest is written into
		// the sheet's {{RFP info}} so the slides step reads it back rather than digesting again.
		String briefDigest = live ? claude.digestBrief(payload.brief()) : null;
		// The change log gets its own digest call rather than riding along inside the brief's: asked to
		// condense a brief, the model treats an appended change-log section as commentary and drops it, so
		// the log's content never reached the sheet or the later batches. Digested separately it lands in
		// {{change log}} in the sheet and in the context below, exactly like the brief.
		String changeLogDigest = live ? claude.digestChangeLog(payload.changeLog()) : null;
		String changeLogText = changeLogDigest == null || changeLogDigest.isBlank()
				? payload.changeLog() : changeLogDigest;
		String briefText = briefDigest == null || briefDigest.isBlank() ? payload.brief() : briefDigest;
		String brief = tokens.combineBriefWithChangeLog(briefText, changeLogText);
		CampaignFrequencies frequencies = placeholders.computeFrequencies(payload, data);

		ClaudeStrategic ccA;
		ClaudeTactical ccB;
		ClaudeResults ccC;

		if (target == GenerationTarget.SHEET) {
			// The sheet template consumes only the Batch A audience fields and the Batch B per-tactic
			// gender/daypart fields — never any Batch C copy — so a single merged call covers both and
			// the (expensive) executive batch is skipped entirely for this target.
			jobProgress.markJobRunningAtStep(jobId, 3, "Claude — sheet batch");
			ClaudeSheetBatch sheetBatch =
					(live && (placeholders.needStrategic(payload) || placeholders.needTactical(payload, data)))
							? claude.batchSheet(data, brief) : claudeDefaults.emptySheetBatch();
			ccA = new ClaudeStrategic(sheetBatch.audienceAge(), sheetBatch.audienceSegments(), null, List.of());
			ccB = new ClaudeTactical(sheetBatch.byTactic());
			ccC = claudeDefaults.emptyResults();
		} else {
			jobProgress.markJobRunningAtStep(jobId, 3, "Claude — campaign batch (A)");
			ccA = (live && placeholders.needStrategic(payload))
					? claude.batchStrategic(data, brief) : claudeDefaults.emptyStrategic();

			jobProgress.markJobRunningAtStep(jobId, 4, "Claude — tactics batch (B)");
			ccB = (live && placeholders.needTactical(payload, data))
					? claude.batchTactical(data, brief) : claudeDefaults.emptyTactical();

			// The campaign-level result copy is produced by the sheet flow's own campaign batch
			// (see runSlidesFromSheet), so this direct-to-deck path ships the empty shape and the
			// resolvers fall back to whatever the workbook already carries.
			ccC = claudeDefaults.emptyResults();
		}

		String geoSummary = (live && placeholders.needGeoSummary(payload))
				? claude.summarizeGeo(payload.geoRows()) : null;

		// Funnel stages are no longer inferred from a scan of the whole workbook — that call shipped every
		// tab of the plan to answer with one short line. They are derived from the per-tactic goals once
		// those are resolved, below, where the goal values already exist in the placeholder map.
		String primaryKpis = (live && placeholders.needPrimaryKpis(payload))
				? claude.summarizePrimaryKpis(data) : null;

		// Both the Sheet and the Slides deck fill only the tactics the campaign actually has, so the
		// placeholder map — and therefore the find-replace batch — is bounded to the real tactic count.
		// A two-tactic campaign no longer fans out ~800 replace requests (28 slots × ~27 tokens); that
		// volume was the cause of the "Read timed out" on both createSheet and createDeck. Unused template
		// slots are cleaned up afterwards: the deck deletes its surplus slides, and the sheet blanks any
		// leftover {{token}} in a single regex pass (see RealSheetDeckProvider#createSheet) plus trims the
		// now-empty rows.
		int flatTacticCount = tokens.maxTacticNumber(data);
		Map<String, String> flatReplacements =
				placeholders.buildFlatReplacements(payload, data, ccA, ccB, ccC, primaryKpis, geoSummary,
						null, briefDigest, changeLogDigest, frequencies, flatTacticCount);
		tokens.fillFunnelStages(claude, flatReplacements, flatTacticCount, live);
		UserGoogleTokenProvider clerk = userGoogleTokens.getIfAvailable();
		String userGoogleToken = clerk == null ? null : clerk.googleAccessToken(clerkUserId);
		String fileName = fileNamer.buildFileName(
				payload.reportType(), flatReplacements.get(CLIENT_NAME_TOKEN), userEmail);

		if (target == GenerationTarget.SHEET) {
			buildSheetArtifact(jobId, fileName, flatReplacements, payload, data, userGoogleToken);
			return;
		}

		// The tactic slide's contribution legend. Deck-only, so it is derived here rather than before the
		// SHEET branch above — the workbook has no such column, and every token added to the sheet's
		// find-replace map costs a request on a write that already timed out once when it grew too large.
		contributions.fillContributions(flatReplacements, flatTacticCount);
		deckStyleTokens.fillDeckStyleTokens(flatReplacements, flatTacticCount);
		// The EOM pacing dashboard, derived here for the same reason and in the same place: the workbook
		// carries no such columns, so these tokens are deck-only.
		tokens.fillEomDashboards(flatReplacements, flatTacticCount, payload.reportType());
		// The EOM channel slides' key takeaways, written last of all: they reason over the METRIC table
		// the two lines above have just finished filling, so the copy cannot contradict the figures it
		// sits on.
		deckSections.fillPacingNarrative(claude, flatReplacements, flatTacticCount, payload.reportType(),
				payload.templateVariant(), live, usageScope, brief);
		// The MPO-only values: the Paid Search / Paid Social figures from the exports, then the copy (HHI,
		// proposal cards, funnel, recommendations headline), written after every other value so its evidence
		// lines quote the figures the deck prints. A no-op for a standard deck.
		List<PaidChannelData> paidChannels =
				deckSections.fillMpoDeckTokens(claude, flatReplacements, flatTacticCount, payload, live, brief);

		jobProgress.markJobRunningAtStep(jobId, 6, "Building slide deck");
		String slideUrl = slides.createDeck(
				String.valueOf(jobId), fileName, flatReplacements, payload.reportType(),
				payload.templateVariant(), userGoogleToken);

		List<String> deckWarnings = assembleDeck(
				jobId, slideUrl, flatTacticCount, flatReplacements, payload, data, paidChannels, userGoogleToken);

		jobProgress.recordArtifact(jobId, fileName, payload.sheetUrl());
		recorder.recordSlideCount(jobId, slideUrl, userGoogleToken);
		jobProgress.markJobDone(jobId, slideUrl, warnings.serializeWarnings(deckWarnings));
	}

	/**
	 * Builds the workbook half of a report and finishes the job on it.
	 *
	 * <p>The workbook then goes to the user, who fills it by hand — often over days, and rarely in the
	 * browser tab that is still open. Everything the slides step will need is stored on the job here, so
	 * that tab is expendable and the report resumes from "My reports" at the review step.
	 *
	 * @param jobId            the running job
	 * @param fileName         the file name the artifact is created under
	 * @param flatReplacements the resolved token map written into the workbook
	 * @param payload          the generation request
	 * @param data             the collected campaign figures
	 * @param userGoogleToken  the caller's Google OAuth token, or null to use the service account
	 */
	public void buildSheetArtifact(
			Long jobId, String fileName, Map<String, String> flatReplacements,
			GeneratePayload payload, CampaignData data, String userGoogleToken) {
		jobProgress.markJobRunningAtStep(jobId, 6, "Building sheet");
		String sheetUrl = sheetHelper.buildSheet(
				String.valueOf(jobId), fileName, flatReplacements, payload.reportType(), userGoogleToken);
		sheetHelper.trimUnusedTactics(sheetUrl, payload, userGoogleToken);
		sheetHelper.clearUnselectedBreakdowns(sheetUrl, payload, userGoogleToken);

		jobProgress.markJobRunningAtStep(jobId, 7, "Building pacing tables");
		List<String> pacingWarnings = sheetHelper.writePacingTables(
				sheetUrl, payload, data, flatReplacements, userGoogleToken);

		jobProgress.recordArtifact(jobId, fileName, sheetUrl);
		jobProgress.recordResumeState(jobId, resumeState.serialize(resumeState.toState(payload, data)));
		jobProgress.markJobDone(jobId, sheetUrl, warnings.serializeWarnings(pacingWarnings));
	}

	/**
	 * Fills an already-created deck: duplicates the tactic master, removes the slides that must never
	 * ship, builds the charts, and dashes any token still standing.
	 *
	 * @param jobId            the running job, for progress reporting
	 * @param slideUrl         the deck created from the template
	 * @param flatTacticCount  how many tactic slots the campaign actually uses
	 * @param flatReplacements the resolved token map
	 * @param payload          the generation request
	 * @param data             the collected campaign figures
	 * @param paidChannels     the parsed paid-media channels whose tokens were written (empty for a standard deck)
	 * @param userGoogleToken  the caller's Google OAuth token, or null to use the service account
	 * @return every warning raised while assembling the deck, in the order it was raised
	 */
	public List<String> assembleDeck(
			Long jobId, String slideUrl, int flatTacticCount, Map<String, String> flatReplacements,
			GeneratePayload payload, CampaignData data, List<PaidChannelData> paidChannels, String userGoogleToken) {
		// Main tactic slides from the single master, same as the sheet flow; a no-op on the legacy
		// 28-slot template, where the trim below removes the surplus slots instead.
		List<String> tacticSlideWarnings =
				chartHelper.addTacticSlides(
						slideUrl, flatTacticCount, flatReplacements, payload.reportType(), payload.templateVariant(),
						userGoogleToken);
		chartHelper.trimUnusedTactics(slideUrl, payload, userGoogleToken);
		// Template master slides must never ship. This flow inserts no breakdowns, but it does duplicate
		// the tactic master above, which would otherwise arrive full of raw {{tactic n …}} tokens.
		chartHelper.deleteMasterSlides(slideUrl, payload.reportType(), payload.templateVariant(), userGoogleToken);
		// EOC-only story slides the EOM deck inherited from the template it was copied from.
		chartHelper.deleteReportTypeSlides(slideUrl, payload.reportType(), userGoogleToken);
		deckSections.finishPaidSlides(slideUrl, paidChannels, payload, userGoogleToken);

		jobProgress.markJobRunningAtStep(jobId, 7, "Building charts");
		List<String> chartWarnings = chartHelper.buildCharts(
				slideUrl, payload, data, flatReplacements, userGoogleToken);

		List<String> deckWarnings = new ArrayList<>(tacticSlideWarnings);
		deckWarnings.addAll(chartWarnings);
		deckWarnings.addAll(deckSections.buildPaidCharts(slideUrl, paidChannels, payload, flatReplacements,
				userGoogleToken));
		// Last pass over the finished deck: any token still standing had no value anywhere in the
		// pipeline, and shipping its own name is worse than shipping a dash.
		deckWarnings.addAll(chartHelper.dashUnresolvedTokens(
				slideUrl, payload.reportType(), payload.templateVariant(), userGoogleToken));
		return deckWarnings;
	}
}
