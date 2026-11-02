package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.UpdatePageElementTransformRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Closes the gap an EOM deck leaves on a tactic slide when it drops a block (the EOC "Monthly Impressions
 * Distribution" card). The EOM deck is built from the EOC template, so the slide cannot simply be redrawn in
 * the template without taking the block away from EOC decks too; the gap is closed on the copy instead.
 *
 * <p>Two moves, both found from geometry because element ids differ on every tactic slide:
 * <ul>
 *   <li>the cluster of elements sitting above the "grow" card in its column (the KPI stat tiles) moves into
 *       the dropped block's box, centred in it;</li>
 *   <li>the grow card (Top Delivery Content) takes the column the cluster left: its top edge rises to where
 *       the cluster started while its bottom edge stays put, its heading rises with it, and its single image
 *       grows into the freed height. Its remaining text stays anchored at the bottom of the card.</li>
 * </ul>
 * Anything the slide does not match — a missing heading, a cluster that does not fit the vacated box — makes
 * the slide keep its layout rather than being half-moved.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TacticSlideRelayout {

	/** Unit every transform this pass writes is expressed in. */
	private static final String EMU = "EMU";

	private final SlideBlockLocator locator;

	/**
	 * Builds the transform updates that close the dropped block's gap on one slide.
	 *
	 * @param page          the slide, read before the drop pass deletes anything (ids, sizes, transforms,
	 *                      group children, shape text and images)
	 * @param droppedLabels normalized headings of the blocks the drop pass removes
	 * @param growLabel     normalized heading of the card that grows into the cluster's column
	 * @param anchorLabel   normalized heading of the card whose top edge marks the top of the content row
	 * @return the update requests for this slide, or an empty list when the slide does not have that layout
	 */
	public List<Request> relayoutRequests(
			Page page, List<String> droppedLabels, String growLabel, String anchorLabel) {
		List<Request> requests = new ArrayList<>();
		if (page == null || page.getPageElements() == null || growLabel.isEmpty() || anchorLabel.isEmpty()) {
			return requests;
		}
		List<PageElement> elements = page.getPageElements();
		ElementBounds vacated = cardBounds(page, droppedLabels);
		if (vacated == null) {
			// Not a tactic slide (no dropped block on it): nothing to close, and nothing worth reporting.
			return requests;
		}
		PageElement growHeading = locator.findHeading(page, List.of(growLabel));
		PageElement growCard = growHeading == null ? null : locator.enclosingCard(elements, growHeading);
		ElementBounds anchor = cardBounds(page, List.of(anchorLabel));
		if (growCard == null || anchor == null) {
			log.warn("[slides] relayout skipped slide {}: growHeading={} growCard={} anchorCard={}",
					page.getObjectId(), growHeading == null ? null : growHeading.getObjectId(),
					growCard == null ? null : growCard.getObjectId(), anchor);
			return requests;
		}
		ElementBounds card = locator.boundsOf(growCard);
		List<PageElement> cluster = clusterAbove(elements, card, anchor.top());
		ElementBounds clusterBounds = frameBounds(cluster);
		if (clusterBounds == null || !fits(clusterBounds, vacated)) {
			log.warn("[slides] relayout skipped slide {}: cluster of {} element(s) {} does not fit the vacated box {}"
					+ " (grow card {} at {})", page.getObjectId(), cluster.size(), clusterBounds, vacated,
					growCard.getObjectId(), card);
			return requests;
		}
		double dx = vacated.left() + (width(vacated) - width(clusterBounds)) / 2 - clusterBounds.left();
		double dy = vacated.top() + (height(vacated) - height(clusterBounds)) / 2 - clusterBounds.top();
		for (PageElement element : cluster) {
			requests.add(translate(element.getObjectId(), dx, dy));
		}
		requests.addAll(growRequests(elements, growCard, card, growHeading, anchor.top()));
		return requests;
	}

	/**
	 * Tells whether an element belongs to a region, judged by its centre rather than its whole box: the
	 * template's text boxes are often drawn wider than the card they sit on, so a containment test would lose
	 * them, while their centre is always on the card.
	 *
	 * @param region the region
	 * @param bounds the element's box
	 * @return {@code true} when the element's centre lies inside the region
	 */
	boolean centredIn(ElementBounds region, ElementBounds bounds) {
		double centreX = (bounds.left() + bounds.right()) / 2;
		double centreY = (bounds.top() + bounds.bottom()) / 2;
		return centreX >= region.left() && centreX <= region.right()
				&& centreY >= region.top() && centreY <= region.bottom();
	}

	/**
	 * Resolves the box of the card a matching heading sits on.
	 *
	 * @param page   the slide
	 * @param labels normalized headings to look for
	 * @return the card's box, or {@code null} when no heading matches or it sits on no card
	 */
	ElementBounds cardBounds(Page page, List<String> labels) {
		PageElement heading = locator.findHeading(page, labels);
		PageElement card = heading == null ? null : locator.enclosingCard(page.getPageElements(), heading);
		return card == null ? null : locator.boundsOf(card);
	}

	/**
	 * Collects the elements stacked above the grow card in its column, from the content row's top edge down
	 * to the card's top edge — on the EOC tactic slide, the six KPI stat tiles with their frames and labels.
	 *
	 * @param elements the slide's elements
	 * @param card     the grow card's box
	 * @param rowTop   the content row's top edge (the anchor card's top)
	 * @return the cluster in slide order, empty when nothing sits there
	 */
	List<PageElement> clusterAbove(List<PageElement> elements, ElementBounds card, double rowTop) {
		ElementBounds column = new ElementBounds(card.left(), rowTop, card.right(), card.top());
		List<PageElement> cluster = new ArrayList<>();
		for (PageElement element : elements) {
			ElementBounds bounds = element.getObjectId() == null ? null : locator.boundsOf(element);
			if (bounds != null && centredIn(column, bounds)) {
				cluster.add(element);
			}
		}
		return cluster;
	}

	/**
	 * Builds the grow card's updates: the frame stretched upward to the new top with its bottom kept, the
	 * heading lifted by the same amount, and a single image refitted into the height freed between the heading
	 * and the text that stays at the bottom. More than one image is only lifted, never resized.
	 *
	 * @param elements    the slide's elements
	 * @param growCard    the card's frame element
	 * @param card        the frame's box
	 * @param growHeading the card's heading
	 * @param newTop      the frame's new top edge
	 * @return the updates, or an empty list when the new top is not above the current one
	 */
	List<Request> growRequests(List<PageElement> elements, PageElement growCard, ElementBounds card,
			PageElement growHeading, double newTop) {
		List<Request> requests = new ArrayList<>();
		double shift = newTop - card.top();
		if (shift >= 0) {
			return requests;
		}
		requests.add(stretchTop(growCard, card, newTop));
		requests.add(translate(growHeading.getObjectId(), 0, shift));
		ElementBounds heading = locator.boundsOf(growHeading);
		List<PageElement> images = new ArrayList<>();
		double textTop = card.bottom();
		for (PageElement element : elements) {
			if (element == growCard || element == growHeading || element.getObjectId() == null) {
				continue;
			}
			ElementBounds bounds = locator.boundsOf(element);
			if (bounds == null || !centredIn(card, bounds)) {
				continue;
			}
			if (element.getImage() != null) {
				images.add(element);
			} else {
				textTop = Math.min(textTop, bounds.top());
			}
		}
		if (images.size() != 1 || heading == null) {
			for (PageElement image : images) {
				requests.add(translate(image.getObjectId(), 0, shift));
			}
			return requests;
		}
		double gap = Math.max(0, locator.boundsOf(images.get(0)).top() - heading.bottom());
		requests.add(refitImage(images.get(0), card, heading.bottom() + shift, gap, textTop));
		return requests;
	}

	/**
	 * Refits the card's image between the lifted heading and the text that stays at the bottom, keeping the
	 * image's aspect ratio and its original margins: the gap to the heading above and the inset from the card's
	 * nearer side.
	 *
	 * @param image         the image element
	 * @param card          the card's box (its sides, before the stretch — the stretch only moves the top)
	 * @param headingBottom the heading's bottom edge after the lift
	 * @param gap           the original gap between the heading and the image, kept above and below the image
	 * @param textTop       the top edge of the text kept at the bottom of the card
	 * @return the image's new absolute transform
	 */
	Request refitImage(PageElement image, ElementBounds card, double headingBottom, double gap, double textTop) {
		ElementBounds bounds = locator.boundsOf(image);
		double inset = Math.max(0, Math.min(bounds.left() - card.left(), card.right() - bounds.right()));
		double availableWidth = width(card) - 2 * inset;
		double availableHeight = textTop - headingBottom - 2 * gap;
		double factor = Math.min(availableWidth / width(bounds), availableHeight / height(bounds));
		if (factor <= 0) {
			return translate(image.getObjectId(), 0, headingBottom + gap - bounds.top());
		}
		double newWidth = width(bounds) * factor;
		double newHeight = height(bounds) * factor;
		double newLeft = card.left() + (width(card) - newWidth) / 2;
		double newTop = headingBottom + gap + (availableHeight - newHeight) / 2;
		return placed(image, bounds, newLeft, newTop, factor, factor);
	}

	/**
	 * Stretches an element upward: its top edge moves to the new top while its bottom edge stays.
	 *
	 * @param element the element (a shape or a group)
	 * @param bounds  its current box
	 * @param newTop  the new top edge
	 * @return the element's new absolute transform
	 */
	Request stretchTop(PageElement element, ElementBounds bounds, double newTop) {
		double factor = (bounds.bottom() - newTop) / height(bounds);
		return placed(element, bounds, bounds.left(), newTop, 1, factor);
	}

	/**
	 * Builds an absolute transform that scales an element by the given factors and puts its box's top-left
	 * corner at the given point. Works for groups too: the element's own origin need not be its box's corner,
	 * so the box's offset from the origin is carried through the scale.
	 *
	 * @param element the element
	 * @param bounds  its current box
	 * @param left    the box's new left edge
	 * @param top     the box's new top edge
	 * @param factorX horizontal scale factor applied on top of the current one
	 * @param factorY vertical scale factor applied on top of the current one
	 * @return the update request
	 */
	Request placed(PageElement element, ElementBounds bounds, double left, double top,
			double factorX, double factorY) {
		AffineTransform current = element.getTransform() == null ? new AffineTransform() : element.getTransform();
		double scaleX = valueOr(current.getScaleX(), 1);
		double scaleY = valueOr(current.getScaleY(), 1);
		double originX = valueOr(current.getTranslateX(), 0);
		double originY = valueOr(current.getTranslateY(), 0);
		double localLeft = (bounds.left() - originX) / scaleX;
		double localTop = (bounds.top() - originY) / scaleY;
		double newScaleX = scaleX * factorX;
		double newScaleY = scaleY * factorY;
		AffineTransform transform = new AffineTransform()
				.setScaleX(newScaleX).setScaleY(newScaleY)
				.setShearX(valueOr(current.getShearX(), 0)).setShearY(valueOr(current.getShearY(), 0))
				.setTranslateX(left - newScaleX * localLeft).setTranslateY(top - newScaleY * localTop)
				.setUnit(EMU);
		return new Request().setUpdatePageElementTransform(new UpdatePageElementTransformRequest()
				.setObjectId(element.getObjectId()).setApplyMode("ABSOLUTE").setTransform(transform));
	}

	/**
	 * Builds a pure translation, applied on top of the element's current transform.
	 *
	 * @param objectId the element to move
	 * @param dx       horizontal offset in EMU
	 * @param dy       vertical offset in EMU
	 * @return the update request
	 */
	Request translate(String objectId, double dx, double dy) {
		AffineTransform transform = new AffineTransform()
				.setScaleX(1.0).setScaleY(1.0).setShearX(0.0).setShearY(0.0)
				.setTranslateX(dx).setTranslateY(dy).setUnit(EMU);
		return new Request().setUpdatePageElementTransform(new UpdatePageElementTransformRequest()
				.setObjectId(objectId).setApplyMode("RELATIVE").setTransform(transform));
	}

	/**
	 * Tells whether a box fits inside another's width and height, positions aside.
	 *
	 * @param inner the box to place
	 * @param outer the box to place it in
	 * @return {@code true} when it fits within the containment tolerance
	 */
	boolean fits(ElementBounds inner, ElementBounds outer) {
		double tolerance = SlideBlockLocator.CONTAINMENT_TOLERANCE_EMU;
		return width(inner) <= width(outer) + tolerance && height(inner) <= height(outer) + tolerance;
	}

	/**
	 * Measures a cluster by what it visibly draws: the union of its frames — groups and text-less shapes —
	 * rather than of every element. The template's tile value boxes ("4.10", "106,834") are text boxes drawn
	 * far taller than the tile they sit in (0.81M EMU against a 0.43M EMU frame), so measuring them would
	 * make the six tiles look 1.55M EMU tall and never fit the 1.24M EMU gap they are meant to fill. A cluster
	 * with no frame at all falls back to every element's box.
	 *
	 * @param cluster the cluster's elements
	 * @return the union of the frames' boxes, or of all boxes when there is no frame; {@code null} when none
	 *         has a known geometry
	 */
	ElementBounds frameBounds(List<PageElement> cluster) {
		List<PageElement> frames = cluster.stream()
				.filter(e -> e.getElementGroup() != null
						|| e.getShape() != null && locator.textOf(e).isBlank())
				.toList();
		ElementBounds bounds = union(frames);
		return bounds != null ? bounds : union(cluster);
	}

	/**
	 * Unites the boxes of several elements.
	 *
	 * @param elements the elements
	 * @return the smallest box holding all of them, or {@code null} when none has a known geometry
	 */
	ElementBounds union(List<PageElement> elements) {
		ElementBounds union = null;
		for (PageElement element : elements) {
			ElementBounds b = locator.boundsOf(element);
			if (b == null) {
				continue;
			}
			union = union == null ? b : new ElementBounds(Math.min(union.left(), b.left()),
					Math.min(union.top(), b.top()), Math.max(union.right(), b.right()),
					Math.max(union.bottom(), b.bottom()));
		}
		return union;
	}

	/**
	 * Returns a box's width.
	 *
	 * @param bounds the box
	 * @return its width in EMU
	 */
	double width(ElementBounds bounds) {
		return bounds.right() - bounds.left();
	}

	/**
	 * Returns a box's height.
	 *
	 * @param bounds the box
	 * @return its height in EMU
	 */
	double height(ElementBounds bounds) {
		return bounds.bottom() - bounds.top();
	}

	/**
	 * Unboxes a transform component, falling back when the API left it out.
	 *
	 * @param value    the component (may be {@code null})
	 * @param fallback the value to use when it is missing
	 * @return the component or the fallback
	 */
	double valueOr(Double value, double fallback) {
		return value == null ? fallback : value;
	}
}
