package com.aidigital.reportconstructor.externalservices.bridge;

import com.aidigital.reportconstructor.externalservices.google.sheets.SheetsClient;
import com.aidigital.reportconstructor.externalservices.google.sheets.SheetsExternalException;
import com.aidigital.reportconstructor.externalservices.google.sheets.SheetsTabNotFoundException;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.SheetsTabValues;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.GoogleConnectionStatus;
import com.aidigital.reportconstructor.service.reports.dto.SheetData;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SheetQueryServiceImplTest {

	private static final String URL = "https://docs.google.com/spreadsheets/d/sheet-1/edit#gid=0";

	@Test
	@SuppressWarnings("unchecked")
	void shouldReadAsTheCallerAndShapeTheSheetDataTest() {
		// Given: six rows, so the preview is cut to five
		SheetsClient sheets = mock(SheetsClient.class);
		UserGoogleTokenProvider provider = mock(UserGoogleTokenProvider.class);
		when(provider.googleAccessToken("user_1")).thenReturn("ya29.user");
		ObjectProvider<UserGoogleTokenProvider> tokens = mock(ObjectProvider.class);
		when(tokens.getIfAvailable()).thenReturn(provider);
		List<List<String>> rows = List.of(List.of("Line", "Budget"), List.of("a", "1"), List.of("b", "2"),
				List.of("c", "3"), List.of("d", "4"), List.of("e", "5"));
		when(sheets.readTab("sheet-1", "Proposal", "ya29.user"))
				.thenReturn(new SheetsTabValues("sheet-1", "Plan", "Proposal ", List.of("Proposal ", "Geo"), rows));
		SheetQueryServiceImpl service = new SheetQueryServiceImpl(sheets, tokens, new SheetsFailureTranslator());

		// When:
		SheetData data = service.fetchTab(URL, "Proposal", "user_1");

		// Then:
		assertThat(data).isEqualTo(new SheetData("sheet-1", "Plan", "Proposal ", List.of("Proposal ", "Geo"),
				6, 2, List.of("Line", "Budget"), rows.subList(0, 5), rows));
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldReadAsTheServiceAccountWhenThereIsNoTokenProviderTest() {
		// Given:
		SheetsClient sheets = mock(SheetsClient.class);
		ObjectProvider<UserGoogleTokenProvider> tokens = mock(ObjectProvider.class);
		when(tokens.getIfAvailable()).thenReturn(null);
		when(sheets.readTab("sheet-1", "Geo", null))
				.thenReturn(new SheetsTabValues("sheet-1", "Plan", "Geo", List.of("Geo"), List.of()));
		SheetQueryServiceImpl service = new SheetQueryServiceImpl(sheets, tokens, new SheetsFailureTranslator());

		// When:
		SheetData data = service.fetchTab(URL, "Geo", "user_1");

		// Then: an empty tab has no headers
		assertThat(data.headers()).isEmpty();
		assertThat(data.rows()).isZero();
		assertThat(data.rawRows()).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldRejectABlankUrlTest() {
		// Given:
		SheetsClient sheets = mock(SheetsClient.class);
		ObjectProvider<UserGoogleTokenProvider> tokens = mock(ObjectProvider.class);
		when(tokens.getIfAvailable()).thenReturn(null);
		SheetQueryServiceImpl service = new SheetQueryServiceImpl(sheets, tokens, new SheetsFailureTranslator());

		// When-Then:
		assertThatThrownBy(() -> service.fetchTab(" ", "Geo", null))
				.isInstanceOf(AppException.class)
				.extracting(ex -> ((AppException) ex).getCode())
				.isEqualTo(ErrorReason.C002.getCode());
		verifyNoInteractions(sheets);
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldRejectAUrlThatIsNotASpreadsheetTest() {
		// Given:
		SheetsClient sheets = mock(SheetsClient.class);
		ObjectProvider<UserGoogleTokenProvider> tokens = mock(ObjectProvider.class);
		when(tokens.getIfAvailable()).thenReturn(null);
		SheetQueryServiceImpl service = new SheetQueryServiceImpl(sheets, tokens, new SheetsFailureTranslator());

		// When-Then:
		assertThatThrownBy(() -> service.fetchTab("https://docs.google.com/document/d/x", "Geo", null))
				.isInstanceOf(AppException.class)
				.extracting(ex -> ((AppException) ex).getCode())
				.isEqualTo(ErrorReason.C002.getCode());
		verifyNoInteractions(sheets);
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldTranslateAMissingTabTest() {
		// Given:
		SheetsClient sheets = mock(SheetsClient.class);
		when(sheets.readTab("sheet-1", "Geo", null))
				.thenThrow(new SheetsTabNotFoundException("Geo", List.of("Proposal")));
		ObjectProvider<UserGoogleTokenProvider> tokens = mock(ObjectProvider.class);
		when(tokens.getIfAvailable()).thenReturn(null);
		SheetQueryServiceImpl service = new SheetQueryServiceImpl(sheets, tokens, new SheetsFailureTranslator());

		// When-Then:
		assertThatThrownBy(() -> service.fetchTab(URL, "Geo", null))
				.isInstanceOf(AppException.class)
				.extracting(ex -> ((AppException) ex).getCode())
				.isEqualTo(ErrorReason.C001.getCode());
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldTranslateAForbiddenReadAsTheUsersAccessTest() {
		// Given:
		SheetsClient sheets = mock(SheetsClient.class);
		when(sheets.readTab("sheet-1", "Geo", "ya29.user"))
				.thenThrow(new SheetsExternalException(403, "No access", null));
		UserGoogleTokenProvider provider = mock(UserGoogleTokenProvider.class);
		when(provider.googleAccessToken("user_1")).thenReturn("ya29.user");
		ObjectProvider<UserGoogleTokenProvider> tokens = mock(ObjectProvider.class);
		when(tokens.getIfAvailable()).thenReturn(provider);
		SheetQueryServiceImpl service = new SheetQueryServiceImpl(sheets, tokens, new SheetsFailureTranslator());

		// When-Then:
		assertThatThrownBy(() -> service.fetchTab(URL, "Geo", "user_1"))
				.isInstanceOf(AppException.class)
				.hasMessageContaining("Your Google account can't open this spreadsheet");
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldReportStubModeInTheConnectionStatusTest() {
		// Given:
		SheetsClient sheets = mock(SheetsClient.class);
		when(sheets.isLive()).thenReturn(false);
		ObjectProvider<UserGoogleTokenProvider> tokens = mock(ObjectProvider.class);
		when(tokens.getIfAvailable()).thenReturn(null);
		SheetQueryServiceImpl service = new SheetQueryServiceImpl(sheets, tokens, new SheetsFailureTranslator());

		// When:
		GoogleConnectionStatus status = service.connectionStatus("me@x.com");

		// Then:
		assertThat(status).isEqualTo(new GoogleConnectionStatus(true, true, "me@x.com", List.of(), ""));
	}

}
