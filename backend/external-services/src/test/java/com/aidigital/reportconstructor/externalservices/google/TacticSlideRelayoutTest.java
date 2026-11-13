package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Dimension;
import com.google.api.services.slides.v1.model.Group;
import com.google.api.services.slides.v1.model.Image;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.Shape;
import com.google.api.services.slides.v1.model.Size;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import com.google.api.services.slides.v1.model.TextRun;
import com.google.api.services.slides.v1.model.UpdatePageElementTransformRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TacticSlideRelayoutTest {

	@Test
	void shouldMoveTheTilesIntoTheDroppedCardAndGrowTheTopDeliveryCardTest() {
		// Given: the EOC tactic slide's real geometry (template 11qzOC7…, tactic 2) — the daily pacing card
		// opening the content row, the monthly card the EOM drop removes, two of the KPI tile frames (groups of a
		// frame plus an empty text box nudged above it) with one tile label, and the Top Delivery Content card:
		// a group frame, its heading, the creative image and the creative-name text that overhangs the card
		TacticSlideRelayout relayout = new TacticSlideRelayout(new SlideBlockLocator());
		Function<double[], Size> size = wh -> new Size()
				.setWidth(new Dimension().setMagnitude(wh[0]).setUnit("EMU"))
				.setHeight(new Dimension().setMagnitude(wh[1]).setUnit("EMU"));
		Function<double[], AffineTransform> at = t -> new AffineTransform()
				.setScaleX(t[0]).setScaleY(t[1]).setTranslateX(t[2]).setTranslateY(t[3]).setUnit("EMU");
		Function<String, Shape> text = s -> new Shape().setShapeType("TEXT_BOX").setText(new TextContent()
				.setTextElements(List.of(new TextElement().setTextRun(new TextRun().setContent(s)))));
		double[] card = {35_605_375, 36_990_000};
		double[] box = {3_000_000, 3_000_000};
		Function<double[], PageElement> frameGroup = t -> new PageElement().setElementGroup(new Group()
				.setChildren(List.of(
						new PageElement().setObjectId("frame").setShape(new Shape().setShapeType("CUSTOM"))
								.setSize(size.apply(new double[] {37_095_275, 29_652_850}))
								.setTransform(at.apply(new double[] {0.04, 0.04, 0, 0})),
						new PageElement().setObjectId("pad").setShape(new Shape().setShapeType("TEXT_BOX"))
								.setSize(size.apply(box))
								.setTransform(at.apply(new double[] {0.4946, 0.4049, 0, -28_575})))))
				.setTransform(at.apply(t));
		PageElement dailyCard = new PageElement().setObjectId("daily-card").setShape(new Shape())
				.setSize(size.apply(card)).setTransform(at.apply(new double[] {0.1016, 0.0599, 2_183_375, 1_265_260}));
		PageElement dailyHeading = new PageElement().setObjectId("daily-heading").setSize(size.apply(box))
				.setShape(text.apply("Daily pacing Dynamic\n"))
				.setTransform(at.apply(new double[] {1.2057, 0.0564, 2_177_026, 1_388_550}));
		PageElement monthlyCard = new PageElement().setObjectId("monthly-card").setShape(new Shape())
				.setSize(size.apply(card)).setTransform(at.apply(new double[] {0.1016, 0.0334, 2_186_725, 3_638_045}));
		PageElement monthlyHeading = new PageElement().setObjectId("monthly-heading").setSize(size.apply(box))
				.setShape(text.apply("Monthly Impressions Distribution\n"))
				.setTransform(at.apply(new double[] {1.1926, 0.0385, 2_222_754, 3_705_379}));
		PageElement impsTile = frameGroup.apply(new double[] {0.5439, 0.4845, 5_977_150, 1_267_019.585})
				.setObjectId("imps-tile");
		PageElement volumeTile = frameGroup.apply(new double[] {0.5439, 0.3599, 7_933_275, 1_952_573.15})
				.setObjectId("volume-tile");
		PageElement impsLabel = new PageElement().setObjectId("imps-label").setSize(size.apply(box))
				.setShape(text.apply("IMPRESSIONS\n"))
				.setTransform(at.apply(new double[] {0.269, 0.0359, 5_983_625, 1_583_533}));
		// the frequency value box: drawn 0.81M EMU tall, far below its 0.43M EMU tile frame — it must move with
		// the tiles but must not count towards the cluster's size, or the tiles never fit the gap
		PageElement frequencyValue = new PageElement().setObjectId("frequency-value").setSize(size.apply(box))
				.setShape(text.apply("4.10\n\n"))
				.setTransform(at.apply(new double[] {0.269, 0.2686, 5_983_925, 1_999_622}));
		PageElement deliveryCard = frameGroup.apply(new double[] {1.8579, 1.8581, 5_983_625, 2_662_842.205})
				.setObjectId("delivery-card");
		PageElement deliveryHeading = new PageElement().setObjectId("delivery-heading").setSize(size.apply(box))
				.setShape(text.apply("Top Delivery Content\n"))
				.setTransform(at.apply(new double[] {0.9189, 0.0564, 5_983_625, 2_758_815}));
		PageElement creative = new PageElement().setObjectId("creative").setImage(new Image().setContentUrl("u"))
				.setSize(size.apply(new double[] {38_700, 21_550}))
				.setTransform(at.apply(new double[] {39.4464, 39.4448, 6_232_249.815, 3_107_876.1875}));
		PageElement creativeName = new PageElement().setObjectId("creative-name").setSize(size.apply(box))
				.setShape(text.apply("Creative Name: \n42nd Annual Main Street Fest 15s\n"))
				.setTransform(at.apply(new double[] {0.9189, 0.1059, 6_218_800, 4_024_280}));
		Page page = new Page().setObjectId("tactic-2").setPageElements(List.of(dailyCard, dailyHeading,
				monthlyCard, monthlyHeading, impsTile, volumeTile, impsLabel, frequencyValue, deliveryCard,
				deliveryHeading, creative, creativeName));

		// When:
		List<Request> requests = relayout.relayoutRequests(page, List.of("MONTHLYIMPRESSIONSDISTRIBUTION"),
				"TOPDELIVERYCONTENT", "DAILYPACINGDYNAMIC");

		// Then: the tiles move as one cluster, centred in the monthly card's box (3,617,506 × 1,235,466 EMU at
		// 2,186,725 / 3,638,045); the cluster is measured by its frames, 2,763,170 × 1,112,436 from
		// 5,977,150 / 1,267,020, and the overhanging value box moves with it
		Map<String, UpdatePageElementTransformRequest> byId = requests.stream()
				.map(Request::getUpdatePageElementTransform)
				.collect(Collectors.toMap(UpdatePageElementTransformRequest::getObjectId, Function.identity()));
		assertThat(byId).containsOnlyKeys("imps-tile", "volume-tile", "imps-label", "frequency-value",
				"delivery-card", "delivery-heading", "creative");
		double dx = 2_186_725 + (3_617_506.1 - 2_763_170.3) / 2 - 5_977_150;
		double dy = 3_638_045 + (1_235_466 - 1_112_435.6) / 2 - 1_267_019.585;
		for (String tile : List.of("imps-tile", "volume-tile", "imps-label", "frequency-value")) {
			assertThat(byId.get(tile).getApplyMode()).isEqualTo("RELATIVE");
			assertThat(byId.get(tile).getTransform().getTranslateX()).isCloseTo(dx, within(2.0));
			assertThat(byId.get(tile).getTransform().getTranslateY()).isCloseTo(dy, within(2.0));
		}

		// Then: the Top Delivery frame's top rises to the pacing card's top while its bottom stays at 4,866,761
		AffineTransform frame = byId.get("delivery-card").getTransform();
		assertThat(byId.get("delivery-card").getApplyMode()).isEqualTo("ABSOLUTE");
		assertThat(frame.getTranslateY()).isCloseTo(1_265_260, within(2.0));
		assertThat(frame.getTranslateY() + frame.getScaleY() * 1_186_114.0).isCloseTo(4_866_761, within(50.0));
		assertThat(frame.getScaleX()).isEqualTo(1.8579);

		// Then: its heading rises by the same amount
		assertThat(byId.get("delivery-heading").getTransform().getTranslateY())
				.isCloseTo(1_265_260 - 2_662_842.205, within(1.0));

		// Then: the creative grows into the freed height, keeping its aspect ratio, and stays between the lifted
		// heading and the creative-name text, which is not moved
		AffineTransform image = byId.get("creative").getTransform();
		double imageTop = image.getTranslateY();
		double imageBottom = imageTop + 21_550 * image.getScaleY();
		assertThat(image.getScaleX()).isGreaterThan(39.4464);
		assertThat(image.getScaleX() / image.getScaleY()).isCloseTo(39.4464 / 39.4448, within(1e-9));
		assertThat(imageTop).isGreaterThan(2_928_015 - 1_397_582.205);
		assertThat(imageBottom).isLessThan(4_024_280);
		assertThat(image.getTranslateX() + 38_700 * image.getScaleX()).isLessThanOrEqualTo(8_740_401);
	}

	@Test
	void shouldLeaveTheSlideAsItIsWhenItHasNoGrowCardTest() {
		// Given: a slide carrying the dropped block but no "Top Delivery Content" card
		TacticSlideRelayout relayout = new TacticSlideRelayout(new SlideBlockLocator());
		Size cardSize = new Size().setWidth(new Dimension().setMagnitude(35_605_375.0))
				.setHeight(new Dimension().setMagnitude(36_990_000.0));
		PageElement monthlyCard = new PageElement().setObjectId("monthly-card").setShape(new Shape())
				.setSize(cardSize).setTransform(new AffineTransform().setScaleX(0.1016).setScaleY(0.0334)
						.setTranslateX(2_186_725.0).setTranslateY(3_638_045.0));
		PageElement monthlyHeading = new PageElement().setObjectId("monthly-heading")
				.setSize(new Size().setWidth(new Dimension().setMagnitude(3_000_000.0))
						.setHeight(new Dimension().setMagnitude(3_000_000.0)))
				.setShape(new Shape().setText(new TextContent().setTextElements(List.of(new TextElement()
						.setTextRun(new TextRun().setContent("Monthly Impressions Distribution\n"))))))
				.setTransform(new AffineTransform().setScaleX(1.1926).setScaleY(0.0385)
						.setTranslateX(2_222_754.0).setTranslateY(3_705_379.0));
		Page page = new Page().setObjectId("other").setPageElements(List.of(monthlyCard, monthlyHeading));

		// When:
		List<Request> requests = relayout.relayoutRequests(page, List.of("MONTHLYIMPRESSIONSDISTRIBUTION"),
				"TOPDELIVERYCONTENT", "DAILYPACINGDYNAMIC");

		// Then: nothing is moved half-way
		assertThat(requests).isEmpty();
	}

	@Test
	void shouldLeaveASlideAloneWhenThereIsNothingToRelayoutTest() {
		// Given: a missing page, a page read without elements, blank labels, and a slide with no dropped block
		TacticSlideRelayout relayout = new TacticSlideRelayout(new SlideBlockLocator());
		Page withoutElements = new Page().setObjectId("p1");
		Page withoutDroppedBlock = new Page().setObjectId("p2").setPageElements(List.of(
				new PageElement().setObjectId("title").setShape(new Shape().setText(new TextContent()
						.setTextElements(List.of(new TextElement().setTextRun(new TextRun().setContent("Hi\n"))))))));
		List<String> dropped = List.of("MONTHLY");

		// When-Then:
		assertThat(relayout.relayoutRequests(null, dropped, "GROW", "ANCHOR")).isEmpty();
		assertThat(relayout.relayoutRequests(withoutElements, dropped, "GROW", "ANCHOR")).isEmpty();
		assertThat(relayout.relayoutRequests(withoutDroppedBlock, dropped, "", "ANCHOR")).isEmpty();
		assertThat(relayout.relayoutRequests(withoutDroppedBlock, dropped, "GROW", "")).isEmpty();
		assertThat(relayout.relayoutRequests(withoutDroppedBlock, dropped, "GROW", "ANCHOR")).isEmpty();
	}

	@Test
	void shouldSkipASlideWhoseGrowHeadingOrAnchorCardIsMissingTest() {
		// Given: three slides with the dropped block — one without the grow heading, one whose grow heading sits
		// on no card, and one without the anchor card
		SlideBlockLocator locator = mock(SlideBlockLocator.class);
		TacticSlideRelayout relayout = new TacticSlideRelayout(locator);
		PageElement dropHeading = new PageElement().setObjectId("drop-heading");
		PageElement dropCard = new PageElement().setObjectId("drop-card");
		PageElement growHeading = new PageElement().setObjectId("grow-heading");
		PageElement growCard = new PageElement().setObjectId("grow-card");
		List<PageElement> elements = List.of(dropHeading, dropCard, growHeading, growCard);
		Page noGrowHeading = new Page().setObjectId("s1").setPageElements(elements);
		Page noGrowCard = new Page().setObjectId("s2").setPageElements(elements);
		Page noAnchor = new Page().setObjectId("s3").setPageElements(elements);
		for (Page page : List.of(noGrowHeading, noGrowCard, noAnchor)) {
			when(locator.findHeading(page, List.of("DROP"))).thenReturn(dropHeading);
		}
		when(locator.enclosingCard(elements, dropHeading)).thenReturn(dropCard);
		when(locator.boundsOf(dropCard)).thenReturn(new ElementBounds(0, 0, 100, 50));
		when(locator.findHeading(noGrowCard, List.of("GROW"))).thenReturn(new PageElement().setObjectId("loose"));
		when(locator.findHeading(noGrowCard, List.of("ANCHOR"))).thenReturn(dropHeading);
		when(locator.findHeading(noAnchor, List.of("GROW"))).thenReturn(growHeading);
		when(locator.enclosingCard(elements, growHeading)).thenReturn(growCard);

		// When-Then: none of them is half-moved
		assertThat(relayout.relayoutRequests(noGrowHeading, List.of("DROP"), "GROW", "ANCHOR")).isEmpty();
		assertThat(relayout.relayoutRequests(noGrowCard, List.of("DROP"), "GROW", "ANCHOR")).isEmpty();
		assertThat(relayout.relayoutRequests(noAnchor, List.of("DROP"), "GROW", "ANCHOR")).isEmpty();
		verify(locator, never()).boundsOf(growCard);
	}

	@Test
	void shouldSkipASlideWhoseClusterIsMissingOrDoesNotFitTheVacatedBoxTest() {
		// Given: a dropped block far smaller than the tile frame sitting above the grow card, and a twin slide
		// with nothing above the grow card at all
		SlideBlockLocator locator = mock(SlideBlockLocator.class);
		TacticSlideRelayout relayout = new TacticSlideRelayout(locator);
		PageElement dropHeading = new PageElement().setObjectId("drop-heading");
		PageElement dropCard = new PageElement().setObjectId("drop-card");
		PageElement growHeading = new PageElement().setObjectId("grow-heading");
		PageElement growCard = new PageElement().setObjectId("grow-card");
		PageElement anchorHeading = new PageElement().setObjectId("anchor-heading");
		PageElement anchorCard = new PageElement().setObjectId("anchor-card");
		PageElement tile = new PageElement().setObjectId("tile").setElementGroup(new Group());
		PageElement unnamed = new PageElement();
		List<PageElement> withTile = List.of(dropHeading, dropCard, growHeading, growCard, anchorHeading,
				anchorCard, tile, unnamed);
		List<PageElement> withoutTile = List.of(dropHeading, dropCard, growHeading, growCard, anchorHeading,
				anchorCard);
		Page tooBig = new Page().setObjectId("s1").setPageElements(withTile);
		Page empty = new Page().setObjectId("s2").setPageElements(withoutTile);
		for (Page page : List.of(tooBig, empty)) {
			when(locator.findHeading(page, List.of("DROP"))).thenReturn(dropHeading);
			when(locator.findHeading(page, List.of("GROW"))).thenReturn(growHeading);
			when(locator.findHeading(page, List.of("ANCHOR"))).thenReturn(anchorHeading);
			when(locator.enclosingCard(page.getPageElements(), dropHeading)).thenReturn(dropCard);
			when(locator.enclosingCard(page.getPageElements(), growHeading)).thenReturn(growCard);
			when(locator.enclosingCard(page.getPageElements(), anchorHeading)).thenReturn(anchorCard);
		}
		when(locator.boundsOf(dropCard)).thenReturn(new ElementBounds(0, 0, 1_000_000, 500_000));
		when(locator.boundsOf(growCard)).thenReturn(new ElementBounds(2_000_000, 3_000_000, 4_000_000, 6_000_000));
		when(locator.boundsOf(anchorCard)).thenReturn(new ElementBounds(0, 1_000_000, 1_500_000, 6_000_000));
		when(locator.boundsOf(tile)).thenReturn(new ElementBounds(2_100_000, 1_100_000, 3_900_000, 2_900_000));

		// When:
		List<Request> tooBigRequests = relayout.relayoutRequests(tooBig, List.of("DROP"), "GROW", "ANCHOR");
		List<Request> emptyRequests = relayout.relayoutRequests(empty, List.of("DROP"), "GROW", "ANCHOR");

		// Then: the 1.8M x 1.8M EMU tile cannot go into the 1M x 0.5M EMU box; an empty column has nothing to move
		assertThat(tooBigRequests).isEmpty();
		assertThat(emptyRequests).isEmpty();
	}

	@Test
	void shouldNotGrowACardWhoseTopIsAlreadyAtTheRowTopTest() {
		// Given:
		TacticSlideRelayout relayout = new TacticSlideRelayout(mock(SlideBlockLocator.class));
		PageElement card = new PageElement().setObjectId("card");

		// When:
		List<Request> requests = relayout.growRequests(List.of(card), card, new ElementBounds(0, 100, 100, 300),
				new PageElement().setObjectId("heading"), 100);

		// Then:
		assertThat(requests).isEmpty();
	}

	@Test
	void shouldOnlyLiftTheImagesOfACardHoldingMoreThanOneTest() {
		// Given: a card with two images, a caption, an unnamed element, one element off the card and one whose
		// geometry is unknown
		SlideBlockLocator locator = mock(SlideBlockLocator.class);
		TacticSlideRelayout relayout = new TacticSlideRelayout(locator);
		PageElement card = new PageElement().setObjectId("card");
		PageElement heading = new PageElement().setObjectId("heading");
		PageElement left = new PageElement().setObjectId("img-left").setImage(new Image());
		PageElement right = new PageElement().setObjectId("img-right").setImage(new Image());
		PageElement caption = new PageElement().setObjectId("caption").setShape(new Shape());
		PageElement far = new PageElement().setObjectId("far").setShape(new Shape());
		PageElement ghost = new PageElement().setObjectId("ghost").setShape(new Shape());
		PageElement unnamed = new PageElement().setShape(new Shape());
		when(locator.boundsOf(heading)).thenReturn(new ElementBounds(10, 100, 90, 120));
		when(locator.boundsOf(left)).thenReturn(new ElementBounds(10, 130, 50, 170));
		when(locator.boundsOf(right)).thenReturn(new ElementBounds(50, 130, 90, 170));
		when(locator.boundsOf(caption)).thenReturn(new ElementBounds(10, 250, 90, 290));
		when(locator.boundsOf(far)).thenReturn(new ElementBounds(500, 500, 600, 600));
		ElementBounds cardBounds = new ElementBounds(0, 100, 100, 300);

		// When:
		List<Request> requests = relayout.growRequests(
				List.of(card, heading, left, right, caption, far, ghost, unnamed), card, cardBounds, heading, 50);

		// Then: the frame is stretched up to 50 keeping its bottom, the heading and both images are lifted
		UpdatePageElementTransformRequest stretch = requests.get(0).getUpdatePageElementTransform();
		assertThat(stretch.getObjectId()).isEqualTo("card");
		assertThat(stretch.getApplyMode()).isEqualTo("ABSOLUTE");
		assertThat(stretch.getTransform().getScaleY()).isEqualTo(1.25);
		assertThat(stretch.getTransform().getTranslateY()).isEqualTo(-75.0);
		assertThat(requests.subList(1, requests.size())).containsExactly(
				relayout.translate("heading", 0, -50),
				relayout.translate("img-left", 0, -50),
				relayout.translate("img-right", 0, -50));
	}

	@Test
	void shouldOnlyLiftTheImageWhenTheHeadingHasNoKnownGeometryTest() {
		// Given:
		SlideBlockLocator locator = mock(SlideBlockLocator.class);
		TacticSlideRelayout relayout = new TacticSlideRelayout(locator);
		PageElement card = new PageElement().setObjectId("card");
		PageElement heading = new PageElement().setObjectId("heading");
		PageElement image = new PageElement().setObjectId("img").setImage(new Image());
		when(locator.boundsOf(image)).thenReturn(new ElementBounds(10, 130, 90, 170));

		// When:
		List<Request> requests = relayout.growRequests(List.of(card, heading, image), card,
				new ElementBounds(0, 100, 100, 300), heading, 80);

		// Then: the image is not refitted against a heading it cannot measure
		assertThat(requests).hasSize(3);
		assertThat(requests.get(2)).isEqualTo(relayout.translate("img", 0, -20));
	}

	@Test
	void shouldOnlyMoveAnImageThatHasNoRoomToGrowTest() {
		// Given: text that starts above the lifted heading's bottom, leaving negative height for the image
		SlideBlockLocator locator = mock(SlideBlockLocator.class);
		TacticSlideRelayout relayout = new TacticSlideRelayout(locator);
		PageElement image = new PageElement().setObjectId("img").setImage(new Image());
		when(locator.boundsOf(image)).thenReturn(new ElementBounds(10, 100, 90, 140));

		// When:
		Request request = relayout.refitImage(image, new ElementBounds(0, 0, 100, 300), 90, 5, 80);

		// Then: it keeps its size and only moves to sit the original gap below the heading
		assertThat(request).isEqualTo(relayout.translate("img", 0, -5));
	}

	@Test
	void shouldPlaceAnElementReadWithoutATransformAsIfItWereIdentityTest() {
		// Given:
		TacticSlideRelayout relayout = new TacticSlideRelayout(mock(SlideBlockLocator.class));
		PageElement element = new PageElement().setObjectId("box");

		// When:
		Request request = relayout.placed(element, new ElementBounds(10, 20, 110, 220), 0, 0, 2, 3);

		// Then: the box is scaled about the identity origin and its corner put at (0, 0)
		AffineTransform transform = request.getUpdatePageElementTransform().getTransform();
		assertThat(transform.getScaleX()).isEqualTo(2.0);
		assertThat(transform.getScaleY()).isEqualTo(3.0);
		assertThat(transform.getShearX()).isEqualTo(0.0);
		assertThat(transform.getShearY()).isEqualTo(0.0);
		assertThat(transform.getTranslateX()).isEqualTo(-20.0);
		assertThat(transform.getTranslateY()).isEqualTo(-60.0);
		assertThat(transform.getUnit()).isEqualTo("EMU");
	}

	@Test
	void shouldFitOnlyABoxNoWiderAndNoTallerThanTheTargetTest() {
		// Given:
		TacticSlideRelayout relayout = new TacticSlideRelayout(new SlideBlockLocator());
		ElementBounds outer = new ElementBounds(0, 0, 1_000_000, 500_000);

		// When-Then: the containment tolerance absorbs a rounding overhang, nothing more
		assertThat(relayout.fits(new ElementBounds(5, 5, 1_010_000, 505_000), outer)).isTrue();
		assertThat(relayout.fits(new ElementBounds(0, 0, 1_100_000, 100), outer)).isFalse();
		assertThat(relayout.fits(new ElementBounds(0, 0, 100, 600_000), outer)).isFalse();
	}

	@Test
	void shouldMeasureAClusterByItsTextlessFrameShapeOnlyTest() {
		// Given: a blank frame shape and a value text box drawn taller than the frame
		SlideBlockLocator locator = mock(SlideBlockLocator.class);
		TacticSlideRelayout relayout = new TacticSlideRelayout(locator);
		PageElement frame = new PageElement().setObjectId("frame").setShape(new Shape());
		PageElement value = new PageElement().setObjectId("value").setShape(new Shape());
		when(locator.textOf(frame)).thenReturn(" ");
		when(locator.textOf(value)).thenReturn("106,834");
		when(locator.boundsOf(frame)).thenReturn(new ElementBounds(0, 0, 100, 40));
		when(locator.boundsOf(value)).thenReturn(new ElementBounds(10, 5, 90, 80));

		// When:
		ElementBounds bounds = relayout.frameBounds(List.of(frame, value));

		// Then: the overhanging value box does not count towards the cluster's size
		assertThat(bounds).isEqualTo(new ElementBounds(0, 0, 100, 40));
	}

	@Test
	void shouldMeasureAClusterWithoutFramesByAllOfItsElementsTest() {
		// Given: a cluster of value text boxes only, one of them without a known geometry
		SlideBlockLocator locator = mock(SlideBlockLocator.class);
		TacticSlideRelayout relayout = new TacticSlideRelayout(locator);
		PageElement value = new PageElement().setObjectId("value").setShape(new Shape());
		PageElement label = new PageElement().setObjectId("label").setShape(new Shape());
		PageElement unknown = new PageElement().setObjectId("unknown");
		when(locator.textOf(value)).thenReturn("4.10");
		when(locator.textOf(label)).thenReturn("FREQUENCY");
		when(locator.boundsOf(value)).thenReturn(new ElementBounds(10, 20, 30, 40));
		when(locator.boundsOf(label)).thenReturn(new ElementBounds(5, 25, 50, 35));

		// When:
		ElementBounds bounds = relayout.frameBounds(List.of(value, label, unknown));

		// Then:
		assertThat(bounds).isEqualTo(new ElementBounds(5, 20, 50, 40));
	}

	@Test
	void shouldCollectOnlyTheNamedElementsCentredInTheColumnAboveTheCardTest() {
		// Given: a column from the row top (100) to the card top (300) between x 200 and 400
		SlideBlockLocator locator = mock(SlideBlockLocator.class);
		TacticSlideRelayout relayout = new TacticSlideRelayout(locator);
		PageElement inside = new PageElement().setObjectId("inside");
		PageElement leftOf = new PageElement().setObjectId("left-of");
		PageElement below = new PageElement().setObjectId("below");
		PageElement unknown = new PageElement().setObjectId("unknown");
		PageElement unnamed = new PageElement();
		when(locator.boundsOf(inside)).thenReturn(new ElementBounds(250, 150, 350, 250));
		when(locator.boundsOf(leftOf)).thenReturn(new ElementBounds(0, 150, 100, 250));
		when(locator.boundsOf(below)).thenReturn(new ElementBounds(250, 350, 350, 450));

		// When:
		List<PageElement> cluster = relayout.clusterAbove(List.of(inside, leftOf, below, unknown, unnamed),
				new ElementBounds(200, 300, 400, 600), 100);

		// Then:
		assertThat(cluster).containsExactly(inside);
	}
}
