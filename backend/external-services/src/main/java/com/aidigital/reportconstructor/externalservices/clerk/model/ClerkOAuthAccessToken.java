package com.aidigital.reportconstructor.externalservices.clerk.model;

/**
 * One OAuth access token entry returned by Clerk's {@code oauth_access_tokens} endpoint.
 *
 * @param token the provider access token; may be {@code null} when Clerk returns none
 */
public record ClerkOAuthAccessToken(String token) {
}
