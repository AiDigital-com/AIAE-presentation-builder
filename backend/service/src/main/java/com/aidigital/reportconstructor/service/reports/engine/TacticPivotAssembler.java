package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.model.TacticPivots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pivots the BigQuery export into every tactic's daily and monthly pacing series — the step that feeds both
 * the deck's pacing charts and the workbook's pacing tables. The Google integration receives the result and
 * only writes it.
 */
@Component
@RequiredArgsConstructor
public class TacticPivotAssembler {

	private final ChartPivot chartPivot;
	private final TacticLineItemGrouper lineItemGrouper;

	/**
	 * Computes the daily and monthly pivots for tactics {@code 1..tacticCount}. Each tactic's rows are filtered
	 * by its own line item ids; the monthly labels carry the year when {@code monthlyTs} spans more than one.
	 *
	 * @param bqRows          the BigQuery export rows, header row included
	 * @param lineItemMapping the tactic-to-line-item mapping
	 * @param tacticCount     number of active tactics
	 * @param dailyTs         flight window the daily series is clipped to, or {@code null} for every date
	 * @param monthlyTs       window the monthly series is clipped to (EOM charts every month since campaign
	 *                        start), or {@code null} for every date
	 * @return the pivots; both maps empty when the export lacks the Date or Impressions column
	 */
	public TacticPivots assemble(
			List<List<String>> bqRows, List<LineItemMapping> lineItemMapping, int tacticCount,
			FlightDates dailyTs, FlightDates monthlyTs) {
		Map<Integer, List<String>> lineItemIds = lineItemGrouper.groupByTactic(lineItemMapping);
		Headers headers = chartPivot.parseBqHeaders(bqRows);
		if (!headers.valid()) {
			return new TacticPivots(false, lineItemIds, Map.of(), Map.of());
		}
		boolean multiYear = chartPivot.isMultiYear(bqRows, headers, monthlyTs);
		Map<Integer, Pivot> daily = new LinkedHashMap<>();
		Map<Integer, Pivot> monthly = new LinkedHashMap<>();
		for (int n = 1; n <= tacticCount; n++) {
			List<String> ids = lineItemIds.getOrDefault(n, List.of());
			daily.put(n, chartPivot.buildDailyPivot(bqRows, ids, headers, dailyTs));
			monthly.put(n, chartPivot.buildMonthlyPivot(bqRows, ids, headers, monthlyTs, multiYear));
		}
		return new TacticPivots(true, lineItemIds, daily, monthly);
	}
}
