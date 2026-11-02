package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Dimension;
import com.google.api.services.slides.v1.model.Group;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.SheetsChart;
import com.google.api.services.slides.v1.model.Shape;
import com.google.api.services.slides.v1.model.Size;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import com.google.api.services.slides.v1.model.TextRun;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SlideBlockLocatorTest {

	@Test
	void shouldReturnTheCardWithEverythingOnItWhenTheHeadingSitsOnACardTest() {
		// Given: the EOC tactic slide's geometry — the monthly card with its heading and chart on it, the daily
		// pacing card above it, and the contribution chart to its left
		SlideBlockLocator locator = new SlideBlockLocator();
		Size cardSize = new Size()
				.setWidth(new Dimension().setMagnitude(35_605_375.0).setUnit("EMU"))
				.setHeight(new Dimension().setMagnitude(36_990_000.0).setUnit("EMU"));
		Size textSize = new Size()
				.setWidth(new Dimension().setMagnitude(3_000_000.0).setUnit("EMU"))
				.setHeight(new Dimension().setMagnitude(3_000_000.0).setUnit("EMU"));
		Size chartSize = new Size()
				.setWidth(new Dimension().setMagnitude(30_000.0).setUnit("EMU"))
				.setHeight(new Dimension().setMagnitude(18_550.0).setUnit("EMU"));
		PageElement dailyCard = new PageElement().setObjectId("daily-card").setShape(new Shape()).setSize(cardSize)
				.setTransform(new AffineTransform().setScaleX(0.1016).setScaleY(0.0599)
						.setTranslateX(2_183_375.0).setTranslateY(1_265_260.0));
		PageElement monthlyCard = new PageElement().setObjectId("monthly-card").setShape(new Shape())
				.setSize(cardSize).setTransform(new AffineTransform().setScaleX(0.1016).setScaleY(0.0334)
						.setTranslateX(2_186_725.0).setTranslateY(3_638_045.0));
		PageElement heading = new PageElement().setObjectId("monthly-heading").setSize(textSize)
				.setShape(new Shape().setText(new TextContent().setTextElements(List.of(new TextElement()
						.setTextRun(new TextRun().setContent("Monthly Impressions Distribution\n"))))))
				.setTransform(new AffineTransform().setScaleX(1.1926).setScaleY(0.0385)
						.setTranslateX(2_222_754.0).setTranslateY(3_705_379.0));
		PageElement monthlyChart = new PageElement().setObjectId("monthly-chart").setSize(chartSize)
				.setSheetsChart(new SheetsChart().setSpreadsheetId("monthly-workbook"))
				.setTransform(new AffineTransform().setScaleX(53.9392).setScaleY(47.2857)
						.setTranslateX(3_183_925.0).setTranslateY(3_887_350.0));
		PageElement contributionChart = new PageElement().setObjectId("contribution-chart").setSize(chartSize)
				.setSheetsChart(new SheetsChart().setSpreadsheetId("dist-workbook"))
				.setTransform(new AffineTransform().setScaleX(40.3275).setScaleY(41.2507)
						.setTranslateX(611_855.0).setTranslateY(3_705_375.0));
		Page page = new Page().setObjectId("p7")
				.setPageElements(List.of(dailyCard, contributionChart, monthlyCard, heading, monthlyChart));

		// When:
		List<String> ids = locator.blockObjectIds(page, List.of("MONTHLYIMPRESSIONSDISTRIBUTION"));

		// Then: the monthly card, its heading and its chart go; the neighbouring blocks stay
		assertThat(ids).containsExactly("monthly-card", "monthly-heading", "monthly-chart");
	}

	@Test
	void shouldReturnTheHeadingAloneWhenNoCardEnclosesItTest() {
		// Given: a heading with no text-less shape underneath it
		SlideBlockLocator locator = new SlideBlockLocator();
		PageElement heading = new PageElement().setObjectId("heading")
				.setSize(new Size()
						.setWidth(new Dimension().setMagnitude(3_000_000.0).setUnit("EMU"))
						.setHeight(new Dimension().setMagnitude(3_000_000.0).setUnit("EMU")))
				.setShape(new Shape().setText(new TextContent().setTextElements(List.of(new TextElement()
						.setTextRun(new TextRun().setContent("MONTHLY impressions — distribution"))))))
				.setTransform(new AffineTransform().setScaleX(1.0).setScaleY(0.04)
						.setTranslateX(100.0).setTranslateY(100.0));
		Page page = new Page().setObjectId("slide").setPageElements(List.of(heading));

		// When:
		List<String> ids = locator.blockObjectIds(page, List.of("MONTHLYIMPRESSIONSDISTRIBUTION"));

		// Then: the heading is removed on its own, matched despite casing and punctuation
		assertThat(ids).containsExactly("heading");
	}

	@Test
	void shouldMeasureAGroupAsItsVisibleChildrenMappedThroughTheGroupTransformTest() {
		// Given: the template's Top Delivery card — a group of a frame and an empty text box nudged above it,
		// both positioned relative to the group
		SlideBlockLocator locator = new SlideBlockLocator();
		PageElement frame = new PageElement().setObjectId("frame").setShape(new Shape().setShapeType("CUSTOM"))
				.setSize(new Size().setWidth(new Dimension().setMagnitude(37_095_275.0))
						.setHeight(new Dimension().setMagnitude(29_652_850.0)))
				.setTransform(new AffineTransform().setScaleX(0.04).setScaleY(0.04));
		PageElement pad = new PageElement().setObjectId("pad").setShape(new Shape().setShapeType("TEXT_BOX"))
				.setSize(new Size().setWidth(new Dimension().setMagnitude(3_000_000.0))
						.setHeight(new Dimension().setMagnitude(3_000_000.0)))
				.setTransform(new AffineTransform().setScaleX(0.4946).setScaleY(0.4049).setTranslateY(-28_575.0));
		PageElement group = new PageElement().setObjectId("card")
				.setElementGroup(new Group().setChildren(List.of(frame, pad)))
				.setTransform(new AffineTransform().setScaleX(1.8579).setScaleY(1.8581)
						.setTranslateX(5_983_625.0).setTranslateY(2_662_842.0));

		// When:
		ElementBounds bounds = locator.boundsOf(group);

		// Then: the group measures as its frame on the slide — the empty text box poking above it is ignored
		assertThat(bounds.left()).isCloseTo(5_983_625, within(1.0));
		assertThat(bounds.top()).isCloseTo(2_662_842, within(1.0));
		assertThat(bounds.right()).isCloseTo(5_983_625 + 1.8579 * 1_483_811, within(1.0));
		assertThat(bounds.bottom()).isCloseTo(2_662_842 + 1.8581 * 1_186_114, within(1.0));
	}

	@Test
	void shouldReturnNothingWhenNoHeadingMatchesTest() {
		// Given: a slide whose only heading is a different block's
		SlideBlockLocator locator = new SlideBlockLocator();
		PageElement heading = new PageElement().setObjectId("daily-heading")
				.setShape(new Shape().setText(new TextContent().setTextElements(List.of(new TextElement()
						.setTextRun(new TextRun().setContent("Daily pacing Dynamic\n"))))));
		Page page = new Page().setObjectId("slide").setPageElements(List.of(heading));

		// When:
		List<String> ids = locator.blockObjectIds(page, List.of("MONTHLYIMPRESSIONSDISTRIBUTION"));

		// Then:
		assertThat(ids).isEmpty();
	}
}
