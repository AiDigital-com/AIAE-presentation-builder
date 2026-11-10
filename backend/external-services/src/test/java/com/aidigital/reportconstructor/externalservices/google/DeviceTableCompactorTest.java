package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Dimension;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.Shape;
import com.google.api.services.slides.v1.model.Size;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import com.google.api.services.slides.v1.model.TextRun;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceTableCompactorTest {

	private final DeviceTableCompactor compactor = new DeviceTableCompactor(
			new SlideBlockLocator(), new LooseTableColumnRemover(new SlideBlockLocator()));

	@Test
	void shouldDropTheAllDashConnectedTvRowAndItsNoteAndPullLaterRowsUpTest() {
		// Given: the live device slide of a tactic that ran on Mobile, Desktop and Tablet only
		Page slide = new Page().setPageElements(deviceSlide("294,763", "—", "52,237", "26,118"));

		// When:
		List<Request> requests = compactor.compactRequests(slide);

		// Then: the Connected TV label, its four dash cells, the separator under it and the CTV-only note go
		List<String> deleted = requests.stream().filter(r -> r.getDeleteObject() != null)
				.map(r -> r.getDeleteObject().getObjectId()).toList();
		assertThat(deleted).containsExactlyInAnyOrder(
				"ctv_label", "ctv_imps", "ctv_ctr", "ctv_vcr", "ctv_spend", "sep_desktop", "note");

		// Then: Desktop, Tablet and the bottom border move up by the removed row's height; nothing above
		// the removed row, and nothing outside the table, moves
		Map<String, Double> moves = moves(requests);
		double rowHeight = 2_653_518.75 - 2_370_948.75;
		assertThat(moves).containsOnlyKeys("desktop_label", "desktop_imps", "desktop_ctr", "desktop_vcr",
				"desktop_spend", "sep_tablet", "tablet_label", "tablet_imps", "tablet_ctr", "tablet_vcr",
				"tablet_spend", "sep_bottom");
		assertThat(moves.values()).allSatisfy(dy -> assertThat(dy).isEqualTo(-rowHeight));
	}

	@Test
	void shouldDropTheFirstRowWithoutRemovingTheHeaderUnderlineTest() {
		// Given: a tactic with no Mobile delivery
		Page slide = new Page().setPageElements(deviceSlide("—", "363,555", "17,521", "26,281"));

		// When:
		List<Request> requests = compactor.compactRequests(slide);

		// Then: the Mobile row goes with the separator under it, the header underline stays, and the CTV
		// note stays because Connected TV is still listed
		List<String> deleted = requests.stream().filter(r -> r.getDeleteObject() != null)
				.map(r -> r.getDeleteObject().getObjectId()).toList();
		assertThat(deleted).containsExactlyInAnyOrder(
				"mobile_label", "mobile_imps", "mobile_ctr", "mobile_vcr", "mobile_spend", "sep_ctv");
		assertThat(moves(requests)).containsKeys("ctv_label", "tablet_spend", "sep_bottom", "note")
				.doesNotContainKeys("sep_header", "header", "lower_card");
	}

	@Test
	void shouldLeaveTheTableAloneWhenEveryDeviceIsFilledOrNoneIsTest() {
		// Given: one slide with all four devices filled, one with none
		Page full = new Page().setPageElements(deviceSlide("1", "2", "3", "4"));
		Page empty = new Page().setPageElements(deviceSlide("—", "—", "—", "—"));

		// When:
		List<Request> fullRequests = compactor.compactRequests(full);
		List<Request> emptyRequests = compactor.compactRequests(empty);

		// Then: nothing to drop in the first, and an all-dash table is kept as the "no data" signal
		assertThat(fullRequests).isEmpty();
		assertThat(emptyRequests).isEmpty();
	}

	@Test
	void shouldRemoveTheWholeCtrColumnWhenNoDeviceHasACtrTest() {
		// Given: a video tactic on all four devices, so every CTR cell is a dash, plus the live column headers
		// (the CTR header sits on a pill) and the KPI tile that also says CTR
		List<PageElement> elements = deviceSlide("1", "2", "3", "4");
		elements.add(element("ctr_pill", "ROUND_RECTANGLE", null, 0.0953, 0.0381, 6_943_539.75, 1_920_200.25));
		elements.add(element("ctr_header", "TEXT_BOX", "CTR\n", 0.0953, 0.0308, 6_943_539.75, 1_920_200.25));
		elements.add(element("vcr_header", "TEXT_BOX", "VCR\n", 0.2524, 0.0308, 7_257_856.5, 1_925_772));
		elements.add(element("spend_header", "TEXT_BOX", "SPEND\n", 0.2524, 0.0308, 8_043_647.25, 1_925_772));
		elements.add(element("kpi_tile", "TEXT_BOX", "HIGHEST CTR\n", 0.5953, 0.0359, 442_900.5, 1_021_556.25));

		// When:
		List<Request> requests = compactor.compactRequests(new Page().setPageElements(elements));

		// Then: the header, its pill, the four CTR cells and the CTR note go; the KPI tile stays
		List<String> deleted = requests.stream().filter(r -> r.getDeleteObject() != null)
				.map(r -> r.getDeleteObject().getObjectId()).toList();
		assertThat(deleted).containsExactlyInAnyOrder("ctr_pill", "ctr_header", "mobile_ctr", "ctv_ctr",
				"desktop_ctr", "tablet_ctr", "note");

		// Then: VCR and SPEND, header and cells, move left by one column pitch; nothing else moves
		Map<String, Double> shifts = requests.stream().filter(r -> r.getUpdatePageElementTransform() != null)
				.collect(Collectors.toMap(r -> r.getUpdatePageElementTransform().getObjectId(),
						r -> r.getUpdatePageElementTransform().getTransform().getTranslateX()));
		assertThat(shifts).containsOnlyKeys("vcr_header", "spend_header", "mobile_vcr", "mobile_spend",
				"ctv_vcr", "ctv_spend", "desktop_vcr", "desktop_spend", "tablet_vcr", "tablet_spend");
		assertThat(shifts.values()).allSatisfy(dx -> assertThat(dx).isEqualTo(-(7_257_856.5 - 6_472_065.75)));
	}

	@Test
	void shouldKeepTheCtrColumnWhenOneDeviceHasACtrTest() {
		// Given: the same table, but Mobile served clickable inventory
		List<PageElement> elements = deviceSlide("1", "2", "3", "4");
		elements.add(element("ctr_pill", "ROUND_RECTANGLE", null, 0.0953, 0.0381, 6_943_539.75, 1_920_200.25));
		elements.add(element("ctr_header", "TEXT_BOX", "CTR\n", 0.0953, 0.0308, 6_943_539.75, 1_920_200.25));
		elements.stream().filter(e -> "mobile_ctr".equals(e.getObjectId())).findFirst().orElseThrow()
				.getShape().getText().getTextElements().getFirst().getTextRun().setContent("0.42%\n");

		// When:
		List<Request> requests = compactor.compactRequests(new Page().setPageElements(elements));

		// Then:
		assertThat(requests).isEmpty();
	}

	@Test
	void shouldRemoveTheWholeVcrColumnWhenNoDeviceHasAVcrTest() {
		// Given: a display tactic on all four devices: every row has a CTR, no row has a VCR
		List<PageElement> elements = deviceSlide("1", "2", "3", "4");
		elements.add(element("ctr_pill", "ROUND_RECTANGLE", null, 0.0953, 0.0381, 6_943_539.75, 1_920_200.25));
		elements.add(element("ctr_header", "TEXT_BOX", "CTR\n", 0.0953, 0.0308, 6_943_539.75, 1_920_200.25));
		elements.add(element("vcr_header", "TEXT_BOX", "VCR\n", 0.2524, 0.0308, 7_257_856.5, 1_925_772));
		elements.add(element("spend_header", "TEXT_BOX", "SPEND\n", 0.2524, 0.0308, 8_043_647.25, 1_925_772));
		for (PageElement element : elements) {
			String id = element.getObjectId();
			if (id.endsWith("_ctr") || id.endsWith("_vcr")) {
				element.getShape().getText().getTextElements().getFirst().getTextRun()
						.setContent(id.endsWith("_ctr") ? "0.12%\n" : "—\n");
			}
		}

		// When:
		List<Request> requests = compactor.compactRequests(new Page().setPageElements(elements));

		// Then: the VCR header and the four VCR cells go; the CTR column and its note stay
		List<String> deleted = requests.stream().filter(r -> r.getDeleteObject() != null)
				.map(r -> r.getDeleteObject().getObjectId()).toList();
		assertThat(deleted).containsExactlyInAnyOrder(
				"vcr_header", "mobile_vcr", "ctv_vcr", "desktop_vcr", "tablet_vcr");

		// Then: SPEND, header and cells, moves left into the VCR slot
		Map<String, Double> shifts = requests.stream().filter(r -> r.getUpdatePageElementTransform() != null)
				.collect(Collectors.toMap(r -> r.getUpdatePageElementTransform().getObjectId(),
						r -> r.getUpdatePageElementTransform().getTransform().getTranslateX()));
		assertThat(shifts).containsOnlyKeys(
				"spend_header", "mobile_spend", "ctv_spend", "desktop_spend", "tablet_spend");
		assertThat(shifts.get("spend_header")).isEqualTo(-(8_043_647.25 - 7_257_856.5));
	}

	/**
	 * Rebuilds the live template's device slide geometry (read off a generated deck): the DEVICE header, the
	 * separators, four rows of label + four cells, the CTV note under the table and one lower card.
	 */
	private List<PageElement> deviceSlide(String mobileImps, String ctvImps, String desktopImps, String tabletImps) {
		List<PageElement> elements = new ArrayList<>();
		elements.add(element("header", "TEXT_BOX", "DEVICE\n", 0.2286, 0.0308, 5_000_491.5, 1_925_772));
		elements.add(element("sep_header", "RECTANGLE", null, 1.2764, 0.0048, 5_000_491.5, 2_082_948.77));
		addRow(elements, "mobile", "Mobile", mobileImps, 2_192_517.75, 5_000_491.5);
		elements.add(element("sep_ctv", "RECTANGLE", null, 1.2764, 0.0014, 5_000_491.5, 2_370_948.75));
		addRow(elements, "ctv", "Connected TV", ctvImps, 2_475_088.5, 5_000_491.5);
		elements.add(element("sep_desktop", "RECTANGLE", null, 1.2764, 0.0014, 5_000_491.5, 2_653_518.75));
		addRow(elements, "desktop", "Desktop", desktopImps, 2_730_084.75, 5_000_491.5);
		elements.add(element("sep_tablet", "RECTANGLE", null, 1.2764, 0.0014, 5_000_491.5, 2_915_408.25));
		addRow(elements, "tablet", "Tablet", tabletImps, 2_978_187.75, 5_018_166.75);
		elements.add(element("sep_bottom", "RECTANGLE", null, 1.2764, 0.0014, 5_000_491.5, 3_177_298.5));
		elements.add(element("note", "TEXT_BOX", "CTR not applicable to Connected TV (non-clickable inventory).\n",
				1.2764, 0.0308, 5_000_491.5, 3_202_929.75));
		elements.add(element("lower_card", "ROUND_RECTANGLE", null, 0.9007, 0.2459, 6_131_053.78, 4_265_683.63));
		return elements;
	}

	/** Adds one device row: its label and its four metric cells, each cell dashed when impressions are. */
	private void addRow(List<PageElement> elements, String id, String label, String imps, double y, double labelX) {
		String other = "—".equals(imps) ? "—" : "1.00%";
		elements.add(element(id + "_label", "TEXT_BOX", label + "\n", 0.2286, 0.041, labelX, y));
		elements.add(element(id + "_imps", "TEXT_BOX", imps + "\n\n", 0.2524, 0.0821, 5_686_273.5, y));
		elements.add(element(id + "_ctr", "TEXT_BOX", "—\n", 0.2524, 0.041, 6_472_065.75, y));
		elements.add(element(id + "_vcr", "TEXT_BOX", other + "\n", 0.2524, 0.041, 7_257_856.5, y));
		elements.add(element(id + "_spend", "TEXT_BOX", other + "\n", 0.2524, 0.0821, 8_043_647.25, y));
	}

	/** Builds a 3,000,000 EMU square shape scaled and placed like the template's elements. */
	private PageElement element(
			String id, String shapeType, String text, double scaleX, double scaleY, double x, double y) {
		Shape shape = new Shape().setShapeType(shapeType);
		if (text != null) {
			shape.setText(new TextContent().setTextElements(
					List.of(new TextElement().setTextRun(new TextRun().setContent(text)))));
		}
		return new PageElement().setObjectId(id).setShape(shape)
				.setSize(new Size().setWidth(new Dimension().setMagnitude(3_000_000.0).setUnit("EMU"))
						.setHeight(new Dimension().setMagnitude(3_000_000.0).setUnit("EMU")))
				.setTransform(new AffineTransform().setScaleX(scaleX).setScaleY(scaleY)
						.setTranslateX(x).setTranslateY(y).setUnit("EMU"));
	}

	/** Maps each moved element to the vertical translation applied to it. */
	private Map<String, Double> moves(List<Request> requests) {
		return requests.stream().filter(r -> r.getUpdatePageElementTransform() != null)
				.collect(Collectors.toMap(r -> r.getUpdatePageElementTransform().getObjectId(),
						r -> r.getUpdatePageElementTransform().getTransform().getTranslateY()));
	}
}
