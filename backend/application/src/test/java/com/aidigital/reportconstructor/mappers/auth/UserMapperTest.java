package com.aidigital.reportconstructor.mappers.auth;

import com.aidigital.reportconstructor.api.v1.model.UserV1;
import com.aidigital.reportconstructor.service.common.security.AppUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UserMapper} through its generated implementation.
 */
class UserMapperTest {

	@Test
	void shouldMapTheCallerAndTheAdminFlagTest() {
		// Given:
		UserMapperImpl mapper = new UserMapperImpl();
		AppUser user = new AppUser("user_1", "alice@aidigital.com", "Alice Example");

		// When:
		UserV1 admin = mapper.toUserV1(user, true);
		UserV1 member = mapper.toUserV1(user, false);

		// Then:
		assertThat(admin.getUserId()).isEqualTo("user_1");
		assertThat(admin.getEmail()).isEqualTo("alice@aidigital.com");
		assertThat(admin.getFullName()).isEqualTo("Alice Example");
		assertThat(admin.getIsAdmin()).isTrue();
		assertThat(member.getIsAdmin()).isFalse();
	}

	@Test
	void shouldReturnNullWithoutACallerTest() {
		// Given:
		UserMapperImpl mapper = new UserMapperImpl();

		// When:
		UserV1 dto = mapper.toUserV1(null, true);

		// Then:
		assertThat(dto).isNull();
	}
}
