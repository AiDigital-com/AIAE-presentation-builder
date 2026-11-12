package com.aidigital.reportconstructor.service.reports.enums;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaidCopySlotResolverTest {

	@Test
	void shouldListOnlyTheChannelsSlotsForTheReportTypeInSlideOrderTest() {
		// Given:
		PaidCopySlotResolver resolver = new PaidCopySlotResolver();

		// When:
		List<PaidCopySlot> eocSearch = resolver.forChannel(PaidChannelKind.SEARCH, false);
		List<PaidCopySlot> eomSocial = resolver.forChannel(PaidChannelKind.SOCIAL, true);

		// Then: EOC search keeps its highlights/recos and drops the EOM-only metric slots, and vice versa
		assertThat(eocSearch).allMatch(s -> s.channel() == PaidChannelKind.SEARCH)
				.contains(PaidCopySlot.SEARCH_VERDICT, PaidCopySlot.SEARCH_HIGHLIGHT, PaidCopySlot.SEARCH_RECO)
				.doesNotContain(PaidCopySlot.SEARCH_EOM_WORKED);
		assertThat(eocSearch.get(0)).isEqualTo(PaidCopySlot.SEARCH_VERDICT);
		assertThat(eomSocial).allMatch(s -> s.channel() == PaidChannelKind.SOCIAL)
				.contains(PaidCopySlot.SOCIAL_VERDICT, PaidCopySlot.SOCIAL_EOM_TOP_CREATIVE)
				.doesNotContain(PaidCopySlot.SOCIAL_HIGHLIGHT, PaidCopySlot.SOCIAL_RECO);
	}
}
