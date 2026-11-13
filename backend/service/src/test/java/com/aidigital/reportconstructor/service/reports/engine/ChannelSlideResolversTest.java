package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelSlideResolversTest {

	private ChannelSlideResolvers resolvers;

	@BeforeEach
	void setUp() {
		SheetRowHelper sheetUtils = ReportsEngineTestSupport.sheetRowHelper();
		TacticResolvers tacticResolvers = new TacticResolvers(sheetUtils, new Fmt(),
				ReportsEngineTestSupport.tacticExtractionHelper(),
				new CampaignResolvers(sheetUtils, new Fmt(), ReportsEngineTestSupport.tacticExtractionHelper(),
						new RatePlanCalculator()),
				new RatePlanCalculator());
		resolvers = new ChannelSlideResolvers(sheetUtils, new Fmt(), tacticResolvers,
				ReportsEngineTestSupport.reportNumberParser());
	}

	/**
	 * One tactic pacing ahead of its month's goal: 512,300 of 500,000 impressions on a $6,000 monthly
	 * budget, against a flight booked for 1,500,000 impressions, 1,800 clicks, 400,000 reach and $18,000.
	 */
	private CampaignData campaign(double factImps, double factSpend) {
		Tactic tactic = new Tactic(
				"CTV", "Video", null,
				factSpend, factImps, 769, 0,
				0.15, null, null, null,
				6_000.0, 500_000.0, 0.12, null, 10.0,
				null, null, null,
				600.0, null, 2.0, 400_000.0,
				18_000.0, 1_500_000.0, 1_800.0);
		return new CampaignData(
				null, null, null, null, null,
				new FlightDates(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)),
				null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, tactic), null, null, null, null, null, null);
	}

	@Test
	void shouldFillTheImpressionsRowFromMonthPlanAndFlightGoalTest() {
		// Given: a tactic pacing at 102% of its monthly impressions goal
		CampaignData data = campaign(512_300, 5_994);

		// When: the impressions row is resolved
		Resolved pacing = resolvers.resolveImpsPacing(1, List.of(), List.of(), data);
		Resolved flightGoal = resolvers.resolveEocPlannedImps(1, List.of(), List.of(), data);
		Resolved projection = resolvers.resolveProjImps(1, List.of(), List.of(), data);

		// Then: the month's pace is a whole percentage and the flight goal carries forward at that pace
		assertThat(pacing.value()).isEqualTo("102%");
		assertThat(flightGoal.value()).isEqualTo("1,500,000");
		assertThat(projection.value()).isEqualTo("1,536,900");
	}

	@Test
	void shouldFloorAnUnderPacingProjectionAtItsFlightGoalTest() {
		// Given: a tactic that delivered only 400,000 of its 500,000 monthly impressions
		CampaignData data = campaign(400_000, 5_994);

		// When:
		Resolved pacing = resolvers.resolveImpsPacing(1, List.of(), List.of(), data);
		Resolved projection = resolvers.resolveProjImps(1, List.of(), List.of(), data);

		// Then: the projection restates the flight goal rather than dropping below it — a deliberate floor,
		// so the end-of-campaign column never projects a shortfall
		assertThat(pacing.value()).isEqualTo("80%");
		assertThat(projection.value()).isEqualTo("1,500,000");
	}

	@Test
	void shouldFillTheCtrRowAsPointsAndAHalvedProjectionTest() {
		// Given: a tactic beating its 0.12% CTR goal with 0.15%
		CampaignData data = campaign(512_300, 5_994);

		// When:
		Resolved pacing = resolvers.resolveCtrPacing(1, List.of(), List.of(), data);
		Resolved projection = resolvers.resolveCtrProj(1, List.of(), List.of(), data);

		// Then: the variance is signed percentage points and the projection sits midway to the goal
		assertThat(pacing.value()).isEqualTo("+0.03pp");
		assertThat(projection.value()).isEqualTo("0.14%");
	}

	@Test
	void shouldFillTheClicksRowFromTheMediaPlansOwnClickGoalTest() {
		// Given: 769 clicks against a 600-click month and an 1,800-click flight
		CampaignData data = campaign(512_300, 5_994);

		// When:
		Resolved pacing = resolvers.resolveClicksPacing(1, List.of(), List.of(), data);
		Resolved flightGoal = resolvers.resolveClicksMp(1, List.of(), List.of(), data);
		Resolved projection = resolvers.resolveClicksProj(1, List.of(), List.of(), data);

		// Then: the flight goal is the media plan's Clicks column, carried forward at the clicks pace
		assertThat(pacing.value()).isEqualTo("128%");
		assertThat(flightGoal.value()).isEqualTo("1,800");
		assertThat(projection.value()).isEqualTo("2,307");
	}

	@Test
	void shouldFillTheCpmRowWithSignedVarianceAndMidpointProjectionTest() {
		// Given: a $6,000 / 500,000 plan delivering 512,300 impressions for $5,994
		CampaignData data = campaign(512_300, 5_994);

		// When:
		Resolved planned = resolvers.resolvePlannedCpm(1, List.of(), List.of(), data);
		Resolved actual = resolvers.resolveFactCpm(1, List.of(), List.of(), data);
		Resolved pacing = resolvers.resolveCpmPacing(1, List.of(), List.of(), data);
		Resolved projection = resolvers.resolveCpmProj(1, List.of(), List.of(), data);

		// Then: the variance is planned − delivered, so cheaper than plan reads as a plus
		assertThat(planned.value()).isEqualTo("$12.00");
		assertThat(actual.value()).isEqualTo("$11.70");
		assertThat(pacing.value()).isEqualTo("+ $0.30");
		assertThat(projection.value()).isEqualTo("$11.85");
	}

	@Test
	void shouldFillTheSpendRowWithTheFlightBudgetInBothEndOfCampaignColumnsTest() {
		// Given: $5,994 spent of a $6,000 month, on an $18,000 flight
		CampaignData data = campaign(512_300, 5_994);

		// When:
		Resolved pacing = resolvers.resolveBudgetPacing(1, List.of(), List.of(), data);
		Resolved flightGoal = resolvers.resolveSpendPlanEoc(1, List.of(), List.of(), data);
		Resolved projection = resolvers.resolveSpendProj(1, List.of(), List.of(), data);

		// Then: a flight spends what it was booked for, so the projection restates the goal
		assertThat(pacing.value()).isEqualTo("100%");
		assertThat(flightGoal.value()).isEqualTo("$18,000.00");
		assertThat(projection.value()).isEqualTo("$18,000.00");
	}

	@Test
	void shouldFillTheReachRowAgainstThePlannedWeeklyFrequencyTest() {
		// Given: 512,300 impressions over a 30-day window at a planned 2 exposures per week
		CampaignData data = campaign(512_300, 5_994);

		// When:
		Resolved plan = resolvers.resolveReachPlan(1, List.of(), List.of(), data);
		Resolved flightGoal = resolvers.resolveReachPlanEoc(1, List.of(), List.of(), data);
		Resolved projection = resolvers.resolveReachProj(1, List.of(), List.of(), data);

		// Then: the month's goal is the reach those impressions imply at the planned frequency, while both
		// end-of-campaign columns carry the media plan's own Reach figure
		assertThat(plan.value()).isEqualTo("59,768");
		assertThat(flightGoal.value()).isEqualTo("400,000");
		assertThat(projection.value()).isEqualTo("400,000");
	}

	@Test
	void shouldDashEveryCellWhenTheTacticHasNoPlanTest() {
		// Given: a campaign with no data for the tactic at all
		CampaignData empty = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);

		// When / Then: every cell prints the table's dash rather than an empty or infinite value
		assertThat(resolvers.resolveImpsPacing(1, List.of(), List.of(), empty).value()).isEqualTo("—");
		assertThat(resolvers.resolveProjImps(1, List.of(), List.of(), empty).value()).isEqualTo("—");
		assertThat(resolvers.resolveCtrPacing(1, List.of(), List.of(), empty).value()).isEqualTo("—");
		assertThat(resolvers.resolveClicksMp(1, List.of(), List.of(), empty).value()).isEqualTo("—");
		assertThat(resolvers.resolveReachPlan(1, List.of(), List.of(), empty).value()).isEqualTo("—");
		assertThat(resolvers.resolveFactCpm(1, List.of(), List.of(), empty).value()).isEqualTo("—");
		assertThat(resolvers.resolveSpendProj(1, List.of(), List.of(), empty).value()).isEqualTo("—");
	}

	@Test
	void shouldPreferAManualAdjustmentOverrideTest() {
		// Given: the Adjustments tab pins the flight impressions goal
		List<List<String>> adj = List.of(List.of("Tactic 1 eoc planned imps:", "2,000,000"));

		// When:
		Resolved r = resolvers.resolveEocPlannedImps(1, List.of(), adj, campaign(512_300, 5_994));

		// Then: the override wins over the media plan's own figure
		assertThat(r.source()).isEqualTo("adj");
		assertThat(r.value()).isEqualTo("2,000,000");
	}

	@Test
	void shouldTakeEveryCellFromTheMediaPlanWhenItCarriesTheLabelTest() {
		// Given: the Media Plan pins every cell of the table and the Adjustments tab is empty
		ChannelSlideResolvers channelSlide = ReportsEngineTestSupport.channelSlideResolvers();
		List<List<String>> sheet = List.of(
				List.of("Tactic 1 imps pacing:", "v1"),
				List.of("Tactic 1 eoc planned imps:", "v2"),
				List.of("Tactic 1 proj imps:", "v3"),
				List.of("Tactic 1 ctr pacing:", "v4"),
				List.of("Tactic 1 ctr proj:", "v5"),
				List.of("Tactic 1 clicks pacing:", "v6"),
				List.of("Tactic 1 clicks mp:", "v7"),
				List.of("Tactic 1 clicks proj:", "v8"),
				List.of("Tactic 1 reach plan:", "v9"),
				List.of("Tactic 1 reach pacing:", "v10"),
				List.of("Tactic 1 reach plan eoc:", "v11"),
				List.of("Tactic 1 reach proj:", "v12"),
				List.of("Tactic 1 planned cpm:", "v13"),
				List.of("Tactic 1 fact cpm:", "v14"),
				List.of("Tactic 1 cpm pacing:", "v15"),
				List.of("Tactic 1 cpm proj:", "v16"),
				List.of("Tactic 1 budget pacing:", "v17"),
				List.of("Tactic 1 spend plan eoc:", "v18"),
				List.of("Tactic 1 spend proj:", "v19"));
		List<List<String>> adj = List.of();

		// When:
		List<Resolved> resolved = List.of(
				channelSlide.resolveImpsPacing(1, sheet, adj, null),
				channelSlide.resolveEocPlannedImps(1, sheet, adj, null),
				channelSlide.resolveProjImps(1, sheet, adj, null),
				channelSlide.resolveCtrPacing(1, sheet, adj, null),
				channelSlide.resolveCtrProj(1, sheet, adj, null),
				channelSlide.resolveClicksPacing(1, sheet, adj, null),
				channelSlide.resolveClicksMp(1, sheet, adj, null),
				channelSlide.resolveClicksProj(1, sheet, adj, null),
				channelSlide.resolveReachPlan(1, sheet, adj, null),
				channelSlide.resolveReachPacing(1, sheet, adj, null),
				channelSlide.resolveReachPlanEoc(1, sheet, adj, null),
				channelSlide.resolveReachProj(1, sheet, adj, null),
				channelSlide.resolvePlannedCpm(1, sheet, adj, null),
				channelSlide.resolveFactCpm(1, sheet, adj, null),
				channelSlide.resolveCpmPacing(1, sheet, adj, null),
				channelSlide.resolveCpmProj(1, sheet, adj, null),
				channelSlide.resolveBudgetPacing(1, sheet, adj, null),
				channelSlide.resolveSpendPlanEoc(1, sheet, adj, null),
				channelSlide.resolveSpendProj(1, sheet, adj, null));

		// Then:
		assertThat(resolved).extracting(Resolved::value).containsExactly(
				"v1", "v2", "v3", "v4", "v5", "v6", "v7", "v8", "v9", "v10",
				"v11", "v12", "v13", "v14", "v15", "v16", "v17", "v18", "v19");
		assertThat(resolved).extracting(Resolved::source).containsOnly("sheet");
	}

	@Test
	void shouldDashCtrPacingAndProjectTheGoalWhenTheActualCtrIsMissingTest() {
		// Given: a tactic with a 0.12% CTR goal but no measured CTR
		ChannelSlideResolvers channelSlide = ReportsEngineTestSupport.channelSlideResolvers();
		Tactic tactic = new Tactic(
				"CTV", "Video", null,
				5_994, 512_300, 0, 0,
				null, null, null, null,
				6_000.0, 500_000.0, 0.12, null, null,
				null, null, null,
				null, null, null, null,
				null, null, null);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(1, tactic), null, null, null, null, null, null);

		// When:
		Resolved pacing = channelSlide.resolveCtrPacing(1, List.of(), List.of(), data);
		Resolved projection = channelSlide.resolveCtrProj(1, List.of(), List.of(), data);

		// Then:
		assertThat(pacing).isEqualTo(new Resolved("Tactic 1 ctr pacing:", "—", "not_found"));
		assertThat(projection).isEqualTo(new Resolved("Tactic 1 ctr proj: (auto)", "0.12%", "adj"));
	}

	@Test
	void shouldDashTheCtrRowWhenTheTacticHasNoCtrGoalTest() {
		// Given: a tactic with a measured 0.15% CTR but no CTR goal
		ChannelSlideResolvers channelSlide = ReportsEngineTestSupport.channelSlideResolvers();
		Tactic tactic = new Tactic(
				"CTV", "Video", null,
				5_994, 512_300, 769, 0,
				0.15, null, null, null,
				6_000.0, 500_000.0, null, null, null,
				null, null, null,
				null, null, null, null,
				null, null, null);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(1, tactic), null, null, null, null, null, null);

		// When:
		Resolved pacing = channelSlide.resolveCtrPacing(1, List.of(), List.of(), data);
		Resolved projection = channelSlide.resolveCtrProj(1, List.of(), List.of(), data);

		// Then:
		assertThat(pacing.value()).isEqualTo("—");
		assertThat(projection.value()).isEqualTo("—");
		assertThat(projection.source()).isEqualTo("not_found");
	}

	@Test
	void shouldShowANegativeCtrVarianceAndKeepTheGoalAsProjectionTest() {
		// Given: a tactic missing its 0.12% CTR goal with 0.10%
		ChannelSlideResolvers channelSlide = ReportsEngineTestSupport.channelSlideResolvers();
		Tactic tactic = new Tactic(
				"CTV", "Video", null,
				5_994, 512_300, 512, 0,
				0.10, null, null, null,
				6_000.0, 500_000.0, 0.12, null, null,
				null, null, null,
				null, null, null, null,
				null, null, null);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(1, tactic), null, null, null, null, null, null);

		// When:
		Resolved pacing = channelSlide.resolveCtrPacing(1, List.of(), List.of(), data);
		Resolved projection = channelSlide.resolveCtrProj(1, List.of(), List.of(), data);

		// Then: an under-delivering CTR never projects below its goal
		assertThat(pacing.value()).isEqualTo("-0.02pp");
		assertThat(projection.value()).isEqualTo("0.12%");
	}

	@Test
	void shouldDashTheRemainingCellsWhenTheTacticIsMissingTest() {
		// Given: campaign data that carries no tactic 1
		ChannelSlideResolvers channelSlide = ReportsEngineTestSupport.channelSlideResolvers();
		CampaignData empty = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);

		// When:
		List<Resolved> resolved = List.of(
				channelSlide.resolveEocPlannedImps(1, List.of(), List.of(), empty),
				channelSlide.resolveCtrProj(1, List.of(), List.of(), empty),
				channelSlide.resolveClicksPacing(1, List.of(), List.of(), empty),
				channelSlide.resolveClicksProj(1, List.of(), List.of(), empty),
				channelSlide.resolveReachPacing(1, List.of(), List.of(), empty),
				channelSlide.resolveReachPlanEoc(1, List.of(), List.of(), empty),
				channelSlide.resolveReachProj(1, List.of(), List.of(), empty),
				channelSlide.resolvePlannedCpm(1, List.of(), List.of(), empty),
				channelSlide.resolveCpmPacing(1, List.of(), List.of(), empty),
				channelSlide.resolveCpmProj(1, List.of(), List.of(), empty),
				channelSlide.resolveBudgetPacing(1, List.of(), List.of(), empty),
				channelSlide.resolveSpendPlanEoc(1, List.of(), List.of(), empty));

		// Then:
		assertThat(resolved).extracting(Resolved::value).containsOnly("—");
		assertThat(resolved).extracting(Resolved::source).containsOnly("not_found");
	}

	@Test
	void shouldFindNoTacticWithoutCampaignDataOrTacticsTest() {
		// Given:
		ChannelSlideResolvers channelSlide = ReportsEngineTestSupport.channelSlideResolvers();
		CampaignData noTactics = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), null, null, null, null, null, null, null);

		// When:
		Tactic withoutData = channelSlide.tactic(null, 1);
		Tactic withoutTactics = channelSlide.tactic(noTactics, 1);

		// Then:
		assertThat(withoutData).isNull();
		assertThat(withoutTactics).isNull();
	}

	@Test
	void shouldDashTheSpendGoalWhenThePlanCarriesNoFlightCostTest() {
		// Given: a tactic without a full-flight Total Cost
		ChannelSlideResolvers channelSlide = ReportsEngineTestSupport.channelSlideResolvers();
		Tactic tactic = new Tactic(
				"CTV", "Video", null,
				5_994, 512_300, 769, 0,
				0.15, null, null, null,
				6_000.0, 500_000.0, 0.12, null, null,
				null, null, null,
				null, null, null, null,
				null, null, null);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(1, tactic), null, null, null, null, null, null);

		// When:
		Resolved flightGoal = channelSlide.resolveSpendPlanEoc(1, List.of(), List.of(), data);
		Resolved projection = channelSlide.resolveSpendProj(1, List.of(), List.of(), data);

		// Then:
		assertThat(flightGoal.value()).isEqualTo("—");
		assertThat(projection.value()).isEqualTo("—");
	}

	@Test
	void shouldDeriveTheMonthClickGoalFromImpressionsAtThePlannedCtrTest() {
		// Given: tactics without a click plan of their own, with and without the inputs to derive one
		ChannelSlideResolvers channelSlide = ReportsEngineTestSupport.channelSlideResolvers();
		Tactic derivable = new Tactic(
				"Display", "Display", null,
				5_994, 512_300, 769, 0,
				0.15, null, null, null,
				6_000.0, 500_000.0, 0.12, null, null,
				null, null, null,
				0.0, null, null, null,
				null, null, null);
		Tactic noPlanImps = new Tactic(
				"Display", "Display", null,
				5_994, 512_300, 769, 0,
				0.15, null, null, null,
				6_000.0, null, 0.12, null, null,
				null, null, null,
				null, null, null, null,
				null, null, null);
		Tactic noPlanCtr = new Tactic(
				"Display", "Display", null,
				5_994, 512_300, 769, 0,
				0.15, null, null, null,
				6_000.0, 500_000.0, null, null, null,
				null, null, null,
				null, null, null, null,
				null, null, null);

		// When:
		Double derived = channelSlide.monthPlanClicks(derivable);
		Double withoutPlanImps = channelSlide.monthPlanClicks(noPlanImps);
		Double withoutPlanCtr = channelSlide.monthPlanClicks(noPlanCtr);
		Double withoutTactic = channelSlide.monthPlanClicks(null);

		// Then: a zero click plan counts as no plan, so 0.12% of 500,000 impressions is the goal
		assertThat(derived).isCloseTo(600.0, within(1e-6));
		assertThat(withoutPlanImps).isNull();
		assertThat(withoutPlanCtr).isNull();
		assertThat(withoutTactic).isNull();
	}

	@Test
	void shouldCountReportingWeeksOnlyForACompleteForwardWindowTest() {
		// Given: a two-week window, a reversed window, open-ended windows, no window and no data
		ChannelSlideResolvers channelSlide = ReportsEngineTestSupport.channelSlideResolvers();
		CampaignData twoWeeks = new CampaignData(
				null, null, null, null, null,
				new FlightDates(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 14)),
				null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		CampaignData reversed = new CampaignData(
				null, null, null, null, null,
				new FlightDates(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 9)),
				null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		CampaignData noStart = new CampaignData(
				null, null, null, null, null,
				new FlightDates(null, LocalDate.of(2026, 6, 9)),
				null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		CampaignData noEnd = new CampaignData(
				null, null, null, null, null,
				new FlightDates(LocalDate.of(2026, 6, 1), null),
				null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		CampaignData noWindow = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);

		// When:
		Double weeks = channelSlide.reportingWeeks(twoWeeks);

		// Then:
		assertThat(weeks).isEqualTo(2.0);
		assertThat(channelSlide.reportingWeeks(reversed)).isNull();
		assertThat(channelSlide.reportingWeeks(noStart)).isNull();
		assertThat(channelSlide.reportingWeeks(noEnd)).isNull();
		assertThat(channelSlide.reportingWeeks(noWindow)).isNull();
		assertThat(channelSlide.reportingWeeks(null)).isNull();
	}

	@Test
	void shouldDashTheReachGoalWithoutFrequencyWindowOrDeliveryTest() {
		// Given: tactic 1 has no frequency, 2 a zero frequency, 3 no delivery; a second campaign has no window
		ChannelSlideResolvers channelSlide = ReportsEngineTestSupport.channelSlideResolvers();
		Tactic noFreq = new Tactic(
				"CTV", "Video", null,
				5_994, 512_300, 769, 0,
				0.15, null, null, null,
				6_000.0, 500_000.0, 0.12, null, null,
				null, null, null,
				null, null, null, null,
				null, null, null);
		Tactic zeroFreq = new Tactic(
				"CTV", "Video", null,
				5_994, 512_300, 769, 0,
				0.15, null, null, null,
				6_000.0, 500_000.0, 0.12, null, null,
				null, null, null,
				null, null, 0.0, null,
				null, null, null);
		Tactic noDelivery = new Tactic(
				"CTV", "Video", null,
				0, 0, 0, 0,
				null, null, null, null,
				6_000.0, 500_000.0, 0.12, null, null,
				null, null, null,
				null, null, 2.0, null,
				null, null, null);
		FlightDates june = new FlightDates(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
		CampaignData data = new CampaignData(
				null, null, null, null, null, june, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(1, noFreq, 2, zeroFreq, 3, noDelivery),
				null, null, null, null, null, null);
		Tactic delivered = new Tactic(
				"CTV", "Video", null,
				5_994, 512_300, 769, 0,
				0.15, null, null, null,
				6_000.0, 500_000.0, 0.12, null, null,
				null, null, null,
				null, null, 2.0, null,
				null, null, null);
		CampaignData noWindow = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(1, delivered), null, null, null, null, null, null);

		// When:
		List<Resolved> resolved = List.of(
				channelSlide.resolveReachPlan(1, List.of(), List.of(), data),
				channelSlide.resolveReachPlan(2, List.of(), List.of(), data),
				channelSlide.resolveReachPlan(3, List.of(), List.of(), data),
				channelSlide.resolveReachPlan(1, List.of(), List.of(), noWindow));

		// Then:
		assertThat(resolved).extracting(Resolved::value).containsOnly("—");
	}

	@Test
	void shouldDashCpmCellsWhenEitherRateIsMissingTest() {
		// Given: tactic 1 has no spend plan, 2 no impressions plan, 3 a zero impressions plan, 4 no delivery
		ChannelSlideResolvers channelSlide = ReportsEngineTestSupport.channelSlideResolvers();
		Tactic noPlanSpend = new Tactic(
				"CTV", "Video", null,
				5_994, 512_300, 769, 0,
				0.15, null, null, null,
				null, 500_000.0, 0.12, null, null,
				null, null, null,
				null, null, null, null,
				null, null, null);
		Tactic noPlanImps = new Tactic(
				"CTV", "Video", null,
				5_994, 512_300, 769, 0,
				0.15, null, null, null,
				6_000.0, null, 0.12, null, null,
				null, null, null,
				null, null, null, null,
				null, null, null);
		Tactic zeroPlanImps = new Tactic(
				"CTV", "Video", null,
				5_994, 512_300, 769, 0,
				0.15, null, null, null,
				6_000.0, 0.0, 0.12, null, null,
				null, null, null,
				null, null, null, null,
				null, null, null);
		Tactic noDelivery = new Tactic(
				"CTV", "Video", null,
				0, 0, 0, 0,
				null, null, null, null,
				6_000.0, 500_000.0, 0.12, null, null,
				null, null, null,
				null, null, null, null,
				null, null, null);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, noPlanSpend, 2, noPlanImps, 3, zeroPlanImps, 4, noDelivery),
				null, null, null, null, null, null);

		// When:
		List<Resolved> resolved = List.of(
				channelSlide.resolvePlannedCpm(1, List.of(), List.of(), data),
				channelSlide.resolvePlannedCpm(2, List.of(), List.of(), data),
				channelSlide.resolvePlannedCpm(3, List.of(), List.of(), data),
				channelSlide.resolveFactCpm(4, List.of(), List.of(), data),
				channelSlide.resolveCpmPacing(1, List.of(), List.of(), data),
				channelSlide.resolveCpmPacing(4, List.of(), List.of(), data),
				channelSlide.resolveCpmProj(1, List.of(), List.of(), data),
				channelSlide.resolveCpmProj(4, List.of(), List.of(), data));

		// Then:
		assertThat(resolved).extracting(Resolved::value).containsOnly("—");
	}

	@Test
	void shouldSpellAnOverPlanCpmVarianceWithAMinusTest() {
		// Given: a $12.00 planned CPM delivered at $13.00
		ChannelSlideResolvers channelSlide = ReportsEngineTestSupport.channelSlideResolvers();
		Tactic tactic = new Tactic(
				"CTV", "Video", null,
				6_500, 500_000, 0, 0,
				null, null, null, null,
				6_000.0, 500_000.0, null, null, null,
				null, null, null,
				null, null, null, null,
				null, null, null);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(1, tactic), null, null, null, null, null, null);

		// When:
		Resolved pacing = channelSlide.resolveCpmPacing(1, List.of(), List.of(), data);

		// Then: on CPM lower is better, so paying more than plan reads as a minus
		assertThat(pacing).isEqualTo(new Resolved("Tactic 1 cpm pacing: (auto)", "- $1.00", "adj"));
	}

	@Test
	void shouldComputeNoRatioWithoutAPositiveGoalTest() {
		// Given:
		ChannelSlideResolvers channelSlide = ReportsEngineTestSupport.channelSlideResolvers();

		// When:
		Double withoutGoal = channelSlide.ratio(50.0, null);
		Double zeroGoal = channelSlide.ratio(50.0, 0.0);
		Double withoutActual = channelSlide.ratio(null, 100.0);
		Double half = channelSlide.ratio(50.0, 100.0);

		// Then:
		assertThat(withoutGoal).isNull();
		assertThat(zeroGoal).isNull();
		assertThat(withoutActual).isNull();
		assertThat(half).isEqualTo(50.0);
	}

	@Test
	void shouldProjectOnlyAPositiveFlightGoalAndKeepItWithoutAPaceTest() {
		// Given:
		ChannelSlideResolvers channelSlide = ReportsEngineTestSupport.channelSlideResolvers();

		// When:
		String withoutGoal = channelSlide.projectAtPace(null, 120.0);
		String zeroGoal = channelSlide.projectAtPace(0.0, 120.0);
		String withoutPace = channelSlide.projectAtPace(1_000.0, null);
		String ahead = channelSlide.projectAtPace(1_000.0, 120.0);

		// Then:
		assertThat(withoutGoal).isNull();
		assertThat(zeroGoal).isNull();
		assertThat(withoutPace).isEqualTo("1,000");
		assertThat(ahead).isEqualTo("1,200");
	}

	@Test
	void shouldFormatOnlyPositiveCountsTest() {
		// Given:
		ChannelSlideResolvers channelSlide = ReportsEngineTestSupport.channelSlideResolvers();

		// When:
		String missing = channelSlide.count(null);
		String zero = channelSlide.count(0.0);
		String positive = channelSlide.count(1_234.4);

		// Then:
		assertThat(missing).isNull();
		assertThat(zero).isNull();
		assertThat(positive).isEqualTo("1,234");
	}

	@Test
	void shouldPaceDeliveredReachAgainstTheMonthsImpliedReachGoalTest() {
		// Given: 512,300 impressions over June at 2 per week imply a 59,768 reach goal for every tactic;
		// the reach resolver finds nothing for 1, a valueless figure for 2, zero for 3 and 29,884 for 4
		TacticResolvers tacticResolvers = mock(TacticResolvers.class);
		ChannelSlideResolvers channelSlide = new ChannelSlideResolvers(ReportsEngineTestSupport.sheetRowHelper(),
				new Fmt(), tacticResolvers, ReportsEngineTestSupport.reportNumberParser());
		Tactic tactic = new Tactic(
				"CTV", "Video", null,
				5_994, 512_300, 769, 0,
				0.15, null, null, null,
				6_000.0, 500_000.0, 0.12, null, null,
				null, null, null,
				null, null, 2.0, null,
				null, null, null);
		CampaignData data = new CampaignData(
				null, null, null, null, null,
				new FlightDates(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)),
				null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(1, tactic, 2, tactic, 3, tactic, 4, tactic),
				null, null, null, null, null, null);
		List<List<String>> sheet = List.of();
		List<List<String>> adj = List.of();
		when(tacticResolvers.resolveTacticReach(1, sheet, adj, data))
				.thenReturn(new Resolved("Tactic 1 reach:", null, "not_found"));
		when(tacticResolvers.resolveTacticReach(2, sheet, adj, data))
				.thenReturn(new Resolved("Tactic 2 reach:", null, "adj"));
		when(tacticResolvers.resolveTacticReach(3, sheet, adj, data))
				.thenReturn(new Resolved("Tactic 3 reach:", "0", "adj"));
		when(tacticResolvers.resolveTacticReach(4, sheet, adj, data))
				.thenReturn(new Resolved("Tactic 4 reach:", "29,884", "adj"));

		// When:
		Resolved notFound = channelSlide.resolveReachPacing(1, sheet, adj, data);
		Resolved valueless = channelSlide.resolveReachPacing(2, sheet, adj, data);
		Resolved zero = channelSlide.resolveReachPacing(3, sheet, adj, data);
		Resolved paced = channelSlide.resolveReachPacing(4, sheet, adj, data);

		// Then:
		assertThat(notFound.value()).isEqualTo("—");
		assertThat(valueless.value()).isEqualTo("—");
		assertThat(zero.value()).isEqualTo("—");
		assertThat(paced).isEqualTo(new Resolved("Tactic 4 reach pacing: (auto)", "50%", "adj"));
	}
}
