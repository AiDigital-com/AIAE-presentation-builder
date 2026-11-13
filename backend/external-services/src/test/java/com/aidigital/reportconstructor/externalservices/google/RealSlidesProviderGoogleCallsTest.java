package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.externalservices.google.deck.DeckBreakdown;
import com.aidigital.reportconstructor.externalservices.google.deck.DeckTemplate;
import com.aidigital.reportconstructor.externalservices.google.deck.PaidDeckChannel;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PaidDeckFill;
import com.aidigital.reportconstructor.externalservices.google.drive.DriveShareRecipients;
import com.aidigital.reportconstructor.externalservices.google.drive.DriveSharer;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.slides.v1.Slides;
import com.google.api.services.slides.v1.model.BatchUpdatePresentationRequest;
import com.google.api.services.slides.v1.model.DeleteObjectRequest;
import com.google.api.services.slides.v1.model.DeleteTableRowRequest;
import com.google.api.services.slides.v1.model.Group;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Presentation;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.Shape;
import com.google.api.services.slides.v1.model.Table;
import com.google.api.services.slides.v1.model.TableCell;
import com.google.api.services.slides.v1.model.TableRow;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import com.google.api.services.slides.v1.model.TextRun;
import com.google.api.services.slides.v1.model.UpdatePageElementTransformRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the {@link RealSlidesProvider} operations that talk to Google: the reads and batchUpdates each one
 * sends through the {@link GoogleRequestRetrier} (the mocked client boundary), what it does with the deck it
 * reads back, and how a failed call is translated or swallowed.
 */
class RealSlidesProviderGoogleCallsTest {

	private static final String PRESENTATION_ID = "pres-1";

	private static final String BREAKDOWN_FIELDS = "slides.objectId,"
			+ "slides.pageElements.shape.text.textElements.textRun.content,"
			+ "slides.pageElements.table.tableRows.tableCells.text.textElements.textRun.content";

	@Test
	void shouldCopyTheTemplateIntoTheTargetFolderFillItAndShareItTest() throws Exception {
		// Given: a service-account run with a target folder configured and one token whose value is missing
		GoogleCredentialsFactory creds = credentials();
		GoogleProperties props = new GoogleProperties();
		props.setSlidesTemplateId("template");
		props.setSlidesTargetFolderId(" folder-9 ");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		DriveSharer driveSharer = mock(DriveSharer.class);
		DriveShareRecipients recipients = mock(DriveShareRecipients.class);
		when(recipients.resolve()).thenReturn(List.of("owner@example.test"));
		RealSlidesProvider provider = provider(creds, props, new EomDeckProperties(), retrier, driveSharer,
				recipients, new SummaryTableRowTrimmer(), new EomSlideFinder(), mock(DeckMarkupRequestBuilder.class),
				mock(SlideBlockLocator.class), mock(TacticSlideRelayout.class), mock(DeviceTableCompactor.class),
				mock(CreativeTableCompactor.class), mock(PaidSlidesWriter.class));
		ArgumentCaptor<Drive.Files.Copy> copy = ArgumentCaptor.forClass(Drive.Files.Copy.class);
		doReturn(new File().setId("new-deck")).when(retrier).execute(copy.capture(), eq("createDeck copy of template"));
		Map<String, String> placeholders = new LinkedHashMap<>();
		placeholders.put("{{client}}", "Acme");
		placeholders.put("{{note}}", null);

		// When:
		String url = provider.createDeck("job-1", "Acme EOC", placeholders, "EOC", DeckTemplate.STANDARD, null);

		// Then: the edit URL of the copy is returned
		assertThat(url).isEqualTo("https://docs.google.com/presentation/d/new-deck/edit");
		// And: the template is copied, under the job's name, into the trimmed target folder
		assertThat(copy.getValue().getFileId()).isEqualTo("template");
		assertThat(copy.getValue().getFields()).isEqualTo("id,webViewLink");
		assertThat(copy.getValue().getSupportsAllDrives()).isTrue();
		File copied = (File) copy.getValue().getJsonContent();
		assertThat(copied.getName()).isEqualTo("Acme EOC");
		assertThat(copied.getParents()).containsExactly("folder-9");
		// And: every token is replaced case-sensitively on the copy, a missing value with nothing
		ArgumentCaptor<Slides.Presentations.BatchUpdate> batch =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(retrier).execute(batch.capture(), eq("createDeck batchUpdate for new-deck (chunk 1/1)"));
		assertThat(batch.getValue().getPresentationId()).isEqualTo("new-deck");
		List<Request> sent = sent(batch.getValue());
		assertThat(sent).hasSize(2);
		assertThat(sent.get(0).getReplaceAllText().getContainsText().getText()).isEqualTo("{{client}}");
		assertThat(sent.get(0).getReplaceAllText().getContainsText().getMatchCase()).isTrue();
		assertThat(sent.get(0).getReplaceAllText().getReplaceText()).isEqualTo("Acme");
		assertThat(sent.get(1).getReplaceAllText().getContainsText().getText()).isEqualTo("{{note}}");
		assertThat(sent.get(1).getReplaceAllText().getReplaceText()).isEmpty();
		// And: the copy is shared with the configured recipients through the client that copied it
		ArgumentCaptor<Drive> sharedWith = ArgumentCaptor.forClass(Drive.class);
		verify(driveSharer).shareWith(sharedWith.capture(), eq("new-deck"), eq(List.of("owner@example.test")));
		assertThat(sharedWith.getValue()).isSameAs(copy.getValue().getAbstractGoogleClient());
	}

	@Test
	void shouldCopyIntoTheUsersOwnDriveRootAndSkipTheFillWhenThereIsNothingToReplaceTest() throws Exception {
		// Given: a signed-in user's run with no target folder and no placeholders
		GoogleCredentialsFactory creds = credentials();
		GoogleProperties props = new GoogleProperties();
		props.setSlidesTemplateId("template");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		DriveSharer driveSharer = mock(DriveSharer.class);
		DriveShareRecipients recipients = mock(DriveShareRecipients.class);
		when(recipients.resolve()).thenReturn(List.of());
		RealSlidesProvider provider = provider(creds, props, new EomDeckProperties(), retrier, driveSharer,
				recipients, new SummaryTableRowTrimmer(), new EomSlideFinder(), mock(DeckMarkupRequestBuilder.class),
				mock(SlideBlockLocator.class), mock(TacticSlideRelayout.class), mock(DeviceTableCompactor.class),
				mock(CreativeTableCompactor.class), mock(PaidSlidesWriter.class));
		ArgumentCaptor<Drive.Files.Copy> copy = ArgumentCaptor.forClass(Drive.Files.Copy.class);
		doReturn(new File().setId("user-deck")).when(retrier)
				.execute(copy.capture(), eq("createDeck copy of template"));

		// When:
		String url = provider.createDeck("job-2", "Deck", Map.of(), "EOC", DeckTemplate.STANDARD, "user-token");

		// Then: the copy is forced into the user's My Drive root instead of the template's shared folder
		assertThat(url).isEqualTo("https://docs.google.com/presentation/d/user-deck/edit");
		assertThat(((File) copy.getValue().getJsonContent()).getParents()).containsExactly("root");
		// And: both clients are built on the user's own token
		ArgumentCaptor<HttpRequestInitializer> initializers = ArgumentCaptor.forClass(HttpRequestInitializer.class);
		verify(creds, times(2)).withTimeout(initializers.capture());
		assertThat(initializers.getAllValues()).allSatisfy(initializer -> assertThat(
				((GoogleCredentials) ((HttpCredentialsAdapter) initializer).getCredentials())
						.getAccessToken().getTokenValue()).isEqualTo("user-token"));
		// And: with nothing to replace no batchUpdate is sent
		verify(retrier).execute(copy.getValue(), "createDeck copy of template");
		verifyNoMoreInteractions(retrier);
		verify(driveSharer).shareWith(eq((Drive) copy.getValue().getAbstractGoogleClient()), eq("user-deck"),
				eq(List.of()));
	}

	@Test
	void shouldLeaveTheCopysParentToDriveForAServiceAccountWithoutATargetFolderTest() throws Exception {
		// Given: no target folder configured at all
		GoogleCredentialsFactory creds = credentials();
		GoogleProperties props = new GoogleProperties();
		props.setSlidesTemplateId("template");
		props.setSlidesTargetFolderId(null);
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(creds, props, new EomDeckProperties(), retrier);
		ArgumentCaptor<Drive.Files.Copy> copy = ArgumentCaptor.forClass(Drive.Files.Copy.class);
		doReturn(new File().setId("svc-deck")).when(retrier).execute(copy.capture(), eq("createDeck copy of template"));

		// When:
		provider.createDeck("job-3", "Deck", Map.of(), "EOC", DeckTemplate.STANDARD, " ");

		// Then: a blank token is the service account, and no parent is forced
		assertThat(((File) copy.getValue().getJsonContent()).getParents()).isNull();
		ArgumentCaptor<HttpRequestInitializer> initializer = ArgumentCaptor.forClass(HttpRequestInitializer.class);
		verify(creds, never()).withTimeout(initializer.capture());
	}

	@Test
	void shouldTranslateAFailedTemplateCopyIntoAGoogleApiExceptionTest() throws Exception {
		// Given: Drive refuses the copy
		GoogleCredentialsFactory creds = credentials();
		GoogleProperties props = new GoogleProperties();
		props.setSlidesTemplateId("template");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		DriveSharer driveSharer = mock(DriveSharer.class);
		RealSlidesProvider provider = provider(creds, props, new EomDeckProperties(), retrier, driveSharer,
				mock(DriveShareRecipients.class), new SummaryTableRowTrimmer(), new EomSlideFinder(),
				mock(DeckMarkupRequestBuilder.class), mock(SlideBlockLocator.class), mock(TacticSlideRelayout.class),
				mock(DeviceTableCompactor.class), mock(CreativeTableCompactor.class), mock(PaidSlidesWriter.class));
		ArgumentCaptor<Drive.Files.Copy> copy = ArgumentCaptor.forClass(Drive.Files.Copy.class);
		doThrow(new IOException("quota exceeded")).when(retrier)
				.execute(copy.capture(), eq("createDeck copy of template"));

		// When-Then:
		assertThatThrownBy(() -> provider.createDeck(
				"job-4", "Deck", Map.of("{{a}}", "b"), "EOC", DeckTemplate.STANDARD, null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessage("Google Slides deck creation failed: quota exceeded");
		verifyNoInteractions(driveSharer);
	}

	@Test
	void shouldDeleteSurplusLegacySlotsGroupSlidesAndSummaryRowsInOneBatchTest() throws Exception {
		// Given: the legacy 28-slot model, two real tactics, and a group-1 summary table with a header, seven
		// tactic rows and a Totals row
		GoogleCredentialsFactory creds = credentials();
		GoogleProperties props = new GoogleProperties();
		Map<Integer, String> slots = new HashMap<>();
		for (int t = 1; t <= 28; t++) {
			slots.put(t, "slot_" + t);
		}
		props.setTacticSlideObjectIds(slots);
		props.setResultsSlideObjectIds(Map.of(1, "res1", 2, "res2"));
		props.setSummarySlideObjectIds(Map.of(2, "sum2"));
		props.setSummaryTableObjectIds(Map.of(1, "tbl1"));
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(creds, props, new EomDeckProperties(), retrier);
		List<TableRow> rows = new ArrayList<>();
		rows.add(row("Platforms"));
		for (int n = 1; n <= 7; n++) {
			rows.add(row("{{tactic " + n + "}}"));
		}
		rows.add(row("Total"));
		Presentation deck = new Presentation().setSlides(List.of(new Page().setObjectId("p7").setPageElements(
				List.of(new PageElement().setObjectId("tbl1").setTable(new Table().setTableRows(rows))))));
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(deck).when(retrier).execute(get.capture(), eq("trimTactics get pres-1"));

		// When:
		provider.trimTactics(PRESENTATION_ID, 2, "EOC", DeckTemplate.STANDARD, null);

		// Then: the read names the table element ids
		assertThat(get.getValue().getPresentationId()).isEqualTo(PRESENTATION_ID);
		assertThat(get.getValue().getFields()).isEqualTo("slides.pageElements.objectId,"
				+ "slides.pageElements.table.tableRows.tableCells.text.textElements.textRun.content");
		// And: slots 3..28 go, then group 2's results and summary slides, then rows 7..3 bottom-up
		ArgumentCaptor<Slides.Presentations.BatchUpdate> batch =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(retrier).execute(batch.capture(), eq("trimTactics batchUpdate for pres-1 (chunk 1/1)"));
		List<Request> sent = sent(batch.getValue());
		List<String> expectedDeletes = new ArrayList<>();
		for (int t = 3; t <= 28; t++) {
			expectedDeletes.add("slot_" + t);
		}
		expectedDeletes.add("res2");
		expectedDeletes.add("sum2");
		assertThat(sent).hasSize(33);
		assertThat(sent.subList(0, 28).stream().map(r -> r.getDeleteObject().getObjectId()).toList())
				.isEqualTo(expectedDeletes);
		assertThat(sent.subList(28, 33).stream()
				.map(r -> r.getDeleteTableRow().getCellLocation().getRowIndex()).toList())
				.containsExactly(7, 6, 5, 4, 3);
	}

	@Test
	void shouldNotTouchTheDeckWhenTrimmingAFullOrUnconfiguredDeckTest() {
		// Given: a deck with no trim ids configured
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(
				credentials(), new GoogleProperties(), new EomDeckProperties(), retrier);

		// When: a full 28-tactic deck and a full first group are trimmed
		provider.trimTactics(PRESENTATION_ID, 28, "EOC", DeckTemplate.STANDARD, null);
		provider.trimTactics(PRESENTATION_ID, 7, "EOC", DeckTemplate.STANDARD, null);

		// Then: nothing is read or written
		verifyNoInteractions(retrier);
	}

	@Test
	void shouldTranslateAFailedTrimBatchIntoAGoogleApiExceptionTest() throws Exception {
		// Given: a three-tactic deck whose group-2 results slide must go, and Slides failing the write
		GoogleProperties props = new GoogleProperties();
		props.setResultsSlideObjectIds(Map.of(2, "res2"));
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(credentials(), props, new EomDeckProperties(), retrier);
		ArgumentCaptor<Slides.Presentations.BatchUpdate> batch =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		doThrow(new IOException("backendError")).when(retrier)
				.execute(batch.capture(), eq("trimTactics batchUpdate for pres-1 (chunk 1/1)"));

		// When-Then:
		assertThatThrownBy(() -> provider.trimTactics(PRESENTATION_ID, 3, "EOC", DeckTemplate.STANDARD, "tok"))
				.isInstanceOf(GoogleApiException.class)
				.hasMessage("Google Slides trimTactics failed: backendError");
		assertThat(sent(batch.getValue()).stream().map(r -> r.getDeleteObject().getObjectId()).toList())
				.containsExactly("res2");
	}

	@Test
	void shouldLeaveTheSummaryTableUntrimmedWhenItCannotBeReadTest() throws Exception {
		// Given: a configured group-1 summary table whose read fails
		GoogleProperties props = new GoogleProperties();
		props.setSummaryTableObjectIds(Map.of(1, "tbl1", 2, " "));
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(credentials(), props, new EomDeckProperties(), retrier);
		Slides client = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doThrow(new IOException("unavailable")).when(retrier).execute(get.capture(), eq("trimTactics get pres-1"));
		SlideModel model = provider.slideModelFor("EOC", DeckTemplate.STANDARD);

		// When:
		List<Request> unreadable = provider.surplusRowRequests(model, client, PRESENTATION_ID, 3);
		List<Request> blankTable = provider.surplusRowRequests(model, client, PRESENTATION_ID, 9);

		// Then: no row request in either case, and a blank table id is never read
		assertThat(unreadable).isEmpty();
		assertThat(blankTable).isEmpty();
		verify(retrier, times(1)).execute(get.getValue(), "trimTactics get pres-1");
		verifyNoMoreInteractions(retrier);
	}

	@Test
	void shouldSplitALongRequestListIntoChunksOfAHundredInOrderTest() throws Exception {
		// Given: 101 requests
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(
				credentials(), new GoogleProperties(), new EomDeckProperties(), retrier);
		Slides client = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		List<Request> requests = new ArrayList<>();
		for (int i = 0; i < 101; i++) {
			requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId("o_" + i)));
		}

		// When:
		provider.executeInChunks(client, PRESENTATION_ID, requests, "bulk");

		// Then: the first chunk carries the first hundred, the second the last one
		ArgumentCaptor<Slides.Presentations.BatchUpdate> first =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		ArgumentCaptor<Slides.Presentations.BatchUpdate> second =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(retrier).execute(first.capture(), eq("bulk (chunk 1/2)"));
		verify(retrier).execute(second.capture(), eq("bulk (chunk 2/2)"));
		assertThat(sent(first.getValue())).hasSize(100);
		assertThat(sent(first.getValue()).getFirst().getDeleteObject().getObjectId()).isEqualTo("o_0");
		assertThat(sent(second.getValue()).stream().map(r -> r.getDeleteObject().getObjectId()).toList())
				.containsExactly("o_100");
	}

	@Test
	void shouldDropSurplusEomDashboardSlidesAndTheirPartialBlockRowsTest() throws Exception {
		// Given: an EOM deck of three tactics whose 8–14 dashboard is surplus and whose 1–7 table is partly used
		EomDeckProperties eomProps = new EomDeckProperties();
		eomProps.setSlidesTemplateId("eom-template");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		EomSlideFinder finder = mock(EomSlideFinder.class);
		SummaryTableRowTrimmer trimmer = mock(SummaryTableRowTrimmer.class);
		RealSlidesProvider provider = provider(credentials(), new GoogleProperties(), eomProps, retrier,
				mock(DriveSharer.class), mock(DriveShareRecipients.class), trimmer, finder,
				mock(DeckMarkupRequestBuilder.class), mock(SlideBlockLocator.class), mock(TacticSlideRelayout.class),
				mock(DeviceTableCompactor.class), mock(CreativeTableCompactor.class), mock(PaidSlidesWriter.class));
		List<Page> slides = List.of(new Page().setObjectId("dash_1_7"), new Page().setObjectId("dash_8_14"));
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(slides)).when(retrier)
				.execute(get.capture(), eq("trimEomDashboardSlides get pres-1"));
		when(finder.surplusTacticSlideIds(slides, 3, 7)).thenReturn(List.of("dash_8_14"));
		when(finder.partialBlockTableIds(slides, 3, 7)).thenReturn(List.of("pace_tbl"));
		Request rowDelete = new Request().setDeleteTableRow(new DeleteTableRowRequest().setTableObjectId("pace_tbl"));
		when(trimmer.deleteRowRequests(slides, "pace_tbl", 7, 3)).thenReturn(List.of(rowDelete));

		// When:
		provider.trimTactics(PRESENTATION_ID, 3, "EOM", DeckTemplate.STANDARD, null);

		// Then: the read carries slide and element ids, and one batch drops the slide and the surplus rows
		assertThat(get.getValue().getFields()).isEqualTo("slides.objectId,slides.pageElements.objectId,"
				+ "slides.pageElements.shape.text.textElements.textRun.content,"
				+ "slides.pageElements.table.tableRows.tableCells.text.textElements.textRun.content");
		ArgumentCaptor<Slides.Presentations.BatchUpdate> batch =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(retrier).execute(batch.capture(), eq("trimEomDashboardSlides batchUpdate for pres-1 (chunk 1/1)"));
		List<Request> sent = sent(batch.getValue());
		assertThat(sent).hasSize(2);
		assertThat(sent.get(0).getDeleteObject().getObjectId()).isEqualTo("dash_8_14");
		assertThat(sent.get(1)).isSameAs(rowDelete);
	}

	@Test
	void shouldSendNothingWhenAnEomDeckHasNoSurplusDashboardTest() throws Exception {
		// Given: an EOM deck of fourteen tactics, so both blocks are full
		EomDeckProperties eomProps = new EomDeckProperties();
		eomProps.setSlidesTemplateId("eom-template");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		EomSlideFinder finder = mock(EomSlideFinder.class);
		RealSlidesProvider provider = provider(credentials(), new GoogleProperties(), eomProps, retrier,
				mock(DriveSharer.class), mock(DriveShareRecipients.class), mock(SummaryTableRowTrimmer.class), finder,
				mock(DeckMarkupRequestBuilder.class), mock(SlideBlockLocator.class), mock(TacticSlideRelayout.class),
				mock(DeviceTableCompactor.class), mock(CreativeTableCompactor.class), mock(PaidSlidesWriter.class));
		List<Page> slides = List.of(new Page().setObjectId("dash_1_7"));
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(slides)).when(retrier)
				.execute(get.capture(), eq("trimEomDashboardSlides get pres-1"));
		when(finder.surplusTacticSlideIds(slides, 14, 7)).thenReturn(List.of());

		// When:
		provider.trimTactics(PRESENTATION_ID, 14, "EOM", DeckTemplate.STANDARD, null);

		// Then: the deck is only read, and a full block never asks for its tables
		verify(retrier).execute(get.getValue(), "trimEomDashboardSlides get pres-1");
		verifyNoMoreInteractions(retrier);
		verify(finder, never()).partialBlockTableIds(slides, 14, 7);
	}

	@Test
	void shouldTranslateAFailedEomTrimReadIntoAGoogleApiExceptionTest() throws Exception {
		// Given:
		EomDeckProperties eomProps = new EomDeckProperties();
		eomProps.setSlidesTemplateId("eom-template");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(credentials(), new GoogleProperties(), eomProps, retrier);
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doThrow(new IOException("not found")).when(retrier)
				.execute(get.capture(), eq("trimEomDashboardSlides get pres-1"));

		// When-Then:
		assertThatThrownBy(() -> provider.trimTactics(PRESENTATION_ID, 3, "EOM", DeckTemplate.STANDARD, null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessage("Google Slides trimEomDashboardSlides failed: not found");
	}

	@Test
	void shouldBuildOneTacticSlideFromTheMasterWhenTheCountIsBelowOneTest() throws Exception {
		// Given: the master model and a deck carrying the master at index 1
		GoogleProperties props = new GoogleProperties();
		props.setTacticMasterSlideObjectId("m_tactic");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(credentials(), props, new EomDeckProperties(), retrier);
		Presentation deck = new Presentation().setSlides(List.of(
				textSlide("title", "Campaign"), textSlide("m_tactic", "{{tactic n}}")));
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(deck).when(retrier).execute(get.capture(), eq("addTacticSlides get pres-1"));

		// When: zero tactics are asked for and no values are known
		provider.addTacticSlides(PRESENTATION_ID, 0, null, "EOC", DeckTemplate.STANDARD, null);

		// Then: the count is clamped to one copy, its token renumbered, and moved into the master's slot
		assertThat(get.getValue().getFields()).isEqualTo(BREAKDOWN_FIELDS);
		ArgumentCaptor<Slides.Presentations.BatchUpdate> batch =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(retrier).execute(batch.capture(), eq("addTacticSlides batchUpdate for pres-1 (chunk 1/1)"));
		List<Request> sent = sent(batch.getValue());
		assertThat(sent).hasSize(3);
		assertThat(sent.get(0).getDuplicateObject().getObjectIds()).containsExactly(Map.entry("m_tactic", "tct_1"));
		assertThat(sent.get(1).getReplaceAllText().getContainsText().getText()).isEqualTo("{{tactic n}}");
		assertThat(sent.get(1).getReplaceAllText().getReplaceText()).isEqualTo("{{tactic 1}}");
		assertThat(sent.get(1).getReplaceAllText().getPageObjectIds()).containsExactly("tct_1");
		assertThat(sent.get(2).getUpdateSlidesPosition().getSlideObjectIds()).containsExactly("tct_1");
		assertThat(sent.get(2).getUpdateSlidesPosition().getInsertionIndex()).isEqualTo(1);
	}

	@Test
	void shouldReportAMissingTacticMasterInsteadOfShippingADeckWithoutTacticSlidesTest() throws Exception {
		// Given: a configured master the deck does not carry
		GoogleProperties props = new GoogleProperties();
		props.setTacticMasterSlideObjectId("m_tactic");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(credentials(), props, new EomDeckProperties(), retrier);
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(List.of(textSlide("title", "Campaign")))).when(retrier)
				.execute(get.capture(), eq("addTacticSlides get pres-1"));

		// When-Then:
		assertThatThrownBy(() -> provider.addTacticSlides(
				PRESENTATION_ID, 2, Map.of(), "EOC", DeckTemplate.STANDARD, null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessage("master tactic slide m_tactic was not found in deck pres-1; "
						+ "no per-tactic slides were built");
		verify(retrier).execute(get.getValue(), "addTacticSlides get pres-1");
		verifyNoMoreInteractions(retrier);
	}

	@Test
	void shouldSkipTacticSlidesUnderTheLegacyModelAndTranslateAFailedReadTest() throws Exception {
		// Given: a legacy provider and a master-model provider whose deck read fails
		GoogleRequestRetrier legacyRetrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider legacy = standardProvider(
				credentials(), new GoogleProperties(), new EomDeckProperties(), legacyRetrier);
		GoogleProperties props = new GoogleProperties();
		props.setTacticMasterSlideObjectId("m_tactic");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider master = standardProvider(credentials(), props, new EomDeckProperties(), retrier);
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doThrow(new IOException("timeout")).when(retrier).execute(get.capture(), eq("addTacticSlides get pres-1"));

		// When:
		legacy.addTacticSlides(PRESENTATION_ID, 3, Map.of(), "EOC", DeckTemplate.STANDARD, null);

		// Then: the legacy deck already carries its tactic slides, so nothing is sent
		verifyNoInteractions(legacyRetrier);
		// And: a failed read surfaces as a GoogleApiException naming the step
		assertThatThrownBy(() -> master.addTacticSlides(
				PRESENTATION_ID, 3, Map.of(), "EOC", DeckTemplate.STANDARD, "user-token"))
				.isInstanceOf(GoogleApiException.class)
				.hasMessage("Google Slides addTacticSlides failed: timeout");
	}

	@Test
	void shouldBuildEomTacticSlidesFromTheMastersFoundByTokenTest() throws Exception {
		// Given: an EOM deck carrying one master tactic slide after its cover, built for a signed-in user
		EomDeckProperties eomProps = new EomDeckProperties();
		eomProps.setSlidesTemplateId("eom-template");
		GoogleCredentialsFactory creds = credentials();
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(creds, new GoogleProperties(), eomProps, retrier);
		Presentation deck = new Presentation().setSlides(List.of(
				textSlide("cover", "{{client_name}}"), textSlide("master-a", "{{tactic n}}")));
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(deck).when(retrier).execute(get.capture(), eq("addEomTacticSlides get pres-1"));

		// When:
		provider.addTacticSlides(PRESENTATION_ID, 1, Map.of("{{tactic 1}}", "CTV"), "EOM", DeckTemplate.STANDARD,
				"user-token");

		// Then: the master is copied for the tactic, filled with its value and moved to the master's slot
		ArgumentCaptor<Slides.Presentations.BatchUpdate> batch =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(retrier).execute(batch.capture(), eq("addEomTacticSlides batchUpdate for pres-1 (chunk 1/1)"));
		List<Request> sent = sent(batch.getValue());
		assertThat(sent).hasSize(3);
		assertThat(sent.get(0).getDuplicateObject().getObjectIds())
				.containsExactly(Map.entry("master-a", "eom_m0_t1"));
		assertThat(sent.get(1).getReplaceAllText().getReplaceText()).isEqualTo("CTV");
		assertThat(sent.get(1).getReplaceAllText().getPageObjectIds()).containsExactly("eom_m0_t1");
		assertThat(sent.get(2).getUpdateSlidesPosition().getSlideObjectIds()).containsExactly("eom_m0_t1");
		assertThat(sent.get(2).getUpdateSlidesPosition().getInsertionIndex()).isEqualTo(1);
		ArgumentCaptor<HttpRequestInitializer> initializer = ArgumentCaptor.forClass(HttpRequestInitializer.class);
		verify(creds).withTimeout(initializer.capture());
		assertThat(((GoogleCredentials) ((HttpCredentialsAdapter) initializer.getValue()).getCredentials())
				.getAccessToken().getTokenValue()).isEqualTo("user-token");
	}

	@Test
	void shouldReportAnEomDeckWithoutAMasterAndTranslateAFailedReadTest() throws Exception {
		// Given: one EOM deck with no master tactic slide, and one whose read fails
		EomDeckProperties eomProps = new EomDeckProperties();
		eomProps.setSlidesTemplateId("eom-template");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(credentials(), new GoogleProperties(), eomProps, retrier);
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(List.of(textSlide("cover", "PACING DASHBOARD")))).when(retrier)
				.execute(get.capture(), eq("addEomTacticSlides get pres-1"));
		doThrow(new IOException("gone")).when(retrier).execute(get.capture(), eq("addEomTacticSlides get pres-2"));

		// When-Then: no master is a reported failure, not a silent success
		assertThatThrownBy(() -> provider.addTacticSlides(
				PRESENTATION_ID, 2, null, "EOM", DeckTemplate.STANDARD, null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessage("no EOM master tactic slide found in deck pres-1; no per-tactic slides were built");
		assertThatThrownBy(() -> provider.addTacticSlides("pres-2", 2, null, "EOM", DeckTemplate.STANDARD, null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessage("Google Slides addEomTacticSlides failed: gone");
	}

	@Test
	void shouldInsertBreakdownCopiesAndThenCompactTheirDeviceTablesTest() throws Exception {
		// Given: tactic 1 enables the device breakdown, whose CTR tile resolved to a dash
		GoogleProperties props = new GoogleProperties();
		props.setTacticSlideObjectIds(Map.of(1, "tactic1"));
		props.setBreakdownMasterSlideObjectIds(Map.of("dev", "m_dev"));
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		DeviceTableCompactor deviceCompactor = mock(DeviceTableCompactor.class);
		RealSlidesProvider provider = provider(credentials(), props, new EomDeckProperties(), retrier,
				mock(DriveSharer.class), mock(DriveShareRecipients.class), new SummaryTableRowTrimmer(),
				new EomSlideFinder(), mock(DeckMarkupRequestBuilder.class), mock(SlideBlockLocator.class),
				mock(TacticSlideRelayout.class), deviceCompactor, mock(CreativeTableCompactor.class),
				mock(PaidSlidesWriter.class));
		Presentation deck = new Presentation().setSlides(List.of(
				textSlide("tactic1", "CTV"), textSlide("m_dev", "{{dev_n_ctr}}")));
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(deck).when(retrier).execute(get.capture(), eq("addBreakdownSlides get pres-1"));
		Page filledCopy = new Page().setObjectId("bd_dev_1");
		doReturn(new Presentation().setSlides(List.of(filledCopy))).when(retrier)
				.execute(get.capture(), eq("compactDeviceTables get pres-1"));
		Request dropRow = new Request().setDeleteObject(new DeleteObjectRequest().setObjectId("row-tablet"));
		when(deviceCompactor.compactRequests(filledCopy)).thenReturn(List.of(dropRow));

		// When:
		provider.addBreakdownSlides(PRESENTATION_ID, Map.of(1, Set.of(DeckBreakdown.DEVICE)),
				Map.of("{{dev_1_ctr}}", "—"), Set.of(), "EOC", DeckTemplate.STANDARD, null);

		// Then: the copy is made, its dashed tile swallows the master's percent sign, and it follows tactic 1
		ArgumentCaptor<Slides.Presentations.BatchUpdate> batch =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(retrier).execute(batch.capture(), eq("addBreakdownSlides batchUpdate for pres-1 (chunk 1/1)"));
		List<Request> sent = sent(batch.getValue());
		assertThat(sent.getFirst().getDuplicateObject().getObjectIds()).containsExactly(Map.entry("m_dev", "bd_dev_1"));
		assertThat(sent.subList(1, 4).stream().map(r -> r.getReplaceAllText().getContainsText().getText()).toList())
				.containsExactly("{{dev_n_ctr}} %", "{{dev_n_ctr}}%", "{{dev_n_ctr}}");
		assertThat(sent.subList(1, 4)).allMatch(r -> "—".equals(r.getReplaceAllText().getReplaceText()));
		assertThat(sent.get(4).getUpdateSlidesPosition().getSlideObjectIds()).containsExactly("bd_dev_1");
		assertThat(sent.get(4).getUpdateSlidesPosition().getInsertionIndex()).isEqualTo(1);
		// And: the filled device copy is re-read with its geometry and its empty rows are dropped
		assertThat(get.getAllValues().get(1).getFields()).isEqualTo("slides.objectId,slides.pageElements("
				+ "objectId,size,transform,shape.shapeType,shape.text.textElements.textRun.content,"
				+ "table.tableRows.tableCells.text.textElements.textRun.content)");
		ArgumentCaptor<Slides.Presentations.BatchUpdate> compact =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(retrier).execute(compact.capture(), eq("compactDeviceTables batchUpdate for pres-1 (chunk 1/1)"));
		assertThat(sent(compact.getValue())).containsExactly(dropRow);
	}

	@Test
	void shouldSkipBreakdownSlidesWhenNothingIsEnabledOrConfiguredTest() {
		// Given: one provider with a breakdown master and one without
		GoogleProperties props = new GoogleProperties();
		props.setBreakdownMasterSlideObjectIds(Map.of("dev", "m_dev"));
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(credentials(), props, new EomDeckProperties(), retrier);
		RealSlidesProvider unconfigured = standardProvider(
				credentials(), new GoogleProperties(), new EomDeckProperties(), retrier);

		// When:
		provider.addBreakdownSlides(PRESENTATION_ID, null, Map.of(), Set.of(), "EOC", DeckTemplate.STANDARD, null);
		provider.addBreakdownSlides(PRESENTATION_ID, Map.of(), Map.of(), Set.of(), "EOC", DeckTemplate.STANDARD, null);
		unconfigured.addBreakdownSlides(PRESENTATION_ID, Map.of(1, Set.of(DeckBreakdown.DEVICE)), Map.of(), Set.of(),
				"EOC", DeckTemplate.STANDARD, null);

		// Then:
		verifyNoInteractions(retrier);
	}

	@Test
	void shouldOnlyReadTheDeckWhenNoBreakdownCopyCanBePlacedTest() throws Exception {
		// Given: tactic 1's main slide is missing from the deck
		GoogleProperties props = new GoogleProperties();
		props.setTacticSlideObjectIds(Map.of(1, "tactic1"));
		props.setBreakdownMasterSlideObjectIds(Map.of("dev", "m_dev"));
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(credentials(), props, new EomDeckProperties(), retrier);
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(List.of(textSlide("m_dev", "{{dev_n_ctr}}")))).when(retrier)
				.execute(get.capture(), eq("addBreakdownSlides get pres-1"));
		doThrow(new IOException("denied")).when(retrier).execute(get.capture(), eq("addBreakdownSlides get pres-2"));

		// When:
		provider.addBreakdownSlides(PRESENTATION_ID, Map.of(1, Set.of(DeckBreakdown.DEVICE)), null, Set.of(),
				"EOC", DeckTemplate.STANDARD, null);

		// Then: no batch and no compaction read follow
		verify(retrier).execute(get.getValue(), "addBreakdownSlides get pres-1");
		verifyNoMoreInteractions(retrier);
		// And: a failed read is translated
		assertThatThrownBy(() -> provider.addBreakdownSlides("pres-2", Map.of(1, Set.of(DeckBreakdown.DEVICE)),
				Map.of(), Set.of(), "EOC", DeckTemplate.STANDARD, "tok"))
				.isInstanceOf(GoogleApiException.class)
				.hasMessage("Google Slides addBreakdownSlides failed: denied");
	}

	@Test
	void shouldAnchorEomBreakdownCopiesAfterTheTacticsLastMasterCopyTest() throws Exception {
		// Given: an EOM deck holding tactic 1's copy, the tactic master and the publishers master
		EomDeckProperties eomProps = new EomDeckProperties();
		eomProps.setSlidesTemplateId("eom-template");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(credentials(), new GoogleProperties(), eomProps, retrier);
		Presentation deck = new Presentation().setSlides(List.of(
				textSlide("eom_m0_t1", "{{tactic 1}}"), textSlide("master-a", "{{tactic n}}"),
				textSlide("publishers-master", "{{publisher_n.1}}")));
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(deck).when(retrier).execute(get.capture(), eq("addEomBreakdownSlides get pres-1"));

		// When: tactic 1 enables Top Publishers only, so no table needs compacting
		provider.addBreakdownSlides(PRESENTATION_ID, Map.of(1, Set.of(DeckBreakdown.TOP_PUBLISHERS)), null, Set.of(),
				"EOM", DeckTemplate.STANDARD, null);

		// Then:
		ArgumentCaptor<Slides.Presentations.BatchUpdate> batch =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(retrier).execute(batch.capture(), eq("addEomBreakdownSlides batchUpdate for pres-1 (chunk 1/1)"));
		List<Request> sent = sent(batch.getValue());
		assertThat(sent).hasSize(3);
		assertThat(sent.get(0).getDuplicateObject().getObjectIds())
				.containsExactly(Map.entry("publishers-master", "bd_tp_1"));
		assertThat(sent.get(1).getReplaceAllText().getReplaceText()).isEqualTo("{{publisher_1.1}}");
		assertThat(sent.get(2).getUpdateSlidesPosition().getSlideObjectIds()).containsExactly("bd_tp_1");
		assertThat(sent.get(2).getUpdateSlidesPosition().getInsertionIndex()).isEqualTo(1);
		// And: with no device or creative copy, no compaction read follows
		verify(retrier).execute(get.getValue(), "addEomBreakdownSlides get pres-1");
		verifyNoMoreInteractions(retrier);
	}

	@Test
	void shouldHandleEomBreakdownsWithNothingToDoOrAFailedReadTest() throws Exception {
		// Given: an EOM deck with no breakdown master, and a second deck whose read fails
		EomDeckProperties eomProps = new EomDeckProperties();
		eomProps.setSlidesTemplateId("eom-template");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(credentials(), new GoogleProperties(), eomProps, retrier);
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(List.of(textSlide("master-a", "{{tactic n}}")))).when(retrier)
				.execute(get.capture(), eq("addEomBreakdownSlides get pres-1"));
		doThrow(new IOException("reset")).when(retrier).execute(get.capture(), eq("addEomBreakdownSlides get pres-2"));
		Map<Integer, Set<DeckBreakdown>> enabled = Map.of(1, Set.of(DeckBreakdown.GEO));

		// When:
		provider.addBreakdownSlides(PRESENTATION_ID, null, Map.of(), Set.of(), "EOM", DeckTemplate.STANDARD, null);
		provider.addBreakdownSlides(PRESENTATION_ID, enabled, Map.of(), Set.of(), "EOM", DeckTemplate.STANDARD, null);

		// Then: nothing enabled reads nothing, and no master means only the read
		verify(retrier).execute(get.getValue(), "addEomBreakdownSlides get pres-1");
		verifyNoMoreInteractions(retrier);
		// And:
		assertThatThrownBy(() -> provider.addBreakdownSlides(
				"pres-2", enabled, Map.of(), Set.of(), "EOM", DeckTemplate.STANDARD, null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessage("Google Slides addEomBreakdownSlides failed: reset");
	}

	@Test
	void shouldCompactDeviceAndCreativeCopiesOnlyTest() throws Exception {
		// Given: tactic 1 has a device copy, tactic 2 a creative copy; the deck also carries an unrelated slide
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		DeviceTableCompactor deviceCompactor = mock(DeviceTableCompactor.class);
		CreativeTableCompactor creativeCompactor = mock(CreativeTableCompactor.class);
		RealSlidesProvider provider = provider(credentials(), new GoogleProperties(), new EomDeckProperties(),
				retrier, mock(DriveSharer.class), mock(DriveShareRecipients.class), new SummaryTableRowTrimmer(),
				new EomSlideFinder(), mock(DeckMarkupRequestBuilder.class), mock(SlideBlockLocator.class),
				mock(TacticSlideRelayout.class), deviceCompactor, creativeCompactor, mock(PaidSlidesWriter.class));
		Slides client = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		Page device = new Page().setObjectId("bd_dev_1");
		Page creative = new Page().setObjectId("bd_ca_2");
		Page other = new Page().setObjectId("bd_geo_1");
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(List.of(device, creative, other))).when(retrier)
				.execute(get.capture(), eq("compactDeviceTables get pres-1"));
		Request deviceRow = new Request().setDeleteObject(new DeleteObjectRequest().setObjectId("dev-row"));
		Request creativeRow = new Request().setDeleteObject(new DeleteObjectRequest().setObjectId("ca-row"));
		when(deviceCompactor.compactRequests(device)).thenReturn(List.of(deviceRow));
		when(creativeCompactor.compactRequests(creative)).thenReturn(List.of(creativeRow));
		Map<Integer, Set<DeckBreakdown>> enabled = new HashMap<>();
		enabled.put(1, Set.of(DeckBreakdown.DEVICE, DeckBreakdown.GEO));
		enabled.put(2, Set.of(DeckBreakdown.CREATIVE));
		enabled.put(3, null);

		// When:
		provider.compactDeviceTables(client, PRESENTATION_ID, enabled);

		// Then: one batch carries both compactions, and the unrelated copy is left alone
		ArgumentCaptor<Slides.Presentations.BatchUpdate> batch =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(retrier).execute(batch.capture(), eq("compactDeviceTables batchUpdate for pres-1 (chunk 1/1)"));
		assertThat(sent(batch.getValue())).containsExactly(deviceRow, creativeRow);
		verify(deviceCompactor, never()).compactRequests(other);
		verify(creativeCompactor, never()).compactRequests(other);
	}

	@Test
	void shouldNotWriteWhenCompactionHasNothingToDoOrCannotReadTest() throws Exception {
		// Given: device copies whose tables are already compact, a deck read that returns nothing, and one that
		// fails
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		DeviceTableCompactor deviceCompactor = mock(DeviceTableCompactor.class);
		RealSlidesProvider provider = provider(credentials(), new GoogleProperties(), new EomDeckProperties(),
				retrier, mock(DriveSharer.class), mock(DriveShareRecipients.class), new SummaryTableRowTrimmer(),
				new EomSlideFinder(), mock(DeckMarkupRequestBuilder.class), mock(SlideBlockLocator.class),
				mock(TacticSlideRelayout.class), deviceCompactor, mock(CreativeTableCompactor.class),
				mock(PaidSlidesWriter.class));
		Slides client = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		Page device = new Page().setObjectId("bd_dev_1");
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(List.of(device))).when(retrier)
				.execute(get.capture(), eq("compactDeviceTables get compact"));
		doReturn(new Presentation()).when(retrier).execute(get.capture(), eq("compactDeviceTables get empty"));
		doThrow(new IOException("flaky")).when(retrier).execute(get.capture(), eq("compactDeviceTables get broken"));
		when(deviceCompactor.compactRequests(device)).thenReturn(List.of());
		Map<Integer, Set<DeckBreakdown>> enabled = Map.of(1, Set.of(DeckBreakdown.DEVICE));

		// When:
		provider.compactDeviceTables(client, "compact", enabled);
		provider.compactDeviceTables(client, "empty", enabled);
		provider.compactDeviceTables(client, "unread", enabled);

		// Then: a failed read is non-fatal
		assertThatCode(() -> provider.compactDeviceTables(client, "broken", enabled)).doesNotThrowAnyException();
		// And: every deck is only read (an unanswered read counts as nothing to compact), never written
		ArgumentCaptor<Slides.Presentations.Get> reads = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		for (String deck : List.of("compact", "empty", "unread", "broken")) {
			verify(retrier).execute(reads.capture(), eq("compactDeviceTables get " + deck));
		}
		verifyNoMoreInteractions(retrier);
		assertThat(reads.getAllValues().stream().map(Slides.Presentations.Get::getPresentationId).toList())
				.containsExactly("compact", "empty", "unread", "broken");
	}

	@Test
	void shouldSkipTheCompactionReadWhenNoCopyHasATableToCompactTest() {
		// Given: tactics enabling neither device nor creative, one keyed by no tactic at all
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(
				credentials(), new GoogleProperties(), new EomDeckProperties(), retrier);
		Slides client = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		Map<Integer, Set<DeckBreakdown>> enabled = new HashMap<>();
		enabled.put(null, Set.of(DeckBreakdown.DEVICE, DeckBreakdown.CREATIVE));
		enabled.put(1, Set.of(DeckBreakdown.GEO));

		// When:
		provider.compactDeviceTables(client, PRESENTATION_ID, enabled);

		// Then:
		verifyNoInteractions(retrier);
	}

	@Test
	void shouldDashEveryTokenLeftStandingInAnEomDeckTest() throws Exception {
		// Given: an EOM deck with one raw token in a shape and one inside a group
		EomDeckProperties eomProps = new EomDeckProperties();
		eomProps.setSlidesTemplateId("eom-template");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(credentials(), new GoogleProperties(), eomProps, retrier);
		Page cover = textSlide("cover", "{{total imps pace}}");
		cover.getPageElements().add(new PageElement().setElementGroup(new Group().setChildren(List.of(
				new PageElement().setShape(new Shape().setText(text("{{planned short}}")))))));
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(List.of(cover))).when(retrier)
				.execute(get.capture(), eq("dashUnresolvedTokens get pres-1"));

		// When:
		List<String> dashed = provider.dashUnresolvedTokens(PRESENTATION_ID, "EOM", DeckTemplate.STANDARD, null);

		// Then: both tokens are reported and replaced with the deck's em-dash, the read reaching into groups
		assertThat(dashed).containsExactly("{{total imps pace}}", "{{planned short}}");
		assertThat(get.getValue().getFields()).startsWith(BREAKDOWN_FIELDS + ",")
				.contains("slides.pageElements.elementGroup.children(shape.text.textElements.textRun.content,");
		ArgumentCaptor<Slides.Presentations.BatchUpdate> batch =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(retrier).execute(batch.capture(), eq("dashUnresolvedTokens batchUpdate for pres-1 (chunk 1/1)"));
		List<Request> sent = sent(batch.getValue());
		assertThat(sent.stream().map(r -> r.getReplaceAllText().getContainsText().getText()).toList())
				.containsExactly("{{total imps pace}}", "{{planned short}}");
		assertThat(sent).allMatch(r -> "—".equals(r.getReplaceAllText().getReplaceText())
				&& r.getReplaceAllText().getContainsText().getMatchCase());
	}

	@Test
	void shouldReturnNoDashedTokensForAFilledDeckOrAFailedSweepTest() throws Exception {
		// Given: one filled EOM deck and one whose read fails
		EomDeckProperties eomProps = new EomDeckProperties();
		eomProps.setSlidesTemplateId("eom-template");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(credentials(), new GoogleProperties(), eomProps, retrier);
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(List.of(textSlide("cover", "Acme")))).when(retrier)
				.execute(get.capture(), eq("dashUnresolvedTokens get pres-1"));
		doThrow(new IOException("down")).when(retrier).execute(get.capture(), eq("dashUnresolvedTokens get pres-2"));

		// When:
		List<String> filled = provider.dashUnresolvedTokens(PRESENTATION_ID, "EOM", DeckTemplate.STANDARD, "tok");
		List<String> failed = provider.dashUnresolvedTokens("pres-2", "EOM", DeckTemplate.STANDARD, null);

		// Then: neither sends a write, and the failed sweep is non-fatal
		assertThat(filled).isEmpty();
		assertThat(failed).isEmpty();
		verify(retrier).execute(get.getAllValues().get(0), "dashUnresolvedTokens get pres-1");
		verify(retrier).execute(get.getAllValues().get(1), "dashUnresolvedTokens get pres-2");
		verifyNoMoreInteractions(retrier);
	}

	@Test
	void shouldApplyTheDeckMarkupRequestsBuiltFromTheDeckTest() throws Exception {
		// Given: a markup builder that turns the deck into one style request
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		DeckMarkupRequestBuilder markup = mock(DeckMarkupRequestBuilder.class);
		RealSlidesProvider provider = provider(credentials(), new GoogleProperties(), new EomDeckProperties(),
				retrier, mock(DriveSharer.class), mock(DriveShareRecipients.class), new SummaryTableRowTrimmer(),
				new EomSlideFinder(), markup, mock(SlideBlockLocator.class), mock(TacticSlideRelayout.class),
				mock(DeviceTableCompactor.class), mock(CreativeTableCompactor.class), mock(PaidSlidesWriter.class));
		List<Page> slides = List.of(textSlide("p1", "**Lead** takeaway"));
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(slides)).when(retrier)
				.execute(get.capture(), eq("applyDeckMarkup get pres-1"));
		Request style = new Request().setDeleteObject(new DeleteObjectRequest().setObjectId("marker"));
		when(markup.deckMarkupRequests(slides)).thenReturn(List.of(style));

		// When:
		provider.applyDeckMarkup(PRESENTATION_ID, "user-token");

		// Then: the read asks for geometry and fill, and the built requests are sent as they are
		assertThat(get.getValue().getFields()).isEqualTo("slides.pageElements(objectId,size,transform,"
				+ "shape(shapeProperties.shapeBackgroundFill,text.textElements(startIndex,textRun.content)))");
		ArgumentCaptor<Slides.Presentations.BatchUpdate> batch =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(retrier).execute(batch.capture(), eq("applyDeckMarkup batchUpdate for pres-1 (chunk 1/1)"));
		assertThat(sent(batch.getValue())).containsExactly(style);
	}

	@Test
	void shouldSkipTheMarkupWriteWhenThereIsNoMarkerAndSwallowAFailedReadTest() throws Exception {
		// Given: a deck without markers and a deck whose read fails
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		DeckMarkupRequestBuilder markup = mock(DeckMarkupRequestBuilder.class);
		RealSlidesProvider provider = provider(credentials(), new GoogleProperties(), new EomDeckProperties(),
				retrier, mock(DriveSharer.class), mock(DriveShareRecipients.class), new SummaryTableRowTrimmer(),
				new EomSlideFinder(), markup, mock(SlideBlockLocator.class), mock(TacticSlideRelayout.class),
				mock(DeviceTableCompactor.class), mock(CreativeTableCompactor.class), mock(PaidSlidesWriter.class));
		List<Page> slides = List.of(textSlide("p1", "Plain"));
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(slides)).when(retrier)
				.execute(get.capture(), eq("applyDeckMarkup get pres-1"));
		doThrow(new IOException("down")).when(retrier).execute(get.capture(), eq("applyDeckMarkup get pres-2"));
		when(markup.deckMarkupRequests(slides)).thenReturn(List.of());

		// When:
		provider.applyDeckMarkup(PRESENTATION_ID, null);

		// Then: only the read is sent, and a failed read is non-fatal
		assertThatCode(() -> provider.applyDeckMarkup("pres-2", null)).doesNotThrowAnyException();
		verify(retrier).execute(get.getAllValues().get(0), "applyDeckMarkup get pres-1");
		verify(retrier).execute(get.getAllValues().get(1), "applyDeckMarkup get pres-2");
		verifyNoMoreInteractions(retrier);
	}

	@Test
	void shouldWriteThePaidSlidesRequestsAndSkipAnEmptyFillTest() throws Exception {
		// Given: a fill deleting the paid-social slides, and a writer turning it into one request
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		PaidSlidesWriter writer = mock(PaidSlidesWriter.class);
		RealSlidesProvider provider = provider(credentials(), new GoogleProperties(), new EomDeckProperties(),
				retrier, mock(DriveSharer.class), mock(DriveShareRecipients.class), new SummaryTableRowTrimmer(),
				new EomSlideFinder(), mock(DeckMarkupRequestBuilder.class), mock(SlideBlockLocator.class),
				mock(TacticSlideRelayout.class), mock(DeviceTableCompactor.class), mock(CreativeTableCompactor.class),
				writer);
		PaidDeckFill fill = new PaidDeckFill(List.of(), Set.of(PaidDeckChannel.SOCIAL));
		List<Page> slides = List.of(textSlide("social-divider", "PAID SOCIAL CAMPAIGN RESULTS"));
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(slides)).when(retrier)
				.execute(get.capture(), eq("fillPaidSlides get pres-1"));
		Request drop = new Request().setDeleteObject(new DeleteObjectRequest().setObjectId("social-divider"));
		when(writer.requests(slides, fill)).thenReturn(List.of(drop));

		// When:
		provider.fillPaidSlides(PRESENTATION_ID, null, null);
		provider.fillPaidSlides(PRESENTATION_ID, new PaidDeckFill(List.of(), Set.of()), null);
		provider.fillPaidSlides(PRESENTATION_ID, fill, "user-token");

		// Then: only the non-empty fill reads the deck, with the grouped text and alt-text titles, and writes
		assertThat(get.getAllValues()).hasSize(1);
		assertThat(get.getValue().getFields()).startsWith("slides(objectId,pageElements(objectId,title,")
				.contains("elementGroup.children(objectId,title,shape.text.textElements.textRun.content))))");
		ArgumentCaptor<Slides.Presentations.BatchUpdate> batch =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(retrier).execute(batch.capture(), eq("fillPaidSlides batchUpdate for pres-1 (chunk 1/1)"));
		assertThat(sent(batch.getValue())).containsExactly(drop);
	}

	@Test
	void shouldNotWritePaidSlidesWithoutRequestsAndTranslateAFailedReadTest() throws Exception {
		// Given:
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		PaidSlidesWriter writer = mock(PaidSlidesWriter.class);
		RealSlidesProvider provider = provider(credentials(), new GoogleProperties(), new EomDeckProperties(),
				retrier, mock(DriveSharer.class), mock(DriveShareRecipients.class), new SummaryTableRowTrimmer(),
				new EomSlideFinder(), mock(DeckMarkupRequestBuilder.class), mock(SlideBlockLocator.class),
				mock(TacticSlideRelayout.class), mock(DeviceTableCompactor.class), mock(CreativeTableCompactor.class),
				writer);
		PaidDeckFill fill = new PaidDeckFill(List.of(), Set.of(PaidDeckChannel.SEARCH));
		List<Page> slides = List.of(textSlide("cover", "Acme"));
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(slides)).when(retrier)
				.execute(get.capture(), eq("fillPaidSlides get pres-1"));
		doThrow(new IOException("forbidden")).when(retrier).execute(get.capture(), eq("fillPaidSlides get pres-2"));
		when(writer.requests(slides, fill)).thenReturn(List.of());

		// When:
		provider.fillPaidSlides(PRESENTATION_ID, fill, null);

		// Then:
		verify(retrier).execute(get.getValue(), "fillPaidSlides get pres-1");
		verifyNoMoreInteractions(retrier);
		assertThatThrownBy(() -> provider.fillPaidSlides("pres-2", fill, null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessage("Google Slides fillPaidSlides failed: forbidden");
	}

	@Test
	void shouldCountTheDecksSlidesAndReadZeroWhenTheyCannotBeCountedTest() throws Exception {
		// Given: a three-slide deck, a deck that reports no slides, and a deck whose read fails
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(
				credentials(), new GoogleProperties(), new EomDeckProperties(), retrier);
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(List.of(new Page(), new Page(), new Page()))).when(retrier)
				.execute(get.capture(), eq("countSlides get three"));
		doReturn(new Presentation()).when(retrier).execute(get.capture(), eq("countSlides get none"));
		doThrow(new IOException("down")).when(retrier).execute(get.capture(), eq("countSlides get broken"));

		// When:
		int three = provider.countSlides("three", null);
		int none = provider.countSlides("none", "user-token");
		int broken = provider.countSlides("broken", null);

		// Then: only slide ids are asked for
		assertThat(three).isEqualTo(3);
		assertThat(none).isZero();
		assertThat(broken).isZero();
		assertThat(get.getAllValues()).allMatch(read -> "slides.objectId".equals(read.getFields()));
	}

	@Test
	void shouldDeleteTheConfiguredMastersPresentInTheDeckTest() throws Exception {
		// Given: a master-model deck still carrying its device, thoughts and tactic masters
		GoogleProperties props = new GoogleProperties();
		props.setTacticMasterSlideObjectId("m_tactic");
		props.setThoughtsMasterSlideObjectId("m_thoughts");
		props.setBreakdownMasterSlideObjectIds(Map.of("dev", "m_dev"));
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(credentials(), props, new EomDeckProperties(), retrier);
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(List.of(new Page().setObjectId("tct_1"),
				new Page().setObjectId("m_dev"), new Page().setObjectId("m_thoughts"),
				new Page().setObjectId("m_tactic")))).when(retrier)
				.execute(get.capture(), eq("deleteMasterSlides get pres-1"));

		// When:
		provider.deleteMasterSlides(PRESENTATION_ID, "EOC", DeckTemplate.STANDARD, "user-token");

		// Then: breakdown master, thoughts master, then tactic master — the copy stays
		ArgumentCaptor<Slides.Presentations.BatchUpdate> batch =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(retrier).execute(batch.capture(), eq("deleteMasterSlides batchUpdate for pres-1 (chunk 1/1)"));
		assertThat(sent(batch.getValue()).stream().map(r -> r.getDeleteObject().getObjectId()).toList())
				.containsExactly("m_dev", "m_thoughts", "m_tactic");
	}

	@Test
	void shouldNotDeleteMastersThatAreUnconfiguredOrAbsentAndTranslateAFailedReadTest() throws Exception {
		// Given: an unconfigured provider, and a configured one whose deck carries no master or cannot be read
		GoogleRequestRetrier unconfiguredRetrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider unconfigured = standardProvider(
				credentials(), new GoogleProperties(), new EomDeckProperties(), unconfiguredRetrier);
		GoogleProperties props = new GoogleProperties();
		props.setThoughtsMasterSlideObjectId("m_thoughts");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(credentials(), props, new EomDeckProperties(), retrier);
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(List.of(new Page().setObjectId("tct_1")))).when(retrier)
				.execute(get.capture(), eq("deleteMasterSlides get pres-1"));
		doThrow(new IOException("down")).when(retrier).execute(get.capture(), eq("deleteMasterSlides get pres-2"));

		// When:
		unconfigured.deleteMasterSlides(PRESENTATION_ID, "EOC", DeckTemplate.STANDARD, null);
		provider.deleteMasterSlides(PRESENTATION_ID, "EOC", DeckTemplate.STANDARD, null);

		// Then:
		verifyNoInteractions(unconfiguredRetrier);
		verify(retrier).execute(get.getValue(), "deleteMasterSlides get pres-1");
		verifyNoMoreInteractions(retrier);
		assertThatThrownBy(() -> provider.deleteMasterSlides("pres-2", "EOC", DeckTemplate.STANDARD, null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessage("Google Slides deleteMasterSlides failed: down");
	}

	@Test
	void shouldDeleteTheEomMastersFoundByTokenTest() throws Exception {
		// Given: an EOM deck with a tactic copy, the tactic master and the thoughts master
		EomDeckProperties eomProps = new EomDeckProperties();
		eomProps.setSlidesTemplateId("eom-template");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(credentials(), new GoogleProperties(), eomProps, retrier);
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(List.of(textSlide("eom_m0_t1", "{{tactic 1}}"),
				textSlide("master-a", "{{tactic n}}"),
				textSlide("thoughts-master", "{{thoughts on tactic n performance 1}}")))).when(retrier)
				.execute(get.capture(), eq("deleteEomMasterSlides get pres-1"));

		// When:
		provider.deleteMasterSlides(PRESENTATION_ID, "EOM", DeckTemplate.STANDARD, "user-token");

		// Then:
		ArgumentCaptor<Slides.Presentations.BatchUpdate> batch =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(retrier).execute(batch.capture(), eq("deleteEomMasterSlides batchUpdate for pres-1 (chunk 1/1)"));
		assertThat(sent(batch.getValue()).stream().map(r -> r.getDeleteObject().getObjectId()).toList())
				.containsExactly("master-a", "thoughts-master");
	}

	@Test
	void shouldOnlyReadAnEomDeckWithoutMastersAndTranslateAFailedReadTest() throws Exception {
		// Given:
		EomDeckProperties eomProps = new EomDeckProperties();
		eomProps.setSlidesTemplateId("eom-template");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(credentials(), new GoogleProperties(), eomProps, retrier);
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(List.of(textSlide("eom_m0_t1", "{{tactic 1}}")))).when(retrier)
				.execute(get.capture(), eq("deleteEomMasterSlides get pres-1"));
		doThrow(new IOException("down")).when(retrier).execute(get.capture(), eq("deleteEomMasterSlides get pres-2"));

		// When:
		provider.deleteMasterSlides(PRESENTATION_ID, "EOM", DeckTemplate.STANDARD, null);

		// Then:
		verify(retrier).execute(get.getValue(), "deleteEomMasterSlides get pres-1");
		verifyNoMoreInteractions(retrier);
		assertThatThrownBy(() -> provider.deleteMasterSlides("pres-2", "EOM", DeckTemplate.STANDARD, null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessage("Google Slides deleteEomMasterSlides failed: down");
	}

	@Test
	void shouldDropEomSlidesAndBlocksAndRelayoutTheKeptSlidesInOneBatchTest() throws Exception {
		// Given: an EOM deck that drops one slide by id and one block, relaying the kept slide out around it
		EomDeckProperties eomProps = new EomDeckProperties();
		eomProps.setDropSlideObjectIds(List.of("drop-me"));
		eomProps.setDropElementLabels(List.of("Monthly Impressions Distribution"));
		eomProps.setRelayoutGrowLabel("Top Delivery Content");
		eomProps.setRelayoutAnchorLabel("Daily pacing Dynamic");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		SlideBlockLocator locator = mock(SlideBlockLocator.class);
		TacticSlideRelayout relayout = mock(TacticSlideRelayout.class);
		RealSlidesProvider provider = provider(credentials(), new GoogleProperties(), eomProps, retrier,
				mock(DriveSharer.class), mock(DriveShareRecipients.class), new SummaryTableRowTrimmer(),
				new EomSlideFinder(), mock(DeckMarkupRequestBuilder.class), locator, relayout,
				mock(DeviceTableCompactor.class), mock(CreativeTableCompactor.class), mock(PaidSlidesWriter.class));
		Page dropped = new Page().setObjectId("drop-me");
		Page kept = new Page().setObjectId("p7");
		List<String> labels = List.of("MONTHLYIMPRESSIONSDISTRIBUTION");
		when(locator.blockObjectIds(kept, labels)).thenReturn(List.of("card"));
		Request move = new Request().setUpdatePageElementTransform(
				new UpdatePageElementTransformRequest().setObjectId("tiles"));
		when(relayout.relayoutRequests(kept, labels, "TOPDELIVERYCONTENT", "DAILYPACINGDYNAMIC"))
				.thenReturn(List.of(move));
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(List.of(dropped, kept))).when(retrier)
				.execute(get.capture(), eq("deleteReportTypeSlides get pres-1"));

		// When:
		provider.deleteReportTypeSlides(PRESENTATION_ID, "EOM", "user-token");

		// Then: the read carries geometry and group children, and the batch is slide, block, then relayout
		assertThat(get.getValue().getFields()).startsWith("slides.objectId,slides.pageElements(objectId,size,")
				.contains("elementGroup.children(objectId,size,transform,shape.shapeType,");
		ArgumentCaptor<Slides.Presentations.BatchUpdate> batch =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(retrier).execute(batch.capture(), eq("deleteReportTypeSlides batchUpdate for pres-1 (chunk 1/1)"));
		List<Request> sent = sent(batch.getValue());
		assertThat(sent).hasSize(3);
		assertThat(sent.get(0).getDeleteObject().getObjectId()).isEqualTo("drop-me");
		assertThat(sent.get(1).getDeleteObject().getObjectId()).isEqualTo("card");
		assertThat(sent.get(2)).isSameAs(move);
		verify(locator, never()).blockObjectIds(dropped, labels);
	}

	@Test
	void shouldSkipTheRelayoutWhenNoBlockIsDroppedTest() throws Exception {
		// Given: a slide dropped by title, and a kept slide without the configured block
		EomDeckProperties eomProps = new EomDeckProperties();
		eomProps.setDropSlideTitles(List.of("The Frequency & Velocity Play"));
		eomProps.setDropElementLabels(List.of("Monthly Impressions Distribution"));
		eomProps.setRelayoutGrowLabel("Top Delivery Content");
		eomProps.setRelayoutAnchorLabel("Daily pacing Dynamic");
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		SlideBlockLocator locator = mock(SlideBlockLocator.class);
		TacticSlideRelayout relayout = mock(TacticSlideRelayout.class);
		RealSlidesProvider provider = provider(credentials(), new GoogleProperties(), eomProps, retrier,
				mock(DriveSharer.class), mock(DriveShareRecipients.class), new SummaryTableRowTrimmer(),
				new EomSlideFinder(), mock(DeckMarkupRequestBuilder.class), locator, relayout,
				mock(DeviceTableCompactor.class), mock(CreativeTableCompactor.class), mock(PaidSlidesWriter.class));
		Page frequency = textSlide("freq", "THE FREQUENCY & VELOCITY PLAY");
		Page kept = textSlide("p7", "CTV");
		List<String> labels = List.of("MONTHLYIMPRESSIONSDISTRIBUTION");
		when(locator.blockObjectIds(kept, labels)).thenReturn(List.of());
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(List.of(frequency, kept))).when(retrier)
				.execute(get.capture(), eq("deleteReportTypeSlides get pres-1"));

		// When:
		provider.deleteReportTypeSlides(PRESENTATION_ID, "EOM", null);

		// Then: only the titled slide goes, and nothing is relaid out
		ArgumentCaptor<Slides.Presentations.BatchUpdate> batch =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(retrier).execute(batch.capture(), eq("deleteReportTypeSlides batchUpdate for pres-1 (chunk 1/1)"));
		assertThat(sent(batch.getValue()).stream().map(r -> r.getDeleteObject().getObjectId()).toList())
				.containsExactly("freq");
		verifyNoInteractions(relayout);
	}

	@Test
	void shouldSendNothingWhenNoReportTypeSlideMatchesOrNoneIsConfiguredTest() throws Exception {
		// Given: a configured drop id the deck does not carry, a provider with nothing configured, and a failing read
		EomDeckProperties eomProps = new EomDeckProperties();
		eomProps.setDropSlideObjectIds(List.of("gone"));
		GoogleRequestRetrier retrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider provider = standardProvider(credentials(), new GoogleProperties(), eomProps, retrier);
		GoogleRequestRetrier unconfiguredRetrier = mock(GoogleRequestRetrier.class);
		RealSlidesProvider unconfigured = standardProvider(
				credentials(), new GoogleProperties(), new EomDeckProperties(), unconfiguredRetrier);
		ArgumentCaptor<Slides.Presentations.Get> get = ArgumentCaptor.forClass(Slides.Presentations.Get.class);
		doReturn(new Presentation().setSlides(List.of(textSlide("p1", "Overview")))).when(retrier)
				.execute(get.capture(), eq("deleteReportTypeSlides get pres-1"));
		doThrow(new IOException("down")).when(retrier).execute(get.capture(), eq("deleteReportTypeSlides get pres-2"));

		// When:
		provider.deleteReportTypeSlides(PRESENTATION_ID, "EOC", null);
		provider.deleteReportTypeSlides(PRESENTATION_ID, "EOM", null);
		unconfigured.deleteReportTypeSlides(PRESENTATION_ID, "EOM", null);

		// Then: an EOC deck is never read, a match-less EOM deck is only read, an unconfigured one not at all
		verify(retrier).execute(get.getValue(), "deleteReportTypeSlides get pres-1");
		verifyNoMoreInteractions(retrier);
		verifyNoInteractions(unconfiguredRetrier);
		// And:
		assertThatThrownBy(() -> provider.deleteReportTypeSlides("pres-2", "EOM", null))
				.isInstanceOf(GoogleApiException.class)
				.hasMessage("Google Slides deleteReportTypeSlides failed: down");
	}

	/**
	 * Wires a provider around the given client boundary and collaborators.
	 *
	 * @return the provider under test
	 */
	private RealSlidesProvider provider(
			GoogleCredentialsFactory creds, GoogleProperties props, EomDeckProperties eomProps,
			GoogleRequestRetrier retrier, DriveSharer driveSharer, DriveShareRecipients recipients,
			SummaryTableRowTrimmer trimmer, EomSlideFinder finder, DeckMarkupRequestBuilder markup,
			SlideBlockLocator locator, TacticSlideRelayout relayout, DeviceTableCompactor deviceCompactor,
			CreativeTableCompactor creativeCompactor, PaidSlidesWriter paidWriter) {
		return new RealSlidesProvider(creds, props, driveSharer, recipients, retrier, new BreakdownSlideNaming(),
				trimmer, new EomDeckPolicy(eomProps), new MpoDeckPolicy(new MpoDeckProperties()), finder, markup,
				locator, relayout, deviceCompactor, creativeCompactor, paidWriter);
	}

	/**
	 * Wires a provider with the real row trimmer and slide finder and mocks for every other collaborator.
	 *
	 * @return the provider under test
	 */
	private RealSlidesProvider standardProvider(
			GoogleCredentialsFactory creds, GoogleProperties props, EomDeckProperties eomProps,
			GoogleRequestRetrier retrier) {
		return provider(creds, props, eomProps, retrier, mock(DriveSharer.class), mock(DriveShareRecipients.class),
				new SummaryTableRowTrimmer(), new EomSlideFinder(), mock(DeckMarkupRequestBuilder.class),
				mock(SlideBlockLocator.class), mock(TacticSlideRelayout.class), mock(DeviceTableCompactor.class),
				mock(CreativeTableCompactor.class), mock(PaidSlidesWriter.class));
	}

	/**
	 * Mocks the credentials factory with a real offline transport, so built requests can be inspected.
	 *
	 * @return the mocked factory
	 */
	private GoogleCredentialsFactory credentials() {
		GoogleCredentialsFactory creds = mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(new NetHttpTransport());
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		return creds;
	}

	/**
	 * Reads the requests a captured batchUpdate carries.
	 *
	 * @return the batch's requests in order
	 */
	private List<Request> sent(Slides.Presentations.BatchUpdate batch) {
		return ((BatchUpdatePresentationRequest) batch.getJsonContent()).getRequests();
	}

	private Page textSlide(String objectId, String... texts) {
		List<PageElement> elements = new ArrayList<>();
		for (String content : texts) {
			elements.add(new PageElement().setShape(new Shape().setText(text(content))));
		}
		return new Page().setObjectId(objectId).setPageElements(elements);
	}

	private TextContent text(String content) {
		return new TextContent().setTextElements(List.of(new TextElement().setTextRun(new TextRun()
				.setContent(content))));
	}

	private TableRow row(String content) {
		return new TableRow().setTableCells(List.of(new TableCell().setText(text(content))));
	}
}
