package com.aidigital.reportconstructor.externalservices.bridge;

import com.aidigital.reportconstructor.externalservices.google.sheets.SheetsExternalException;
import com.aidigital.reportconstructor.externalservices.google.sheets.SheetsTabNotFoundException;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.common.error.ValidationParameter;
import com.aidigital.reportconstructor.service.reports.ports.SheetQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Turns Sheets client failures into the user-facing {@link AppException}s the sheet endpoints return.
 */
@Component
public class SheetsFailureTranslator {

	private static final int FORBIDDEN = HttpStatus.FORBIDDEN.value();
	private static final int NOT_FOUND = HttpStatus.NOT_FOUND.value();
	private static final String TAB_NOT_FOUND_MESSAGE_PARAM = "param0";
	private static final String USER_CANNOT_OPEN = "Your Google account can't open this spreadsheet — make sure "
			+ "you're signed in with an account that has at least Viewer access, or check the link";
	private static final String SERVICE_ACCOUNT_CANNOT_OPEN = "The report's Google service account can't open "
			+ "this spreadsheet — share it with the service account (Viewer access), or check the link";
	private static final String REQUEST_FAILED = "Google Sheets request failed";
	private static final String FETCH_FAILED = "Google Sheets fetch failed: ";

	/**
	 * A missing tab becomes {@link ErrorReason#C001} carrying the visible tabs as a structured parameter,
	 * so the web layer can offer them as a manual media-plan tab picker instead of failing outright.
	 *
	 * @param ex the client's tab-not-found failure
	 * @return the exception to throw
	 */
	public AppException tabNotFound(SheetsTabNotFoundException ex) {
		return new AppException(ErrorReason.C001,
				new ValidationParameter(TAB_NOT_FOUND_MESSAGE_PARAM, ex.getMessage()),
				new ValidationParameter(SheetQueryService.TAB_NOT_FOUND_TABS_PARAM,
						String.join(SheetQueryService.TAB_NOT_FOUND_TABS_DELIMITER, ex.visibleTabs())));
	}

	/**
	 * Any other failure becomes {@link ErrorReason#C000}. A 403/404 explains whose access is missing
	 * (the user's own account or the service account); Google's message and status are appended.
	 *
	 * @param ex     the client failure
	 * @param asUser whether the read ran with the user's own Google token
	 * @return the exception to throw
	 */
	public AppException requestFailed(SheetsExternalException ex, boolean asUser) {
		Integer code = ex.statusCode();
		if (code == null) {
			return new AppException(ErrorReason.C000, FETCH_FAILED + ex.providerMessage());
		}
		String hint;
		if (code == FORBIDDEN || code == NOT_FOUND) {
			hint = asUser ? USER_CANNOT_OPEN : SERVICE_ACCOUNT_CANNOT_OPEN;
		} else {
			hint = REQUEST_FAILED;
		}
		String googleMsg = ex.providerMessage();
		return new AppException(ErrorReason.C000,
				hint + (googleMsg != null && !googleMsg.isBlank() ? " (" + googleMsg + ")" : "")
						+ " [HTTP " + code + "]");
	}
}
