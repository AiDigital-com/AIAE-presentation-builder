package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CampaignPacingResolversTest {

	@Test
	void shouldPreferTheAdjustmentsTabForEveryPacingTokenTest() {
		// Given: the Adjustments tab pins every token, while the Media Plan carries different values
		CampaignPacingResolvers resolvers = new CampaignPacingResolvers(ReportsEngineTestSupport.sheetRowHelper(),
				new Fmt(), new RatePlanCalculator(), mock(CampaignResolvers.class));
		List<List<String>> adj = List.of(
				List.of("Total imps plan ctd:", "A1"),
				List.of("Total imps pace:", "A2"),
				List.of("Total investment plan ctd:", "A3"),
				List.of("Total investment pace:", "A4"),
				List.of("Campaign pace status:", "A5"),
				List.of("Eom month number:", "A6"),
				List.of("Eom flight months total:", "A7"),
				List.of("Eom report month:", "A8"),
				List.of("Eom next month number:", "A9"),
				List.of("Eom next report month:", "A10"));
		List<List<String>> sheet = List.of(List.of("Total imps plan ctd:", "S1"));

		// When:
		List<Resolved> resolved = List.of(
				resolvers.resolveTotalImpsPlanCtd(sheet, adj, null),
				resolvers.resolveTotalImpsPace(sheet, adj, null),
				resolvers.resolveTotalInvestmentPlanCtd(sheet, adj, null),
				resolvers.resolveTotalInvestmentPace(sheet, adj, null),
				resolvers.resolveCampaignPaceStatus(sheet, adj, null),
				resolvers.resolveEomMonthNumber(sheet, adj, null),
				resolvers.resolveEomFlightMonthsTotal(sheet, adj, null),
				resolvers.resolveEomReportMonth(sheet, adj, null),
				resolvers.resolveEomNextMonthNumber(sheet, adj, null),
				resolvers.resolveEomNextReportMonth(sheet, adj, null));

		// Then:
		assertThat(resolved).extracting(Resolved::value)
				.containsExactly("A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10");
		assertThat(resolved).extracting(Resolved::source).containsOnly("adj");
		assertThat(resolved.get(0).label()).isEqualTo("Total imps plan ctd:");
	}

	@Test
	void shouldFallBackToTheMediaPlanForEveryPacingTokenTest() {
		// Given: no Adjustments override, the Media Plan pins every token
		CampaignPacingResolvers resolvers = new CampaignPacingResolvers(ReportsEngineTestSupport.sheetRowHelper(),
				new Fmt(), new RatePlanCalculator(), mock(CampaignResolvers.class));
		List<List<String>> sheet = List.of(
				List.of("Total imps plan ctd:", "S1"),
				List.of("Total imps pace:", "S2"),
				List.of("Total investment plan ctd:", "S3"),
				List.of("Total investment pace:", "S4"),
				List.of("Campaign pace status:", "S5"),
				List.of("Eom month number:", "S6"),
				List.of("Eom flight months total:", "S7"),
				List.of("Eom report month:", "S8"),
				List.of("Eom next month number:", "S9"),
				List.of("Eom next report month:", "S10"));

		// When:
		List<Resolved> resolved = List.of(
				resolvers.resolveTotalImpsPlanCtd(sheet, List.of(), null),
				resolvers.resolveTotalImpsPace(sheet, List.of(), null),
				resolvers.resolveTotalInvestmentPlanCtd(sheet, List.of(), null),
				resolvers.resolveTotalInvestmentPace(sheet, List.of(), null),
				resolvers.resolveCampaignPaceStatus(sheet, List.of(), null),
				resolvers.resolveEomMonthNumber(sheet, List.of(), null),
				resolvers.resolveEomFlightMonthsTotal(sheet, List.of(), null),
				resolvers.resolveEomReportMonth(sheet, List.of(), null),
				resolvers.resolveEomNextMonthNumber(sheet, List.of(), null),
				resolvers.resolveEomNextReportMonth(sheet, List.of(), null));

		// Then:
		assertThat(resolved).extracting(Resolved::value)
				.containsExactly("S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9", "S10");
		assertThat(resolved).extracting(Resolved::source).containsOnly("sheet");
	}

	@Test
	void shouldProrateTheImpressionsPlanToTheElapsedMonthsTest() {
		// Given: 2 of 3 months elapsed on a 300,000-impression plan
		CampaignResolvers campaignResolvers = mock(CampaignResolvers.class);
		CampaignPacingResolvers resolvers = new CampaignPacingResolvers(ReportsEngineTestSupport.sheetRowHelper(),
				new Fmt(), new RatePlanCalculator(), campaignResolvers);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		when(campaignResolvers.elapsedAndTotalMonths(data)).thenReturn(new int[]{2, 3});
		when(campaignResolvers.totalPlanImps(data)).thenReturn(300_000.0);

		// When:
		Resolved r = resolvers.resolveTotalImpsPlanCtd(List.of(), List.of(), data);

		// Then:
		assertThat(r).isEqualTo(new Resolved("Total imps plan ctd (auto: sum of tactic plans, prorated)",
				"200,000", "adj"));
	}

	@Test
	void shouldLeaveTheImpressionsPlanUnresolvedWithoutMonthsOrPlanTest() {
		// Given: one campaign with no month counts and another with no impressions plan
		CampaignResolvers campaignResolvers = mock(CampaignResolvers.class);
		CampaignPacingResolvers resolvers = new CampaignPacingResolvers(ReportsEngineTestSupport.sheetRowHelper(),
				new Fmt(), new RatePlanCalculator(), campaignResolvers);
		CampaignData noMonths = new CampaignData(
				"no months", null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		CampaignData noPlan = new CampaignData(
				"no plan", null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		when(campaignResolvers.elapsedAndTotalMonths(noMonths)).thenReturn(null);
		when(campaignResolvers.totalPlanImps(noMonths)).thenReturn(300_000.0);
		when(campaignResolvers.elapsedAndTotalMonths(noPlan)).thenReturn(new int[]{1, 3});
		when(campaignResolvers.totalPlanImps(noPlan)).thenReturn(null);

		// When:
		Resolved withoutMonths = resolvers.resolveTotalImpsPlanCtd(List.of(), List.of(), noMonths);
		Resolved withoutPlan = resolvers.resolveTotalImpsPlanCtd(List.of(), List.of(), noPlan);

		// Then:
		assertThat(withoutMonths.value()).isNull();
		assertThat(withoutMonths.source()).isEqualTo("not_found");
		assertThat(withoutPlan.value()).isNull();
		assertThat(withoutPlan.source()).isEqualTo("not_found");
	}

	@Test
	void shouldFormatTheImpressionsPaceAsLiftAboveAndShareBelowPlanTest() {
		// Given:
		CampaignPacingResolvers resolvers = ReportsEngineTestSupport.campaignPacingResolvers();

		// When:
		String over = resolvers.impsPace(1_020, 1_000);
		String under = resolvers.impsPace(980, 1_000);
		String huge = resolvers.impsPace(3_611_000, 1_000);

		// Then:
		assertThat(over).isEqualTo("+2%");
		assertThat(under).isEqualTo("98%");
		assertThat(huge).isEqualTo("+361K%");
	}

	@Test
	void shouldLeaveTheImpressionsPaceUnresolvedWithoutAUsablePlanOrTotalsTest() {
		// Given: a missing plan, a zero plan, no campaign data and campaign data without totals
		CampaignResolvers campaignResolvers = mock(CampaignResolvers.class);
		CampaignPacingResolvers resolvers = new CampaignPacingResolvers(ReportsEngineTestSupport.sheetRowHelper(),
				new Fmt(), new RatePlanCalculator(), campaignResolvers);
		CampaignData noPlan = new CampaignData(
				"no plan", null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		CampaignData zeroPlan = new CampaignData(
				"zero plan", null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		CampaignData noTotals = new CampaignData(
				"no totals", null, null, null, null, null, null, null, null, null, null,
				null, Map.of(), null, null, null, null, null, null);
		when(campaignResolvers.totalPlanImps(noPlan)).thenReturn(null);
		when(campaignResolvers.totalPlanImps(zeroPlan)).thenReturn(0.0);
		when(campaignResolvers.totalPlanImps(null)).thenReturn(1_000.0);
		when(campaignResolvers.totalPlanImps(noTotals)).thenReturn(1_000.0);

		// When:
		List<Resolved> resolved = List.of(
				resolvers.resolveTotalImpsPace(List.of(), List.of(), noPlan),
				resolvers.resolveTotalImpsPace(List.of(), List.of(), zeroPlan),
				resolvers.resolveTotalImpsPace(List.of(), List.of(), null),
				resolvers.resolveTotalImpsPace(List.of(), List.of(), noTotals));

		// Then:
		assertThat(resolved).extracting(Resolved::value).containsOnlyNulls();
		assertThat(resolved).extracting(Resolved::source).containsOnly("not_found");
	}

	@Test
	void shouldResolveTheImpressionsPaceFromTotalsAgainstPlanTest() {
		// Given: 1,020 impressions delivered on a 1,000-impression plan
		CampaignResolvers campaignResolvers = mock(CampaignResolvers.class);
		CampaignPacingResolvers resolvers = new CampaignPacingResolvers(ReportsEngineTestSupport.sheetRowHelper(),
				new Fmt(), new RatePlanCalculator(), campaignResolvers);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 1_020, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		when(campaignResolvers.totalPlanImps(data)).thenReturn(1_000.0);

		// When:
		Resolved r = resolvers.resolveTotalImpsPace(List.of(), List.of(), data);

		// Then:
		assertThat(r).isEqualTo(new Resolved("Total imps pace (auto: fact vs planned impressions)", "+2%", "adj"));
	}

	@Test
	void shouldProrateTheInvestmentPlanKeepingCentsTest() {
		// Given: 1 of 3 months elapsed on a $10,000 plan
		CampaignResolvers campaignResolvers = mock(CampaignResolvers.class);
		CampaignPacingResolvers resolvers = new CampaignPacingResolvers(ReportsEngineTestSupport.sheetRowHelper(),
				new Fmt(), new RatePlanCalculator(), campaignResolvers);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		when(campaignResolvers.elapsedAndTotalMonths(data)).thenReturn(new int[]{1, 3});
		when(campaignResolvers.totalPlanSpend(data)).thenReturn(10_000.0);

		// When:
		Resolved r = resolvers.resolveTotalInvestmentPlanCtd(List.of(), List.of(), data);

		// Then:
		assertThat(r).isEqualTo(new Resolved("Total investment plan ctd (auto: sum of tactic plans, prorated)",
				"$3,333.33", "adj"));
	}

	@Test
	void shouldLeaveTheInvestmentPlanUnresolvedWithoutMonthsOrPlanTest() {
		// Given:
		CampaignResolvers campaignResolvers = mock(CampaignResolvers.class);
		CampaignPacingResolvers resolvers = new CampaignPacingResolvers(ReportsEngineTestSupport.sheetRowHelper(),
				new Fmt(), new RatePlanCalculator(), campaignResolvers);
		CampaignData noMonths = new CampaignData(
				"no months", null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		CampaignData noPlan = new CampaignData(
				"no plan", null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		when(campaignResolvers.elapsedAndTotalMonths(noMonths)).thenReturn(null);
		when(campaignResolvers.totalPlanSpend(noMonths)).thenReturn(10_000.0);
		when(campaignResolvers.elapsedAndTotalMonths(noPlan)).thenReturn(new int[]{1, 3});
		when(campaignResolvers.totalPlanSpend(noPlan)).thenReturn(null);

		// When:
		Resolved withoutMonths = resolvers.resolveTotalInvestmentPlanCtd(List.of(), List.of(), noMonths);
		Resolved withoutPlan = resolvers.resolveTotalInvestmentPlanCtd(List.of(), List.of(), noPlan);

		// Then:
		assertThat(withoutMonths.value()).isNull();
		assertThat(withoutMonths.source()).isEqualTo("not_found");
		assertThat(withoutPlan.value()).isNull();
		assertThat(withoutPlan.source()).isEqualTo("not_found");
	}

	@Test
	void shouldResolveTheInvestmentPaceAgainstTheProratedGoalTest() {
		// Given: $5,500 spent with 1 of 2 months elapsed on a $10,000 plan (goal to date $5,000)
		CampaignResolvers campaignResolvers = mock(CampaignResolvers.class);
		CampaignPacingResolvers resolvers = new CampaignPacingResolvers(ReportsEngineTestSupport.sheetRowHelper(),
				new Fmt(), new RatePlanCalculator(), campaignResolvers);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(5_500, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		when(campaignResolvers.elapsedAndTotalMonths(data)).thenReturn(new int[]{1, 2});
		when(campaignResolvers.totalPlanSpend(data)).thenReturn(10_000.0);

		// When:
		Resolved r = resolvers.resolveTotalInvestmentPace(List.of(), List.of(), data);

		// Then:
		assertThat(r).isEqualTo(new Resolved("Total investment pace (auto: to-date actual vs prorated goal)",
				"+10%", "adj"));
	}

	@Test
	void shouldLeaveTheInvestmentPaceUnresolvedWhenItCannotBeComputedTest() {
		// Given: no months, no plan, no totals, and a zero plan whose relative variance is undefined
		CampaignResolvers campaignResolvers = mock(CampaignResolvers.class);
		CampaignPacingResolvers resolvers = new CampaignPacingResolvers(ReportsEngineTestSupport.sheetRowHelper(),
				new Fmt(), new RatePlanCalculator(), campaignResolvers);
		CampaignData noMonths = new CampaignData(
				"no months", null, null, null, null, null, null, null, null, null, null,
				new Totals(100, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		CampaignData noPlan = new CampaignData(
				"no plan", null, null, null, null, null, null, null, null, null, null,
				new Totals(100, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		CampaignData noTotals = new CampaignData(
				"no totals", null, null, null, null, null, null, null, null, null, null,
				null, Map.of(), null, null, null, null, null, null);
		CampaignData zeroPlan = new CampaignData(
				"zero plan", null, null, null, null, null, null, null, null, null, null,
				new Totals(100, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		when(campaignResolvers.elapsedAndTotalMonths(noMonths)).thenReturn(null);
		when(campaignResolvers.totalPlanSpend(noMonths)).thenReturn(10_000.0);
		when(campaignResolvers.elapsedAndTotalMonths(noPlan)).thenReturn(new int[]{1, 2});
		when(campaignResolvers.totalPlanSpend(noPlan)).thenReturn(null);
		when(campaignResolvers.elapsedAndTotalMonths(noTotals)).thenReturn(new int[]{1, 2});
		when(campaignResolvers.totalPlanSpend(noTotals)).thenReturn(10_000.0);
		when(campaignResolvers.elapsedAndTotalMonths(zeroPlan)).thenReturn(new int[]{1, 2});
		when(campaignResolvers.totalPlanSpend(zeroPlan)).thenReturn(0.0);

		// When:
		List<Resolved> resolved = List.of(
				resolvers.resolveTotalInvestmentPace(List.of(), List.of(), noMonths),
				resolvers.resolveTotalInvestmentPace(List.of(), List.of(), noPlan),
				resolvers.resolveTotalInvestmentPace(List.of(), List.of(), noTotals),
				resolvers.resolveTotalInvestmentPace(List.of(), List.of(), zeroPlan));

		// Then:
		assertThat(resolved).extracting(Resolved::value).containsOnlyNulls();
		assertThat(resolved).extracting(Resolved::source).containsOnly("not_found");
	}

	@Test
	void shouldGradeTheCampaignPaceAheadOnOrBehindTest() {
		// Given: 1 of 2 months elapsed on a 2,000-impression plan (goal to date 1,000)
		CampaignResolvers campaignResolvers = mock(CampaignResolvers.class);
		CampaignPacingResolvers resolvers = new CampaignPacingResolvers(ReportsEngineTestSupport.sheetRowHelper(),
				new Fmt(), new RatePlanCalculator(), campaignResolvers);
		CampaignData ahead = new CampaignData(
				"ahead", null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 1_100, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		CampaignData onPace = new CampaignData(
				"on pace", null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 1_020, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		CampaignData behind = new CampaignData(
				"behind", null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 900, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		for (CampaignData data : List.of(ahead, onPace, behind)) {
			when(campaignResolvers.elapsedAndTotalMonths(data)).thenReturn(new int[]{1, 2});
			when(campaignResolvers.totalPlanImps(data)).thenReturn(2_000.0);
		}

		// When:
		Resolved aheadStatus = resolvers.resolveCampaignPaceStatus(List.of(), List.of(), ahead);
		Resolved onPaceStatus = resolvers.resolveCampaignPaceStatus(List.of(), List.of(), onPace);
		Resolved behindStatus = resolvers.resolveCampaignPaceStatus(List.of(), List.of(), behind);

		// Then: the +/-5% band around the goal to date reads as on pace
		assertThat(aheadStatus.value()).isEqualTo("AHEAD OF PACE");
		assertThat(onPaceStatus.value()).isEqualTo("ON PACE");
		assertThat(behindStatus.value()).isEqualTo("BEHIND PACE");
		assertThat(behindStatus.source()).isEqualTo("adj");
		assertThat(behindStatus.label()).isEqualTo("Campaign pace status (auto: imps pace vs prorated goal)");
	}

	@Test
	void shouldLeaveTheCampaignPaceUnresolvedWhenItCannotBeGradedTest() {
		// Given: no months, no plan, no totals, and a zero plan
		CampaignResolvers campaignResolvers = mock(CampaignResolvers.class);
		CampaignPacingResolvers resolvers = new CampaignPacingResolvers(ReportsEngineTestSupport.sheetRowHelper(),
				new Fmt(), new RatePlanCalculator(), campaignResolvers);
		CampaignData noMonths = new CampaignData(
				"no months", null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 100, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		CampaignData noPlan = new CampaignData(
				"no plan", null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 100, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		CampaignData noTotals = new CampaignData(
				"no totals", null, null, null, null, null, null, null, null, null, null,
				null, Map.of(), null, null, null, null, null, null);
		CampaignData zeroPlan = new CampaignData(
				"zero plan", null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 100, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		when(campaignResolvers.elapsedAndTotalMonths(noMonths)).thenReturn(null);
		when(campaignResolvers.totalPlanImps(noMonths)).thenReturn(2_000.0);
		when(campaignResolvers.elapsedAndTotalMonths(noPlan)).thenReturn(new int[]{1, 2});
		when(campaignResolvers.totalPlanImps(noPlan)).thenReturn(null);
		when(campaignResolvers.elapsedAndTotalMonths(noTotals)).thenReturn(new int[]{1, 2});
		when(campaignResolvers.totalPlanImps(noTotals)).thenReturn(2_000.0);
		when(campaignResolvers.elapsedAndTotalMonths(zeroPlan)).thenReturn(new int[]{1, 2});
		when(campaignResolvers.totalPlanImps(zeroPlan)).thenReturn(0.0);

		// When:
		List<Resolved> resolved = List.of(
				resolvers.resolveCampaignPaceStatus(List.of(), List.of(), noMonths),
				resolvers.resolveCampaignPaceStatus(List.of(), List.of(), noPlan),
				resolvers.resolveCampaignPaceStatus(List.of(), List.of(), noTotals),
				resolvers.resolveCampaignPaceStatus(List.of(), List.of(), zeroPlan));

		// Then:
		assertThat(resolved).extracting(Resolved::value).containsOnlyNulls();
		assertThat(resolved).extracting(Resolved::source).containsOnly("not_found");
	}

	@Test
	void shouldResolveTheEomMonthCountsFromCampaignDataTest() {
		// Given: the second month of a three-month flight
		CampaignPacingResolvers resolvers = ReportsEngineTestSupport.campaignPacingResolvers();
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), 2, 3, null, null, null, null);

		// When:
		Resolved monthNumber = resolvers.resolveEomMonthNumber(List.of(), List.of(), data);
		Resolved monthsTotal = resolvers.resolveEomFlightMonthsTotal(List.of(), List.of(), data);

		// Then:
		assertThat(monthNumber).isEqualTo(
				new Resolved("Eom month number (auto: calendar months since flight start)", "2", "adj"));
		assertThat(monthsTotal).isEqualTo(
				new Resolved("Eom flight months total (auto: Data Inputs Flight dates)", "3", "adj"));
	}

	@Test
	void shouldLeaveTheEomMonthCountsUnresolvedForAnEocCampaignTest() {
		// Given: no campaign data at all, and campaign data without the EOM month counts
		CampaignPacingResolvers resolvers = ReportsEngineTestSupport.campaignPacingResolvers();
		CampaignData eoc = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);

		// When:
		List<Resolved> resolved = List.of(
				resolvers.resolveEomMonthNumber(List.of(), List.of(), null),
				resolvers.resolveEomMonthNumber(List.of(), List.of(), eoc),
				resolvers.resolveEomFlightMonthsTotal(List.of(), List.of(), null),
				resolvers.resolveEomFlightMonthsTotal(List.of(), List.of(), eoc),
				resolvers.resolveEomReportMonth(List.of(), List.of(), null),
				resolvers.resolveEomReportMonth(List.of(), List.of(), eoc));

		// Then:
		assertThat(resolved).extracting(Resolved::value).containsOnlyNulls();
		assertThat(resolved).extracting(Resolved::source).containsOnly("not_found");
	}

	@Test
	void shouldNameTheReportMonthFromTheFlightWindowEndTest() {
		// Given:
		CampaignPacingResolvers resolvers = ReportsEngineTestSupport.campaignPacingResolvers();
		CampaignData data = new CampaignData(
				null, null, null, null, null,
				new FlightDates(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 31)),
				null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);

		// When:
		Resolved r = resolvers.resolveEomReportMonth(List.of(), List.of(), data);

		// Then:
		assertThat(r).isEqualTo(new Resolved("Eom report month (auto: flight window end)", "October 2025", "adj"));
	}

	@Test
	void shouldResolveTheNextMonthWhileTheFlightHasMonthsLeftTest() {
		// Given: an October report in the second month of a three-month flight
		CampaignResolvers campaignResolvers = mock(CampaignResolvers.class);
		CampaignPacingResolvers resolvers = new CampaignPacingResolvers(ReportsEngineTestSupport.sheetRowHelper(),
				new Fmt(), new RatePlanCalculator(), campaignResolvers);
		CampaignData data = new CampaignData(
				null, null, null, null, null,
				new FlightDates(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 31)),
				null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		when(campaignResolvers.elapsedAndTotalMonths(data)).thenReturn(new int[]{2, 3});

		// When:
		Resolved nextNumber = resolvers.resolveEomNextMonthNumber(List.of(), List.of(), data);
		Resolved nextMonth = resolvers.resolveEomNextReportMonth(List.of(), List.of(), data);

		// Then:
		assertThat(nextNumber).isEqualTo(new Resolved("Eom next month number (auto: month number + 1)", "3", "adj"));
		assertThat(nextMonth).isEqualTo(
				new Resolved("Eom next report month (auto: flight window end + 1 month)", "November", "adj"));
	}

	@Test
	void shouldLeaveTheNextMonthEmptyOnTheLastMonthOrWithoutMonthsTest() {
		// Given: unknown month counts, the flight's last month, and a missing flight window
		CampaignResolvers campaignResolvers = mock(CampaignResolvers.class);
		CampaignPacingResolvers resolvers = new CampaignPacingResolvers(ReportsEngineTestSupport.sheetRowHelper(),
				new Fmt(), new RatePlanCalculator(), campaignResolvers);
		FlightDates october = new FlightDates(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 31));
		CampaignData noMonths = new CampaignData(
				"no months", null, null, null, null, october, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		CampaignData lastMonth = new CampaignData(
				"last month", null, null, null, null, october, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		CampaignData noWindow = new CampaignData(
				"no window", null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);
		when(campaignResolvers.elapsedAndTotalMonths(noMonths)).thenReturn(null);
		when(campaignResolvers.elapsedAndTotalMonths(lastMonth)).thenReturn(new int[]{3, 3});
		when(campaignResolvers.elapsedAndTotalMonths(noWindow)).thenReturn(new int[]{1, 3});

		// When:
		List<Resolved> resolved = List.of(
				resolvers.resolveEomNextMonthNumber(List.of(), List.of(), noMonths),
				resolvers.resolveEomNextMonthNumber(List.of(), List.of(), lastMonth),
				resolvers.resolveEomNextReportMonth(List.of(), List.of(), noMonths),
				resolvers.resolveEomNextReportMonth(List.of(), List.of(), lastMonth),
				resolvers.resolveEomNextReportMonth(List.of(), List.of(), noWindow));

		// Then:
		assertThat(resolved).extracting(Resolved::value).containsOnlyNulls();
		assertThat(resolved).extracting(Resolved::source).containsOnly("not_found");
	}
}
