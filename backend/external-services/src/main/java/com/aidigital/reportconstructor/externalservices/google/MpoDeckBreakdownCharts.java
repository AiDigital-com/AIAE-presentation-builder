package com.aidigital.reportconstructor.externalservices.google;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * The source workbooks of the breakdown charts on an MPO deck's audience and device slides, mirroring
 * {@code external.google.breakdown-charts} of the standard deck. Bound as a property group of
 * {@link MpoDeckProperties}; keyed by chart series code ({@code aud}, {@code aud-seg}, {@code dev}).
 *
 * <p>Each workbook doubles as the key that finds the chart on a breakdown copy: the MPO master links its
 * charts to these workbooks, so the copies do too. A series left out keeps the standard workbook, which the
 * MPO master does not link to, so that chart is reported missing.
 */
@Getter
@Setter
public class MpoDeckBreakdownCharts {

	/** Chart series code &rarr; workbook its chart is copied from and linked to on the MPO master. */
	private Map<String, String> sourceSheetIds = Map.of();

	/** Chart series code &rarr; embedded chart id inside that series' workbook. */
	private Map<String, String> chartIdInSheet = Map.of();
}
