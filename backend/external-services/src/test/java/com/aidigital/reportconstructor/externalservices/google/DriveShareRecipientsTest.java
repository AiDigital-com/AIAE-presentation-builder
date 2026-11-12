package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.admin.services.AdminEmailDirectory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DriveShareRecipientsTest {

	private final GoogleProperties props = new GoogleProperties();
	private final AdminEmailDirectory adminEmailDirectory = mock(AdminEmailDirectory.class);
	private final DriveShareRecipients recipients = new DriveShareRecipients(props, adminEmailDirectory);

	@Test
	void shouldMergeConfiguredEmailsWithAdminEmailsTest() {
		// Given:
		props.setShareWithEmails(List.of("owner@x.com", " "));
		when(adminEmailDirectory.adminEmails()).thenReturn(List.of("root@x.com", "managed@x.com"));

		// When:
		List<String> resolved = recipients.resolve();

		// Then:
		assertThat(resolved).containsExactly("owner@x.com", "root@x.com", "managed@x.com");
	}

	@Test
	void shouldDeduplicateCaseInsensitivelyTest() {
		// Given: the same address configured as a share recipient and as an admin
		props.setShareWithEmails(List.of("Owner@X.com"));
		when(adminEmailDirectory.adminEmails()).thenReturn(List.of("owner@x.com", "OWNER@x.com"));

		// When:
		List<String> resolved = recipients.resolve();

		// Then:
		assertThat(resolved).containsExactly("owner@x.com");
	}

	@Test
	void shouldReturnConfiguredEmailsWhenThereAreNoAdminsTest() {
		// Given:
		props.setShareWithEmails(List.of("owner@x.com"));
		when(adminEmailDirectory.adminEmails()).thenReturn(List.of());

		// When:
		List<String> resolved = recipients.resolve();

		// Then:
		assertThat(resolved).containsExactly("owner@x.com");
	}
}
