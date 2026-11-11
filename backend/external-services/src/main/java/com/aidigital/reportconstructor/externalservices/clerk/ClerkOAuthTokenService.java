package com.aidigital.reportconstructor.externalservices.clerk;

import com.aidigital.reportconstructor.externalservices.common.http.PooledRestClientFactory;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Fetches a signed-in user's Google OAuth access token from the Clerk Backend
 * API. Used so source spreadsheets can be read with the caller's own Google
 * account (as well as so generated decks can be created in the user's Drive).
 */
@Slf4j
@Component
@ConditionalOnExpression("'${app.clerk.secret-key:}' != ''")
public class ClerkOAuthTokenService implements UserGoogleTokenProvider {

	/** Low-cardinality client name used for metrics and pooled-client diagnostics. */
	static final String CLIENT_NAME = "clerk";
	static final String API_BASE = "https://api.clerk.com/v1";
	static final String TOKEN_PATH = "/users/{userId}/oauth_access_tokens/{provider}";
	static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(15);
	private static final String BEARER_PREFIX = "Bearer ";

	private final String secretKey;
	private final String provider;
	private final RestClient http;
	private final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Creates the service with a pooled, instrumented Clerk client using a 15-second response timeout.
	 *
	 * @param props       Clerk configuration supplying the secret key and OAuth provider id
	 * @param httpClients shared factory building pooled, instrumented and logged HTTP clients
	 */
	public ClerkOAuthTokenService(ClerkProperties props, PooledRestClientFactory httpClients) {
		this.secretKey = props.getSecretKey();
		this.provider = props.getGoogleProvider();
		this.http = httpClients.createClient(CLIENT_NAME, API_BASE, RESPONSE_TIMEOUT);
	}

	@Override
	public String googleAccessToken(String clerkUserId) {
		if (clerkUserId == null || clerkUserId.isBlank()) {
			return null;
		}
		try {
			ResponseEntity<String> resp = fetchTokens(clerkUserId);
			if (!resp.getStatusCode().is2xxSuccessful()) {
				log.warn("[clerk] Google token fetch for user {} returned HTTP {}",
						clerkUserId, resp.getStatusCode().value());
				return null;
			}
			JsonNode root = mapper.readTree(resp.getBody());
			JsonNode arr = root.isArray() ? root : root.path("data");
			if (arr.isArray() && !arr.isEmpty()) {
				String token = arr.get(0).path("token").asText(null);
				if (token != null && !token.isBlank()) {
					return token;
				}
			}
			log.warn("[clerk] no Google OAuth token present for user {}", clerkUserId);
			return null;
		} catch (Exception ex) {
			log.warn("[clerk] Google token fetch failed for user {}: {}",
					clerkUserId, ex.getMessage());
			return null;
		}
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
}
