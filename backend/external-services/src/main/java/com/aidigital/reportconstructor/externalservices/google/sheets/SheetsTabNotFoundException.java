package com.aidigital.reportconstructor.externalservices.google.sheets;

import java.util.List;

/**
 * Raised by {@link SheetsClient#readTab} when no visible tab matches the requested name. Carries the
 * visible tabs so callers can offer them as alternatives.
 */
public class SheetsTabNotFoundException extends RuntimeException {

	/** The tab name that was requested. */
	private final String requestedTab;

	/** Titles of the spreadsheet's visible tabs, in sheet order. */
	private final List<String> visibleTabs;

	/**
	 * Creates the exception.
	 *
	 * @param requestedTab the tab name that was requested
	 * @param visibleTabs  titles of the spreadsheet's visible tabs
	 */
	public SheetsTabNotFoundException(String requestedTab, List<String> visibleTabs) {
		super("Tab \"" + requestedTab + "\" not found among visible tabs: " + String.join(", ", visibleTabs));
		this.requestedTab = requestedTab;
		this.visibleTabs = List.copyOf(visibleTabs);
	}

	/**
	 * Returns the tab name that was requested.
	 *
	 * @return the requested tab name
	 */
	public String requestedTab() {
		return requestedTab;
	}

	/**
	 * Returns the titles of the spreadsheet's visible tabs.
	 *
	 * @return visible tab titles, in sheet order
	 */
	public List<String> visibleTabs() {
		return visibleTabs;
	}
}
