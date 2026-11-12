package com.aidigital.reportconstructor.externalservices.bridge;

import com.aidigital.reportconstructor.externalservices.google.GoogleApiException;
import com.aidigital.reportconstructor.externalservices.google.deck.DeckBreakdown;
import com.aidigital.reportconstructor.externalservices.google.sheets.SheetWorkbookClient;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookGeoRow;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookGeoTable;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookPacingRequest;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.common.error.ValidationParameter;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.GeoRow;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import com.aidigital.reportconstructor.service.reports.engine.TacticPivotAssembler;
import com.aidigital.reportconstructor.service.reports.model.TacticPivots;
import com.aidigital.reportconstructor.service.reports.ports.PacingTablesRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SheetDeckProviderBridgeTest {

	@Test
	void shouldPivotTheExportAndHandTheTablesToTheWorkbookTest() {
		// Given: an EOM request whose monthly window is wider than its daily one
		SheetWorkbookClient workbook = mock(SheetWorkbookClient.class);
		TacticPivotAssembler assembler = mock(TacticPivotAssembler.class);
		FlightDates daily = new FlightDates(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
		FlightDates monthly = new FlightDates(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 31));
		List<List<String>> rows = List.of(List.of("Date", "Impressions"));
		List<LineItemMapping> mapping = List.of(new LineItemMapping("Display", "li-1", 1));
		PacingTablesRequest request = new PacingTablesRequest(rows, mapping, daily, monthly, 1, Map.of(1, "Display"),
				Map.of(1, 10.0), 10.0, Map.of(1, "ctr"), "ya29.user");
		Pivot pivot = new Pivot(new LinkedHashMap<>(Map.of("2026-10-01", new double[] {10, 1, 0})), true, false);
		when(assembler.assemble(rows, mapping, 1, daily, monthly)).thenReturn(
				new TacticPivots(true, Map.of(1, List.of("li-1")), Map.of(1, pivot), Map.of(1, pivot)));
		SheetDeckProviderBridge bridge = new SheetDeckProviderBridge(workbook, assembler, new ChartModelMapper(),
				new DeckModelMapper(), new WorkbookModelMapper());

		// When:
		List<String> errors = bridge.writePacingTables("wb-1", request);

		// Then: the workbook receives the pivots, not the export
		ArgumentCaptor<WorkbookPacingRequest> sent = ArgumentCaptor.forClass(WorkbookPacingRequest.class);
		verify(workbook).writePacingTables(eq("wb-1"), sent.capture());
		assertThat(errors).isEmpty();
		assertThat(sent.getValue().pacing().bqColumnsFound()).isTrue();
		assertThat(sent.getValue().pacing().daily().byTactic().get(1).data()).containsOnlyKeys("2026-10-01");
		assertThat(sent.getValue().pacing().monthly().fromSheet()).isFalse();
		assertThat(sent.getValue().tacticKpiTypes()).isEqualTo(Map.of(1, "ctr"));
		assertThat(sent.getValue().userGoogleAccessToken()).isEqualTo("ya29.user");
	}

	@Test
	void shouldFallBackToTheFlightWindowForTheMonthlyPivotsTest() {
		// Given: an EOC request without a separate monthly window
		SheetWorkbookClient workbook = mock(SheetWorkbookClient.class);
		TacticPivotAssembler assembler = mock(TacticPivotAssembler.class);
		FlightDates flight = new FlightDates(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
		PacingTablesRequest request = new PacingTablesRequest(List.of(), List.of(), flight, null, 1, Map.of(),
				Map.of(), 0, Map.of(), null);
		when(assembler.assemble(List.of(), List.of(), 1, flight, flight))
				.thenReturn(new TacticPivots(false, Map.of(), Map.of(), Map.of()));
		SheetDeckProviderBridge bridge = new SheetDeckProviderBridge(workbook, assembler, new ChartModelMapper(),
				new DeckModelMapper(), new WorkbookModelMapper());

		// When:
		bridge.writePacingTables("wb-1", request);

		// Then:
		verify(assembler).assemble(List.of(), List.of(), 1, flight, flight);
	}

	@Test
	void shouldMapTheSectionsToClearAndTheTablesReadBackTest() {
		// Given:
		SheetWorkbookClient workbook = mock(SheetWorkbookClient.class);
		when(workbook.readGeoTables("wb-1", Set.of(1), null)).thenReturn(Map.of(1,
				new WorkbookGeoTable("12", "NY", "1%", List.of(new WorkbookGeoRow("NY", "500", "1%")))));
		SheetDeckProviderBridge bridge = new SheetDeckProviderBridge(workbook, mock(TacticPivotAssembler.class),
				new ChartModelMapper(), new DeckModelMapper(), new WorkbookModelMapper());

		// When:
		bridge.clearBreakdowns("wb-1", Map.of(1, EnumSet.of(BreakdownType.GEO)), null);
		Map<Integer, GeoTable> geo = bridge.readGeoTables("wb-1", Set.of(1), null);

		// Then:
		verify(workbook).clearBreakdowns("wb-1", Map.of(1, EnumSet.of(DeckBreakdown.GEO)), null);
		assertThat(geo).isEqualTo(Map.of(1, new GeoTable("12", "NY", "1%", List.of(new GeoRow("NY", "500", "1%")))));
	}

	@Test
	void shouldTurnAGoogleFailureIntoTheSameJobErrorTest() {
		// Given:
		SheetWorkbookClient workbook = mock(SheetWorkbookClient.class);
		doThrow(new GoogleApiException("Google Sheets trimTactics failed: 500"))
				.when(workbook).trimTactics("wb-1", 3, null);
		SheetDeckProviderBridge bridge = new SheetDeckProviderBridge(workbook, mock(TacticPivotAssembler.class),
				new ChartModelMapper(), new DeckModelMapper(), new WorkbookModelMapper());

		// When-Then:
		assertThatThrownBy(() -> bridge.trimTactics("wb-1", 3, null))
				.asInstanceOf(type(AppException.class))
				.satisfies(ex -> {
					assertThat(ex.getCode()).isEqualTo(ErrorReason.C000.getCode());
					assertThat(ex.getValidationMessage().getParameters()).containsExactly(
							new ValidationParameter("param0", "Google Sheets trimTactics failed: 500"));
				});
	}
}
