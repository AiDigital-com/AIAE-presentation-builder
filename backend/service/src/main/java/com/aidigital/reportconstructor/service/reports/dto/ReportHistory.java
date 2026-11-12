package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * One owner's report history for the "My reports" screen.
 *
 * @param total   number of listed reports (equals {@code reports.size()})
 * @param reports the owner's reports, newest first
 */
public record ReportHistory(int total, List<ReportSummary> reports) {

	/**
	 * Builds the history from its rows, deriving the count.
	 *
	 * @param reports the owner's reports, newest first
	 */
	public ReportHistory(List<ReportSummary> reports) {
		this(reports.size(), List.copyOf(reports));
	}
}
