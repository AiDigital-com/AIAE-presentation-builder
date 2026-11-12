package com.aidigital.reportconstructor.service.integration;

import com.aidigital.reportconstructor.externalservices.google.sheets.SheetsExternalException;
import com.aidigital.reportconstructor.externalservices.google.sheets.SheetsTabNotFoundException;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.common.error.ValidationParameter;
import com.aidigital.reportconstructor.service.reports.ports.SheetQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SheetsFailureTranslatorTest {

	@Test
	void shouldCarryTheVisibleTabsWhenATabIsMissingTest() {
		// Given:
		SheetsFailureTranslator translator = new SheetsFailureTranslator();
		SheetsTabNotFoundException failure = new SheetsTabNotFoundException("Geo", List.of("Proposal", "Basic"));

		// When:
		AppException ex = translator.tabNotFound(failure);

		// Then:
		assertThat(ex.getCode()).isEqualTo(ErrorReason.C001.getCode());
		assertThat(ex.getValidationMessage().getParameters()).containsExactly(
				new ValidationParameter("param0", "Tab \"Geo\" not found among visible tabs: Proposal, Basic"),
				new ValidationParameter(SheetQueryService.TAB_NOT_FOUND_TABS_PARAM,
						"Proposal" + SheetQueryService.TAB_NOT_FOUND_TABS_DELIMITER + "Basic"));
	}

	@Test
	void shouldBlameTheUsersAccountWhenTheirReadIsForbiddenTest() {
		// Given:
		SheetsFailureTranslator translator = new SheetsFailureTranslator();
		SheetsExternalException failure = new SheetsExternalException(403, "No access", null);

		// When:
		AppException ex = translator.requestFailed(failure, true);

		// Then:
		assertThat(ex.getCode()).isEqualTo(ErrorReason.C000.getCode());
		assertThat(ex.getValidationMessage().getParameters()).containsExactly(new ValidationParameter("param0",
				"Your Google account can't open this spreadsheet — make sure you're signed in with an account "
						+ "that has at least Viewer access, or check the link (No access) [HTTP 403]"));
	}

	@Test
	void shouldBlameTheServiceAccountWhenItsReadIsNotFoundTest() {
		// Given:
		SheetsFailureTranslator translator = new SheetsFailureTranslator();
		SheetsExternalException failure = new SheetsExternalException(404, " ", null);

		// When:
		AppException ex = translator.requestFailed(failure, false);

		// Then: a blank Google message is left out
		assertThat(ex.getValidationMessage().getParameters()).containsExactly(new ValidationParameter("param0",
				"The report's Google service account can't open this spreadsheet — share it with the service "
						+ "account (Viewer access), or check the link [HTTP 404]"));
	}

	@Test
	void shouldUseAGenericHintForOtherStatusesTest() {
		// Given:
		SheetsFailureTranslator translator = new SheetsFailureTranslator();
		SheetsExternalException failure = new SheetsExternalException(500, "Backend error", null);

		// When:
		AppException ex = translator.requestFailed(failure, true);

		// Then:
		assertThat(ex.getValidationMessage().getParameters()).containsExactly(
				new ValidationParameter("param0", "Google Sheets request failed (Backend error) [HTTP 500]"));
	}

	@Test
	void shouldReportTransportFailuresWithTheirMessageTest() {
		// Given:
		SheetsFailureTranslator translator = new SheetsFailureTranslator();
		SheetsExternalException failure = new SheetsExternalException(null, "Read timed out", null);

		// When:
		AppException ex = translator.requestFailed(failure, false);

		// Then:
		assertThat(ex.getCode()).isEqualTo(ErrorReason.C000.getCode());
		assertThat(ex.getValidationMessage().getParameters()).containsExactly(
				new ValidationParameter("param0", "Google Sheets fetch failed: Read timed out"));
	}
}
