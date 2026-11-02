package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.ports.DeckMarkup;
import com.google.api.services.slides.v1.model.DeleteObjectRequest;
import com.google.api.services.slides.v1.model.DeleteTextRequest;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Range;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.TextElement;
import com.google.api.services.slides.v1.model.TextStyle;
import com.google.api.services.slides.v1.model.UpdateTextStyleRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the markup placeholder values carry to a finished deck: {@code **key phrase**} markers become real
 * bold text, and a shape whose whole text is {@link DeckMarkup#HIDE_SHAPE} is removed.
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
 * <p>An optional chip (an audience segment the campaign does not have) is written as
 * {@link DeckMarkup#HIDE_SHAPE}; its shape is deleted so the slide shows no empty pill.
 */
@Component
public class DeckMarkupRequestBuilder {

	/** The marker the copy wraps a bold phrase in. */
	static final String MARKER = "**";

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
	public List<Request> deckMarkupRequests(List<Page> slides) {
		List<Request> requests = new ArrayList<>();
		if (slides == null) {
			return requests;
		}
		for (Page page : slides) {
			if (page.getPageElements() == null) {
				continue;
			}
			for (PageElement element : page.getPageElements()) {
				requests.addAll(shapeRequests(element));
			}
		}
		return requests;
	}

	/**
	 * Builds one shape's requests: reset its text to regular, then work through its markers from the last to
	 * the first, so every deletion only shifts text that has already been handled.
	 *
	 * @param element a page element, possibly not a shape or without text
	 * @return the shape's requests, empty when it carries no marker
	 */
	List<Request> shapeRequests(PageElement element) {
		if (element.getShape() == null || element.getShape().getText() == null) {
			return List.of();
		}
		if (DeckMarkup.HIDE_SHAPE.equals(shapeText(element.getShape().getText().getTextElements()).strip())) {
			return List.of(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(element.getObjectId())));
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
