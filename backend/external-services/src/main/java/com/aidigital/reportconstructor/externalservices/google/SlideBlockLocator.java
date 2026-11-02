package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Dimension;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Size;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds a labelled block on a slide: the card shape a static heading sits on, plus every element placed
 * inside that card. The template draws such a block as loose elements — a text-less background shape, a
 * heading text box and a chart laid over it — with no group tying them together, so the block is recovered
 * from geometry rather than from ids, which differ on every tactic slide.
 */
@Component
public class SlideBlockLocator {

	/** Everything that is not a letter or a digit, stripped before a heading is compared. */
	private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

	/**
	 * How far, in EMU, an element may overhang its card and still count as inside it: one point, enough to
	 * absorb the rounding of a hand-placed chart without reaching a neighbouring block.
	 */
	static final double CONTAINMENT_TOLERANCE_EMU = 12_700;

	/** Shape type of a plain text box, the kind of child a card group carries as an invisible placeholder. */
	private static final String TEXT_BOX = "TEXT_BOX";

	/**
	 * Collects the object ids of every block on the slide whose heading matches one of the labels: the card
	 * under the heading and everything inside the card. A heading that sits on no card is returned alone, so
	 * a template that drops the card still loses the heading rather than keeping it over an empty space.
	 *
	 * @param page             the slide to search (needs element ids, sizes, transforms and shape text)
	 * @param normalizedLabels the headings to look for, already passed through {@link #normalizeLabel}
	 * @return the matching elements' object ids in slide order, empty when no heading matched
	 */
	public List<String> blockObjectIds(Page page, List<String> normalizedLabels) {
		Set<String> ids = new LinkedHashSet<>();
		if (page == null || page.getPageElements() == null || normalizedLabels == null
				|| normalizedLabels.isEmpty()) {
			return new ArrayList<>(ids);
		}
		List<PageElement> elements = page.getPageElements();
		for (PageElement heading : elements) {
			if (heading.getObjectId() == null || !normalizedLabels.contains(normalizeLabel(textOf(heading)))) {
				continue;
			}
			PageElement card = enclosingCard(elements, heading);
			if (card == null) {
				ids.add(heading.getObjectId());
				continue;
			}
			ids.addAll(elementsInside(elements, card));
		}
		return new ArrayList<>(ids);
	}

	/**
	 * Finds the first element whose whole text matches one of the labels.
	 *
	 * @param page             the slide to search
	 * @param normalizedLabels the headings to look for, already passed through {@link #normalizeLabel}
	 * @return the first matching heading in slide order, or {@code null} when none matches
	 */
	public PageElement findHeading(Page page, List<String> normalizedLabels) {
		if (page == null || page.getPageElements() == null || normalizedLabels == null) {
			return null;
		}
		for (PageElement element : page.getPageElements()) {
			if (element.getObjectId() != null && normalizedLabels.contains(normalizeLabel(textOf(element)))) {
				return element;
			}
		}
		return null;
	}

	/**
	 * Picks the card a heading sits on: the smallest text-less shape, or element group drawn as a card, whose
	 * box contains the heading's box. Groups qualify because some of the template's cards are a frame shape
	 * grouped with an empty text box rather than a loose shape.
	 *
	 * @param elements the slide's elements
	 * @param heading  the matched heading
	 * @return the enclosing card, or {@code null} when the heading sits on none (or its geometry is unknown)
	 */
	public PageElement enclosingCard(List<PageElement> elements, PageElement heading) {
		ElementBounds headingBounds = boundsOf(heading);
		if (headingBounds == null) {
			return null;
		}
		PageElement best = null;
		double bestArea = Double.MAX_VALUE;
		for (PageElement candidate : elements) {
			boolean card = candidate.getElementGroup() != null
					|| candidate.getShape() != null && textOf(candidate).isBlank();
			if (candidate == heading || !card) {
				continue;
			}
			ElementBounds bounds = boundsOf(candidate);
			if (bounds != null && bounds.contains(headingBounds, CONTAINMENT_TOLERANCE_EMU)
					&& bounds.area() < bestArea) {
				best = candidate;
				bestArea = bounds.area();
			}
		}
		return best;
	}

	/**
	 * Lists the card and every element whose box lies inside it, in slide order.
	 *
	 * @param elements the slide's elements
	 * @param card     the block's card
	 * @return the object ids of the card and its contents
	 */
	List<String> elementsInside(List<PageElement> elements, PageElement card) {
		List<String> ids = new ArrayList<>();
		ElementBounds cardBounds = boundsOf(card);
		for (PageElement element : elements) {
			if (element.getObjectId() == null) {
				continue;
			}
			ElementBounds bounds = element == card ? cardBounds : boundsOf(element);
			if (bounds != null && cardBounds.contains(bounds, CONTAINMENT_TOLERANCE_EMU)) {
				ids.add(element.getObjectId());
			}
		}
		return ids;
	}

	/**
	 * Resolves an element's on-slide box from its size and transform. Shear is ignored: the template's cards,
	 * headings and charts are never sheared, and a rotated element is not one this lookup needs to place.
	 * A group reports no size of its own, so its box is the union of its children's, mapped through the
	 * group's transform.
	 *
	 * @param element the element to measure
	 * @return its box, or {@code null} when its geometry is unknown
	 */
	public ElementBounds boundsOf(PageElement element) {
		if (element.getElementGroup() != null) {
			return groupBoundsOf(element);
		}
		Size size = element.getSize();
		if (size == null || size.getWidth() == null || size.getHeight() == null) {
			return null;
		}
		AffineTransform transform = element.getTransform();
		double scaleX = transform == null || transform.getScaleX() == null ? 1 : transform.getScaleX();
		double scaleY = transform == null || transform.getScaleY() == null ? 1 : transform.getScaleY();
		double x = transform == null || transform.getTranslateX() == null ? 0 : transform.getTranslateX();
		double y = transform == null || transform.getTranslateY() == null ? 0 : transform.getTranslateY();
		double width = magnitude(size.getWidth()) * scaleX;
		double height = magnitude(size.getHeight()) * scaleY;
		return new ElementBounds(Math.min(x, x + width), Math.min(y, y + height),
				Math.max(x, x + width), Math.max(y, y + height));
	}

	/**
	 * Measures a group: the union of its visible children's boxes, each mapped from the group's own coordinate
	 * space onto the slide through the group's transform (the Slides API reports a child's transform relative
	 * to its group, not to the page). The template's card groups pair a frame with an empty text box nudged
	 * slightly outside it; that box draws nothing, so it is left out and the group measures as its frame. A
	 * group of nothing but empty text boxes falls back to all of them.
	 *
	 * @param group the group element
	 * @return the group's on-slide box, or {@code null} when no child has a known geometry
	 */
	ElementBounds groupBoundsOf(PageElement group) {
		List<PageElement> children = group.getElementGroup().getChildren();
		if (children == null || children.isEmpty()) {
			return null;
		}
		List<PageElement> visible = children.stream().filter(child -> !emptyTextBox(child)).toList();
		ElementBounds bounds = unionMapped(group, visible);
		return bounds != null ? bounds : unionMapped(group, children);
	}

	/**
	 * Tells whether an element is a text box with no text — an invisible placeholder inside a card group.
	 *
	 * @param element the element
	 * @return {@code true} for a {@code TEXT_BOX} shape whose text is blank
	 */
	boolean emptyTextBox(PageElement element) {
		return element.getShape() != null && TEXT_BOX.equals(element.getShape().getShapeType())
				&& textOf(element).isBlank();
	}

	/**
	 * Unites the boxes of a group's children, mapped onto the slide through the group's transform.
	 *
	 * @param group    the group element
	 * @param children the children to measure
	 * @return the union on the slide, or {@code null} when none of them has a known geometry
	 */
	ElementBounds unionMapped(PageElement group, List<PageElement> children) {
		AffineTransform transform = group.getTransform();
		double scaleX = transform == null || transform.getScaleX() == null ? 1 : transform.getScaleX();
		double scaleY = transform == null || transform.getScaleY() == null ? 1 : transform.getScaleY();
		double x = transform == null || transform.getTranslateX() == null ? 0 : transform.getTranslateX();
		double y = transform == null || transform.getTranslateY() == null ? 0 : transform.getTranslateY();
		ElementBounds union = null;
		for (PageElement child : children) {
			ElementBounds local = boundsOf(child);
			if (local == null) {
				continue;
			}
			double left = x + scaleX * local.left();
			double right = x + scaleX * local.right();
			double top = y + scaleY * local.top();
			double bottom = y + scaleY * local.bottom();
			ElementBounds mapped = new ElementBounds(Math.min(left, right), Math.min(top, bottom),
					Math.max(left, right), Math.max(top, bottom));
			union = union == null ? mapped : new ElementBounds(Math.min(union.left(), mapped.left()),
					Math.min(union.top(), mapped.top()), Math.max(union.right(), mapped.right()),
					Math.max(union.bottom(), mapped.bottom()));
		}
		return union;
	}

	/**
	 * Reads a dimension's magnitude, treating a missing one as zero.
	 *
	 * @param dimension the dimension
	 * @return its magnitude in its own unit (EMU for every element the API reports)
	 */
	double magnitude(Dimension dimension) {
		return dimension.getMagnitude() == null ? 0 : dimension.getMagnitude();
	}

	/**
	 * Concatenates a shape's text runs; any non-shape element reads as having no text.
	 *
	 * @param element the element to read
	 * @return the element's text, or an empty string
	 */
	String textOf(PageElement element) {
		if (element.getShape() == null) {
			return "";
		}
		TextContent text = element.getShape().getText();
		if (text == null || text.getTextElements() == null) {
			return "";
		}
		StringBuilder joined = new StringBuilder();
		for (TextElement run : text.getTextElements()) {
			if (run.getTextRun() != null && run.getTextRun().getContent() != null) {
				joined.append(run.getTextRun().getContent());
			}
		}
		return joined.toString();
	}

	/**
	 * Reduces a heading to its upper-case letters and digits, so casing, spacing, line breaks and punctuation
	 * cannot break the match between the configured heading and the template's.
	 *
	 * @param value the raw heading (may be {@code null})
	 * @return the normalized heading, or an empty string when there is nothing to match
	 */
	public String normalizeLabel(String value) {
		if (value == null) {
			return "";
		}
		return NON_ALPHANUMERIC.matcher(value).replaceAll("").toUpperCase(Locale.ROOT);
	}
}
