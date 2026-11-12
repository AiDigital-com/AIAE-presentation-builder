package com.aidigital.reportconstructor.externalservices.clerk.impl;

import com.aidigital.reportconstructor.externalservices.clerk.ClerkClient;
import com.aidigital.reportconstructor.externalservices.clerk.ClerkExternalException;
import com.aidigital.reportconstructor.externalservices.clerk.config.ClerkProperties;
import com.aidigital.reportconstructor.externalservices.clerk.model.ClerkOAuthAccessToken;
import com.aidigital.reportconstructor.externalservices.common.http.PooledRestClientFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Production {@link ClerkClient} over the Clerk Backend API, using a pooled, instrumented and logged
 * HTTP client with a 15-second response timeout.
 */
public class ClerkClientImpl implements ClerkClient {

	/** Low-cardinality client name used for metrics and pooled-client diagnostics. */
	static final String CLIENT_NAME = "clerk";
	static final String API_BASE = "https://api.clerk.com/v1";
	static final String TOKEN_PATH = "/users/{userId}/oauth_access_tokens/{provider}";
	static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(15);
	private static final String BEARER_PREFIX = "Bearer ";
	private static final String DATA_FIELD = "data";
	private static final String TOKEN_FIELD = "token";

	private final String secretKey;
	private final String provider;
	private final RestClient http;
	private final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Creates the client from the Clerk configuration and the shared HTTP client factory.
	 *
	 * @param props       Clerk configuration supplying the secret key and OAuth provider id
	 * @param httpClients shared factory building pooled, instrumented and logged HTTP clients
	 */
	public ClerkClientImpl(ClerkProperties props, PooledRestClientFactory httpClients) {
		this.secretKey = props.getSecretKey();
		this.provider = props.getGoogleProvider();
		this.http = httpClients.createClient(CLIENT_NAME, API_BASE, RESPONSE_TIMEOUT);
	}

	@Override
	public List<ClerkOAuthAccessToken> googleOAuthAccessTokens(String clerkUserId) {
		ResponseEntity<String> resp;
		try {
			resp = fetchTokens(clerkUserId);
		} catch (RestClientException ex) {
			throw new ClerkExternalException("Clerk token request failed: " + ex.getMessage(), ex);
		}
		if (!resp.getStatusCode().is2xxSuccessful()) {
			throw new ClerkExternalException("Clerk token request returned HTTP " + resp.getStatusCode().value());
		}
		return parseTokens(resp.getBody());
	}

	/**
	 * Calls the Clerk OAuth-access-token endpoint and returns its status and body without throwing on a
	 * non-2xx status; transport failures propagate as exceptions.
	 *
	 * @param clerkUserId Clerk user whose provider tokens are read
	 * @return response status and body text
	 */
	ResponseEntity<String> fetchTokens(String clerkUserId) {
		return http.get()
				.uri(TOKEN_PATH, clerkUserId, provider)
				.header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + secretKey)
				.accept(MediaType.APPLICATION_JSON)
				.exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
						.body(StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8)));
	}

	/**
	 * Parses the token list. Clerk answers either a bare JSON array or an object with a {@code data}
	 * array; anything else yields an empty list.
	 *
	 * @param body response body text
	 * @return parsed tokens in response order
	 * @throws ClerkExternalException when the body is not valid JSON
	 */
	List<ClerkOAuthAccessToken> parseTokens(String body) {
		JsonNode root;
		try {
			root = mapper.readTree(body == null ? "" : body);
		} catch (JsonProcessingException ex) {
			throw new ClerkExternalException("Clerk token response could not be parsed", ex);
		}
		JsonNode arr = root.isArray() ? root : root.path(DATA_FIELD);
		List<ClerkOAuthAccessToken> tokens = new ArrayList<>();
		if (arr.isArray()) {
			for (JsonNode node : arr) {
				tokens.add(new ClerkOAuthAccessToken(node.path(TOKEN_FIELD).asText(null)));
			}
		}
		return tokens;
	}
}
