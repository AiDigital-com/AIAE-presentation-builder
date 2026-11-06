package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.enums.PaidChannelKind;
import com.aidigital.reportconstructor.service.reports.enums.PaidTableMarker;
import com.aidigital.reportconstructor.service.reports.model.PaidSlideTable;
import com.aidigital.reportconstructor.service.reports.model.PaidSlidesFill;
import com.google.api.services.slides.v1.model.Group;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.Shape;
import com.google.api.services.slides.v1.model.Table;
import com.google.api.services.slides.v1.model.TableCell;
import com.google.api.services.slides.v1.model.TableCellLocation;
import com.google.api.services.slides.v1.model.TableRow;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import com.google.api.services.slides.v1.model.TextRun;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class PaidSlidesWriterTest {

	private final PaidSlidesWriter writer = new PaidSlidesWriter();

	@Test
	void shouldWriteTheDataRowsAndTotalAndDeleteTheUnusedRowsTest() {
		// Given: the PMax table as the template draws it (5 data rows + TOTAL, no header row), and an export with
		// two placements
		Function<String, TableRow> row = label -> new TableRow().setTableCells(List.of(
				new TableCell().setText(new TextContent().setTextElements(List.of(
						new TextElement().setTextRun(new TextRun().setContent(label + "\n"))))),
				new TableCell().setText(new TextContent().setTextElements(List.of(
						new TextElement().setTextRun(new TextRun().setContent("250,000\n")))))));
		Page slide = new Page().setObjectId("s1").setPageElements(List.of(new PageElement()
				.setObjectId("pmax").setTitle(" ps_pmax_table ")
				.setTable(new Table().setTableRows(List.of(row.apply("SEARCH"), row.apply("SHOPPING"),
						row.apply("YOUTUBE"), row.apply("DISPLAY"), row.apply("OTHER"), row.apply("TOTAL"))))));
		PaidSlideTable table = new PaidSlideTable(PaidTableMarker.SEARCH_PMAX,
				List.of(List.of("SEARCH", "9,000"), List.of("DISPLAY", "1,000")), List.of("TOTAL", "10,000"));

		// When:
		List<Request> requests = writer.requests(List.of(slide), new PaidSlidesFill(List.of(table), Set.of()));

		// Then: row 0's label already reads SEARCH, so only its value changes; row 1 and the TOTAL row are written;
		// data rows 4, 3 and 2 are deleted bottom-up after every write
		List<Request> inserts = requests.stream().filter(r -> r.getInsertText() != null).toList();
		assertThat(inserts).extracting(r -> r.getInsertText().getText())
				.containsExactly("9,000", "DISPLAY", "1,000", "10,000");
		assertThat(inserts.get(1).getInsertText().getCellLocation().getRowIndex()).isEqualTo(1);
		assertThat(inserts.get(3).getInsertText().getCellLocation().getRowIndex()).isEqualTo(5);
		List<Request> rowDeletes = requests.subList(requests.size() - 3, requests.size());
		assertThat(rowDeletes).extracting(r -> r.getDeleteTableRow().getCellLocation().getRowIndex())
				.containsExactly(4, 3, 2);
		assertThat(rowDeletes).extracting(r -> r.getDeleteTableRow().getTableObjectId())
				.containsOnly("pmax");
	}

	@Test
	void shouldReplaceACellsTextKeepingItsFirstCharactersStyleTest() {
		// Given: a cell reading "$2.40" and its new value
		TableCellLocation location = new TableCellLocation().setRowIndex(2).setColumnIndex(4);

		// When:
		List<Request> requests = writer.cellRequests("t1", location, "$2.40\n", "$19.04");

		// Then: the value goes in after the first character, the old tail and then the old first character go
		List<Request> expected = List.of(
				writer.insert("t1", location, 1, "$19.04"),
				writer.delete("t1", location, 7, 11),
				writer.delete("t1", location, 0, 1));
		assertThat(requests).isEqualTo(expected);
	}

	@Test
	void shouldInsertIntoAnEmptyCellAndDashABlankValueTest() {
		// Given: an empty cell and a blank value
		TableCellLocation location = new TableCellLocation().setRowIndex(0).setColumnIndex(0);

		// When:
		List<Request> requests = writer.cellRequests("t1", location, "\n", " ");

		// Then:
		assertThat(requests).containsExactly(writer.insert("t1", location, 0, "—"));
	}

	@Test
	void shouldKeepOneDashedRowForATableWithoutDataTest() {
		// Given: a three-row keyword-style table (no TOTAL) and an export with no keywords
		Function<String, TableRow> row = label -> new TableRow().setTableCells(List.of(
				new TableCell().setText(new TextContent().setTextElements(List.of(
						new TextElement().setTextRun(new TextRun().setContent(label + "\n"))))),
				new TableCell().setText(new TextContent().setTextElements(List.of(
						new TextElement().setTextRun(new TextRun().setContent("x\n")))))));
		Page slide = new Page().setObjectId("s1").setPageElements(List.of(new PageElement()
				.setObjectId("kw").setTitle("ps_kw_table")
				.setTable(new Table().setTableRows(List.of(row.apply("1"), row.apply("2"), row.apply("3"))))));
		PaidSlideTable table = new PaidSlideTable(PaidTableMarker.SEARCH_KEYWORDS, List.of(), null);

		// When:
		List<Request> requests = writer.requests(List.of(slide), new PaidSlidesFill(List.of(table), Set.of()));

		// Then: row 0 is dashed in both cells, rows 2 and 1 are deleted
		assertThat(requests.stream().filter(r -> r.getInsertText() != null))
				.extracting(r -> r.getInsertText().getText()).containsExactly("—", "—");
		assertThat(requests.stream().filter(r -> r.getDeleteTableRow() != null))
				.extracting(r -> r.getDeleteTableRow().getCellLocation().getRowIndex()).containsExactly(2, 1);
	}

	@Test
	void shouldSkipATableTheDeckDoesNotCarryTest() {
		// Given: a deck without the EOM metric table
		Page slide = new Page().setObjectId("s1").setPageElements(List.of());
		PaidSlideTable table = new PaidSlideTable(PaidTableMarker.SEARCH_EOM_METRICS,
				List.of(List.of("Clicks", "—")), null);

		// When:
		List<Request> requests = writer.requests(List.of(slide), new PaidSlidesFill(List.of(table), Set.of()));

		// Then:
		assertThat(requests).isEmpty();
	}

	@Test
	void shouldDeleteEverySlideOfAnAbsentChannelOnlyTest() {
		// Given: a Search divider without tokens (EOM), a Search slide with a raw token inside a group, a Search
		// table slide found by its marker, a Social slide and an unrelated slide
		Function<String, PageElement> shape = text -> new PageElement().setShape(new Shape().setText(
				new TextContent().setTextElements(List.of(new TextElement().setTextRun(
						new TextRun().setContent(text))))));
		Page divider = new Page().setObjectId("divider").setPageElements(List.of(
				shape.apply("PAID SEARCH\n"), shape.apply("CAMPAIGN RESULTS\n"), shape.apply("Observation\n")));
		Page overview = new Page().setObjectId("overview").setPageElements(List.of(new PageElement()
				.setElementGroup(new Group().setChildren(List.of(shape.apply("Spend {{ps_spend}}\n"))))));
		Page tableSlide = new Page().setObjectId("groups").setPageElements(List.of(new PageElement()
				.setTitle("ps_grp_table").setTable(new Table().setTableRows(List.of()))));
		Page social = new Page().setObjectId("social").setPageElements(List.of(shape.apply("{{pso_spend}}\n")));
		Page other = new Page().setObjectId("other").setPageElements(List.of(shape.apply("Paid search plan\n")));
		PaidSlidesFill fill = new PaidSlidesFill(List.of(), Set.of(PaidChannelKind.SEARCH));

		// When:
		List<Request> requests = writer.requests(List.of(divider, overview, tableSlide, social, other), fill);

		// Then:
		assertThat(requests).extracting(r -> r.getDeleteObject().getObjectId())
				.containsExactly("divider", "overview", "groups");
	}
}
