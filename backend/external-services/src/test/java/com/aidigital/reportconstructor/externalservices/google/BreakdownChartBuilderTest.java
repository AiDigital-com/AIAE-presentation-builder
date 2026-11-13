package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.observability.external.ExternalCallTimer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.aidigital.reportconstructor.externalservices.google.charts.model.BreakdownChart;
import com.aidigital.reportconstructor.externalservices.google.charts.model.BreakdownChartsRequest;
import com.aidigital.reportconstructor.externalservices.google.charts.model.ChartSlice;
import com.aidigital.reportconstructor.externalservices.google.drive.ChartFileSharer;
import com.aidigital.reportconstructor.externalservices.google.drive.DriveCopier;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.EmbeddedChart;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.slides.v1.Slides;
import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Presentation;
import com.google.api.services.slides.v1.model.SheetsChart;
import com.google.api.services.slides.v1.model.Size;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BreakdownChartBuilderTest {

	private BreakdownChartBuilder newBuilder() {
		// The pure methods under test use only the ChartSheetWriter (for label normalisation); the Google
		// collaborators are not touched, so nulls are safe here.
		return new BreakdownChartBuilder(
				null, new ChartSheetWriter(new GoogleCallTimer(new ExternalCallTimer(new SimpleMeterRegistry()))), null, null, null, new BreakdownChartCatalog(),
				new BreakdownSlideNaming(), null, null);
	}

	@Test
	void parseChartId_treatsBlankOrNonNumericAsUnconfiguredTest() {
		// Given: the builder
		BreakdownChartBuilder builder = newBuilder();

		// When-Then: only a clean integer parses; the empty default and junk are treated as unset
		assertThat(builder.parseChartId("1087145314")).isEqualTo(1087145314);
		assertThat(builder.parseChartId(" 42 ")).isEqualTo(42);
		assertThat(builder.parseChartId("")).isNull();
		assertThat(builder.parseChartId(null)).isNull();
		assertThat(builder.parseChartId("not-a-number")).isNull();
	}

	@Test
	void valuesByLabel_shouldDropNonPositiveAndNormaliseMatchedLabelsTest() {
		// Given: slices for four devices, one with zero impressions and one CTV row — a series whose labels
		// are matched against the workbook's own category column
		BreakdownChartBuilder builder = newBuilder();
		List<ChartSlice> slices = List.of(
				new ChartSlice("Mobile", 1000.4),
				new ChartSlice("CTV", 500.0),
				new ChartSlice("Desktop", 0.0),
				new ChartSlice("Tablet", -5.0));

		// When:
		Map<String, Double> byLabel = builder.valuesByLabel(slices, false);

		// Then: only positive slices survive, keyed by the normalised label (CTV → connectedtv)
		assertThat(byLabel).containsOnly(
				Map.entry("mobile", 1000.4),
				Map.entry("connectedtv", 500.0));

		// And: the impressions writer gets them rounded
		assertThat(builder.rounded(byLabel)).containsOnly(
				Map.entry("mobile", 1000L),
				Map.entry("connectedtv", 500L));
	}

	@Test
	void valuesByLabel_shouldKeepLabelsVerbatimWhenTheyAreWrittenIntoTheWorkbookTest() {
		// Given: audience segments, whose names go onto the chart as the reader sees them
		BreakdownChartBuilder builder = newBuilder();
		List<ChartSlice> slices = List.of(
				new ChartSlice("  In-Market: Luxury Travel ", 142.0),
				new ChartSlice("Affinity: Foodies", 118.5),
				new ChartSlice("", 100.0),
				new ChartSlice("Affinity: Sports", 0.0));

		// When:
		Map<String, Double> byLabel = builder.valuesByLabel(slices, true);

		// Then: trimmed but not normalised, in sheet order, and the blank label and the zero are dropped
		assertThat(byLabel).containsExactly(
				Map.entry("In-Market: Luxury Travel", 142.0),
				Map.entry("Affinity: Foodies", 118.5));
	}

	@Test
	void valuesByLabel_shouldBeEmptyWhenNoSliceIsPositiveTest() {
		// Given: every slice is non-positive
		BreakdownChartBuilder builder = newBuilder();
		List<ChartSlice> slices = List.of(
				new ChartSlice("Mobile", 0.0),
				new ChartSlice("Desktop", -1.0));

		// When-Then: nothing to chart
		assertThat(builder.valuesByLabel(slices, false)).isEmpty();
	}

	@Test
	void loadBreakdownChartElements_shouldKeyEveryChartOnABreakdownSlideByItsSourceWorkbookTest() {
		// Given: a deck whose audience copy carries two linked charts and whose device copy carries one; a
		// tactic slide and a chartless breakdown slide are also present
		BreakdownChartBuilder builder = newBuilder();

		// When:
		Map<String, Map<String, ChartElementRef>> bySlide = builder.chartsBySlideOf(List.of(
				chartSlide("bd_aud_2", Map.of("book_age", "el_age", "book_seg", "el_seg")),
				chartSlide("bd_dev_2", Map.of("book_dev", "el_dev")),
				chartSlide("tct_2", Map.of("book_daily", "el_daily")),
				new Page().setObjectId("bd_geo_2")));

		// Then: both audience charts are reachable, each by the workbook it links to — which is what lets the
		// age series and the segment series each replace their own chart
		assertThat(bySlide.get("bd_aud_2").keySet()).containsExactlyInAnyOrder("book_age", "book_seg");
		assertThat(bySlide.get("bd_aud_2").get("book_seg").objectId()).isEqualTo("el_seg");
		assertThat(bySlide.get("bd_dev_2").get("book_dev").objectId()).isEqualTo("el_dev");

		// And: non-breakdown slides are absent, while a chartless breakdown slide is kept with an empty index —
		// that is what separates "this slide carries no such chart" from "this slide was never inserted"
		assertThat(bySlide).doesNotContainKey("tct_2");
		assertThat(bySlide.get("bd_geo_2")).isEmpty();
	}

	@Test
	void shouldLinkTheConfiguredChartWhenTheCopyStillCarriesItTest() {
		// Given: a copy holding the configured chart on a chart sheet and another chart on the data tab
		BreakdownChartBuilder builder = newBuilder();
		List<Sheet> tabs = List.of(
				new Sheet().setProperties(new SheetProperties().setTitle("Sheet1"))
						.setCharts(List.of(new EmbeddedChart().setChartId(111))),
				new Sheet().setProperties(new SheetProperties().setTitle("Chart1"))
						.setCharts(List.of(new EmbeddedChart().setChartId(1497669750))));

		// When:
		Integer chartId = builder.pickChartId(tabs, "Sheet1", 1497669750);

		// Then:
		assertThat(chartId).isEqualTo(1497669750);
	}

	@Test
	void shouldFallBackToTheDataTabChartWhenTheConfiguredOneIsGoneTest() {
		// Given: a source workbook whose chart was recreated, so neither chart carries the configured id
		BreakdownChartBuilder builder = newBuilder();
		List<Sheet> tabs = List.of(
				new Sheet().setProperties(new SheetProperties().setTitle("Chart1"))
						.setCharts(List.of(new EmbeddedChart().setChartId(222))),
				new Sheet().setProperties(new SheetProperties().setTitle("Sheet1"))
						.setCharts(List.of(new EmbeddedChart().setChartId(333))));

		// When:
		Integer chartId = builder.pickChartId(tabs, "Sheet1", 1497669750);

		// Then: the chart drawn over the written values, not the first one in tab order
		assertThat(chartId).isEqualTo(333);
	}

	@Test
	void shouldTakeAnyChartWhenTheDataTabHasNoneTest() {
		// Given:
		BreakdownChartBuilder builder = newBuilder();
		List<Sheet> tabs = List.of(
				new Sheet().setProperties(new SheetProperties().setTitle("Sheet1")),
				new Sheet().setProperties(new SheetProperties().setTitle("Chart1"))
						.setCharts(List.of(new EmbeddedChart().setChartId(444))));

		// When:
		Integer chartId = builder.pickChartId(tabs, "Sheet1", 1497669750);

		// Then:
		assertThat(chartId).isEqualTo(444);
	}

	@Test
	void shouldReportNoChartForAChartlessCopyTest() {
		// Given:
		BreakdownChartBuilder builder = newBuilder();
		List<Sheet> tabs = List.of(new Sheet().setProperties(new SheetProperties().setTitle("Sheet1")));

		// When:
		Integer chartId = builder.pickChartId(tabs, "Sheet1", 1497669750);

		// Then:
		assertThat(chartId).isNull();
	}

	@Test
	void shouldCopyFillAndLinkEachSeriesChartOnItsBreakdownSlideTest() throws IOException {
		// Given: tactic 2's device and audience-segment copies, each carrying the chart linked to its workbook
		DriveCopier driveCopier = mock(DriveCopier.class);
		ChartSheetWriter writer = mock(ChartSheetWriter.class);
		ChartSpecBuilder specs = mock(ChartSpecBuilder.class);
		SlideChartSwapper swapper = mock(SlideChartSwapper.class);
		ChartFileSharer sharer = mock(ChartFileSharer.class);
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		BreakdownChartCatalog catalog = new BreakdownChartCatalog();
		catalog.setSourceSheetIds(Map.of("dev", "book_dev", "aud-seg", "book_seg"));
		catalog.setChartIdInSheet(Map.of("dev", "111", "aud-seg", "222"));
		catalog.setDataStartRow(Map.of("aud-seg", 3));
		BreakdownChartBuilder builder = new BreakdownChartBuilder(driveCopier, writer, specs, swapper,
				new ChartErrorTranslator(), catalog, new BreakdownSlideNaming(), sharer, googleCalls);
		Drive drive = mock(Drive.class);
		Sheets sheets = new Sheets.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		Slides slides = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		Size devSize = new Size();
		AffineTransform devAt = new AffineTransform().setTranslateX(1d);
		Size segSize = new Size();
		AffineTransform segAt = new AffineTransform().setTranslateX(2d);
		Presentation deck = new Presentation().setSlides(List.of(
				new Page().setObjectId("bd_dev_2").setPageElements(List.of(new PageElement().setObjectId("el_dev")
						.setSize(devSize).setTransform(devAt)
						.setSheetsChart(new SheetsChart().setSpreadsheetId("book_dev")))),
				new Page().setObjectId("bd_aud_2").setPageElements(List.of(new PageElement().setObjectId("el_seg")
						.setSize(segSize).setTransform(segAt)
						.setSheetsChart(new SheetsChart().setSpreadsheetId("book_seg"))))));
		doReturn(deck).when(googleCalls).execute(argThat((AbstractGoogleClientRequest<?> r) ->
				r instanceof Slides.Presentations.Get get && "deck-1".equals(get.getPresentationId())));
		doReturn(new Spreadsheet().setSheets(List.of(new Sheet().setProperties(new SheetProperties()
				.setTitle("Sheet1")).setCharts(List.of(new EmbeddedChart().setChartId(111))))))
				.when(googleCalls).execute(argThat((AbstractGoogleClientRequest<?> r) ->
						r instanceof Sheets.Spreadsheets.Get get && "copy-dev".equals(get.getSpreadsheetId())));
		doReturn(new Spreadsheet().setSheets(List.of(new Sheet().setProperties(new SheetProperties()
				.setTitle("Data")).setCharts(List.of(new EmbeddedChart().setChartId(999))))))
				.when(googleCalls).execute(argThat((AbstractGoogleClientRequest<?> r) ->
						r instanceof Sheets.Spreadsheets.Get get && "copy-seg".equals(get.getSpreadsheetId())));
		when(driveCopier.createFolder(same(drive), eq("Breakdown charts — Acme"))).thenReturn("folder-1");
		when(driveCopier.copyFile(same(drive), eq("book_dev"), eq("Breakdown dev tactic 2 — Acme"), eq("folder-1")))
				.thenReturn("copy-dev");
		when(driveCopier.copyFile(same(drive), eq("book_seg"), eq("Breakdown aud-seg tactic 2 — Acme"),
				eq("folder-1"))).thenReturn("copy-seg");
		when(specs.findDataTab(same(sheets), eq("copy-dev"))).thenReturn("Sheet1");
		when(specs.findDataTab(same(sheets), eq("copy-seg"))).thenReturn("Data");
		when(writer.normalizeBreakdownLabel(eq("Mobile"))).thenReturn("mobile");
		when(writer.writeBreakdownImpressions(same(sheets), eq("copy-dev"), eq("Sheet1"), eq(Map.of("mobile", 1000L))))
				.thenReturn(1);
		when(writer.writeBreakdownSeries(same(sheets), eq("copy-seg"), eq("Data"), eq(Map.of("Luxury", 42.5)), eq(3)))
				.thenReturn(1);
		BreakdownChartsRequest request = new BreakdownChartsRequest("deck-1", "Acme", List.of(
				new BreakdownChart("dev", 2, List.of(new ChartSlice("Mobile", 999.6))),
				new BreakdownChart(" AUD-SEG ", 2, List.of(new ChartSlice(" Luxury ", 42.5)))), null);

		// When:
		List<String> errors = builder.buildBreakdownCharts(new ChartClients(drive, sheets, slides), request);

		// Then: both charts are linked in place; the device copy keeps its configured chart, the segment copy's
		// recreated chart is found on its data tab
		assertThat(errors).isEmpty();
		verify(sharer).shareFolder(same(drive), eq("folder-1"));
		verify(sharer).shareLooseCopy(same(drive), eq("folder-1"), eq("copy-dev"));
		verify(sharer).shareLooseCopy(same(drive), eq("folder-1"), eq("copy-seg"));
		verify(swapper).replaceChartOnSlide(same(slides), eq("deck-1"), eq("el_dev"), eq("copy-dev"),
				eq(new ElementTransform(devSize, devAt, "bd_dev_2")), eq(111));
		verify(swapper).replaceChartOnSlide(same(slides), eq("deck-1"), eq("el_seg"), eq("copy-seg"),
				eq(new ElementTransform(segSize, segAt, "bd_aud_2")), eq(999));

		// And: the deck read asks for the chart links, the copy reads ask only for titles and chart ids
		@SuppressWarnings({"unchecked", "rawtypes"})
		ArgumentCaptor<AbstractGoogleClientRequest<?>> calls =
				(ArgumentCaptor) ArgumentCaptor.forClass(AbstractGoogleClientRequest.class);
		verify(googleCalls, times(3)).execute(calls.capture());
		List<AbstractGoogleClientRequest<?>> sent = calls.getAllValues();
		assertThat(((Slides.Presentations.Get) sent.get(0)).getFields())
				.isEqualTo("slides.objectId,slides.pageElements(objectId,size,transform,sheetsChart)");
		Sheets.Spreadsheets.Get copyRead = (Sheets.Spreadsheets.Get) sent.get(1);
		assertThat(copyRead.getSpreadsheetId()).isEqualTo("copy-dev");
		assertThat(copyRead.getIncludeGridData()).isFalse();
		assertThat(copyRead.getFields()).isEqualTo("sheets(properties(title),charts(chartId))");
		assertThat(((Sheets.Spreadsheets.Get) sent.get(2)).getSpreadsheetId()).isEqualTo("copy-seg");
	}

	@Test
	void shouldReportOrDropEachBreakdownChartThatCannotBeLinkedTest() throws IOException {
		// Given: a folder that cannot be created, unknown series, misconfigured series and segment jobs that miss
		// their slide, their chart, their data, their labels, their copied chart or their copy
		DriveCopier driveCopier = mock(DriveCopier.class);
		ChartSheetWriter writer = mock(ChartSheetWriter.class);
		ChartSpecBuilder specs = mock(ChartSpecBuilder.class);
		SlideChartSwapper swapper = mock(SlideChartSwapper.class);
		ChartFileSharer sharer = mock(ChartFileSharer.class);
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		BreakdownChartCatalog catalog = new BreakdownChartCatalog();
		catalog.setSourceSheetIds(Map.of("aud", "book_age", "dev", " ", "aud-seg", "book_seg"));
		catalog.setChartIdInSheet(Map.of("aud", "not-a-number", "dev", "1", "aud-seg", "5"));
		BreakdownChartBuilder builder = new BreakdownChartBuilder(driveCopier, writer, specs, swapper,
				new ChartErrorTranslator(), catalog, new BreakdownSlideNaming(), sharer, googleCalls);
		Drive drive = mock(Drive.class);
		Sheets sheets = new Sheets.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		Slides slides = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		List<Page> pages = new ArrayList<>();
		pages.add(new Page().setObjectId("bd_aud_4").setPageElements(List.of(new PageElement().setObjectId("el_4")
				.setSheetsChart(new SheetsChart().setSpreadsheetId("book_age")))));
		for (int n = 5; n <= 8; n++) {
			pages.add(new Page().setObjectId("bd_aud_" + n).setPageElements(List.of(new PageElement()
					.setObjectId("el_" + n).setSheetsChart(new SheetsChart().setSpreadsheetId("book_seg")))));
		}
		doReturn(new Presentation().setSlides(pages)).when(googleCalls).execute(
				argThat((AbstractGoogleClientRequest<?> r) ->
						r instanceof Slides.Presentations.Get get && "deck-1".equals(get.getPresentationId())));
		doReturn(new Spreadsheet().setSheets(List.of(new Sheet().setProperties(new SheetProperties()
				.setTitle("Data"))))).when(googleCalls).execute(argThat((AbstractGoogleClientRequest<?> r) ->
						r instanceof Sheets.Spreadsheets.Get get && "copy-7".equals(get.getSpreadsheetId())));
		when(driveCopier.createFolder(same(drive), eq("Breakdown charts — Acme"))).thenThrow(new IOException("full"));
		when(driveCopier.copyFile(same(drive), eq("book_seg"), eq("Breakdown aud-seg tactic 6 — Acme"), isNull()))
				.thenReturn("copy-6");
		when(driveCopier.copyFile(same(drive), eq("book_seg"), eq("Breakdown aud-seg tactic 7 — Acme"), isNull()))
				.thenReturn("copy-7");
		when(driveCopier.copyFile(same(drive), eq("book_seg"), eq("Breakdown aud-seg tactic 8 — Acme"), isNull()))
				.thenThrow(new IOException("copy refused"));
		when(specs.findDataTab(same(sheets), eq("copy-6"))).thenReturn("Data");
		when(specs.findDataTab(same(sheets), eq("copy-7"))).thenReturn("Data");
		when(writer.writeBreakdownSeries(same(sheets), eq("copy-6"), eq("Data"), eq(Map.of("Sports", 9.0)), eq(2)))
				.thenReturn(0);
		when(writer.writeBreakdownSeries(same(sheets), eq("copy-7"), eq("Data"), eq(Map.of("Sports", 9.0)), eq(2)))
				.thenReturn(1);
		List<ChartSlice> slices = List.of(new ChartSlice("Sports", 9.0));
		BreakdownChartsRequest request = new BreakdownChartsRequest("deck-1", "Acme", Arrays.asList(
				new BreakdownChart(null, 1, slices),
				new BreakdownChart("  ", 1, slices),
				new BreakdownChart("geo", 1, slices),
				new BreakdownChart("aud", 1, slices),
				new BreakdownChart("dev", 1, slices),
				new BreakdownChart("aud-seg", 3, slices),
				new BreakdownChart("aud-seg", 4, slices),
				new BreakdownChart("aud-seg", 5, List.of(new ChartSlice("Sports", 0.0))),
				new BreakdownChart("aud-seg", 6, slices),
				new BreakdownChart("aud-seg", 7, slices),
				new BreakdownChart("aud-seg", 8, slices)), null);

		// When:
		List<String> errors = builder.buildBreakdownCharts(new ChartClients(drive, sheets, slides), request);

		// Then: unknown series are skipped silently, every other miss is reported under its own cause
		assertThat(errors).containsExactly(
				"Breakdown aud tactic 1: no chart-source spreadsheet id / chart id configured",
				"Breakdown dev tactic 1: no chart-source spreadsheet id / chart id configured",
				"Breakdown aud-seg tactic 3: breakdown slide bd_aud_3 is not in the deck, so its chart was not "
						+ "linked (the tactic's main slide was most likely not built)",
				"Breakdown aud-seg tactic 4: no chart linked to book_seg found on breakdown slide bd_aud_4 — check "
						+ "that the master breakdown slide's chart really points at that workbook",
				"Breakdown aud-seg tactic 6: no category labels matched the chart source — chart left empty",
				"Breakdown aud-seg tactic 7: the copied chart source copy-7 carries no chart to link",
				"Breakdown aud-seg tactic 8: copy refused");

		// And: the data-less tactic's sample chart is removed, copies go to the root, and nothing is swapped
		verify(swapper).deleteChartFromSlide(same(slides), eq("deck-1"), eq("el_5"));
		verify(sharer).shareFolder(same(drive), isNull());
		verify(sharer).shareLooseCopy(same(drive), isNull(), eq("copy-6"));
		verifyNoMoreInteractions(swapper);
	}

	@Test
	void shouldReportAnUnreadableDeckAndMissTheSlidesItCouldNotSeeTest() throws IOException {
		// Given: the deck read fails
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		BreakdownChartCatalog catalog = new BreakdownChartCatalog();
		catalog.setSourceSheetIds(Map.of("dev", "book_dev"));
		catalog.setChartIdInSheet(Map.of("dev", "1"));
		BreakdownChartBuilder builder = new BreakdownChartBuilder(mock(DriveCopier.class),
				mock(ChartSheetWriter.class), mock(ChartSpecBuilder.class), mock(SlideChartSwapper.class),
				new ChartErrorTranslator(), catalog, new BreakdownSlideNaming(), mock(ChartFileSharer.class),
				googleCalls);
		Slides slides = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		doThrow(new IOException("timeout")).when(googleCalls).execute(argThat((AbstractGoogleClientRequest<?> r) ->
				r instanceof Slides.Presentations.Get get && "deck-1".equals(get.getPresentationId())));
		BreakdownChartsRequest request = new BreakdownChartsRequest("deck-1", "Acme", List.of(
				new BreakdownChart("dev", 1, List.of(new ChartSlice("Mobile", 5.0))),
				new BreakdownChart("aud", 1, List.of(new ChartSlice("18-24", 5.0)))), null);

		// When:
		List<String> errors = builder.buildBreakdownCharts(new ChartClients(null, null, slides), request);

		// Then: the read failure is reported, the configured series misses its slide, the unconfigured one
		// reports its configuration
		assertThat(errors).containsExactly(
				"Breakdown charts: could not read presentation layout — timeout",
				"Breakdown dev tactic 1: breakdown slide bd_dev_1 is not in the deck, so its chart was not linked "
						+ "(the tactic's main slide was most likely not built)",
				"Breakdown aud tactic 1: no chart-source spreadsheet id / chart id configured");
	}

	@Test
	void shouldReadNoBreakdownChartsFromADeckWithoutSlidesTest() throws IOException {
		// Given:
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		BreakdownChartBuilder builder = new BreakdownChartBuilder(null, null, null, null, null,
				new BreakdownChartCatalog(), new BreakdownSlideNaming(), null, googleCalls);
		Slides slides = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		doReturn(new Presentation()).when(googleCalls).execute(argThat((AbstractGoogleClientRequest<?> r) ->
				r instanceof Slides.Presentations.Get get && "deck-1".equals(get.getPresentationId())));
		List<String> errors = new ArrayList<>();

		// When:
		Map<String, Map<String, ChartElementRef>> bySlide =
				builder.loadBreakdownChartElements(slides, "deck-1", errors);

		// Then:
		assertThat(bySlide).isEmpty();
		assertThat(errors).isEmpty();
	}

	@Test
	void shouldDoNothingWithoutBreakdownJobsTest() {
		// Given:
		DriveCopier driveCopier = mock(DriveCopier.class);
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		BreakdownChartBuilder builder = new BreakdownChartBuilder(driveCopier, mock(ChartSheetWriter.class),
				mock(ChartSpecBuilder.class), mock(SlideChartSwapper.class), new ChartErrorTranslator(),
				new BreakdownChartCatalog(), new BreakdownSlideNaming(), mock(ChartFileSharer.class), googleCalls);

		// When:
		List<String> none = builder.buildBreakdownCharts(null,
				new BreakdownChartsRequest("deck-1", "Acme", null, null));
		List<String> empty = builder.buildBreakdownCharts(null,
				new BreakdownChartsRequest("deck-1", "Acme", List.of(), null));

		// Then: the deck is not even read
		assertThat(none).isEmpty();
		assertThat(empty).isEmpty();
		verifyNoInteractions(driveCopier, googleCalls);
	}

	@Test
	void shouldSkipUnidentifiedSlidesAndUnlinkedElementsAndKeepTheFirstChartPerWorkbookTest() {
		// Given: an id-less slide, and a breakdown slide holding a plain shape, a chart without a workbook, an
		// id-less chart and two charts linked to the same workbook
		BreakdownChartBuilder builder = new BreakdownChartBuilder(null, null, null, null, null,
				new BreakdownChartCatalog(), new BreakdownSlideNaming(), null, null);
		List<Page> slides = List.of(
				new Page().setPageElements(List.of(new PageElement().setObjectId("lost")
						.setSheetsChart(new SheetsChart().setSpreadsheetId("book_x")))),
				new Page().setObjectId("bd_dev_1").setPageElements(List.of(
						new PageElement().setObjectId("shape"),
						new PageElement().setObjectId("unlinked").setSheetsChart(new SheetsChart()),
						new PageElement().setSheetsChart(new SheetsChart().setSpreadsheetId("book_dev")),
						new PageElement().setObjectId("first").setSheetsChart(new SheetsChart()
								.setSpreadsheetId("book_dev")),
						new PageElement().setObjectId("second").setSheetsChart(new SheetsChart()
								.setSpreadsheetId("book_dev")))));

		// When:
		Map<String, Map<String, ChartElementRef>> bySlide = builder.chartsBySlideOf(slides);

		// Then:
		assertThat(bySlide).containsOnlyKeys("bd_dev_1");
		assertThat(bySlide.get("bd_dev_1")).containsOnlyKeys("book_dev");
		assertThat(bySlide.get("bd_dev_1").get("book_dev").objectId()).isEqualTo("first");
		assertThat(bySlide.get("bd_dev_1").get("book_dev").transform().slideId()).isEqualTo("bd_dev_1");
	}

	@Test
	void shouldIgnoreIdlessChartsAndUntitledTabsWhenPickingAChartTest() {
		// Given: an untitled tab with an id-less chart and a chart, then the data tab without charts
		BreakdownChartBuilder builder = newBuilder();
		List<Sheet> tabs = List.of(
				new Sheet().setCharts(List.of(new EmbeddedChart(), new EmbeddedChart().setChartId(555))),
				new Sheet().setProperties(new SheetProperties().setTitle("Sheet1")));

		// When-Then: the first identified chart anywhere is taken; no tabs or no data tab name still resolve
		assertThat(builder.pickChartId(tabs, "Sheet1", 1)).isEqualTo(555);
		assertThat(builder.pickChartId(tabs, null, 1)).isEqualTo(555);
		assertThat(builder.pickChartId(null, "Sheet1", 1)).isNull();
	}

	@Test
	void shouldTakeTheFirstDataTabChartWhenItCarriesSeveralTest() {
		// Given: a data tab with two recreated charts, neither carrying the configured id
		BreakdownChartBuilder builder = newBuilder();
		List<Sheet> tabs = List.of(new Sheet().setProperties(new SheetProperties().setTitle("Sheet1"))
				.setCharts(List.of(new EmbeddedChart().setChartId(61), new EmbeddedChart().setChartId(62))));

		// When-Then: the first one drawn wins, also when the data tab is unknown
		assertThat(builder.pickChartId(tabs, "Sheet1", 1)).isEqualTo(61);
		assertThat(builder.pickChartId(tabs, null, 1)).isEqualTo(61);
	}

	@Test
	void shouldReturnNoValuesForMissingSlicesAndSkipNullOnesTest() {
		// Given:
		BreakdownChartBuilder builder = newBuilder();

		// When-Then:
		assertThat(builder.valuesByLabel(null, true)).isEmpty();
		assertThat(builder.valuesByLabel(Arrays.asList(null, new ChartSlice(null, 3.0), new ChartSlice("A", 2.0)),
				true)).containsExactly(Map.entry("A", 2.0));
	}

	private Page chartSlide(String slideId, Map<String, String> elementIdBySourceBook) {
		List<PageElement> elements = new ArrayList<>();
		elementIdBySourceBook.forEach((book, elementId) -> elements.add(new PageElement()
				.setObjectId(elementId)
				.setSize(new Size())
				.setTransform(new AffineTransform())
				.setSheetsChart(new SheetsChart().setSpreadsheetId(book).setChartId(1))));
		return new Page().setObjectId(slideId).setPageElements(elements);
	}
}
