package com.aidigital.reportconstructor.service.integration;

import com.aidigital.reportconstructor.externalservices.clerk.config.ClerkConfig;
import com.aidigital.reportconstructor.externalservices.common.http.PooledRestClientFactory;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ClerkUserGoogleTokenProviderWiringTest {

	@Test
	void shouldProvideUserGoogleTokensWhenAClerkSecretKeyIsSetTest() {
		// Given:
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withBean(PooledRestClientFactory.class, () -> mock(PooledRestClientFactory.class))
				.withUserConfiguration(ClerkConfig.class, ClerkUserGoogleTokenProvider.class)
				.withPropertyValues("app.clerk.secret-key=sk_test");

		// When-Then:
		runner.run(context -> assertThat(context).hasSingleBean(UserGoogleTokenProvider.class));
	}

	@Test
	void shouldProvideNoUserGoogleTokensWithoutAClerkSecretKeyTest() {
		// Given: no key, so callers fall back to the Google service account
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withBean(PooledRestClientFactory.class, () -> mock(PooledRestClientFactory.class))
				.withUserConfiguration(ClerkConfig.class, ClerkUserGoogleTokenProvider.class);

		// When-Then:
		runner.run(context -> assertThat(context).doesNotHaveBean(UserGoogleTokenProvider.class));
	}
}
