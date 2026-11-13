package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.service.admin.config.AdminProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccessPolicyTest {

	@Mock
	private AdminUserService adminUserService;

	@Test
	void shouldNeverTreatAMissingEmailAsAdminTest() {
		// Given:
		AdminProperties properties = new AdminProperties();
		properties.setEmails(List.of("root@aidigital.com"));
		AdminAccessPolicy policy = new AdminAccessPolicy(properties, adminUserService);

		// When:
		boolean nullIsAdmin = policy.isAdmin(null);
		boolean blankIsAdmin = policy.isAdmin("  ");

		// Then: neither reaches the managed-grant lookup.
		assertThat(nullIsAdmin).isFalse();
		assertThat(blankIsAdmin).isFalse();
		verifyNoInteractions(adminUserService);
	}

	@Test
	void shouldRecognizeAConfigAdminInAnyCaseWithoutALookupTest() {
		// Given: the allow-list carries blank and null entries alongside a padded, mixed-case address.
		AdminProperties properties = new AdminProperties();
		properties.setEmails(Arrays.asList(null, " ", " Root@AIDigital.com "));
		AdminAccessPolicy policy = new AdminAccessPolicy(properties, adminUserService);

		// When:
		boolean admin = policy.isAdmin("ROOT@aidigital.com");

		// Then:
		assertThat(admin).isTrue();
		verifyNoInteractions(adminUserService);
	}

	@Test
	void shouldFallBackToAManagedGrantTest() {
		// Given: an address not on the allow-list but granted through the UI.
		AdminProperties properties = new AdminProperties();
		properties.setEmails(List.of("root@aidigital.com"));
		when(adminUserService.exists("managed@aidigital.com")).thenReturn(true);
		AdminAccessPolicy policy = new AdminAccessPolicy(properties, adminUserService);

		// When:
		boolean admin = policy.isAdmin("managed@aidigital.com");

		// Then:
		assertThat(admin).isTrue();
		verify(adminUserService).exists("managed@aidigital.com");
	}

	@Test
	void shouldRejectAnAddressWithNeitherConfigNorManagedGrantTest() {
		// Given:
		AdminProperties properties = new AdminProperties();
		properties.setEmails(List.of("root@aidigital.com"));
		when(adminUserService.exists("user@aidigital.com")).thenReturn(false);
		AdminAccessPolicy policy = new AdminAccessPolicy(properties, adminUserService);

		// When:
		boolean admin = policy.isAdmin("user@aidigital.com");

		// Then:
		assertThat(admin).isFalse();
	}

	@Test
	void shouldNotTreatAMissingEmailAsConfigAdminTest() {
		// Given:
		AdminProperties properties = new AdminProperties();
		properties.setEmails(List.of("root@aidigital.com"));
		AdminAccessPolicy policy = new AdminAccessPolicy(properties, adminUserService);

		// When:
		boolean nullIsRoot = policy.isConfigAdmin(null);
		boolean blankIsRoot = policy.isConfigAdmin("");

		// Then:
		assertThat(nullIsRoot).isFalse();
		assertThat(blankIsRoot).isFalse();
	}

	@Test
	void shouldListNormalizedDistinctConfigEmailsSkippingBlanksTest() {
		// Given: duplicates differing only by case and padding, plus null and blank entries.
		AdminProperties properties = new AdminProperties();
		properties.setEmails(Arrays.asList(
				" Root@AIDigital.com", null, "", "root@aidigital.com ", "Second@AIDigital.com"));
		AdminAccessPolicy policy = new AdminAccessPolicy(properties, adminUserService);

		// When:
		List<String> emails = policy.configAdminEmails();

		// Then:
		assertThat(emails).containsExactly("root@aidigital.com", "second@aidigital.com");
	}
}
