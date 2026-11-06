package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Size;

import java.util.List;

/**
 * A paid chart's sample image as found on the deck, with what the swap needs to put the linked chart in its place.
 *
 * @param objectId  the sample image's object id
 * @param slideId   the slide carrying it
 * @param size      the image's intrinsic size
 * @param transform the image's transform (scale and position)
 * @param aboveIds  the slide's elements drawn above the image, in z-order: brought to the front again after the swap,
 *                  so a label laid over the sample (a donut's hole label) stays on top of the new chart
 */
public record PaidChartSlot(
		String objectId,
		String slideId,
		Size size,
		AffineTransform transform,
		List<String> aboveIds) {
}
