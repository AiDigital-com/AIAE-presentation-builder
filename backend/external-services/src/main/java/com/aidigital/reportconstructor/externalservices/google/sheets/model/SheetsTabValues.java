package com.aidigital.reportconstructor.externalservices.google.sheets.model;

import java.util.List;

/**
 * One tab read from a Google spreadsheet.
 *
 * @param spreadsheetId Google spreadsheet id
 * @param title         spreadsheet title
 * @param tab           the visible tab actually read (may differ from the requested name in case or
 *                      surrounding whitespace)
 * @param visibleTabs   titles of every visible tab, in sheet order
 * @param rows          every row of the tab, cells rendered as strings ({@code ""} for empty cells)
 */
public record SheetsTabValues(
		String spreadsheetId, String title, String tab, List<String> visibleTabs, List<List<String>> rows) {
}
