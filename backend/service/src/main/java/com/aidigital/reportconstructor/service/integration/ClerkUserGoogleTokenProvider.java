package com.aidigital.reportconstructor.service.integration;

import com.aidigital.reportconstructor.externalservices.clerk.ClerkClient;
import com.aidigital.reportconstructor.externalservices.clerk.ClerkExternalException;
import com.aidigital.reportconstructor.externalservices.clerk.config.ClerkConfig;
import com.aidigital.reportconstructor.externalservices.clerk.model.ClerkOAuthAccessToken;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implements the service's {@link UserGoogleTokenProvider} port on top of the provider-level
 * {@link ClerkClient}. Every failure is non-fatal: it is logged and answered with {@code null}, which
 * makes the caller fall back to the Google service account.
 *
 * <p>Bridges are the only external-services classes allowed to import {@code service} types; they
 * move into {@code service} once every provider is leaf-shaped and the module edge is flipped (D6).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression(ClerkConfig.SECRET_KEY_PRESENT)
public class ClerkUserGoogleTokenProvider implements UserGoogleTokenProvider {

	private final ClerkClient clerkClient;

	@Override
	public String googleAccessToken(String clerkUserId) {
		if (clerkUserId == null || clerkUserId.isBlank()) {
			return null;
		}
		List<ClerkOAuthAccessToken> tokens;
		try {
			tokens = clerkClient.googleOAuthAccessTokens(clerkUserId);
		} catch (ClerkExternalException ex) {
			log.warn("[clerk] Google token fetch failed for user {}: {}", clerkUserId, ex.getMessage());
			return null;
		}
		if (!tokens.isEmpty()) {
			String token = tokens.get(0).token();
			if (token != null && !token.isBlank()) {
				return token;
			}
		}
		log.warn("[clerk] no Google OAuth token present for user {}", clerkUserId);
		return null;
	}
}
