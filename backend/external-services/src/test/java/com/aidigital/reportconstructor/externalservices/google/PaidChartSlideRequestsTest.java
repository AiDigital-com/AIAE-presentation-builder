package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Dimension;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.Size;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PaidChartSlideRequestsTest {

	private final PaidChartSlideRequests requests = new PaidChartSlideRequests();

	@Test
	void shouldFindMarkedImagesWithTheElementsDrawnAboveThemTest() {
		// Given: a slide whose sample donut has a hole label drawn above it, and a slide without markers
		Page slide = new Page().setObjectId("s1").setPageElements(List.of(
				new PageElement().setObjectId("card"),
				new PageElement().setObjectId("img").setTitle(" pso_chart_placement_donut "),
				new PageElement().setObjectId("hole_label")));
		Page other = new Page().setObjectId("s2").setPageElements(List.of(new PageElement().setObjectId("x")));

		// When:
		Map<String, PaidChartSlot> slots = requests.slots(List.of(slide, other),
				Set.of("pso_chart_placement_donut", "ps_chart_spend"));

		// Then: the deck has no Search spend chart, so only the placement donut is found
		assertThat(slots).containsOnlyKeys("pso_chart_placement_donut");
		PaidChartSlot slot = slots.get("pso_chart_placement_donut");
		assertThat(slot.objectId()).isEqualTo("img");
		assertThat(slot.slideId()).isEqualTo("s1");
		assertThat(slot.aboveIds()).containsExactly("hole_label");
	}

	@Test
	void shouldLinkAnOverviewDonutAsASquareAndLabelItsHoleTest() {
		// Given: the Search overview donut image, 2,043,000 x 1,452,789 EMU drawn, under nothing
		PaidChartSlot slot = new PaidChartSlot("img", "s1",
				new Size().setWidth(emu(22_500)).setHeight(emu(16_000)),
				new AffineTransform().setScaleX(90.8).setScaleY(90.8).setTranslateX(202_125d).setTranslateY(3_540_535d),
				List.of());

		// When:
		List<Request> swap = requests.swap(slot, PaidChartStyle.OVERVIEW_DONUT, "sheet1", 42, "paidch_1", "1,248");

		// Then: delete, link as a square of 90% of the image's height centred on the image, then the hole label
		assertThat(swap.get(0).getDeleteObject().getObjectId()).isEqualTo("img");
		var create = swap.get(1).getCreateSheetsChart();
		assertThat(create.getSpreadsheetId()).isEqualTo("sheet1");
		assertThat(create.getChartId()).isEqualTo(42);
		assertThat(create.getLinkingMode()).isEqualTo("LINKED");
		assertThat(create.getElementProperties().getSize().getWidth().getMagnitude()).isCloseTo(1_307_520d, within(1d));
		assertThat(create.getElementProperties().getSize().getHeight().getMagnitude())
				.isCloseTo(1_307_520d, within(1d));
		assertThat(create.getElementProperties().getTransform().getTranslateX()).isCloseTo(569_865d, within(1d));
		assertThat(create.getElementProperties().getTransform().getTranslateY()).isCloseTo(3_613_175d, within(1d));
		assertThat(swap).noneMatch(r -> r.getUpdatePageElementsZOrder() != null);
		assertThat(swap.get(2).getCreateShape().getObjectId()).isEqualTo("paidch_1_hole");
		assertThat(swap.get(3).getInsertText().getText()).isEqualTo("1,248\nConversions");
	}

	@Test
	void shouldKeepAColumnChartsBoxAndBringLabelsAboveItBackToTheFrontTest() {
		// Given: the spend chart image with a shape drawn above it
		PaidChartSlot slot = new PaidChartSlot("img", "s1",
				new Size().setWidth(emu(60_000)).setHeight(emu(32_500)),
				new AffineTransform().setScaleX(41.91).setScaleY(41.91).setTranslateX(3_060_700d)
						.setTranslateY(3_556_000d),
				List.of("legend"));

		// When:
		List<Request> swap = requests.swap(slot, PaidChartStyle.SPEND, "sheet1", 7, "paidch_2", null);

		// Then: same box as the image, no hole label, the shape above is brought back to the front
		var size = swap.get(1).getCreateSheetsChart().getElementProperties().getSize();
		assertThat(size.getWidth().getMagnitude()).isCloseTo(2_514_600d, within(1d));
		assertThat(size.getHeight().getMagnitude()).isCloseTo(1_362_075d, within(1d));
		assertThat(swap).hasSize(3);
		assertThat(swap.get(2).getUpdatePageElementsZOrder().getPageElementObjectIds()).containsExactly("legend");
		assertThat(swap.get(2).getUpdatePageElementsZOrder().getOperation()).isEqualTo("BRING_TO_FRONT");
	}

	private Dimension emu(double magnitude) {
		return new Dimension().setMagnitude(magnitude).setUnit("EMU");
	}
}
