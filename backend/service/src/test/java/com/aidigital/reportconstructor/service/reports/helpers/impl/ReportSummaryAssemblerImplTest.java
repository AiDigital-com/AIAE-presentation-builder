package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.common.text.DisplayNameHelper;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;
import com.aidigital.reportconstructor.service.reports.usage.JobTokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportSummaryAssemblerImplTest {

	@Mock
	private DisplayNameHelper displayNameHelper;

	@Mock
	private JobTokenUsage tokenUsage;

	@InjectMocks
	private ReportSummaryAssemblerImpl assembler;

	@Test
	void shouldMapEveryFieldOfAFinishedJobTest() {
		// Given: a job with a generated file name, an owner and a creation time.
		ReportJobEntity job = new ReportJobEntity();
		job.setId(11L);
		job.setReportTypeCode("eom");
		job.setStatus("done");
		job.setArtifactName("Acme EOM April");
		job.setCreatedAt(OffsetDateTime.parse("2026-04-30T17:45:00Z"));
		job.setSlideUrl("https://slides/1");
		job.setSheetUrl("https://sheet/1");
		job.setMediaPlanUrl("https://plan/1");
		job.setElevateUrl("https://elevate/1");
		job.setOwnerEmail("jane.doe@aidigital.com");
		when(displayNameHelper.fromEmail("jane.doe@aidigital.com")).thenReturn("Jane Doe");
		when(tokenUsage.allInputTokens(same(job))).thenReturn(1500L);
		when(tokenUsage.outputTokens(same(job))).thenReturn(500L);
		when(tokenUsage.totalTokens(same(job))).thenReturn(2000L);
		when(tokenUsage.costUsd(same(job))).thenReturn(0.42d);

		// When:
		ReportSummary summary = assembler.toSummary(job);

		// Then: the file name is the title and the row is not a draft.
		assertThat(summary).isEqualTo(new ReportSummary(
				11L, "eom", "done", "Acme EOM April", LocalDateTime.of(2026, 4, 30, 17, 45),
				"https://slides/1", "https://sheet/1", "Acme EOM April", "https://plan/1", "https://elevate/1",
				"jane.doe@aidigital.com", "Jane Doe", 1500L, 500L, 2000L, 0.42d, false));
	}

	@Test
	void shouldFallBackToATypeTitleAndLeaveBlankOwnerUnnamedTest() {
		// Given: a draft with a blank file name, a blank owner and no creation time.
		ReportJobEntity job = new ReportJobEntity();
		job.setReportTypeCode(" eoc ");
		job.setArtifactName("  ");
		job.setOwnerEmail(" ");

		// When:
		ReportSummary summary = assembler.toSummary(job, true);

		// Then:
		assertThat(summary.title()).isEqualTo("EOC report");
		assertThat(summary.createdAt()).isNull();
		assertThat(summary.ownerName()).isNull();
		assertThat(summary.draft()).isTrue();
		verifyNoInteractions(displayNameHelper);
	}

	@Test
	void shouldUseAGenericTitleWhenNeitherNameNorTypeIsKnownTest() {
		// Given: a job with nothing to name it by and no owner.
		ReportJobEntity job = new ReportJobEntity();

		// When:
		ReportSummary summary = assembler.toSummary(job);

		// Then:
		assertThat(summary.title()).isEqualTo("Report");
		assertThat(summary.ownerEmail()).isNull();
		assertThat(summary.ownerName()).isNull();
		verifyNoInteractions(displayNameHelper);
	}

	@Test
	void shouldUseAGenericTitleWhenTheTypeIsBlankTest() {
		// Given:
		ReportJobEntity job = new ReportJobEntity();
		job.setReportTypeCode("   ");

		// When:
		String title = assembler.title(job);

		// Then:
		assertThat(title).isEqualTo("Report");
	}
}
