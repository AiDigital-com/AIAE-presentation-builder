package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.externalservices.google.charts.PaidChartKind;
import com.aidigital.reportconstructor.externalservices.google.charts.model.PaidChart;
import com.aidigital.reportconstructor.externalservices.google.drive.DriveCopier;
import com.aidigital.reportconstructor.externalservices.google.drive.ChartFileSharer;
import com.aidigital.reportconstructor.observability.external.ExternalCallTimer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.aidigital.reportconstructor.externalservices.google.charts.model.PaidChartsRequest;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AddChartResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.EmbeddedChart;
import com.google.api.services.sheets.v4.model.OverlayPosition;
import com.google.api.services.sheets.v4.model.Response;
import com.google.api.services.slides.v1.Slides;
import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.BatchUpdatePresentationRequest;
import com.google.api.services.slides.v1.model.CreateSheetsChartRequest;
import com.google.api.services.slides.v1.model.Dimension;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Presentation;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.Size;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaidChartBuilderTest {

	@Test
	void shouldTakeTheChartIdFromTheAddChartReplyTest() {
		// Given: theme and data replies are empty, the chart reply is last
		PaidChartBuilder builder = new PaidChartBuilder(mock(DriveCopier.class), mock(ChartFileSharer.class),
				mock(ChartErrorTranslator.class), new PaidChartSheetRequests(), new PaidChartSlideRequests(), new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())), new PaidChartStyleResolver());
		BatchUpdateSpreadsheetResponse reply = new BatchUpdateSpreadsheetResponse().setReplies(List.of(
				new Response(), new Response(),
				new Response().setAddChart(new AddChartResponse().setChart(new EmbeddedChart().setChartId(913)))));

		// When:
		Integer chartId = builder.chartId(reply);

		// Then:
		assertThat(chartId).isEqualTo(913);
		assertThat(builder.chartId(new BatchUpdateSpreadsheetResponse())).isNull();
	}

	@Test
	void shouldDoNothingWithoutChartsTest() {
		// Given:
		DriveCopier driveCopier = mock(DriveCopier.class);
		PaidChartBuilder builder = new PaidChartBuilder(driveCopier, mock(ChartFileSharer.class),
				mock(ChartErrorTranslator.class), new PaidChartSheetRequests(), new PaidChartSlideRequests(), new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry())), new PaidChartStyleResolver());

		// When:
		List<String> errors = builder.buildPaidCharts(new ChartClients(null, null, null),
				new PaidChartsRequest("deck", "Acme", List.of(), null));
		List<String> nullErrors = builder.buildPaidCharts(new ChartClients(null, null, null),
				new PaidChartsRequest("deck", "Acme", null, null));

		// Then:
		assertThat(errors).isEmpty();
		assertThat(nullErrors).isEmpty();
		verifyNoInteractions(driveCopier);
	}

	@Test
	void shouldFindNoChartIdInAMissingOrChartlessReplyTest() {
		// Given: a reply whose last entries are a null and an add-chart reply without its chart
		PaidChartBuilder builder = new PaidChartBuilder(mock(DriveCopier.class), mock(ChartFileSharer.class),
				new ChartErrorTranslator(), new PaidChartSheetRequests(), new PaidChartSlideRequests(),
				mock(GoogleCallTimer.class), new PaidChartStyleResolver());
		BatchUpdateSpreadsheetResponse chartless = new BatchUpdateSpreadsheetResponse().setReplies(Arrays.asList(
				new Response().setAddChart(new AddChartResponse()), null));
		BatchUpdateSpreadsheetResponse earlierChart = new BatchUpdateSpreadsheetResponse().setReplies(Arrays.asList(
				new Response().setAddChart(new AddChartResponse().setChart(new EmbeddedChart().setChartId(7))),
				new Response().setAddChart(new AddChartResponse())));

		// When-Then: nothing to link without a reply or a chart, and the last reply carrying a chart wins
		assertThat(builder.chartId(null)).isNull();
		assertThat(builder.chartId(chartless)).isNull();
		assertThat(builder.chartId(earlierChart)).isEqualTo(7);
	}

	@Test
	void shouldLinkAChartedSlotAndDropTheSampleImageOfAnEmptyOneTest() throws IOException {
		// Given: a deck carrying the spend and clicks/CTR sample images (a logo sits above them) but no donut; the
		// spend chart has data, the clicks/CTR chart has none
		DriveCopier driveCopier = mock(DriveCopier.class);
		ChartFileSharer sharer = mock(ChartFileSharer.class);
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		PaidChartBuilder builder = new PaidChartBuilder(driveCopier, sharer, new ChartErrorTranslator(),
				new PaidChartSheetRequests(), new PaidChartSlideRequests(), googleCalls, new PaidChartStyleResolver());
		Drive drive = mock(Drive.class);
		Sheets sheets = new Sheets.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		Slides slides = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		Size size = new Size()
				.setWidth(new Dimension().setMagnitude(3_000_000d).setUnit("EMU"))
				.setHeight(new Dimension().setMagnitude(1_905_000d).setUnit("EMU"));
		AffineTransform at = new AffineTransform().setScaleX(1d).setScaleY(1d).setTranslateX(100d).setTranslateY(200d);
		Presentation deck = new Presentation().setSlides(List.of(new Page().setObjectId("p5").setPageElements(List.of(
				new PageElement().setObjectId("img_spend").setTitle("ps_chart_spend").setSize(size).setTransform(at),
				new PageElement().setObjectId("img_ctr").setTitle(" ps_chart_clicks_ctr ").setSize(size)
						.setTransform(at),
				new PageElement().setObjectId("logo")))));
		doReturn(deck).when(googleCalls).execute(argThat((AbstractGoogleClientRequest<?> r) ->
				r instanceof Slides.Presentations.Get get && "deck-1".equals(get.getPresentationId())));
		doReturn(new BatchUpdateSpreadsheetResponse().setReplies(List.of(new Response(), new Response(),
				new Response().setAddChart(new AddChartResponse().setChart(new EmbeddedChart().setChartId(913))))))
				.when(googleCalls).execute(argThat((AbstractGoogleClientRequest<?> r) ->
						r instanceof Sheets.Spreadsheets.BatchUpdate update
								&& "sheet-1".equals(update.getSpreadsheetId())));
		when(driveCopier.createFolder(same(drive), eq("Paid charts — Acme"))).thenReturn("folder-1");
		when(driveCopier.createSpreadsheet(same(drive), eq("ps_chart_spend — Acme"), eq("folder-1")))
				.thenReturn("sheet-1");
		PaidChartsRequest request = new PaidChartsRequest("deck-1", "Acme", List.of(
				new PaidChart(PaidChartKind.SEARCH_SPEND, List.of("Jan", "Feb"), List.of(100d, 250d), List.of(), null),
				new PaidChart(PaidChartKind.SEARCH_CLICKS_CTR, List.of("Jan"), List.of(0d), List.of(0d), null),
				new PaidChart(PaidChartKind.SEARCH_CONVERSION_DONUT, List.of("A"), List.of(5d), List.of(), "5")),
				null);

		// When:
		List<String> errors = builder.buildPaidCharts(new ChartClients(drive, sheets, slides), request);

		// Then: no errors, and the deck's chart folder is shared once with the spend spreadsheet placed in it
		assertThat(errors).isEmpty();
		verify(sharer).shareFolder(same(drive), eq("folder-1"));
		verify(sharer).shareLooseCopy(same(drive), eq("folder-1"), eq("sheet-1"));
		verify(driveCopier, times(1)).createSpreadsheet(same(drive), eq("ps_chart_spend — Acme"), eq("folder-1"));

		// And: the deck is read, the spend chart drawn, swapped in, and the empty chart's sample image deleted
		@SuppressWarnings({"unchecked", "rawtypes"})
		ArgumentCaptor<AbstractGoogleClientRequest<?>> calls =
				(ArgumentCaptor) ArgumentCaptor.forClass(AbstractGoogleClientRequest.class);
		verify(googleCalls, times(4)).execute(calls.capture());
		List<AbstractGoogleClientRequest<?>> sent = calls.getAllValues();
		assertThat(((Slides.Presentations.Get) sent.get(0)).getFields())
				.isEqualTo("slides(objectId,pageElements(objectId,title,size,transform))");

		Sheets.Spreadsheets.BatchUpdate draw = (Sheets.Spreadsheets.BatchUpdate) sent.get(1);
		List<com.google.api.services.sheets.v4.model.Request> sheetRequests =
				((BatchUpdateSpreadsheetRequest) draw.getJsonContent()).getRequests();
		assertThat(sheetRequests).hasSize(3);
		OverlayPosition overlay = sheetRequests.get(2).getAddChart().getChart().getPosition().getOverlayPosition();
		assertThat(overlay.getWidthPixels()).isEqualTo(315);
		assertThat(overlay.getHeightPixels()).isEqualTo(200);

		Slides.Presentations.BatchUpdate swap = (Slides.Presentations.BatchUpdate) sent.get(2);
		assertThat(swap.getPresentationId()).isEqualTo("deck-1");
		List<Request> swapRequests = ((BatchUpdatePresentationRequest) swap.getJsonContent()).getRequests();
		assertThat(swapRequests).hasSize(3);
		assertThat(swapRequests.get(0).getDeleteObject().getObjectId()).isEqualTo("img_spend");
		CreateSheetsChartRequest create = swapRequests.get(1).getCreateSheetsChart();
		assertThat(create.getSpreadsheetId()).isEqualTo("sheet-1");
		assertThat(create.getChartId()).isEqualTo(913);
		assertThat(create.getLinkingMode()).isEqualTo("LINKED");
		assertThat(create.getObjectId()).startsWith("paidch_").hasSize(27);
		assertThat(create.getElementProperties().getPageObjectId()).isEqualTo("p5");
		assertThat(swapRequests.get(2).getUpdatePageElementsZOrder().getPageElementObjectIds())
				.containsExactly("img_ctr", "logo");

		Slides.Presentations.BatchUpdate drop = (Slides.Presentations.BatchUpdate) sent.get(3);
		assertThat(((BatchUpdatePresentationRequest) drop.getJsonContent()).getRequests())
				.singleElement()
				.satisfies(r -> assertThat(r.getDeleteObject().getObjectId()).isEqualTo("img_ctr"));
	}

	@Test
	void shouldReportAnUnreadableDeckWithoutTouchingDriveTest() throws IOException {
		// Given:
		DriveCopier driveCopier = mock(DriveCopier.class);
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		PaidChartBuilder builder = new PaidChartBuilder(driveCopier, mock(ChartFileSharer.class),
				new ChartErrorTranslator(), new PaidChartSheetRequests(), new PaidChartSlideRequests(), googleCalls,
				new PaidChartStyleResolver());
		Slides slides = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		doThrow(new IOException("deck gone")).when(googleCalls).execute(argThat((AbstractGoogleClientRequest<?> r) ->
				r instanceof Slides.Presentations.Get get && "deck-1".equals(get.getPresentationId())));
		PaidChartsRequest request = new PaidChartsRequest("deck-1", "Acme", List.of(
				new PaidChart(PaidChartKind.SEARCH_SPEND, List.of("Jan"), List.of(100d), List.of(), null)), null);

		// When:
		List<String> errors = builder.buildPaidCharts(new ChartClients(null, null, slides), request);

		// Then:
		assertThat(errors).containsExactly("Paid charts: could not read presentation layout — deck gone");
		verifyNoInteractions(driveCopier);
	}

	@Test
	void shouldPutSpreadsheetsInTheRootWhenTheFolderCannotBeCreatedAndReportPerChartFailuresTest()
			throws IOException {
		// Given: the folder cannot be created; the spend spreadsheet replies without a chart, and the social spend
		// spreadsheet cannot be created at all
		DriveCopier driveCopier = mock(DriveCopier.class);
		ChartFileSharer sharer = mock(ChartFileSharer.class);
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		PaidChartBuilder builder = new PaidChartBuilder(driveCopier, sharer, new ChartErrorTranslator(),
				new PaidChartSheetRequests(), new PaidChartSlideRequests(), googleCalls, new PaidChartStyleResolver());
		Drive drive = mock(Drive.class);
		Sheets sheets = new Sheets.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		Slides slides = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		Size size = new Size()
				.setWidth(new Dimension().setMagnitude(952_500d).setUnit("EMU"))
				.setHeight(new Dimension().setMagnitude(952_500d).setUnit("EMU"));
		Presentation deck = new Presentation().setSlides(List.of(new Page().setObjectId("p9").setPageElements(List.of(
				new PageElement().setObjectId("img_a").setTitle("ps_chart_spend").setSize(size)
						.setTransform(new AffineTransform()),
				new PageElement().setObjectId("img_b").setTitle("pso_chart_spend").setSize(size)
						.setTransform(new AffineTransform())))));
		doReturn(deck).when(googleCalls).execute(argThat((AbstractGoogleClientRequest<?> r) ->
				r instanceof Slides.Presentations.Get get && "deck-1".equals(get.getPresentationId())));
		doReturn(new BatchUpdateSpreadsheetResponse().setReplies(List.of(new Response())))
				.when(googleCalls).execute(argThat((AbstractGoogleClientRequest<?> r) ->
						r instanceof Sheets.Spreadsheets.BatchUpdate update
								&& "sheet-1".equals(update.getSpreadsheetId())));
		when(driveCopier.createFolder(same(drive), eq("Paid charts — Acme"))).thenThrow(new IOException("no quota"));
		when(driveCopier.createSpreadsheet(same(drive), eq("ps_chart_spend — Acme"), eq(""))).thenReturn("sheet-1");
		when(driveCopier.createSpreadsheet(same(drive), eq("pso_chart_spend — Acme"), eq("")))
				.thenThrow(new IOException("storage full"));
		PaidChartsRequest request = new PaidChartsRequest("deck-1", "Acme", List.of(
				new PaidChart(PaidChartKind.SEARCH_SPEND, List.of("Jan"), List.of(100d), List.of(), null),
				new PaidChart(PaidChartKind.SOCIAL_SPEND, List.of("Jan"), List.of(80d), List.of(), null)), null);

		// When:
		List<String> errors = builder.buildPaidCharts(new ChartClients(drive, sheets, slides), request);

		// Then: one message per failed chart, under its marker; the folder is attempted only once
		assertThat(errors).containsExactly(
				"Paid chart ps_chart_spend: the chart spreadsheet sheet-1 returned no chart id",
				"Paid chart pso_chart_spend: storage full");
		verify(driveCopier, times(1)).createFolder(same(drive), eq("Paid charts — Acme"));
		verify(sharer, never()).shareFolder(same(drive), eq(""));
		verify(sharer).shareLooseCopy(same(drive), eq(""), eq("sheet-1"));

		// And: nothing is swapped on the deck — only the read and the chart spreadsheet's update went out
		@SuppressWarnings({"unchecked", "rawtypes"})
		ArgumentCaptor<AbstractGoogleClientRequest<?>> calls =
				(ArgumentCaptor) ArgumentCaptor.forClass(AbstractGoogleClientRequest.class);
		verify(googleCalls, times(2)).execute(calls.capture());
		assertThat(calls.getAllValues()).noneMatch(r -> r instanceof Slides.Presentations.BatchUpdate);
	}
}
