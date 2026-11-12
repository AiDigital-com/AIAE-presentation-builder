package com.aidigital.reportconstructor.service.mappers.reports;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ReportJobEntityMapperTest {

	private final ReportJobEntityMapper mapper = new ReportJobEntityMapperImpl();

	@Test
	void shouldBuildAQueuedJobAtStepZeroOfSevenTest() {
		// Given:
		OffsetDateTime now = OffsetDateTime.of(2026, 11, 12, 10, 0, 0, 0, ZoneOffset.UTC);

		// When:
		ReportJobEntity job = mapper.toQueuedJob("user-1", "EOC", now);

		// Then:
		assertThat(job.getId()).isNull();
		assertThat(job.getOwnerUserId()).isEqualTo("user-1");
		assertThat(job.getReportTypeCode()).isEqualTo("EOC");
		assertThat(job.getStatus()).isEqualTo("queued");
		assertThat(job.getStep()).isZero();
		assertThat(job.getTotal()).isEqualTo(7);
		assertThat(job.getLabel()).isEqualTo("Queued…");
		assertThat(job.getCreatedAt()).isEqualTo(now);
		assertThat(job.getUpdatedAt()).isEqualTo(now);
		assertThat(job.getOwnerEmail()).isNull();
		assertThat(job.getPayloadJson()).isNull();
	}
}
