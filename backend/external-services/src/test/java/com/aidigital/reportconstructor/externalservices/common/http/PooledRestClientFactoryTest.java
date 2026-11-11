package com.aidigital.reportconstructor.externalservices.common.http;

import com.aidigital.reportconstructor.externalservices.common.http.config.PooledHttpClientProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.zalando.logbook.Logbook;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PooledRestClientFactoryTest {

	@Test
	void shouldCreateIndependentManagedClientsTest() {
		// Given:
		PooledRestClientFactory factory = new PooledRestClientFactory(
				Logbook.builder().build(), new SimpleMeterRegistry(), new PooledHttpClientProperties());

		// When:
		RestClient first = factory.createClient("anthropic", "https://a.example.com");
		RestClient second = factory.createClient("clerk", "https://b.example.com");

		// Then:
		assertThat(first).isNotNull().isNotSameAs(second);
		assertThat(factory.managedClientCount()).isEqualTo(2);
	}

	@Test
	void shouldApplyConfiguredPoolLimitsTest() {
		// Given:
		PooledHttpClientProperties props = new PooledHttpClientProperties();
		props.setMaxTotalConnections(77);
		props.setMaxConnectionsPerRoute(13);
		PooledRestClientFactory factory = new PooledRestClientFactory(
				Logbook.builder().build(), new SimpleMeterRegistry(), props);

		// When:
		PoolingHttpClientConnectionManager pool = factory.buildConnectionManager();

		// Then:
		assertThat(pool.getMaxTotal()).isEqualTo(77);
		assertThat(pool.getDefaultMaxPerRoute()).isEqualTo(13);
	}

	@Test
	void shouldApplyConfiguredTimeoutsWithAPerClientResponseTimeoutTest() {
		// Given:
		PooledHttpClientProperties props = new PooledHttpClientProperties();
		props.setConnectTimeout(Duration.ofSeconds(2));
		props.setConnectionRequestTimeout(Duration.ofSeconds(3));
		PooledRestClientFactory factory = new PooledRestClientFactory(
				Logbook.builder().build(), new SimpleMeterRegistry(), props);

		// When:
		RequestConfig config = factory.buildRequestConfig(Duration.ofSeconds(120));

		// Then:
		assertThat(config.getConnectTimeout()).isEqualTo(Timeout.ofMilliseconds(2_000));
		assertThat(config.getConnectionRequestTimeout()).isEqualTo(Timeout.ofMilliseconds(3_000));
		assertThat(config.getResponseTimeout()).isEqualTo(Timeout.ofMilliseconds(120_000));
	}

	@Test
	void shouldCloseEveryManagedClientOnDestroyTest() {
		// Given:
		PooledRestClientFactory factory = new PooledRestClientFactory(
				Logbook.builder().build(), new SimpleMeterRegistry(), new PooledHttpClientProperties());
		factory.createClient("anthropic", "https://a.example.com", Duration.ofSeconds(60));

		// When:
		factory.destroy();

		// Then:
		assertThat(factory.managedClientCount()).isZero();
	}
}
