package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Answers which source workbook (and which chart inside it) a breakdown chart series is built from. A deck
 * cloned from an MPO template takes its workbooks from {@link MpoDeckPolicy#breakdownChartSources()}, because
 * the MPO breakdown masters link their charts to their own workbooks and the chart step finds the slide chart
 * by that link; every other deck, and an MPO series left unconfigured, uses {@link BreakdownChartCatalog}.
 */
@Component
@RequiredArgsConstructor
public class BreakdownChartSources {

	private final BreakdownChartCatalog catalog;
	private final MpoDeckPolicy mpoDeckPolicy;

	/**
	 * Resolves the source workbook of one chart series.
	 *
	 * @param req        the breakdown chart request, whose template variant and report type pick the design
	 * @param seriesCode the chart series code ({@code aud}, {@code aud-seg}, {@code dev})
	 * @return the workbook id, or {@code null} when nothing is configured
	 */
	public String sourceSheetIdFor(BreakdownChartRequest req, String seriesCode) {
		String mpoId = mpoSourceSheetId(req, seriesCode);
		return mpoId.isEmpty() ? catalog.getSourceSheetIds().get(seriesCode) : mpoId;
	}

	/**
	 * Resolves the configured chart id inside the series' source workbook, as configured (unparsed). It is
	 * taken from the same side, MPO or standard, as {@link #sourceSheetIdFor}, so the id always belongs to
	 * the workbook that is copied.
	 *
	 * @param req        the breakdown chart request
	 * @param seriesCode the chart series code
	 * @return the configured chart id text, or {@code null} when nothing is configured
	 */
	public String chartIdInSheetFor(BreakdownChartRequest req, String seriesCode) {
		return mpoSourceSheetId(req, seriesCode).isEmpty()
				? catalog.getChartIdInSheet().get(seriesCode)
				: mpoDeckPolicy.breakdownChartSources().getChartIdInSheet().get(seriesCode);
	}

	/**
	 * Returns the MPO workbook of a series when this deck is cloned from an MPO template and one is configured.
	 *
	 * @param req        the breakdown chart request
	 * @param seriesCode the chart series code
	 * @return the trimmed MPO workbook id, or an empty string when the standard catalog applies
	 */
	String mpoSourceSheetId(BreakdownChartRequest req, String seriesCode) {
		if (!mpoDeckPolicy.usesMpoTemplate(req.templateVariant(), req.reportType())) {
			return "";
		}
		String id = mpoDeckPolicy.breakdownChartSources().getSourceSheetIds().get(seriesCode);
		return id == null ? "" : id.trim();
	}
}
