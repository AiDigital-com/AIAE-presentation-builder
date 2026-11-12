package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.ReportResume;
import com.aidigital.reportconstructor.service.reports.dto.ReportResumeState;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.TemplateVariant;
import com.aidigital.reportconstructor.service.reports.helpers.AdoptedDraftRecorder;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownInferenceHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.helpers.SheetCampaignReader;
import com.aidigital.reportconstructor.service.reports.helpers.SheetPlaceholderReader;
import com.aidigital.reportconstructor.service.reports.helpers.SheetTacticCountHelper;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import com.aidigital.reportconstructor.service.reports.services.SheetAdoptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Default {@link SheetAdoptionService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SheetAdoptionServiceImpl implements SheetAdoptionService {

	/** Sheet field carrying the campaign context; the brief the deck step will actually use. */
	private static final String RFP_INFO_TOKEN = "{{RFP info}}";

	/** Sheet field carrying the mid-flight change log. */
	private static final String CHANGE_LOG_TOKEN = "{{change log}}";

	/** Sheet field carrying the addressable market volume. */
	private static final String MARKET_VOLUME_TOKEN = "{{market volume}}";

	private final ReportSheetHelper sheetHelper;
	private final SheetPlaceholderReader placeholderReader;
	private final SheetCampaignReader sheetCampaign;
	private final SheetTacticCountHelper tacticCounter;
	private final BreakdownInferenceHelper breakdownInference;
	private final AdoptedDraftRecorder draftRecorder;
	private final ObjectProvider<UserGoogleTokenProvider> userGoogleTokens;

	@Override
	public ReportResume adopt(
			String userId, String clerkUserId, String userEmail, String sheetUrl, String reportType,
			TemplateVariant templateVariant) {
		if (sheetUrl == null || sheetUrl.isBlank()) {
			throw new AppException(ErrorReason.C002, "Sheet URL is required");
		}
		UserGoogleTokenProvider clerk = userGoogleTokens.getIfAvailable();
		String userGoogleToken = clerk == null ? null : clerk.googleAccessToken(clerkUserId);

		List<List<String>> grid = sheetHelper.readSheetGrid(sheetUrl, userGoogleToken);
		if (grid == null || grid.isEmpty()) {
			throw new AppException(ErrorReason.C002,
					"Could not read that Google Sheet — check the link and that you have access to it");
		}
		Map<String, String> sheetValues = placeholderReader.readPlaceholders(grid);
		int tacticCount = tacticCounter.countFromPlaceholders(sheetValues);
		// The gate that separates "a report workbook" from "some other spreadsheet". Checked here,
		// before a job row exists, so a wrong link fails in the UI rather than three Claude batches later.
		if (tacticCount == 0) {
			throw new AppException(ErrorReason.C002,
					"That sheet doesn't look like a report workbook — no tactics found on its first tab");
		}
		CampaignData data = sheetCampaign.read(sheetValues, tacticCount);
		List<BreakdownSelection> breakdowns = breakdownInference.infer(sheetUrl, tacticCount, userGoogleToken);

		ReportResumeState state = new ReportResumeState(
				reportType,
				sheetValues.get(RFP_INFO_TOKEN),
				sheetValues.get(CHANGE_LOG_TOKEN),
				sheetValues.get(MARKET_VOLUME_TOKEN),
				// Deliberately no date window: the workbook's own {{flight_dates}} is free text this app
				// did not write, and the sheet value wins for every figure the deck renders anyway.
				null,
				Boolean.TRUE,
				breakdowns,
				tacticNames(data, tacticCount),
				templateVariant == null ? TemplateVariant.STANDARD : templateVariant);

		Long jobId = draftRecorder.record(userId, userEmail, reportType, sheetUrl, data.client(), state);
		log.info("[adopt] job {} adopted sheet {} for {} with {} tactic(s)", jobId, sheetUrl, userEmail, tacticCount);
		return new ReportResume(jobId, sheetUrl, null, null, state);
	}

	/**
	 * Lists the workbook's tactic names in report order.
	 *
	 * @param data        the campaign data read from the workbook
	 * @param tacticCount how many tactics it reports
	 * @return the tactic names, ordered by tactic number
	 */
	List<String> tacticNames(CampaignData data, int tacticCount) {
		Map<Integer, Tactic> tactics = data == null ? null : data.tactics();
		if (tactics == null || tactics.isEmpty()) {
			return List.of();
		}
		List<String> names = new ArrayList<>();
		for (Integer tacticNum : new TreeSet<>(tactics.keySet())) {
			if (tacticNum == null || tacticNum > tacticCount) {
				continue;
			}
			Tactic tactic = tactics.get(tacticNum);
			names.add(tactic == null ? null : tactic.name());
		}
		return names;
	}
}
