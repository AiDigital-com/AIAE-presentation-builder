package com.aidigital.reportconstructor.service.reports.jobs;

import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fails report jobs whose node stopped while they ran.
 *
 * <p>A report runs on an {@code @Async} thread of the node that accepted it. When that node restarts — a deploy,
 * a crash, a scale-down — the job's row stays {@code queued} or {@code running} for ever: the user's progress
 * view keeps polling and the admin dashboard keeps counting it in flight. This sweep marks such a job
 * {@code error} once it has gone {@link ReportJobProperties#getAbandonedAfterMinutes()} without any progress, so
 * the user sees why and can run it again.
 *
 * <p>Every node runs the sweep; it is one conditional {@code UPDATE}, so concurrent sweeps claim each row once and
 * a repeat finds nothing to do. A job that was only slow and finishes later still ends {@code done}: completion
 * overwrites the status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbandonedJobReaper {

	/** How often the sweep runs, in milliseconds. */
	private static final long SWEEP_INTERVAL_MS = 300_000L;

	/** What an abandoned job shows the person who ran it. */
	static final String ABANDONED_MESSAGE =
			"The report was interrupted — the server restarted while it was running. Please run it again.";

	private final ReportJobProgressHelper jobs;
	private final ReportJobProperties props;

	/**
	 * Marks every abandoned job failed. Never throws: a failed sweep is retried on the next tick.
	 */
	@Scheduled(fixedDelay = SWEEP_INTERVAL_MS, initialDelay = SWEEP_INTERVAL_MS)
	public void sweep() {
		try {
			int failed = jobs.failAbandonedJobs(
					Duration.ofMinutes(Math.max(1, props.getAbandonedAfterMinutes())), ABANDONED_MESSAGE);
			if (failed > 0) {
				log.warn("[jobs] marked {} abandoned report job(s) failed (no progress for {} min)",
						failed, props.getAbandonedAfterMinutes());
			}
		} catch (RuntimeException ex) {
			log.warn("[jobs] abandoned-job sweep failed; retrying on the next tick", ex);
		}
	}
}
