package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChartErrorTranslatorTest {

	private static final String ACCESS_HINT = "Tactic 3: chart template not accessible — sign in with a Google account "
			+ "that can open it, or re-share / re-home the template file so the running account has access (Google: ";
	private static final String NOT_FOUND_HINT = "Tactic 3: chart template not found — it may have been moved or "
			+ "deleted; re-share or re-create the template file (Google: ";

	@Test
	void shouldReportInaccessibleTemplateWhenForbiddenWithoutReasonTest() {
		// Given: a 403 whose body carries no error details at all
		ChartErrorTranslator translator = new ChartErrorTranslator();
		GoogleJsonResponseException ex = new GoogleJsonResponseException(
				new HttpResponseException.Builder(403, "Forbidden", new HttpHeaders()).setMessage("403 Forbidden"),
				null);

		// When:
		String warning = translator.describeChartError("Tactic 3", ex);

		// Then:
		assertThat(warning).isEqualTo(ACCESS_HINT + "403 Forbidden)");
	}

	@Test
	void shouldReportInaccessibleTemplateWhenReasonIsInsufficientFilePermissionsTest() {
		// Given:
		ChartErrorTranslator translator = new ChartErrorTranslator();
		GoogleJsonResponseException ex = googleError(403, "insufficientFilePermissions", "no access");

		// When:
		String warning = translator.describeChartError("Tactic 3", ex);

		// Then:
		assertThat(warning).isEqualTo(ACCESS_HINT + "no access)");
	}

	@Test
	void shouldReportInaccessibleTemplateWhenReasonIsForbiddenTest() {
		// Given:
		ChartErrorTranslator translator = new ChartErrorTranslator();
		GoogleJsonResponseException ex = googleError(403, "forbidden", "denied");

		// When:
		String warning = translator.describeChartError("Tactic 3", ex);

		// Then:
		assertThat(warning).isEqualTo(ACCESS_HINT + "denied)");
	}

	@Test
	void shouldReportInaccessibleTemplateWhenMessageMentionsSufficientPermissionsTest() {
		// Given: an unrecognised reason, but Google's message says the caller lacks permissions
		ChartErrorTranslator translator = new ChartErrorTranslator();
		GoogleJsonResponseException ex = googleError(403, "appNotAuthorizedToFile",
				"The caller does not have Sufficient Permissions");

		// When:
		String warning = translator.describeChartError("Tactic 3", ex);

		// Then:
		assertThat(warning).isEqualTo(ACCESS_HINT + "The caller does not have Sufficient Permissions)");
	}

	@Test
	void shouldFallBackToRawMessageWhenForbiddenForAnotherReasonTest() {
		// Given: a quota 403 is not an access problem
		ChartErrorTranslator translator = new ChartErrorTranslator();
		GoogleJsonResponseException ex = googleError(403, "rateLimitExceeded", "Rate limit exceeded");

		// When:
		String warning = translator.describeChartError("Tactic 3", ex);

		// Then:
		assertThat(warning).isEqualTo("Tactic 3: Rate limit exceeded");
	}

	@Test
	void shouldFallBackToRawMessageWhenForbiddenForAnotherReasonWithoutMessageTest() {
		// Given:
		ChartErrorTranslator translator = new ChartErrorTranslator();
		GoogleJsonResponseException ex = googleError(403, "rateLimitExceeded", null);

		// When:
		String warning = translator.describeChartError("Tactic 3", ex);

		// Then:
		assertThat(warning).isEqualTo("Tactic 3: null");
	}

	@Test
	void shouldReportMissingTemplateWhenStatusIsNotFoundTest() {
		// Given:
		ChartErrorTranslator translator = new ChartErrorTranslator();
		GoogleJsonResponseException ex = new GoogleJsonResponseException(
				new HttpResponseException.Builder(404, "Not Found", new HttpHeaders()).setMessage("File not found"),
				null);

		// When:
		String warning = translator.describeChartError("Tactic 3", ex);

		// Then:
		assertThat(warning).isEqualTo(NOT_FOUND_HINT + "File not found)");
	}

	@Test
	void shouldReportMissingTemplateWhenReasonIsNotFoundTest() {
		// Given: a non-404 status whose reason still says the file is gone
		ChartErrorTranslator translator = new ChartErrorTranslator();
		GoogleJsonResponseException ex = googleError(400, "notFound", "Requested entity was not found");

		// When:
		String warning = translator.describeChartError("Tactic 3", ex);

		// Then:
		assertThat(warning).isEqualTo(NOT_FOUND_HINT + "Requested entity was not found)");
	}

	@Test
	void shouldTreatDetailsWithoutErrorsAsMissingReasonTest() {
		// Given: details present but the errors list absent (500) and empty (403)
		ChartErrorTranslator translator = new ChartErrorTranslator();
		GoogleJsonError noErrors = new GoogleJsonError();
		GoogleJsonError emptyErrors = new GoogleJsonError();
		emptyErrors.setErrors(List.of());
		GoogleJsonResponseException serverError = new GoogleJsonResponseException(
				new HttpResponseException.Builder(500, "Internal", new HttpHeaders()).setMessage("Backend error"),
				noErrors);
		GoogleJsonResponseException forbidden = new GoogleJsonResponseException(
				new HttpResponseException.Builder(403, "Forbidden", new HttpHeaders()).setMessage("Forbidden"),
				emptyErrors);

		// When:
		String serverWarning = translator.describeChartError("Tactic 3", serverError);
		String forbiddenWarning = translator.describeChartError("Tactic 3", forbidden);

		// Then:
		assertThat(serverWarning).isEqualTo("Tactic 3: Backend error");
		assertThat(forbiddenWarning).isEqualTo(ACCESS_HINT + "Forbidden)");
	}

	@Test
	void shouldPrefixTagToMessageOfNonGoogleFailureTest() {
		// Given:
		ChartErrorTranslator translator = new ChartErrorTranslator();
		IllegalArgumentException ex = new IllegalArgumentException("Required parameter spreadsheetId must be specified");

		// When:
		String warning = translator.describeChartError("Tactic 1", ex);

		// Then:
		assertThat(warning).isEqualTo("Tactic 1: Required parameter spreadsheetId must be specified");
	}

	private static GoogleJsonResponseException googleError(int status, String reason, String message) {
		GoogleJsonError.ErrorInfo info = new GoogleJsonError.ErrorInfo();
		info.setReason(reason);
		GoogleJsonError details = new GoogleJsonError();
		details.setErrors(List.of(info));
		return new GoogleJsonResponseException(
				new HttpResponseException.Builder(status, "status", new HttpHeaders()).setMessage(message), details);
	}
}
