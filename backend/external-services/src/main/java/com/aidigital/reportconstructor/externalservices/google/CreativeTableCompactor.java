package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.DeleteTableRowRequest;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.TableCell;
import com.google.api.services.slides.v1.model.TableCellLocation;
import com.google.api.services.slides.v1.model.TableRow;
import com.google.api.services.slides.v1.model.TextElement;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Drops the rows of a filled "Creative analysis" slide's creative table that hold no creative.
 *
 * <p>The master draws a fixed five-row Slides table (one creative per row, the headers are separate shapes),
 * so a tactic that ran fewer creatives shipped the rest as rows of dashes. By the time this runs every cell
 * holds its final value, and an unused row reads as a dash in every cell; the first cell's second line is the
 * tactic name, which the master prints under every creative, so only that cell's first line is read.
 */
@Component
public class CreativeTableCompactor {

	/** The "no figure" mark the creative helper writes into every cell of an unused row. */
	private static final String DASH = "—";

	/**
	 * Builds the requests that delete a creative slide's unused table rows, last row first so each index is
	 * still valid when its request runs.
	 *
	 * @param slide the filled creative breakdown slide (needs element ids and table cell text)
	 * @return the row deletions, or an empty list when every row is used, every row is unused (an all-dash
	 *         table is left as the "no data" signal it is), or the slide has no table
	 */
	List<Request> compactRequests(Page slide) {
		List<Request> requests = new ArrayList<>();
		if (slide == null || slide.getPageElements() == null) {
			return requests;
		}
		for (PageElement element : slide.getPageElements()) {
			if (element.getTable() == null || element.getTable().getTableRows() == null) {
				continue;
			}
			List<TableRow> rows = element.getTable().getTableRows();
			List<Integer> unused = new ArrayList<>();
			for (int i = 0; i < rows.size(); i++) {
				if (unusedRow(rows.get(i))) {
					unused.add(i);
				}
			}
			if (unused.isEmpty() || unused.size() == rows.size()) {
				continue;
			}
			for (int i = unused.size() - 1; i >= 0; i--) {
				requests.add(new Request().setDeleteTableRow(new DeleteTableRowRequest()
						.setTableObjectId(element.getObjectId())
						.setCellLocation(new TableCellLocation().setRowIndex(unused.get(i)).setColumnIndex(0))));
			}
		}
		return requests;
	}

	/**
	 * Tells whether a row holds no creative: the first line of every cell is a dash or blank.
	 *
	 * @param row the table row
	 * @return {@code true} when the row has at least one cell and none carries a value
	 */
	boolean unusedRow(TableRow row) {
		if (row.getTableCells() == null || row.getTableCells().isEmpty()) {
			return false;
		}
		for (TableCell cell : row.getTableCells()) {
			String first = firstLine(cell).trim();
			if (!first.isEmpty() && !DASH.equals(first)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Reads a cell's first line of text.
	 *
	 * @param cell the table cell
	 * @return the text before the first line break, empty when the cell has no text
	 */
	String firstLine(TableCell cell) {
		StringBuilder text = new StringBuilder();
		if (cell.getText() != null && cell.getText().getTextElements() != null) {
			for (TextElement te : cell.getText().getTextElements()) {
				if (te.getTextRun() != null && te.getTextRun().getContent() != null) {
					text.append(te.getTextRun().getContent());
				}
			}
		}
		String all = text.toString();
		int lineEnd = all.indexOf('\n');
		return lineEnd < 0 ? all : all.substring(0, lineEnd);
	}
}
