package com.aidigital.reportconstructor.externalservices.google.sheets;

import com.google.api.services.sheets.v4.Sheets;

import java.io.IOException;
import java.util.List;

/**
 * Writes content into one tab of a workbook through a Sheets client the workbook client has already
 * authenticated for the caller. Used for the pacing tables until their pivots are computed by the service
 * and handed over as plain data (planned with the chart conversion).
 */
@FunctionalInterface
public interface WorkbookTabWriter {

	/**
	 * Writes into the given tab.
	 *
	 * @param sheets        Sheets client authenticated as the caller (or the service account)
	 * @param spreadsheetId the workbook to write into
	 * @param tabName       the tab to write into
	 * @return human-readable error strings for any partial failures (empty on full success)
	 * @throws IOException when a Sheets call fails
	 */
	List<String> write(Sheets sheets, String spreadsheetId, String tabName) throws IOException;
}
