package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.reports.dto.GenerationTarget;
import com.aidigital.reportconstructor.service.reports.dto.ReportResumeState;
import com.aidigital.reportconstructor.service.reports.helpers.AdoptedDraftRecorder;
import com.aidigital.reportconstructor.service.reports.helpers.ReportFileNamer;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportResumeStateHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Spring bean implementation of {@link AdoptedDraftRecorder}.
 */
@Component
@RequiredArgsConstructor
public class AdoptedDraftRecorderImpl implements AdoptedDraftRecorder {

	private final ReportJobProgressHelper jobProgress;
	private final ReportResumeStateHelper resumeState;
	private final ReportFileNamer fileNamer;

	@Override
	public Long record(String userId, String userEmail, String reportType, String sheetUrl, String clientName,
			ReportResumeState state) {
		ReportJobEntity job = jobProgress.createQueuedJob(userId, reportType);
		// Recorded as a finished SHEET build because that is exactly what it is from here on: a
		// reviewed workbook waiting to become a deck. The draft policy and the resume endpoint then
		// treat it like any workbook this app built itself.
		jobProgress.recordJobContext(job.getId(), userEmail, GenerationTarget.SHEET.name(), null, null);
		jobProgress.recordResumeState(job.getId(), resumeState.serialize(state));
		jobProgress.recordArtifact(job.getId(), fileNamer.buildFileName(reportType, clientName, userEmail), sheetUrl);
		jobProgress.markJobDone(job.getId(), sheetUrl, null);
		return job.getId();
	}
}
