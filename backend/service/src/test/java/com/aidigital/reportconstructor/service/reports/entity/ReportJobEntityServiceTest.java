package com.aidigital.reportconstructor.service.reports.entity;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.domain.reports.projections.JobOwner;
import com.aidigital.reportconstructor.domain.reports.repositories.ReportJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportJobEntityServiceTest {

	@Mock
	private ReportJobRepository reportJobRepository;

	@InjectMocks
	private ReportJobEntityService service;

	@Test
	void shouldListAnOwnersJobsThroughTheRepositoryTest() {
		// Given: one job owned by the user
		ReportJobEntity job = new ReportJobEntity();
		when(reportJobRepository.findByOwnerUserIdOrderByCreatedAtDesc("user_1")).thenReturn(List.of(job));

		// When:
		List<ReportJobEntity> jobs = service.findByOwnerUserIdOrderByCreatedAtDesc("user_1");

		// Then:
		assertThat(jobs).containsExactly(job);
	}

	@Test
	void shouldCountJobsByStatusThroughTheRepositoryTest() {
		// Given: two in-flight jobs
		List<String> statuses = List.of("queued", "running");
		when(reportJobRepository.countByStatusIn(statuses)).thenReturn(2);

		// When:
		int count = service.countByStatusIn(statuses);

		// Then:
		assertThat(count).isEqualTo(2);
	}

	@Test
	void shouldListFailuresBoundedAndUnboundedThroughTheRepositoryTest() {
		// Given: one failed job, reachable both with and without a page bound
		ReportJobEntity failed = new ReportJobEntity();
		List<String> statuses = List.of("error");
		Pageable page = PageRequest.of(0, 5);
		when(reportJobRepository.findByStatusInOrderByCreatedAtDesc(statuses, page)).thenReturn(List.of(failed));
		when(reportJobRepository.findByStatusInOrderByCreatedAtDesc(statuses)).thenReturn(List.of(failed));

		// When:
		List<ReportJobEntity> bounded = service.findByStatusInOrderByCreatedAtDesc(statuses, page);
		List<ReportJobEntity> unbounded = service.findByStatusInOrderByCreatedAtDesc(statuses);

		// Then:
		assertThat(bounded).containsExactly(failed);
		assertThat(unbounded).containsExactly(failed);
	}

	@Test
	void shouldReadWarnedJobsOwnersAndEarliestDateThroughTheRepositoryTest() {
		// Given: a warned job, one owner and an earliest creation time
		ReportJobEntity warned = new ReportJobEntity();
		Pageable page = PageRequest.of(0, 3);
		JobOwner owner = new JobOwner("user_1", "a@b.c", OffsetDateTime.of(2026, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC));
		OffsetDateTime earliest = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
		when(reportJobRepository.findRecentWarned(page)).thenReturn(List.of(warned));
		when(reportJobRepository.listOwners()).thenReturn(List.of(owner));
		when(reportJobRepository.earliestCreatedAt()).thenReturn(earliest);

		// When:
		List<ReportJobEntity> warnedJobs = service.findRecentWarned(page);
		List<JobOwner> owners = service.listOwners();
		OffsetDateTime first = service.earliestCreatedAt();

		// Then:
		assertThat(warnedJobs).containsExactly(warned);
		assertThat(owners).containsExactly(owner);
		assertThat(first).isEqualTo(earliest);
	}

	@Test
	void shouldPageDeliverablesAndListAllJobsThroughTheRepositoryTest() {
		// Given: one deliverable job on the first page
		ReportJobEntity job = new ReportJobEntity();
		Pageable page = PageRequest.of(0, 20);
		Page<ReportJobEntity> deliverables = new PageImpl<>(List.of(job), page, 1);
		when(reportJobRepository.findDeliverables("SHEET", page)).thenReturn(deliverables);
		when(reportJobRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(job));

		// When:
		Page<ReportJobEntity> result = service.findDeliverables("SHEET", page);
		List<ReportJobEntity> all = service.findAllByOrderByCreatedAtDesc();

		// Then:
		assertThat(result).isSameAs(deliverables);
		assertThat(all).containsExactly(job);
	}

	@Test
	void shouldDeleteAJobThroughTheRepositoryTest() {
		// Given: a job id

		// When:
		service.deleteById(42L);

		// Then:
		verify(reportJobRepository).deleteById(42L);
	}
}
