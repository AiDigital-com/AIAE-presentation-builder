package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.diagnostics.ClaudeFailureLog;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.GenerationTarget;
import com.aidigital.reportconstructor.service.reports.dto.ProgressView;
import com.aidigital.reportconstructor.service.reports.helpers.DirectReportWorkflow;
import com.aidigital.reportconstructor.service.reports.helpers.ReportGenerationWarningsHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportRunRecorder;
import com.aidigital.reportconstructor.service.reports.helpers.SlidesFromSheetWorkflow;
import com.aidigital.reportconstructor.service.reports.services.ReportGenerationService;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageScope;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the end-to-end marketing report build: persists a {@link ReportJobEntity}, runs the build
 * on an async thread under the run's Claude usage and failure scopes, and tracks per-step progress for
 * the UI.
 *
 * <p>The build itself is one of two workflows: {@link DirectReportWorkflow} builds the workbook or deck
 * straight from the request's source grids, and {@link SlidesFromSheetWorkflow} builds the deck from a
 * workbook the user has already reviewed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationServiceImpl implements ReportGenerationService {

	private final ReportJobProgressHelper jobProgress;
	private final ReportGenerationWarningsHelper warnings;
	private final ObjectProvider<ReportGenerationService> self;
	/** Per-run Claude token accounting, opened in {@link #run} and stamped onto the job when it ends. */
	private final ClaudeUsageTracker usageTracker;
	private final ClaudeFailureLog failureLog;
	private final ReportRunRecorder recorder;
	private final SlidesFromSheetWorkflow slidesFromSheet;
	private final DirectReportWorkflow direct;

	/**
	 * Validates the brief, then enqueues the job and launches the build through the
	 * self-proxy so the {@code @Async} boundary on {@link #run} takes effect.
	 */
	@Override
	public ReportJobEntity start(
			String userId, String clerkUserId, String userEmail, GeneratePayload payload, GenerationTarget target,
			String mediaPlanUrl, String elevateUrl) {
		// Only the sheet-building flow has nowhere else to get the campaign context from. A
		// slides-from-sheet run reads {{RFP info}} straight off the reviewed workbook and lets it win over
		// this payload (see SlidesFromSheetWorkflow), so requiring a brief here would reject a workbook that
		// carries a perfectly good one in its own cell — which is exactly what a resumed draft looks like
		// once the user fills that cell after the sheet was built.
		if (target != GenerationTarget.SLIDES_FROM_SHEET
				&& (payload.brief() == null || payload.brief().isBlank())) {
			throw new AppException(ErrorReason.C002, "Brief is required");
		}
		if (target == GenerationTarget.SLIDES_FROM_SHEET
				&& (payload.sheetUrl() == null || payload.sheetUrl().isBlank())) {
			throw new AppException(ErrorReason.C002, "Sheet URL is required for the slides-from-sheet flow");
		}
		ReportJobEntity job = enqueue(userId, payload);
		jobProgress.recordJobContext(job.getId(), userEmail, target.name(), mediaPlanUrl, elevateUrl);
		self.getObject().run(job.getId(), payload, clerkUserId, userEmail, target);
		return job;
	}

	/**
	 * Persists the queued job in its own transaction via {@link ReportJobProgressHelper}.
	 */
	@Override
	@Transactional
	public ReportJobEntity enqueue(String userId, GeneratePayload payload) {
		return jobProgress.createQueuedJob(userId, payload.reportType());
	}

	/**
	 * Runs on a Spring {@code @Async} thread. Any failure is caught and recorded on the
	 * job as {@code error} rather than propagated to the caller.
	 */
	@Override
	@Async
	public void run(Long jobId, GeneratePayload payload, String clerkUserId, String userEmail,
			GenerationTarget target) {
		// Opened before any Claude work and read back in the finally below, so a run that fails half-way
		// still reports the tokens it burned — those are billed either way, and a failed expensive run is
		// exactly what the admin token dashboard exists to make visible.
		ClaudeUsageScope usageScope = usageTracker.begin(jobId, null, userEmail);
		// Opened alongside it for the same reason, on the other axis: a reply Claude sent but the pipeline
		// rejected only shows up in the server log, which the person who ran the report cannot read. Collecting
		// the reasons here puts them on the result card next to the blank slide they produced.
		failureLog.begin();
		try {
			if (target == GenerationTarget.SLIDES_FROM_SHEET) {
				// Step 2 of the sheet-as-source flow: the user-reviewed sheet is the only input, so
				// none of the raw-grid collection or the Batch A/B copy runs here — they are already
				// baked into the sheet by step 1. This branch reads the sheet back and fills the deck.
				slidesFromSheet.runSlidesFromSheet(jobId, payload, clerkUserId, userEmail);
				return;
			}
			direct.runDirect(jobId, payload, clerkUserId, userEmail, target, usageScope);
		} catch (Exception ex) {
			log.error("[report] job {} failed", jobId, ex);
			jobProgress.markJobFailed(jobId, ex.getMessage());
		} finally {
			recorder.recordUsage(jobId, usageScope);
			usageTracker.clear();
			failureLog.clear();
		}
	}

	/**
	 * Read-only lookup; delegates ownership enforcement to the job-progress helper and
	 * normalises null string fields to empty strings before returning.
	 */
	@Override
	@Transactional(readOnly = true)
	public ProgressView progress(String userId, Long jobId) {
		ReportJobEntity job = jobProgress.loadJobForOwner(userId, jobId);
		return new ProgressView(
				job.getStep(),
				job.getTotal(),
				job.getLabel() == null ? "" : job.getLabel(),
				job.getStatus(),
				job.getSlideUrl() == null ? "" : job.getSlideUrl(),
				job.getErrorMessage() == null ? "" : job.getErrorMessage(),
				warnings.parseWarnings(job.getWarningsJson())
		);
	}
}
