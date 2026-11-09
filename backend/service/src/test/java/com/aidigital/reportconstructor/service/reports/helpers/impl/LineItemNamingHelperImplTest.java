package com.aidigital.reportconstructor.service.reports.helpers.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LineItemNamingHelperImplTest {

	private final LineItemNamingHelperImpl helper = new LineItemNamingHelperImpl();

	@Test
	void extractLineItemId_readsIndexEight() {
		assertThat(helper.extractLineItemId("a_b_c_d_e_f_g_h_42_tail")).isEqualTo("42");
		assertThat(helper.extractLineItemId("short_name")).isNull();
		assertThat(helper.extractLineItemId("a_b_c_d_e_f_g_h_-_tail")).isNull();
	}

	@Test
	void shouldReadPlanLineIdFromThirdSegmentTest() {
		// Given
		String naming = "GCVB_Grapevine Convention & Visitors Bureau_14.2_Main Street Fest 2026"
				+ "_Video_Prospecting_CPM_-_650119_Contextual";

		// When
		String planLineId = helper.extractPlanLineId(naming);

		// Then
		assertThat(planLineId).isEqualTo("14.2");
	}

	@Test
	void shouldReturnNoPlanLineIdForMalformedOrPlaceholderNamingTest() {
		// Given
		String tooShort = "GCVB_Grapevine_24_Fest";
		String placeholder = "a_b_-_d_e_f_g_h_42_tail";
		String blank = "a_b_ _d_e_f_g_h_42_tail";

		// When
		String fromShort = helper.extractPlanLineId(tooShort);
		String fromPlaceholder = helper.extractPlanLineId(placeholder);
		String fromBlank = helper.extractPlanLineId(blank);
		String fromNull = helper.extractPlanLineId(null);

		// Then
		assertThat(fromShort).isNull();
		assertThat(fromPlaceholder).isNull();
		assertThat(fromBlank).isNull();
		assertThat(fromNull).isNull();
	}

	@Test
	void extractLineItemIdOrBlank_returnsEmptyWhenInvalid() {
		assertThat(helper.extractLineItemIdOrBlank("a_b_c_d_e_f_g_h_12345_x")).isEqualTo("12345");
		assertThat(helper.extractLineItemIdOrBlank("a_b_c")).isEmpty();
		assertThat(helper.extractLineItemIdOrBlank(null)).isEmpty();
	}
}
