package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Dimension;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.Shape;
import com.google.api.services.slides.v1.model.Size;
import com.google.api.services.slides.v1.model.Table;
import com.google.api.services.slides.v1.model.TableCell;
import com.google.api.services.slides.v1.model.TableRow;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import com.google.api.services.slides.v1.model.TextRun;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CreativeTableCompactorTest {

	private final CreativeTableCompactor compactor = new CreativeTableCompactor(
			new SlideBlockLocator(), new LooseTableColumnRemover(new SlideBlockLocator()));

	@Test
	void shouldDeleteTheRowsWithoutACreativeLastFirstTest() {
		// Given: a five-row creative table where rows 4 and 5 were left as dashes (the tactic name still
		// prints under each row's dashed creative name)
		Function<List<String>, TableRow> row = cells -> new TableRow().setTableCells(cells.stream()
				.map(text -> new TableCell().setText(new TextContent().setTextElements(List.of(
						new TextElement().setTextRun(new TextRun().setContent(text))))))
				.toList());
		Page slide = new Page().setPageElements(List.of(new PageElement().setObjectId("cr_table")
				.setTable(new Table().setTableRows(List.of(
						row.apply(List.of("Spot_30.mp4\nDOOH\n", "656,078", "—", "$14,437.67")),
						row.apply(List.of("Spot_15.mp4\nDOOH\n", "295,943", "—", "$6,512.53")),
						row.apply(List.of("Spot_10.mp4\nDOOH\n", "194,341", "—", "$4,276.67")),
						row.apply(List.of("—\nDOOH\n", "—", "—", "—")),
						row.apply(List.of("—\nDOOH\n", "—", "—", "—")))))));

		// When:
		List<Request> requests = compactor.compactRequests(slide);

		// Then: the two unused rows go, the bottom one first so the upper index is still valid
		assertThat(requests).hasSize(2);
		assertThat(requests.get(0).getDeleteTableRow().getTableObjectId()).isEqualTo("cr_table");
		assertThat(requests.get(0).getDeleteTableRow().getCellLocation().getRowIndex()).isEqualTo(4);
		assertThat(requests.get(1).getDeleteTableRow().getCellLocation().getRowIndex()).isEqualTo(3);
	}

	@Test
	void shouldKeepATableWhoseRowsAreAllEmptyTest() {
		// Given: a tactic with no creative rows at all
		Function<List<String>, TableRow> row = cells -> new TableRow().setTableCells(cells.stream()
				.map(text -> new TableCell().setText(new TextContent().setTextElements(List.of(
						new TextElement().setTextRun(new TextRun().setContent(text))))))
				.toList());
		Page slide = new Page().setPageElements(List.of(new PageElement().setObjectId("cr_table")
				.setTable(new Table().setTableRows(List.of(
						row.apply(List.of("—\nDOOH\n", "—")), row.apply(List.of("—\nDOOH\n", "—")))))));

		// When:
		List<Request> requests = compactor.compactRequests(slide);

		// Then: the dashed table stays as the visible "no data" signal instead of vanishing
		assertThat(requests).isEmpty();
	}

	@Test
	void shouldDropTheTrailingDashedRowsOfALooseCreativeTableTest() {
		// Given: the live creative slide (loose elements, no Slides table) of a tactic with three creatives
		Page slide = new Page().setPageElements(looseSlide(true, true, true, false, false));

		// When:
		List<Request> requests = compactor.compactRequests(slide);

		// Then: rows 4 and 5 go with the separators under them; the last kept separator becomes the border
		assertThat(deletedIds(requests)).containsExactlyInAnyOrder(
				"r4_name", "r4_imps", "r4_ctr", "r4_vcr", "r4_spend", "sep4",
				"r5_name", "r5_imps", "r5_ctr", "r5_vcr", "r5_spend", "sep5");
		// Then: nothing sits below the removed rows, and the takeaways column is never touched
		assertThat(moves(requests)).isEmpty();
	}

	@Test
	void shouldPullTheRowsBelowARemovedMiddleRowUpTest() {
		// Given: a loose creative table whose second row is unused
		Page slide = new Page().setPageElements(looseSlide(true, false, true, true, true));

		// When:
		List<Request> requests = compactor.compactRequests(slide);

		// Then: row 2 and the separator under it go
		assertThat(deletedIds(requests)).containsExactlyInAnyOrder(
				"r2_name", "r2_imps", "r2_ctr", "r2_vcr", "r2_spend", "sep2");
		// Then: rows 3-5, their separators and the bottom border move up one row; row 1 and the takeaway stay
		Map<String, Double> moves = moves(requests);
		assertThat(moves).containsEntry("r3_name", -ROW_HEIGHT).containsEntry("sep3", -ROW_HEIGHT)
				.containsEntry("r5_spend", -ROW_HEIGHT).containsEntry("sep5", -ROW_HEIGHT);
		assertThat(moves).doesNotContainKeys("r1_name", "sep0", "sep1", "takeaway", "header");
	}

	@Test
	void shouldKeepALooseTableWhoseRowsAreAllEmptyTest() {
		// Given: a loose creative table with no creative at all
		Page slide = new Page().setPageElements(looseSlide(false, false, false, false, false));

		// When:
		List<Request> requests = compactor.compactRequests(slide);

		// Then: the dashed table stays as the visible "no data" signal
		assertThat(requests).isEmpty();
	}

	@Test
	void shouldDropTheEmptyVcrColumnOfADisplayTacticAndPullSpendLeftTest() {
		// Given: a DOOH creative table (CTR filled, VCR all dashes) with the live column headers; the CTR
		// header sits on a pill
		List<PageElement> elements = looseSlide(true, true, true, false, false);
		elements.addAll(columnHeaders());

		// When:
		List<Request> requests = compactor.compactRequests(new Page().setPageElements(elements));

		// Then: the VCR header and every VCR cell go, along with the two unused rows; CTR stays
		assertThat(deletedIds(requests)).contains("vcr_header", "r1_vcr", "r2_vcr", "r3_vcr", "r4_vcr", "r5_vcr",
				"r4_name", "r5_spend", "sep5").doesNotContain("ctr_header", "ctr_pill", "r1_ctr");
		// Then: the SPEND column moves left into the VCR slot; nothing else moves sideways
		Map<String, Double> shifts = shifts(requests);
		assertThat(shifts).containsOnlyKeys("spend_header", "r1_spend", "r2_spend", "r3_spend");
		assertThat(shifts.values()).allSatisfy(dx -> assertThat(dx).isEqualTo(-(4_479_131.0 - 3_643_312.0)));
	}

	@Test
	void shouldDropTheEmptyCtrColumnOfAVideoTacticWithItsPillTest() {
		// Given: a video creative table: every row has a VCR but no CTR
		List<PageElement> elements = looseSlide(true, true, true, true, true);
		elements.addAll(columnHeaders());
		for (PageElement element : elements) {
			String id = element.getObjectId();
			TextRun run = id.endsWith("_ctr") || id.endsWith("_vcr")
					? element.getShape().getText().getTextElements().getFirst().getTextRun() : null;
			if (run != null) {
				run.setContent(id.endsWith("_ctr") ? "—\n" : "96.2%\n");
			}
		}

		// When:
		List<Request> requests = compactor.compactRequests(new Page().setPageElements(elements));

		// Then: the CTR header, its pill and the five CTR cells go
		assertThat(deletedIds(requests)).containsExactlyInAnyOrder(
				"ctr_header", "ctr_pill", "r1_ctr", "r2_ctr", "r3_ctr", "r4_ctr", "r5_ctr");
		// Then: VCR and SPEND, headers and cells, move left by one column pitch; the takeaway never moves
		Map<String, Double> shifts = shifts(requests);
		assertThat(shifts).containsKeys("vcr_header", "spend_header", "r1_vcr", "r5_spend")
				.doesNotContainKeys("takeaway", "r1_imps", "header");
		assertThat(shifts.values()).allSatisfy(dx -> assertThat(dx).isEqualTo(-(3_643_312.0 - 2_807_494.0)));
	}

	@Test
	void shouldKeepTheKpiColumnsOfALooseTableWithNoDataTest() {
		// Given: an all-dash creative table with its column headers
		List<PageElement> elements = looseSlide(false, false, false, false, false);
		elements.addAll(columnHeaders());

		// When:
		List<Request> requests = compactor.compactRequests(new Page().setPageElements(elements));

		// Then: neither column is dropped, so the empty table still reads as "no data"
		assertThat(requests).isEmpty();
	}

	/** Builds the live creative table's metric column headers; the CTR header sits on a pill. */
	private List<PageElement> columnHeaders() {
		return new ArrayList<>(List.of(
				element("imps_header", "IMPRESSIONS\n", 0.2643, 0.0359, 1_971_675, 2_138_362),
				element("ctr_pill", null, 0.1, 0.0429, 3_300_412, 2_131_219),
				element("ctr_header", "CTR\n", 0.1, 0.0359, 3_300_412, 2_144_969),
				element("vcr_header", "VCR\n", 0.2643, 0.0359, 3_643_312, 2_138_362),
				element("spend_header", "SPEND\n", 0.2643, 0.0359, 4_479_131, 2_138_362),
				element("best_ctr_tile", "BEST CTR\n", 0.5953, 0.0359, 2_600_325, 1_257_300)));
	}

	/** Maps each element moved sideways to the horizontal translation applied to it. */
	private Map<String, Double> shifts(List<Request> requests) {
		return requests.stream().filter(r -> r.getUpdatePageElementTransform() != null
						&& r.getUpdatePageElementTransform().getTransform().getTranslateX() != 0)
				.collect(Collectors.toMap(r -> r.getUpdatePageElementTransform().getObjectId(),
						r -> r.getUpdatePageElementTransform().getTransform().getTranslateX()));
	}

	/** Row pitch of the live template's creative table, in EMU. */
	private static final double ROW_HEIGHT = 390_525;

	/** Builds the live creative slide's table: header, six separators, five rows, plus a takeaway on the right. */
	private List<PageElement> looseSlide(boolean... filled) {
		List<PageElement> elements = new ArrayList<>();
		elements.add(element("title", "TOP CREATIVE\n", 0.5953, 0.0359, 6_915_150, 1_257_300));
		elements.add(element("header", "CREATIVE\n", 0.5525, 0.0359, 314_325, 2_138_362));
		elements.add(element("takeaway", "Lead copy\n", 1.0573, 0.0978, 5_734_050, 3_629_025));
		double first = 2_281_238;
		for (int i = 0; i <= filled.length; i++) {
			elements.add(element("sep" + i, null, 1.6669, 0.0014, 314_325, first + i * ROW_HEIGHT));
		}
		for (int i = 0; i < filled.length; i++) {
			String id = "r" + (i + 1);
			double y = first + i * ROW_HEIGHT + 180_974;
			boolean used = filled[i];
			elements.add(element(id + "_name", (used ? "Spot_" + i : "—") + "\nDOOH\n", 0.5525, 0.0761, 314_325, y));
			elements.add(element(id + "_imps", (used ? "135,840" : "—") + "\n", 0.2643, 0.1231, 1_971_675, y));
			elements.add(element(id + "_ctr", (used ? "0.11%" : "—") + "\n", 0.2643, 0.1231, 2_807_494, y));
			elements.add(element(id + "_vcr", "—\n", 0.2643, 0.0821, 3_643_312, y));
			elements.add(element(id + "_spend", (used ? "$1,630.08" : "—") + "\n", 0.2643, 0.1231, 4_479_131, y));
		}
		return elements;
	}

	/** Builds a 3,000,000 EMU square shape scaled and placed like the template's elements. */
	private PageElement element(String id, String text, double scaleX, double scaleY, double x, double y) {
		Shape shape = new Shape().setShapeType(text == null ? "RECTANGLE" : "TEXT_BOX");
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

	/** Lists the ids of the deleted elements. */
	private List<String> deletedIds(List<Request> requests) {
		return requests.stream().filter(r -> r.getDeleteObject() != null)
				.map(r -> r.getDeleteObject().getObjectId()).toList();
	}

	/** Maps each moved element to the vertical translation applied to it. */
	private Map<String, Double> moves(List<Request> requests) {
		return requests.stream().filter(r -> r.getUpdatePageElementTransform() != null)
				.collect(Collectors.toMap(r -> r.getUpdatePageElementTransform().getObjectId(),
						r -> r.getUpdatePageElementTransform().getTransform().getTranslateY()));
	}
}
