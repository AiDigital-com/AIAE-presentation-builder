package com.aidigital.reportconstructor.externalservices.google;

import lombok.Getter;
import lombok.Setter;

/**
 * The source workbooks of the daily pacing, monthly impressions and weighted impression contribution charts on
 * an MPO deck's tactic slides, mirroring the singular {@code external.google.charts.*-template-sheet-id}
 * settings of the standard deck. Bound as a
 * property group of {@link MpoDeckProperties}.
 *
 * <p>Each id doubles as the key that finds the chart on a duplicated tactic slide: the MPO template's master
 * links its pacing charts to these exact workbooks, so the copies do too. A blank id keeps the standard
 * catalog in charge of that chart type, which on an MPO deck finds no linked chart and reports it.
 */
@Getter
@Setter
public class MpoDeckChartSources {

	/** Workbook the daily pacing chart is copied from and linked to on the MPO tactic master. */
	private String dailyTemplateSheetId = "";

	/** Embedded chart id inside {@link #dailyTemplateSheetId}; {@code null} uses the standard daily id. */
	private Integer dailyChartIdInSheet;

	/** Workbook the monthly impressions chart is copied from and linked to on the MPO tactic master. */
	private String monthlyTemplateSheetId = "";

	/** Embedded chart id inside {@link #monthlyTemplateSheetId}; {@code null} uses the standard monthly id. */
	private Integer monthlyChartIdInSheet;

	/** Workbook the weighted impression contribution donut is copied from and linked to on the MPO master. */
	private String distTemplateSheetId = "";

	/** Embedded chart id inside {@link #distTemplateSheetId}; {@code null} uses the standard distribution id. */
	private Integer distChartIdInSheet;
}
