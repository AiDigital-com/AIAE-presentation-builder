package com.aidigital.reportconstructor.externalservices.clerk;

import com.aidigital.reportconstructor.externalservices.common.http.PooledRestClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClerkOAuthTokenServiceTest {

	@Test
	void shouldReturnTheFirstGoogleTokenFromTheClerkResponseTest() {
		// Given: Clerk answers with one Google OAuth token
		ClerkProperties props = new ClerkProperties();
		props.setSecretKey("sk_test");
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.clerk.com/v1");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://api.clerk.com/v1/users/user_1/oauth_access_tokens/oauth_google"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Authorization", "Bearer sk_test"))
				.andRespond(withSuccess("[{\"token\": \"ya29.token\"}]", MediaType.APPLICATION_JSON));
		PooledRestClientFactory factory = mock(PooledRestClientFactory.class);
		when(factory.createClient("clerk", "https://api.clerk.com/v1", Duration.ofSeconds(15)))
				.thenReturn(builder.build());
		ClerkOAuthTokenService service = new ClerkOAuthTokenService(props, factory);

		// When:
		String token = service.googleAccessToken("user_1");

		// Then:
		assertThat(token).isEqualTo("ya29.token");
		server.verify();
	}

	@Test
	void shouldReturnNullWhenClerkRejectsTheRequestTest() {
		// Given: Clerk answers 404 for an unknown user
		ClerkProperties props = new ClerkProperties();
		props.setSecretKey("sk_test");
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.clerk.com/v1");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://api.clerk.com/v1/users/user_2/oauth_access_tokens/oauth_google"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));
		PooledRestClientFactory factory = mock(PooledRestClientFactory.class);
		when(factory.createClient("clerk", "https://api.clerk.com/v1", Duration.ofSeconds(15)))
				.thenReturn(builder.build());
		ClerkOAuthTokenService service = new ClerkOAuthTokenService(props, factory);

		// When:
		String token = service.googleAccessToken("user_2");

		// Then:
		assertThat(token).isNull();
		server.verify();
	}

	@Test
	void shouldReturnNullWhenTheResponseCarriesNoTokenTest() {
		// Given: Clerk answers 200 with an empty data array
		ClerkProperties props = new ClerkProperties();
		props.setSecretKey("sk_test");
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.clerk.com/v1");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://api.clerk.com/v1/users/user_3/oauth_access_tokens/oauth_google"))
				.andRespond(withSuccess("{\"data\": []}", MediaType.APPLICATION_JSON));
		PooledRestClientFactory factory = mock(PooledRestClientFactory.class);
		when(factory.createClient("clerk", "https://api.clerk.com/v1", Duration.ofSeconds(15)))
				.thenReturn(builder.build());
		ClerkOAuthTokenService service = new ClerkOAuthTokenService(props, factory);

		// When:
		String token = service.googleAccessToken("user_3");

		// Then:
		assertThat(token).isNull();
	}

	@Test
	void shouldSkipTheCallForABlankUserIdTest() {
		// Given:
		ClerkProperties props = new ClerkProperties();
		props.setSecretKey("sk_test");
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.clerk.com/v1");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		PooledRestClientFactory factory = mock(PooledRestClientFactory.class);
		when(factory.createClient("clerk", "https://api.clerk.com/v1", Duration.ofSeconds(15)))
				.thenReturn(builder.build());
		ClerkOAuthTokenService service = new ClerkOAuthTokenService(props, factory);

		// When:
		String token = service.googleAccessToken(" ");

		// Then: no request was sent
		assertThat(token).isNull();
		server.verify();
	}
}
