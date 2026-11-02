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
}
