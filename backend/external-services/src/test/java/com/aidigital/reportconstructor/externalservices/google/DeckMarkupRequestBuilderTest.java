package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.ports.DeckMarkup;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.Shape;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import com.google.api.services.slides.v1.model.TextRun;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeckMarkupRequestBuilderTest {

	private final DeckMarkupRequestBuilder builder = new DeckMarkupRequestBuilder();

	@Test
	void shouldBoldTheMarkedPhraseAndDeleteItsMarkersTest() {
		// Given: a shape whose text run starts at index 0 and carries one marked phrase
		Page page = new Page().setPageElements(List.of(new PageElement().setObjectId("thought").setShape(new Shape()
				.setText(new TextContent().setTextElements(List.of(
						new TextElement().setStartIndex(0).setTextRun(new TextRun().setContent("**Display beat plan** on every line.\n"))))))));

		// When:
		List<Request> requests = builder.deckMarkupRequests(List.of(page));

		// Then: the shape is reset to regular, the phrase between the markers is bolded, and the closing
		// marker is deleted before the opening one so the earlier index stays valid
		assertThat(requests).hasSize(4);
		assertThat(requests.get(0).getUpdateTextStyle().getTextRange().getType()).isEqualTo("ALL");
		assertThat(requests.get(0).getUpdateTextStyle().getStyle().getBold()).isFalse();
		assertThat(requests.get(1).getUpdateTextStyle().getStyle().getBold()).isTrue();
		assertThat(requests.get(1).getUpdateTextStyle().getTextRange().getStartIndex()).isEqualTo(2);
		assertThat(requests.get(1).getUpdateTextStyle().getTextRange().getEndIndex()).isEqualTo(19);
		assertThat(requests.get(2).getDeleteText().getTextRange().getStartIndex()).isEqualTo(19);
		assertThat(requests.get(3).getDeleteText().getTextRange().getStartIndex()).isEqualTo(0);
		assertThat(requests).allMatch(r -> "thought".equals(r.getUpdateTextStyle() != null
				? r.getUpdateTextStyle().getObjectId() : r.getDeleteText().getObjectId()));
	}

	@Test
	void shouldDeleteAnUnpairedMarkerWithoutBoldingAnythingTest() {
		// Given: a phrase whose closing marker was cut off by the length cap
		Page page = new Page().setPageElements(List.of(new PageElement().setObjectId("story").setShape(new Shape()
				.setText(new TextContent().setTextElements(List.of(
						new TextElement().setStartIndex(0).setTextRun(new TextRun().setContent("Steady pacing, **strong reach.\n"))))))));

		// When:
		List<Request> requests = builder.deckMarkupRequests(List.of(page));

		// Then: the shape is reset to regular and the stray marker is removed
		assertThat(requests).hasSize(2);
		assertThat(requests.get(1).getDeleteText().getTextRange().getStartIndex()).isEqualTo(15);
		assertThat(requests.get(1).getDeleteText().getTextRange().getEndIndex()).isEqualTo(17);
	}

	@Test
	void shouldDeleteAShapeWhoseWholeTextIsTheHideMarkerTest() {
		// Given: an unused segment chip, written as the hide marker
		Page page = new Page().setPageElements(List.of(new PageElement().setObjectId("chip3").setShape(new Shape()
				.setText(new TextContent().setTextElements(List.of(
						new TextElement().setStartIndex(0).setTextRun(new TextRun()
								.setContent(DeckMarkup.HIDE_SHAPE + "\n"))))))));

		// When:
		List<Request> requests = builder.deckMarkupRequests(List.of(page));

		// Then: the chip is removed outright
		assertThat(requests).hasSize(1);
		assertThat(requests.getFirst().getDeleteObject().getObjectId()).isEqualTo("chip3");
	}

	@Test
	void shouldLeaveShapesWithoutMarkersUntouchedTest() {
		// Given: plain copy only
		Page page = new Page().setPageElements(List.of(new PageElement().setObjectId("plain").setShape(new Shape()
				.setText(new TextContent().setTextElements(List.of(
						new TextElement().setStartIndex(0).setTextRun(new TextRun().setContent("No emphasis here.\n"))))))));

		// When:
		List<Request> requests = builder.deckMarkupRequests(List.of(page));

		// Then:
		assertThat(requests).isEmpty();
		assertThat(builder.deckMarkupRequests(null)).isEmpty();
	}
}
