package com.aidigital.reportconstructor.mappers.reports;

import com.aidigital.reportconstructor.api.v1.model.ReportJobCreatedV1;
import com.aidigital.reportconstructor.api.v1.model.ReportJobStatusV1;
import com.aidigital.reportconstructor.api.v1.model.ReportJobV1;
import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.reports.dto.ProgressView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ReportJobsApiMapper} through its generated implementation.
 */
class ReportJobsApiMapperTest {

	@Test
	void shouldMapThePersistedJobIdOntoTheCreatedResponseTest() {
		// Given:
		ReportJobsApiMapperImpl mapper = new ReportJobsApiMapperImpl();
		ReportJobEntity job = new ReportJobEntity();
		job.setId(42L);

		// When:
		ReportJobCreatedV1 dto = mapper.toCreated(job);

		// Then:
		assertThat(dto.getJobId()).isEqualTo(42L);
	}

	@Test
	void shouldReturnNullForNullInputsTest() {
		// Given:
		ReportJobsApiMapperImpl mapper = new ReportJobsApiMapperImpl();

		// When-Then:
		assertThat(mapper.toCreated(null)).isNull();
		assertThat(mapper.toJob(null, null)).isNull();
		assertThat(mapper.toStatus(null)).isNull();
	}

	@Test
	void shouldMapEveryProgressFieldTest() {
		// Given: a running job with one warning
		ReportJobsApiMapperImpl mapper = new ReportJobsApiMapperImpl();
		ProgressView view = new ProgressView(3, 7, "Writing copy", "running", "https://slides/1", "",
				List.of("chart skipped"));

		// When:
		ReportJobV1 dto = mapper.toJob(9L, view);

		// Then:
		assertThat(dto.getJobId()).isEqualTo(9L);
		assertThat(dto.getStatus()).isEqualTo(ReportJobStatusV1.RUNNING);
		assertThat(dto.getStep()).isEqualTo(3);
		assertThat(dto.getTotal()).isEqualTo(7);
		assertThat(dto.getLabel()).isEqualTo("Writing copy");
		assertThat(dto.getSlideUrl()).isEqualTo("https://slides/1");
		assertThat(dto.getError()).isEmpty();
		assertThat(dto.getWarnings()).containsExactly("chart skipped");
	}

	@Test
	void shouldKeepEmptyWarningsAndNullStatusWhenTheViewHasNoneTest() {
		// Given: a view without warnings or status
		ReportJobsApiMapperImpl mapper = new ReportJobsApiMapperImpl();
		ProgressView view = new ProgressView(0, 7, null, null, null, null, null);

		// When:
		ReportJobV1 dto = mapper.toJob(null, view);

		// Then:
		assertThat(dto.getJobId()).isNull();
		assertThat(dto.getStatus()).isNull();
		assertThat(dto.getWarnings()).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(ReportJobStatusV1.class)
	void shouldResolveEveryStatusWireCodeTest(ReportJobStatusV1 status) {
		// Given:
		ReportJobsApiMapperImpl mapper = new ReportJobsApiMapperImpl();

		// When:
		ReportJobStatusV1 mapped = mapper.toStatus(status.getValue());

		// Then:
		assertThat(mapped).isEqualTo(status);
	}

	@Test
	void shouldRejectAnUnknownStatusWireCodeTest() {
		// Given:
		ReportJobsApiMapperImpl mapper = new ReportJobsApiMapperImpl();

		// When-Then:
		assertThatThrownBy(() -> mapper.toStatus("paused")).isInstanceOf(IllegalArgumentException.class);
	}
}
