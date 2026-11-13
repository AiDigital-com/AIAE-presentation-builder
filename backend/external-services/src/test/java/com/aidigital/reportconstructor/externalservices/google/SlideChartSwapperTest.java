package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.slides.v1.Slides;
import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.BatchUpdatePresentationRequest;
import com.google.api.services.slides.v1.model.CreateSheetsChartRequest;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Presentation;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.Size;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SlideChartSwapperTest {

	@Test
	void shouldCaptureEveryIdentifiedElementWithItsGeometryAndSlideTest() throws IOException {
		// Given: a deck with a charted slide, a slide without elements and an element missing its id
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		SlideChartSwapper swapper = new SlideChartSwapper(new ChartTemplateCatalog(), googleCalls);
		Slides slides = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		Size size = new Size();
		AffineTransform transform = new AffineTransform().setTranslateX(10d);
		Presentation deck = new Presentation().setSlides(List.of(
				new Page().setObjectId("p1").setPageElements(List.of(
						new PageElement().setObjectId("chart_1").setSize(size).setTransform(transform),
						new PageElement().setSize(new Size()))),
				new Page().setObjectId("p2")));
		doReturn(deck).when(googleCalls).execute(argThat((AbstractGoogleClientRequest<?> r) ->
				r instanceof Slides.Presentations.Get get && "deck-1".equals(get.getPresentationId())));
		List<String> errors = new ArrayList<>();

		// When:
		Map<String, ElementTransform> transforms = swapper.loadTransforms(slides, "deck-1", errors, "Daily");

		// Then: only the identified element is captured, with its own size, transform and owning slide
		assertThat(transforms).containsOnlyKeys("chart_1");
		assertThat(transforms.get("chart_1").size()).isSameAs(size);
		assertThat(transforms.get("chart_1").transform()).isSameAs(transform);
		assertThat(transforms.get("chart_1").slideId()).isEqualTo("p1");
		assertThat(errors).isEmpty();
	}

	@Test
	void shouldReturnNoTransformsForADeckWithoutSlidesTest() throws IOException {
		// Given:
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		SlideChartSwapper swapper = new SlideChartSwapper(new ChartTemplateCatalog(), googleCalls);
		Slides slides = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		doReturn(new Presentation()).when(googleCalls).execute(argThat((AbstractGoogleClientRequest<?> r) ->
				r instanceof Slides.Presentations.Get get && "deck-1".equals(get.getPresentationId())));
		List<String> errors = new ArrayList<>();

		// When:
		Map<String, ElementTransform> transforms = swapper.loadTransforms(slides, "deck-1", errors, "Monthly");

		// Then:
		assertThat(transforms).isEmpty();
		assertThat(errors).isEmpty();
	}

	@Test
	void shouldReportAnUnreadableLayoutUnderTheCallersTagTest() throws IOException {
		// Given:
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		SlideChartSwapper swapper = new SlideChartSwapper(new ChartTemplateCatalog(), googleCalls);
		Slides slides = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		doThrow(new IOException("quota exceeded")).when(googleCalls).execute(
				argThat((AbstractGoogleClientRequest<?> r) ->
				r instanceof Slides.Presentations.Get get && "deck-1".equals(get.getPresentationId())));
		List<String> errors = new ArrayList<>();

		// When:
		Map<String, ElementTransform> transforms = swapper.loadTransforms(slides, "deck-1", errors, "Distribution");

		// Then:
		assertThat(transforms).isEmpty();
		assertThat(errors).containsExactly("Distribution: could not read presentation layout — quota exceeded");
	}

	@Test
	void shouldDeleteThePlaceholderAndLinkTheNewChartInItsPlaceTest() throws IOException {
		// Given: a captured placeholder geometry and the catalog's shared in-sheet chart id
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setChartIdInSheet(555);
		SlideChartSwapper swapper = new SlideChartSwapper(templates, googleCalls);
		Slides slides = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		Size size = new Size();
		AffineTransform transform = new AffineTransform().setScaleX(1d);
		ElementTransform xform = new ElementTransform(size, transform, "slide_7");

		// When:
		swapper.replaceChartOnSlide(slides, "deck-1", "old_chart", "sheet-9", xform);

		// Then: one batch deletes the placeholder first, then creates a LINKED chart at the same spot
		ArgumentCaptor<Slides.Presentations.BatchUpdate> call =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(googleCalls).execute(call.capture());
		assertThat(call.getValue().getPresentationId()).isEqualTo("deck-1");
		List<Request> requests = ((BatchUpdatePresentationRequest) call.getValue().getJsonContent()).getRequests();
		assertThat(requests).hasSize(2);
		assertThat(requests.get(0).getDeleteObject().getObjectId()).isEqualTo("old_chart");
		CreateSheetsChartRequest create = requests.get(1).getCreateSheetsChart();
		assertThat(create.getSpreadsheetId()).isEqualTo("sheet-9");
		assertThat(create.getChartId()).isEqualTo(555);
		assertThat(create.getLinkingMode()).isEqualTo("LINKED");
		assertThat(create.getObjectId()).startsWith("ch_").hasSize(23);
		assertThat(create.getElementProperties().getPageObjectId()).isEqualTo("slide_7");
		assertThat(create.getElementProperties().getSize()).isSameAs(size);
		assertThat(create.getElementProperties().getTransform()).isSameAs(transform);
	}

	@Test
	void shouldUseTheExplicitChartIdAndABlankPageWhenTheSlideIsUnknownTest() throws IOException {
		// Given: geometry captured without its owning slide
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setChartIdInSheet(555);
		SlideChartSwapper swapper = new SlideChartSwapper(templates, googleCalls);
		Slides slides = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		ElementTransform xform = new ElementTransform(new Size(), new AffineTransform(), null);

		// When:
		swapper.replaceChartOnSlide(slides, "deck-1", "old_chart", "sheet-9", xform, 1234);

		// Then: the explicit id wins over the catalog's, and the page id is sent blank rather than null
		ArgumentCaptor<Slides.Presentations.BatchUpdate> call =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(googleCalls).execute(call.capture());
		CreateSheetsChartRequest create = ((BatchUpdatePresentationRequest) call.getValue().getJsonContent())
				.getRequests().get(1).getCreateSheetsChart();
		assertThat(create.getChartId()).isEqualTo(1234);
		assertThat(create.getElementProperties().getPageObjectId()).isEmpty();
	}

	@Test
	void shouldLeaveThePositionToTheApiWhenTheGeometryIsIncompleteTest() throws IOException {
		// Given: no geometry, geometry without a size and geometry without a transform
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		SlideChartSwapper swapper = new SlideChartSwapper(new ChartTemplateCatalog(), googleCalls);
		Slides slides = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();

		// When:
		swapper.replaceChartOnSlide(slides, "deck-1", "a", "sheet-1", null, 1);
		swapper.replaceChartOnSlide(slides, "deck-1", "b", "sheet-1",
				new ElementTransform(null, new AffineTransform(), "p1"), 1);
		swapper.replaceChartOnSlide(slides, "deck-1", "c", "sheet-1", new ElementTransform(new Size(), null, "p1"), 1);

		// Then: every create request is sent without element properties
		ArgumentCaptor<Slides.Presentations.BatchUpdate> call =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(googleCalls, times(3)).execute(call.capture());
		assertThat(call.getAllValues())
				.extracting(c -> ((BatchUpdatePresentationRequest) c.getJsonContent()).getRequests())
				.allSatisfy(requests -> assertThat(requests.get(1).getCreateSheetsChart().getElementProperties())
						.isNull())
				.extracting(requests -> requests.get(0).getDeleteObject().getObjectId())
				.containsExactly("a", "b", "c");
	}

	@Test
	void shouldDeleteOnlyTheChartWhenThereIsNothingToLinkTest() throws IOException {
		// Given:
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		SlideChartSwapper swapper = new SlideChartSwapper(new ChartTemplateCatalog(), googleCalls);
		Slides slides = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();

		// When:
		swapper.deleteChartFromSlide(slides, "deck-1", "sample_chart");

		// Then:
		ArgumentCaptor<Slides.Presentations.BatchUpdate> call =
				ArgumentCaptor.forClass(Slides.Presentations.BatchUpdate.class);
		verify(googleCalls).execute(call.capture());
		assertThat(call.getValue().getPresentationId()).isEqualTo("deck-1");
		List<Request> requests = ((BatchUpdatePresentationRequest) call.getValue().getJsonContent()).getRequests();
		assertThat(requests).singleElement()
				.satisfies(r -> assertThat(r.getDeleteObject().getObjectId()).isEqualTo("sample_chart"));
	}

	@Test
	void shouldPropagateAFailedBatchUpdateTest() throws IOException {
		// Given:
		GoogleCallTimer googleCalls = mock(GoogleCallTimer.class);
		SlideChartSwapper swapper = new SlideChartSwapper(new ChartTemplateCatalog(), googleCalls);
		Slides slides = new Slides.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null).build();
		doThrow(new IOException("rejected")).when(googleCalls).execute(argThat((AbstractGoogleClientRequest<?> r) ->
				r instanceof Slides.Presentations.BatchUpdate update && "deck-1".equals(update.getPresentationId())));

		// When-Then:
		assertThatThrownBy(() -> swapper.deleteChartFromSlide(slides, "deck-1", "sample_chart"))
				.isInstanceOf(IOException.class)
				.hasMessage("rejected");
	}
}
