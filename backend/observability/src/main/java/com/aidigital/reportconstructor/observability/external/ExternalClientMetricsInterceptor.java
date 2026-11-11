package com.aidigital.reportconstructor.observability.external;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Times every request of one Spring HTTP client on the shared {@link ExternalCallTags#METRIC_NAME} timer,
 * tagged with the client name, the HTTP method and a bounded outcome. Never tags URLs, paths or ids.
 */
@RequiredArgsConstructor
public class ExternalClientMetricsInterceptor implements ClientHttpRequestInterceptor {

	private final String clientName;
	private final MeterRegistry meterRegistry;

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		Timer.Sample sample = Timer.start(meterRegistry);
		ExternalCallOutcome outcome = ExternalCallOutcome.IO_ERROR;
		try {
			ClientHttpResponse response = execution.execute(request, body);
			outcome = classifyOutcome(response.getStatusCode());
			return response;
		} catch (RuntimeException ex) {
			outcome = ExternalCallOutcome.RUNTIME_ERROR;
			throw ex;
		} finally {
			sample.stop(Timer.builder(ExternalCallTags.METRIC_NAME)
					.tag(ExternalCallTags.CLIENT, clientName)
					.tag(ExternalCallTags.OPERATION, request.getMethod().name())
					.tag(ExternalCallTags.OUTCOME, outcome.getTagValue())
					.register(meterRegistry));
		}
	}

	/**
	 * Maps an HTTP status to its bounded outcome.
	 *
	 * @param status response status
	 * @return {@code SERVER_ERROR} for 5xx, {@code CLIENT_ERROR} for 4xx, otherwise {@code SUCCESS}
	 */
	ExternalCallOutcome classifyOutcome(HttpStatusCode status) {
		if (status.is5xxServerError()) {
			return ExternalCallOutcome.SERVER_ERROR;
		}
		if (status.is4xxClientError()) {
			return ExternalCallOutcome.CLIENT_ERROR;
		}
		return ExternalCallOutcome.SUCCESS;
	}
}
