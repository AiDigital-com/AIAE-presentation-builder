package com.aidigital.reportconstructor.service.admin.services.impl;

import com.aidigital.reportconstructor.domain.admin.entities.AdminUserEntity;
import com.aidigital.reportconstructor.service.admin.AdminAccessPolicy;
import com.aidigital.reportconstructor.service.admin.AdminUserService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminEmailDirectoryImplTest {

	@Test
	void shouldReturnConfiguredAdminsFollowedByManagedAdminsTest() {
		// Given:
		AdminAccessPolicy accessPolicy = mock(AdminAccessPolicy.class);
		AdminUserService adminUserService = mock(AdminUserService.class);
		AdminUserEntity managed = new AdminUserEntity();
		managed.setEmail("managed@x.com");
		when(accessPolicy.configAdminEmails()).thenReturn(List.of("root@x.com"));
		when(adminUserService.listAll()).thenReturn(List.of(managed));
		AdminEmailDirectoryImpl directory = new AdminEmailDirectoryImpl(accessPolicy, adminUserService);

		// When:
		List<String> emails = directory.adminEmails();

		// Then:
		assertThat(emails).containsExactly("root@x.com", "managed@x.com");
	}

	@Test
	void shouldStillReturnConfiguredAdminsWhenManagedAdminLookupFailsTest() {
		// Given:
		AdminAccessPolicy accessPolicy = mock(AdminAccessPolicy.class);
		AdminUserService adminUserService = mock(AdminUserService.class);
		when(accessPolicy.configAdminEmails()).thenReturn(List.of("root@x.com"));
		when(adminUserService.listAll()).thenThrow(new IllegalStateException("db down"));
		AdminEmailDirectoryImpl directory = new AdminEmailDirectoryImpl(accessPolicy, adminUserService);

		// When:
		List<String> emails = directory.adminEmails();

		// Then:
		assertThat(emails).containsExactly("root@x.com");
	}
}
