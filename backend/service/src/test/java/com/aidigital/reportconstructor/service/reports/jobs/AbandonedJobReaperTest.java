package com.aidigital.reportconstructor.service.reports.jobs;

import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbandonedJobReaperTest {

	@Test
	void shouldFailJobsSilentForTheConfiguredTimeTest() {
		// Given:
		ReportJobProgressHelper jobs = mock(ReportJobProgressHelper.class);
		ReportJobProperties props = new ReportJobProperties();
		props.setAbandonedAfterMinutes(45);
		AbandonedJobReaper reaper = new AbandonedJobReaper(jobs, props);

		// When:
		reaper.sweep();

		// Then:
		verify(jobs).failAbandonedJobs(Duration.ofMinutes(45), AbandonedJobReaper.ABANDONED_MESSAGE);
	}

	@Test
	void shouldNeverLetAFailedSweepEscapeTest() {
		// Given: the database is unavailable this tick
		ReportJobProgressHelper jobs = mock(ReportJobProgressHelper.class);
		when(jobs.failAbandonedJobs(Duration.ofMinutes(60), AbandonedJobReaper.ABANDONED_MESSAGE))
				.thenThrow(new IllegalStateException("db down"));
		AbandonedJobReaper reaper = new AbandonedJobReaper(jobs, new ReportJobProperties());

		// When-Then: the scheduler thread survives to try again
		assertThatCode(reaper::sweep).doesNotThrowAnyException();
	}

	@Test
	void shouldTreatANonPositiveSettingAsOneMinuteTest() {
		// Given: a misconfigured value that would otherwise fail every in-flight job immediately
		ReportJobProgressHelper jobs = mock(ReportJobProgressHelper.class);
		ReportJobProperties props = new ReportJobProperties();
		props.setAbandonedAfterMinutes(0);
		AbandonedJobReaper reaper = new AbandonedJobReaper(jobs, props);

		// When:
		reaper.sweep();

		// Then:
		verify(jobs).failAbandonedJobs(Duration.ofMinutes(1), AbandonedJobReaper.ABANDONED_MESSAGE);
	}
}
