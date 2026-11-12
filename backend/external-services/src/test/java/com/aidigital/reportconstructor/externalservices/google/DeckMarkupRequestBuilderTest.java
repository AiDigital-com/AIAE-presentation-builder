package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.externalservices.google.deck.DeckMarkers;
import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Dimension;
import com.google.api.services.slides.v1.model.OpaqueColor;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.RgbColor;
import com.google.api.services.slides.v1.model.Shape;
import com.google.api.services.slides.v1.model.ShapeBackgroundFill;
import com.google.api.services.slides.v1.model.ShapeProperties;
import com.google.api.services.slides.v1.model.Size;
import com.google.api.services.slides.v1.model.SolidFill;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import com.google.api.services.slides.v1.model.TextRun;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeckMarkupRequestBuilderTest {

	private final DeckMarkupRequestBuilder builder = new DeckMarkupRequestBuilder(new SlideBlockLocator());

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
	void shouldStyleTheLeadPhraseBoldBlueAndTheRestRegularBodyTest() {
		// Given: a publisher takeaway written into a bold blue placeholder, opening with a marked headline
		Page page = new Page().setPageElements(List.of(new PageElement().setObjectId("pub2").setShape(new Shape()
				.setText(new TextContent().setTextElements(List.of(
						new TextElement().setStartIndex(0).setTextRun(new TextRun()
								.setContent("[[Spread Delivery]] — Capped per-publisher volume.\n"))))))));

		// When:
		List<Request> requests = builder.deckMarkupRequests(List.of(page));

		// Then: the whole text turns regular body colour, the headline between the brackets turns bold blue,
		// and the closing brackets are deleted before the opening ones so the earlier index stays valid
		assertThat(requests).hasSize(4);
		assertThat(requests.get(0).getUpdateTextStyle().getTextRange().getType()).isEqualTo("ALL");
		assertThat(requests.get(0).getUpdateTextStyle().getStyle().getBold()).isFalse();
		assertThat(requests.get(0).getUpdateTextStyle().getStyle().getForegroundColor().getOpaqueColor().getRgbColor())
				.isEqualTo(DeckMarkupRequestBuilder.BODY_COLOR);
		assertThat(requests.get(1).getUpdateTextStyle().getStyle().getBold()).isTrue();
		assertThat(requests.get(1).getUpdateTextStyle().getStyle().getForegroundColor().getOpaqueColor().getRgbColor())
				.isEqualTo(DeckMarkupRequestBuilder.LEAD_COLOR);
		assertThat(requests.get(1).getUpdateTextStyle().getTextRange().getStartIndex()).isEqualTo(2);
		assertThat(requests.get(1).getUpdateTextStyle().getTextRange().getEndIndex()).isEqualTo(17);
		assertThat(requests.get(2).getDeleteText().getTextRange().getStartIndex()).isEqualTo(17);
		assertThat(requests.get(3).getDeleteText().getTextRange().getStartIndex()).isEqualTo(0);
		assertThat(requests.get(3).getDeleteText().getTextRange().getEndIndex()).isEqualTo(2);
	}

	@Test
	void shouldStyleALeadTakeawayOnADarkPanelWhiteOverLightBodyTest() {
		// Given: the Geo slide's blue "WHAT THE MAP TELLS US" card, with an unrendered-fill insight box inside it
		Size unit = new Size().setWidth(new Dimension().setMagnitude(3_000_000d).setUnit("EMU"))
				.setHeight(new Dimension().setMagnitude(3_000_000d).setUnit("EMU"));
		PageElement panel = new PageElement().setObjectId("mapCard").setSize(unit)
				.setTransform(new AffineTransform().setScaleX(1.1692).setScaleY(1.3002)
						.setTranslateX(5_322_094d).setTranslateY(928_688d))
				.setShape(new Shape().setShapeProperties(new ShapeProperties().setShapeBackgroundFill(
						new ShapeBackgroundFill().setSolidFill(new SolidFill().setAlpha(1f).setColor(new OpaqueColor()
								.setRgbColor(new RgbColor().setRed(0.003921569f).setGreen(0.1254902f)
										.setBlue(0.94509804f)))))));
		PageElement insight = new PageElement().setObjectId("geoInsight1").setSize(unit)
				.setTransform(new AffineTransform().setScaleX(0.993).setScaleY(0.041)
						.setTranslateX(5_650_706d).setTranslateY(1_357_312d))
				.setShape(new Shape().setShapeProperties(new ShapeProperties().setShapeBackgroundFill(
								new ShapeBackgroundFill().setPropertyState("NOT_RENDERED").setSolidFill(new SolidFill()
										.setColor(new OpaqueColor().setRgbColor(new RgbColor().setRed(1f).setGreen(1f)
												.setBlue(1f))))))
						.setText(new TextContent().setTextElements(List.of(new TextElement().setStartIndex(0)
								.setTextRun(new TextRun().setContent("[[Fairfax drove delivery]] with 490,437 impr.\n"))))));
		Page page = new Page().setPageElements(List.of(panel, insight));

		// When:
		List<Request> requests = builder.deckMarkupRequests(List.of(page));

		// Then: the body turns the light on-dark colour and the lead phrase white, never brand blue on blue
		assertThat(requests).hasSize(4);
		assertThat(requests.get(0).getUpdateTextStyle().getStyle().getForegroundColor().getOpaqueColor().getRgbColor())
				.isEqualTo(DeckMarkupRequestBuilder.ON_DARK_BODY_COLOR);
		assertThat(requests.get(1).getUpdateTextStyle().getStyle().getForegroundColor().getOpaqueColor().getRgbColor())
				.isEqualTo(DeckMarkupRequestBuilder.ON_DARK_LEAD_COLOR);
		assertThat(requests.get(1).getUpdateTextStyle().getObjectId()).isEqualTo("geoInsight1");
	}

	@Test
	void shouldDeleteAnUnpairedLeadBracketWithoutStylingAPhraseTest() {
		// Given: a lead phrase whose closing brackets were cut off by the length cap
		Page page = new Page().setPageElements(List.of(new PageElement().setObjectId("geo1").setShape(new Shape()
				.setText(new TextContent().setTextElements(List.of(
						new TextElement().setStartIndex(0).setTextRun(new TextRun()
								.setContent("[[Fairfax drew half the listed delivery.\n"))))))));

		// When:
		List<Request> requests = builder.deckMarkupRequests(List.of(page));

		// Then: the text is reset to regular body colour and the stray brackets are removed, nothing turns blue
		assertThat(requests).hasSize(2);
		assertThat(requests.get(0).getUpdateTextStyle().getStyle().getBold()).isFalse();
		assertThat(requests.get(1).getDeleteText().getTextRange().getStartIndex()).isEqualTo(0);
		assertThat(requests.get(1).getDeleteText().getTextRange().getEndIndex()).isEqualTo(2);
	}

	@Test
	void shouldDeleteAShapeWhoseWholeTextIsTheHideMarkerTest() {
		// Given: an unused segment chip, written as the hide marker
		Page page = new Page().setPageElements(List.of(new PageElement().setObjectId("chip3").setShape(new Shape()
				.setText(new TextContent().setTextElements(List.of(
						new TextElement().setStartIndex(0).setTextRun(new TextRun()
								.setContent(DeckMarkers.HIDE_SHAPE + "\n"))))))));

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
