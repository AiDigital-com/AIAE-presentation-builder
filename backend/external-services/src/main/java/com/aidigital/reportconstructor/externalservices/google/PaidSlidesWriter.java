package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.enums.PaidChannelKind;
import com.aidigital.reportconstructor.service.reports.enums.PaidTableMarker;
import com.aidigital.reportconstructor.service.reports.model.PaidSlideTable;
import com.aidigital.reportconstructor.service.reports.model.PaidSlidesFill;
import com.google.api.services.slides.v1.model.DeleteObjectRequest;
import com.google.api.services.slides.v1.model.DeleteTableRowRequest;
import com.google.api.services.slides.v1.model.DeleteTextRequest;
import com.google.api.services.slides.v1.model.InsertTextRequest;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Range;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.TableCell;
import com.google.api.services.slides.v1.model.TableCellLocation;
import com.google.api.services.slides.v1.model.TableRow;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Builds the {@code batchUpdate} requests that finish an MPO deck's Paid Search / Paid Social section: the marker
 * tables' cells, their unused rows, and the slides of a channel without an export.
 *
 * <p>A table is found by its alt-text title (the marker), never by object id, so the same code serves the EOC and
 * the EOM template. The template tables hold no header row (the headers are separate shapes); the data rows are
 * the table's rows from the top, and a table whose slide prints a TOTAL keeps it as its last row.
 *
 * <p>A cell is rewritten the way the template markup was written: the new text goes in after the cell's first
 * character, then the old text around it is deleted, so the value takes the style of the sample's own first run
 * (font, weight, colour) rather than the paragraph default.
 */
@Slf4j
@Component
public class PaidSlidesWriter {

	/** What a cell of a table with no data shows. */
	private static final String DASH = "—";
	/** Opens every placeholder token. */
	private static final String TOKEN_OPEN = "{{";
	/** The line break every Slides text container ends with. */
	private static final String NEWLINE = "\n";
	/** Text-range type for a range given by explicit indices. */
	private static final String FIXED_RANGE = "FIXED_RANGE";
	/** Everything that is not a letter or a digit, collapsed before a divider title is matched. */
	private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");
	/** Joins normalized words. */
	private static final String SPACE = " ";

	/**
	 * Builds every request of the paid pass, table writes first, then the slide deletions.
	 *
	 * @param slides the deck's slides, read with element ids, titles, shape text (groups included) and table text
	 * @param fill   the tables to write and the channels to delete
	 * @return the requests, in the order they must run
	 */
	List<Request> requests(List<Page> slides, PaidSlidesFill fill) {
		List<Request> requests = new ArrayList<>();
		List<Page> pages = slides == null ? List.of() : slides;
		for (PaidSlideTable table : fill.tables()) {
			requests.addAll(tableRequests(pages, table));
		}
		for (PaidChannelKind channel : fill.absentChannels()) {
			requests.addAll(slideDeleteRequests(pages, channel));
		}
		return requests;
	}

	/**
	 * Builds the requests that write one marker table and delete its unused rows.
	 *
	 * @param slides the deck's slides
	 * @param table  the table's values
	 * @return the cell writes, then the row deletions bottom-up; empty when the deck has no such table
	 */
	private List<Request> tableRequests(List<Page> slides, PaidSlideTable table) {
		PageElement element = findTable(slides, table.marker());
		if (element == null) {
			log.warn("[slides] fillPaidSlides: no table titled {} in the deck - its sample values stay",
					table.marker().marker());
			return List.of();
		}
		List<TableRow> rows = element.getTable().getTableRows() == null
				? List.of() : element.getTable().getTableRows();
		int totalSlots = table.hasTotalRow() ? 1 : 0;
		int bodySlots = rows.size() - totalSlots;
		if (bodySlots < 1) {
			log.warn("[slides] fillPaidSlides: table {} has {} row(s), too few to write - skipped",
					table.marker().marker(), rows.size());
			return List.of();
		}
		List<List<String>> body = table.bodyRows().isEmpty()
				? List.of(dashRow(rows.get(0))) : table.bodyRows();
		int kept = Math.min(body.size(), bodySlots);
		String tableId = element.getObjectId();
		List<Request> requests = new ArrayList<>();
		for (int r = 0; r < kept; r++) {
			requests.addAll(rowRequests(tableId, r, rows.get(r), body.get(r)));
		}
		if (table.hasTotalRow()) {
			int totalIndex = rows.size() - 1;
			requests.addAll(rowRequests(tableId, totalIndex, rows.get(totalIndex), table.totalRow()));
		}
		for (int r = bodySlots - 1; r >= kept; r--) {
			requests.add(new Request().setDeleteTableRow(new DeleteTableRowRequest()
					.setTableObjectId(tableId)
					.setCellLocation(new TableCellLocation().setRowIndex(r).setColumnIndex(0))));
		}
		log.info("[slides] fillPaidSlides: table {} writes {} of {} data row(s) and deletes {}",
				table.marker().marker(), kept, bodySlots, bodySlots - kept);
		return requests;
	}

	/**
	 * @param slides the deck's slides
	 * @param marker the table to find
	 * @return the first table whose alt-text title is the marker (trimmed, case-insensitive), or {@code null}
	 */
	PageElement findTable(List<Page> slides, PaidTableMarker marker) {
		for (Page page : slides) {
			for (PageElement element : elements(page)) {
				if (element.getTable() != null && element.getTitle() != null
						&& marker.marker().equalsIgnoreCase(element.getTitle().trim())) {
					return element;
				}
			}
		}
		return null;
	}

	/**
	 * Builds the writes of one table row. A row with more values than cells drops the extra values, and one with
	 * fewer leaves the remaining cells as they are; either is logged, as it means the template's columns moved.
	 *
	 * @param tableId  the table's object id
	 * @param rowIndex the row's index in the table
	 * @param row      the row as read from the deck
	 * @param values   the row's values, in column order
	 * @return the cell writes
	 */
	List<Request> rowRequests(String tableId, int rowIndex, TableRow row, List<String> values) {
		List<TableCell> cells = row.getTableCells() == null ? List.of() : row.getTableCells();
		if (cells.size() != values.size()) {
			log.warn("[slides] fillPaidSlides: table {} row {} has {} cell(s) for {} value(s)",
					tableId, rowIndex, cells.size(), values.size());
		}
		List<Request> requests = new ArrayList<>();
		for (int c = 0; c < Math.min(cells.size(), values.size()); c++) {
			TableCellLocation location = new TableCellLocation().setRowIndex(rowIndex).setColumnIndex(c);
			requests.addAll(cellRequests(tableId, location, cellText(cells.get(c).getText()), values.get(c)));
		}
		return requests;
	}

	/**
	 * Builds the writes that replace one cell's text, keeping the style of its first character.
	 *
	 * @param tableId  the table's object id
	 * @param location the cell
	 * @param current  the cell's text as read, including its closing line break
	 * @param value    the value to write; blank writes a dash
	 * @return the writes; empty when the cell already reads the value
	 */
	List<Request> cellRequests(String tableId, TableCellLocation location, String current, String value) {
		String text = value == null || value.isBlank() ? DASH : value;
		String old = current.endsWith(NEWLINE) ? current.substring(0, current.length() - 1) : current;
		if (old.equals(text)) {
			return List.of();
		}
		List<Request> requests = new ArrayList<>();
		if (old.isEmpty()) {
			requests.add(insert(tableId, location, 0, text));
			return requests;
		}
		requests.add(insert(tableId, location, 1, text));
		if (old.length() > 1) {
			requests.add(delete(tableId, location, 1 + text.length(), old.length() + text.length()));
		}
		requests.add(delete(tableId, location, 0, 1));
		return requests;
	}

	/**
	 * Builds the deletions of every slide of a channel without an export: the slides carrying one of its raw
	 * tokens or one of its table markers, and its divider, which carries neither and is found by its title.
	 *
	 * @param slides  the deck's slides
	 * @param channel the channel to remove
	 * @return one deletion per slide, in deck order
	 */
	private List<Request> slideDeleteRequests(List<Page> slides, PaidChannelKind channel) {
		String tokenStart = TOKEN_OPEN + channel.tokenPrefix();
		String divider = normalize(channel.dividerTitle());
		List<Request> requests = new ArrayList<>();
		for (Page page : slides) {
			StringBuilder text = new StringBuilder();
			boolean marked = false;
			for (PageElement element : elements(page)) {
				marked |= carriesMarker(element, channel);
				appendText(element, text);
			}
			String all = text.toString();
			if (marked || all.contains(tokenStart) || normalize(all).contains(divider)) {
				requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(page.getObjectId())));
			}
		}
		log.info("[slides] fillPaidSlides: {} has no export - deleting its {} slide(s)",
				channel.displayName(), requests.size());
		return requests;
	}

	/**
	 * @param element a page element
	 * @param channel the channel
	 * @return {@code true} when the element's alt-text title is one of the channel's table markers
	 */
	private boolean carriesMarker(PageElement element, PaidChannelKind channel) {
		if (element.getTitle() == null) {
			return false;
		}
		for (PaidTableMarker marker : PaidTableMarker.values()) {
			if (marker.channel() == channel && marker.marker().equalsIgnoreCase(element.getTitle().trim())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @param page a slide
	 * @return its page elements with every group's children (at any depth) in place of the group
	 */
	List<PageElement> elements(Page page) {
		List<PageElement> out = new ArrayList<>();
		if (page.getPageElements() != null) {
			flatten(page.getPageElements(), out);
		}
		return out;
	}

	/**
	 * @param elements page elements, possibly groups
	 * @param out      the accumulator every non-group element is added to
	 */
	private void flatten(List<PageElement> elements, List<PageElement> out) {
		for (PageElement element : elements) {
			if (element.getElementGroup() != null && element.getElementGroup().getChildren() != null) {
				flatten(element.getElementGroup().getChildren(), out);
			} else {
				out.add(element);
			}
		}
	}

	/**
	 * Appends an element's shape text, or every cell's text for a table.
	 *
	 * @param element the element
	 * @param text    the accumulator
	 */
	void appendText(PageElement element, StringBuilder text) {
		if (element.getShape() != null) {
			text.append(cellText(element.getShape().getText())).append(SPACE);
		}
		if (element.getTable() != null && element.getTable().getTableRows() != null) {
			for (TableRow row : element.getTable().getTableRows()) {
				for (TableCell cell : row.getTableCells() == null ? List.<TableCell>of() : row.getTableCells()) {
					text.append(cellText(cell.getText())).append(SPACE);
				}
			}
		}
	}

	/**
	 * @param text a text container (may be {@code null})
	 * @return its runs joined, empty when it has none
	 */
	private String cellText(TextContent text) {
		StringBuilder joined = new StringBuilder();
		if (text != null && text.getTextElements() != null) {
			for (TextElement element : text.getTextElements()) {
				if (element.getTextRun() != null && element.getTextRun().getContent() != null) {
					joined.append(element.getTextRun().getContent());
				}
			}
		}
		return joined.toString();
	}

	/**
	 * @param row a template row
	 * @return a row of dashes as wide as the template row, for a table the export has no data for
	 */
	private List<String> dashRow(TableRow row) {
		int cells = row.getTableCells() == null ? 0 : row.getTableCells().size();
		return Collections.nCopies(cells, DASH);
	}

	/**
	 * @param text any text
	 * @return the text in capitals with every run of non-alphanumerics collapsed to one space, trimmed
	 */
	String normalize(String text) {
		return NON_ALPHANUMERIC.matcher(text.toUpperCase(Locale.ROOT)).replaceAll(SPACE).trim();
	}

	/**
	 * @param tableId  the table's object id
	 * @param location the cell
	 * @param index    the insertion index inside the cell
	 * @param text     the text to insert
	 * @return the request
	 */
	Request insert(String tableId, TableCellLocation location, int index, String text) {
		return new Request().setInsertText(new InsertTextRequest()
				.setObjectId(tableId).setCellLocation(location).setInsertionIndex(index).setText(text));
	}

	/**
	 * @param tableId  the table's object id
	 * @param location the cell
	 * @param start    the first index deleted
	 * @param end      the index after the last one deleted
	 * @return the request
	 */
	Request delete(String tableId, TableCellLocation location, int start, int end) {
		return new Request().setDeleteText(new DeleteTextRequest()
				.setObjectId(tableId).setCellLocation(location)
				.setTextRange(new Range().setType(FIXED_RANGE).setStartIndex(start).setEndIndex(end)));
	}
}
