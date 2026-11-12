package com.aidigital.reportconstructor.externalservices.bridge;

import com.aidigital.reportconstructor.externalservices.clerk.ClerkClient;
import com.aidigital.reportconstructor.externalservices.clerk.ClerkExternalException;
import com.aidigital.reportconstructor.externalservices.clerk.model.ClerkOAuthAccessToken;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ClerkUserGoogleTokenProviderTest {

	@Test
	void shouldReturnTheFirstTokenTest() {
		// Given:
		ClerkClient clerkClient = mock(ClerkClient.class);
		when(clerkClient.googleOAuthAccessTokens("user_1"))
				.thenReturn(List.of(new ClerkOAuthAccessToken("ya29.first"), new ClerkOAuthAccessToken("ya29.second")));
		ClerkUserGoogleTokenProvider provider = new ClerkUserGoogleTokenProvider(clerkClient);

		// When:
		String token = provider.googleAccessToken("user_1");

		// Then:
		assertThat(token).isEqualTo("ya29.first");
	}

	@Test
	void shouldReturnNullWhenClerkHasNoTokenTest() {
		// Given:
		ClerkClient clerkClient = mock(ClerkClient.class);
		when(clerkClient.googleOAuthAccessTokens("user_3")).thenReturn(List.of());
		ClerkUserGoogleTokenProvider provider = new ClerkUserGoogleTokenProvider(clerkClient);

		// When:
		String token = provider.googleAccessToken("user_3");

		// Then:
		assertThat(token).isNull();
	}

	@Test
	void shouldReturnNullWhenTheFirstTokenIsBlankTest() {
		// Given:
		ClerkClient clerkClient = mock(ClerkClient.class);
		when(clerkClient.googleOAuthAccessTokens("user_6")).thenReturn(List.of(new ClerkOAuthAccessToken(" ")));
		ClerkUserGoogleTokenProvider provider = new ClerkUserGoogleTokenProvider(clerkClient);

		// When:
		String token = provider.googleAccessToken("user_6");

		// Then:
		assertThat(token).isNull();
	}

	@Test
	void shouldReturnNullWhenTheClerkCallFailsTest() {
		// Given:
		ClerkClient clerkClient = mock(ClerkClient.class);
		when(clerkClient.googleOAuthAccessTokens("user_2"))
				.thenThrow(new ClerkExternalException("Clerk token request returned HTTP 404"));
		ClerkUserGoogleTokenProvider provider = new ClerkUserGoogleTokenProvider(clerkClient);

		// When:
		String token = provider.googleAccessToken("user_2");

		// Then:
		assertThat(token).isNull();
	}

	@Test
	void shouldSkipTheCallForABlankUserIdTest() {
		// Given:
		ClerkClient clerkClient = mock(ClerkClient.class);
		ClerkUserGoogleTokenProvider provider = new ClerkUserGoogleTokenProvider(clerkClient);

		// When:
		String token = provider.googleAccessToken(" ");

		// Then:
		assertThat(token).isNull();
		verifyNoInteractions(clerkClient);
	}
}
