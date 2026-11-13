package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.AudienceAgeRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceSegmentRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceTable;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.DeviceRow;
import com.aidigital.reportconstructor.service.reports.dto.DeviceTable;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.dto.SheetChartData;
import com.aidigital.reportconstructor.service.reports.dto.TemplateVariant;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import com.aidigital.reportconstructor.service.reports.enums.PaidChannelKind;
import com.aidigital.reportconstructor.service.reports.enums.PaidChartMarker;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownSelectionResolver;
import com.aidigital.reportconstructor.service.reports.helpers.EffectiveTacticsHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.helpers.SheetChartDataReader;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import com.aidigital.reportconstructor.service.reports.model.PaidSlideChart;
import com.aidigital.reportconstructor.service.reports.model.PaidSlidesFill;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartJob;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartRequest;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartSlice;
import com.aidigital.reportconstructor.service.reports.ports.ChartProvider;
import com.aidigital.reportconstructor.service.reports.ports.ChartRequest;
import com.aidigital.reportconstructor.service.reports.ports.PaidChartRequest;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGenerationChartHelperImplTest {

	@Mock
	private ChartProvider charts;
	@Mock
	private SlidesProvider slides;
	@Mock
	private TacticExtractionHelper tacticExtraction;
	@Mock
	private EffectiveTacticsHelper effectiveTactics;
	@Mock
	private ReportNumberParser reportNumbers;
	@Mock
	private SheetChartDataReader sheetChartData;
	@Mock
	private BreakdownSelectionResolver breakdownResolver;
	@Mock
	private ReportSheetHelper sheetHelper;

	@InjectMocks
	private ReportGenerationChartHelperImpl helper;

	@Test
	void shouldApplyTheBoldMarkupBeforeDashingTheUnresolvedTokensTest() {
		// Given: a finished deck with nothing left to dash
		String url = "https://docs.google.com/presentation/d/deck123/edit";
		when(slides.dashUnresolvedTokens("deck123", "EOM", TemplateVariant.MPO, "token")).thenReturn(List.of());

		// When:
		List<String> warnings = helper.dashUnresolvedTokens(url, "EOM", TemplateVariant.MPO, "token");

		// Then: the emphasis markers were turned into bold text on the same deck, and nothing was dashed
		InOrder order = inOrder(slides);
		order.verify(slides).applyDeckMarkup("deck123", "token");
		order.verify(slides).dashUnresolvedTokens("deck123", "EOM", TemplateVariant.MPO, "token");
		assertThat(warnings).isEmpty();
	}

	@Test
	void shouldExtractPresentationIdFromSlideUrlTest() {
		assertThat(helper.extractPresentationId("https://docs.google.com/presentation/d/abc-123_9/edit"))
				.isEqualTo("abc-123_9");
		assertThat(helper.extractPresentationId(null)).isNull();
	}

	@Test
	void shouldClampTheTacticCountAndSwallowFailuresWhenAddingTacticSlidesTest() {
		// Given: a provider that fails, and a tactic count above the template ceiling
		Map<String, String> values = Map.of("{{tactic 1}}", "Display");
		doThrow(new IllegalStateException("slides down"))
				.when(slides).addTacticSlides("pres-1", 28, values, "EOC", TemplateVariant.STANDARD, "token");

		// When: the deck's tactic slides are built
		List<String> warnings =
				helper.addTacticSlides("https://docs.google.com/presentation/d/pres-1/edit", 99, values, "EOC", TemplateVariant.STANDARD, "token");

		// Then: the count reached the provider clamped to 28, and the failure did not propagate — the deck
		// still ships, missing tactic slides rather than nothing at all — but it is reported as a job warning
		// carrying the provider's reason, not swallowed into the server log
		verify(slides).addTacticSlides("pres-1", 28, values, "EOC", TemplateVariant.STANDARD, "token");
		assertThat(warnings).hasSize(1);
		assertThat(warnings.getFirst()).contains("28 tactic(s)").contains("slides down");
	}

	@Test
	void shouldReportNoWarningWhenTacticSlidesAreBuiltTest() {
		// Given-When: a provider that inserts the slides without failing
		List<String> warnings = helper.addTacticSlides(
				"https://docs.google.com/presentation/d/pres-1/edit", 2, Map.of(), "EOC", TemplateVariant.STANDARD, "token");

		// Then: nothing is reported on the job
		assertThat(warnings).isEmpty();
	}

	@Test
	void shouldSkipAddingTacticSlidesWhenTheSlideUrlCarriesNoPresentationIdTest() {
		// Given-When: a slide url the presentation id cannot be parsed out of
		List<String> warnings = helper.addTacticSlides("not-a-slides-url", 3, Map.of(), "EOC", TemplateVariant.STANDARD, "token");

		// Then: the provider is never called with a bogus deck id
		verify(slides, never()).addTacticSlides(any(), anyInt(), any(), any(), any(), any());
		assertThat(warnings).isEmpty();
	}

	@Test
	void shouldSkipBreakdownSlidesWhenSelectionsNullTest() {
		// Given: a payload with no breakdown selections
		GeneratePayload payload = new GeneratePayload(
				"brief", "standard", "", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), null, "", null, null, null);

		// When:
		helper.addBreakdownSlides("https://docs.google.com/presentation/d/pres-1/edit", payload, 5, Map.of(), "token");

		// Then: the slides provider is never asked to add breakdown slides
		verify(slides, never()).addBreakdownSlides(any(), any(), any(), any(), any(), any());
	}

	@Test
	void shouldInsertOnlyBreakdownSlidesWithinTacticCountTest() {
		// Given: the resolver reports tactic 1 (Top Publishers) and tactic 3 (Device) enabled, but only
		// 2 tactics are active
		List<BreakdownSelection> selections = List.of(
				new BreakdownSelection(1, List.of("tp")),
				new BreakdownSelection(3, List.of("dev")));
		GeneratePayload payload = new GeneratePayload(
				"brief", "standard", "", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), selections, "", null, null, null);
		Map<Integer, Set<BreakdownType>> resolved = new LinkedHashMap<>();
		resolved.put(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS));
		resolved.put(3, EnumSet.of(BreakdownType.DEVICE));
		when(breakdownResolver.resolve(selections)).thenReturn(resolved);

		// When:
		helper.addBreakdownSlides("https://docs.google.com/presentation/d/pres-1/edit", payload, 2, Map.of(), "token");

		// Then: only tactic 1 (within the active count) is passed to the provider; tactic 3 is dropped
		Map<Integer, Set<BreakdownType>> expected = new LinkedHashMap<>();
		expected.put(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS));
		verify(slides).addBreakdownSlides(eq("pres-1"), eq(expected), eq(Map.of()), eq("standard"), eq(TemplateVariant.STANDARD), eq("token"));
	}

	@Test
	void shouldSkipBreakdownSlidesWhenAllSelectionsBeyondTacticCountTest() {
		// Given: the only enabled tactic is beyond the active count
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(5, List.of("tp")));
		GeneratePayload payload = new GeneratePayload(
				"brief", "standard", "", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), selections, "", null, null, null);
		Map<Integer, Set<BreakdownType>> resolved = new LinkedHashMap<>();
		resolved.put(5, EnumSet.of(BreakdownType.TOP_PUBLISHERS));
		when(breakdownResolver.resolve(selections)).thenReturn(resolved);

		// When:
		helper.addBreakdownSlides("https://docs.google.com/presentation/d/pres-1/edit", payload, 2, Map.of(), "token");

		// Then: nothing is inserted
		verify(slides, never()).addBreakdownSlides(any(), any(), any(), any(), any(), any());
	}

	@Test
	void shouldBuildBreakdownChartJobsForAudienceAndDeviceTacticsTest() {
		// Given: tactic 1 enabled Device, tactic 2 enabled Audience (both within the active count), and each
		// sheet block carries one impressions row
		List<BreakdownSelection> selections = List.of(
				new BreakdownSelection(1, List.of("dev")),
				new BreakdownSelection(2, List.of("aud")));
		GeneratePayload payload = new GeneratePayload(
				"brief", "standard", "", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), selections, "", null, "https://docs.google.com/spreadsheets/d/sheet-1/edit", null);
		Map<Integer, Set<BreakdownType>> resolved = new LinkedHashMap<>();
		resolved.put(1, EnumSet.of(BreakdownType.DEVICE));
		resolved.put(2, EnumSet.of(BreakdownType.AUDIENCE));
		when(breakdownResolver.resolve(selections)).thenReturn(resolved);
		when(sheetHelper.readDeviceTables(eq("https://docs.google.com/spreadsheets/d/sheet-1/edit"), eq(Set.of(1)),
				eq("token"))).thenReturn(Map.of(1, new DeviceTable("", "", "", "", "",
				List.of(new DeviceRow("Mobile", "1,000", "", "", "")))));
		when(sheetHelper.readAudienceTables(eq("https://docs.google.com/spreadsheets/d/sheet-1/edit"), eq(Set.of(2)),
				eq("token"))).thenReturn(Map.of(2, new AudienceTable("", "",
				List.of(new AudienceAgeRow("25-34", "2,000")),
				List.of(new AudienceSegmentRow("In-Market: Luxury Travel", "142")))));
		when(reportNumbers.parseReportNumber("1,000")).thenReturn(1000.0);
		when(reportNumbers.parseReportNumber("2,000")).thenReturn(2000.0);
		when(reportNumbers.parseReportNumber("142")).thenReturn(142.0);

		// When:
		helper.buildBreakdownCharts(
				"https://docs.google.com/presentation/d/pres-1/edit", payload, 2, Map.of(), "token");

		// Then: three jobs — the audience slide carries two charts (age + segments), the device slide one
		ArgumentCaptor<BreakdownChartRequest> captor = ArgumentCaptor.forClass(BreakdownChartRequest.class);
		verify(charts).buildBreakdownCharts(captor.capture());
		List<BreakdownChartJob> jobs = captor.getValue().jobs();
		assertThat(captor.getValue().presentationId()).isEqualTo("pres-1");
		assertThat(jobs).hasSize(3);
		assertThat(jobs).anySatisfy(job -> {
			assertThat(job.seriesCode()).isEqualTo("aud");
			assertThat(job.tacticNum()).isEqualTo(2);
			assertThat(job.slices()).singleElement().satisfies(slice -> {
				assertThat(slice.label()).isEqualTo("25-34");
				assertThat(slice.value()).isEqualTo(2000.0);
			});
		});
		// And: the segment chart plots the affinity index, the only number the sheet's segment table carries
		assertThat(jobs).anySatisfy(job -> {
			assertThat(job.seriesCode()).isEqualTo("aud-seg");
			assertThat(job.tacticNum()).isEqualTo(2);
			assertThat(job.slices()).singleElement().satisfies(slice -> {
				assertThat(slice.label()).isEqualTo("In-Market: Luxury Travel");
				assertThat(slice.value()).isEqualTo(142.0);
			});
		});
		assertThat(jobs).anySatisfy(job -> {
			assertThat(job.seriesCode()).isEqualTo("dev");
			assertThat(job.tacticNum()).isEqualTo(1);
			assertThat(job.slices()).singleElement().satisfies(slice -> {
				assertThat(slice.label()).isEqualTo("Mobile");
				assertThat(slice.value()).isEqualTo(1000.0);
			});
		});
	}

	@Test
	void shouldStillSendADeviceJobForATacticWithoutDeviceRowsTest() {
		// Given: a DOOH tactic whose device block is empty
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("dev")));
		GeneratePayload payload = new GeneratePayload(
				"brief", "standard", "", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), selections, "", null, "https://docs.google.com/spreadsheets/d/sheet-1/edit", null);
		Map<Integer, Set<BreakdownType>> resolved = new LinkedHashMap<>();
		resolved.put(1, EnumSet.of(BreakdownType.DEVICE));
		when(breakdownResolver.resolve(selections)).thenReturn(resolved);
		when(sheetHelper.readDeviceTables(eq("https://docs.google.com/spreadsheets/d/sheet-1/edit"), eq(Set.of(1)),
				eq("token"))).thenReturn(Map.of(1, new DeviceTable("", "", "", "", "", List.of())));

		// When:
		helper.buildBreakdownCharts(
				"https://docs.google.com/presentation/d/pres-1/edit", payload, 1, Map.of(), "token");

		// Then: an empty job goes out, which is what lets the chart step remove the master's sample chart
		ArgumentCaptor<BreakdownChartRequest> captor = ArgumentCaptor.forClass(BreakdownChartRequest.class);
		verify(charts).buildBreakdownCharts(captor.capture());
		assertThat(captor.getValue().jobs()).singleElement().satisfies(job -> {
			assertThat(job.seriesCode()).isEqualTo("dev");
			assertThat(job.slices()).isEmpty();
		});
	}

	@Test
	void shouldSkipBreakdownChartsWhenNoAudienceOrDeviceEnabledTest() {
		// Given: only Top Publishers is enabled — a section with no chart
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("tp")));
		GeneratePayload payload = new GeneratePayload(
				"brief", "standard", "", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), selections, "", null, "https://docs.google.com/spreadsheets/d/sheet-1/edit", null);
		Map<Integer, Set<BreakdownType>> resolved = new LinkedHashMap<>();
		resolved.put(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS));
		when(breakdownResolver.resolve(selections)).thenReturn(resolved);

		// When:
		List<String> warnings = helper.buildBreakdownCharts(
				"https://docs.google.com/presentation/d/pres-1/edit", payload, 2, Map.of(), "token");

		// Then: no chart request is built and the sheet is never read for chart data
		assertThat(warnings).isEmpty();
		verify(charts, never()).buildBreakdownCharts(any());
		verify(sheetHelper, never()).readDeviceTables(any(), any(), any());
		verify(sheetHelper, never()).readAudienceTables(any(), any(), any());
	}

	@Test
	void shouldSkipChartsWhenRequiredInputsMissingTest() {
		GeneratePayload payload = new GeneratePayload(
				"brief", "standard", "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, "", null, null, null);

		List<String> warnings = helper.buildCharts(
				"https://docs.google.com/presentation/d/abc/edit",
				payload,
				emptyCampaignData(),
				Map.of(),
				"token"
		);

		assertThat(warnings).isEmpty();
		verify(charts, never()).buildCharts(any());
	}

	@Test
	void shouldReturnSkipWarningWhenPresentationIdMissingTest() {
		GeneratePayload payload = payloadWithChartInputs();

		List<String> warnings = helper.buildCharts(
				"https://example.com/no-id",
				payload,
				emptyCampaignData(),
				placeholderMap(),
				"token"
		);

		assertThat(warnings).containsExactly(
				"Charts skipped — could not determine presentation id from https://example.com/no-id");
	}

	@Test
	void shouldTrimTacticsWhenPresentationIdPresentTest() {
		GeneratePayload payload = payloadWithChartInputs();
		when(effectiveTactics.effectiveTacticCount(payload.sheetRows(), payload.lineItemMapping())).thenReturn(3);

		helper.trimUnusedTactics("https://docs.google.com/presentation/d/deck-id/edit", payload, "token");

		verify(slides).trimTactics(eq("deck-id"), eq(3), eq("standard"), eq(TemplateVariant.STANDARD), eq("token"));
	}

	@Test
	void shouldTrimTacticsForExplicitCountWhenPresentationIdPresentTest() {
		// Given: an explicit tactic count (the "Slides from Sheet" flow has no Media Plan)

		// When: the deck is trimmed by explicit count
		helper.trimUnusedTactics("https://docs.google.com/presentation/d/deck-id/edit", 2, "EOC", TemplateVariant.STANDARD, "token");

		// Then: the count is passed through, without consulting the Media Plan extractor
		verify(slides).trimTactics(eq("deck-id"), eq(2), eq("EOC"), eq(TemplateVariant.STANDARD), eq("token"));
		verifyNoInteractions(tacticExtraction);
	}

	@Test
	void shouldBuildChartsFromSheetPivotsWithoutBigQueryTest() {
		// Given: a sheet grid and placeholders for one CTR tactic, with a reconstructed daily pivot
		List<List<String>> grid = List.of(List.of("{{tactic 1 date}}"));
		Map<String, String> flat = Map.of(
				"{{Campaign_name}}", "Spring",
				"{{tactic 1}}", "Display",
				"{{tactic 1 imps}}", "1,000",
				"{{total imps}}", "1,000");
		when(tacticExtraction.getTacticKpiSeries("Display")).thenReturn("ctr");
		when(reportNumbers.parseReportNumber("1,000")).thenReturn(1000.0);
		LinkedHashMap<String, double[]> series = new LinkedHashMap<>();
		series.put("Jun 1", new double[] {1000.0, 10.0, 0.0});
		SheetChartData chartData = new SheetChartData(
				Map.of(1, new Pivot(series, true, false)),
				Map.of(1, new Pivot(new LinkedHashMap<>(), false, false)));
		when(sheetChartData.read(grid, 1, Map.of(1, "ctr"))).thenReturn(chartData);
		when(charts.buildCharts(any())).thenReturn(List.of());

		// When: charts are built from the sheet
		List<String> warnings = helper.buildChartsFromSheet(
				"https://docs.google.com/presentation/d/deck-id/edit", grid, flat, 1, "token");

		// Then: the request carries the sheet pivots and no BigQuery rows
		assertThat(warnings).isEmpty();
		ArgumentCaptor<ChartRequest> captor = ArgumentCaptor.forClass(ChartRequest.class);
		verify(charts).buildCharts(captor.capture());
		ChartRequest req = captor.getValue();
		assertThat(req.presentationId()).isEqualTo("deck-id");
		assertThat(req.bqRows()).isEmpty();
		assertThat(req.lineItemMapping()).isEmpty();
		assertThat(req.dailyPivots()).containsKey(1);
		assertThat(req.monthlyPivots()).containsKey(1);
		assertThat(req.tacticKpiTypes()).containsEntry(1, "ctr");
		assertThat(req.distTacticNames()).containsEntry(1, "Display");
	}

	@Test
	void shouldReturnSkipWarningWhenPresentationIdMissingForSheetChartsTest() {
		// Given: a slide URL with no parseable presentation id

		// When: sheet charts are requested
		List<String> warnings = helper.buildChartsFromSheet(
				"https://example.com/no-id", List.of(), Map.of(), 1, "token");

		// Then: a skip warning is returned and no chart provider call is made
		assertThat(warnings).containsExactly(
				"Charts skipped — could not determine presentation id from https://example.com/no-id");
		verify(charts, never()).buildCharts(any());
	}

	@Test
	void shouldSkipChartsWhenAnyBigQueryInputIsMissingTest() {
		// Given: payloads each missing one chart input (sheet id, adjustment rows or line-item mapping)
		List<List<String>> adj = List.of(List.of("Label", "Value"));
		List<LineItemMapping> mapping = List.of(new LineItemMapping("Display", "99", 1));
		List<GeneratePayload> payloads = List.of(
				new GeneratePayload("b", "EOC", "", List.of(), adj, List.of(), List.of(), List.of(),
						mapping, null, null, null, null, null),
				new GeneratePayload("b", "EOC", "", List.of(), null, List.of(), List.of(), List.of(),
						mapping, null, "sheet-id", null, null, null),
				new GeneratePayload("b", "EOC", "", List.of(), List.of(), List.of(), List.of(), List.of(),
						mapping, null, "sheet-id", null, null, null),
				new GeneratePayload("b", "EOC", "", List.of(), adj, List.of(), List.of(), List.of(),
						null, null, "sheet-id", null, null, null),
				new GeneratePayload("b", "EOC", "", List.of(), adj, List.of(), List.of(), List.of(),
						List.of(), null, "sheet-id", null, null, null));
		CampaignData data = mock(CampaignData.class);

		// When-Then: every payload is skipped before any collaborator is consulted
		for (GeneratePayload payload : payloads) {
			assertThat(helper.buildCharts("https://docs.google.com/presentation/d/p1/edit", payload, data,
					Map.of(), "token")).isEmpty();
		}
		verifyNoInteractions(charts, effectiveTactics, reportNumbers, tacticExtraction);
	}

	@Test
	void shouldBuildChartsFromBigQueryInputsWithResolvedTacticMapsTest() {
		// Given: two effective tactics, a dashed campaign name falling back to the client name, and an unnamed
		// second tactic
		List<List<String>> sheetRows = List.of(List.of("Media"));
		List<List<String>> adj = List.of(List.of("Label", "Value"));
		List<LineItemMapping> mapping = List.of(new LineItemMapping("Display", "99", 1));
		GeneratePayload payload = new GeneratePayload("b", "EOC", "", sheetRows, adj, List.of(), List.of(),
				List.of(), mapping, null, "sheet-id", null, null, null);
		FlightDates flight = new FlightDates(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
		CampaignData data = mock(CampaignData.class);
		when(data.flightTs()).thenReturn(flight);
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{Campaign_name}}", " — ");
		flat.put("{{client_name}}", " Acme ");
		flat.put("{{tactic 1}}", "Display");
		flat.put("{{tactic 1 imps}}", "500");
		flat.put("{{total imps}}", "1,500");
		when(effectiveTactics.effectiveTacticCount(sheetRows, mapping)).thenReturn(2);
		when(reportNumbers.parseReportNumber("500")).thenReturn(500.0);
		when(reportNumbers.parseReportNumber("1,500")).thenReturn(1500.0);
		when(reportNumbers.parseReportNumber(null)).thenReturn(0.0);
		when(tacticExtraction.getTacticKpiSeries("Display")).thenReturn("ctr");
		when(tacticExtraction.getTacticKpiSeries("Tactic 2")).thenReturn("vcr");
		Map<Integer, String> names = new LinkedHashMap<>();
		names.put(1, "Display");
		names.put(2, "Tactic 2");
		Map<Integer, Double> imps = new LinkedHashMap<>();
		imps.put(1, 500.0);
		imps.put(2, 0.0);
		Map<Integer, String> kpis = new LinkedHashMap<>();
		kpis.put(1, "ctr");
		kpis.put(2, "vcr");
		ChartRequest expected = new ChartRequest(
				"p1", adj, mapping, flight, 2, "Acme", names, imps, 1500.0, kpis, "token", null, null);
		when(charts.buildCharts(expected)).thenReturn(List.of("chart warning"));

		// When:
		List<String> warnings = helper.buildCharts(
				"https://docs.google.com/presentation/d/p1/edit", payload, data, flat, "token");

		// Then: the provider's warnings are returned as-is
		assertThat(warnings).containsExactly("chart warning");
	}

	@Test
	void shouldReportChartFailureWhenBigQueryChartStepThrowsTest() {
		// Given: one effective tactic and a provider that fails
		List<List<String>> adj = List.of(List.of("Label", "Value"));
		List<LineItemMapping> mapping = List.of(new LineItemMapping("Display", "99", 1));
		GeneratePayload payload = new GeneratePayload("b", "EOC", "", List.of(), adj, List.of(), List.of(),
				List.of(), mapping, null, "sheet-id", null, null, null);
		CampaignData data = mock(CampaignData.class);
		when(effectiveTactics.effectiveTacticCount(List.of(), mapping)).thenReturn(0);
		when(tacticExtraction.getTacticKpiSeries("Tactic 1")).thenReturn("ctr");
		ChartRequest expected = new ChartRequest("p1", adj, mapping, null, 1, "Campaign", Map.of(1, "Tactic 1"),
				Map.of(1, 0.0), 0.0, Map.of(1, "ctr"), "token", null, null);
		doThrow(new IllegalStateException("quota")).when(charts).buildCharts(expected);

		// When:
		List<String> warnings = helper.buildCharts(
				"https://docs.google.com/presentation/d/p1/edit", payload, data, Map.of(), "token");

		// Then: the failure is reported as a warning rather than propagated
		assertThat(warnings).containsExactly("Charts failed: quota");
	}

	@Test
	void shouldReportChartFailureWhenSheetChartStepThrowsForANullGridTest() {
		// Given: no grid, a zero tactic count clamped to one, and a failing provider
		Map<Integer, String> kpis = Map.of(1, "ctr");
		when(tacticExtraction.getTacticKpiSeries("Tactic 1")).thenReturn("ctr");
		when(sheetChartData.read(null, 1, kpis)).thenReturn(new SheetChartData(Map.of(), Map.of()));
		ChartRequest expected = new ChartRequest("deck-id", List.of(), List.of(), null, 1, "Campaign",
				Map.of(1, "Tactic 1"), Map.of(1, 0.0), 0.0, kpis, "token", Map.of(), Map.of());
		doThrow(new IllegalStateException("drive down")).when(charts).buildCharts(expected);

		// When:
		List<String> warnings = helper.buildChartsFromSheet(
				"https://docs.google.com/presentation/d/deck-id/edit", null, Map.of(), 0, "token");

		// Then:
		assertThat(warnings).containsExactly("Charts failed: drive down");
	}

	@Test
	void shouldSkipTrimmingWithoutPresentationIdAndSwallowTrimFailureTest() {
		// Given: a provider that fails for the clamped count
		doThrow(new IllegalStateException("slides down"))
				.when(slides).trimTactics("deck-id", 28, "EOC", TemplateVariant.STANDARD, "token");

		// When:
		helper.trimUnusedTactics("not-a-deck", 2, "EOC", TemplateVariant.STANDARD, "token");
		helper.trimUnusedTactics(
				"https://docs.google.com/presentation/d/deck-id/edit", 50, "EOC", TemplateVariant.STANDARD, "token");

		// Then: only the deck with an id is trimmed, and the failure did not propagate
		verify(slides).trimTactics("deck-id", 28, "EOC", TemplateVariant.STANDARD, "token");
		verifyNoMoreInteractions(slides);
	}

	@Test
	void shouldSkipBreakdownSlidesForEmptySelectionsOrMissingPresentationIdTest() {
		// Given:
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("tp")));
		GeneratePayload noSelections = new GeneratePayload("b", "EOC", "", List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(), List.of(), "", null, null, null);
		GeneratePayload withSelections = new GeneratePayload("b", "EOC", "", List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(), selections, "", null, null, null);

		// When:
		helper.addBreakdownSlides(
				"https://docs.google.com/presentation/d/pres-1/edit", noSelections, 2, Map.of(), "token");
		helper.addBreakdownSlides("not-a-deck", withSelections, 2, Map.of(), "token");

		// Then:
		verifyNoInteractions(breakdownResolver, slides);
	}

	@Test
	void shouldDropInvalidAndEmptyTacticSelectionsAndSwallowBreakdownSlideFailureTest() {
		// Given: a null tactic, tactic 0, a tactic with nothing enabled and one valid tactic
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(2, List.of("dev")));
		GeneratePayload payload = new GeneratePayload("b", "EOM", "", List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(), selections, "", null, null, null);
		Map<Integer, Set<BreakdownType>> resolved = new HashMap<>();
		resolved.put(null, EnumSet.of(BreakdownType.DEVICE));
		resolved.put(0, EnumSet.of(BreakdownType.DEVICE));
		resolved.put(1, EnumSet.noneOf(BreakdownType.class));
		resolved.put(2, EnumSet.of(BreakdownType.DEVICE));
		when(breakdownResolver.resolve(selections)).thenReturn(resolved);
		Map<Integer, Set<BreakdownType>> expected = Map.of(2, EnumSet.of(BreakdownType.DEVICE));
		doThrow(new IllegalStateException("slides down")).when(slides).addBreakdownSlides(
				"pres-1", expected, Map.of(), "EOM", TemplateVariant.STANDARD, "token");

		// When:
		helper.addBreakdownSlides("https://docs.google.com/presentation/d/pres-1/edit", payload, 2, Map.of(), "token");

		// Then: only tactic 2 reached the provider and its failure did not propagate
		verify(slides).addBreakdownSlides("pres-1", expected, Map.of(), "EOM", TemplateVariant.STANDARD, "token");
	}

	@Test
	void shouldSkipDeletingMasterSlidesWithoutPresentationIdAndSwallowFailureTest() {
		// Given:
		doThrow(new IllegalStateException("slides down"))
				.when(slides).deleteMasterSlides("deck-id", "EOC", TemplateVariant.MPO, "token");

		// When:
		helper.deleteMasterSlides("not-a-deck", "EOC", TemplateVariant.MPO, "token");
		helper.deleteMasterSlides(
				"https://docs.google.com/presentation/d/deck-id/edit", "EOC", TemplateVariant.MPO, "token");

		// Then:
		verify(slides).deleteMasterSlides("deck-id", "EOC", TemplateVariant.MPO, "token");
		verifyNoMoreInteractions(slides);
	}

	@Test
	void shouldReportDashedPlaceholdersAsSingleWarningTest() {
		// Given: two tokens the pipeline had no value for
		when(slides.dashUnresolvedTokens("deck-id", "EOC", TemplateVariant.STANDARD, "token"))
				.thenReturn(List.of("{{a}}", "{{b}}"));

		// When:
		List<String> warnings = helper.dashUnresolvedTokens(
				"https://docs.google.com/presentation/d/deck-id/edit", "EOC", TemplateVariant.STANDARD, "token");

		// Then:
		assertThat(warnings).containsExactly("The deck carried 2 placeholder(s) the pipeline has no value "
				+ "for; they were replaced with a dash: {{a}}, {{b}}. Either the template marks a cell the report "
				+ "does not fill yet, or the token was renamed on one side only.");
	}

	@Test
	void shouldReturnNoDashWarningWithoutPresentationIdOrWhenDashingFailsTest() {
		// Given:
		doThrow(new IllegalStateException("slides down")).when(slides).applyDeckMarkup("deck-id", "token");

		// When:
		List<String> noId = helper.dashUnresolvedTokens("not-a-deck", "EOC", TemplateVariant.STANDARD, "token");
		List<String> failed = helper.dashUnresolvedTokens(
				"https://docs.google.com/presentation/d/deck-id/edit", "EOC", TemplateVariant.STANDARD, "token");

		// Then:
		assertThat(noId).isEmpty();
		assertThat(failed).isEmpty();
		verify(slides).applyDeckMarkup("deck-id", "token");
		verifyNoMoreInteractions(slides);
	}

	@Test
	void shouldSkipDeletingReportTypeSlidesWithoutPresentationIdAndSwallowFailureTest() {
		// Given:
		doThrow(new IllegalStateException("slides down")).when(slides).deleteReportTypeSlides("deck-id", "EOM", "t");

		// When:
		helper.deleteReportTypeSlides(null, "EOM", "t");
		helper.deleteReportTypeSlides("https://docs.google.com/presentation/d/deck-id/edit", "EOM", "t");

		// Then:
		verify(slides).deleteReportTypeSlides("deck-id", "EOM", "t");
		verifyNoMoreInteractions(slides);
	}

	@Test
	void shouldSkipPaidSlidesFillWithoutPresentationIdOrContentTest() {
		// Given:
		PaidSlidesFill content = new PaidSlidesFill(List.of(), Set.of(PaidChannelKind.SEARCH));
		PaidSlidesFill empty = new PaidSlidesFill(List.of(), Set.of());
		String url = "https://docs.google.com/presentation/d/deck-id/edit";

		// When:
		helper.fillPaidSlides("not-a-deck", content, "token");
		helper.fillPaidSlides(url, null, "token");
		helper.fillPaidSlides(url, empty, "token");

		// Then:
		verifyNoInteractions(slides);
	}

	@Test
	void shouldFillPaidSlidesAndSwallowFailureTest() {
		// Given:
		PaidSlidesFill content = new PaidSlidesFill(List.of(), Set.of(PaidChannelKind.SOCIAL));
		doThrow(new IllegalStateException("slides down")).when(slides).fillPaidSlides("deck-id", content, "token");

		// When:
		helper.fillPaidSlides("https://docs.google.com/presentation/d/deck-id/edit", content, "token");

		// Then:
		verify(slides).fillPaidSlides("deck-id", content, "token");
	}

	@Test
	void shouldSkipPaidChartsWithoutChartsOrPresentationIdTest() {
		// Given:
		List<PaidSlideChart> paid = List.of(
				new PaidSlideChart(PaidChartMarker.SEARCH_SPEND, List.of("Jan"), List.of(1.0), List.of(), null));

		// When:
		List<String> nullCharts = helper.buildPaidCharts("u", null, Map.of(), "token");
		List<String> noCharts = helper.buildPaidCharts("u", List.of(), Map.of(), "token");
		List<String> noId = helper.buildPaidCharts("https://example.com/no-id", paid, Map.of(), "token");

		// Then:
		assertThat(nullCharts).isEmpty();
		assertThat(noCharts).isEmpty();
		assertThat(noId).containsExactly(
				"Paid charts skipped — could not determine presentation id from https://example.com/no-id");
		verifyNoInteractions(charts);
	}

	@Test
	void shouldBuildPaidChartsUnderCampaignTitleTest() {
		// Given:
		List<PaidSlideChart> paid = List.of(
				new PaidSlideChart(PaidChartMarker.SEARCH_SPEND, List.of("Jan"), List.of(1.0), List.of(), null));
		when(charts.buildPaidCharts(new PaidChartRequest("deck-id", "Spring", paid, "token")))
				.thenReturn(List.of("paid warning"));

		// When:
		List<String> warnings = helper.buildPaidCharts("https://docs.google.com/presentation/d/deck-id/edit",
				paid, Map.of("{{Campaign_name}}", "Spring"), "token");

		// Then:
		assertThat(warnings).containsExactly("paid warning");
	}

	@Test
	void shouldReportPaidChartFailureTest() {
		// Given:
		List<PaidSlideChart> paid = List.of(
				new PaidSlideChart(PaidChartMarker.SEARCH_SPEND, List.of("Jan"), List.of(1.0), List.of(), null));
		doThrow(new IllegalStateException("quota"))
				.when(charts).buildPaidCharts(new PaidChartRequest("deck-id", "Campaign", paid, "token"));

		// When:
		List<String> warnings = helper.buildPaidCharts(
				"https://docs.google.com/presentation/d/deck-id/edit", paid, Map.of(), "token");

		// Then:
		assertThat(warnings).containsExactly("Paid charts failed: quota");
	}

	@Test
	void shouldSkipBreakdownChartsWithoutSelectionsOrPresentationIdTest() {
		// Given:
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("aud")));
		GeneratePayload nullSelections = new GeneratePayload("b", "EOC", "", List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(), null, "", null, null, null);
		GeneratePayload emptySelections = new GeneratePayload("b", "EOC", "", List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(), List.of(), "", null, null, null);
		GeneratePayload withSelections = new GeneratePayload("b", "EOC", "", List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(), selections, "", null, null, null);
		String url = "https://docs.google.com/presentation/d/pres-1/edit";

		// When:
		List<String> none = helper.buildBreakdownCharts(url, nullSelections, 2, Map.of(), "token");
		List<String> empty = helper.buildBreakdownCharts(url, emptySelections, 2, Map.of(), "token");
		List<String> noId = helper.buildBreakdownCharts("https://example.com/x", withSelections, 2, Map.of(), "t");

		// Then:
		assertThat(none).isEmpty();
		assertThat(empty).isEmpty();
		assertThat(noId).containsExactly(
				"Breakdown charts skipped — could not determine presentation id from https://example.com/x");
		verifyNoInteractions(breakdownResolver, charts);
	}

	@Test
	void shouldReportBreakdownChartFailureForAudienceOnlyTacticWithoutSegmentsTest() {
		// Given: tactic 1 enables Audience (its sheet block has age rows but no segment rows); a null tactic,
		// tactic 0, tactic 9 (beyond the count) and a tactic with a null section set are ignored
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("aud")));
		GeneratePayload payload = new GeneratePayload("b", "EOC", "", List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(), selections, "", null, "https://sheet", null);
		Map<Integer, Set<BreakdownType>> resolved = new HashMap<>();
		resolved.put(null, EnumSet.of(BreakdownType.AUDIENCE));
		resolved.put(0, EnumSet.of(BreakdownType.AUDIENCE));
		resolved.put(1, EnumSet.of(BreakdownType.AUDIENCE));
		resolved.put(2, null);
		resolved.put(9, EnumSet.of(BreakdownType.DEVICE));
		when(breakdownResolver.resolve(selections)).thenReturn(resolved);
		when(sheetHelper.readAudienceTables("https://sheet", Set.of(1), "token")).thenReturn(Map.of(1,
				new AudienceTable("", "", List.of(new AudienceAgeRow("18-24", "100")), List.of())));
		when(reportNumbers.parseReportNumber("100")).thenReturn(100.0);
		BreakdownChartRequest expected = new BreakdownChartRequest("pres-1", "Campaign",
				List.of(new BreakdownChartJob("aud", 1, List.of(new BreakdownChartSlice("18-24", 100.0)))), "token");
		doThrow(new IllegalStateException("quota")).when(charts).buildBreakdownCharts(expected);

		// When:
		List<String> warnings = helper.buildBreakdownCharts(
				"https://docs.google.com/presentation/d/pres-1/edit", payload, 2, Map.of(), "token");

		// Then: only the age job went out (no segment job, no device job) and the failure became a warning
		assertThat(warnings).containsExactly("Breakdown charts failed: quota");
		verifyNoMoreInteractions(sheetHelper);
	}

	@Test
	void shouldReturnEmptyStringWhenEveryCandidateIsBlankTest() {
		// When-Then: null, blank and dash candidates are all skipped
		assertThat(helper.firstNonBlank(null, "  ", " — ")).isEmpty();
		assertThat(helper.campaignTitle(Map.of("{{client_name}}", "Acme"))).isEqualTo("Acme");
	}

	private static GeneratePayload payloadWithChartInputs() {
		return new GeneratePayload(
				"brief",
				"standard",
				"",
				List.of(List.of("Media"), List.of("Display")),
				List.of(List.of("Label", "Value")),
				List.of(),
				List.of(),
				List.of(),
				List.of(new LineItemMapping("Display", "99", 1)),
				null,
				"sheet-id",
				null,
				null,
				null
		);
	}

	private static CampaignData emptyCampaignData() {
		return new CampaignData(
				"", "", "", "", "",
				new FlightDates(null, null),
				"", "", "", "", "",
				new Totals(0, 0, 0, 0, null, null),
				Map.of(),
				""
		);
	}

	private static Map<String, String> placeholderMap() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("{{Campaign_name}}", "Spring Campaign");
		map.put("{{total imps}}", "1,234");
		map.put("{{tactic 1}}", "Display");
		map.put("{{tactic 1 imps}}", "500");
		return map;
	}
}
