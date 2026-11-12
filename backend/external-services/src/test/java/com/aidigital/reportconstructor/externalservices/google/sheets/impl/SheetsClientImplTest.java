package com.aidigital.reportconstructor.externalservices.google.sheets.impl;

import com.aidigital.reportconstructor.externalservices.google.GoogleCallTimer;
import com.aidigital.reportconstructor.externalservices.google.GoogleCredentialsFactory;
import com.aidigital.reportconstructor.externalservices.google.sheets.SheetsExternalException;
import com.aidigital.reportconstructor.externalservices.google.sheets.SheetsTabNotFoundException;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.SheetsTabValues;
import com.aidigital.reportconstructor.observability.external.ExternalCallTimer;
import com.google.api.client.json.gson.GsonFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SheetsClientImplTest {

	private static final String META_JSON = "{\"properties\": {\"title\": \"Media Plan\"}, \"sheets\": ["
			+ "{\"properties\": {\"title\": \"Proposal\", \"hidden\": true}},"
			+ "{\"properties\": {\"title\": \"Proposal \"}},"
			+ "{\"properties\": {\"title\": \"Geo\"}}]}";

	@Test
	void shouldReadTheVisibleTabMatchingTheRequestedNameTest() {
		// Given: a hidden exact-name "Proposal" and a visible "Proposal " with a trailing space
		RoutingSheetsTransport transport = new RoutingSheetsTransport(200, META_JSON, 200,
				"{\"values\": [[\"Line\", \"Budget\"], [\"Display\", 1200]]}");
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		SheetsClientImpl client = new SheetsClientImpl(
				creds, new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));

		// When:
		SheetsTabValues values = client.readTab("sheet-1", "Proposal", null);

		// Then: only visible tabs are exposed, the visible copy is read and cells become strings
		assertThat(values).isEqualTo(new SheetsTabValues("sheet-1", "Media Plan", "Proposal ",
				List.of("Proposal ", "Geo"), List.of(List.of("Line", "Budget"), List.of("Display", "1200"))));
		assertThat(transport.urls().get(1)).contains("/values/'Proposal%20'!A1:ZZ");
	}

	@Test
	void shouldReturnNoRowsForAnEmptyTabTest() {
		// Given:
		RoutingSheetsTransport transport = new RoutingSheetsTransport(200, META_JSON, 200, "{}");
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		SheetsClientImpl client = new SheetsClientImpl(
				creds, new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));

		// When:
		SheetsTabValues values = client.readTab("sheet-1", "Geo", null);

		// Then:
		assertThat(values.rows()).isEmpty();
		assertThat(values.tab()).isEqualTo("Geo");
	}

	@Test
	void shouldThrowTabNotFoundWithTheVisibleTabsTest() {
		// Given:
		RoutingSheetsTransport transport = new RoutingSheetsTransport(200, META_JSON, 200, "{}");
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		SheetsClientImpl client = new SheetsClientImpl(
				creds, new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));

		// When-Then: no values call is made for a missing tab
		assertThatThrownBy(() -> client.readTab("sheet-1", "Estimates", null))
				.asInstanceOf(type(SheetsTabNotFoundException.class))
				.satisfies(ex -> {
					assertThat(ex.requestedTab()).isEqualTo("Estimates");
					assertThat(ex.visibleTabs()).containsExactly("Proposal ", "Geo");
				});
		assertThat(transport.urls()).hasSize(1);
	}

	@Test
	void shouldThrowWithStatusAndGoogleMessageWhenGoogleRejectsTheReadTest() {
		// Given:
		RoutingSheetsTransport transport = new RoutingSheetsTransport(403,
				"{\"error\": {\"code\": 403, \"message\": \"The caller does not have permission\"}}", 200, "{}");
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		SheetsClientImpl client = new SheetsClientImpl(
				creds, new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())));

		// When-Then:
		assertThatThrownBy(() -> client.readTab("sheet-1", "Geo", null))
				.asInstanceOf(type(SheetsExternalException.class))
				.satisfies(ex -> {
					assertThat(ex.statusCode()).isEqualTo(403);
					assertThat(ex.providerMessage()).isEqualTo("The caller does not have permission");
				});
	}
}
