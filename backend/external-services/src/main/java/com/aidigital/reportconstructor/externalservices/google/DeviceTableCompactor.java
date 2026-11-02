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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Drops the rows of a filled "Device breakdown" slide's "PERFORMANCE BY DEVICE" table that belong to devices
 * the campaign never ran on, and closes the gap they leave.
 *
 * <p>The master slide draws the table as loose elements, not as a Slides table: a {@code DEVICE} header text
 * box, a thin separator rectangle above every row (plus one under the last), and per row a device label and
 * four metric cells. Its four rows (Mobile, Connected TV, Desktop, Tablet) are fixed, so a tactic that ran on
 * three devices used to ship the fourth as a row of dashes. By the time this runs every cell holds its final
 * value, and a device with no sheet row has a dash in every metric cell (a filled device always has an
 * impressions figure), so an all-dash row is exactly an absent device.
 *
 * <p>Rows are recovered from geometry: the separators under the header, in the header's column, bound the
 * rows. Removing row {@code i} deletes its label, its cells and the separator under it; everything further
 * down the table (later rows, the bottom border and the note right under it) moves up by the row's height.
 * A note under the table that names a removed device (the template's "CTR not applicable to Connected TV")
 * is deleted with it.
 */
@Component
@RequiredArgsConstructor
public class DeviceTableCompactor {

	/** The table's device-column header, normalized as {@link SlideBlockLocator#normalizeLabel} does. */
	static final String DEVICE_HEADER = "DEVICE";

	/** The table's CTR column header, normalized; it also names the note under the table that explains CTR. */
	static final String CTR_HEADER = "CTR";

	/** The "no figure" mark the device helper writes into every cell of a device the sheet did not fill. */
	static final String DASH = "—";

	/**
	 * How far, in EMU, an element's left edge may sit from the header's and still count as in the device
	 * column: two points, which absorbs the template's hand-nudged labels (Tablet sits ~1.4pt right).
	 */
	static final double COLUMN_TOLERANCE_EMU = 25_400;

	/**
	 * Tallest shape, in EMU, still read as a row separator rather than a card: the template's separators are
	 * 4,200–14,400 EMU high; anything over ~2.4pt is a real shape.
	 */
	static final double SEPARATOR_MAX_HEIGHT_EMU = 30_000;

	/** Unit of the relative translations this emits. */
	private static final String EMU = "EMU";

	/** Apply mode that adds a translation to an element's current transform. */
	private static final String RELATIVE = "RELATIVE";

	private final SlideBlockLocator locator;

	/**
	 * Builds the requests that tidy a filled device slide's table: absent-device rows are removed and the
	 * rows under them pulled up ({@link #rowRequests}), and a CTR column with no figure in any row is removed
	 * and the columns right of it pulled left ({@link #ctrColumnRequests}). The two plans are merged so an
	 * element both would touch is deleted once and never moved after it is gone.
	 *
	 * @param slide the filled device breakdown slide (needs element ids, sizes, transforms, shape text)
	 * @return the deletes followed by the moves; empty when the table needs no change or is not recognized
	 */
	public List<Request> compactRequests(Page slide) {
		Set<String> deleted = new LinkedHashSet<>();
		List<Request> moves = new ArrayList<>();
		for (List<Request> plan : List.of(rowRequests(slide), ctrColumnRequests(slide))) {
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
	 * Builds the requests that remove the table's CTR column when no device row carries a CTR figure (every
	 * CTR cell is a dash): the {@code CTR} header with the pill behind it, every CTR cell, and the note under
	 * the table that explains CTR ("CTR not applicable to Connected TV"). The columns right of it (VCR, SPEND)
	 * move left by one column pitch so the table closes up.
	 *
	 * @param slide the filled device breakdown slide
	 * @return the delete and move requests, or an empty list when some row has a CTR figure or the table and
	 *         its CTR column cannot be recognized
	 */
	List<Request> ctrColumnRequests(Page slide) {
		if (slide == null || slide.getPageElements() == null) {
			return List.of();
		}
		List<PageElement> elements = slide.getPageElements();
		PageElement deviceHeader = locator.findHeading(slide, List.of(DEVICE_HEADER));
		PageElement ctrHeader = locator.findHeading(slide, List.of(CTR_HEADER));
		ElementBounds deviceBounds = deviceHeader == null ? null : locator.boundsOf(deviceHeader);
		ElementBounds ctrBounds = ctrHeader == null ? null : locator.boundsOf(ctrHeader);
		if (deviceBounds == null || ctrBounds == null) {
			return List.of();
		}
		List<PageElement> separators = separators(elements, deviceBounds);
		if (separators.size() < 2) {
			return List.of();
		}
		double tableBottom = top(separators.getLast());
		double centerX = (ctrBounds.left() + ctrBounds.right()) / 2;
		double centerY = (ctrBounds.top() + ctrBounds.bottom()) / 2;
		Set<String> deleted = new LinkedHashSet<>();
		double columnLeft = Double.MAX_VALUE;
		double columnRight = -Double.MAX_VALUE;
		for (PageElement element : elements) {
			ElementBounds bounds = locator.boundsOf(element);
			String text = locator.textOf(element).trim();
			if (element.getObjectId() == null || bounds == null || text.isEmpty()
					|| bounds.top() <= ctrBounds.bottom() || bounds.top() >= tableBottom
					|| bounds.left() > centerX || bounds.right() < centerX) {
				continue;
			}
			if (!DASH.equals(text)) {
				return List.of();
			}
			deleted.add(element.getObjectId());
			columnLeft = Math.min(columnLeft, bounds.left());
			columnRight = Math.max(columnRight, bounds.right());
		}
		if (deleted.isEmpty()) {
			return List.of();
		}
		deleted.add(ctrHeader.getObjectId());
		double reach = tableBottom + minRowHeight(separators);
		double pitch = Double.MAX_VALUE;
		List<PageElement> rightOfColumn = new ArrayList<>();
		for (PageElement element : elements) {
			ElementBounds bounds = locator.boundsOf(element);
			if (element.getObjectId() == null || bounds == null || deleted.contains(element.getObjectId())) {
				continue;
			}
			boolean blank = locator.textOf(element).isBlank();
			if (blank && bounds.left() <= centerX && bounds.right() >= centerX && bounds.top() <= centerY
					&& bounds.bottom() >= centerY && bounds.bottom() - bounds.top() > SEPARATOR_MAX_HEIGHT_EMU) {
				deleted.add(element.getObjectId());
			} else if (!blank && bounds.top() > tableBottom && bounds.top() < reach && inColumn(element, deviceBounds)
					&& locator.normalizeLabel(locator.textOf(element)).contains(CTR_HEADER)) {
				deleted.add(element.getObjectId());
			} else if (bounds.left() >= columnRight - COLUMN_TOLERANCE_EMU
					&& bounds.top() >= ctrBounds.top() - COLUMN_TOLERANCE_EMU && bounds.top() < tableBottom) {
				rightOfColumn.add(element);
				pitch = Math.min(pitch, bounds.left() - columnLeft);
			}
		}
		List<Request> requests = new ArrayList<>();
		deleted.forEach(id -> requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(id))));
		if (pitch != Double.MAX_VALUE) {
			for (PageElement element : rightOfColumn) {
				requests.add(moveBy(element.getObjectId(), -pitch, 0));
			}
		}
		return requests;
	}

	/**
	 * Builds the requests that remove a device slide's absent-device rows and pull the rest of the table up.
	 *
	 * @param slide the filled device breakdown slide (needs element ids, sizes, transforms, shape text)
	 * @return the delete and move requests, or an empty list when every row is filled, every row is empty
	 *         (an all-dash table is left as the "no data" signal it is), or the table cannot be recognized
	 */
	List<Request> rowRequests(Page slide) {
		if (slide == null || slide.getPageElements() == null) {
			return List.of();
		}
		List<PageElement> elements = slide.getPageElements();
		PageElement header = locator.findHeading(slide, List.of(DEVICE_HEADER));
		ElementBounds headerBounds = header == null ? null : locator.boundsOf(header);
		if (headerBounds == null) {
			return List.of();
		}
		List<PageElement> separators = separators(elements, headerBounds);
		if (separators.size() < 2) {
			return List.of();
		}
		List<Integer> emptyRows = new ArrayList<>();
		Set<String> deleted = new LinkedHashSet<>();
		List<String> removedLabels = new ArrayList<>();
		for (int i = 0; i + 1 < separators.size(); i++) {
			List<PageElement> row = rowMembers(elements, headerBounds, separators.get(i), separators.get(i + 1));
			PageElement label = rowLabel(row, headerBounds);
			if (label != null && allCellsDashed(row, label)) {
				emptyRows.add(i);
				removedLabels.add(locator.normalizeLabel(locator.textOf(label)));
				row.forEach(member -> deleted.add(member.getObjectId()));
			}
		}
		if (emptyRows.isEmpty() || emptyRows.size() == separators.size() - 1) {
			return List.of();
		}
		List<Request> requests = new ArrayList<>();
		PageElement lastSeparator = separators.getLast();
		double reach = top(lastSeparator) + minRowHeight(separators);
		for (PageElement element : elements) {
			if (deleted.contains(element.getObjectId()) || !inColumn(element, headerBounds)) {
				continue;
			}
			double elementTop = top(element);
			if (elementTop > top(lastSeparator) && elementTop < reach
					&& namesRemovedDevice(element, removedLabels)) {
				deleted.add(element.getObjectId());
				continue;
			}
			double shift = shiftFor(elementTop, emptyRows, separators, reach);
			if (shift > 0) {
				requests.add(moveUp(element.getObjectId(), shift));
			}
		}
		List<Request> deletes = new ArrayList<>();
		for (String objectId : deleted) {
			deletes.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(objectId)));
		}
		deletes.addAll(requests);
		return deletes;
	}

	/**
	 * Collects the table's row separators: text-less, separator-thin shapes in the device column below the
	 * header, top to bottom.
	 *
	 * @param elements     the slide's elements
	 * @param headerBounds the {@code DEVICE} header's box
	 * @return the separators sorted by their top edge
	 */
	List<PageElement> separators(List<PageElement> elements, ElementBounds headerBounds) {
		List<PageElement> separators = new ArrayList<>();
		for (PageElement element : elements) {
			ElementBounds bounds = locator.boundsOf(element);
			if (element.getObjectId() == null || element.getShape() == null || bounds == null
					|| !locator.textOf(element).isBlank() || !inColumn(element, headerBounds)
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
	 * Lists one row's elements: everything in the device column whose top lies below the row's own top
	 * separator and no lower than the next separator, which is included (it is deleted with the row).
	 *
	 * @param elements     the slide's elements
	 * @param headerBounds the {@code DEVICE} header's box
	 * @param above        the separator over the row
	 * @param below        the separator under the row
	 * @return the row's elements, in slide order
	 */
	List<PageElement> rowMembers(
			List<PageElement> elements, ElementBounds headerBounds, PageElement above, PageElement below) {
		List<PageElement> members = new ArrayList<>();
		double rowTop = top(above);
		double rowBottom = top(below);
		for (PageElement element : elements) {
			if (element == above || element.getObjectId() == null || !inColumn(element, headerBounds)) {
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
	 * Picks the row's device label: its text element aligned with the {@code DEVICE} header's left edge.
	 *
	 * @param row          the row's elements
	 * @param headerBounds the {@code DEVICE} header's box
	 * @return the label, or {@code null} when the row has none
	 */
	PageElement rowLabel(List<PageElement> row, ElementBounds headerBounds) {
		for (PageElement element : row) {
			ElementBounds bounds = locator.boundsOf(element);
			if (bounds != null && !locator.textOf(element).isBlank()
					&& Math.abs(bounds.left() - headerBounds.left()) <= COLUMN_TOLERANCE_EMU) {
				return element;
			}
		}
		return null;
	}

	/**
	 * Tells whether every metric cell of a row reads as a dash, i.e. the device was not in the sheet.
	 *
	 * @param row   the row's elements
	 * @param label the row's device label, which is not a metric cell
	 * @return {@code true} when the row has at least one metric cell and each is a dash or blank
	 */
	boolean allCellsDashed(List<PageElement> row, PageElement label) {
		int cells = 0;
		for (PageElement element : row) {
			String text = locator.textOf(element).trim();
			if (element == label || text.isEmpty()) {
				continue;
			}
			if (!DASH.equals(text)) {
				return false;
			}
			cells++;
		}
		return cells > 0;
	}

	/**
	 * Tells whether a note under the table names one of the removed devices.
	 *
	 * @param element       the note candidate
	 * @param removedLabels the removed rows' labels, normalized
	 * @return {@code true} when the element's normalized text contains a removed device's label
	 */
	boolean namesRemovedDevice(PageElement element, List<String> removedLabels) {
		String text = locator.normalizeLabel(locator.textOf(element));
		if (text.isEmpty()) {
			return false;
		}
		for (String label : removedLabels) {
			if (!label.isEmpty() && text.contains(label)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Sums the heights of the removed rows that sit above an element, which is how far it has to move up.
	 * Only elements inside the table (down to the note area right under its bottom border) move; anything
	 * further down the device column (the slide's lower cards) stays put.
	 *
	 * @param elementTop the element's top edge
	 * @param emptyRows  indices of the removed rows
	 * @param separators the table's separators, top to bottom
	 * @param reach      the lowest top edge still treated as part of the table
	 * @return the upward shift in EMU, {@code 0} when the element does not move
	 */
	double shiftFor(double elementTop, List<Integer> emptyRows, List<PageElement> separators, double reach) {
		if (elementTop >= reach) {
			return 0;
		}
		double shift = 0;
		for (int row : emptyRows) {
			double rowTop = top(separators.get(row));
			double rowBottom = top(separators.get(row + 1));
			if (elementTop > rowBottom) {
				shift += rowBottom - rowTop;
			}
		}
		return shift;
	}

	/**
	 * Returns the shortest row height, used as how far below the bottom border a note still belongs to
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
	 * Tells whether an element sits in the table's column: its left edge at or right of the header's.
	 *
	 * @param element      the element
	 * @param headerBounds the {@code DEVICE} header's box
	 * @return {@code true} when the element's geometry is known and it starts in the table's column
	 */
	boolean inColumn(PageElement element, ElementBounds headerBounds) {
		ElementBounds bounds = locator.boundsOf(element);
		return bounds != null && bounds.left() >= headerBounds.left() - COLUMN_TOLERANCE_EMU;
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
		return moveBy(objectId, 0, -dy);
	}

	/**
	 * Builds a pure translation, applied on top of the element's current transform.
	 *
	 * @param objectId the element to move
	 * @param dx       horizontal shift in EMU (negative moves left)
	 * @param dy       vertical shift in EMU (negative moves up)
	 * @return the transform update request
	 */
	Request moveBy(String objectId, double dx, double dy) {
		AffineTransform transform = new AffineTransform()
				.setScaleX(1.0).setScaleY(1.0).setShearX(0.0).setShearY(0.0)
				.setTranslateX(dx).setTranslateY(dy).setUnit(EMU);
		return new Request().setUpdatePageElementTransform(new UpdatePageElementTransformRequest()
				.setObjectId(objectId).setApplyMode(RELATIVE).setTransform(transform));
	}
}
