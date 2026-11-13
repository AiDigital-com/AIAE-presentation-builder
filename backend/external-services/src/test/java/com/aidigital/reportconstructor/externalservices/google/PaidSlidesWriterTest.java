package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.externalservices.google.deck.PaidDeckChannel;
import com.aidigital.reportconstructor.externalservices.google.deck.PaidDeckTableMarker;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PaidDeckTable;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PaidDeckFill;
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
		PaidDeckTable table = new PaidDeckTable(PaidDeckTableMarker.SEARCH_PMAX,
				List.of(List.of("SEARCH", "9,000"), List.of("DISPLAY", "1,000")), List.of("TOTAL", "10,000"));

		// When:
		List<Request> requests = writer.requests(List.of(slide), new PaidDeckFill(List.of(table), Set.of()));

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
		PaidDeckTable table = new PaidDeckTable(PaidDeckTableMarker.SEARCH_KEYWORDS, List.of(), null);

		// When:
		List<Request> requests = writer.requests(List.of(slide), new PaidDeckFill(List.of(table), Set.of()));

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
		PaidDeckTable table = new PaidDeckTable(PaidDeckTableMarker.SEARCH_EOM_METRICS,
				List.of(List.of("Clicks", "—")), null);

		// When:
		List<Request> requests = writer.requests(List.of(slide), new PaidDeckFill(List.of(table), Set.of()));

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
		PaidDeckFill fill = new PaidDeckFill(List.of(), Set.of(PaidDeckChannel.SEARCH));

		// When:
		List<Request> requests = writer.requests(List.of(divider, overview, tableSlide, social, other), fill);

		// Then:
		assertThat(requests).extracting(r -> r.getDeleteObject().getObjectId())
				.containsExactly("divider", "overview", "groups");
	}

	@Test
	void shouldBuildNothingWhenTheDeckWasReadWithoutSlidesTest() {
		// Given: a fill with a table and an absent channel, against a deck read back with no slide list
		PaidDeckTable table = new PaidDeckTable(PaidDeckTableMarker.SEARCH_CAMPAIGNS,
				List.of(List.of("Brand", "1,000")), null);
		PaidDeckFill fill = new PaidDeckFill(List.of(table), Set.of(PaidDeckChannel.SOCIAL));

		// When:
		List<Request> requests = writer.requests(null, fill);

		// Then:
		assertThat(requests).isEmpty();
	}

	@Test
	void shouldSkipATableWithoutRoomForADataRowTest() {
		// Given: a campaigns table read without rows, and a PMax table holding only its TOTAL row
		TableRow totalOnly = new TableRow().setTableCells(List.of(new TableCell().setText(new TextContent()
				.setTextElements(List.of(new TextElement().setTextRun(new TextRun().setContent("TOTAL\n")))))));
		Page slide = new Page().setObjectId("s1").setPageElements(List.of(
				new PageElement().setObjectId("camp").setTitle("ps_camp_table").setTable(new Table()),
				new PageElement().setObjectId("pmax").setTitle("PS_PMAX_TABLE")
						.setTable(new Table().setTableRows(List.of(totalOnly)))));
		PaidDeckFill fill = new PaidDeckFill(List.of(
				new PaidDeckTable(PaidDeckTableMarker.SEARCH_CAMPAIGNS, List.of(List.of("Brand")), null),
				new PaidDeckTable(PaidDeckTableMarker.SEARCH_PMAX, List.of(List.of("SEARCH")), List.of("TOTAL"))),
				Set.of());

		// When:
		List<Request> requests = writer.requests(List.of(slide), fill);

		// Then: neither table is touched rather than writing a data value over the TOTAL row
		assertThat(requests).isEmpty();
	}

	@Test
	void shouldFindTheTableByItsMarkerOnlyTest() {
		// Given: an untitled table, a shape titled like the marker, and the real marker table
		PageElement untitled = new PageElement().setObjectId("untitled").setTable(new Table());
		PageElement shape = new PageElement().setObjectId("shape").setTitle("ps_kw_table").setShape(new Shape());
		PageElement keywords = new PageElement().setObjectId("kw").setTitle("ps_kw_table").setTable(new Table());
		Page slide = new Page().setObjectId("s1").setPageElements(List.of(untitled, shape, keywords));

		// When:
		PageElement found = writer.findTable(List.of(slide), PaidDeckTableMarker.SEARCH_KEYWORDS);
		PageElement missing = writer.findTable(List.of(slide), PaidDeckTableMarker.SOCIAL_AUDIENCES);

		// Then:
		assertThat(found).isSameAs(keywords);
		assertThat(missing).isNull();
	}

	@Test
	void shouldWriteOnlyTheCellsARowActuallyHasTest() {
		// Given: a one-cell row offered two values, and a row read without cells
		TableRow oneCell = new TableRow().setTableCells(List.of(new TableCell().setText(new TextContent()
				.setTextElements(List.of(new TextElement().setTextRun(new TextRun().setContent("old\n")))))));

		// When:
		List<Request> written = writer.rowRequests("t1", 0, oneCell, List.of("new", "extra"));
		List<Request> none = writer.rowRequests("t1", 1, new TableRow(), List.of("value"));

		// Then: the extra value is dropped and the cell-less row gets no write
		TableCellLocation first = new TableCellLocation().setRowIndex(0).setColumnIndex(0);
		assertThat(written).containsExactly(
				writer.insert("t1", first, 1, "new"),
				writer.delete("t1", first, 4, 6),
				writer.delete("t1", first, 0, 1));
		assertThat(none).isEmpty();
	}

	@Test
	void shouldLeaveACellThatAlreadyReadsTheValueTest() {
		// Given:
		TableCellLocation location = new TableCellLocation().setRowIndex(0).setColumnIndex(1);

		// When-Then: a cell already showing the value, or a dash for a missing value, is left alone — with or
		// without the closing line break
		assertThat(writer.cellRequests("t1", location, "1,000\n", "1,000")).isEmpty();
		assertThat(writer.cellRequests("t1", location, "—", null)).isEmpty();
	}

	@Test
	void shouldReplaceASingleCharacterCellWithoutATailDeletionTest() {
		// Given:
		TableCellLocation location = new TableCellLocation().setRowIndex(3).setColumnIndex(0);

		// When:
		List<Request> requests = writer.cellRequests("t1", location, "x\n", "42");

		// Then: only the old first character is removed after the insert
		assertThat(requests).containsExactly(
				writer.insert("t1", location, 1, "42"),
				writer.delete("t1", location, 0, 1));
	}

	@Test
	void shouldKeepSlidesWhoseElementsCarryNothingOfTheChannelTest() {
		// Given: slides with no element list, a group read without children, an untitled table without rows, a
		// table row without cells, text runs without content, and a marker of the other channel
		Page bare = new Page().setObjectId("bare");
		Page grouped = new Page().setObjectId("grouped").setPageElements(List.of(
				new PageElement().setObjectId("group").setElementGroup(new Group())));
		Page tables = new Page().setObjectId("tables").setPageElements(List.of(
				new PageElement().setTable(new Table()),
				new PageElement().setTable(new Table().setTableRows(List.of(new TableRow())))));
		Page texts = new Page().setObjectId("texts").setPageElements(List.of(
				new PageElement().setShape(new Shape()),
				new PageElement().setShape(new Shape().setText(new TextContent())),
				new PageElement().setShape(new Shape().setText(new TextContent().setTextElements(List.of(
						new TextElement(), new TextElement().setTextRun(new TextRun())))))));
		Page social = new Page().setObjectId("social").setPageElements(List.of(
				new PageElement().setTitle("pso_cr_table").setTable(new Table())));

		// When:
		List<Request> requests = writer.slideDeleteRequests(List.of(bare, grouped, tables, texts, social),
				PaidDeckChannel.SEARCH);

		// Then:
		assertThat(requests).isEmpty();
	}

	@Test
	void shouldDeleteASlideWhoseChildlessGroupCarriesTheChannelMarkerTest() {
		// Given: a group read without its children, titled with a Search table marker
		Page slide = new Page().setObjectId("grp").setPageElements(List.of(
				new PageElement().setTitle(" ps_camp_table ").setElementGroup(new Group())));

		// When:
		List<Request> requests = writer.slideDeleteRequests(List.of(slide), PaidDeckChannel.SEARCH);

		// Then: the group stands in for its children, so its marker still identifies the slide
		assertThat(requests).extracting(r -> r.getDeleteObject().getObjectId()).containsExactly("grp");
	}

	@Test
	void shouldDeleteASlideWhoseUntitledTableCellCarriesAChannelTokenTest() {
		// Given: an untitled table whose second cell still holds a raw Social token, next to a clean table slide
		Function<String, TableCell> cell = text -> new TableCell().setText(new TextContent().setTextElements(
				List.of(new TextElement().setTextRun(new TextRun().setContent(text)))));
		Page tokenSlide = new Page().setObjectId("tokens").setPageElements(List.of(new PageElement()
				.setTable(new Table().setTableRows(List.of(new TableRow().setTableCells(List.of(
						cell.apply("Reach\n"), cell.apply("{{pso_reach}}\n"))))))));
		Page cleanSlide = new Page().setObjectId("clean").setPageElements(List.of(new PageElement()
				.setTable(new Table().setTableRows(List.of(new TableRow().setTableCells(List.of(
						cell.apply("Reach\n"), cell.apply("12,000\n"))))))));

		// When:
		List<Request> requests = writer.slideDeleteRequests(List.of(tokenSlide, cleanSlide), PaidDeckChannel.SOCIAL);

		// Then:
		assertThat(requests).extracting(r -> r.getDeleteObject().getObjectId()).containsExactly("tokens");
	}

	@Test
	void shouldBuildAnEmptyDashRowForARowWithoutCellsTest() {
		// When-Then:
		assertThat(writer.dashRow(new TableRow())).isEmpty();
	}
}
