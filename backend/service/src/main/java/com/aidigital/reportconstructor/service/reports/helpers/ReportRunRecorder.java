package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.ClaudeUsage;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Run bookkeeping stamped onto a finished report job — the deck's slide count and the run's Claude token usage.
 * Bookkeeping never fails a report: problems are logged and swallowed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportRunRecorder {

	private final ReportJobProgressHelper jobProgress;
	private final SlidesProvider slides;

	/**
	 * Measures the finished deck and stamps its slide count onto the job, for the admin dashboard's
	 * saved-hours figure. Runs after the surplus template slides have been deleted, so the number is
	 * what the client receives. Like token accounting, this is bookkeeping: a deck that shipped must
	 * never be reported as failed because its slides could not be counted.
	 *
	 * @param jobId           id of the job that just finished
	 * @param slideUrl        the finished deck
	 * @param userGoogleToken signed-in user's Google OAuth token, or {@code null} for the service account
	 */
	public void recordSlideCount(Long jobId, String slideUrl, String userGoogleToken) {
		try {
			jobProgress.recordSlideCount(jobId, slides.countSlides(slideUrl, userGoogleToken));
		} catch (Exception ex) {
			log.warn("[report] job {} slide count could not be recorded: {}", jobId, ex.getMessage());
		}
	}

	/**
	 * Stamps the run's token consumption onto the job. Accounting must never turn a finished report
	 * into a failed one, so a persistence problem here is logged and swallowed.
	 *
	 * @param jobId      id of the job that just finished
	 * @param usageScope the run's token counters
	 */
	public void recordUsage(Long jobId, ClaudeUsageScope usageScope) {
		try {
			ClaudeUsage usage = usageScope.snapshot();
			jobProgress.recordTokenUsage(jobId, usage);
			log.info("[report] job {} claude usage calls={} in={} out={} cacheWrite={} cacheRead={}",
					jobId, usage.calls(), usage.inputTokens(), usage.outputTokens(),
					usage.cacheWriteTokens(), usage.cacheReadTokens());
		} catch (Exception ex) {
			log.warn("[report] job {} token usage could not be recorded: {}", jobId, ex.getMessage());
		}
	}
}
