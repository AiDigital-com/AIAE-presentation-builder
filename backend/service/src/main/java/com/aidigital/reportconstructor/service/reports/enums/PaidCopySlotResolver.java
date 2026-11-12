package com.aidigital.reportconstructor.service.reports.enums;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Lists the {@link PaidCopySlot} families one paid channel's slides carry. A bean rather than a static factory on
 * the enum, so callers can replace it in tests.
 */
@Component
public class PaidCopySlotResolver {

	/**
	 * The slot families one channel's slides carry in one report type.
	 *
	 * @param channel the paid channel
	 * @param eom     whether the deck is an EOM report
	 * @return the channel's slots, in declaration (slide) order
	 */
	public List<PaidCopySlot> forChannel(PaidChannelKind channel, boolean eom) {
		return Arrays.stream(PaidCopySlot.values())
				.filter(s -> s.channel() == channel && s.inReport(eom))
				.toList();
	}
}
