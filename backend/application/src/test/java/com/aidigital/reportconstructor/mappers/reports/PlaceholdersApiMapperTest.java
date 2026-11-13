package com.aidigital.reportconstructor.mappers.reports;

import com.aidigital.reportconstructor.api.v1.model.BreakdownSelectionV1;
import com.aidigital.reportconstructor.api.v1.model.DateFilterModeV1;
import com.aidigital.reportconstructor.api.v1.model.DateFilterV1;
import com.aidigital.reportconstructor.api.v1.model.DateRangeResultV1;
import com.aidigital.reportconstructor.api.v1.model.GenerateRequestV1;
import com.aidigital.reportconstructor.api.v1.model.GenerationTargetV1;
import com.aidigital.reportconstructor.api.v1.model.LabelPairV1;
import com.aidigital.reportconstructor.api.v1.model.MappingEntryV1;
import com.aidigital.reportconstructor.api.v1.model.PlaceholderEntryV1;
import com.aidigital.reportconstructor.api.v1.model.PreviewRequestV1;
import com.aidigital.reportconstructor.api.v1.model.PreviewResultV1;
import com.aidigital.reportconstructor.api.v1.model.RateTypeV1;
import com.aidigital.reportconstructor.api.v1.model.ReportTypeV1;
import com.aidigital.reportconstructor.api.v1.model.SectionV1;
import com.aidigital.reportconstructor.api.v1.model.SourceV1;
import com.aidigital.reportconstructor.api.v1.model.TemplateVariantV1;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.DateFilter;
import com.aidigital.reportconstructor.service.reports.dto.DateFilterMode;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.GenerationTarget;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.dto.Placeholder;
import com.aidigital.reportconstructor.service.reports.dto.PreviewSection;
import com.aidigital.reportconstructor.service.reports.dto.RateType;
import com.aidigital.reportconstructor.service.reports.dto.TemplateVariant;
import com.aidigital.reportconstructor.service.reports.services.LabelChip;
import com.aidigital.reportconstructor.service.reports.services.Labels;
import com.aidigital.reportconstructor.service.reports.services.PreviewResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

	@Test
	void shouldMapEveryFieldOfAFullGenerateRequestTest() {
		// Given: a generate request with every field populated
		List<List<String>> sheet = List.of(List.of("s1"));
		List<List<String>> adj = List.of(List.of("a1"));
		List<List<String>> audience = List.of(List.of("au1"));
		List<List<String>> estimates = List.of(List.of("e1"));
		List<List<String>> geo = List.of(List.of("g1"));
		List<List<String>> socialTab = List.of(List.of("Date", "Clicks"));
		MappingEntryV1 entry = new MappingEntryV1()
				.tacticNum(3)
				.planTacticNum(7)
				.tacticName("CTV")
				.lineItemId("LI-1")
				.rateType(RateTypeV1.CPM)
				.unitPrice(new BigDecimal("12.5"))
				.monthlyBudget(new BigDecimal("1000.25"));
		BreakdownSelectionV1 selection = new BreakdownSelectionV1().tacticNum(3).breakdowns(List.of("device", "geo"));
		DateFilterV1 dateFilter = new DateFilterV1()
				.mode(DateFilterModeV1.RANGE)
				.start(LocalDate.of(2026, 1, 1))
				.end(LocalDate.of(2026, 1, 31));
		GenerateRequestV1 body = new GenerateRequestV1()
				.brief("brief")
				.reportType(ReportTypeV1.EOM)
				.marketVolume("1M")
				.sheetRows(sheet)
				.adjRows(adj)
				.audienceRows(audience)
				.estimatesRows(estimates)
				.geoRows(geo)
				.lineItemMapping(List.of(entry))
				.breakdownSelections(List.of(selection))
				.bqSheetId("bq-1")
				.dateFilter(dateFilter)
				.sheetUrl("https://sheet")
				.changeLog("log")
				.estimateDaypartGender(Boolean.FALSE)
				.templateVariant(TemplateVariantV1.STANDARD)
				.paidSocialTabs(Map.of("SOCIAL", socialTab));

		// When:
		GeneratePayload payload = mapper.toPayload(body);

		// Then:
		assertThat(payload).isEqualTo(new GeneratePayload(
				"brief", "EOM", "1M", sheet, adj, audience, estimates, geo,
				List.of(new LineItemMapping("CTV", "LI-1", 3, RateType.CPM, 12.5, 1000.25, 7)),
				List.of(new BreakdownSelection(3, List.of("device", "geo"))),
				"bq-1",
				new DateFilter(DateFilterMode.RANGE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
				"https://sheet", "log", Boolean.FALSE, TemplateVariant.STANDARD,
				Map.of(), Map.of("SOCIAL", socialTab)));
	}

	@Test
	void shouldMapNullCollectionsAndNestedObjectsOfAGenerateRequestTest() {
		// Given: a generate request whose optional collections and nested objects are all null
		GenerateRequestV1 body = new GenerateRequestV1()
				.brief("brief")
				.reportType(ReportTypeV1.EOC)
				.sheetRows(null)
				.adjRows(null)
				.audienceRows(null)
				.estimatesRows(null)
				.geoRows(null)
				.lineItemMapping(null)
				.breakdownSelections(null)
				.estimateDaypartGender(null);

		// When:
		GeneratePayload payload = mapper.toPayload(body);

		// Then: row lists stay null, mapped collections default to empty, the date filter is an empty shell
		assertThat(payload.reportType()).isEqualTo("EOC");
		assertThat(payload.sheetRows()).isNull();
		assertThat(payload.adjRows()).isNull();
		assertThat(payload.audienceRows()).isNull();
		assertThat(payload.estimatesRows()).isNull();
		assertThat(payload.geoRows()).isNull();
		assertThat(payload.lineItemMapping()).isEmpty();
		assertThat(payload.breakdownSelections()).isEmpty();
		assertThat(payload.dateFilter()).isEqualTo(new DateFilter(null, null, null));
		assertThat(payload.estimateDaypartGender()).isNull();
		assertThat(payload.templateVariant()).isEqualTo(TemplateVariant.STANDARD);
		assertThat(payload.paidSearchTabs()).isEmpty();
		assertThat(payload.paidSocialTabs()).isEmpty();
	}

	@Test
	void shouldMapNullElementsOfGenerateRequestCollectionsTest() {
		// Given: null list elements, a selection without breakdowns and a mapping without prices
		MappingEntryV1 bare = new MappingEntryV1().tacticNum(1).tacticName("Display");
		BreakdownSelectionV1 noBreakdowns = new BreakdownSelectionV1().tacticNum(2).breakdowns(null);
		GenerateRequestV1 body = new GenerateRequestV1()
				.brief("brief")
				.reportType(ReportTypeV1.EOC)
				.lineItemMapping(new ArrayList<>(Arrays.asList(bare, null)))
				.breakdownSelections(new ArrayList<>(Arrays.asList(noBreakdowns, null)))
				.dateFilter(new DateFilterV1().mode(null));

		// When:
		GeneratePayload payload = mapper.toPayload(body);

		// Then:
		assertThat(payload.lineItemMapping()).containsExactly(
				new LineItemMapping("Display", null, 1, null, null, null, null),
				new LineItemMapping(null, null, null, null, null, null, null));
		assertThat(payload.breakdownSelections()).containsExactly(
				new BreakdownSelection(2, null),
				new BreakdownSelection(null, null));
		assertThat(payload.dateFilter()).isEqualTo(new DateFilter(null, null, null));
	}

	@Test
	void shouldFailOnANullGenerateRequestBecauseTheReportTypeIsDereferencedTest() {
		// Given:
		GenerateRequestV1 body = null;

		// When-Then: the reportType expression runs outside the generated null guard
		assertThatThrownBy(() -> mapper.toPayload(body)).isInstanceOf(NullPointerException.class);
	}

	@ParameterizedTest
	@EnumSource(TemplateVariantV1.class)
	void shouldMapEveryTemplateVariantTest(TemplateVariantV1 variant) {
		// Given:
		GenerateRequestV1 body = new GenerateRequestV1().brief("b").reportType(ReportTypeV1.EOC).templateVariant(variant);

		// When:
		GeneratePayload payload = mapper.toPayload(body);

		// Then:
		assertThat(payload.templateVariant()).isEqualTo(TemplateVariant.valueOf(variant.name()));
	}

	@ParameterizedTest
	@EnumSource(DateFilterModeV1.class)
	void shouldMapEveryDateFilterModeTest(DateFilterModeV1 mode) {
		// Given:
		GenerateRequestV1 body = new GenerateRequestV1()
				.brief("b")
				.reportType(ReportTypeV1.EOC)
				.dateFilter(new DateFilterV1().mode(mode));

		// When:
		GeneratePayload payload = mapper.toPayload(body);

		// Then:
		assertThat(payload.dateFilter().mode()).isEqualTo(DateFilterMode.valueOf(mode.name()));
	}

	@ParameterizedTest
	@EnumSource(RateTypeV1.class)
	void shouldMapEveryRateTypeTest(RateTypeV1 rateType) {
		// Given:
		MappingEntryV1 entry = new MappingEntryV1().tacticNum(1).tacticName("T").rateType(rateType);

		// When:
		LineItemMapping mapping = mapper.toLineItemMapping(entry);

		// Then:
		assertThat(mapping.rateType()).isEqualTo(RateType.valueOf(rateType.name()));
	}

	@ParameterizedTest
	@EnumSource(ReportTypeV1.class)
	void shouldMapEveryReportTypeToItsWireValueTest(ReportTypeV1 reportType) {
		// Given:
		PreviewRequestV1 body = new PreviewRequestV1().brief("b").reportType(reportType);

		// When:
		GeneratePayload payload = mapper.toPayload(body);

		// Then:
		assertThat(payload.reportType()).isEqualTo(reportType.getValue());
	}

	@Test
	void shouldMapANullMappingEntryToAnEmptyLineItemMappingTest() {
		// Given:
		MappingEntryV1 entry = null;

		// When:
		LineItemMapping mapping = mapper.toLineItemMapping(entry);

		// Then:
		assertThat(mapping).isEqualTo(new LineItemMapping(null, null, null, null, null, null, null));
	}

	@Test
	void shouldMapEveryFieldOfAFullPreviewRequestAndLeaveGenerationOnlyFieldsUnsetTest() {
		// Given:
		List<List<String>> sheet = List.of(List.of("s1"));
		List<List<String>> adj = List.of(List.of("a1"));
		List<List<String>> audience = List.of(List.of("au1"));
		List<List<String>> estimates = List.of(List.of("e1"));
		List<List<String>> geo = List.of(List.of("g1"));
		PreviewRequestV1 body = new PreviewRequestV1()
				.brief("brief")
				.reportType(ReportTypeV1.EOC)
				.marketVolume("2M")
				.sheetRows(sheet)
				.adjRows(adj)
				.audienceRows(audience)
				.estimatesRows(estimates)
				.geoRows(geo)
				.lineItemMapping(List.of(new MappingEntryV1().tacticNum(4).tacticName("Audio").lineItemId("LI-4")))
				.dateFilter(new DateFilterV1().mode(DateFilterModeV1.ALL));

		// When:
		GeneratePayload payload = mapper.toPayload(body);

		// Then:
		assertThat(payload).isEqualTo(new GeneratePayload(
				"brief", "EOC", "2M", sheet, adj, audience, estimates, geo,
				List.of(new LineItemMapping("Audio", "LI-4", 4, null, null, null, null)),
				null, null, new DateFilter(DateFilterMode.ALL, null, null),
				null, null, null, TemplateVariant.STANDARD, Map.of(), Map.of()));
	}

	@Test
	void shouldMapNullCollectionsOfAPreviewRequestTest() {
		// Given:
		PreviewRequestV1 body = new PreviewRequestV1()
				.brief("brief")
				.reportType(ReportTypeV1.EOM)
				.sheetRows(null)
				.adjRows(null)
				.audienceRows(null)
				.estimatesRows(null)
				.geoRows(null)
				.lineItemMapping(null);

		// When:
		GeneratePayload payload = mapper.toPayload(body);

		// Then:
		assertThat(payload.reportType()).isEqualTo("EOM");
		assertThat(payload.sheetRows()).isNull();
		assertThat(payload.adjRows()).isNull();
		assertThat(payload.audienceRows()).isNull();
		assertThat(payload.estimatesRows()).isNull();
		assertThat(payload.geoRows()).isNull();
		assertThat(payload.lineItemMapping()).isEmpty();
		assertThat(payload.dateFilter()).isEqualTo(new DateFilter(null, null, null));
	}

	@Test
	void shouldFailOnANullPreviewRequestBecauseTheReportTypeIsDereferencedTest() {
		// Given:
		PreviewRequestV1 body = null;

		// When-Then:
		assertThatThrownBy(() -> mapper.toPayload(body)).isInstanceOf(NullPointerException.class);
	}

	@ParameterizedTest
	@EnumSource(GenerationTargetV1.class)
	void shouldMapEveryGenerationTargetTest(GenerationTargetV1 target) {
		// When:
		GenerationTarget result = mapper.toTarget(target);

		// Then:
		assertThat(result).isEqualTo(GenerationTarget.valueOf(target.name()));
	}

	@Test
	void shouldDefaultAMissingGenerationTargetToSlidesTest() {
		// When:
		GenerationTarget result = mapper.toTarget(null);

		// Then:
		assertThat(result).isEqualTo(GenerationTarget.SLIDES);
	}

	@Test
	void shouldMapAFlightWindowToTheDateRangeResponseTest() {
		// Given:
		FlightDates range = new FlightDates(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

		// When:
		DateRangeResultV1 response = mapper.toDateRangeResponse(range);

		// Then:
		assertThat(response.getStart()).isEqualTo(LocalDate.of(2026, 3, 1));
		assertThat(response.getEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
	}

	@Test
	void shouldMapAMissingFlightWindowToAnEmptyDateRangeResponseTest() {
		// When:
		DateRangeResultV1 response = mapper.toDateRangeResponse(null);

		// Then:
		assertThat(response).isNotNull();
		assertThat(response.getStart()).isNull();
		assertThat(response.getEnd()).isNull();
	}

	@Test
	void shouldMapAFullPreviewResultTest() {
		// Given:
		Placeholder resolved = new Placeholder("{{CTR}}", "CTR", "0.5%", "sheet");
		PreviewSection section = new PreviewSection("Performance", List.of(resolved));
		Labels labels = new Labels(List.of(new LabelChip("Impressions", "100")), List.of(new LabelChip("Spend", "$5")));
		PreviewResult result = new PreviewResult(List.of(section), labels, 8, 10, 120, 40);

		// When:
		PreviewResultV1 response = mapper.toPreviewResponse(result);

		// Then:
		assertThat(response.getStats().getFound()).isEqualTo(8);
		assertThat(response.getStats().getTotal()).isEqualTo(10);
		assertThat(response.getSheetCount()).isEqualTo(120);
		assertThat(response.getAdjCount()).isEqualTo(40);
		assertThat(response.getSections()).containsExactly(new SectionV1()
				.title("Performance")
				.placeholders(List.of(new PlaceholderEntryV1()
						.key("{{CTR}}").label("CTR").value("0.5%").source(SourceV1.SHEET))));
		assertThat(response.getAllLabels()).containsOnly(
				Map.entry("sheet", List.of(new LabelPairV1().label("Impressions").value("100"))),
				Map.entry("adj", List.of(new LabelPairV1().label("Spend").value("$5"))));
	}

	@Test
	void shouldMapAPreviewResultWithoutSectionsOrLabelsTest() {
		// Given:
		PreviewResult result = new PreviewResult(null, null, 0, 0, 0, 0);

		// When:
		PreviewResultV1 response = mapper.toPreviewResponse(result);

		// Then:
		assertThat(response.getSections()).isEmpty();
		assertThat(response.getAllLabels()).isEmpty();
		assertThat(response.getStats().getFound()).isZero();
		assertThat(response.getStats().getTotal()).isZero();
	}

	@Test
	void shouldFailOnANullPreviewResultBecauseTheLabelsAreDereferencedTest() {
		// Given:
		PreviewResult result = null;

		// When-Then: the allLabels expression runs outside the generated null guard
		assertThatThrownBy(() -> mapper.toPreviewResponse(result)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void shouldMapNullSectionsAndNullPlaceholderListsTest() {
		// Given:
		List<PreviewSection> sections = new ArrayList<>(Arrays.asList(new PreviewSection("Empty", null), null));

		// When:
		List<SectionV1> response = mapper.toSections(sections);

		// Then:
		assertThat(response).containsExactly(
				new SectionV1().title("Empty").placeholders(new ArrayList<>()),
				new SectionV1());
	}

	@Test
	void shouldMapANullSectionListToAnEmptyListTest() {
		// When:
		List<SectionV1> response = mapper.toSections(null);

		// Then:
		assertThat(response).isEmpty();
	}

	@Test
	void shouldMapANullPlaceholderListToAnEmptyListTest() {
		// When:
		List<PlaceholderEntryV1> response = mapper.toPlaceholders(null);

		// Then:
		assertThat(response).isEmpty();
	}

	@Test
	void shouldMapAPlaceholderWithoutASourceTest() {
		// Given:
		Placeholder placeholder = new Placeholder("{{X}}", "X", null, null);

		// When:
		PlaceholderEntryV1 entry = mapper.toPlaceholder(placeholder);

		// Then:
		assertThat(entry).isEqualTo(new PlaceholderEntryV1().key("{{X}}").label("X"));
	}

	@Test
	void shouldFailOnANullPlaceholderBecauseTheSourceIsDereferencedTest() {
		// Given:
		Placeholder placeholder = null;

		// When-Then: the source expression runs outside the generated null guard
		assertThatThrownBy(() -> mapper.toPlaceholder(placeholder)).isInstanceOf(NullPointerException.class);
	}

	@ParameterizedTest
	@CsvSource({
			"ai, CLAUDE",
			"fallback, UI",
			"adj, ADJ",
			"sheet, SHEET",
			"ui, UI",
			"claude, CLAUDE",
			"not_found, NOT_FOUND"
	})
	void shouldMapEverySourceTagIncludingLegacyAliasesTest(String source, SourceV1 expected) {
		// When:
		SourceV1 result = mapper.toSource(source);

		// Then:
		assertThat(result).isEqualTo(expected);
	}

	@Test
	void shouldMapANullSourceTagToNullTest() {
		// When:
		SourceV1 result = mapper.toSource(null);

		// Then:
		assertThat(result).isNull();
	}

	@Test
	void shouldRejectAnUnknownSourceTagTest() {
		// When-Then:
		assertThatThrownBy(() -> mapper.toSource("gemini"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("gemini");
	}

	@Test
	void shouldMapNullLabelsToAnEmptyMapTest() {
		// When:
		Map<String, List<LabelPairV1>> result = mapper.toAllLabels(null);

		// Then:
		assertThat(result).isEmpty();
	}

	@Test
	void shouldMapLabelsWithNullBucketsToEmptyBucketsTest() {
		// Given:
		Labels labels = new Labels(null, null);

		// When:
		Map<String, List<LabelPairV1>> result = mapper.toAllLabels(labels);

		// Then:
		assertThat(result).containsOnly(Map.entry("sheet", List.of()), Map.entry("adj", List.of()));
	}

	@Test
	void shouldMapNullLabelChipsToEmptyLabelPairsTest() {
		// Given:
		List<LabelChip> chips = new ArrayList<>(Arrays.asList(new LabelChip("Clicks", "7"), null));

		// When:
		List<LabelPairV1> result = mapper.toLabelPairs(chips);

		// Then:
		assertThat(result).containsExactly(new LabelPairV1().label("Clicks").value("7"), new LabelPairV1());
	}
}
