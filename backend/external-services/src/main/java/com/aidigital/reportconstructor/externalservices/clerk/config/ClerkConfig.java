package com.aidigital.reportconstructor.externalservices.clerk.config;

import com.aidigital.reportconstructor.externalservices.clerk.ClerkClient;
import com.aidigital.reportconstructor.externalservices.clerk.impl.ClerkClientImpl;
import com.aidigital.reportconstructor.externalservices.common.http.PooledRestClientFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the Clerk properties binding and, when a secret key is configured, the Clerk client.
 * Without a key there is no client: callers fall back to the Google service account.
 */
@Configuration
@EnableConfigurationProperties(ClerkProperties.class)
public class ClerkConfig {

	/** Spring expression that is true when {@code app.clerk.secret-key} is set. */
	public static final String SECRET_KEY_PRESENT = "'${app.clerk.secret-key:}' != ''";

	/**
	 * Live Clerk client, created only when a secret key is configured.
	 *
	 * @param props       Clerk configuration
	 * @param httpClients shared pooled HTTP client factory
	 * @return configured client
	 */
	@Bean
	@ConditionalOnExpression(SECRET_KEY_PRESENT)
	public ClerkClient clerkClient(ClerkProperties props, PooledRestClientFactory httpClients) {
		return new ClerkClientImpl(props, httpClients);
	}
}
