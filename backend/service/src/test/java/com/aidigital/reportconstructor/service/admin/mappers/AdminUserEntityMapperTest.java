package com.aidigital.reportconstructor.service.admin.mappers;

import com.aidigital.reportconstructor.domain.admin.entities.AdminUserEntity;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUserEntityMapperTest {

	private final AdminUserEntityMapper mapper = new AdminUserEntityMapperImpl();

	@Test
	void shouldBuildATransientGrantFromTheGivenValuesTest() {
		// Given:
		OffsetDateTime at = OffsetDateTime.of(2026, 11, 12, 10, 0, 0, 0, ZoneOffset.UTC);

		// When:
		AdminUserEntity entity = mapper.toEntity("new@aidigital.com", "root@aidigital.com", at);

		// Then:
		assertThat(entity.getId()).isNull();
		assertThat(entity.getEmail()).isEqualTo("new@aidigital.com");
		assertThat(entity.getAddedByEmail()).isEqualTo("root@aidigital.com");
		assertThat(entity.getCreatedAt()).isEqualTo(at);
	}
}
