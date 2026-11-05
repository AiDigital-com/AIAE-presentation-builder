package com.aidigital.reportconstructor.service.reports.engine;

import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Formats the Paid Search / Paid Social figures the way the MPO sample decks print them: rounded (not truncated)
 * compact counts ({@code 327K}, {@code 14.9K}, {@code 1.2M}), compact spend ({@code $42.8K}), cent-precise unit
 * costs ({@code $19.04}), one-decimal rates ({@code 4.6%}) and whole-number shares ({@code 28%}). A rate or share
 * without a denominator renders as an em-dash.
 */
@Component
public class PaidValueFormatter {

	/** What the deck prints for a value that cannot be computed. */
	public static final String DASH = "—";

	private static final double THOUSAND = 1_000d;
	private static final double MILLION = 1_000_000d;
	private static final double PERCENT = 100d;
	/** From here on thousands print without a decimal ({@code 327K}, not {@code 327.4K}). */
	private static final double WHOLE_THOUSANDS_FROM = 99_950d;

	/**
	 * @param v a count
	 * @return the count rounded and comma-grouped, e.g. {@code 1,248}
	 */
	public String count(double v) {
		return String.format(Locale.US, "%,d", Math.round(v));
	}

	/**
	 * @param v a count
	 * @return the count in compact notation: {@code 1.2M}, {@code 327K}, {@code 14.9K}, or grouped below 1,000
	 */
	public String compact(double v) {
		double abs = Math.abs(v);
		if (abs >= MILLION) {
			return String.format(Locale.US, "%.1fM", v / MILLION);
		}
		if (abs >= WHOLE_THOUSANDS_FROM) {
			return Math.round(v / THOUSAND) + "K";
		}
		if (abs >= THOUSAND) {
			return String.format(Locale.US, "%.1fK", v / THOUSAND);
		}
		return count(v);
	}

	/**
	 * @param v a spend amount
	 * @return the amount compact with a dollar sign ({@code $42.8K}), or whole dollars below 1,000 ({@code $150})
	 */
	public String moneyCompact(double v) {
		return "$" + compact(v);
	}

	/**
	 * @param v a unit cost (CPC, CPA), or {@code null} when it has no denominator
	 * @return the cost with cents, e.g. {@code $19.04}, or an em-dash
	 */
	public String money(Double v) {
		return v == null ? DASH : String.format(Locale.US, "$%,.2f", v);
	}

	/**
	 * @param v a rate already in percent (CTR, CR), or {@code null} when it has no denominator
	 * @return the rate with one decimal, e.g. {@code 4.6%}, or an em-dash
	 */
	public String pct1(Double v) {
		return v == null ? DASH : String.format(Locale.US, "%.1f%%", v);
	}

	/**
	 * @param part  the slice
	 * @param whole the total the slice is part of
	 * @return the slice's whole-number share, e.g. {@code 28%}, or an em-dash without a total
	 */
	public String share(double part, double whole) {
		return whole > 0 ? String.format(Locale.US, "%.0f%%", part / whole * PERCENT) : DASH;
	}

	/**
	 * @param part  the slice
	 * @param whole the total the slice is part of
	 * @return the slice's one-decimal share, e.g. {@code 50.3%}, or an em-dash without a total
	 */
	public String share1(double part, double whole) {
		return whole > 0 ? String.format(Locale.US, "%.1f%%", part / whole * PERCENT) : DASH;
	}

	/**
	 * @param current  this period's value
	 * @param previous the previous period's value, or {@code null} when there is none
	 * @return the signed whole-number change, e.g. {@code +18%} or {@code -5%}, or an em-dash without a base
	 */
	public String growth(double current, Double previous) {
		if (previous == null || previous <= 0) {
			return DASH;
		}
		return String.format(Locale.US, "%+.0f%%", (current - previous) / previous * PERCENT);
	}

	/**
	 * @param month a calendar month
	 * @return the month spelled out with its year, e.g. {@code October 2026}
	 */
	public String month(YearMonth month) {
		return month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();
	}
}
