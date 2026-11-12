package com.aidigital.reportconstructor.externalservices.bridge;

import com.aidigital.reportconstructor.externalservices.google.sheets.SheetsClient;
import com.aidigital.reportconstructor.externalservices.google.sheets.SheetsExternalException;
import com.aidigital.reportconstructor.externalservices.google.sheets.SheetsTabNotFoundException;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.SheetsTabValues;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.GoogleConnectionStatus;
import com.aidigital.reportconstructor.service.reports.dto.SheetData;
import com.aidigital.reportconstructor.service.reports.ports.SheetQueryService;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import com.aidigital.reportconstructor.usagelogging.LogUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implements the service's {@link SheetQueryService} port on top of the provider-level
 * {@link SheetsClient} (real or stub): validates the spreadsheet URL, resolves the caller's Google
 * token, reads the tab and shapes it as {@link SheetData}; client failures become user-facing errors
 * through {@link SheetsFailureTranslator}.
 */
@Service
@RequiredArgsConstructor
public class SheetQueryServiceImpl implements SheetQueryService {

	private static final Pattern ID_PATTERN = Pattern.compile("/spreadsheets/d/([a-zA-Z0-9-_]+)");
	private static final int PREVIEW_ROWS = 5;
	private static final String URL_REQUIRED = "Spreadsheet URL is required";
	private static final String URL_UNPARSEABLE = "Could not parse Google Sheets URL. Expected /spreadsheets/d/<id>/…";

	private final SheetsClient sheets;
	private final ObjectProvider<UserGoogleTokenProvider> userGoogleTokens;
	private final SheetsFailureTranslator failures;

	/**
	 * Assembles the Google connection status for the sheets UI, reporting whether the
	 * backing client is a stub (offline) and echoing the requesting account.
	 *
	 * @param callerEmail email of the account whose connection is being reported
	 * @return status flagged as connected, marked stub when the client is not live,
	 * carrying the caller email and empty spreadsheet list/error fields
	 */
	@Override
	@LogUsage
	public GoogleConnectionStatus connectionStatus(String callerEmail) {
		return new GoogleConnectionStatus(true, !sheets.isLive(), callerEmail, List.of(), "");
	}

	/**
	 * Reads a single tab of a Google spreadsheet, preferring the caller's own Google credentials.
	 * Resolves the caller's Clerk-brokered Google OAuth token (when the optional
	 * {@link UserGoogleTokenProvider} bean is present) so the read runs as that user; a {@code null}
	 * token makes the client fall back to the service account.
	 *
	 * @param spreadsheetUrl full URL of the target Google spreadsheet
	 * @param tab            name of the worksheet tab to read within that spreadsheet
	 * @param callerUserId   caller's Clerk user id ({@code sub}); {@code null} forces
	 *                       the service-account fallback
	 * @return the tab's cell contents wrapped as {@link SheetData}
	 */
	@Override
	@LogUsage
	public SheetData fetchTab(String spreadsheetUrl, String tab, String callerUserId) {
		UserGoogleTokenProvider tokens = userGoogleTokens.getIfAvailable();
		String userGoogleToken = (tokens == null || callerUserId == null)
				? null
				: tokens.googleAccessToken(callerUserId);
		String spreadsheetId = spreadsheetId(spreadsheetUrl);
		boolean asUser = userGoogleToken != null && !userGoogleToken.isBlank();
		try {
			return toSheetData(sheets.readTab(spreadsheetId, tab, userGoogleToken));
		} catch (SheetsTabNotFoundException ex) {
			throw failures.tabNotFound(ex);
		} catch (SheetsExternalException ex) {
			throw failures.requestFailed(ex, asUser);
		}
	}

	/**
	 * Extracts the spreadsheet id from a Google Sheets URL.
	 *
	 * @param spreadsheetUrl full URL of the spreadsheet
	 * @return the spreadsheet id
	 * @throws AppException {@link ErrorReason#C002} when the URL is blank or not a Sheets URL
	 */
	String spreadsheetId(String spreadsheetUrl) {
		if (!StringUtils.hasText(spreadsheetUrl)) {
			throw new AppException(ErrorReason.C002, URL_REQUIRED);
		}
		Matcher m = ID_PATTERN.matcher(spreadsheetUrl);
		if (!m.find()) {
			throw new AppException(ErrorReason.C002, URL_UNPARSEABLE);
		}
		return m.group(1);
	}

	/**
	 * Shapes a read tab as the sheet payload the UI and report engine consume: first row as headers,
	 * the first five rows as a preview, and every row.
	 *
	 * @param values the tab read by the client
	 * @return the sheet payload
	 */
	SheetData toSheetData(SheetsTabValues values) {
		List<List<String>> rows = values.rows();
		List<String> headers = rows.isEmpty() ? List.of() : rows.get(0);
		return new SheetData(
				values.spreadsheetId(), values.title(), values.tab(), values.visibleTabs(),
				rows.size(), headers.size(), headers,
				rows.stream().limit(PREVIEW_ROWS).toList(),
				rows);
	}
}
