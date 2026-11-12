package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.DeleteObjectRequest;
import com.google.api.services.slides.v1.model.DeleteTableRowRequest;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.TableCell;
import com.google.api.services.slides.v1.model.TableCellLocation;
import com.google.api.services.slides.v1.model.TableRow;
import com.google.api.services.slides.v1.model.TextElement;
import com.google.api.services.slides.v1.model.UpdatePageElementTransformRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Drops the rows of a filled "Creative analysis" slide's creative table that hold no creative.
 *
 * <p>The master has five creative rows, so a tactic that ran fewer creatives shipped the rest as rows of
 * dashes. By the time this runs every cell holds its final value, and an unused row reads as a dash in every
 * cell; the first cell's second line is the tactic name, which the master prints under every creative, so only
 * each cell's first line is read.
 *
 * <p>Two master layouts exist. The older one draws a real Slides table, whose unused rows are deleted with
 * {@code deleteTableRow}. The current one draws the table as loose elements, like the device slide: a
 * {@code CREATIVE} header text box, a thin separator rectangle above every row (plus one under the last), and
 * per row a creative name and its metric cells. There an unused row's elements and the separator under it are
 * deleted, and whatever sits further down the table moves up by the row's height. The slide's right-hand
 * column (key takeaways) lies outside the separators' width and never moves.
 */
@Component
@RequiredArgsConstructor
public class CreativeTableCompactor {

	/** The "no figure" mark the creative helper writes into every cell of an unused row. */
	private static final String DASH = "—";

	/** The loose table's creative-column header, normalized as {@link SlideBlockLocator#normalizeLabel} does. */
	private static final String CREATIVE_HEADER = "CREATIVE";

	/** How far, in EMU, an element's left edge may sit outside the table's span and still belong to it. */
	private static final double COLUMN_TOLERANCE_EMU = 25_400;

	/**
	 * Tallest text-less shape, in EMU, still read as a row separator rather than a card: the template's
	 * separators are 4,200–14,400 EMU high.
	 */
	private static final double SEPARATOR_MAX_HEIGHT_EMU = 30_000;

	/** Unit of the relative translations this emits. */
	private static final String EMU = "EMU";

	/** Apply mode that adds a translation to an element's current transform. */
	private static final String RELATIVE = "RELATIVE";

	/** The table's CTR column header, normalized. */
	private static final String CTR_HEADER = "CTR";

	/** The table's VCR column header, normalized. */
	private static final String VCR_HEADER = "VCR";

	private final SlideBlockLocator locator;

	private final LooseTableColumnRemover columnRemover;

	/**
	 * Builds the requests that drop a creative slide's unused rows: table-row deletions when the master draws
	 * a Slides table, otherwise element deletions and upward moves for the loose-element layout.
	 *
	 * @param slide the filled creative breakdown slide (needs element ids, sizes, transforms and text)
	 * @return the requests, or an empty list when every row is used, every row is unused (an all-dash table
	 *         is left as the "no data" signal it is), or no creative table is recognized
	 */
	List<Request> compactRequests(Page slide) {
		if (slide == null || slide.getPageElements() == null) {
			return new ArrayList<>();
		}
		boolean hasTable = slide.getPageElements().stream()
				.anyMatch(element -> element.getTable() != null && element.getTable().getTableRows() != null);
		return hasTable ? tableRowRequests(slide) : looseRequests(slide);
	}

	/**
	 * Builds the requests that tidy a loose-element creative table: unused rows are removed and the rows under
	 * them pulled up ({@link #looseRowRequests}), and a CTR or VCR column with no figure in any row is removed
	 * and the columns right of it pulled left ({@link LooseTableColumnRemover}). The plans are merged so an
	 * element several would touch is deleted once and never moved after it is gone; the moves are relative,
	 * so a cell shifted by both a row and a column removal ends up shifted by both.
	 *
	 * @param slide the filled creative breakdown slide
	 * @return the deletes followed by the moves; empty when the table needs no change or is not recognized
	 */
	List<Request> looseRequests(Page slide) {
		Set<String> deleted = new LinkedHashSet<>();
		List<Request> moves = new ArrayList<>();
		List<List<Request>> plans = List.of(looseRowRequests(slide),
				looseColumnRequests(slide, CTR_HEADER), looseColumnRequests(slide, VCR_HEADER));
		for (List<Request> plan : plans) {
			for (Request request : plan) {
				if (request.getDeleteObject() != null) {
					deleted.add(request.getDeleteObject().getObjectId());
				} else {
					moves.add(request);
				}
			}
		}
		List<Request> requests = new ArrayList<>();
		deleted.forEach(id -> requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(id))));
		for (Request move : moves) {
			if (!deleted.contains(move.getUpdatePageElementTransform().getObjectId())) {
				requests.add(move);
			}
		}
		return requests;
	}

	/**
	 * Locates the loose creative table (its {@code CREATIVE} header and separators) and asks the column
	 * remover to drop one metric column when no creative has a figure in it.
	 *
	 * @param slide       the filled creative breakdown slide
	 * @param columnLabel the normalized header of the column to check
	 * @return the remover's delete and move requests, or an empty list when the table is not recognized
	 */
	List<Request> looseColumnRequests(Page slide, String columnLabel) {
		PageElement header = locator.findHeading(slide, List.of(CREATIVE_HEADER));
		ElementBounds headerBounds = header == null ? null : locator.boundsOf(header);
		if (headerBounds == null) {
			return new ArrayList<>();
		}
		List<PageElement> separators = separators(slide.getPageElements(), headerBounds);
		if (separators.size() < 2) {
			return new ArrayList<>();
		}
		return columnRemover.columnRequests(
				slide, header, columnLabel, top(separators.getLast()), tableRight(separators));
	}

	/**
	 * Builds the requests that delete a Slides table's unused rows, last row first so each index is still
	 * valid when its request runs.
	 *
	 * @param slide the filled creative breakdown slide
	 * @return the row deletions; empty when every row is used or every row is unused
	 */
	List<Request> tableRowRequests(Page slide) {
		List<Request> requests = new ArrayList<>();
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
	 * Builds the requests that remove a loose-element table's unused rows and pull up what sits below them.
	 * Rows are recovered from geometry: the separators under the {@code CREATIVE} header, aligned with its left
	 * edge, bound the rows, and the widest separator gives the table's right edge.
	 *
	 * @param slide the filled creative breakdown slide
	 * @return the deletes followed by the moves; empty when the table is not recognized, every row is used,
	 *         or every row is unused
	 */
	List<Request> looseRowRequests(Page slide) {
		PageElement header = locator.findHeading(slide, List.of(CREATIVE_HEADER));
		ElementBounds headerBounds = header == null ? null : locator.boundsOf(header);
		if (headerBounds == null) {
			return new ArrayList<>();
		}
		List<PageElement> elements = slide.getPageElements();
		List<PageElement> separators = separators(elements, headerBounds);
		if (separators.size() < 2) {
			return new ArrayList<>();
		}
		double left = headerBounds.left() - COLUMN_TOLERANCE_EMU;
		double right = tableRight(separators);
		List<Integer> unusedRows = new ArrayList<>();
		Set<String> deleted = new LinkedHashSet<>();
		for (int i = 0; i + 1 < separators.size(); i++) {
			List<PageElement> row = rowMembers(elements, left, right, separators.get(i), separators.get(i + 1));
			if (allDashed(row)) {
				unusedRows.add(i);
				row.forEach(member -> deleted.add(member.getObjectId()));
			}
		}
		if (unusedRows.isEmpty() || unusedRows.size() == separators.size() - 1) {
			return new ArrayList<>();
		}
		List<Request> requests = new ArrayList<>();
		deleted.forEach(id -> requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(id))));
		double reach = top(separators.getLast()) + minRowHeight(separators);
		for (PageElement element : elements) {
			if (element.getObjectId() == null || deleted.contains(element.getObjectId())
					|| !inSpan(element, left, right)) {
				continue;
			}
			double shift = shiftFor(top(element), unusedRows, separators, reach);
			if (shift > 0) {
				requests.add(moveUp(element.getObjectId(), shift));
			}
		}
		return requests;
	}

	/**
	 * Collects the loose table's row separators: text-less, separator-thin shapes starting at the header's
	 * left edge and lying below it, top to bottom.
	 *
	 * @param elements     the slide's elements
	 * @param headerBounds the {@code CREATIVE} header's box
	 * @return the separators sorted by their top edge
	 */
	List<PageElement> separators(List<PageElement> elements, ElementBounds headerBounds) {
		List<PageElement> separators = new ArrayList<>();
		for (PageElement element : elements) {
			ElementBounds bounds = locator.boundsOf(element);
			if (element.getObjectId() == null || element.getShape() == null || bounds == null
					|| !locator.textOf(element).isBlank()
					|| Math.abs(bounds.left() - headerBounds.left()) > COLUMN_TOLERANCE_EMU
					|| bounds.top() <= headerBounds.top()
					|| bounds.bottom() - bounds.top() > SEPARATOR_MAX_HEIGHT_EMU) {
				continue;
			}
			separators.add(element);
		}
		separators.sort(Comparator.comparingDouble(this::top));
		return separators;
	}

	/**
	 * Returns the table's right edge: the right edge of its widest separator.
	 *
	 * @param separators the table's separators
	 * @return the right edge in EMU
	 */
	double tableRight(List<PageElement> separators) {
		double right = -Double.MAX_VALUE;
		for (PageElement separator : separators) {
			right = Math.max(right, locator.boundsOf(separator).right());
		}
		return right + COLUMN_TOLERANCE_EMU;
	}

	/**
	 * Lists one row's elements: everything within the table's span whose top lies below the row's own top
	 * separator and no lower than the next separator, which is included (it is deleted with the row).
	 *
	 * @param elements the slide's elements
	 * @param left     the table's left edge, tolerance included
	 * @param right    the table's right edge, tolerance included
	 * @param above    the separator over the row
	 * @param below    the separator under the row
	 * @return the row's elements, in slide order
	 */
	List<PageElement> rowMembers(
			List<PageElement> elements, double left, double right, PageElement above, PageElement below) {
		List<PageElement> members = new ArrayList<>();
		double rowTop = top(above);
		double rowBottom = top(below);
		for (PageElement element : elements) {
			if (element == above || element.getObjectId() == null || !inSpan(element, left, right)) {
				continue;
			}
			double elementTop = top(element);
			if (elementTop > rowTop && elementTop <= rowBottom) {
				members.add(element);
			}
		}
		return members;
	}

	/**
	 * Tells whether a loose row holds no creative: every text element's first line is a dash.
	 *
	 * @param row the row's elements (its separator included, which carries no text)
	 * @return {@code true} when the row has at least one text element and each reads as a dash
	 */
	boolean allDashed(List<PageElement> row) {
		int cells = 0;
		for (PageElement element : row) {
			String text = locator.textOf(element);
			if (text.isBlank()) {
				continue;
			}
			if (!DASH.equals(firstLine(text).trim())) {
				return false;
			}
			cells++;
		}
		return cells > 0;
	}

	/**
	 * Tells whether an element lies within the table's horizontal span.
	 *
	 * @param element the element
	 * @param left    the table's left edge, tolerance included
	 * @param right   the table's right edge, tolerance included
	 * @return {@code true} when the element's geometry is known and its left edge falls inside the span
	 */
	boolean inSpan(PageElement element, double left, double right) {
		ElementBounds bounds = locator.boundsOf(element);
		return bounds != null && bounds.left() >= left && bounds.left() < right;
	}

	/**
	 * Sums the heights of the removed rows that sit above an element, which is how far it has to move up.
	 * Only elements inside the table (down to just under its bottom border) move.
	 *
	 * @param elementTop the element's top edge
	 * @param unusedRows indices of the removed rows
	 * @param separators the table's separators, top to bottom
	 * @param reach      the lowest top edge still treated as part of the table
	 * @return the upward shift in EMU, {@code 0} when the element does not move
	 */
	double shiftFor(double elementTop, List<Integer> unusedRows, List<PageElement> separators, double reach) {
		if (elementTop >= reach) {
			return 0;
		}
		double shift = 0;
		for (int row : unusedRows) {
			double rowTop = top(separators.get(row));
			double rowBottom = top(separators.get(row + 1));
			if (elementTop > rowBottom) {
				shift += rowBottom - rowTop;
			}
		}
		return shift;
	}

	/**
	 * Returns the shortest row height, used as how far below the bottom border an element still belongs to
	 * the table.
	 *
	 * @param separators the table's separators, top to bottom (at least two)
	 * @return the smallest gap between consecutive separators
	 */
	double minRowHeight(List<PageElement> separators) {
		double min = Double.MAX_VALUE;
		for (int i = 0; i + 1 < separators.size(); i++) {
			min = Math.min(min, top(separators.get(i + 1)) - top(separators.get(i)));
		}
		return min;
	}

	/**
	 * Reads an element's top edge.
	 *
	 * @param element the element (its geometry must be known)
	 * @return the top edge in EMU, or {@link Double#MAX_VALUE} when unknown
	 */
	double top(PageElement element) {
		ElementBounds bounds = locator.boundsOf(element);
		return bounds == null ? Double.MAX_VALUE : bounds.top();
	}

	/**
	 * Builds a pure upward translation, applied on top of the element's current transform.
	 *
	 * @param objectId the element to move
	 * @param dy       how far up to move it, in EMU
	 * @return the transform update request
	 */
	Request moveUp(String objectId, double dy) {
		AffineTransform transform = new AffineTransform()
				.setScaleX(1.0).setScaleY(1.0).setShearX(0.0).setShearY(0.0)
				.setTranslateX(0.0).setTranslateY(-dy).setUnit(EMU);
		return new Request().setUpdatePageElementTransform(new UpdatePageElementTransformRequest()
				.setObjectId(objectId).setApplyMode(RELATIVE).setTransform(transform));
	}

	/**
	 * Tells whether a table row holds no creative: the first line of every cell is a dash or blank.
	 *
	 * @param row the table row
	 * @return {@code true} when the row has at least one cell and none carries a value
	 */
	boolean unusedRow(TableRow row) {
		if (row.getTableCells() == null || row.getTableCells().isEmpty()) {
			return false;
		}
		for (TableCell cell : row.getTableCells()) {
			String first = firstLine(cellText(cell)).trim();
			if (!first.isEmpty() && !DASH.equals(first)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Reads a table cell's whole text.
	 *
	 * @param cell the table cell
	 * @return the cell's concatenated text runs, empty when it has none
	 */
	String cellText(TableCell cell) {
		StringBuilder text = new StringBuilder();
		if (cell.getText() != null && cell.getText().getTextElements() != null) {
			for (TextElement te : cell.getText().getTextElements()) {
				if (te.getTextRun() != null && te.getTextRun().getContent() != null) {
					text.append(te.getTextRun().getContent());
				}
			}
		}
		return text.toString();
	}

	/**
	 * Cuts a text down to its first line.
	 *
	 * @param text the text
	 * @return the text before the first line break, or the whole text when it has none
	 */
	String firstLine(String text) {
		int lineEnd = text.indexOf('\n');
		return lineEnd < 0 ? text : text.substring(0, lineEnd);
	}
}
