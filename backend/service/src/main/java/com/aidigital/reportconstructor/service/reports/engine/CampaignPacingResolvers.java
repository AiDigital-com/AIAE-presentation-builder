package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Campaign-wide EOM pacing and reporting-period resolvers.
 *
 * <p>Split out of {@link CampaignResolvers}, which had grown past the file-length limit. These belong
 * together and apart from the rest: every one of them only produces a value for an EOM report, where
 * a single reporting month sits inside a longer booked flight, and all of them are read by the one
 * caller that fills the EOM pacing tokens. An EOC report resolves none of them.
 */
@Component
public class CampaignPacingResolvers {

	private final SheetRowHelper sheetUtils;
	private final Fmt fmt;
	private final RatePlanCalculator pacing;
	private final CampaignResolvers campaignResolvers;

	/**
	 * Wires the collaborators the pacing resolvers share with the campaign resolvers.
	 *
	 * @param sheetUtils        label/value lookups against the Media Plan and Adjustments tabs
	 * @param fmt               number/percentage/currency formatter for display values
	 * @param pacing            EOM proration/projection math
	 * @param campaignResolvers source of the elapsed/total month counts these resolvers prorate by
	 */
	public CampaignPacingResolvers(
			SheetRowHelper sheetUtils, Fmt fmt, RatePlanCalculator pacing, CampaignResolvers campaignResolvers) {
		this.sheetUtils = sheetUtils;
		this.fmt = fmt;
		this.pacing = pacing;
		this.campaignResolvers = campaignResolvers;
	}

	static final String TOTAL_IMPS_PACE_AUTO_LABEL = "Total imps pace (auto: fact vs planned impressions)";

	/**
	 * Formats the campaign-wide impressions pace two different ways, matching how the cover reads it.
	 * Over-delivery is a signed lift against the plan ({@code (fact / plan - 1) × 100}, e.g.
	 * {@code "+2%"}); anything at or below plan is instead the share of the plan actually delivered
	 * ({@code fact / plan × 100}, e.g. {@code "98%"}, and exactly {@code "100%"} on plan), so a shortfall
	 * never shows up as a bare negative number on the cover.
	 *
	 * @param fact the delivered impressions
	 * @param plan the planned impressions (must be positive)
	 * @return the formatted pace string
	 */
	String impsPace(double fact, double plan) {
		double liftPct = (fact / plan - 1) * 100;
		if (liftPct > 0) {
			return "+" + Math.round(liftPct) + "%";
		}
		return Math.round(fact / plan * 100) + "%";
	}


	/**
	 * Resolves the campaign-wide prorated to-date impressions goal: the summed full-campaign tactic
	 * plans scaled by elapsedMonths / totalMonths, preferring a manual override.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing the per-tactic plans and the elapsed/total month counts
	 * @return a {@link Resolved} prorated goal, or a null-valued {@code "not_found"} when unavailable
	 */
	public Resolved resolveTotalImpsPlanCtd(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                        CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Total imps plan ctd:");
		if (fromAdj != null) {
			return new Resolved("Total imps plan ctd:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Total imps plan ctd:");
		if (fromSheet != null) {
			return new Resolved("Total imps plan ctd:", fromSheet, "sheet");
		}
		int[] months = campaignResolvers.elapsedAndTotalMonths(data);
		Double totalPlan = campaignResolvers.totalPlanImps(data);
		if (months == null || totalPlan == null) {
			return new Resolved("Total imps plan ctd (auto: sum of tactic plans, prorated)", null, "not_found");
		}
		double planCtd = pacing.planCtd(totalPlan, months[0], months[1]);
		return new Resolved("Total imps plan ctd (auto: sum of tactic plans, prorated)", fmt.intGroup(planCtd), "adj");
	}

	/**
	 * Resolves the campaign-wide impressions pace of the actual against the plan, preferring a manual
	 * override. See {@link #impsPace} for the formula.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing the summed plan and the actual impressions
	 * @return a {@link Resolved} pace figure, or a null-valued {@code "not_found"} when unavailable
	 */
	public Resolved resolveTotalImpsPace(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                     CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Total imps pace:");
		if (fromAdj != null) {
			return new Resolved("Total imps pace:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Total imps pace:");
		if (fromSheet != null) {
			return new Resolved("Total imps pace:", fromSheet, "sheet");
		}
		Double totalPlan = campaignResolvers.totalPlanImps(data);
		if (totalPlan == null || totalPlan <= 0 || data == null || data.totals() == null) {
			return new Resolved(TOTAL_IMPS_PACE_AUTO_LABEL, null, "not_found");
		}
		return new Resolved(TOTAL_IMPS_PACE_AUTO_LABEL, impsPace(data.totals().imps(), totalPlan), "adj");
	}

	/**
	 * Resolves the campaign-wide prorated to-date investment goal, the budget counterpart to
	 * {@link #resolveTotalImpsPlanCtd}, preferring a manual override.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing the per-tactic plans and the elapsed/total month counts
	 * @return a {@link Resolved} prorated goal, or a null-valued {@code "not_found"} when unavailable
	 */
	public Resolved resolveTotalInvestmentPlanCtd(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                              CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Total investment plan ctd:");
		if (fromAdj != null) {
			return new Resolved("Total investment plan ctd:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Total investment plan ctd:");
		if (fromSheet != null) {
			return new Resolved("Total investment plan ctd:", fromSheet, "sheet");
		}
		int[] months = campaignResolvers.elapsedAndTotalMonths(data);
		Double totalPlan = campaignResolvers.totalPlanSpend(data);
		if (months == null || totalPlan == null) {
			return new Resolved("Total investment plan ctd (auto: sum of tactic plans, prorated)", null, "not_found");
		}
		double planCtd = pacing.planCtd(totalPlan, months[0], months[1]);
		return new Resolved("Total investment plan ctd (auto: sum of tactic plans, prorated)",
				fmt.moneyExact(planCtd), "adj");
	}

	/**
	 * Resolves the campaign-wide investment pacing variance of the to-date actual against the prorated
	 * to-date goal, preferring a manual override.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data aggregated campaign data providing the plan, the to-date actual and the elapsed/total month counts
	 * @return a {@link Resolved} pacing variance, or a null-valued {@code "not_found"} when unavailable
	 */
	public Resolved resolveTotalInvestmentPace(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                           CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Total investment pace:");
		if (fromAdj != null) {
			return new Resolved("Total investment pace:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Total investment pace:");
		if (fromSheet != null) {
			return new Resolved("Total investment pace:", fromSheet, "sheet");
		}
		int[] months = campaignResolvers.elapsedAndTotalMonths(data);
		Double totalPlan = campaignResolvers.totalPlanSpend(data);
		if (months == null || totalPlan == null || data.totals() == null) {
			return new Resolved("Total investment pace (auto: to-date actual vs prorated goal)", null, "not_found");
		}
		double planCtd = pacing.planCtd(totalPlan, months[0], months[1]);
		String variance = pacing.paceVariance(data.totals().spend(), planCtd, false);
		return new Resolved("Total investment pace (auto: to-date actual vs prorated goal)", variance,
				variance == null ? "not_found" : "adj");
	}

	/** Percentage-point threshold beyond which the campaign is deemed ahead of / behind pace. */
	private static final double CAMPAIGN_PACE_STATUS_THRESHOLD_PCT = 5.0;

	/**
	 * Resolves the campaign's headline pacing verdict ({@code "AHEAD OF PACE"} / {@code "ON PACE"} /
	 * {@code "BEHIND PACE"}) from the impressions pacing variance, preferring a manual override.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data aggregated campaign data providing the plan, the to-date actual and the elapsed/total month counts
	 * @return a {@link Resolved} pacing verdict, or a null-valued {@code "not_found"} when unavailable
	 */
	public Resolved resolveCampaignPaceStatus(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                          CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Campaign pace status:");
		if (fromAdj != null) {
			return new Resolved("Campaign pace status:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Campaign pace status:");
		if (fromSheet != null) {
			return new Resolved("Campaign pace status:", fromSheet, "sheet");
		}
		int[] months = campaignResolvers.elapsedAndTotalMonths(data);
		Double totalPlan = campaignResolvers.totalPlanImps(data);
		if (months == null || totalPlan == null || data.totals() == null) {
			return new Resolved("Campaign pace status (auto: imps pace vs prorated goal)", null, "not_found");
		}
		double planCtd = pacing.planCtd(totalPlan, months[0], months[1]);
		if (planCtd <= 0) {
			return new Resolved("Campaign pace status (auto: imps pace vs prorated goal)", null, "not_found");
		}
		double pct = (data.totals().imps() - planCtd) / planCtd * 100;
		String status = pct >= CAMPAIGN_PACE_STATUS_THRESHOLD_PCT ? "AHEAD OF PACE"
				: pct <= -CAMPAIGN_PACE_STATUS_THRESHOLD_PCT ? "BEHIND PACE" : "ON PACE";
		return new Resolved("Campaign pace status (auto: imps pace vs prorated goal)", status, "adj");
	}

	/**
	 * Resolves the reporting month's 1-based index within the flight (e.g. the second calendar month
	 * of a 3-month flight resolves to {@code 2}), preferring a manual override.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing {@code eomMonthNumber()}
	 * @return a {@link Resolved} month index, or a null-valued {@code "not_found"} when EOC or unset
	 */
	public Resolved resolveEomMonthNumber(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                      CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Eom month number:");
		if (fromAdj != null) {
			return new Resolved("Eom month number:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Eom month number:");
		if (fromSheet != null) {
			return new Resolved("Eom month number:", fromSheet, "sheet");
		}
		if (data == null || data.eomMonthNumber() == null) {
			return new Resolved("Eom month number (auto: calendar months since flight start)", null, "not_found");
		}
		return new Resolved("Eom month number (auto: calendar months since flight start)",
				String.valueOf(data.eomMonthNumber()), "adj");
	}

	/**
	 * Resolves the total number of calendar months the flight spans, preferring a manual override.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing {@code eomFlightMonthsTotal()}
	 * @return a {@link Resolved} month count, or a null-valued {@code "not_found"} when EOC or unset
	 */
	public Resolved resolveEomFlightMonthsTotal(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                            CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Eom flight months total:");
		if (fromAdj != null) {
			return new Resolved("Eom flight months total:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Eom flight months total:");
		if (fromSheet != null) {
			return new Resolved("Eom flight months total:", fromSheet, "sheet");
		}
		if (data == null || data.eomFlightMonthsTotal() == null) {
			return new Resolved("Eom flight months total (auto: Data Inputs Flight dates)", null, "not_found");
		}
		return new Resolved("Eom flight months total (auto: Data Inputs Flight dates)",
				String.valueOf(data.eomFlightMonthsTotal()), "adj");
	}

	/**
	 * Resolves the reporting month's calendar name and year (e.g. {@code "October 2025"}), preferring a
	 * manual override.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing the flight window whose end is the report cut-off
	 * @return a {@link Resolved} month label, or a null-valued {@code "not_found"} when no flight window is set
	 */
	public Resolved resolveEomReportMonth(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                      CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Eom report month:");
		if (fromAdj != null) {
			return new Resolved("Eom report month:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Eom report month:");
		if (fromSheet != null) {
			return new Resolved("Eom report month:", fromSheet, "sheet");
		}
		if (data == null || data.flightTs() == null) {
			return new Resolved("Eom report month (auto: flight window end)", null, "not_found");
		}
		return new Resolved("Eom report month (auto: flight window end)",
				pacing.monthLabel(data.flightTs().end()), "adj");
	}

	/**
	 * Resolves the next month's 1-based index within the flight, preferring a manual override. Empty
	 * when the reporting month is already the flight's last calendar month (there is no next month).
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing the elapsed/total month counts
	 * @return a {@link Resolved} next month index, or a null-valued {@code "not_found"} when unavailable
	 * or the reporting month is the flight's last
	 */
	public Resolved resolveEomNextMonthNumber(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                          CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Eom next month number:");
		if (fromAdj != null) {
			return new Resolved("Eom next month number:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Eom next month number:");
		if (fromSheet != null) {
			return new Resolved("Eom next month number:", fromSheet, "sheet");
		}
		int[] months = campaignResolvers.elapsedAndTotalMonths(data);
		if (months == null || months[0] >= months[1]) {
			return new Resolved("Eom next month number (auto: month number + 1)", null, "not_found");
		}
		return new Resolved("Eom next month number (auto: month number + 1)", String.valueOf(months[0] + 1), "adj");
	}

	/**
	 * Resolves the next calendar month's name (no year, e.g. {@code "November"}), preferring a manual
	 * override. Empty when the reporting month is already the flight's last calendar month.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing the flight window and the elapsed/total month counts
	 * @return a {@link Resolved} next month name, or a null-valued {@code "not_found"} when unavailable
	 * or the reporting month is the flight's last
	 */
	public Resolved resolveEomNextReportMonth(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                          CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Eom next report month:");
		if (fromAdj != null) {
			return new Resolved("Eom next report month:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Eom next report month:");
		if (fromSheet != null) {
			return new Resolved("Eom next report month:", fromSheet, "sheet");
		}
		int[] months = campaignResolvers.elapsedAndTotalMonths(data);
		if (months == null || months[0] >= months[1] || data.flightTs() == null) {
			return new Resolved("Eom next report month (auto: flight window end + 1 month)", null, "not_found");
		}
		LocalDate nextMonth = data.flightTs().end().plusMonths(1);
		return new Resolved("Eom next report month (auto: flight window end + 1 month)",
				pacing.monthNameOnly(nextMonth), "adj");
	}
}
