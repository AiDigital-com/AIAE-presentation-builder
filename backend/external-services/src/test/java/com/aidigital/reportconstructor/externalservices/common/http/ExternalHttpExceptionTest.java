package com.aidigital.reportconstructor.externalservices.common.http;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalHttpExceptionTest {

	@Test
	void shouldCarryTheStatusAndBodyOfARejectedResponseTest() {
		// Given:

		// When:
		ExternalHttpException ex = new ExternalHttpException("clerk returned 503", 503, null);

		// Then:
		assertThat(ex.getStatusCode()).isEqualTo(503);
		assertThat(ex.getResponseBody()).isEmpty();
		assertThat(ex.getMessage()).isEqualTo("clerk returned 503");
	}

	@Test
	void shouldMarkATransportFailureAsHavingNoStatusTest() {
		// Given:
		IOException cause = new IOException("connect timed out");

		// When:
		ExternalHttpException ex = new ExternalHttpException("anthropic unreachable", cause);

		// Then:
		assertThat(ex.getStatusCode()).isEqualTo(ExternalHttpException.NO_STATUS);
		assertThat(ex.getResponseBody()).isEmpty();
		assertThat(ex.getCause()).isSameAs(cause);
	}
}
