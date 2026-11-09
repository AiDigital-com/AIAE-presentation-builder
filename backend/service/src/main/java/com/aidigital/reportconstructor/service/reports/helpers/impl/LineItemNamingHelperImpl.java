package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.helpers.LineItemNamingHelper;
import org.springframework.stereotype.Component;

/**
 * Spring bean implementation of {@link LineItemNamingHelper}.
 */
@Component
public class LineItemNamingHelperImpl implements LineItemNamingHelper {

	/** Segment separator of a Level 1 naming. */
	private static final String SEPARATOR = "_";

	/** Minimum segment count of a well-formed naming (the line item ID is the 9th). */
	private static final int MIN_SEGMENTS = 9;

	/** Zero-based index of the numeric line item ID segment. */
	private static final int LINE_ITEM_ID_SEGMENT = 8;

	/** Zero-based index of the media plan line ID segment. */
	private static final int PLAN_LINE_ID_SEGMENT = 2;

	/** Placeholder the name builder writes into an unused segment. */
	private static final String EMPTY_SEGMENT = "-";

	@Override
	public String extractLineItemId(String naming) {
		String id = segment(naming, LINE_ITEM_ID_SEGMENT);
		return id != null && id.chars().allMatch(Character::isDigit) ? id : null;
	}

	@Override
	public String extractLineItemIdOrBlank(String naming) {
		String id = extractLineItemId(naming);
		return id == null ? "" : id;
	}

	/**
	 * Reads the media plan's line ID from the 3rd underscore-delimited segment of a Level 1 naming. Unlike
	 * the line item ID it is not required to be numeric, because plans use IDs such as {@code "24"} or
	 * {@code "A3"}.
	 *
	 * @param naming Level 1 Naming cell value (may be {@code null})
	 * @return the trimmed plan line ID, or {@code null} when the naming is malformed or the segment is blank/"-"
	 */
	@Override
	public String extractPlanLineId(String naming) {
		return segment(naming, PLAN_LINE_ID_SEGMENT);
	}

	/**
	 * Reads one trimmed segment of a well-formed naming.
	 *
	 * @param naming Level 1 Naming cell value (may be {@code null})
	 * @param index  zero-based segment index
	 * @return the trimmed segment, or {@code null} when the naming is malformed or the segment is blank/"-"
	 */
	String segment(String naming, int index) {
		if (naming == null) {
			return null;
		}
		String[] parts = naming.split(SEPARATOR);
		if (parts.length < MIN_SEGMENTS) {
			return null;
		}
		String value = parts[index].trim();
		return value.isEmpty() || value.equals(EMPTY_SEGMENT) ? null : value;
	}
}
