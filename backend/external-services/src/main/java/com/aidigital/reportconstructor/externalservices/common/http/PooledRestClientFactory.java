package com.aidigital.reportconstructor.externalservices.common.http;

import com.aidigital.reportconstructor.externalservices.common.http.config.PooledHttpClientProperties;
import com.aidigital.reportconstructor.observability.external.ExternalClientMetricsInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds every third-party {@link RestClient}: a dedicated, bounded Apache HttpClient 5 pool with explicit
 * connect, response and pool-lease timeouts, plus {@link ExternalClientMetricsInterceptor} (metrics) and
 * {@link LogbookClientHttpRequestInterceptor} (masked, metadata-only logging). All pools are closed on
 * shutdown.
 */
@Component
@RequiredArgsConstructor
public class PooledRestClientFactory {

	private final Logbook logbook;
	private final MeterRegistry meterRegistry;
	private final PooledHttpClientProperties properties;
	private final List<ManagedPooledRestClient> managedClients = new ArrayList<>();

	/**
	 * Creates a client using the configured default response timeout.
	 *
	 * @param name    low-cardinality client name, used as the metrics {@code client} tag
	 * @param baseUrl base URL for every request of this client
	 * @return pooled, instrumented client
	 */
	public RestClient createClient(String name, String baseUrl) {
		return createClient(name, baseUrl, properties.getResponseTimeout());
	}

	/**
	 * Creates a client whose response timeout differs from the configured default, for integrations whose
	 * calls legitimately take longer (or must fail faster) than the shared default.
	 *
	 * @param name            low-cardinality client name, used as the metrics {@code client} tag
	 * @param baseUrl         base URL for every request of this client
	 * @param responseTimeout maximum wait for response data after the request is sent
	 * @return pooled, instrumented client
	 */
	public synchronized RestClient createClient(String name, String baseUrl, Duration responseTimeout) {
		PoolingHttpClientConnectionManager connectionManager = buildConnectionManager();
		CloseableHttpClient httpClient = HttpClients.custom()
				.setConnectionManager(connectionManager)
				.setDefaultRequestConfig(buildRequestConfig(responseTimeout))
				.evictIdleConnections(TimeValue.ofMilliseconds(properties.getIdleEvictionDuration().toMillis()))
				.build();
		RestClient restClient = RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
				.requestInterceptor(new ExternalClientMetricsInterceptor(name, meterRegistry))
				.requestInterceptor(new LogbookClientHttpRequestInterceptor(logbook))
				.build();
		managedClients.add(new ManagedPooledRestClient(name, restClient, httpClient, connectionManager));
		return restClient;
	}

	/**
	 * Builds the per-request timeouts: configured connect and pool-lease timeouts plus the given response
	 * timeout.
	 *
	 * @param responseTimeout maximum wait for response data after the request is sent
	 * @return request configuration applied to every request of the client
	 */
	RequestConfig buildRequestConfig(Duration responseTimeout) {
		return RequestConfig.custom()
				.setConnectTimeout(Timeout.ofMilliseconds(properties.getConnectTimeout().toMillis()))
				.setResponseTimeout(Timeout.ofMilliseconds(responseTimeout.toMillis()))
				.setConnectionRequestTimeout(
						Timeout.ofMilliseconds(properties.getConnectionRequestTimeout().toMillis()))
				.build();
	}

	/**
	 * Builds a bounded connection pool with the configured limits, connection lifetime and idle
	 * re-validation.
	 *
	 * @return configured connection manager
	 */
	PoolingHttpClientConnectionManager buildConnectionManager() {
		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
		connectionManager.setMaxTotal(properties.getMaxTotalConnections());
		connectionManager.setDefaultMaxPerRoute(properties.getMaxConnectionsPerRoute());
		connectionManager.setDefaultConnectionConfig(ConnectionConfig.custom()
				.setTimeToLive(TimeValue.ofMilliseconds(properties.getKeepAliveDuration().toMillis()))
				.setValidateAfterInactivity(
						TimeValue.ofMilliseconds(properties.getIdleEvictionDuration().toMillis()))
				.build());
		return connectionManager;
	}

	/**
	 * Reports how many pooled clients are currently open.
	 *
	 * @return number of managed clients not yet closed
	 */
	synchronized int managedClientCount() {
		return managedClients.size();
	}

	/**
	 * Closes every pool created by this factory.
	 */
	@PreDestroy
	public synchronized void destroy() {
		for (ManagedPooledRestClient managed : managedClients) {
			managed.close();
		}
		managedClients.clear();
	}
}
