package com.aidigital.reportconstructor.externalservices.common.http;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.web.client.RestClient;

import java.io.IOException;

/**
 * Pooled HTTP resources created for one logical external client, closed together on shutdown.
 */
@Slf4j
@Getter
@RequiredArgsConstructor
public class ManagedPooledRestClient implements AutoCloseable {

	/** Logical client name used in metrics and diagnostics. */
	private final String name;

	/** Spring client backed by {@link #httpClient}. */
	private final RestClient restClient;

	/** Apache client owning the pool. */
	private final CloseableHttpClient httpClient;

	/** Connection pool of {@link #httpClient}. */
	private final PoolingHttpClientConnectionManager connectionManager;

	@Override
	public void close() {
		try {
			httpClient.close();
		} catch (IOException ex) {
			log.warn("[http] closing pooled client {} failed: {}", name, ex.getMessage());
		}
		connectionManager.close();
	}
}
