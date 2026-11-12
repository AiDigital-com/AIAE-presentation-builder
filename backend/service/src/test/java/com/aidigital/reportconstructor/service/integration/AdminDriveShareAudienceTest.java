package com.aidigital.reportconstructor.service.integration;

import com.aidigital.reportconstructor.service.admin.services.AdminEmailDirectory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminDriveShareAudienceTest {

	@Test
	void shouldReturnTheCurrentAdminEmailsTest() {
		// Given:
		AdminEmailDirectory adminEmailDirectory = mock(AdminEmailDirectory.class);
		when(adminEmailDirectory.adminEmails()).thenReturn(List.of("root@x.com", "managed@x.com"));
		AdminDriveShareAudience audience = new AdminDriveShareAudience(adminEmailDirectory);

		// When:
		List<String> recipients = audience.additionalRecipients();

		// Then:
		assertThat(recipients).containsExactly("root@x.com", "managed@x.com");
	}
}
