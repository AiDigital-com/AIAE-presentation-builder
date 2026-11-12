package com.aidigital.reportconstructor.externalservices.bridge;

import com.aidigital.reportconstructor.externalservices.google.GoogleApiException;
import com.aidigital.reportconstructor.externalservices.google.deck.model.TacticPacing;
import com.aidigital.reportconstructor.externalservices.google.sheets.SheetWorkbookClient;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookPacingRequest;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.AudienceTable;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.DeviceTable;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;
import com.aidigital.reportconstructor.service.reports.engine.TacticPivotAssembler;
import com.aidigital.reportconstructor.service.reports.model.TacticPivots;
import com.aidigital.reportconstructor.service.reports.ports.PacingTablesRequest;
import com.aidigital.reportconstructor.service.reports.ports.SheetDeckProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Implements the service's {@link SheetDeckProvider} port on top of the Google integration's
 * {@link SheetWorkbookClient} (real or stub): maps the breakdown sections and the tables read back, and turns
 * a {@link GoogleApiException} into the {@link AppException} the report pipeline records, with the same
 * message. The pacing tables' pivots are computed here by the service's {@link TacticPivotAssembler}; the client
 * only places them.
 */
@Component
@RequiredArgsConstructor
public class SheetDeckProviderBridge implements SheetDeckProvider {

	private final SheetWorkbookClient workbook;
	private final TacticPivotAssembler pivots;
	private final ChartModelMapper chartMapper;
	private final DeckModelMapper deckMapper;
	private final WorkbookModelMapper tableMapper;

	@Override
	public boolean isLive() {
		return workbook.isLive();
	}

	@Override
	public String createSheet(
			String jobId, String fileName, Map<String, String> placeholderMap, String reportType,
			String userGoogleAccessToken) {
		return call(() -> workbook.createSheet(jobId, fileName, placeholderMap, reportType, userGoogleAccessToken));
	}

	@Override
	public void trimTactics(String spreadsheetId, int tacticCount, String userGoogleAccessToken) {
		call(() -> {
			workbook.trimTactics(spreadsheetId, tacticCount, userGoogleAccessToken);
			return null;
		});
	}

	@Override
	public List<String> writePacingTables(String spreadsheetId, PacingTablesRequest request) {
		// The monthly blocks may span a wider window than the daily ones (EOM reports on a single month but
		// charts every month since campaign start), so they are pivoted over their own window.
		FlightDates monthlyTs = request.monthlyTs() != null ? request.monthlyTs() : request.flightTs();
		TacticPivots computed = pivots.assemble(request.bqRows(), request.lineItemMapping(), request.tacticCount(),
				request.flightTs(), monthlyTs);
		TacticPacing pacing = new TacticPacing(computed.columnsFound(), computed.lineItemIds(),
				chartMapper.series(false, computed.daily()), chartMapper.series(false, computed.monthly()));
		return call(() -> workbook.writePacingTables(spreadsheetId, new WorkbookPacingRequest(
				request.tacticCount(), pacing, request.distTacticNames(), request.distTacticImps(),
				request.distTotalImps(), request.tacticKpiTypes(), request.userGoogleAccessToken())));
	}

	@Override
	public List<List<String>> readSheetGrid(String spreadsheetId, String userGoogleAccessToken) {
		return call(() -> workbook.readSheetGrid(spreadsheetId, userGoogleAccessToken));
	}

	@Override
	public void clearBreakdowns(
			String spreadsheetId, Map<Integer, Set<BreakdownType>> enabledByTactic, String userGoogleAccessToken) {
		call(() -> {
			workbook.clearBreakdowns(spreadsheetId, deckMapper.breakdownsByTactic(enabledByTactic),
					userGoogleAccessToken);
			return null;
		});
	}

	@Override
	public Map<Integer, List<PublisherRow>> readPublisherTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken) {
		return call(() -> tableMapper.publisherTables(
				workbook.readPublisherTables(spreadsheetId, tacticNums, userGoogleAccessToken)));
	}

	@Override
	public Map<Integer, CreativeTable> readCreativeTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken) {
		return call(() -> tableMapper.creativeTables(
				workbook.readCreativeTables(spreadsheetId, tacticNums, userGoogleAccessToken)));
	}

	@Override
	public Map<Integer, GeoTable> readGeoTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken) {
		return call(() -> tableMapper.geoTables(
				workbook.readGeoTables(spreadsheetId, tacticNums, userGoogleAccessToken)));
	}

	@Override
	public Map<Integer, AudienceTable> readAudienceTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken) {
		return call(() -> tableMapper.audienceTables(
				workbook.readAudienceTables(spreadsheetId, tacticNums, userGoogleAccessToken)));
	}

	@Override
	public Map<Integer, DeviceTable> readDeviceTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken) {
		return call(() -> tableMapper.deviceTables(
				workbook.readDeviceTables(spreadsheetId, tacticNums, userGoogleAccessToken)));
	}

	/**
	 * Runs a workbook operation, translating its failure for the report pipeline.
	 *
	 * @param operation the client call
	 * @param <T>       the result type
	 * @return the operation's result
	 * @throws AppException {@link ErrorReason#C000} carrying the client's message when the call fails
	 */
	<T> T call(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (GoogleApiException ex) {
			throw new AppException(ErrorReason.C000, ex.getMessage());
		}
	}
}
