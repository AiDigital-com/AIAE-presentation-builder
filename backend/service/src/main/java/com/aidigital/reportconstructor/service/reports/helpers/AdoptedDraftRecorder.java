package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.ReportResumeState;

/**
 * Records a workbook the user filled themselves as a finished sheet build, so the draft policy and
 * the resume flow treat it like any workbook this app built.
 */
public interface AdoptedDraftRecorder {

	/**
	 * Creates the job row and records it as a done SHEET build carrying the given resume state.
	 *
	 * @param userId     internal owner id
	 * @param userEmail  owner email
	 * @param reportType report type code
	 * @param sheetUrl   the adopted workbook
	 * @param clientName client name read from the workbook, for the artifact file name
	 * @param state      the wizard state to resume with
	 * @return id of the recorded job
	 */
	Long record(String userId, String userEmail, String reportType, String sheetUrl, String clientName,
			ReportResumeState state);
}
