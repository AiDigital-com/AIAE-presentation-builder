package com.aidigital.reportconstructor.externalservices.google.sheets.impl;

import com.aidigital.reportconstructor.externalservices.google.sheets.SheetsClient;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.SheetsTabValues;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic stub used when no {@code @Primary} real Sheets client is registered (i.e. when
 * {@code GOOGLE_SERVICE_ACCOUNT_JSON} is unset and {@link SheetsClientImpl} stays conditional-excluded).
 *
 * <p>Returns fixture rows tailored to the requested tab so the downstream matcher and placeholder
 * resolvers see realistic shapes. Any tab name is accepted; unknown names get the proposal fixture.
 */
@Component
public class StubSheetsClient implements SheetsClient {

	private static final String STUB_TITLE = "Sample Media Plan — Stub";
	private static final List<String> STUB_TABS =
			List.of("Proposal", "Audience&Inventory", "Estimates", "Geo", "Basic", "Workspace");

	@Override
	public boolean isLive() {
		return false;
	}

	@Override
	public SheetsTabValues readTab(String spreadsheetId, String tab, String userAccessToken) {
		return new SheetsTabValues(spreadsheetId, STUB_TITLE, tab, STUB_TABS, sampleRowsFor(tab));
	}

	/**
	 * Picks the fixture that stands in for a given tab.
	 *
	 * @param tab the tab name the caller asked for
	 * @return the fixture rows for that tab, or the proposal fixture when the name is unknown
	 */
	List<List<String>> sampleRowsFor(String tab) {

		return switch (tab) {
			case "Basic", "Workspace" -> bigQueryFixture();
			case "Audience&Inventory" -> audienceFixture();
			case "Estimates" -> estimatesFixture();
			case "Geo" -> geoFixture();
			default -> proposalFixture();
		};
	}

	/**
	 * Fixture standing in for the Media Plan / proposal tab.
	 *
	 * @return the proposal rows
	 */
	List<List<String>> proposalFixture() {

		List<List<String>> rows = new ArrayList<>();
		rows.add(List.of("Client", "Campaign", "Flight", "Budget", "KPI"));
		rows.add(List.of("Demo Client", "Awareness Q2", "May 1 – Jun 30", "$120,000", "Impressions"));
		rows.add(List.of("Media", "Tactic", "Cost", "Comments", ""));
		rows.add(List.of("CTV", "Premium CTV", "$45,000", "Roku + Hulu", ""));
		rows.add(List.of("Display", "Programmatic Display", "$30,000", "Open exchange", ""));
		rows.add(List.of("Video", "Online Video", "$25,000", "YouTube + Trade Desk", ""));
		rows.add(List.of("Audio", "Streaming Audio", "$20,000", "Spotify + iHeart", ""));
		return rows;
	}

	/**
	 * Fixture standing in for the BigQuery delivery export tab.
	 *
	 * @return the delivery rows
	 */
	List<List<String>> bigQueryFixture() {

		List<List<String>> rows = new ArrayList<>();
		rows.add(List.of("Level 1 Naming", "Tactic", "Channel", "Impressions", "Clicks", "Spend"));
		rows.add(List.of("DEMO_CLIENT_AWARE_Q2_CTV_2025_05_ROKU_PREM_1001_NA", "Premium CTV", "CTV", "1,840,221", "—",
				"$45,000"));
		rows.add(List.of("DEMO_CLIENT_AWARE_Q2_DSP_2025_05_TTD_OPEN_1002_NA", "Programmatic Display", "Display", "8," +
				"210,440", "32,114", "$30,000"));
		rows.add(List.of("DEMO_CLIENT_AWARE_Q2_VID_2025_05_YT_INST_1003_NA", "Online Video", "Video", "3,109,002",
				"12,448", "$25,000"));
		rows.add(List.of("DEMO_CLIENT_AWARE_Q2_AUD_2025_05_SPOT_STR_1004_NA", "Streaming Audio", "Audio", "2,400,118",
				"—", "$20,000"));
		return rows;
	}

	/**
	 * Fixture standing in for the Audience &amp; Inventory tab.
	 *
	 * @return the audience rows
	 */
	List<List<String>> audienceFixture() {

		return List.of(
				List.of("Segment", "Reach", "Frequency"),
				List.of("A18-34", "1,200,000", "3.2"),
				List.of("A35-54", "980,000", "2.8")
		);
	}

	/**
	 * Fixture standing in for the Estimates tab, source of the planned KPIs.
	 *
	 * @return the estimates rows
	 */
	List<List<String>> estimatesFixture() {

		return List.of(
				List.of("Line", "Planned Impr", "Planned Spend"),
				List.of("CTV", "1,800,000", "$45,000"),
				List.of("Display", "8,000,000", "$30,000")
		);
	}

	/**
	 * Fixture standing in for the Geo tab.
	 *
	 * @return the geo rows
	 */
	List<List<String>> geoFixture() {

		return List.of(
				List.of("DMA", "Share"),
				List.of("New York", "32%"),
				List.of("Los Angeles", "21%"),
				List.of("Chicago", "14%")
		);
	}
}
