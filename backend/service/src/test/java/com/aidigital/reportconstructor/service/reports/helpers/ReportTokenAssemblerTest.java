package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownValues;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.DateFilter;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.dto.Recommendation;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.TacticConclusion;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughts;
import com.aidigital.reportconstructor.service.reports.engine.EomDashboardResolver;
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportTokenAssemblerTest {

	@Mock
	private Fmt fmt;
	@Mock
	private ReportNumberParser reportNumbers;
	@Mock
	private EomDashboardResolver eomDashboards;
	@Mock
	private ClaudeClient claude;

	@InjectMocks
	private ReportTokenAssembler assembler;

	@Test
	void shouldReadTrimmedTacticNamesSkippingBlankAndMissingSlotsTest() {
		// Given: tactic 1 named with padding, tactic 2 blank, tactic 3 absent
		Map<String, String> flat = new HashMap<>();
		flat.put("{{tactic 1}}", "  Display  ");
		flat.put("{{tactic 2}}", "   ");

		// When:
		Map<Integer, String> names = assembler.tacticNames(flat, 3);

		// Then: only the named tactic is returned, trimmed
		assertThat(names).containsExactly(Map.entry(1, "Display"));
	}

	@Test
	void shouldWriteFiveThoughtsTokensPerQualifyingTacticBlankingMissingOnesTest() {
		// Given: a null entry, a tactic without a thoughts list, a tactic with a null paragraph, and tactic 3
		// qualifying with no thoughts at all and a blank display name, and tactic 5 qualifying with no name entry
		List<TacticThoughts> thoughts = Arrays.asList(
				null,
				new TacticThoughts(2, null),
				new TacticThoughts(1, Arrays.asList("first", null, "third")));
		Set<Integer> qualifying = new LinkedHashSet<>(List.of(1, 3, 5));
		Map<String, String> flat = Map.of("{{tactic 1}}", "CTV", "{{tactic 3}}", " ");
		Map<String, String> breakdownValues = new LinkedHashMap<>();

		// When:
		assembler.writeThoughtsTokens(breakdownValues, qualifying, thoughts, flat);

		// Then: tactic 1 carries its name and its two paragraphs, every other slot is blanked
		Map<String, String> expected = new LinkedHashMap<>();
		expected.put("{{tactic 1}}", "CTV");
		expected.put("{{thoughts on tactic 1 performance 1}}", "first");
		expected.put("{{thoughts on tactic 1 performance 2}}", "");
		expected.put("{{thoughts on tactic 1 performance 3}}", "third");
		expected.put("{{thoughts on tactic 1 performance 4}}", "");
		expected.put("{{thoughts on tactic 1 performance 5}}", "");
		for (int i = 1; i <= 5; i++) {
			expected.put("{{thoughts on tactic 3 performance " + i + "}}", "");
		}
		for (int i = 1; i <= 5; i++) {
			expected.put("{{thoughts on tactic 5 performance " + i + "}}", "");
		}
		assertThat(breakdownValues).containsExactlyEntriesOf(expected);
	}

	@Test
	void shouldMergeOnlyNonBlankTacticOverviewsIntoCampaignResultsTest() {
		// Given: campaign results and conclusions with a null, a blank and a real overview
		List<Recommendation> recommendations = List.of(new Recommendation("Scale CTV", "Move budget."));
		ClaudeResults campaign = new ClaudeResults(
				Map.of(1, "overview"), List.of("thought"), Map.of(), recommendations, "opp", "fact", "story");
		List<TacticConclusion> conclusions = List.of(
				new TacticConclusion(1, null),
				new TacticConclusion(2, "  "),
				new TacticConclusion(3, "Tactic three led."));

		// When:
		ClaudeResults merged = assembler.mergeTacticOverviews(campaign, conclusions);

		// Then: only tactic 3's overview is merged, the campaign-level copy is untouched
		assertThat(merged.tacticOverviews()).containsExactly(Map.entry(3, "Tactic three led."));
		assertThat(merged.resultsOverviews()).isEqualTo(Map.of(1, "overview"));
		assertThat(merged.thoughtsOnPerformance()).containsExactly("thought");
		assertThat(merged.recommendations()).isSameAs(recommendations);
		assertThat(merged.fOpportunity()).isEqualTo("opp");
		assertThat(merged.fFact()).isEqualTo("fact");
		assertThat(merged.fStorytelling()).isEqualTo("story");
	}

	@Test
	void shouldReportCampaignResultsEmptyOnlyWhenAllNarrativeIsMissingTest() {
		// Given: one fully empty batch and three batches each carrying one kind of narrative
		ClaudeResults empty = new ClaudeResults(Map.of(), List.of(), Map.of(1, "x"), List.of(), "o", null, null);
		ClaudeResults withOverview = new ClaudeResults(Map.of(1, "x"), List.of(), Map.of(), List.of(), null, null, null);
		ClaudeResults withThoughts = new ClaudeResults(Map.of(), List.of("t"), Map.of(), List.of(), null, null, null);
		ClaudeResults withRecs = new ClaudeResults(
				Map.of(), List.of(), Map.of(), List.of(new Recommendation("t", "x")), null, null, null);

		// When-Then:
		assertThat(assembler.isCampaignResultsEmpty(empty)).isTrue();
		assertThat(assembler.isCampaignResultsEmpty(withOverview)).isFalse();
		assertThat(assembler.isCampaignResultsEmpty(withThoughts)).isFalse();
		assertThat(assembler.isCampaignResultsEmpty(withRecs)).isFalse();
	}

	@Test
	void shouldDefaultMaxTacticNumberToOneWhenDataOrTacticsAreMissingTest() {
		// Given:
		CampaignData noTactics = mock(CampaignData.class);
		when(noTactics.tactics()).thenReturn(null);
		CampaignData emptyTactics = mock(CampaignData.class);
		when(emptyTactics.tactics()).thenReturn(Map.of());

		// When-Then:
		assertThat(assembler.maxTacticNumber(null)).isEqualTo(1);
		assertThat(assembler.maxTacticNumber(noTactics)).isEqualTo(1);
		assertThat(assembler.maxTacticNumber(emptyTactics)).isEqualTo(1);
	}

	@Test
	void shouldReturnHighestTacticNumberIgnoringNullKeysAndClampingToTemplateMaxTest() {
		// Given: tactic keys a null key, 5 then 2; and a second campaign with tactic 40
		Map<Integer, Tactic> tactics = new LinkedHashMap<>();
		tactics.put(null, null);
		tactics.put(5, null);
		tactics.put(2, null);
		Map<Integer, Tactic> overflow = new HashMap<>();
		overflow.put(40, null);
		CampaignData data = mock(CampaignData.class);
		when(data.tactics()).thenReturn(tactics);
		CampaignData overflowData = mock(CampaignData.class);
		when(overflowData.tactics()).thenReturn(overflow);

		// When:
		int max = assembler.maxTacticNumber(data);
		int clamped = assembler.maxTacticNumber(overflowData);

		// Then:
		assertThat(max).isEqualTo(5);
		assertThat(clamped).isEqualTo(28);
	}

	@Test
	void shouldStripRawGridsButKeepNarrativeInputsWhenBuildingNarrativeOnlyPayloadTest() {
		// Given: a payload carrying raw grids and narrative inputs
		DateFilter dateFilter = new DateFilter(null, null, null);
		GeneratePayload payload = new GeneratePayload(
				"brief", "EOM", "1000", List.of(List.of("a")), List.of(List.of("b")), List.of(List.of("c")),
				List.of(List.of("d")), List.of(List.of("e")), List.of(new LineItemMapping("Display", "9", 1)),
				List.of(), "bq-sheet", dateFilter, "https://sheet", "log", Boolean.FALSE);

		// When:
		GeneratePayload narrative = assembler.narrativeOnly(payload);

		// Then:
		assertThat(narrative.brief()).isEqualTo("brief");
		assertThat(narrative.reportType()).isEqualTo("EOM");
		assertThat(narrative.marketVolume()).isNull();
		assertThat(narrative.sheetRows()).isEmpty();
		assertThat(narrative.adjRows()).isEmpty();
		assertThat(narrative.audienceRows()).isEmpty();
		assertThat(narrative.estimatesRows()).isEmpty();
		assertThat(narrative.geoRows()).isEmpty();
		assertThat(narrative.lineItemMapping()).isNull();
		assertThat(narrative.breakdownSelections()).isNull();
		assertThat(narrative.bqSheetId()).isNull();
		assertThat(narrative.dateFilter()).isSameAs(dateFilter);
		assertThat(narrative.sheetUrl()).isEqualTo("https://sheet");
		assertThat(narrative.changeLog()).isEqualTo("log");
		assertThat(narrative.estimateDaypartGender()).isFalse();
	}

	@Test
	void shouldBuildBreakdownDigestSkippingEmptySectionsAndBlankValuesTest() {
		// Given: a null section, a section with no value map, and a section with null/blank/marked/long values
		Map<String, String> values = new LinkedHashMap<>();
		values.put("{{tp_1_1}}", null);
		values.put("{{tp_1_2}}", "  ");
		values.put("{{tp_1_3}}", " [[Lead phrase]] carries the rest ");
		values.put("{{tp_1_4}}", "x".repeat(200));
		List<BreakdownValues> sections = Arrays.asList(
				null, new BreakdownValues(null, List.of()), new BreakdownValues(values, List.of()));

		// When:
		List<String> digest = assembler.buildBreakdownDigest(sections);

		// Then: markers are stripped and the long line is capped at 160 characters
		assertThat(digest).hasSize(2);
		assertThat(digest.get(0)).isEqualTo("tp_1_3: Lead phrase carries the rest");
		assertThat(digest.get(1)).hasSize(160).startsWith("tp_1_4: xxx");
	}

	@Test
	void shouldCapBreakdownDigestAtEightyLinesTest() {
		// Given: two sections whose combined lines exceed the digest cap
		Map<String, String> first = new LinkedHashMap<>();
		for (int i = 0; i < 85; i++) {
			first.put("{{dev_" + i + "}}", "line " + i);
		}
		Map<String, String> second = Map.of("{{geo_1}}", "never read");
		List<BreakdownValues> sections = List.of(
				new BreakdownValues(first, List.of()), new BreakdownValues(second, List.of()));

		// When:
		List<String> digest = assembler.buildBreakdownDigest(sections);

		// Then: the digest stops at 80 lines, before the second section
		assertThat(digest).hasSize(80);
		assertThat(digest.getLast()).isEqualTo("dev_79: line 79");
	}

	@Test
	void shouldNotCallClaudeForFunnelStagesWhenClientIsNotLiveTest() {
		// Given:
		Map<String, String> flat = new HashMap<>(Map.of("{{tactic 1 goal}}", "Awareness"));

		// When:
		assembler.fillFunnelStages(claude, flat, 1, false);

		// Then:
		verifyNoInteractions(claude);
		assertThat(flat).doesNotContainKey("{{funnel_stages}}");
	}

	@Test
	void shouldKeepAnExistingFunnelStagesValueTest() {
		// Given: a reviewed funnel-stages value
		Map<String, String> flat = new HashMap<>();
		flat.put("{{funnel_stages}}", "Upper, Mid");
		flat.put("{{tactic 1 goal}}", "Awareness");

		// When:
		assembler.fillFunnelStages(claude, flat, 1, true);

		// Then:
		verifyNoInteractions(claude);
		assertThat(flat).containsEntry("{{funnel_stages}}", "Upper, Mid");
	}

	@Test
	void shouldFillDashedFunnelStagesFromNonBlankTacticGoalsTest() {
		// Given: the token is a dash, and the goals mix a real value with dash, blank and missing ones
		Map<String, String> flat = new HashMap<>();
		flat.put("{{funnel_stages}}", " — ");
		flat.put("{{tactic 1 goal}}", " Awareness ");
		flat.put("{{tactic 2 goal}}", "—");
		flat.put("{{tactic 3 goal}}", "  ");
		when(claude.summarizeFunnelStages(List.of("Awareness"))).thenReturn("  Upper Funnel  ");

		// When:
		assembler.fillFunnelStages(claude, flat, 4, true);

		// Then: only the real goal is sent and the trimmed answer is written
		assertThat(flat).containsEntry("{{funnel_stages}}", "Upper Funnel");
	}

	@Test
	void shouldSkipFunnelStagesCallWhenNoTacticCarriesAGoalTest() {
		// Given: a blank token and no goals
		Map<String, String> flat = new HashMap<>(Map.of("{{funnel_stages}}", " "));

		// When:
		assembler.fillFunnelStages(claude, flat, 2, true);

		// Then:
		verifyNoInteractions(claude);
		assertThat(flat).containsEntry("{{funnel_stages}}", " ");
	}

	@Test
	void shouldLeaveFunnelStagesUnsetWhenClaudeAnswersNullOrBlankTest() {
		// Given: two runs whose Claude answer is null and blank respectively
		Map<String, String> nullAnswer = new HashMap<>(Map.of("{{tactic 1 goal}}", "Awareness"));
		Map<String, String> blankAnswer = new HashMap<>(Map.of("{{tactic 1 goal}}", "Conversion"));
		when(claude.summarizeFunnelStages(List.of("Awareness"))).thenReturn(null);
		when(claude.summarizeFunnelStages(List.of("Conversion"))).thenReturn("   ");

		// When:
		assembler.fillFunnelStages(claude, nullAnswer, 1, true);
		assembler.fillFunnelStages(claude, blankAnswer, 1, true);

		// Then:
		assertThat(nullAnswer).doesNotContainKey("{{funnel_stages}}");
		assertThat(blankAnswer).doesNotContainKey("{{funnel_stages}}");
	}

	@Test
	void shouldCombineBriefWithChangeLogOnlyWhenTheLogHasContentTest() {
		// When-Then:
		assertThat(assembler.combineBriefWithChangeLog(null, null)).isEmpty();
		assertThat(assembler.combineBriefWithChangeLog("Brief", "   ")).isEqualTo("Brief");
		assertThat(assembler.combineBriefWithChangeLog("Brief", " Paused CTV ")).isEqualTo(
				"Brief\n\n=== MID-FLIGHT CHANGES / CHANGE LOG ===\nPaused CTV");
	}

	@Test
	void shouldAliasSheetTokensAndFillEomDashboardsForEomReportsTest() {
		// Given: sheet values for spend, reach and tactic 1 completions; tactic 2 has none
		Map<String, String> flat = new HashMap<>();
		flat.put("{{total_investment}}", "$1,000");
		flat.put("{{reach}}", "70,000");
		flat.put("{{tactic 1 complitions}}", "500");
		when(reportNumbers.parseReportNumber("70,000")).thenReturn(70000.0);
		when(fmt.compact(70000.0)).thenReturn("70k");

		// When:
		assembler.aliasSheetTokens(flat, 2, "EOM");

		// Then:
		assertThat(flat).containsEntry("{{total spend}}", "$1,000")
				.containsEntry("{{reach_f}}", "70,000")
				.containsEntry("{{reach_p}}", "70k")
				.containsEntry("{{reach_f_pres}}", "70k")
				.containsEntry("{{tactic 1 completions}}", "500")
				.doesNotContainKey("{{tactic 2 completions}}");
		verify(eomDashboards).fill(same(flat), eq(2));
	}

	@Test
	void shouldLeaveAliasesUnsetAndSkipDashboardsWhenSourcesBlankForEocReportsTest() {
		// Given: a blank investment and no reach
		Map<String, String> flat = new HashMap<>(Map.of("{{total_investment}}", " "));

		// When:
		assembler.aliasSheetTokens(flat, 1, "EOC");

		// Then:
		assertThat(flat).containsOnlyKeys("{{total_investment}}");
		verifyNoInteractions(eomDashboards, reportNumbers, fmt);
	}

	@Test
	void shouldLeaveCompactDestinationUntouchedWhenSourceIsBlankTest() {
		// Given:
		Map<String, String> flat = new HashMap<>(Map.of("{{reach}}", "  ", "{{reach_p}}", "keep"));

		// When:
		assembler.compactToken(flat, "{{reach}}", "{{reach_p}}");

		// Then:
		assertThat(flat).containsEntry("{{reach_p}}", "keep");
		verifyNoInteractions(fmt, reportNumbers);
	}

	@Test
	void shouldDescribeTacticsAsOneLineSummaryTest() {
		// Given: two sheet-reconstructed tactics
		Map<Integer, Tactic> tactics = new LinkedHashMap<>();
		Tactic display = new Tactic(
				"Display", "Programmatic", "li-1", 100.0, 2000.0, 0, 0, null, null, null, null, null, null, null,
				null, null, null, null, null);
		Tactic ctv = new Tactic(
				"CTV", "Video", "li-2", 50.5, 300.0, 0, 0, null, null, null, null, null, null, null,
				null, null, null, null, null);
		tactics.put(1, display);
		tactics.put(2, ctv);
		CampaignData data = mock(CampaignData.class);
		when(data.tactics()).thenReturn(tactics);

		// When:
		String description = assembler.describeTactics(data);

		// Then:
		assertThat(description).isEqualTo("1=Display spend=100.0 imps=2000.0; 2=CTV spend=50.5 imps=300.0");
	}

	@Test
	void shouldDescribeNoTacticsAsEmptyStringTest() {
		// Given:
		CampaignData noTactics = mock(CampaignData.class);
		when(noTactics.tactics()).thenReturn(null);

		// When-Then:
		assertThat(assembler.describeTactics(null)).isEmpty();
		assertThat(assembler.describeTactics(noTactics)).isEmpty();
	}
}
