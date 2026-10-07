package com.aidigital.reportconstructor.service.reports.model;

/**
 * Mutable accumulator for one aggregation key — the whole campaign, a channel, or a line item —
 * filled by a single pass over the delivery export.
 *
 * <p>Mutable rather than a record on purpose: the collector walks every delivery row once and adds
 * into whichever bucket that row belongs to, so a new immutable instance per row would allocate once
 * per row per key for no benefit.
 *
 * <p>Completions are tracked with a separate flag rather than inferred from a non-zero sum, because
 * zero completions and "this campaign has no completions column at all" mean different things: the
 * first is a VCR of 0%, the second has no VCR to report.
 */
public final class CampaignAggregate {

	private double spend;
	private double impressions;
	private double clicks;
	private double completions;
	private double weekdayImpressions;
	private double weekendImpressions;
	private boolean hasCompletions;

	/**
	 * Adds one delivery row's core metrics.
	 *
	 * @param rowSpend       the row's cost
	 * @param rowImpressions the row's impressions
	 * @param rowClicks      the row's clicks
	 */
	public void add(double rowSpend, double rowImpressions, double rowClicks) {
		this.spend += rowSpend;
		this.impressions += rowImpressions;
		this.clicks += rowClicks;
	}

	/**
	 * Adds completions and records that this key has a completion figure at all, which is what makes
	 * a VCR reportable.
	 *
	 * @param rowCompletions the row's completions
	 */
	public void addCompletions(double rowCompletions) {
		this.completions += rowCompletions;
		this.hasCompletions = true;
	}

	/**
	 * Adds the row's impressions to the weekday or weekend bucket, which feed the day-parting split.
	 *
	 * @param rowImpressions the row's impressions
	 * @param weekend        whether the row's timestamp fell on a Saturday or Sunday
	 */
	public void addDayParted(double rowImpressions, boolean weekend) {
		if (weekend) {
			this.weekendImpressions += rowImpressions;
		} else {
			this.weekdayImpressions += rowImpressions;
		}
	}

	/**
	 * Returns the accumulated spend.
	 *
	 * @return total cost across the rows added
	 */
	public double spend() {
		return spend;
	}

	/**
	 * Returns the accumulated impressions.
	 *
	 * @return total impressions across the rows added
	 */
	public double impressions() {
		return impressions;
	}

	/**
	 * Returns the accumulated clicks.
	 *
	 * @return total clicks across the rows added
	 */
	public double clicks() {
		return clicks;
	}

	/**
	 * Returns the accumulated completions.
	 *
	 * @return total completions across the rows added; {@code 0} when none were tracked
	 */
	public double completions() {
		return completions;
	}

	/**
	 * Returns the impressions that landed on a weekday.
	 *
	 * @return weekday impressions
	 */
	public double weekdayImpressions() {
		return weekdayImpressions;
	}

	/**
	 * Returns the impressions that landed on a weekend.
	 *
	 * @return weekend impressions
	 */
	public double weekendImpressions() {
		return weekendImpressions;
	}

	/**
	 * Reports whether any completion figure was added, which decides whether a VCR exists to report.
	 *
	 * @return true when at least one row contributed completions
	 */
	public boolean hasCompletions() {
		return hasCompletions;
	}
}
