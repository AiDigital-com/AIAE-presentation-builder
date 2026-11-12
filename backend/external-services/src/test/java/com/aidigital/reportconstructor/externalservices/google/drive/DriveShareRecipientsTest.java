package com.aidigital.reportconstructor.externalservices.google.drive;

import com.aidigital.reportconstructor.externalservices.google.GoogleProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DriveShareRecipientsTest {

	private final GoogleProperties props = new GoogleProperties();
	private final DriveShareAudience shareAudience = mock(DriveShareAudience.class);
	private final DriveShareRecipients recipients = new DriveShareRecipients(props, shareAudience);

	@Test
	void shouldMergeConfiguredEmailsWithTheAudienceTest() {
		// Given:
		props.setShareWithEmails(List.of("owner@x.com", " "));
		when(shareAudience.additionalRecipients()).thenReturn(List.of("root@x.com", "managed@x.com"));

		// When:
		List<String> resolved = recipients.resolve();

		// Then:
		assertThat(resolved).containsExactly("owner@x.com", "root@x.com", "managed@x.com");
	}

	@Test
	void shouldDeduplicateCaseInsensitivelyTest() {
		// Given: the same address configured as a share recipient and as an admin
		props.setShareWithEmails(List.of("Owner@X.com"));
		when(shareAudience.additionalRecipients()).thenReturn(List.of("owner@x.com", "OWNER@x.com"));

		// When:
		List<String> resolved = recipients.resolve();

		// Then:
		assertThat(resolved).containsExactly("owner@x.com");
	}

	@Test
	void shouldReturnConfiguredEmailsWhenTheAudienceIsEmptyTest() {
		// Given:
		props.setShareWithEmails(List.of("owner@x.com"));
		when(shareAudience.additionalRecipients()).thenReturn(List.of());

		// When:
		List<String> resolved = recipients.resolve();

		// Then:
		assertThat(resolved).containsExactly("owner@x.com");
	}
}
