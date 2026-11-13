package com.aidigital.reportconstructor.mappers.admin;

import com.aidigital.reportconstructor.api.v1.model.AdminEntryV1;
import com.aidigital.reportconstructor.api.v1.model.AdminListV1;
import com.aidigital.reportconstructor.api.v1.model.AdminSourceV1;
import com.aidigital.reportconstructor.service.admin.dto.AdminEntry;
import com.aidigital.reportconstructor.service.admin.dto.AdminList;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the "Manage admins" mapping: entries, the list envelope, and the source wire codes.
 */
class AdminManagementApiMapperTest {

	@Test
	void shouldMapAdminListWithConfigAndManagedEntriesTest() {
		// Given:
		AdminManagementApiMapper mapper = new AdminManagementApiMapperImpl();
		LocalDateTime grantedAt = LocalDateTime.of(2026, 8, 2, 14, 30);
		AdminList list = new AdminList(List.of(
				new AdminEntry("root@aidigital.com", "config", null, null, false),
				new AdminEntry("dave@aidigital.com", "managed", "root@aidigital.com", grantedAt, true)));

		// When:
		AdminListV1 dto = mapper.toList(list);

		// Then:
		assertThat(dto.getAdmins()).hasSize(2);
		AdminEntryV1 root = dto.getAdmins().get(0);
		assertThat(root.getEmail()).isEqualTo("root@aidigital.com");
		assertThat(root.getSource()).isEqualTo(AdminSourceV1.CONFIG);
		assertThat(root.getAddedByEmail()).isNull();
		assertThat(root.getCreatedAt()).isNull();
		assertThat(root.getRemovable()).isFalse();
		AdminEntryV1 managed = dto.getAdmins().get(1);
		assertThat(managed.getEmail()).isEqualTo("dave@aidigital.com");
		assertThat(managed.getSource()).isEqualTo(AdminSourceV1.MANAGED);
		assertThat(managed.getAddedByEmail()).isEqualTo("root@aidigital.com");
		assertThat(managed.getCreatedAt()).isEqualTo(grantedAt);
		assertThat(managed.getRemovable()).isTrue();
	}

	@Test
	void shouldMapEntryWithoutSourceTest() {
		// Given:
		AdminManagementApiMapper mapper = new AdminManagementApiMapperImpl();
		AdminEntry entry = new AdminEntry("erin@aidigital.com", null, null, null, false);

		// When:
		AdminEntryV1 dto = mapper.toEntry(entry);

		// Then:
		assertThat(dto.getEmail()).isEqualTo("erin@aidigital.com");
		assertThat(dto.getSource()).isNull();
	}

	@Test
	void shouldMapEverySourceCodeTest() {
		// Given:
		AdminManagementApiMapper mapper = new AdminManagementApiMapperImpl();

		// When-Then:
		assertThat(mapper.toSource("config")).isEqualTo(AdminSourceV1.CONFIG);
		assertThat(mapper.toSource("managed")).isEqualTo(AdminSourceV1.MANAGED);
		assertThat(mapper.toSource(null)).isNull();
	}

	@Test
	void shouldRejectUnknownSourceCodeTest() {
		// Given:
		AdminManagementApiMapper mapper = new AdminManagementApiMapperImpl();

		// When-Then:
		assertThatThrownBy(() -> mapper.toSource("ldap"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ldap");
	}

	@Test
	void shouldKeepNullEntriesInsideTheListTest() {
		// Given:
		AdminManagementApiMapper mapper = new AdminManagementApiMapperImpl();

		// When:
		List<AdminEntryV1> dtos = mapper.toEntries(Collections.singletonList(null));

		// Then:
		assertThat(dtos).containsExactly((AdminEntryV1) null);
	}

	@Test
	void shouldMapListWithoutAdminsToNullAdminsTest() {
		// Given:
		AdminManagementApiMapper mapper = new AdminManagementApiMapperImpl();

		// When:
		AdminListV1 dto = mapper.toList(new AdminList(null));

		// Then:
		assertThat(dto.getAdmins()).isNull();
	}

	@Test
	void shouldReturnNullForEveryNullSourceTest() {
		// Given:
		AdminManagementApiMapper mapper = new AdminManagementApiMapperImpl();

		// When-Then:
		assertThat(mapper.toEntry(null)).isNull();
		assertThat(mapper.toEntries(null)).isNull();
		assertThat(mapper.toList(null)).isNull();
	}
}
