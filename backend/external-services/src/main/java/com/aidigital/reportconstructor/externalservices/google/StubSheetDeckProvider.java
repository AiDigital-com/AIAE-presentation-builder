package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookAudienceTable;
import com.aidigital.reportconstructor.externalservices.google.deck.DeckBreakdown;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookCreativeTable;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookDeviceTable;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookGeoTable;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookPublisherRow;
import com.aidigital.reportconstructor.externalservices.google.sheets.WorkbookTabWriter;
import com.aidigital.reportconstructor.externalservices.google.sheets.SheetWorkbookClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic Sheets provider — the only candidate when no {@code @Primary}
 * real Sheets-deck bean is registered (i.e. when {@code GOOGLE_SERVICE_ACCOUNT_JSON}
 * is unset and {@link RealSheetDeckProvider} stays conditional-excluded).
 *
 * <p>Fabricates the template URL with the job-id suffix so the "Generate Sheet"
 * flow remains end-to-end runnable without Google access. Mirrors
 * {@link StubSlidesProvider}.
 */
@Component
@RequiredArgsConstructor
public class StubSheetDeckProvider implements SheetWorkbookClient {

	private final GoogleProperties props;

	@Override
	public boolean isLive() {
		return false;
	}

	@Override
	public String createSheet(
			String jobId, String fileName, Map<String, String> placeholderMap, String reportType,
			String userGoogleAccessToken) {
		String eomTemplateId = props.getEomSheetsTemplateId();
		String templateId = "EOM".equals(reportType) && eomTemplateId != null && !eomTemplateId.isBlank()
				? eomTemplateId : props.getSheetsTemplateId();
		return "https://docs.google.com/spreadsheets/d/" + templateId + "/edit?stub=" + jobId;
	}

	@Override
	public void trimTactics(String spreadsheetId, int tacticCount, String userGoogleAccessToken) {
		// No-op: the stub never clones a real workbook, so there are no ranges to clear.
	}

	@Override
	public List<String> writePacingTables(
			String spreadsheetId, String userGoogleAccessToken, WorkbookTabWriter writer) {
		// No-op: the stub never clones a real workbook, so there are no tables to write.
		return List.of();
	}

	@Override
	public List<List<String>> readSheetGrid(String spreadsheetId, String userGoogleAccessToken) {
		// No workbook is ever cloned offline, so there is no grid to read back.
		return List.of();
	}

	@Override
	public void clearBreakdowns(
			String spreadsheetId, Map<Integer, Set<DeckBreakdown>> enabledByTactic, String userGoogleAccessToken) {
		// No-op: the stub never clones a real workbook, so there is no "Breakdowns" tab to clear.
	}

	@Override
	public Map<Integer, List<WorkbookPublisherRow>> readPublisherTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken) {
		// The stub never clones a real workbook, so there are no hand-entered publisher rows to read.
		return Map.of();
	}

	@Override
	public Map<Integer, WorkbookCreativeTable> readCreativeTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken) {
		// The stub never clones a real workbook, so there are no hand-entered creative rows to read.
		return Map.of();
	}

	@Override
	public Map<Integer, WorkbookGeoTable> readGeoTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken) {
		// The stub never clones a real workbook, so there are no hand-entered geo rows to read.
		return Map.of();
	}

	@Override
	public Map<Integer, WorkbookAudienceTable> readAudienceTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken) {
		// The stub never clones a real workbook, so there are no hand-entered audience rows to read.
		return Map.of();
	}

	@Override
	public Map<Integer, WorkbookDeviceTable> readDeviceTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken) {
		// The stub never clones a real workbook, so there are no hand-entered device rows to read.
		return Map.of();
	}
}
