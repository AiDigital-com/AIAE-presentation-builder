package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.DeleteObjectRequest;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.UpdatePageElementTransformRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes a metric column (e.g. {@code CTR} or {@code VCR}) from a breakdown table drawn as loose elements
 * when no row carries a figure in it, and closes the gap by moving the columns right of it left.
 *
 * <p>The breakdown masters draw their tables as text boxes, not Slides tables: a row of header text boxes
 * (some on a pill shape), thin separator rectangles between rows, and per row one text box per column. A
 * column is recognized by its header's horizontal center: every text element below the header and above the
 * table's bottom separator that spans that center is one of the column's cells. When each such cell reads as a
 * dash, the header, its pill and the cells are deleted, and everything right of the column inside the table
 * moves left by one column pitch (the distance from the removed column's left edge to the next column's).
 *
 * <p>A table with no data at all is left alone, so an all-dash table stays as the visible "no data" signal.
 */
@Component
@RequiredArgsConstructor
public class LooseTableColumnRemover {

	/** The "no figure" mark the breakdown helpers write into an unfilled cell. */
	private static final String DASH = "—";

	/** How far, in EMU, edges may disagree and still count as aligned: two points. */
	private static final double TOLERANCE_EMU = 25_400;

	/** Largest vertical offset, in EMU, between a column header and the table's anchor header (~5pt). */
	private static final double HEADER_ROW_TOLERANCE_EMU = 60_000;

	/**
	 * Tallest text-less shape, in EMU, still read as a row separator rather than a pill or card: the
	 * template's separators are 4,200–14,400 EMU high.
	 */
	private static final double SEPARATOR_MAX_HEIGHT_EMU = 30_000;

	/** Unit of the relative translations this emits. */
	private static final String EMU = "EMU";

	/** Apply mode that adds a translation to an element's current transform. */
	private static final String RELATIVE = "RELATIVE";

	private final SlideBlockLocator locator;

	/**
	 * Builds the requests that remove one column of a loose-element table when none of its cells holds a
	 * figure.
	 *
	 * @param slide        the filled breakdown slide (needs element ids, sizes, transforms and text)
	 * @param anchorHeader the table's first-column header (e.g. {@code DEVICE}); fixes the header row and the
	 *                     label column, whose cells never count as data
	 * @param columnLabel  the column's header text, normalized as {@link SlideBlockLocator#normalizeLabel} does
	 * @param tableBottom  top edge of the table's bottom separator; cells lie above it
	 * @param tableRight   right edge of the table; elements further right (other cards) never move
	 * @return the deletes followed by the moves; empty when the column is absent, holds a figure, or the
	 *         table holds no data at all
	 */
	List<Request> columnRequests(
			Page slide, PageElement anchorHeader, String columnLabel, double tableBottom, double tableRight) {
		ElementBounds anchorBounds = anchorHeader == null ? null : locator.boundsOf(anchorHeader);
		if (slide == null || slide.getPageElements() == null || anchorBounds == null) {
			return new ArrayList<>();
		}
		List<PageElement> elements = slide.getPageElements();
		PageElement header = columnHeader(elements, anchorBounds, columnLabel);
		ElementBounds headerBounds = header == null ? null : locator.boundsOf(header);
		if (headerBounds == null || !hasData(elements, anchorBounds, tableBottom, tableRight)) {
			return new ArrayList<>();
		}
		double centerX = (headerBounds.left() + headerBounds.right()) / 2;
		double centerY = (headerBounds.top() + headerBounds.bottom()) / 2;
		Set<String> deleted = new LinkedHashSet<>();
		double columnLeft = headerBounds.left();
		double columnRight = headerBounds.right();
		for (PageElement element : elements) {
			ElementBounds bounds = locator.boundsOf(element);
			String text = locator.textOf(element).trim();
			if (element.getObjectId() == null || bounds == null || text.isEmpty()
					|| bounds.top() <= headerBounds.bottom() || bounds.top() >= tableBottom
					|| bounds.left() > centerX || bounds.right() < centerX) {
				continue;
			}
			if (!DASH.equals(text)) {
				return new ArrayList<>();
			}
			deleted.add(element.getObjectId());
			columnLeft = Math.min(columnLeft, bounds.left());
			columnRight = Math.max(columnRight, bounds.right());
		}
		if (deleted.isEmpty()) {
			return new ArrayList<>();
		}
		deleted.add(header.getObjectId());
		double pitch = Double.MAX_VALUE;
		List<PageElement> rightOfColumn = new ArrayList<>();
		for (PageElement element : elements) {
			ElementBounds bounds = locator.boundsOf(element);
			if (element.getObjectId() == null || bounds == null || deleted.contains(element.getObjectId())) {
				continue;
			}
			if (isPill(element, bounds, centerX, centerY)) {
				deleted.add(element.getObjectId());
			} else if (bounds.left() >= columnRight - TOLERANCE_EMU && bounds.left() < tableRight
					&& bounds.top() >= headerBounds.top() - TOLERANCE_EMU && bounds.top() < tableBottom) {
				rightOfColumn.add(element);
				pitch = Math.min(pitch, bounds.left() - columnLeft);
			}
		}
		List<Request> requests = new ArrayList<>();
		deleted.forEach(id -> requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(id))));
		if (pitch != Double.MAX_VALUE) {
			for (PageElement element : rightOfColumn) {
				requests.add(moveLeft(element.getObjectId(), pitch));
			}
		}
		return requests;
	}

	/**
	 * Finds the column's header: an element whose whole text is the label and which sits on the anchor
	 * header's row. Restricting to that row keeps a stat tile that happens to read the same (e.g. a
	 * {@code CTR} tile) from being mistaken for the column.
	 *
	 * @param elements     the slide's elements
	 * @param anchorBounds the anchor header's box
	 * @param columnLabel  the normalized header text
	 * @return the header, or {@code null} when the table has no such column
	 */
	PageElement columnHeader(List<PageElement> elements, ElementBounds anchorBounds, String columnLabel) {
		for (PageElement element : elements) {
			ElementBounds bounds = locator.boundsOf(element);
			if (element.getObjectId() != null && bounds != null
					&& columnLabel.equals(locator.normalizeLabel(locator.textOf(element)))
					&& Math.abs(bounds.top() - anchorBounds.top()) <= HEADER_ROW_TOLERANCE_EMU
					&& bounds.left() > anchorBounds.left()) {
				return element;
			}
		}
		return null;
	}

	/**
	 * Tells whether the table holds any figure outside its label column: a text element below the header row
	 * and above the bottom separator, right of the label column and inside the table, that is not a dash.
	 *
	 * @param elements     the slide's elements
	 * @param anchorBounds the anchor header's box (its left edge is the label column's)
	 * @param tableBottom  top edge of the table's bottom separator
	 * @param tableRight   right edge of the table
	 * @return {@code true} when at least one cell carries a value
	 */
	boolean hasData(List<PageElement> elements, ElementBounds anchorBounds, double tableBottom, double tableRight) {
		for (PageElement element : elements) {
			ElementBounds bounds = locator.boundsOf(element);
			String text = locator.textOf(element).trim();
			if (bounds == null || text.isEmpty() || bounds.top() <= anchorBounds.bottom()
					|| bounds.top() >= tableBottom || bounds.left() <= anchorBounds.left() + TOLERANCE_EMU
					|| bounds.left() >= tableRight) {
				continue;
			}
			if (!DASH.equals(firstLine(text).trim())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Tells whether an element is the pill drawn behind the column header: a text-less shape, taller than a
	 * separator, that contains the header's center.
	 *
	 * @param element the candidate
	 * @param bounds  its box
	 * @param centerX the header's horizontal center
	 * @param centerY the header's vertical center
	 * @return {@code true} for the header's pill
	 */
	boolean isPill(PageElement element, ElementBounds bounds, double centerX, double centerY) {
		return element.getShape() != null && locator.textOf(element).isBlank()
				&& bounds.left() <= centerX && bounds.right() >= centerX
				&& bounds.top() <= centerY && bounds.bottom() >= centerY
				&& bounds.bottom() - bounds.top() > SEPARATOR_MAX_HEIGHT_EMU;
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

	/**
	 * Builds a pure leftward translation, applied on top of the element's current transform.
	 *
	 * @param objectId the element to move
	 * @param dx       how far left to move it, in EMU
	 * @return the transform update request
	 */
	Request moveLeft(String objectId, double dx) {
		AffineTransform transform = new AffineTransform()
				.setScaleX(1.0).setScaleY(1.0).setShearX(0.0).setShearY(0.0)
				.setTranslateX(-dx).setTranslateY(0.0).setUnit(EMU);
		return new Request().setUpdatePageElementTransform(new UpdatePageElementTransformRequest()
				.setObjectId(objectId).setApplyMode(RELATIVE).setTransform(transform));
	}
}
