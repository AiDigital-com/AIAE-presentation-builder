package com.aidigital.reportconstructor.externalservices.google.sheets.impl;

import com.aidigital.reportconstructor.externalservices.google.sheets.model.SheetsTabValues;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubSheetsClientTest {

	@Test
	void shouldReportItselfAsNotLiveTest() {
		// Given:
		StubSheetsClient client = new StubSheetsClient();

		// When:
		boolean live = client.isLive();

		// Then:
		assertThat(live).isFalse();
	}

	@Test
	void shouldReturnTheFixtureForTheRequestedTabTest() {
		// Given:
		StubSheetsClient client = new StubSheetsClient();

		// When:
		SheetsTabValues values = client.readTab("sheet-1", "Geo", null);

		// Then:
		assertThat(values.spreadsheetId()).isEqualTo("sheet-1");
		assertThat(values.title()).isEqualTo("Sample Media Plan — Stub");
		assertThat(values.tab()).isEqualTo("Geo");
		assertThat(values.visibleTabs())
				.containsExactly("Proposal", "Audience&Inventory", "Estimates", "Geo", "Basic", "Workspace");
		assertThat(values.rows()).isEqualTo(client.geoFixture());
	}

	@Test
	void shouldFallBackToTheProposalFixtureForAnUnknownTabTest() {
		// Given:
		StubSheetsClient client = new StubSheetsClient();

		// When:
		SheetsTabValues values = client.readTab("sheet-1", "Something else", null);

		// Then: any tab name is accepted
		assertThat(values.tab()).isEqualTo("Something else");
		assertThat(values.rows()).isEqualTo(client.proposalFixture());
	}
}
