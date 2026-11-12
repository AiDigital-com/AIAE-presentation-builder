package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.model.TacticPivots;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TacticPivotAssemblerTest {

	@Test
	void shouldPivotEveryTacticByItsOwnLineItemsTest() {
		// Given: two tactics, tactic 2 without mapped line items; the monthly window spans two years
		ChartPivot chartPivot = mock(ChartPivot.class);
		List<List<String>> rows = List.of(List.of("Date", "Impressions"), List.of("2025-12-31", "10"));
		Headers headers = new Headers(0, 1, -1, -1, -1);
		FlightDates daily = new FlightDates(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
		FlightDates monthly = new FlightDates(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 31));
		Pivot daily1 = new Pivot(new LinkedHashMap<>(Map.of("2026-01-01", new double[] {10, 0, 0})), false, false);
		Pivot monthly1 = new Pivot(new LinkedHashMap<>(Map.of("January 2026", new double[] {10, 0, 0})), false, false);
		Pivot empty = new Pivot(new LinkedHashMap<>(), false, false);
		when(chartPivot.parseBqHeaders(rows)).thenReturn(headers);
		when(chartPivot.isMultiYear(rows, headers, monthly)).thenReturn(true);
		when(chartPivot.buildDailyPivot(rows, List.of("li-1"), headers, daily)).thenReturn(daily1);
		when(chartPivot.buildMonthlyPivot(rows, List.of("li-1"), headers, monthly, true)).thenReturn(monthly1);
		when(chartPivot.buildDailyPivot(rows, List.of(), headers, daily)).thenReturn(empty);
		when(chartPivot.buildMonthlyPivot(rows, List.of(), headers, monthly, true)).thenReturn(empty);
		TacticPivotAssembler assembler = new TacticPivotAssembler(chartPivot, new TacticLineItemGrouper());

		// When:
		TacticPivots pivots = assembler.assemble(
				rows, List.of(new LineItemMapping("Display", "li-1", 1)), 2, daily, monthly);

		// Then:
		assertThat(pivots.columnsFound()).isTrue();
		assertThat(pivots.lineItemIds()).isEqualTo(Map.of(1, List.of("li-1")));
		assertThat(pivots.daily()).containsExactly(Map.entry(1, daily1), Map.entry(2, empty));
		assertThat(pivots.monthly()).containsExactly(Map.entry(1, monthly1), Map.entry(2, empty));
	}

	@Test
	void shouldPivotNothingWhenTheExportLacksTheRequiredColumnsTest() {
		// Given:
		ChartPivot chartPivot = mock(ChartPivot.class);
		List<List<String>> rows = List.of(List.of("Day", "Imps"));
		when(chartPivot.parseBqHeaders(rows)).thenReturn(new Headers(-1, -1, -1, -1, -1));
		TacticPivotAssembler assembler = new TacticPivotAssembler(chartPivot, new TacticLineItemGrouper());

		// When:
		TacticPivots pivots = assembler.assemble(rows, List.of(), 3, null, null);

		// Then: the line item ids are still reported, no pivot is attempted
		assertThat(pivots.columnsFound()).isFalse();
		assertThat(pivots.daily()).isEmpty();
		assertThat(pivots.monthly()).isEmpty();
		verify(chartPivot).parseBqHeaders(rows);
		verifyNoMoreInteractions(chartPivot);
	}
}
