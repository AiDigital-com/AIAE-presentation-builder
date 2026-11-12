package com.aidigital.reportconstructor.externalservices.clerk.impl;

import com.aidigital.reportconstructor.externalservices.clerk.ClerkExternalException;
import com.aidigital.reportconstructor.externalservices.clerk.config.ClerkProperties;
import com.aidigital.reportconstructor.externalservices.clerk.model.ClerkOAuthAccessToken;
import com.aidigital.reportconstructor.externalservices.common.http.PooledRestClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClerkClientImplTest {

	@Test
	void shouldReturnTheTokensFromABareArrayResponseTest() {
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
		ClerkClientImpl client = new ClerkClientImpl(props, factory);

		// When:
		List<ClerkOAuthAccessToken> tokens = client.googleOAuthAccessTokens("user_1");

		// Then:
		assertThat(tokens).containsExactly(new ClerkOAuthAccessToken("ya29.token"));
		server.verify();
	}

	@Test
	void shouldReturnTheTokensFromADataEnvelopeTest() {
		// Given: Clerk answers with the paginated {"data": [...]} shape
		ClerkProperties props = new ClerkProperties();
		props.setSecretKey("sk_test");
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.clerk.com/v1");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://api.clerk.com/v1/users/user_4/oauth_access_tokens/oauth_google"))
				.andRespond(withSuccess("{\"data\": [{\"token\": \"ya29.a\"}, {}]}", MediaType.APPLICATION_JSON));
		PooledRestClientFactory factory = mock(PooledRestClientFactory.class);
		when(factory.createClient("clerk", "https://api.clerk.com/v1", Duration.ofSeconds(15)))
				.thenReturn(builder.build());
		ClerkClientImpl client = new ClerkClientImpl(props, factory);

		// When:
		List<ClerkOAuthAccessToken> tokens = client.googleOAuthAccessTokens("user_4");

		// Then:
		assertThat(tokens).containsExactly(new ClerkOAuthAccessToken("ya29.a"), new ClerkOAuthAccessToken(null));
	}

	@Test
	void shouldReturnAnEmptyListWhenTheResponseCarriesNoTokenTest() {
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
		ClerkClientImpl client = new ClerkClientImpl(props, factory);

		// When:
		List<ClerkOAuthAccessToken> tokens = client.googleOAuthAccessTokens("user_3");

		// Then:
		assertThat(tokens).isEmpty();
	}

	@Test
	void shouldThrowClerkExternalExceptionWhenClerkRejectsTheRequestTest() {
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
		ClerkClientImpl client = new ClerkClientImpl(props, factory);

		// When-Then:
		assertThatThrownBy(() -> client.googleOAuthAccessTokens("user_2"))
				.isInstanceOf(ClerkExternalException.class)
				.hasMessageContaining("404");
		server.verify();
	}

	@Test
	void shouldThrowClerkExternalExceptionWhenTheBodyIsNotJsonTest() {
		// Given: Clerk answers 200 with a non-JSON body
		ClerkProperties props = new ClerkProperties();
		props.setSecretKey("sk_test");
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.clerk.com/v1");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://api.clerk.com/v1/users/user_5/oauth_access_tokens/oauth_google"))
				.andRespond(withSuccess("<html>", MediaType.TEXT_HTML));
		PooledRestClientFactory factory = mock(PooledRestClientFactory.class);
		when(factory.createClient("clerk", "https://api.clerk.com/v1", Duration.ofSeconds(15)))
				.thenReturn(builder.build());
		ClerkClientImpl client = new ClerkClientImpl(props, factory);

		// When-Then:
		assertThatThrownBy(() -> client.googleOAuthAccessTokens("user_5"))
				.isInstanceOf(ClerkExternalException.class);
	}
}
