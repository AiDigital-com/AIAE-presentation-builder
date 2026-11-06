package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.CreateSheetsChartRequest;
import com.google.api.services.slides.v1.model.CreateShapeRequest;
import com.google.api.services.slides.v1.model.DeleteObjectRequest;
import com.google.api.services.slides.v1.model.Dimension;
import com.google.api.services.slides.v1.model.InsertTextRequest;
import com.google.api.services.slides.v1.model.OpaqueColor;
import com.google.api.services.slides.v1.model.OptionalColor;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.PageElementProperties;
import com.google.api.services.slides.v1.model.ParagraphStyle;
import com.google.api.services.slides.v1.model.Range;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.RgbColor;
import com.google.api.services.slides.v1.model.ShapeProperties;
import com.google.api.services.slides.v1.model.Size;
import com.google.api.services.slides.v1.model.TextStyle;
import com.google.api.services.slides.v1.model.UpdatePageElementsZOrderRequest;
import com.google.api.services.slides.v1.model.UpdateParagraphStyleRequest;
import com.google.api.services.slides.v1.model.UpdateShapePropertiesRequest;
import com.google.api.services.slides.v1.model.UpdateTextStyleRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the paid charts' sample images on a deck and builds the Slides requests that swap one for a linked chart:
 * delete the image, link the chart in its place (as a square at its left edge for a donut), bring back to the front
 * whatever was drawn above the image, and, for an overview donut, lay the total over the hole the way the sample
 * image printed it.
 */
@Component
public class PaidChartSlideRequests {

	/** EMU per point. */
	private static final double EMU_PER_PT = 12_700d;
	/** EMU per pixel at 96 dpi, for sizing the chart in its spreadsheet like the slide element. */
	private static final double EMU_PER_PIXEL = 9_525d;
	private static final String EMU = "EMU";
	private static final String PT = "PT";
	/** Hole label: the total, then a smaller caption, as on the sample donut. */
	private static final String CENTER_CAPTION = "Conversions";
	private static final String CENTER_FONT = "Inter";
	private static final int CENTER_VALUE_SIZE = 12;
	private static final int CENTER_CAPTION_SIZE = 7;
	private static final String CENTER_VALUE_COLOR = "#080808";
	private static final String CENTER_CAPTION_COLOR = "#737373";
	/** Hole label box: width as a share of the donut's side, fixed height in points. */
	private static final double CENTER_BOX_WIDTH_SHARE = 0.5;
	private static final double CENTER_BOX_HEIGHT_PT = 30;
	private static final double CHANNEL_MAX = 255d;

	/**
	 * Indexes the deck's elements whose alt-text title is one of the given markers. Only top-level elements count,
	 * which is where the template keeps its sample chart images.
	 *
	 * @param slides  the deck's slides, read with object ids, titles, sizes and transforms
	 * @param markers the alt-text titles to look for
	 * @return each found marker's slot, keyed by marker; a marker the deck lacks is absent
	 */
	public Map<String, PaidChartSlot> slots(List<Page> slides, Set<String> markers) {
		Map<String, PaidChartSlot> out = new LinkedHashMap<>();
		for (Page slide : slides == null ? List.<Page>of() : slides) {
			List<PageElement> els = slide.getPageElements() == null ? List.of() : slide.getPageElements();
			for (int i = 0; i < els.size(); i++) {
				PageElement el = els.get(i);
				String title = el.getTitle() == null ? null : el.getTitle().trim();
				if (title == null || !markers.contains(title) || out.containsKey(title)) {
					continue;
				}
				List<String> above = els.subList(i + 1, els.size()).stream().map(PageElement::getObjectId).toList();
				out.put(title, new PaidChartSlot(
						el.getObjectId(), slide.getObjectId(), el.getSize(), el.getTransform(), above));
			}
		}
		return out;
	}

	/**
	 * @param slot  the sample image
	 * @param style the chart's style
	 * @return the chart's box in EMU as {@code [x, y, width, height]}: the image's own box, or for a donut a square
	 *         centred on the image, sized as the style's share of the image's shorter side
	 */
	public double[] box(PaidChartSlot slot, PaidChartStyle style) {
		double x = slot.transform().getTranslateX() == null ? 0 : slot.transform().getTranslateX();
		double y = slot.transform().getTranslateY() == null ? 0 : slot.transform().getTranslateY();
		double width = slot.size().getWidth().getMagnitude() * scale(slot.transform().getScaleX());
		double height = slot.size().getHeight().getMagnitude() * scale(slot.transform().getScaleY());
		if (!style.square()) {
			return new double[] {x, y, width, height};
		}
		double side = Math.min(width, height) * style.squareShare();
		return new double[] {x + (width - side) / 2, y + (height - side) / 2, side, side};
	}

	/**
	 * @param emu a length in EMU
	 * @return the same length in pixels at 96 dpi, at least 1
	 */
	public int pixels(double emu) {
		return Math.max(1, (int) Math.round(emu / EMU_PER_PIXEL));
	}

	/**
	 * @param slot          the sample image to replace
	 * @param style         the chart's style
	 * @param spreadsheetId the chart's spreadsheet
	 * @param chartId       the chart's id in that spreadsheet
	 * @param newObjectId   object id for the linked chart
	 * @param centerValue   the total printed in a donut's hole, or {@code null}
	 * @return the swap requests, in order
	 */
	public List<Request> swap(PaidChartSlot slot, PaidChartStyle style, String spreadsheetId, int chartId,
			String newObjectId, String centerValue) {
		double[] box = box(slot, style);
		double x = box[0];
		double y = box[1];
		double[] bounds = {box[2], box[3]};
		List<Request> requests = new ArrayList<>();
		requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(slot.objectId())));
		requests.add(new Request().setCreateSheetsChart(new CreateSheetsChartRequest()
				.setObjectId(newObjectId)
				.setSpreadsheetId(spreadsheetId)
				.setChartId(chartId)
				.setLinkingMode("LINKED")
				.setElementProperties(properties(slot.slideId(), bounds[0], bounds[1], x, y))));
		if (!slot.aboveIds().isEmpty()) {
			requests.add(new Request().setUpdatePageElementsZOrder(new UpdatePageElementsZOrderRequest()
					.setPageElementObjectIds(slot.aboveIds()).setOperation("BRING_TO_FRONT")));
		}
		if (style.centerText() && centerValue != null) {
			requests.addAll(centerLabel(slot.slideId(), newObjectId + "_hole", bounds[0], x, y, centerValue));
		}
		return requests;
	}

	/**
	 * @param slideId  the slide carrying the donut
	 * @param objectId object id for the label box
	 * @param side     the donut's side in EMU
	 * @param x        the donut's left edge in EMU
	 * @param y        the donut's top edge in EMU
	 * @param value    the total to print
	 * @return requests creating a centred two-line label (total, caption) over the donut's hole
	 */
	List<Request> centerLabel(String slideId, String objectId, double side, double x, double y, String value) {
		double width = side * CENTER_BOX_WIDTH_SHARE;
		double height = CENTER_BOX_HEIGHT_PT * EMU_PER_PT;
		String text = value + "\n" + CENTER_CAPTION;
		int valueEnd = value.length();
		List<Request> requests = new ArrayList<>();
		requests.add(new Request().setCreateShape(new CreateShapeRequest()
				.setObjectId(objectId)
				.setShapeType("TEXT_BOX")
				.setElementProperties(properties(slideId, width, height,
						x + (side - width) / 2, y + (side - height) / 2))));
		requests.add(new Request().setInsertText(new InsertTextRequest().setObjectId(objectId).setText(text)));
		requests.add(new Request().setUpdateShapeProperties(new UpdateShapePropertiesRequest().setObjectId(objectId)
				.setShapeProperties(new ShapeProperties().setContentAlignment("MIDDLE"))
				.setFields("contentAlignment")));
		requests.add(new Request().setUpdateParagraphStyle(new UpdateParagraphStyleRequest().setObjectId(objectId)
				.setTextRange(new Range().setType("ALL"))
				.setStyle(new ParagraphStyle().setAlignment("CENTER"))
				.setFields("alignment")));
		requests.add(textStyle(objectId, 0, valueEnd, CENTER_VALUE_SIZE, CENTER_VALUE_COLOR, true));
		requests.add(textStyle(objectId, valueEnd + 1, text.length(), CENTER_CAPTION_SIZE, CENTER_CAPTION_COLOR,
				false));
		return requests;
	}

	/**
	 * @param objectId the text box
	 * @param start    inclusive start index
	 * @param end      exclusive end index
	 * @param size     font size in points
	 * @param hex      text colour, {@code #RRGGBB}
	 * @param bold     whether the run is bold
	 * @return a request styling that run of the box's text
	 */
	Request textStyle(String objectId, int start, int end, int size, String hex, boolean bold) {
		return new Request().setUpdateTextStyle(new UpdateTextStyleRequest().setObjectId(objectId)
				.setTextRange(new Range().setType("FIXED_RANGE").setStartIndex(start).setEndIndex(end))
				.setStyle(new TextStyle().setFontFamily(CENTER_FONT).setBold(bold)
						.setFontSize(new Dimension().setMagnitude((double) size).setUnit(PT))
						.setForegroundColor(new OptionalColor()
								.setOpaqueColor(new OpaqueColor().setRgbColor(rgb(hex)))))
				.setFields("fontFamily,bold,fontSize,foregroundColor"));
	}

	/**
	 * @param slideId the slide
	 * @param width   width in EMU
	 * @param height  height in EMU
	 * @param x       left edge in EMU
	 * @param y       top edge in EMU
	 * @return element properties placing an element at that box, unscaled
	 */
	PageElementProperties properties(String slideId, double width, double height, double x, double y) {
		return new PageElementProperties().setPageObjectId(slideId)
				.setSize(new Size()
						.setWidth(new Dimension().setMagnitude(width).setUnit(EMU))
						.setHeight(new Dimension().setMagnitude(height).setUnit(EMU)))
				.setTransform(new AffineTransform().setScaleX(1d).setScaleY(1d)
						.setTranslateX(x).setTranslateY(y).setUnit(EMU));
	}

	/**
	 * @param scale a transform scale, possibly absent
	 * @return the scale's magnitude, 1 when absent
	 */
	double scale(Double scale) {
		return scale == null ? 1d : Math.abs(scale);
	}

	/**
	 * @param hex a colour, {@code #RRGGBB}
	 * @return the colour with 0..1 channels
	 */
	RgbColor rgb(String hex) {
		byte[] c = HexFormat.of().parseHex(hex.substring(1));
		return new RgbColor()
				.setRed((float) (Byte.toUnsignedInt(c[0]) / CHANNEL_MAX))
				.setGreen((float) (Byte.toUnsignedInt(c[1]) / CHANNEL_MAX))
				.setBlue((float) (Byte.toUnsignedInt(c[2]) / CHANNEL_MAX));
	}
}
