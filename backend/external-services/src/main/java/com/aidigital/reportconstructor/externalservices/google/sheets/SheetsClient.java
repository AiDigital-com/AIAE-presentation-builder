package com.aidigital.reportconstructor.externalservices.google.sheets;

import com.aidigital.reportconstructor.externalservices.google.sheets.model.SheetsTabValues;

/**
 * Narrow interface for reading Google Sheets. {@code SheetsClientImpl} calls the Sheets v4 API; the
 * {@code StubSheetsClient} returns deterministic fixtures for offline demos (bean selection is
 * automatic: the real client is {@code @Primary} when Google credentials are configured).
 * Vendor types never cross this interface.
 */
public interface SheetsClient {

	/**
	 * Reports whether this client talks to the real Google APIs.
	 *
	 * @return {@code true} for the real client, {@code false} for the stub
	 */
	boolean isLive();

	/**
	 * Reads one tab of a spreadsheet. Only visible tabs are considered; the requested name is matched
	 * exactly first, then trimmed and case-insensitively (media plans often keep a hidden stale copy
	 * and a visible {@code "Proposal "} with a trailing space).
	 *
	 * @param spreadsheetId   Google spreadsheet id
	 * @param tab             tab name to read
	 * @param userAccessToken the signed-in user's Google OAuth access token; when non-blank the read runs
	 *                        as that user, otherwise as the service account
	 * @return the tab's title, resolved name, visible tabs and every row as strings
	 * @throws SheetsTabNotFoundException when no visible tab matches
	 * @throws SheetsExternalException    on any other Google or transport failure
	 */
	SheetsTabValues readTab(String spreadsheetId, String tab, String userAccessToken);
}
