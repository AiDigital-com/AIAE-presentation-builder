package com.aidigital.reportconstructor.service.reports.services;

import com.aidigital.reportconstructor.service.reports.dto.SheetTabRead;

/**
 * Reads one sheet tab for the wizard, turning a missing tab into a normal result that lists the
 * workbook's visible tabs rather than an error.
 */
public interface SheetTabReadService {

	/**
	 * Reads one tab as the caller.
	 *
	 * @param spreadsheetUrl the spreadsheet URL
	 * @param tab            the tab title
	 * @param callerUserId   the signed-in user's Clerk id
	 * @return the tab's data, or the visible tabs when the tab does not exist
	 */
	SheetTabRead readTab(String spreadsheetUrl, String tab, String callerUserId);
}
