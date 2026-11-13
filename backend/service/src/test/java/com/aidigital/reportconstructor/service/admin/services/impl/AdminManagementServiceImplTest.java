package com.aidigital.reportconstructor.service.admin.services.impl;

import com.aidigital.reportconstructor.domain.admin.entities.AdminUserEntity;
import com.aidigital.reportconstructor.service.admin.AdminAccessPolicy;
import com.aidigital.reportconstructor.service.admin.AdminUserService;
import com.aidigital.reportconstructor.service.admin.dto.AdminEntry;
import com.aidigital.reportconstructor.service.admin.dto.AdminList;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminManagementServiceImplTest {

	private static final String CALLER = "admin@aidigital.com";

	@Mock
	private AdminAccessPolicy adminAccessPolicy;

	@Mock
	private AdminUserService adminUserService;

	@InjectMocks
	private AdminManagementServiceImpl service;

	@Test
	void shouldRejectANonAdminCallerWhenListingTest() {
		// Given: a caller who is not an admin.
		when(adminAccessPolicy.isAdmin("intruder@example.com")).thenReturn(false);

		// When-Then: access is refused before anything is read.
		assertThatThrownBy(() -> service.listAdmins("intruder@example.com"))
				.isInstanceOf(AppException.class)
				.hasFieldOrPropertyWithValue("code", ErrorReason.C004.getCode());
		verifyNoInteractions(adminUserService);
	}

	@Test
	void shouldListConfigAdminsFirstAndSkipManagedDuplicatesTest() {
		// Given: one root admin, a managed grant duplicating it in another case, and two genuine grants —
		// one with a creation time, one without.
		when(adminAccessPolicy.isAdmin(CALLER)).thenReturn(true);
		when(adminAccessPolicy.configAdminEmails()).thenReturn(List.of("root@aidigital.com"));
		AdminUserEntity duplicate = new AdminUserEntity();
		duplicate.setEmail("Root@AIDigital.com");
		AdminUserEntity dated = new AdminUserEntity();
		dated.setEmail("dated@aidigital.com");
		dated.setAddedByEmail("root@aidigital.com");
		dated.setCreatedAt(OffsetDateTime.parse("2026-03-04T10:15:00Z"));
		AdminUserEntity undated = new AdminUserEntity();
		undated.setEmail("undated@aidigital.com");
		undated.setAddedByEmail("dated@aidigital.com");
		when(adminUserService.listAll()).thenReturn(List.of(duplicate, dated, undated));

		// When:
		AdminList result = service.listAdmins(CALLER);

		// Then: root first and not removable, the duplicate dropped, managed grants removable.
		assertThat(result.admins()).containsExactly(
				new AdminEntry("root@aidigital.com", "config", null, null, false),
				new AdminEntry("dated@aidigital.com", "managed", "root@aidigital.com",
						LocalDateTime.of(2026, 3, 4, 10, 15), true),
				new AdminEntry("undated@aidigital.com", "managed", "dated@aidigital.com", null, true));
	}

	@Test
	void shouldGrantANormalizedEmailTest() {
		// Given: an admin adding an address typed with whitespace and mixed case.
		when(adminAccessPolicy.isAdmin(CALLER)).thenReturn(true);
		when(adminAccessPolicy.configAdminEmails()).thenReturn(List.of());
		AdminUserEntity added = new AdminUserEntity();
		added.setEmail("new.admin@aidigital.com");
		added.setAddedByEmail(CALLER);
		when(adminUserService.listAll()).thenReturn(List.of(added));

		// When:
		AdminList result = service.addAdmin(CALLER, "  New.Admin@AIDigital.com ");

		// Then: the grant is stored lowercased and trimmed, and the refreshed list is returned.
		verify(adminUserService).add("new.admin@aidigital.com", CALLER);
		assertThat(result.admins()).extracting(AdminEntry::email).containsExactly("new.admin@aidigital.com");
	}

	@Test
	void shouldRejectAMissingEmailWhenAddingTest() {
		// Given:
		when(adminAccessPolicy.isAdmin(CALLER)).thenReturn(true);

		// When-Then:
		assertThatThrownBy(() -> service.addAdmin(CALLER, null))
				.isInstanceOf(AppException.class)
				.hasFieldOrPropertyWithValue("code", ErrorReason.C002.getCode());
		verifyNoInteractions(adminUserService);
	}

	@Test
	void shouldRejectABlankEmailWhenAddingTest() {
		// Given:
		when(adminAccessPolicy.isAdmin(CALLER)).thenReturn(true);

		// When-Then:
		assertThatThrownBy(() -> service.addAdmin(CALLER, "   "))
				.isInstanceOf(AppException.class)
				.hasFieldOrPropertyWithValue("code", ErrorReason.C002.getCode());
		verifyNoInteractions(adminUserService);
	}

	@Test
	void shouldRejectAnEmailWithNoLocalPartTest() {
		// Given:
		when(adminAccessPolicy.isAdmin(CALLER)).thenReturn(true);

		// When-Then: "@" first means there is nobody to grant.
		assertThatThrownBy(() -> service.addAdmin(CALLER, "@aidigital.com"))
				.isInstanceOf(AppException.class)
				.hasFieldOrPropertyWithValue("code", ErrorReason.C002.getCode());
		verifyNoInteractions(adminUserService);
	}

	@Test
	void shouldRejectAnEmailWithoutAtSignTest() {
		// Given:
		when(adminAccessPolicy.isAdmin(CALLER)).thenReturn(true);

		// When-Then:
		assertThatThrownBy(() -> service.addAdmin(CALLER, "admin.aidigital.com"))
				.isInstanceOf(AppException.class)
				.hasFieldOrPropertyWithValue("code", ErrorReason.C002.getCode());
		verifyNoInteractions(adminUserService);
	}

	@Test
	void shouldRejectAnEmailEndingInAtSignTest() {
		// Given:
		when(adminAccessPolicy.isAdmin(CALLER)).thenReturn(true);

		// When-Then:
		assertThatThrownBy(() -> service.addAdmin(CALLER, "admin@"))
				.isInstanceOf(AppException.class)
				.hasFieldOrPropertyWithValue("code", ErrorReason.C002.getCode());
		verifyNoInteractions(adminUserService);
	}

	@Test
	void shouldRejectAnEmailWhoseDomainHasNoDotTest() {
		// Given:
		when(adminAccessPolicy.isAdmin(CALLER)).thenReturn(true);

		// When-Then:
		assertThatThrownBy(() -> service.addAdmin(CALLER, "admin@localhost"))
				.isInstanceOf(AppException.class)
				.hasFieldOrPropertyWithValue("code", ErrorReason.C002.getCode());
		verifyNoInteractions(adminUserService);
	}

	@Test
	void shouldRejectAMissingEmailWhenRemovingTest() {
		// Given:
		when(adminAccessPolicy.isAdmin(CALLER)).thenReturn(true);

		// When-Then:
		assertThatThrownBy(() -> service.removeAdmin(CALLER, null))
				.isInstanceOf(AppException.class)
				.hasFieldOrPropertyWithValue("code", ErrorReason.C002.getCode());
		verifyNoInteractions(adminUserService);
	}

	@Test
	void shouldRejectABlankEmailWhenRemovingTest() {
		// Given:
		when(adminAccessPolicy.isAdmin(CALLER)).thenReturn(true);

		// When-Then:
		assertThatThrownBy(() -> service.removeAdmin(CALLER, " "))
				.isInstanceOf(AppException.class)
				.hasFieldOrPropertyWithValue("code", ErrorReason.C002.getCode());
		verifyNoInteractions(adminUserService);
	}

	@Test
	void shouldRefuseToRemoveARootAdminTest() {
		// Given: the address is on the config allow-list.
		when(adminAccessPolicy.isAdmin(CALLER)).thenReturn(true);
		when(adminAccessPolicy.isConfigAdmin("root@aidigital.com")).thenReturn(true);

		// When-Then: root admins cannot be revoked through the UI.
		assertThatThrownBy(() -> service.removeAdmin(CALLER, " Root@AIDigital.com "))
				.isInstanceOf(AppException.class)
				.hasFieldOrPropertyWithValue("code", ErrorReason.C002.getCode());
		verifyNoInteractions(adminUserService);
	}

	@Test
	void shouldReportAnUnknownManagedAdminTest() {
		// Given: no managed grant exists for the address.
		when(adminAccessPolicy.isAdmin(CALLER)).thenReturn(true);
		when(adminAccessPolicy.isConfigAdmin("ghost@aidigital.com")).thenReturn(false);
		when(adminUserService.removeByEmail("ghost@aidigital.com")).thenReturn(false);

		// When-Then:
		assertThatThrownBy(() -> service.removeAdmin(CALLER, "Ghost@AIDigital.com"))
				.isInstanceOf(AppException.class)
				.hasFieldOrPropertyWithValue("code", ErrorReason.C001.getCode());
	}

	@Test
	void shouldRemoveAManagedAdminAndReturnTheRemainingListTest() {
		// Given: a managed grant that is removed successfully.
		when(adminAccessPolicy.isAdmin(CALLER)).thenReturn(true);
		when(adminAccessPolicy.isConfigAdmin("managed@aidigital.com")).thenReturn(false);
		when(adminUserService.removeByEmail("managed@aidigital.com")).thenReturn(true);
		when(adminAccessPolicy.configAdminEmails()).thenReturn(List.of("root@aidigital.com"));
		when(adminUserService.listAll()).thenReturn(List.of());

		// When:
		AdminList result = service.removeAdmin(CALLER, "Managed@AIDigital.com");

		// Then: only the root admin remains.
		assertThat(result.admins()).containsExactly(
				new AdminEntry("root@aidigital.com", "config", null, null, false));
	}
}
