package com.aidigital.reportconstructor.observability.external;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalClientMetricsInterceptorTest {

	@Test
	void shouldRecordSuccessWithBoundedTagsTest() throws IOException {
		// Given:
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		ExternalClientMetricsInterceptor interceptor = new ExternalClientMetricsInterceptor("anthropic", registry);
		HttpRequest request = mock(HttpRequest.class);
		when(request.getMethod()).thenReturn(HttpMethod.POST);
		byte[] body = new byte[0];
		ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
		MockClientHttpResponse ok = new MockClientHttpResponse(new byte[0], HttpStatus.OK);
		when(execution.execute(request, body)).thenReturn(ok);

		// When:
		ClientHttpResponse response = interceptor.intercept(request, body, execution);

		// Then:
		assertThat(response).isSameAs(ok);
		assertThat(registry.get(ExternalCallTimer.METRIC_NAME)
				.tags("client", "anthropic", "operation", "POST", "outcome", "success")
				.timer().count()).isEqualTo(1);
	}

	@Test
	void shouldRecordClientErrorTest() throws IOException {
		// Given:
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		ExternalClientMetricsInterceptor interceptor = new ExternalClientMetricsInterceptor("anthropic", registry);
		HttpRequest request = mock(HttpRequest.class);
		when(request.getMethod()).thenReturn(HttpMethod.POST);
		byte[] body = new byte[0];
		ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
		when(execution.execute(request, body)).thenReturn(new MockClientHttpResponse(new byte[0], HttpStatus.BAD_REQUEST));

		// When:
		interceptor.intercept(request, body, execution);

		// Then:
		assertThat(registry.get(ExternalCallTimer.METRIC_NAME)
				.tags("client", "anthropic", "operation", "POST", "outcome", "client_error")
				.timer().count()).isEqualTo(1);
	}

	@Test
	void shouldRecordServerErrorTest() throws IOException {
		// Given:
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		ExternalClientMetricsInterceptor interceptor = new ExternalClientMetricsInterceptor("clerk", registry);
		HttpRequest request = mock(HttpRequest.class);
		when(request.getMethod()).thenReturn(HttpMethod.GET);
		byte[] body = new byte[0];
		ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
		when(execution.execute(request, body))
				.thenReturn(new MockClientHttpResponse(new byte[0], HttpStatus.SERVICE_UNAVAILABLE));

		// When:
		interceptor.intercept(request, body, execution);

		// Then:
		assertThat(registry.get(ExternalCallTimer.METRIC_NAME)
				.tags("client", "clerk", "operation", "GET", "outcome", "server_error")
				.timer().count()).isEqualTo(1);
	}

	@Test
	void shouldRecordIoErrorAndRethrowTest() throws IOException {
		// Given:
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		ExternalClientMetricsInterceptor interceptor = new ExternalClientMetricsInterceptor("anthropic", registry);
		HttpRequest request = mock(HttpRequest.class);
		when(request.getMethod()).thenReturn(HttpMethod.POST);
		byte[] body = new byte[0];
		ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
		when(execution.execute(request, body)).thenThrow(new IOException("offline"));

		// When-Then:
		assertThatThrownBy(() -> interceptor.intercept(request, body, execution)).isInstanceOf(IOException.class);
		assertThat(registry.get(ExternalCallTimer.METRIC_NAME)
				.tags("client", "anthropic", "operation", "POST", "outcome", "io_error")
				.timer().count()).isEqualTo(1);
	}

	@Test
	void shouldRecordRuntimeErrorAndRethrowTest() throws IOException {
		// Given:
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		ExternalClientMetricsInterceptor interceptor = new ExternalClientMetricsInterceptor("anthropic", registry);
		HttpRequest request = mock(HttpRequest.class);
		when(request.getMethod()).thenReturn(HttpMethod.POST);
		byte[] body = new byte[0];
		ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
		when(execution.execute(request, body)).thenThrow(new IllegalStateException("invalid client state"));

		// When-Then:
		assertThatThrownBy(() -> interceptor.intercept(request, body, execution))
				.isInstanceOf(IllegalStateException.class);
		assertThat(registry.get(ExternalCallTimer.METRIC_NAME)
				.tags("client", "anthropic", "operation", "POST", "outcome", "runtime_error")
				.timer().count()).isEqualTo(1);
	}
}
