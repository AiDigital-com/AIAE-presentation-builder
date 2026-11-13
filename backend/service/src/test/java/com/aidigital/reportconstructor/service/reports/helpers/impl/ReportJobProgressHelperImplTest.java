package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.reports.entity.ReportJobEntityService;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.common.time.CurrentTimeImpl;
import com.aidigital.reportconstructor.service.mappers.reports.ReportJobEntityMapperImpl;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportJobProgressHelperImplTest {

	@Mock
	private ReportJobEntityService jobs;

	@Spy
	private CurrentTimeImpl currentTime = new CurrentTimeImpl();

	@Spy
	private ReportJobEntityMapperImpl jobMapper = new ReportJobEntityMapperImpl();

	@InjectMocks
	private ReportJobProgressHelperImpl helper;

	@Test
	void shouldFailInFlightJobsSilentSinceTheCutoffTest() {
		// Given: a fixed clock
		OffsetDateTime now = OffsetDateTime.parse("2026-11-12T12:00:00Z");
		doReturn(now).when(currentTime).nowOffsetDateTime();
		when(jobs.failInFlightNotUpdatedSince(List.of("queued", "running"), now.minusMinutes(60), "error",
				"interrupted", now)).thenReturn(2);

		// When:
		int failed = helper.failAbandonedJobs(Duration.ofMinutes(60), "interrupted");

		// Then: only queued/running jobs untouched for the whole window are failed, stamped now
		assertThat(failed).isEqualTo(2);
	}

	@Test
	void shouldCreateQueuedJobWithSevenStepsTest() {
		when(jobs.save(any(ReportJobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

		ReportJobEntity job = helper.createQueuedJob("user-1", "standard");

		ArgumentCaptor<ReportJobEntity> captor = ArgumentCaptor.forClass(ReportJobEntity.class);
		verify(jobs).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo("queued");
		assertThat(captor.getValue().getTotal()).isEqualTo(7);
		assertThat(captor.getValue().getOwnerUserId()).isEqualTo("user-1");
		assertThat(captor.getValue().getReportTypeCode()).isEqualTo("standard");
		assertThat(job.getLabel()).isEqualTo("Queued…");
	}

	@Test
	void shouldMarkJobRunningAtStepTest() {
		ReportJobEntity existing = new ReportJobEntity();
		existing.setId(5L);
		when(jobs.findById(5L)).thenReturn(Optional.of(existing));
		when(jobs.save(any(ReportJobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

		helper.markJobRunningAtStep(5L, 3, "Claude — campaign batch (A)");

		assertThat(existing.getStatus()).isEqualTo("running");
		assertThat(existing.getStep()).isEqualTo(3);
		assertThat(existing.getLabel()).isEqualTo("Claude — campaign batch (A)");
	}

	@Test
	void shouldMarkJobDoneTest() {
		ReportJobEntity existing = new ReportJobEntity();
		existing.setId(9L);
		when(jobs.findById(9L)).thenReturn(Optional.of(existing));
		when(jobs.save(any(ReportJobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

		helper.markJobDone(9L, "https://slides/d/abc123/edit", "[\"warn\"]");

		assertThat(existing.getStatus()).isEqualTo("done");
		assertThat(existing.getStep()).isEqualTo(7);
		assertThat(existing.getLabel()).isEqualTo("Done!");
		assertThat(existing.getSlideUrl()).isEqualTo("https://slides/d/abc123/edit");
		assertThat(existing.getWarningsJson()).isEqualTo("[\"warn\"]");
	}

	@Test
	void shouldThrowWhenJobNotOwnedByUserTest() {
		ReportJobEntity existing = new ReportJobEntity();
		existing.setOwnerUserId("other");
		when(jobs.findById(1L)).thenReturn(Optional.of(existing));

		Throwable thrown = catchThrowable(() -> helper.loadJobForOwner("user-1", 1L));

		assertThat(thrown)
				.isInstanceOf(AppException.class)
				.hasFieldOrPropertyWithValue("code", ErrorReason.C001.getCode());
	}

	@Test
	void shouldIgnoreAMissingResumeStateTest() {
		// Given: no payload to store.
		String blank = "  ";

		// When:
		helper.recordResumeState(4L, null);
		helper.recordResumeState(4L, blank);

		// Then: the job is never even loaded.
		verifyNoInteractions(jobs);
	}

	@Test
	void shouldStoreTheResumeStateTest() {
		// Given:
		OffsetDateTime now = OffsetDateTime.parse("2026-11-12T12:00:00Z");
		doReturn(now).when(currentTime).nowOffsetDateTime();
		ReportJobEntity job = new ReportJobEntity();
		job.setId(4L);
		when(jobs.findById(4L)).thenReturn(Optional.of(job));

		// When:
		helper.recordResumeState(4L, "{\"step\":3}");

		// Then:
		verify(jobs).save(same(job));
		assertThat(job.getPayloadJson()).isEqualTo("{\"step\":3}");
		assertThat(job.getUpdatedAt()).isEqualTo(now);
	}

	@Test
	void shouldKeepTheFirstDismissalTimeTest() {
		// Given: a job that was already dismissed.
		OffsetDateTime dismissed = OffsetDateTime.parse("2026-10-01T08:00:00Z");
		ReportJobEntity job = new ReportJobEntity();
		job.setId(6L);
		job.setDismissedAt(dismissed);
		when(jobs.findById(6L)).thenReturn(Optional.of(job));

		// When:
		helper.dismissJob(6L);

		// Then: dismissing again is a no-op.
		assertThat(job.getDismissedAt()).isEqualTo(dismissed);
		verify(jobs, never()).save(same(job));
	}

	@Test
	void shouldDismissAJobTest() {
		// Given:
		OffsetDateTime now = OffsetDateTime.parse("2026-11-12T12:00:00Z");
		doReturn(now).when(currentTime).nowOffsetDateTime();
		ReportJobEntity job = new ReportJobEntity();
		job.setId(6L);
		when(jobs.findById(6L)).thenReturn(Optional.of(job));

		// When:
		helper.dismissJob(6L);

		// Then:
		verify(jobs).save(same(job));
		assertThat(job.getDismissedAt()).isEqualTo(now);
		assertThat(job.getUpdatedAt()).isEqualTo(now);
	}

	@Test
	void shouldIgnoreANonPositiveSlideCountTest() {
		// Given: an empty deck.
		int slides = 0;

		// When:
		helper.recordSlideCount(8L, slides);

		// Then:
		verifyNoInteractions(jobs);
	}

	@Test
	void shouldRecordTheSlideCountTest() {
		// Given:
		ReportJobEntity job = new ReportJobEntity();
		job.setId(8L);
		when(jobs.findById(8L)).thenReturn(Optional.of(job));

		// When:
		helper.recordSlideCount(8L, 14);

		// Then:
		verify(jobs).save(same(job));
		assertThat(job.getSlideCount()).isEqualTo(14);
	}

	@Test
	void shouldIgnoreMissingOrEmptyTokenUsageTest() {
		// Given: no usage at all, and a usage block with zero calls.
		ClaudeUsage noCalls = new ClaudeUsage(5L, 5L, 0L, 0L, 0, "claude-sonnet");

		// When:
		helper.recordTokenUsage(3L, null);
		helper.recordTokenUsage(3L, noCalls);

		// Then:
		verifyNoInteractions(jobs);
	}

	@Test
	void shouldStampTheTokenUsageOnTheJobTest() {
		// Given:
		ReportJobEntity job = new ReportJobEntity();
		job.setId(3L);
		when(jobs.findById(3L)).thenReturn(Optional.of(job));
		ClaudeUsage usage = new ClaudeUsage(1200L, 300L, 40L, 900L, 6, "claude-sonnet");

		// When:
		helper.recordTokenUsage(3L, usage);

		// Then:
		verify(jobs).save(same(job));
		assertThat(job.getInputTokens()).isEqualTo(1200L);
		assertThat(job.getOutputTokens()).isEqualTo(300L);
		assertThat(job.getCacheWriteTokens()).isEqualTo(40L);
		assertThat(job.getCacheReadTokens()).isEqualTo(900L);
		assertThat(job.getClaudeCalls()).isEqualTo(6);
		assertThat(job.getClaudeModel()).isEqualTo("claude-sonnet");
	}

	@Test
	void shouldRefuseAJobToAnAnonymousCallerTest() {
		// Given: a job that has an owner.
		ReportJobEntity existing = new ReportJobEntity();
		existing.setOwnerUserId("user-1");
		when(jobs.findById(2L)).thenReturn(Optional.of(existing));

		// When:
		Throwable thrown = catchThrowable(() -> helper.loadJobForOwner(null, 2L));

		// Then:
		assertThat(thrown)
				.isInstanceOf(AppException.class)
				.hasFieldOrPropertyWithValue("code", ErrorReason.C001.getCode());
	}

	@Test
	void shouldReturnAJobToItsOwnerTest() {
		// Given:
		ReportJobEntity existing = new ReportJobEntity();
		existing.setOwnerUserId("user-1");
		when(jobs.findById(2L)).thenReturn(Optional.of(existing));

		// When:
		ReportJobEntity job = helper.loadJobForOwner("user-1", 2L);

		// Then:
		assertThat(job).isSameAs(existing);
	}
}
