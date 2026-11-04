package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.Table;
import com.google.api.services.slides.v1.model.TableCell;
import com.google.api.services.slides.v1.model.TableRow;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import com.google.api.services.slides.v1.model.TextRun;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class CreativeTableCompactorTest {

	private final CreativeTableCompactor compactor = new CreativeTableCompactor();

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
}
