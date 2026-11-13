package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.externalservices.google.deck.DeckBreakdown;
import com.aidigital.reportconstructor.externalservices.google.drive.DriveShareRecipients;
import com.aidigital.reportconstructor.externalservices.google.drive.DriveSharer;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookAudienceAgeRow;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookAudienceSegmentRow;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookAudienceTable;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookCreativeRow;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookCreativeTable;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookDeviceRow;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookDeviceTable;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookGeoRow;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookGeoTable;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookPacingRequest;
import com.aidigital.reportconstructor.externalservices.google.sheets.model.WorkbookPublisherRow;
import com.aidigital.reportconstructor.observability.external.ExternalCallTimer;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.FindReplaceRequest;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link RealSheetDeckProvider}'s Google-facing operations end to end against a recording fake
 * HTTP transport: the real Sheets/Drive SDK clients and the real retrier build and send every request, and
 * the tests assert what reached Google (URLs, request bodies, ordering) and how failures are translated.
 */
class RealSheetDeckProviderApiTest {

	private static final String VALUES = "/values/";
	private static final String BATCH_UPDATE = ":batchUpdate";
	private static final String COPY = "/copy";
	private static final String METADATA = "?fields=sheets";
	private static final String TWO_TABS = "{\"sheets\": ["
			+ "{\"properties\": {\"sheetId\": 11, \"title\": \"Report\"}},"
			+ "{\"properties\": {\"sheetId\": 22, \"title\": \"Breakdowns\"}}]}";
	private static final String REPORT_TAB_ONLY = "{\"sheets\": ["
			+ "{\"properties\": {\"sheetId\": 11, \"title\": \"Report\"}}]}";
	private static final String NO_TABS = "{}";
	private static final String FORBIDDEN = "{\"error\": {\"code\": 403, \"message\": \"caller lacks permission\"}}";

	@Test
	void shouldCopyTheTemplateIntoTheFolderReplaceTokensAndShareTheWorkbookTest() throws IOException {
		// Given: a configured target folder, a workbook with a first tab and a "Breakdowns" tab, and three
		// placeholders — a first-tab-only token, a token the Breakdowns tab also carries, and a null value
		RecordingGoogleTransport transport = new RecordingGoogleTransport()
				.respond(BATCH_UPDATE, 200, "{}")
				.respond(COPY, 200, "{\"id\": \"wb-new\", \"webViewLink\": \"https://x\"}")
				.respond(METADATA, 200, TWO_TABS);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		GoogleProperties props = new GoogleProperties();
		props.setSheetsTemplateId("tpl-eoc");
		props.setEomSheetsTemplateId("tpl-eom");
		props.setSheetsTargetFolderId("  folder-9  ");
		DriveSharer driveSharer = mock(DriveSharer.class);
		DriveShareRecipients recipients = mock(DriveShareRecipients.class);
		when(recipients.resolve()).thenReturn(List.of("ops@example.com"));
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, props, mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				driveSharer, recipients);
		Map<String, String> placeholders = new LinkedHashMap<>();
		placeholders.put("{{client}}", "Acme");
		placeholders.put("{{tactic 1 imps}}", "1,200");
		placeholders.put("{{notes}}", null);

		// When:
		String url = provider.createSheet("job-7", "Acme EOC", placeholders, "EOC", null);

		// Then: the EOC template is copied into the trimmed folder, across shared drives
		assertThat(url).isEqualTo("https://docs.google.com/spreadsheets/d/wb-new/edit");
		assertThat(transport.methods()).containsExactly("POST", "GET", "POST");
		assertThat(transport.urls().get(0)).contains("/files/tpl-eoc/copy").contains("supportsAllDrives=true");
		File copy = GsonFactory.getDefaultInstance().fromString(transport.bodies().get(0), File.class);
		assertThat(copy.getName()).isEqualTo("Acme EOC");
		assertThat(copy.getParents()).containsExactly("folder-9");
		assertThat(transport.urls().get(1)).contains("/spreadsheets/wb-new?fields=sheets");

		// Then: every token is scoped to the first tab, and only the Breakdowns-tab token is re-sent there
		assertThat(transport.urls().get(2)).contains("/spreadsheets/wb-new:batchUpdate");
		List<FindReplaceRequest> sent = batchRequests(transport.bodies().get(2)).stream()
				.map(Request::getFindReplace)
				.toList();
		assertThat(sent).extracting(FindReplaceRequest::getFind)
				.containsExactly("{{client}}", "{{tactic 1 imps}}", "{{tactic 1 imps}}", "{{notes}}");
		assertThat(sent).extracting(FindReplaceRequest::getReplacement).containsExactly("Acme", "1,200", "1,200", "");
		assertThat(sent).extracting(FindReplaceRequest::getSheetId).containsExactly(11, 11, 22, 11);
		assertThat(sent).extracting(FindReplaceRequest::getMatchCase).containsOnly(true);
		assertThat(sent).extracting(FindReplaceRequest::getAllSheets).containsOnlyNulls();

		// Then: the new workbook is shared, through the service-account Drive client, with the recipients
		ArgumentCaptor<Drive> driveCaptor = ArgumentCaptor.forClass(Drive.class);
		verify(driveSharer).shareWith(driveCaptor.capture(), eq("wb-new"), eq(List.of("ops@example.com")));
		assertThat(driveCaptor.getValue().getRequestFactory().getTransport()).isSameAs(transport);
	}

	@Test
	void shouldCopyTheEomTemplateIntoTheUsersRootAndReplaceAcrossAllSheetsWhenNoTabResolvesTest()
			throws IOException {
		// Given: no target folder, a signed-in user's token, and a copied workbook whose metadata lists no
		// usable tab (no properties, no id, no title)
		RecordingGoogleTransport transport = new RecordingGoogleTransport()
				.respond(BATCH_UPDATE, 200, "{}")
				.respond(COPY, 200, "{\"id\": \"wb-user\"}")
				.respond(METADATA, 200, "{\"sheets\": [{}, {\"properties\": {\"title\": \"NoId\"}},"
						+ " {\"properties\": {\"sheetId\": 5}}]}");
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		GoogleProperties props = new GoogleProperties();
		props.setSheetsTemplateId("tpl-eoc");
		props.setEomSheetsTemplateId("tpl-eom");
		props.setSheetsTargetFolderId(null);
		DriveSharer driveSharer = mock(DriveSharer.class);
		DriveShareRecipients recipients = mock(DriveShareRecipients.class);
		when(recipients.resolve()).thenReturn(List.of());
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, props, mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				driveSharer, recipients);

		// When:
		String url = provider.createSheet("job-8", "Acme EOM", Map.of("{{tactic 1}}", "Video"), "EOM", "user-token");

		// Then: the EOM template is copied into the user's own My Drive root
		assertThat(url).isEqualTo("https://docs.google.com/spreadsheets/d/wb-user/edit");
		assertThat(transport.urls().get(0)).contains("/files/tpl-eom/copy");
		File copy = GsonFactory.getDefaultInstance().fromString(transport.bodies().get(0), File.class);
		assertThat(copy.getParents()).containsExactly("root");

		// Then: with no resolvable tab the replacement falls back to every sheet, and is not duplicated
		List<Request> sent = batchRequests(transport.bodies().get(2));
		assertThat(sent).hasSize(1);
		assertThat(sent.get(0).getFindReplace().getAllSheets()).isTrue();
		assertThat(sent.get(0).getFindReplace().getSheetId()).isNull();
		assertThat(sent.get(0).getFindReplace().getReplacement()).isEqualTo("Video");

		// Then: both the Drive and the Sheets client authenticate as the user, with the user's own token
		ArgumentCaptor<HttpRequestInitializer> initializers = ArgumentCaptor.forClass(HttpRequestInitializer.class);
		verify(creds, times(2)).withTimeout(initializers.capture());
		assertThat(initializers.getAllValues()).allSatisfy(initializer ->
				assertThat(((GoogleCredentials) ((HttpCredentialsAdapter) initializer).getCredentials())
						.getAccessToken().getTokenValue()).isEqualTo("user-token"));
		ArgumentCaptor<Drive> driveCaptor = ArgumentCaptor.forClass(Drive.class);
		verify(driveSharer).shareWith(driveCaptor.capture(), eq("wb-user"), eq(List.of()));
		assertThat(driveCaptor.getValue().getRequestFactory().getTransport()).isSameAs(transport);
	}

	@Test
	void shouldSkipTheBatchUpdateAndParentsWhenThereIsNothingToReplaceTest() throws IOException {
		// Given: no target folder, the service account (blank token) and an empty placeholder map
		RecordingGoogleTransport transport = new RecordingGoogleTransport()
				.respond(COPY, 200, "{\"id\": \"wb-empty\"}")
				.respond(METADATA, 200, REPORT_TAB_ONLY);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		GoogleProperties props = new GoogleProperties();
		props.setSheetsTemplateId("tpl-eoc");
		DriveSharer driveSharer = mock(DriveSharer.class);
		DriveShareRecipients recipients = mock(DriveShareRecipients.class);
		when(recipients.resolve()).thenReturn(List.of("ops@example.com"));
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, props, mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				driveSharer, recipients);

		// When:
		String url = provider.createSheet("job-9", "Empty", Map.of(), "EOM", "  ");

		// Then: the blank EOM template id falls back to EOC, the copy keeps the template's parent, and no
		// batchUpdate is sent at all
		assertThat(url).isEqualTo("https://docs.google.com/spreadsheets/d/wb-empty/edit");
		assertThat(transport.urls()).hasSize(2);
		assertThat(transport.urls().get(0)).contains("/files/tpl-eoc/copy");
		File copy = GsonFactory.getDefaultInstance().fromString(transport.bodies().get(0), File.class);
		assertThat(copy.getParents()).isNull();
		ArgumentCaptor<Drive> driveCaptor = ArgumentCaptor.forClass(Drive.class);
		verify(driveSharer).shareWith(driveCaptor.capture(), eq("wb-empty"), eq(List.of("ops@example.com")));
		assertThat(driveCaptor.getValue().getRequestFactory().getTransport()).isSameAs(transport);
	}

	@Test
	void shouldSplitAFullReportsReplacementsIntoChunksOfAHundredTest() throws IOException {
		// Given: 250 first-tab tokens
		RecordingGoogleTransport transport = new RecordingGoogleTransport()
				.respond(BATCH_UPDATE, 200, "{}")
				.respond(COPY, 200, "{\"id\": \"wb-big\"}")
				.respond(METADATA, 200, REPORT_TAB_ONLY);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		GoogleProperties props = new GoogleProperties();
		props.setSheetsTemplateId("tpl-eoc");
		DriveShareRecipients recipients = mock(DriveShareRecipients.class);
		when(recipients.resolve()).thenReturn(List.of());
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, props, mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), recipients);
		Map<String, String> placeholders = new LinkedHashMap<>();
		for (int i = 1; i <= 250; i++) {
			placeholders.put("{{token " + i + "}}", "v" + i);
		}

		// When:
		provider.createSheet("job-10", "Big", placeholders, "EOC", null);

		// Then: three batchUpdates of 100, 100 and 50 requests, in the original token order
		assertThat(transport.urls().subList(2, 5)).allSatisfy(u -> assertThat(u).contains("wb-big:batchUpdate"));
		List<Request> first = batchRequests(transport.bodies().get(2));
		List<Request> second = batchRequests(transport.bodies().get(3));
		List<Request> third = batchRequests(transport.bodies().get(4));
		assertThat(first).hasSize(100);
		assertThat(second).hasSize(100);
		assertThat(third).hasSize(50);
		assertThat(first.get(0).getFindReplace().getFind()).isEqualTo("{{token 1}}");
		assertThat(second.get(0).getFindReplace().getFind()).isEqualTo("{{token 101}}");
		assertThat(third.get(49).getFindReplace().getFind()).isEqualTo("{{token 250}}");
	}

	@Test
	void shouldTranslateAFailedCopyIntoAGoogleApiExceptionWithoutSharingTest() {
		// Given: Drive rejects the template copy
		RecordingGoogleTransport transport = new RecordingGoogleTransport().respond(COPY, 403, FORBIDDEN);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		GoogleProperties props = new GoogleProperties();
		props.setSheetsTemplateId("tpl-eoc");
		DriveSharer driveSharer = mock(DriveSharer.class);
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, props, mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				driveSharer, mock(DriveShareRecipients.class));

		// When-Then:
		assertThatThrownBy(() -> provider.createSheet("job-11", "Denied", Map.of("{{a}}", "b"), "EOC", null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessageStartingWith("Google Sheets workbook creation failed: 403")
				.hasMessageContaining("caller lacks permission");
		assertThat(transport.urls()).hasSize(1);
		verifyNoInteractions(driveSharer);
	}

	@Test
	void shouldNotTouchTheWorkbookWhenEveryTacticSlotIsUsedTest() {
		// Given: a report that fills all 28 tactic slots
		RecordingGoogleTransport transport = new RecordingGoogleTransport();
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		provider.trimTactics("wb-1", 28, "user-token");

		// Then: no request reaches Google
		assertThat(transport.urls()).isEmpty();
	}

	@Test
	void shouldStopTrimmingAfterTheMetadataReadWhenTheWorkbookHasNoTabsTest() {
		// Given: a workbook whose metadata lists no tabs
		RecordingGoogleTransport transport = new RecordingGoogleTransport().respond(METADATA, 200, NO_TABS);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		provider.trimTactics("wb-1", 3, "  ");

		// Then: only the metadata read was made
		assertThat(transport.urls()).singleElement().satisfies(u -> assertThat(u).contains("wb-1?fields=sheets"));
	}

	@Test
	void shouldDashUnusedSummaryRowsAndClearUnusedDetailAndMetricBlocksInOneBatchTest() throws IOException {
		// Given: a 26-tactic report whose first tab carries the "Main slide 27" block at the top (no title
		// row above it), the summary table header, tactic 27's still-tokenised METRIC block and tactic 5's
		// METRIC block — and no "Main slide 28" anchor at all
		List<List<Object>> grid = new ArrayList<>();
		grid.add(List.of("Main slide 27", ""));
		grid.add(List.of(" Tactic name ", "Benchmark", "KPI type", "KPI (fact)", "", "stray"));
		grid.add(List.of("{{tactic 27}}"));
		grid.add(List.of("METRIC", "GOAL", "", "", "", "", "", "", "", "", "metric"));
		grid.add(List.of("Impressions", "{{tactic 27 planned imps}}", "", "", "", "", "", "", "", "",
				"Impressions", "{{tactic 5 planned imps}}"));
		RecordingGoogleTransport transport = new RecordingGoogleTransport()
				.respond(VALUES, 200, valuesJson(grid))
				.respond(BATCH_UPDATE, 200, "{}")
				.respond(METADATA, 200, TWO_TABS);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		provider.trimTactics("wb-1", 26, "user-token");

		// Then: the first tab is read whole, then one batchUpdate carries every trim request
		assertThat(transport.urls().get(1)).contains("/values/'Report'!A1:ZZ");
		assertThat(transport.urls().get(2)).contains("wb-1:batchUpdate");
		List<Request> sent = batchRequests(transport.bodies().get(2));
		assertThat(sent).hasSize(4);

		// Then: summary slots 27 and 28 are dashed across the four-column header span, values only
		assertThat(sent.get(0).getRepeatCell().getFields()).isEqualTo("userEnteredValue");
		assertThat(sent.get(0).getRepeatCell().getCell().getUserEnteredValue().getStringValue()).isEqualTo("—");
		assertRange(sent.get(0).getRepeatCell().getRange(), 11, 28, 29, 0, 4);
		assertRange(sent.get(1).getRepeatCell().getRange(), 11, 29, 30, 0, 4);

		// Then: the "Main slide 27" block is cleared from row 0 (no title row to reach above it)
		assertThat(sent.get(2).getRepeatCell().getFields()).isEqualTo("*");
		assertRange(sent.get(2).getRepeatCell().getRange(), 11, 0, 15, 0, 2);

		// Then: only tactic 27's METRIC block is cleared, together with its heading row
		assertThat(sent.get(3).getRepeatCell().getFields()).isEqualTo("*");
		assertRange(sent.get(3).getRepeatCell().getRange(), 11, 2, 10, 0, 6);
	}

	@Test
	void shouldNotSendABatchUpdateWhenTheFirstTabHasNothingToTrimTest() {
		// Given: a first tab with no values at all
		RecordingGoogleTransport transport = new RecordingGoogleTransport()
				.respond(VALUES, 200, "{}")
				.respond(METADATA, 200, REPORT_TAB_ONLY);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		provider.trimTactics("wb-1", 2, null);

		// Then: metadata and values were read, but nothing was written
		assertThat(transport.methods()).containsExactly("GET", "GET");
	}

	@Test
	void shouldTranslateATrimFailureIntoAGoogleApiExceptionTest() {
		// Given: Sheets rejects the metadata read
		RecordingGoogleTransport transport = new RecordingGoogleTransport().respond(METADATA, 403, FORBIDDEN);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When-Then:
		assertThatThrownBy(() -> provider.trimTactics("wb-1", 2, null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessageStartingWith("Google Sheets trimTactics failed: 403")
				.hasMessageContaining("caller lacks permission");
	}

	@Test
	void shouldDelegatePacingTablesToTheWriterOnTheFirstTabTest() throws IOException {
		// Given: a workbook whose first tab is "Report", and a writer that reports one skipped table
		RecordingGoogleTransport transport = new RecordingGoogleTransport().respond(METADATA, 200, TWO_TABS);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		SheetPacingTableWriter writer = mock(SheetPacingTableWriter.class);
		WorkbookPacingRequest request = new WorkbookPacingRequest(2, null, Map.of(), Map.of(), 0, Map.of(), null);
		ArgumentCaptor<Sheets> sheetsCaptor = ArgumentCaptor.forClass(Sheets.class);
		when(writer.writeAll(sheetsCaptor.capture(), eq("wb-1"), eq("Report"), same(request)))
				.thenReturn(List.of("Monthly pacing 2 skipped"));
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(), writer,
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		List<String> errors = provider.writePacingTables("wb-1", request);

		// Then: the writer's own result is returned, and it wrote through the service-account client
		assertThat(errors).containsExactly("Monthly pacing 2 skipped");
		assertThat(sheetsCaptor.getValue().getRequestFactory().getTransport()).isSameAs(transport);
		ArgumentCaptor<HttpRequestInitializer> initializer = ArgumentCaptor.forClass(HttpRequestInitializer.class);
		verify(creds, never()).withTimeout(initializer.capture());
	}

	@Test
	void shouldWritePacingTablesAsTheSignedInUserTest() throws IOException {
		// Given: a request carrying the user's token
		RecordingGoogleTransport transport = new RecordingGoogleTransport().respond(METADATA, 200, REPORT_TAB_ONLY);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		SheetPacingTableWriter writer = mock(SheetPacingTableWriter.class);
		WorkbookPacingRequest request =
				new WorkbookPacingRequest(1, null, Map.of(), Map.of(), 0, Map.of(), "user-token");
		ArgumentCaptor<Sheets> sheetsCaptor = ArgumentCaptor.forClass(Sheets.class);
		when(writer.writeAll(sheetsCaptor.capture(), eq("wb-2"), eq("Report"), same(request))).thenReturn(List.of());
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(), writer,
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		List<String> errors = provider.writePacingTables("wb-2", request);

		// Then: a user-scoped Sheets client was built from the user's token and handed to the writer
		assertThat(errors).isEmpty();
		ArgumentCaptor<HttpRequestInitializer> initializer = ArgumentCaptor.forClass(HttpRequestInitializer.class);
		verify(creds).withTimeout(initializer.capture());
		assertThat(((GoogleCredentials) ((HttpCredentialsAdapter) initializer.getValue()).getCredentials())
				.getAccessToken().getTokenValue()).isEqualTo("user-token");
		assertThat(sheetsCaptor.getValue().getRequestFactory().getTransport()).isSameAs(transport);
	}

	@Test
	void shouldSkipPacingTablesWhenTheWorkbookHasNoTabsTest() {
		// Given:
		RecordingGoogleTransport transport = new RecordingGoogleTransport().respond(METADATA, 200, NO_TABS);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		SheetPacingTableWriter writer = mock(SheetPacingTableWriter.class);
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(), writer,
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		List<String> errors = provider.writePacingTables("wb-1",
				new WorkbookPacingRequest(1, null, Map.of(), Map.of(), 0, Map.of(), " "));

		// Then:
		assertThat(errors).containsExactly("Pacing tables skipped — workbook has no tabs");
		verifyNoInteractions(writer);
	}

	@Test
	void shouldReportAPacingFailureInsteadOfThrowingTest() {
		// Given: Sheets rejects the metadata read
		RecordingGoogleTransport transport = new RecordingGoogleTransport().respond(METADATA, 403, FORBIDDEN);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		SheetPacingTableWriter writer = mock(SheetPacingTableWriter.class);
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(), writer,
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		List<String> errors = provider.writePacingTables("wb-1",
				new WorkbookPacingRequest(1, null, Map.of(), Map.of(), 0, Map.of(), null));

		// Then: the failure becomes a report line, so the rest of the job can carry on
		assertThat(errors).singleElement().satisfies(e -> assertThat(e)
				.startsWith("Pacing tables failed: 403").contains("caller lacks permission"));
		verifyNoInteractions(writer);
	}

	@Test
	void shouldReadTheFirstTabAsTrimmedStringsQuotingItsTitleTest() {
		// Given: a first tab whose title carries an apostrophe, holding padded text and a number
		RecordingGoogleTransport transport = new RecordingGoogleTransport()
				.respond(VALUES, 200, "{\"values\": [[\"  Tactic name  \", 42], [\"x\"]]}")
				.respond(METADATA, 200, "{\"sheets\": [{\"properties\": {\"sheetId\": 3, \"title\": \"Bob's\"}}]}");
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		List<List<String>> read = provider.readSheetGrid("wb-1", null);

		// Then: the apostrophe is doubled inside the quoted A1 range and every cell comes back trimmed text
		assertThat(transport.urls().get(1)).contains("/values/'Bob''s'!A1:ZZ");
		assertThat(read).containsExactly(List.of("Tactic name", "42"), List.of("x"));
	}

	@Test
	void shouldReadNothingWhenTheWorkbookHasNoTabsTest() {
		// Given:
		RecordingGoogleTransport transport = new RecordingGoogleTransport().respond(METADATA, 200, NO_TABS);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		List<List<String>> read = provider.readSheetGrid("wb-1", "user-token");

		// Then:
		assertThat(read).isEmpty();
		assertThat(transport.urls()).hasSize(1);
	}

	@Test
	void shouldTranslateAGridReadFailureIntoAGoogleApiExceptionTest() {
		// Given: the tab resolves but its values read is rejected
		RecordingGoogleTransport transport = new RecordingGoogleTransport()
				.respond(VALUES, 403, FORBIDDEN)
				.respond(METADATA, 200, REPORT_TAB_ONLY);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When-Then:
		assertThatThrownBy(() -> provider.readSheetGrid("wb-1", "  "))
				.isInstanceOf(GoogleApiException.class)
				.hasMessageStartingWith("Google Sheets read failed: 403")
				.hasMessageContaining("caller lacks permission");
	}

	@Test
	void shouldSkipClearingBreakdownsWhenTheWorkbookHasNoBreakdownsTabTest() {
		// Given:
		RecordingGoogleTransport transport = new RecordingGoogleTransport().respond(METADATA, 200, REPORT_TAB_ONLY);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		provider.clearBreakdowns("wb-1", Map.of(), null);

		// Then: no tab read and no write
		assertThat(transport.urls()).hasSize(1);
	}

	@Test
	void shouldClearTheBreakdownSectionsATacticDidNotEnableOnTheBreakdownsTabTest() throws IOException {
		// Given: tactic 1 enabled Geo but not Top Publishers
		List<List<Object>> grid = List.of(
				List.of("Top Publishers 1", "", "Geo analysis 1", ""),
				List.of("Publisher", "Impressions", "Geo", "IMPS"));
		RecordingGoogleTransport transport = new RecordingGoogleTransport()
				.respond(VALUES, 200, valuesJson(grid))
				.respond(BATCH_UPDATE, 200, "{}")
				.respond(METADATA, 200, TWO_TABS);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		provider.clearBreakdowns("wb-1", Map.of(1, EnumSet.of(DeckBreakdown.GEO)), "user-token");

		// Then: the Breakdowns tab is read and only the Top Publishers section is cleared, on that tab's id,
		// over the fallback 18-row block height and up to the Geo anchor's column
		assertThat(transport.urls().get(1)).contains("/values/'Breakdowns'!A1:ZZ");
		List<Request> sent = batchRequests(transport.bodies().get(2));
		assertThat(sent).singleElement().satisfies(r -> {
			assertThat(r.getRepeatCell().getFields()).isEqualTo("*");
			assertRange(r.getRepeatCell().getRange(), 22, 0, 18, 0, 2);
		});
	}

	@Test
	void shouldNotWriteWhenTheBreakdownsTabCarriesNoAnchorsTest() {
		// Given:
		RecordingGoogleTransport transport = new RecordingGoogleTransport()
				.respond(VALUES, 200, valuesJson(List.of(List.of("nothing here"))))
				.respond(METADATA, 200, TWO_TABS);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		provider.clearBreakdowns("wb-1", Map.of(), " ");

		// Then: metadata and tab read only
		assertThat(transport.methods()).containsExactly("GET", "GET");
	}

	@Test
	void shouldTranslateAClearBreakdownsFailureIntoAGoogleApiExceptionTest() {
		// Given:
		RecordingGoogleTransport transport = new RecordingGoogleTransport().respond(METADATA, 403, FORBIDDEN);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When-Then:
		assertThatThrownBy(() -> provider.clearBreakdowns("wb-1", Map.of(), null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessageStartingWith("Google Sheets clearBreakdowns failed: 403")
				.hasMessageContaining("caller lacks permission");
	}

	@Test
	void shouldReturnNoBreakdownTablesWithoutCallingGoogleWhenNoTacticIsRequestedTest() {
		// Given:
		RecordingGoogleTransport transport = new RecordingGoogleTransport();
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When: every breakdown read is asked for no tactics (null) and for an empty set
		List<Map<Integer, ?>> results = List.of(
				provider.readPublisherTables("wb-1", null, null),
				provider.readPublisherTables("wb-1", Set.of(), null),
				provider.readCreativeTables("wb-1", null, null),
				provider.readCreativeTables("wb-1", Set.of(), null),
				provider.readGeoTables("wb-1", null, null),
				provider.readGeoTables("wb-1", Set.of(), null),
				provider.readAudienceTables("wb-1", null, null),
				provider.readAudienceTables("wb-1", Set.of(), null),
				provider.readDeviceTables("wb-1", null, null),
				provider.readDeviceTables("wb-1", Set.of(), null));

		// Then: each is empty and no request was sent
		assertThat(results).allSatisfy(result -> assertThat(result).isEmpty());
		assertThat(transport.urls()).isEmpty();
	}

	@Test
	void shouldReturnNoBreakdownTablesWhenTheWorkbookHasNoBreakdownsTabTest() {
		// Given: a workbook with only its first tab
		RecordingGoogleTransport transport = new RecordingGoogleTransport().respond(METADATA, 200, REPORT_TAB_ONLY);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When: each breakdown read runs once, with a blank token
		List<Map<Integer, ?>> results = List.of(
				provider.readPublisherTables("wb-1", Set.of(1), " "),
				provider.readCreativeTables("wb-1", Set.of(1), " "),
				provider.readGeoTables("wb-1", Set.of(1), " "),
				provider.readAudienceTables("wb-1", Set.of(1), " "),
				provider.readDeviceTables("wb-1", Set.of(1), " "));

		// Then: each comes back empty after only the metadata read — the tab is never read
		assertThat(results).allSatisfy(result -> assertThat(result).isEmpty());
		assertThat(transport.urls()).hasSize(5).allSatisfy(u -> assertThat(u).contains("?fields=sheets"));
	}

	@Test
	void shouldTranslateEveryBreakdownReadFailureIntoAGoogleApiExceptionTest() {
		// Given: Sheets rejects every metadata read
		RecordingGoogleTransport transport = new RecordingGoogleTransport().respond(METADATA, 403, FORBIDDEN);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When-Then: each read names itself in the translated message
		assertThatThrownBy(() -> provider.readPublisherTables("wb-1", Set.of(1), null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessageStartingWith("Google Sheets readPublisherTables failed: 403");
		assertThatThrownBy(() -> provider.readCreativeTables("wb-1", Set.of(1), null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessageStartingWith("Google Sheets readCreativeTables failed: 403");
		assertThatThrownBy(() -> provider.readGeoTables("wb-1", Set.of(1), null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessageStartingWith("Google Sheets readGeoTables failed: 403");
		assertThatThrownBy(() -> provider.readAudienceTables("wb-1", Set.of(1), null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessageStartingWith("Google Sheets readAudienceTables failed: 403");
		assertThatThrownBy(() -> provider.readDeviceTables("wb-1", Set.of(1), null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessageStartingWith("Google Sheets readDeviceTables failed: 403");
	}

	@Test
	void shouldReadPublisherRowsFromTheBreakdownsTabAsTheUserTest() {
		// Given:
		List<List<Object>> grid = List.of(
				List.of("Top Publishers 1"),
				List.of("#", "Publisher", "Impressions", "Share of voice"),
				List.of("1", "YouTube", "1,200", "26%"));
		RecordingGoogleTransport transport = new RecordingGoogleTransport()
				.respond(VALUES, 200, valuesJson(grid))
				.respond(METADATA, 200, TWO_TABS);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		Map<Integer, List<WorkbookPublisherRow>> tables =
				provider.readPublisherTables("wb-1", Set.of(1, 2), "user-token");

		// Then: tactic 1's row is read off the Breakdowns tab; tactic 2 has no block and maps to no rows
		assertThat(transport.urls().get(1)).contains("/values/'Breakdowns'!A1:ZZ");
		assertThat(tables).containsEntry(1, List.of(new WorkbookPublisherRow("YouTube", "1,200", "26%")))
				.containsEntry(2, List.of());
		ArgumentCaptor<HttpRequestInitializer> initializer = ArgumentCaptor.forClass(HttpRequestInitializer.class);
		verify(creds).withTimeout(initializer.capture());
		assertThat(((GoogleCredentials) ((HttpCredentialsAdapter) initializer.getValue()).getCredentials())
				.getAccessToken().getTokenValue()).isEqualTo("user-token");
	}

	@Test
	void shouldReadCreativeBlocksFromTheBreakdownsTabAsTheUserTest() {
		// Given:
		List<List<Object>> grid = List.of(
				List.of("Creative analysis 1"),
				List.of("CREATIVES LIVE", "3"),
				List.of("Creative", "Impressions", "CTR", "VCR", "Spend"),
				List.of("Hero", "900", "0.5%", "80%", "$10"));
		RecordingGoogleTransport transport = new RecordingGoogleTransport()
				.respond(VALUES, 200, valuesJson(grid))
				.respond(METADATA, 200, TWO_TABS);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		Map<Integer, WorkbookCreativeTable> tables = provider.readCreativeTables("wb-1", Set.of(1), "user-token");

		// Then:
		assertThat(tables).containsEntry(1, new WorkbookCreativeTable("3", "", "", "",
				List.of(new WorkbookCreativeRow("Hero", "900", "0.5%", "80%", "$10"))));
	}

	@Test
	void shouldReadGeoBlocksFromTheBreakdownsTabAsTheUserTest() {
		// Given:
		List<List<Object>> grid = List.of(
				List.of("Geo analysis 1"),
				List.of("TOP GEO", "Miami"),
				List.of("Geo", "IMPS", "CTR"),
				List.of("Miami", "700", "0.4%"));
		RecordingGoogleTransport transport = new RecordingGoogleTransport()
				.respond(VALUES, 200, valuesJson(grid))
				.respond(METADATA, 200, TWO_TABS);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		Map<Integer, WorkbookGeoTable> tables = provider.readGeoTables("wb-1", Set.of(1), "user-token");

		// Then:
		assertThat(tables).containsEntry(1,
				new WorkbookGeoTable("", "Miami", "", List.of(new WorkbookGeoRow("Miami", "700", "0.4%"))));
	}

	@Test
	void shouldReadAudienceBlocksFromTheBreakdownsTabAsTheUserTest() {
		// Given:
		List<List<Object>> grid = List.of(
				List.of("Audience analysis 1"),
				List.of("GENDER DEMOGRAPHICS", "55% F"),
				List.of("age", "impressions", "", "Segment", "Affinity index"),
				List.of("18-24", "500", "", "Gamers", "130"));
		RecordingGoogleTransport transport = new RecordingGoogleTransport()
				.respond(VALUES, 200, valuesJson(grid))
				.respond(METADATA, 200, TWO_TABS);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		Map<Integer, WorkbookAudienceTable> tables = provider.readAudienceTables("wb-1", Set.of(1), "user-token");

		// Then:
		assertThat(tables).containsEntry(1, new WorkbookAudienceTable("", "55% F",
				List.of(new WorkbookAudienceAgeRow("18-24", "500")),
				List.of(new WorkbookAudienceSegmentRow("Gamers", "130"))));
	}

	@Test
	void shouldReadDeviceBlocksFromTheBreakdownsTabAsTheUserTest() {
		// Given:
		List<List<Object>> grid = List.of(
				List.of("Device breakdown 1"),
				List.of("DEVICES TRACKED", "3"),
				List.of("Device", "Impressions", "CTR", "VCR", "Spend"),
				List.of("CTV", "400", "0.1%", "95%", "$7"));
		RecordingGoogleTransport transport = new RecordingGoogleTransport()
				.respond(VALUES, 200, valuesJson(grid))
				.respond(METADATA, 200, TWO_TABS);
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(transport);
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> { });
		RealSheetDeckProvider provider = new RealSheetDeckProvider(creds, new GoogleProperties(),
				mock(SheetPacingTableWriter.class),
				new GoogleRequestRetrier(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))),
				mock(DriveSharer.class), mock(DriveShareRecipients.class));

		// When:
		Map<Integer, WorkbookDeviceTable> tables = provider.readDeviceTables("wb-1", Set.of(1), "user-token");

		// Then:
		assertThat(tables).containsEntry(1, new WorkbookDeviceTable("", "", "3", "", "",
				List.of(new WorkbookDeviceRow("CTV", "400", "0.1%", "95%", "$7"))));
	}

	/**
	 * Parses a recorded {@code batchUpdate} body back into its requests.
	 *
	 * @param body the recorded JSON body
	 * @return the requests, in sent order
	 * @throws IOException when the body is not valid JSON
	 */
	private static List<Request> batchRequests(String body) throws IOException {
		return GsonFactory.getDefaultInstance().fromString(body, BatchUpdateSpreadsheetRequest.class).getRequests();
	}

	/**
	 * Serialises grid rows as a Sheets {@code values.get} response body.
	 *
	 * @param rows the tab's rows
	 * @return the JSON body
	 */
	private static String valuesJson(List<List<Object>> rows) {
		try {
			return GsonFactory.getDefaultInstance().toString(new ValueRange().setValues(rows));
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * Asserts a grid range's sheet id and bounds.
	 *
	 * @param range    the range to check
	 * @param sheetId  expected sheet id
	 * @param startRow expected inclusive start row
	 * @param endRow   expected exclusive end row
	 * @param startCol expected inclusive start column
	 * @param endCol   expected exclusive end column
	 */
	private static void assertRange(GridRange range, int sheetId, int startRow, int endRow, int startCol, int endCol) {
		assertThat(range.getSheetId()).isEqualTo(sheetId);
		assertThat(range.getStartRowIndex()).isEqualTo(startRow);
		assertThat(range.getEndRowIndex()).isEqualTo(endRow);
		assertThat(range.getStartColumnIndex()).isEqualTo(startCol);
		assertThat(range.getEndColumnIndex()).isEqualTo(endCol);
	}
}
