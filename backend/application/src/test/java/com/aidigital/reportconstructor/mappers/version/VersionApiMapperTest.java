package com.aidigital.reportconstructor.mappers.version;

import com.aidigital.reportconstructor.api.v1.model.VersionV1;
import com.aidigital.reportconstructor.service.version.dto.GitVersionInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link VersionApiMapper} through its generated implementation.
 */
class VersionApiMapperTest {

	@Test
	void shouldExposeTheShortCommitIdAndCommitTimeTest() {
		// Given:
		VersionApiMapperImpl mapper = new VersionApiMapperImpl();
		GitVersionInfo info = new GitVersionInfo("4ea6920", "2026-11-13T10:00:00Z");

		// When:
		VersionV1 dto = mapper.toVersion(info);

		// Then:
		assertThat(dto.getCommitId()).isEqualTo("4ea6920");
		assertThat(dto.getCommitTime()).isEqualTo("2026-11-13T10:00:00Z");
	}

	@Test
	void shouldReturnNullWhenThereIsNoVersionInfoTest() {
		// Given:
		VersionApiMapperImpl mapper = new VersionApiMapperImpl();

		// When:
		VersionV1 dto = mapper.toVersion(null);

		// Then:
		assertThat(dto).isNull();
	}
}
