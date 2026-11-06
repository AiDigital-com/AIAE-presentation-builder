package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.service.reports.dto.PaidNarrativeInput;
import com.aidigital.reportconstructor.service.reports.helpers.impl.PaidNarrativeAssemblerImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.PaidSlideChartAssemblerImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.PaidSlideTableAssemblerImpl;
import com.aidigital.reportconstructor.service.reports.engine.PaidValueFormatter;
import com.aidigital.reportconstructor.service.reports.enums.PaidChannelKind;
import com.aidigital.reportconstructor.service.reports.model.PaidChannelData;
import com.aidigital.reportconstructor.service.reports.model.PaidMetrics;
import com.aidigital.reportconstructor.service.reports.model.PaidSlideChart;
import com.aidigital.reportconstructor.service.reports.model.PaidSlidesFill;
import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.diagnostics.impl.ClaudeFailureLogImpl;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.MpoFunnelStage;
import com.aidigital.reportconstructor.service.reports.dto.MpoNarrative;
import com.aidigital.reportconstructor.service.reports.dto.MpoNarrativeInput;
import com.aidigital.reportconstructor.service.reports.dto.MpoProposalPoint;
import com.aidigital.reportconstructor.service.reports.dto.TemplateVariant;
import com.aidigital.reportconstructor.service.reports.enums.NarrativeCopyLimits;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeSheetBatch;
import com.aidigital.reportconstructor.service.reports.dto.GenerationTarget;
import com.aidigital.reportconstructor.service.reports.dto.ProgressView;
import com.aidigital.reportconstructor.service.reports.engine.EomDashboardResolver;
import com.aidigital.reportconstructor.service.reports.engine.RatePlanCalculator;
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSectionInputs;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.helpers.CreativeBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.AudienceBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.DeviceBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.GeoBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.PublisherBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportFileNamer;
import com.aidigital.reportconstructor.service.reports.helpers.ReportResumeStateHelper;
import com.aidigital.reportconstructor.service.reports.helpers.impl.SheetTacticCountHelperImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.BreakdownSelectionResolverImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.BreakdownThoughtsGateImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.DeckStyleTokenHelperImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.SheetRowHelperImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.ImpressionContributionHelperImpl;
import com.aidigital.reportconstructor.service.reports.helpers.PaidTokenHelper;
import com.aidigital.reportconstructor.service.reports.helpers.impl.MpoNarrativeAssemblerImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.PacingNarrativeAssemblerImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.ReportNumberParserImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.TacticConclusionAssemblerImpl;
import com.aidigital.reportconstructor.service.reports.helpers.ReportGenerationChartHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportGenerationWarningsHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.helpers.SheetCampaignReader;
import com.aidigital.reportconstructor.service.reports.helpers.SheetPlaceholderReader;
import com.aidigital.reportconstructor.service.reports.helpers.SheetChartDataReader;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClientFlavors;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import com.aidigital.reportconstructor.service.reports.services.PlaceholderResolverService;
import com.aidigital.reportconstructor.service.reports.services.ReportGenerationService;
import com.aidigital.reportconstructor.service.reports.usage.impl.ClaudeUsageTrackerImpl;
import com.aidigital.reportconstructor.service.reports.usage.impl.NoOpClaudeUsageEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGenerationServiceImplTest {

	@Mock
	ReportJobProgressHelper jobProgress;
	@Mock
	ReportGenerationWarningsHelper warnings;
	@Mock
	ReportGenerationChartHelper chartHelper;
	@Mock
	ReportSheetHelper sheetHelper;
	@Mock
	SheetPlaceholderReader placeholderReader;
	@Mock
	SheetCampaignReader sheetCampaign;
	@Mock
	PlaceholderResolverService placeholders;
	@Mock
	ClaudeClient claude;
	@Mock
	ClaudeClientFlavors claudeClients;
	@Mock
	SheetChartDataReader sheetChartData;
	@Mock
	TacticExtractionHelper tacticExtraction;
	@Mock
	SlidesProvider slides;
	@Mock
	ObjectProvider<UserGoogleTokenProvider> userGoogleTokens;
	@Mock
	ObjectProvider<ReportGenerationService> self;
	@Mock
	ReportClaudeDefaults claudeDefaults;
	@Mock
	ReportFileNamer fileNamer;
	@Mock
	PublisherBreakdownHelper publisherBreakdown;
	@Mock
	CreativeBreakdownHelper creativeBreakdown;
	@Mock
	GeoBreakdownHelper geoBreakdown;
	@Mock
	AudienceBreakdownHelper audienceBreakdown;
	@Mock
	DeviceBreakdownHelper deviceBreakdown;
	@Mock
	ReportResumeStateHelper resumeState;
	@Mock
	PaidTokenHelper paidTokens;

	ReportGenerationServiceImpl service;

	@BeforeEach
	void setUp() {
		lenient().when(claudeClients.forReport(any(), any())).thenReturn(claude);
		service = new ReportGenerationServiceImpl(
				jobProgress, warnings, chartHelper, sheetHelper, publisherBreakdown, creativeBreakdown, geoBreakdown, audienceBreakdown, deviceBreakdown, placeholderReader, sheetCampaign, placeholders,
				claudeClients, slides, userGoogleTokens, self, claudeDefaults, fileNamer,
				new ReportNumberParserImpl(), new Fmt(),
				new EomDashboardResolver(new ReportNumberParserImpl(), new Fmt(), new RatePlanCalculator()), new SimpleAsyncTaskExecutor(),
				new ClaudeUsageTrackerImpl(new NoOpClaudeUsageEventService()), new ClaudeFailureLogImpl(),
				new BreakdownSelectionResolverImpl(), new BreakdownThoughtsGateImpl(),
				new TacticConclusionAssemblerImpl(), sheetChartData, tacticExtraction,
				new ImpressionContributionHelperImpl(new ReportNumberParserImpl(), new Fmt()), resumeState,
				new SheetTacticCountHelperImpl(), new PacingNarrativeAssemblerImpl(), new MpoNarrativeAssemblerImpl(),
				new DeckStyleTokenHelperImpl(new SheetRowHelperImpl()), paidTokens,
				new PaidNarrativeAssemblerImpl(new PaidValueFormatter()),
				new PaidSlideTableAssemblerImpl(new PaidValueFormatter()),
				new PaidSlideChartAssemblerImpl(new PaidValueFormatter()));
	}

	@Test
	void shouldThrowAppExceptionWhenBriefIsBlankTest() {
		GeneratePayload payload = new GeneratePayload(
				"  ", "standard", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, "", null, null, null);

		Throwable thrown = catchThrowable(
				() -> service.start("user-1", "clerk-1", "user@x.com", payload, GenerationTarget.SLIDES, null, null));

		assertThat(thrown)
				.isInstanceOf(AppException.class)
				.hasFieldOrPropertyWithValue("code", ErrorReason.C002.getCode());
	}

	@Test
	void shouldAcceptABlankBriefWhenBuildingSlidesFromTheSheetTest() {
		// Given: a deck run off a reviewed workbook, started without a brief in the payload — the sheet's
		// own {{RFP info}} cell is the campaign context this flow reads, and it wins over the payload anyway
		GeneratePayload payload = new GeneratePayload(
				"  ", "standard", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, "", null, "http://sheet", null);
		ReportJobEntity queued = new ReportJobEntity();
		queued.setId(11L);
		when(jobProgress.createQueuedJob("user-1", "standard")).thenReturn(queued);
		ReportGenerationService selfBean = mock(ReportGenerationService.class);
		when(self.getObject()).thenReturn(selfBean);

		// When: the job is started
		ReportJobEntity job = service.start(
				"user-1", "clerk-1", "user@x.com", payload, GenerationTarget.SLIDES_FROM_SHEET, null, null);

		// Then: it is enqueued and run rather than rejected for a missing brief
		assertThat(job).isSameAs(queued);
		verify(selfBean).run(11L, payload, "clerk-1", "user@x.com", GenerationTarget.SLIDES_FROM_SHEET);
	}

	@Test
	void shouldDelegateEnqueueToJobProgressHelperTest() {
		ReportJobEntity queued = new ReportJobEntity();
		queued.setId(99L);
		queued.setStatus("queued");
		queued.setTotal(7);
		queued.setOwnerUserId("user-1");
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "standard", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, "", null, null, null);
		when(jobProgress.createQueuedJob("user-1", "standard")).thenReturn(queued);

		ReportJobEntity job = service.enqueue("user-1", payload);

		verify(jobProgress).createQueuedJob("user-1", "standard");
		assertThat(job.getId()).isEqualTo(99L);
		assertThat(job.getStatus()).isEqualTo("queued");
		assertThat(job.getTotal()).isEqualTo(7);
	}

	@Test
	void shouldEnqueueAndKickOffAsyncRunOnStartTest() {
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "standard", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, "", null, null, null);
		ReportJobEntity queued = new ReportJobEntity();
		queued.setId(5L);
		when(jobProgress.createQueuedJob("user-1", "standard")).thenReturn(queued);
		ReportGenerationService selfBean = mock(ReportGenerationService.class);
		when(self.getObject()).thenReturn(selfBean);

		ReportJobEntity job = service.start(
				"user-1", "clerk-1", "user@x.com", payload, GenerationTarget.SLIDES, "http://plan", "http://elevate");

		assertThat(job).isSameAs(queued);
		verify(selfBean).run(5L, payload, "clerk-1", "user@x.com", GenerationTarget.SLIDES);
		verify(jobProgress).recordJobContext(5L, "user@x.com", "SLIDES", "http://plan", "http://elevate");
	}

	@Test
	void shouldRunPipelineAndMarkJobDoneWhenClaudeOfflineTest() {
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "standard", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, "", null, null, null);
		when(claude.isLive()).thenReturn(false);
		// Mutable: the pipeline writes the inferred funnel stages and the contribution legend into this map.
		when(placeholders.buildFlatReplacements(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
				.thenReturn(new LinkedHashMap<>());
		when(fileNamer.buildFileName(any(), any(), any())).thenReturn("deck-file");
		when(slides.createDeck(eq("7"), eq("deck-file"), any(), any(), eq(TemplateVariant.STANDARD), isNull())).thenReturn("http://deck");
		when(chartHelper.buildCharts(eq("http://deck"), any(), any(), any(), isNull())).thenReturn(List.of());
		when(warnings.serializeWarnings(List.of())).thenReturn("[]");

		service.run(7L, payload, "clerk-1", "user@x.com", GenerationTarget.SLIDES);

		// The standard deck's copy is written to the standard templates' budgets
		verify(claudeClients).forReport("standard", NarrativeCopyLimits.STANDARD);
		verify(chartHelper).trimUnusedTactics("http://deck", payload, null);
		verify(jobProgress).markJobDone(7L, "http://deck", "[]");
		// The deck placeholder map is bounded to the real tactic count (here the collector yields none, so 1),
		// never the full 28 template slots — this is what keeps createDeck's find-replace from timing out.
		verify(placeholders).buildFlatReplacements(
				any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(1));
	}

	@Test
	void shouldBuildSheetAndSkipChartsWhenTargetIsSheetTest() {
		// Given:
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "standard", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, "", null, null, null);
		when(claude.isLive()).thenReturn(false);
		when(claudeDefaults.emptySheetBatch()).thenReturn(new ClaudeSheetBatch(null, null, Map.of()));
		// Mutable: the pipeline writes the inferred funnel stages and the contribution legend into this map.
		when(placeholders.buildFlatReplacements(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
				.thenReturn(new LinkedHashMap<>());
		when(fileNamer.buildFileName(any(), any(), any())).thenReturn("sheet-file");
		when(sheetHelper.buildSheet("9", "sheet-file", Map.of(), "standard", null)).thenReturn("http://sheet");
		when(sheetHelper.writePacingTables(eq("http://sheet"), eq(payload), any(), eq(Map.of()), isNull()))
				.thenReturn(List.of());
		when(warnings.serializeWarnings(List.of())).thenReturn("[]");

		// When:
		service.run(9L, payload, "clerk-1", "user@x.com", GenerationTarget.SHEET);

		// Then:
		verify(sheetHelper).trimUnusedTactics("http://sheet", payload, null);
		verify(jobProgress).markJobDone(9L, "http://sheet", "[]");
		// The Sheet placeholder map is bounded to the real tactic count too (here the collector yields none,
		// so 1), not all 28 slots — the ~800-request find-replace was the createSheet "Read timed out" cause.
		// Unused slots' leftover tokens are blanked by a single regex pass in createSheet.
		verify(placeholders).buildFlatReplacements(
				any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(1));
		// The sheet's copy is sized for the deck it will feed — the only thing asked of the slides provider
		verify(slides).carriesMpoNarrative("standard", TemplateVariant.STANDARD);
		verifyNoMoreInteractions(slides);
		verifyNoInteractions(chartHelper);
	}

	@Test
	void shouldDigestTheChangeLogSeparatelyAndWriteItIntoTheSheetTest() {
		// Given: a run with both a brief and a change log, and a live model that condenses each of them.
		// The two are digested by separate calls: folded into the brief's prompt, the change log came back
		// dropped as commentary, so its content reached neither the sheet nor any later batch.
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "standard", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), null, "", null, null, "Shifted 20% of Display budget to CTV on Jul 3.");
		when(claude.isLive()).thenReturn(true);
		when(claude.digestBrief("Campaign brief.")).thenReturn("Digested brief.");
		when(claude.digestChangeLog("Shifted 20% of Display budget to CTV on Jul 3."))
				.thenReturn("Jul 3: Display budget moved to CTV.");
		when(claudeDefaults.emptySheetBatch()).thenReturn(new ClaudeSheetBatch(null, null, Map.of()));
		when(claudeDefaults.emptyResults())
				.thenReturn(new ClaudeResults(Map.of(), List.of(), Map.of(), List.of(), null, null, null));
		// Mutable: the pipeline writes the inferred funnel stages and the contribution legend into this map.
		when(placeholders.buildFlatReplacements(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
				.thenReturn(new LinkedHashMap<>());
		when(fileNamer.buildFileName(any(), any(), any())).thenReturn("sheet-file");
		when(sheetHelper.buildSheet("21", "sheet-file", Map.of(), "standard", null)).thenReturn("http://sheet");
		when(sheetHelper.writePacingTables(eq("http://sheet"), eq(payload), any(), eq(Map.of()), isNull()))
				.thenReturn(List.of());
		when(warnings.serializeWarnings(List.of())).thenReturn("[]");

		// When:
		service.run(21L, payload, "clerk-1", "user@x.com", GenerationTarget.SHEET);

		// Then: the brief's own digest call sees the brief alone, and the change log gets its own call
		verify(claude).digestBrief("Campaign brief.");
		verify(claude).digestChangeLog("Shifted 20% of Display budget to CTV on Jul 3.");

		// Then: the sheet is written with the condensed change log beside the condensed brief, so step 2 reads
		// back the exact text this step reasoned over
		ArgumentCaptor<String> briefDigest = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> changeLogDigest = ArgumentCaptor.forClass(String.class);
		verify(placeholders).buildFlatReplacements(
				any(), any(), any(), any(), any(), any(), any(), any(),
				briefDigest.capture(), changeLogDigest.capture(), any(), anyInt());
		assertThat(briefDigest.getValue()).isEqualTo("Digested brief.");
		assertThat(changeLogDigest.getValue()).isEqualTo("Jul 3: Display budget moved to CTV.");
	}

	@Test
	void shouldFillDeckFromSheetWithNarrativeOverlaidBySheetValuesTest() {
		// Given: a step-2 request naming the user-edited sheet, whose grid yields two filled tactics
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "standard", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), null, "", null, "http://sheet", null);
		List<List<String>> grid = List.of(List.of("Tactic name"), List.of("CTV"), List.of("Display"));
		Map<String, String> sheetValues = Map.of(
				"{{client_name}}", "Acme", "{{tactic 1}}", "CTV", "{{tactic 2}}", "Display");
		// Narrative carries a sheet-less recommendation and a client name the sheet must override
		Map<String, String> narrative = Map.of("{{client_name}}", "From Claude", "{{recommendation 1}}", "Do X");
		when(sheetHelper.readSheetGrid("http://sheet", null)).thenReturn(grid);
		when(placeholderReader.readPlaceholders(grid)).thenReturn(sheetValues);
		when(claude.isLive()).thenReturn(false);
		when(placeholders.buildFlatReplacements(
				any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(narrative);
		when(fileNamer.buildFileName(any(), any(), any())).thenReturn("deck-file");
		when(slides.createDeck(eq("11"), eq("deck-file"), any(), any(), eq(TemplateVariant.STANDARD), isNull())).thenReturn("http://deck");
		// Step 2 reads (no Claude call): each section returns no enabled tactics, so no tokens and no writes.
		when(publisherBreakdown.readPublisherInputs(eq("http://sheet"), isNull(), any(), isNull()))
				.thenReturn(new BreakdownSectionInputs<>(Set.of(), Map.of(), Map.of(), List.of()));
		when(creativeBreakdown.readCreativeInputs(eq("http://sheet"), isNull(), any(), isNull()))
				.thenReturn(new BreakdownSectionInputs<>(Set.of(), Map.of(), Map.of(), List.of()));
		when(geoBreakdown.readGeoInputs(eq("http://sheet"), isNull(), any(), isNull()))
				.thenReturn(new BreakdownSectionInputs<>(Set.of(), Map.of(), Map.of(), List.of()));
		when(audienceBreakdown.readAudienceInputs(eq("http://sheet"), isNull(), any(), isNull()))
				.thenReturn(new BreakdownSectionInputs<>(Set.of(), Map.of(), Map.of(), List.of()));
		when(deviceBreakdown.readDeviceInputs(eq("http://sheet"), isNull(), any(), isNull()))
				.thenReturn(new BreakdownSectionInputs<>(Set.of(), Map.of(), Map.of(), List.of()));
		when(publisherBreakdown.writePublisherObservations(any(), any(), any(), any(), any()))
				.thenReturn(List.of());
		when(creativeBreakdown.writeCreativeTakeaways(any(), any(), any(), any(), any())).thenReturn(List.of());
		when(geoBreakdown.writeGeoInsights(any(), any(), any(), any(), any())).thenReturn(List.of());
		when(audienceBreakdown.writeAudienceInsights(any(), any(), any(), any(), any())).thenReturn(List.of());
		when(deviceBreakdown.writeDeviceInsights(any(), any(), any(), any(), any())).thenReturn(List.of());
		when(claudeDefaults.emptyResults())
				.thenReturn(new ClaudeResults(Map.of(), List.of(), Map.of(), List.of(), null, null, null));
		when(chartHelper.buildChartsFromSheet(eq("http://deck"), eq(grid), any(), eq(2), isNull()))
				.thenReturn(List.of());
		when(warnings.serializeWarnings(List.of())).thenReturn("[]");

		// When: the slides-from-sheet job runs
		service.run(11L, payload, "clerk-1", "user@x.com", GenerationTarget.SLIDES_FROM_SHEET);

		// Then: it reconstructs campaign context from the sheet, then merges narrative UNDER the sheet values
		verify(sheetCampaign).read(sheetValues, 2, "standard", null);
		ArgumentCaptor<Map<String, String>> deckMap = ArgumentCaptor.forClass(Map.class);
		verify(slides).createDeck(eq("11"), eq("deck-file"), deckMap.capture(), any(), eq(TemplateVariant.STANDARD), isNull());
		assertThat(deckMap.getValue())
				.containsEntry("{{client_name}}", "Acme")
				.containsEntry("{{recommendation 1}}", "Do X");
		verify(chartHelper).trimUnusedTactics("http://deck", 2, "standard", TemplateVariant.STANDARD, null);
		verify(chartHelper).buildChartsFromSheet(eq("http://deck"), eq(grid), any(), eq(2), isNull());
		verify(jobProgress).markJobDone(11L, "http://deck", "[]");
		// And: the raw-grid collection and the offline Claude batches never run — no duplicate work
		verify(placeholders, never()).collectData(any());
		verify(claude, never()).batchStrategicNarrative(any(), any(), any());
	}

	@Test
	void shouldCloneTheDeckOnTheRequestedMpoDesignTest() {
		// Given: a step-2 request on the MPO design
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "EOC", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), null, "", null, "http://sheet", null, Boolean.TRUE, TemplateVariant.MPO);
		List<List<String>> grid = List.of(List.of("Tactic name"), List.of("CTV"));
		Map<String, String> sheetValues = Map.of("{{client_name}}", "Acme", "{{tactic 1}}", "CTV");
		when(sheetHelper.readSheetGrid("http://sheet", null)).thenReturn(grid);
		when(placeholderReader.readPlaceholders(grid)).thenReturn(sheetValues);
		when(claude.isLive()).thenReturn(false);
		when(slides.carriesMpoNarrative("EOC", TemplateVariant.MPO)).thenReturn(true);
		when(placeholders.buildFlatReplacements(
				any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(Map.of());
		when(fileNamer.buildFileName(any(), any(), any())).thenReturn("deck-file");
		when(slides.createDeck(eq("12"), eq("deck-file"), any(), eq("EOC"), eq(TemplateVariant.MPO), isNull()))
				.thenReturn("http://deck");
		when(publisherBreakdown.readPublisherInputs(eq("http://sheet"), isNull(), any(), isNull()))
				.thenReturn(new BreakdownSectionInputs<>(Set.of(), Map.of(), Map.of(), List.of()));
		when(creativeBreakdown.readCreativeInputs(eq("http://sheet"), isNull(), any(), isNull()))
				.thenReturn(new BreakdownSectionInputs<>(Set.of(), Map.of(), Map.of(), List.of()));
		when(geoBreakdown.readGeoInputs(eq("http://sheet"), isNull(), any(), isNull()))
				.thenReturn(new BreakdownSectionInputs<>(Set.of(), Map.of(), Map.of(), List.of()));
		when(audienceBreakdown.readAudienceInputs(eq("http://sheet"), isNull(), any(), isNull()))
				.thenReturn(new BreakdownSectionInputs<>(Set.of(), Map.of(), Map.of(), List.of()));
		when(deviceBreakdown.readDeviceInputs(eq("http://sheet"), isNull(), any(), isNull()))
				.thenReturn(new BreakdownSectionInputs<>(Set.of(), Map.of(), Map.of(), List.of()));
		when(publisherBreakdown.writePublisherObservations(any(), any(), any(), any(), any()))
				.thenReturn(List.of());
		when(creativeBreakdown.writeCreativeTakeaways(any(), any(), any(), any(), any())).thenReturn(List.of());
		when(geoBreakdown.writeGeoInsights(any(), any(), any(), any(), any())).thenReturn(List.of());
		when(audienceBreakdown.writeAudienceInsights(any(), any(), any(), any(), any())).thenReturn(List.of());
		when(deviceBreakdown.writeDeviceInsights(any(), any(), any(), any(), any())).thenReturn(List.of());
		when(claudeDefaults.emptyResults())
				.thenReturn(new ClaudeResults(Map.of(), List.of(), Map.of(), List.of(), null, null, null));
		when(chartHelper.buildChartsFromSheet(eq("http://deck"), eq(grid), any(), eq(1), isNull()))
				.thenReturn(List.of());
		when(warnings.serializeWarnings(List.of())).thenReturn("[]");

		// When
		service.run(12L, payload, "clerk-1", "user@x.com", GenerationTarget.SLIDES_FROM_SHEET);

		// Then: the provider is asked for the MPO design, and the job completes on the deck it returned
		verify(slides).createDeck(eq("12"), eq("deck-file"), any(), eq("EOC"), eq(TemplateVariant.MPO), isNull());
		// And: the copy is written to the MPO boxes' budgets
		verify(claudeClients).forReport("EOC", NarrativeCopyLimits.COMPACT);
		verify(jobProgress).markJobDone(12L, "http://deck", "[]");
	}

	@Test
	void shouldPickTacticsPastTheBreakdownGateWhenTheDeckCarriesThoughtsTest() {
		// Given: tactic 1 enables three breakdowns, tactic 2 only two, and the deck has a thoughts slide
		List<BreakdownSelection> selections = List.of(
				new BreakdownSelection(1, List.of("tp", "ca", "geo")),
				new BreakdownSelection(2, List.of("aud", "dev")));
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "EOC", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), selections, "", null, "http://sheet", null);
		when(slides.carriesTacticThoughts("EOC", TemplateVariant.STANDARD)).thenReturn(true);

		// When:
		Set<Integer> qualifying = service.thoughtsTactics(payload, 2);

		// Then: only the tactic past the "> 2 breakdowns" gate gets the thoughts call
		assertThat(qualifying).containsExactly(1);
	}

	@Test
	void shouldBackBreakdownTokensWithTheDeckMapTest() {
		// Given: a breakdown map without the tactic tokens the MPO audience slide reuses, and a deck map with them
		Map<String, String> breakdownValues = Map.of("{{aud_1_1}}", "Auto Intenders", "{{tactic 1}}", "DISPLAY");
		Map<String, String> flatReplacements = Map.of(
				"{{tactic 1 reach}}", "372,967", "{{tactic 1 KPI}}", "0.19%", "{{tactic 1}}", "Display");

		// When:
		Map<String, String> merged = service.withDeckFallback(breakdownValues, flatReplacements);

		// Then: the missing tactic tokens come from the deck map, and a breakdown value still wins
		assertThat(merged)
				.containsEntry("{{tactic 1 reach}}", "372,967")
				.containsEntry("{{tactic 1 KPI}}", "0.19%")
				.containsEntry("{{aud_1_1}}", "Auto Intenders")
				.containsEntry("{{tactic 1}}", "DISPLAY");
	}

	@Test
	void shouldPickNoTacticsWhenTheDeckHasNoThoughtsSlideTest() {
		// Given: the same selections, on a template without the thoughts slide
		List<BreakdownSelection> selections = List.of(
				new BreakdownSelection(1, List.of("tp", "ca", "geo")),
				new BreakdownSelection(2, List.of("aud", "dev")));
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "EOC", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), selections, "", null, "http://sheet", null);
		when(slides.carriesTacticThoughts("EOC", TemplateVariant.STANDARD)).thenReturn(false);

		// When:
		Set<Integer> qualifying = service.thoughtsTactics(payload, 2);

		// Then: no thoughts call is paid for
		assertThat(qualifying).isEmpty();
	}

	@Test
	void shouldSkipTheMpoNarrativeOnADeckNotClonedFromAnMpoTemplateTest() {
		// Given: a standard deck, which has no MPO-only slots
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "EOC", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), null, "", null, null, null, null, TemplateVariant.STANDARD);
		when(slides.carriesMpoNarrative("EOC", TemplateVariant.STANDARD)).thenReturn(false);
		Map<String, String> flat = new LinkedHashMap<>(Map.of("{{client_name}}", "Acme"));

		// When:
		service.fillMpoNarrative(claude, flat, 1, payload, true, "brief");

		// Then: the call is never paid for and no MPO token is written
		verify(claude, never()).mpoNarrative(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		assertThat(flat).containsOnlyKeys("{{client_name}}");
	}

	@Test
	void shouldFillThePaidTokensOnAnMpoDeckTest() {
		// Given: an MPO deck with a Paid Search export connected
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "EOC", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), null, "", null, null, null, null, TemplateVariant.MPO,
				Map.of("FULL-FLIGHT PERFORMANCE", List.of(List.of("Date"))), Map.of());
		when(slides.carriesMpoNarrative("EOC", TemplateVariant.MPO)).thenReturn(true);
		Map<String, String> flat = new LinkedHashMap<>();

		// When:
		service.fillPaidTokens(flat, payload);

		// Then: the helper gets the very map and request
		verify(paidTokens).fillPaidTokens(same(flat), same(payload));
	}

	@Test
	void shouldWriteThePaidCopyFromClaudeForEachConnectedChannelTest() {
		// Given: a live EOC run with a parsed Paid Search export, and a reply that only wrote the verdict
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "EOC", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), null, "", null, null, null, null, TemplateVariant.MPO, Map.of(), Map.of());
		PaidChannelData search = new PaidChannelData(PaidChannelKind.SEARCH, new PaidMetrics(1000, 100, 50, 5), null,
				List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of());
		PaidNarrativeInput expectedInput = new PaidNarrativeAssemblerImpl(new PaidValueFormatter())
				.toInput(search, false, "the full flight");
		when(claude.paidNarrative(eq(expectedInput), eq("brief")))
				.thenReturn(Map.of("verdict", "Google Ads delivered 1.2K clicks."));
		Map<String, String> flat = new LinkedHashMap<>(Map.of("{{ps_period_label}}", "the full flight"));

		// When:
		service.fillPaidNarrative(claude, flat, List.of(search), payload, true, "brief");

		// Then: the reply's field is written and every field it left out is dashed, never left raw
		assertThat(flat)
				.containsEntry("{{ps_verdict}}", "Google Ads delivered 1.2K clicks.")
				.containsEntry("{{ps_camp_takeaway_1}}", "—")
				.containsEntry("{{ps_highlight_4_title}}", "—")
				.containsEntry("{{ps_reco_4}}", "—")
				.doesNotContainKey("{{ps_eom_worked}}");
	}

	@Test
	void shouldDashThePaidCopyWithoutCallingClaudeOnADryRunTest() {
		// Given: a dry EOC run with a parsed Paid Social export
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "EOC", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), null, "", null, null, null, null, TemplateVariant.MPO, Map.of(), Map.of());
		PaidChannelData social = new PaidChannelData(PaidChannelKind.SOCIAL, new PaidMetrics(1000, 100, 50, 5), null,
				List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of());
		Map<String, String> flat = new LinkedHashMap<>();

		// When:
		service.fillPaidNarrative(claude, flat, List.of(social), payload, false, "brief");

		// Then:
		verify(claude, never()).paidNarrative(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		assertThat(flat)
				.containsEntry("{{pso_verdict}}", "—")
				.containsEntry("{{pso_pl_take_4_title}}", "—")
				.containsEntry("{{pso_reco_1}}", "—");
	}

	@Test
	void shouldNotParseThePaidExportsOnAStandardDeckTest() {
		// Given: a standard deck that still carries an export (connected before switching tabs)
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "EOC", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), null, "", null, null, null, null, TemplateVariant.STANDARD,
				Map.of("FULL-FLIGHT PERFORMANCE", List.of(List.of("Date"))), Map.of());
		when(slides.carriesMpoNarrative("EOC", TemplateVariant.STANDARD)).thenReturn(false);

		// When:
		service.fillPaidTokens(new LinkedHashMap<>(), payload);

		// Then:
		verifyNoInteractions(paidTokens);
	}

	@Test
	void shouldDeleteBothPaidSectionsOfAnMpoDeckWithoutExportsTest() {
		// Given: an MPO EOC deck generated without a paid export
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "EOC", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), null, "", null, null, null, null, TemplateVariant.MPO, Map.of(), Map.of());
		when(slides.carriesMpoNarrative("EOC", TemplateVariant.MPO)).thenReturn(true);

		// When:
		service.finishPaidSlides("https://docs.google.com/presentation/d/deck1/edit", List.of(), payload, "tok");

		// Then:
		PaidSlidesFill expected = new PaidSlidesFill(List.of(),
				EnumSet.of(PaidChannelKind.SEARCH, PaidChannelKind.SOCIAL));
		verify(chartHelper).fillPaidSlides(
				eq("https://docs.google.com/presentation/d/deck1/edit"), eq(expected), eq("tok"));
	}

	@Test
	void shouldLeaveAStandardDeckWithoutAPaidSectionAloneTest() {
		// Given:
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "EOC", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), null, "", null, null, null, null, TemplateVariant.STANDARD, Map.of(), Map.of());
		when(slides.carriesMpoNarrative("EOC", TemplateVariant.STANDARD)).thenReturn(false);

		// When:
		service.finishPaidSlides("https://docs.google.com/presentation/d/deck1/edit", List.of(), payload, "tok");

		// Then:
		verifyNoInteractions(chartHelper);
	}

	@Test
	void shouldLinkPaidChartsOfEveryChannelOnAnMpoDeckTest() {
		// Given: an MPO EOC deck with a Search export
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "EOC", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), null, "", null, null, null, null, TemplateVariant.MPO, Map.of(), Map.of());
		when(slides.carriesMpoNarrative("EOC", TemplateVariant.MPO)).thenReturn(true);
		PaidChannelData search = new PaidChannelData(PaidChannelKind.SEARCH, new PaidMetrics(1000, 100, 50, 5), null,
				List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of());
		Map<String, String> flat = Map.of("{{campaign_name}}", "Acme");
		List<PaidSlideChart> expected = new PaidSlideChartAssemblerImpl(new PaidValueFormatter())
				.assemble(List.of(search));
		when(chartHelper.buildPaidCharts("https://docs.google.com/presentation/d/deck1/edit", expected, flat, "tok"))
				.thenReturn(List.of("chart warning"));

		// When:
		List<String> result = service.buildPaidCharts(
				"https://docs.google.com/presentation/d/deck1/edit", List.of(search), payload, flat, "tok");

		// Then:
		assertThat(result).containsExactly("chart warning");
	}

	@Test
	void shouldNotBuildPaidChartsOnAStandardDeckTest() {
		// Given:
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "EOC", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), null, "", null, null, null, null, TemplateVariant.STANDARD, Map.of(), Map.of());
		when(slides.carriesMpoNarrative("EOC", TemplateVariant.STANDARD)).thenReturn(false);
		PaidChannelData search = new PaidChannelData(PaidChannelKind.SEARCH, new PaidMetrics(1000, 100, 50, 5), null,
				List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of());

		// When:
		List<String> result = service.buildPaidCharts(
				"https://docs.google.com/presentation/d/deck1/edit", List.of(search), payload, Map.of(), "tok");

		// Then:
		assertThat(result).isEmpty();
		verifyNoInteractions(chartHelper);
	}

	@Test
	void shouldWriteTheMpoNarrativeOnAnMpoDeckTest() {
		// Given: an MPO deck whose map already carries the figures the narrative is written against
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "EOC", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), null, "", null, null, null, null, TemplateVariant.MPO);
		when(slides.carriesMpoNarrative("EOC", TemplateVariant.MPO)).thenReturn(true);
		Map<String, String> flat = new LinkedHashMap<>(Map.of("{{client_name}}", "Acme", "{{tactic 1}}", "CTV"));
		MpoNarrativeInput expectedInput = new MpoNarrativeAssemblerImpl().toInput(flat, 1);
		MpoNarrative narrative = new MpoNarrative("$150K+",
				List.of(new MpoProposalPoint("Long-Lead Awareness", "Stay first choice.")),
				List.of(new MpoFunnelStage("THE DREAM", "98% VCR", "Build the universe.", "1.06M CTV impressions.",
						"The story was heard in full.")),
				"TURNING CURIOSITY INTO BOOKINGS", List.of());
		when(claude.mpoNarrative(eq(expectedInput), eq("brief"))).thenReturn(narrative);

		// When:
		service.fillMpoNarrative(claude, flat, 1, payload, true, "brief");

		// Then: the reply lands on its tokens, and the slots it left empty are dashed rather than raw
		assertThat(flat)
				.containsEntry("{{audience_hhi}}", "$150K+")
				.containsEntry("{{proposal point 1}}", "Long-Lead Awareness")
				.containsEntry("{{funnel stage 1}}", "THE DREAM")
				.containsEntry("{{funnel data 1}}", "1.06M CTV impressions.")
				.containsEntry("{{recommendations headline}}", "TURNING CURIOSITY INTO BOOKINGS")
				.containsEntry("{{proposal point 2}}", "—")
				.containsEntry("{{funnel story 4}}", "—");
	}

	@Test
	void shouldDashTheMpoNarrativeOnADryRunTest() {
		// Given: an MPO deck, but no live Claude client
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "EOC", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), null, "", null, null, null, null, TemplateVariant.MPO);
		when(slides.carriesMpoNarrative("EOC", TemplateVariant.MPO)).thenReturn(true);
		Map<String, String> flat = new LinkedHashMap<>();

		// When:
		service.fillMpoNarrative(claude, flat, 1, payload, false, "brief");

		// Then: no call, and every MPO slot reads as "no figure" instead of its own token name
		verify(claude, never()).mpoNarrative(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		assertThat(flat).containsEntry("{{audience_hhi}}", "—").containsEntry("{{funnel title 3}}", "—");
	}

	@Test
	void shouldMarkJobFailedWhenPipelineThrowsTest() {
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "standard", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, "", null, null, null);
		when(placeholders.collectData(payload)).thenThrow(new RuntimeException("boom"));

		service.run(8L, payload, "clerk-1", "user@x.com", GenerationTarget.SLIDES);

		verify(jobProgress).markJobFailed(8L, "boom");
	}

	@Test
	void shouldAliasReachFactFromReachColumnAndCompactShortTokensTest() {
		// Given: the sheet reader loaded reach_f with the Frequency total (its internal channel for the
		// frequency narrative) and the full campaign reach sits in {{reach}}
		Map<String, String> flat = new HashMap<>();
		flat.put("{{reach}}", "70,001");
		flat.put("{{reach_f}}", "5.383523093");

		// When: the sheet tokens are aliased for the deck
		service.aliasSheetTokens(flat, 0, "EOC");

		// Then: "Market Captured" ({{reach_f}}) is reclaimed from the Reach column, not the Frequency total,
		// and the presentation-short reach tokens render it abbreviated
		assertThat(flat)
				.containsEntry("{{reach_f}}", "70,001")
				.containsEntry("{{reach_p}}", "70k")
				.containsEntry("{{reach_f_pres}}", "70k");
	}

	@Test
	void shouldReturnNullSafeProgressViewTest() {
		ReportJobEntity job = new ReportJobEntity();
		job.setStep(3);
		job.setTotal(7);
		job.setLabel("Building slide deck");
		job.setStatus("running");
		job.setSlideUrl(null);
		job.setErrorMessage(null);
		job.setWarningsJson("[]");
		when(jobProgress.loadJobForOwner("user-1", 9L)).thenReturn(job);
		when(warnings.parseWarnings("[]")).thenReturn(List.of("w1"));

		ProgressView view = service.progress("user-1", 9L);

		assertThat(view.step()).isEqualTo(3);
		assertThat(view.total()).isEqualTo(7);
		assertThat(view.label()).isEqualTo("Building slide deck");
		assertThat(view.status()).isEqualTo("running");
		assertThat(view.slideUrl()).isEmpty();
		assertThat(view.error()).isEmpty();
		assertThat(view.warnings()).containsExactly("w1");
	}

	@Test
	void shouldFillFunnelStagesFromTheReviewedTacticGoalsTest() {
		// Given: a map whose funnel token is an unresolved dash and whose two tactics carry goals
		Map<String, String> flat = new HashMap<>();
		flat.put("{{funnel_stages}}", "—");
		flat.put("{{tactic 1 goal}}", "Build awareness");
		flat.put("{{tactic 2 goal}}", "Drive site visits");
		when(claude.summarizeFunnelStages(List.of("Build awareness", "Drive site visits")))
				.thenReturn("Awareness, Consideration");

		// When:
		service.fillFunnelStages(claude, flat, 2, true);

		// Then: the goals — never the source workbook — produced the funnel line
		assertThat(flat).containsEntry("{{funnel_stages}}", "Awareness, Consideration");
		verify(claude).summarizeFunnelStages(List.of("Build awareness", "Drive site visits"));
	}

	@Test
	void shouldKeepAnAlreadyResolvedFunnelValueWithoutCallingClaudeTest() {
		// Given: a funnel value the media plan or the user already supplied
		Map<String, String> flat = new HashMap<>();
		flat.put("{{funnel_stages}}", "Awareness, Conversion");
		flat.put("{{tactic 1 goal}}", "Build awareness");

		// When:
		service.fillFunnelStages(claude, flat, 1, true);

		// Then: a reviewed value is never overwritten and costs no request
		assertThat(flat).containsEntry("{{funnel_stages}}", "Awareness, Conversion");
		verifyNoInteractions(claude);
	}

	@Test
	void shouldLeaveFunnelStagesUntouchedWhenNoTacticCarriesAGoalTest() {
		// Given: dash-filled goals, which is what unused tactic slots render as
		Map<String, String> flat = new HashMap<>();
		flat.put("{{funnel_stages}}", "—");
		flat.put("{{tactic 1 goal}}", "—");

		// When:
		service.fillFunnelStages(claude, flat, 1, true);

		// Then:
		assertThat(flat).containsEntry("{{funnel_stages}}", "—");
		verifyNoInteractions(claude);
	}
}
