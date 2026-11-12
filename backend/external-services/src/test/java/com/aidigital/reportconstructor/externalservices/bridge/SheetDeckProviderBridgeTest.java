package com.aidigital.reportconstructor.externalservices.bridge;

import com.aidigital.reportconstructor.externalservices.google.GoogleApiException;
import com.aidigital.reportconstructor.externalservices.google.SheetPacingTableWriter;
import com.aidigital.reportconstructor.externalservices.google.deck.DeckBreakdown;
import com.aidigital.reportconstructor.externalservices.google.sheets.SheetWorkbookClient;
import com.aidigital.reportconstructor.externalservices.google.sheets.WorkbookTabWriter;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookGeoRow;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookGeoTable;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.common.error.ValidationParameter;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.GeoRow;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
import com.aidigital.reportconstructor.service.reports.ports.PacingTablesRequest;
import com.google.api.services.sheets.v4.Sheets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.EnumSet;
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
	void shouldWriteThePacingTablesThroughTheTabTheClientResolvedTest() throws IOException {
		// Given: the client hands its authenticated Sheets client and first tab to the writer it receives
		SheetWorkbookClient workbook = mock(SheetWorkbookClient.class);
		SheetPacingTableWriter pacing = mock(SheetPacingTableWriter.class);
		Sheets sheets = mock(Sheets.class);
		PacingTablesRequest request = new PacingTablesRequest(
				List.of(), List.of(), null, null, 2, Map.of(), Map.of(), 0, Map.of(), "ya29.user");
		when(pacing.writeAll(sheets, "wb-1", "Report", request)).thenReturn(List.of("tactic 2 skipped"));
		ArgumentCaptor<WorkbookTabWriter> writer = ArgumentCaptor.forClass(WorkbookTabWriter.class);
		when(workbook.writePacingTables(eq("wb-1"), eq("ya29.user"), writer.capture()))
				.thenAnswer(invocation -> writer.getValue().write(sheets, "wb-1", "Report"));
		SheetDeckProviderBridge bridge =
				new SheetDeckProviderBridge(workbook, pacing, new DeckModelMapper(), new WorkbookModelMapper());

		// When:
		List<String> errors = bridge.writePacingTables("wb-1", request);

		// Then:
		assertThat(errors).containsExactly("tactic 2 skipped");
	}

	@Test
	void shouldMapTheSectionsToClearAndTheTablesReadBackTest() {
		// Given:
		SheetWorkbookClient workbook = mock(SheetWorkbookClient.class);
		when(workbook.readGeoTables("wb-1", Set.of(1), null)).thenReturn(Map.of(1,
				new WorkbookGeoTable("12", "NY", "1%", List.of(new WorkbookGeoRow("NY", "500", "1%")))));
		SheetDeckProviderBridge bridge = new SheetDeckProviderBridge(
				workbook, mock(SheetPacingTableWriter.class), new DeckModelMapper(), new WorkbookModelMapper());

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
		SheetDeckProviderBridge bridge = new SheetDeckProviderBridge(
				workbook, mock(SheetPacingTableWriter.class), new DeckModelMapper(), new WorkbookModelMapper());

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
