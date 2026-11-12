package com.aidigital.reportconstructor.externalservices.clerk;

import com.aidigital.reportconstructor.externalservices.clerk.model.ClerkOAuthAccessToken;

import java.util.List;

/**
 * Narrow interface over the Clerk Backend API. Callers pass Clerk ids; the implementation handles
 * authentication, the HTTP call and response parsing. Vendor types never cross this interface.
 *
 * <p>Non-2xx responses, transport failures and unparseable bodies are raised as
 * {@link ClerkExternalException}.
 */
public interface ClerkClient {

	/**
	 * Lists the OAuth access tokens Clerk holds for a user and the configured Google provider
	 * ({@code app.clerk.google-provider}).
	 *
	 * @param clerkUserId Clerk user id ({@code sub} claim); must not be blank
	 * @return tokens in Clerk's order; empty when the user has none
	 * @throws ClerkExternalException on any HTTP, transport or parse failure
	 */
	List<ClerkOAuthAccessToken> googleOAuthAccessTokens(String clerkUserId);
}
