package com.aidigital.reportconstructor.mappers.reports;

import com.aidigital.reportconstructor.api.v1.model.GenerateRequestV1;
import com.aidigital.reportconstructor.api.v1.model.ReportTypeV1;
import com.aidigital.reportconstructor.api.v1.model.TemplateVariantV1;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.TemplateVariant;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceholdersApiMapperTest {

	private final PlaceholdersApiMapperImpl mapper = new PlaceholdersApiMapperImpl();

	@Test
	void shouldCarryThePaidExportTabsOntoThePayloadTest() {
		// Given: an MPO request with a Paid Search export and no Paid Social one
		List<List<String>> fullFlight = List.of(List.of("Date", "Impressions"), List.of("2026-10-07", "59"));
		GenerateRequestV1 body = new GenerateRequestV1()
				.brief("the brief")
				.reportType(ReportTypeV1.EOC)
				.templateVariant(TemplateVariantV1.MPO)
				.paidSearchTabs(Map.of("FULL-FLIGHT PERFORMANCE", fullFlight));

		// When:
		GeneratePayload payload = mapper.toPayload(body);

		// Then: the connected export arrives keyed by tab name; the absent one is empty, not null
		assertThat(payload.templateVariant()).isEqualTo(TemplateVariant.MPO);
		assertThat(payload.paidSearchTabs()).containsExactly(Map.entry("FULL-FLIGHT PERFORMANCE", fullFlight));
		assertThat(payload.paidSocialTabs()).isEmpty();
	}
}
