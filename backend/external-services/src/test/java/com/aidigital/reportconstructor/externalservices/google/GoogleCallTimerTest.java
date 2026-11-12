package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.observability.external.ExternalCallTimer;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleCallTimerTest {

	@Test
	void shouldTimeASuccessfulSdkCallWithLowCardinalityTagsTest() throws IOException {
		// Given: a Sheets client whose transport answers one values.get
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		GoogleCallTimer timer = new GoogleCallTimer(new ExternalCallTimer(registry));
		MockHttpTransport transport = new MockHttpTransport.Builder()
				.setLowLevelHttpResponse(new MockLowLevelHttpResponse()
						.setContentType("application/json")
						.setContent("{\"range\": \"Data!A1:B2\"}"))
				.build();
		Sheets sheets = new Sheets.Builder(transport, GsonFactory.getDefaultInstance(), null)
				.setApplicationName("test")
				.build();

		// When:
		ValueRange range = timer.execute(sheets.spreadsheets().values().get("sheet-123", "Data!A1:B2"));

		// Then: the sheet id and range never reach the tags
		assertThat(range.getRange()).isEqualTo("Data!A1:B2");
		assertThat(registry.get(ExternalCallTimer.METRIC_NAME)
				.tags("client", "google-sheets", "operation", "spreadsheets.values.get", "outcome", "success")
				.timer().count()).isEqualTo(1);
	}

	@Test
	void shouldRecordAFailedSdkCallAndRethrowTest() {
		// Given: a Sheets client whose transport answers 500
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		GoogleCallTimer timer = new GoogleCallTimer(new ExternalCallTimer(registry));
		MockHttpTransport transport = new MockHttpTransport.Builder()
				.setLowLevelHttpResponse(new MockLowLevelHttpResponse()
						.setStatusCode(500)
						.setContentType("application/json")
						.setContent("{\"error\": {\"code\": 500, \"message\": \"backend error\"}}"))
				.build();
		Sheets sheets = new Sheets.Builder(transport, GsonFactory.getDefaultInstance(), null)
				.setApplicationName("test")
				.build();

		// When-Then:
		assertThatThrownBy(() -> timer.execute(sheets.spreadsheets().get("sheet-123")))
				.isInstanceOf(GoogleJsonResponseException.class);
		assertThat(registry.get(ExternalCallTimer.METRIC_NAME)
				.tags("client", "google-sheets", "operation", "spreadsheets.get", "outcome", "error")
				.timer().count()).isEqualTo(1);
	}
}
