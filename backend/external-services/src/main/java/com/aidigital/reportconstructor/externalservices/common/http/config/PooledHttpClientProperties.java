package com.aidigital.reportconstructor.externalservices.common.http.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Runtime-tunable limits for every pooled third-party HTTP client built by
 * {@link com.aidigital.reportconstructor.externalservices.common.http.PooledRestClientFactory}.
 * Binds {@code app.external.http.*}; defaults (with comments) live in {@code application.yml}. Each client
 * gets its own pool sized by these values; a client may override only the response timeout.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.external.http")
public class PooledHttpClientProperties {

	/** Maximum time to establish a TCP connection. */
	@NotNull
	private Duration connectTimeout = Duration.ofSeconds(5);

	/** Default maximum wait for response data after the request is sent. */
	@NotNull
	private Duration responseTimeout = Duration.ofSeconds(30);

	/** Maximum time to wait for a connection lease from the pool. */
	@NotNull
	private Duration connectionRequestTimeout = Duration.ofSeconds(5);

	/** Maximum number of connections across all routes of one client. */
	@Positive
	private int maxTotalConnections = 50;

	/** Maximum number of connections per route (host) of one client. */
	@Positive
	private int maxConnectionsPerRoute = 10;

	/** Maximum lifetime of a pooled connection. */
	@NotNull
	private Duration keepAliveDuration = Duration.ofSeconds(60);

	/** Idle time after which connections are evicted and re-validated before reuse. */
	@NotNull
	private Duration idleEvictionDuration = Duration.ofSeconds(30);
}
