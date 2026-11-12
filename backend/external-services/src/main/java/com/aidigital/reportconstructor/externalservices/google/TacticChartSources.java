package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.ports.ChartRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Answers which source workbook (and which chart inside it) a tactic's daily pacing, monthly impressions or
 * weighted impression contribution chart is built from. A deck cloned from an MPO template takes its workbooks
 * from {@link MpoDeckPolicy#tacticChartSources()}, because the MPO tactic master links its charts to its own
 * workbooks and the chart step finds the slide chart by that link; every other deck, and an MPO chart type left
 * unconfigured, uses {@link ChartTemplateCatalog}.
 */
@Component
@RequiredArgsConstructor
public class TacticChartSources {

	private final ChartTemplateCatalog templates;
	private final MpoDeckPolicy mpoDeckPolicy;

	/**
	 * Resolves the daily-chart source workbook for one tactic.
	 *
	 * @param req       the chart request, whose template variant and report type pick the deck design
	 * @param tacticNum the 1-based tactic number
	 * @return the workbook id, or {@code null} when nothing is configured
	 */
	public String dailyTemplateFor(ChartRequest req, int tacticNum) {
		String mpoId = mpoDailyId(req);
		return mpoId.isEmpty() ? templates.dailyTemplateFor(tacticNum) : mpoId;
	}

	/**
	 * Resolves the embedded chart id inside the daily-chart source workbook.
	 *
	 * @param req the chart request
	 * @return the in-sheet chart id
	 */
	public int dailyChartId(ChartRequest req) {
		Integer mpoChartId =
				mpoDailyId(req).isEmpty() ? null : mpoDeckPolicy.tacticChartSources().getDailyChartIdInSheet();
		return mpoChartId == null ? templates.dailyChartId() : mpoChartId;
	}

	/**
	 * Resolves the monthly-chart source workbook for one tactic.
	 *
	 * @param req       the chart request, whose template variant and report type pick the deck design
	 * @param tacticNum the 1-based tactic number
	 * @return the workbook id, or {@code null} when nothing is configured
	 */
	public String monthlyTemplateFor(ChartRequest req, int tacticNum) {
		String mpoId = mpoMonthlyId(req);
		return mpoId.isEmpty() ? templates.monthlyTemplateFor(tacticNum) : mpoId;
	}

	/**
	 * Resolves the embedded chart id inside the monthly-chart source workbook.
	 *
	 * @param req the chart request
	 * @return the in-sheet chart id
	 */
	public int monthlyChartId(ChartRequest req) {
		Integer mpoChartId =
				mpoMonthlyId(req).isEmpty() ? null : mpoDeckPolicy.tacticChartSources().getMonthlyChartIdInSheet();
		return mpoChartId == null ? templates.monthlyChartId() : mpoChartId;
	}

	/**
	 * Resolves the distribution (weighted impression contribution) chart source workbook for one tactic.
	 *
	 * @param req       the chart request, whose template variant and report type pick the deck design
	 * @param tacticNum the 1-based tactic number
	 * @return the workbook id, or {@code null} when nothing is configured
	 */
	public String distTemplateFor(ChartRequest req, int tacticNum) {
		String mpoId = mpoDistId(req);
		return mpoId.isEmpty() ? templates.distTemplateFor(tacticNum) : mpoId;
	}

	/**
	 * Resolves the embedded chart id inside the distribution-chart source workbook.
	 *
	 * @param req the chart request
	 * @return the in-sheet chart id
	 */
	public int distChartId(ChartRequest req) {
		Integer mpoChartId =
				mpoDistId(req).isEmpty() ? null : mpoDeckPolicy.tacticChartSources().getDistChartIdInSheet();
		return mpoChartId == null ? templates.distChartId() : mpoChartId;
	}

	/**
	 * Returns the MPO distribution workbook when this deck is cloned from an MPO template and one is configured.
	 *
	 * @param req the chart request
	 * @return the trimmed MPO distribution workbook id, or an empty string when the standard catalog applies
	 */
	String mpoDistId(ChartRequest req) {
		return usesMpoTemplate(req) ? trimmed(mpoDeckPolicy.tacticChartSources().getDistTemplateSheetId()) : "";
	}

	/**
	 * Returns the MPO daily workbook when this deck is cloned from an MPO template and one is configured.
	 *
	 * @param req the chart request
	 * @return the trimmed MPO daily workbook id, or an empty string when the standard catalog applies
	 */
	String mpoDailyId(ChartRequest req) {
		return usesMpoTemplate(req) ? trimmed(mpoDeckPolicy.tacticChartSources().getDailyTemplateSheetId()) : "";
	}

	/**
	 * Returns the MPO monthly workbook when this deck is cloned from an MPO template and one is configured.
	 *
	 * @param req the chart request
	 * @return the trimmed MPO monthly workbook id, or an empty string when the standard catalog applies
	 */
	String mpoMonthlyId(ChartRequest req) {
		return usesMpoTemplate(req) ? trimmed(mpoDeckPolicy.tacticChartSources().getMonthlyTemplateSheetId()) : "";
	}

	/**
	 * Tells whether the request's deck was cloned from an MPO template.
	 *
	 * @param req the chart request
	 * @return {@code true} for an MPO deck
	 */
	boolean usesMpoTemplate(ChartRequest req) {
		return mpoDeckPolicy.usesMpoTemplate(req.templateVariant(), req.reportType());
	}

	/**
	 * Trims a configured id, treating {@code null} as unset.
	 *
	 * @param id the configured id, may be {@code null}
	 * @return the trimmed id, or an empty string when unset
	 */
	String trimmed(String id) {
		return id == null ? "" : id.trim();
	}
}
