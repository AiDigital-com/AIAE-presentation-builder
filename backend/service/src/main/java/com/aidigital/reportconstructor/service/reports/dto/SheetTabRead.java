package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * Outcome of reading one tab of a Google Sheet for the wizard: either the tab's data, or — when the tab
 * does not exist — the workbook's visible tab titles, so the client can offer a manual tab picker
 * instead of failing.
 *
 * @param data        the tab's data; {@code null} when the tab was not found
 * @param visibleTabs the workbook's visible tab titles when the tab was not found; empty otherwise
 */
public record SheetTabRead(SheetData data, List<String> visibleTabs) {

	/**
	 * Whether the requested tab was found and read.
	 *
	 * @return {@code true} when {@link #data()} carries the tab
	 */
	public boolean found() {
		return data != null;
	}
}
