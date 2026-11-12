package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.externalservices.google.deck.DeckMarkers;
import com.google.api.services.slides.v1.model.DeleteObjectRequest;
import com.google.api.services.slides.v1.model.DeleteTextRequest;
import com.google.api.services.slides.v1.model.OpaqueColor;
import com.google.api.services.slides.v1.model.OptionalColor;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Range;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.RgbColor;
import com.google.api.services.slides.v1.model.ShapeBackgroundFill;
import com.google.api.services.slides.v1.model.SolidFill;
import com.google.api.services.slides.v1.model.TextElement;
import com.google.api.services.slides.v1.model.TextStyle;
import com.google.api.services.slides.v1.model.UpdateTextStyleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Applies the markup placeholder values carry to a finished deck: {@code **key phrase**} markers become real
 * bold text, and a shape whose whole text is {@link DeckMarkers#HIDE_SHAPE} is removed.
 *
 * <p>{@code replaceAllText} can only carry plain text, and the replaced text takes the style of the token it
 * replaced — so a thought written into a bold placeholder came out bold end to end, and one written into a
 * regular placeholder had no emphasis at all. The copy that should carry one bold phrase is written with the
 * phrase wrapped in {@code **}; this pass reads the deck back and, for every shape carrying a marker, resets
 * the shape's text to regular, bolds each marked phrase and deletes the markers.
 *
 * <p>A marker left without its pair — the closing one cut off by a length cap — is deleted and nothing is
 * bolded for it, so a stray {@code **} never reaches the client.
 *
 * <p>A takeaway that opens with a {@code [[lead phrase]]} (the breakdown slides' bullets) gets the design's
 * lead styling instead: the shape's text is reset to regular body colour, the phrase is bold brand blue, and
 * the brackets are removed. An unpaired bracket is removed without styling anything. A takeaway drawn on a
 * dark filled panel (the Geo slide's blue "WHAT THE MAP TELLS US" card) gets the on-dark variant instead —
 * a white lead phrase over light body text — so the brand-blue lead never lands on a blue panel.
 *
 * <p>An optional chip (an audience segment the campaign does not have) is written as
 * {@link DeckMarkers#HIDE_SHAPE}; its shape is deleted so the slide shows no empty pill.
 */
@Component
@RequiredArgsConstructor
public class DeckMarkupRequestBuilder {

	/** The marker the copy wraps a bold phrase in. */
	private static final String MARKER = "**";

	/** Opens a lead phrase. */
	private static final String LEAD_OPEN = DeckMarkers.LEAD_OPEN;

	/** Closes a lead phrase. */
	private static final String LEAD_CLOSE = DeckMarkers.LEAD_CLOSE;

	/** Brand blue of a lead phrase (the design's #0120F1). */
	static final RgbColor LEAD_COLOR = new RgbColor().setRed(0.003921569f).setGreen(0.1254902f).setBlue(0.94509804f);

	/** Body text colour the rest of a lead-styled takeaway is reset to (the design's #2A2D38). */
	static final RgbColor BODY_COLOR = new RgbColor().setRed(0.16470589f).setGreen(0.1764706f).setBlue(0.21960784f);

	/** Lead phrase colour on a dark panel (white). */
	static final RgbColor ON_DARK_LEAD_COLOR = new RgbColor().setRed(1f).setGreen(1f).setBlue(1f);

	/** Body text colour on a dark panel (the design's light #E6E8FF, as on the recommendation box). */
	static final RgbColor ON_DARK_BODY_COLOR = new RgbColor().setRed(0.9019608f).setGreen(0.9098039f).setBlue(1f);

	/** Perceived brightness (0-1) below which a backdrop counts as dark. */
	private static final double DARK_BACKDROP_BRIGHTNESS = 0.5;

	/** Fill opacity below which a fill is too faint to count as a backdrop. */
	private static final double MIN_BACKDROP_ALPHA = 0.5;

	/** Red weight of the perceived-brightness formula (ITU-R BT.601). */
	private static final double RED_WEIGHT = 0.299;

	/** Green weight of the perceived-brightness formula (ITU-R BT.601). */
	private static final double GREEN_WEIGHT = 0.587;

	/** Blue weight of the perceived-brightness formula (ITU-R BT.601). */
	private static final double BLUE_WEIGHT = 0.114;

	/** Property state of a fill the template defines but does not draw. */
	private static final String NOT_RENDERED = "NOT_RENDERED";

	/** Resolves an element's on-slide box from its size and transform. */
	private final SlideBlockLocator locator;

	/** Field mask of the lead style updates: weight and colour. */
	private static final String LEAD_FIELDS = "bold,foregroundColor";

	/** Range type for a fixed start/end range. */
	private static final String FIXED_RANGE = "FIXED_RANGE";

	/** Range type covering a shape's whole text. */
	private static final String ALL_RANGE = "ALL";

	/** Field mask of the style updates: only the weight changes. */
	private static final String BOLD_FIELD = "bold";

	/**
	 * Builds the requests that apply every bold marker in the given slides, in an order that keeps each
	 * request's indices valid against the text as the requests before it left it.
	 *
	 * @param slides the deck's slides, read with each shape's text runs and their start indices
	 * @return the requests, empty when no shape carries a marker
	 */
	List<Request> deckMarkupRequests(List<Page> slides) {
		List<Request> requests = new ArrayList<>();
		if (slides == null) {
			return requests;
		}
		for (Page page : slides) {
			if (page.getPageElements() == null) {
				continue;
			}
			for (PageElement element : page.getPageElements()) {
				requests.addAll(shapeRequests(element, page.getPageElements()));
			}
		}
		return requests;
	}

	/**
	 * Builds one shape's requests: reset its text to regular, then work through its markers from the last to
	 * the first, so every deletion only shifts text that has already been handled.
	 *
	 * @param element  a page element, possibly not a shape or without text
	 * @param siblings every element on the same slide, searched for the panel a lead takeaway sits on
	 * @return the shape's requests, empty when it carries no marker
	 */
	List<Request> shapeRequests(PageElement element, List<PageElement> siblings) {
		if (element.getShape() == null || element.getShape().getText() == null) {
			return List.of();
		}
		String text = shapeText(element.getShape().getText().getTextElements());
		if (DeckMarkers.HIDE_SHAPE.equals(text.strip())) {
			return List.of(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(element.getObjectId())));
		}
		if (text.contains(LEAD_OPEN) || text.contains(LEAD_CLOSE)) {
			return leadRequests(element.getObjectId(), text, onDarkBackdrop(element, siblings));
		}
		List<Integer> markers = markerIndices(element.getShape().getText().getTextElements());
		if (markers.isEmpty()) {
			return List.of();
		}
		String objectId = element.getObjectId();
		List<Request> requests = new ArrayList<>();
		requests.add(styleRequest(objectId, new Range().setType(ALL_RANGE), false));
		int last = markers.size() - 1;
		if (markers.size() % 2 == 1) {
			requests.add(deleteMarker(objectId, markers.get(last)));
			last--;
		}
		for (int close = last; close > 0; close -= 2) {
			int closeIndex = markers.get(close);
			int openIndex = markers.get(close - 1);
			int phraseStart = openIndex + MARKER.length();
			if (closeIndex > phraseStart) {
				requests.add(styleRequest(objectId, new Range().setType(FIXED_RANGE)
						.setStartIndex(phraseStart).setEndIndex(closeIndex), true));
			}
			requests.add(deleteMarker(objectId, closeIndex));
			requests.add(deleteMarker(objectId, openIndex));
		}
		return requests;
	}

	/**
	 * Builds one lead-styled shape's requests: reset the whole text to regular body colour, style the first
	 * {@code [[…]]} phrase bold brand blue, then delete every lead marker from the last to the first so each
	 * deletion only shifts text after the ones still to come. A shape's text indices are its joined runs'
	 * offsets, so {@code text} positions are the API's absolute indices.
	 *
	 * @param objectId the shape
	 * @param text     the shape's joined text
	 * @param onDark   whether the shape sits on a dark panel, which swaps in the light palette
	 * @return the shape's requests
	 */
	List<Request> leadRequests(String objectId, String text, boolean onDark) {
		RgbColor body = onDark ? ON_DARK_BODY_COLOR : BODY_COLOR;
		RgbColor lead = onDark ? ON_DARK_LEAD_COLOR : LEAD_COLOR;
		List<Request> requests = new ArrayList<>();
		requests.add(leadStyleRequest(objectId, new Range().setType(ALL_RANGE), false, body));
		int open = text.indexOf(LEAD_OPEN);
		int close = open < 0 ? -1 : text.indexOf(LEAD_CLOSE, open + LEAD_OPEN.length());
		if (close > open + LEAD_OPEN.length()) {
			requests.add(leadStyleRequest(objectId, new Range().setType(FIXED_RANGE)
					.setStartIndex(open + LEAD_OPEN.length()).setEndIndex(close), true, lead));
		}
		List<Integer> markers = new ArrayList<>();
		markers.addAll(occurrences(text, LEAD_OPEN));
		markers.addAll(occurrences(text, LEAD_CLOSE));
		markers.sort(Comparator.reverseOrder());
		for (int at : markers) {
			requests.add(deleteRange(objectId, at, LEAD_OPEN.length()));
		}
		return requests;
	}

	/**
	 * Tells whether a text shape is drawn over a dark fill: its own rendered fill, or else the fill of the
	 * tightest filled shape on the slide whose box encloses it. Unknown geometry or a theme-coloured fill
	 * counts as light, which keeps the default brand-blue lead styling.
	 *
	 * @param element  the text shape
	 * @param siblings every element on the slide
	 * @return {@code true} when the backdrop is dark
	 */
	boolean onDarkBackdrop(PageElement element, List<PageElement> siblings) {
		RgbColor own = renderedFill(element);
		if (own != null) {
			return brightness(own) < DARK_BACKDROP_BRIGHTNESS;
		}
		ElementBounds bounds = locator.boundsOf(element);
		if (bounds == null || siblings == null) {
			return false;
		}
		RgbColor backdrop = null;
		double bestArea = Double.MAX_VALUE;
		for (PageElement candidate : siblings) {
			RgbColor fill = candidate == element ? null : renderedFill(candidate);
			ElementBounds candidateBounds = fill == null ? null : locator.boundsOf(candidate);
			if (candidateBounds != null && candidateBounds.area() < bestArea
					&& candidateBounds.contains(bounds, SlideBlockLocator.CONTAINMENT_TOLERANCE_EMU)) {
				backdrop = fill;
				bestArea = candidateBounds.area();
			}
		}
		return backdrop != null && brightness(backdrop) < DARK_BACKDROP_BRIGHTNESS;
	}

	/**
	 * Reads a shape's drawn solid fill colour.
	 *
	 * @param element a page element
	 * @return the fill's RGB colour, or {@code null} when it has no rendered, mostly opaque solid RGB fill
	 */
	RgbColor renderedFill(PageElement element) {
		if (element.getShape() == null || element.getShape().getShapeProperties() == null) {
			return null;
		}
		ShapeBackgroundFill fill = element.getShape().getShapeProperties().getShapeBackgroundFill();
		SolidFill solid = fill == null ? null : fill.getSolidFill();
		if (solid == null || solid.getColor() == null || NOT_RENDERED.equals(fill.getPropertyState())
				|| solid.getAlpha() != null && solid.getAlpha() < MIN_BACKDROP_ALPHA) {
			return null;
		}
		return solid.getColor().getRgbColor();
	}

	/**
	 * Computes a colour's perceived brightness.
	 *
	 * @param color the colour; a missing channel reads as 0, since the API omits zero channels
	 * @return the brightness, 0 (black) to 1 (white)
	 */
	double brightness(RgbColor color) {
		return RED_WEIGHT * channel(color.getRed()) + GREEN_WEIGHT * channel(color.getGreen())
				+ BLUE_WEIGHT * channel(color.getBlue());
	}

	/**
	 * Reads one colour channel.
	 *
	 * @param value the channel, {@code null} when the API omitted a zero
	 * @return the channel value
	 */
	double channel(Float value) {
		return value == null ? 0 : value;
	}

	/**
	 * Finds every non-overlapping occurrence of a marker in a text.
	 *
	 * @param text   the text to search
	 * @param marker the marker
	 * @return the occurrences' start indices, ascending
	 */
	List<Integer> occurrences(String text, String marker) {
		List<Integer> out = new ArrayList<>();
		int at = text.indexOf(marker);
		while (at >= 0) {
			out.add(at);
			at = text.indexOf(marker, at + marker.length());
		}
		return out;
	}

	/**
	 * Builds a request that sets the weight and colour of a range of a shape's text.
	 *
	 * @param objectId the shape
	 * @param range    the text range
	 * @param bold     whether the range is bold
	 * @param color    the range's text colour
	 * @return the request
	 */
	Request leadStyleRequest(String objectId, Range range, boolean bold, RgbColor color) {
		return new Request().setUpdateTextStyle(new UpdateTextStyleRequest()
				.setObjectId(objectId)
				.setTextRange(range)
				.setStyle(new TextStyle().setBold(bold)
						.setForegroundColor(new OptionalColor().setOpaqueColor(new OpaqueColor().setRgbColor(color))))
				.setFields(LEAD_FIELDS));
	}

	/**
	 * Builds a request that deletes a range of a shape's text.
	 *
	 * @param objectId the shape
	 * @param index    the range start
	 * @param length   the range length
	 * @return the request
	 */
	Request deleteRange(String objectId, int index, int length) {
		return new Request().setDeleteText(new DeleteTextRequest()
				.setObjectId(objectId)
				.setTextRange(new Range().setType(FIXED_RANGE).setStartIndex(index).setEndIndex(index + length)));
	}

	/**
	 * Joins a shape's text runs into its full text.
	 *
	 * @param textElements the shape's text elements, may be {@code null}
	 * @return the shape's text, empty when it has none
	 */
	String shapeText(List<TextElement> textElements) {
		StringBuilder text = new StringBuilder();
		if (textElements != null) {
			for (TextElement te : textElements) {
				if (te.getTextRun() != null && te.getTextRun().getContent() != null) {
					text.append(te.getTextRun().getContent());
				}
			}
		}
		return text.toString();
	}

	/**
	 * Finds every marker in a shape's text, as absolute text indices in ascending order. A marker is matched
	 * within a single text run, which is where a {@code replaceAllText} value always lands.
	 *
	 * @param textElements the shape's text elements, carrying their start indices
	 * @return the marker start indices, ascending
	 */
	List<Integer> markerIndices(List<TextElement> textElements) {
		List<Integer> indices = new ArrayList<>();
		if (textElements == null) {
			return indices;
		}
		for (TextElement te : textElements) {
			if (te.getTextRun() == null || te.getTextRun().getContent() == null) {
				continue;
			}
			String content = te.getTextRun().getContent();
			int base = te.getStartIndex() == null ? 0 : te.getStartIndex();
			int at = content.indexOf(MARKER);
			while (at >= 0) {
				indices.add(base + at);
				at = content.indexOf(MARKER, at + MARKER.length());
			}
		}
		return indices;
	}

	/**
	 * Builds a request that sets the weight of a range of a shape's text.
	 *
	 * @param objectId the shape
	 * @param range    the text range
	 * @param bold     whether the range is bold
	 * @return the request
	 */
	Request styleRequest(String objectId, Range range, boolean bold) {
		return new Request().setUpdateTextStyle(new UpdateTextStyleRequest()
				.setObjectId(objectId)
				.setTextRange(range)
				.setStyle(new TextStyle().setBold(bold))
				.setFields(BOLD_FIELD));
	}

	/**
	 * Builds a request that deletes one marker.
	 *
	 * @param objectId the shape
	 * @param index    the marker's start index
	 * @return the request
	 */
	Request deleteMarker(String objectId, int index) {
		return new Request().setDeleteText(new DeleteTextRequest()
				.setObjectId(objectId)
				.setTextRange(new Range().setType(FIXED_RANGE)
						.setStartIndex(index).setEndIndex(index + MARKER.length())));
	}
}
