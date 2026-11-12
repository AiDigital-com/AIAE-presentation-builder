package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.projections.ClaudeLabelUsage;
import com.aidigital.reportconstructor.domain.reports.projections.UsageActiveDay;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyBucket;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyUserRow;
import com.aidigital.reportconstructor.service.admin.dto.AdminActiveUsersPeriod;
import com.aidigital.reportconstructor.service.admin.dto.AdminDateRange;
import com.aidigital.reportconstructor.service.admin.dto.AdminRangeView;
import com.aidigital.reportconstructor.service.admin.dto.AdminStats;
import com.aidigital.reportconstructor.service.admin.enums.AdminPeriodUnit;
import com.aidigital.reportconstructor.service.common.time.CurrentTime;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageEventService;
import com.aidigital.reportconstructor.service.reports.usage.UsageDailyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Assembles the admin dashboard payload for one window of dates — the expensive, cached half of
 * {@code AdminStatsService}.
 *
 * <p>Built out of a handful of bounded reads: the {@code usage_daily} rollup by day, by user, and its
 * distinct active (day, user) pairs, the per-stage usage aggregate, and the live operational counters.
 * None of them loads a report job into memory. All figures are derived; nothing is faked.
 */
@Component
@RequiredArgsConstructor
public class AdminStatsAssembler {

	/** Failures shown on the dashboard — enough to spot a pattern, bounded so the payload stays small. */
	private static final int FAILURE_LIMIT = 50;

	private final ReportJobProgressHelper jobs;
	private final UsageDailyService usageDaily;
	private final ClaudeUsageEventService usageEvents;
	private final AdminPeriodBucketer bucketer;
	private final AdminRollupTotals rollupTotals;
	private final AdminRollupTokenTotals rollupTokenTotals;
	private final AdminRollupUserStats rollupUserStats;
	private final AdminTrendBuilder trendBuilder;
	private final AdminActiveUsersBuilder activeUsersBuilder;
	private final AdminSavingsCalculator savingsCalculator;
	private final AdminTokenAggregator tokenAggregator;
	private final AdminFailureAssembler failureAssembler;
	private final CurrentTime currentTime;

	/**
	 * Assembles the dashboard payload for one window, from the rollup and the live counters.
	 *
	 * @param range the window to report on, already resolved and clamped
	 * @return the assembled snapshot
	 */
	public AdminStats assemble(AdminDateRange range) {
		List<UsageDailyBucket> days = usageDaily.byDay(range.from(), range.to());
		List<UsageDailyUserRow> userRows = usageDaily.byUser(range.from(), range.to());

		// Active users need history from before the window to tell an arrival from a return: someone
		// who first appeared last year is not a new user this month. The window's own figures are then
		// taken from this longer series rather than from a second, shorter read.
		List<UsageActiveDay> activeHistory = usageDaily.activeDays(range.from().minusYears(1), range.to());

		OffsetDateTime eventsFrom = range.from().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
		OffsetDateTime eventsTo = range.to().plusDays(1)
				.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
		List<ClaudeLabelUsage> byLabel = usageEvents.byLabel(eventsFrom, eventsTo);
		List<ClaudeLabelUsage> unattributed = usageEvents.unattributed(eventsFrom, eventsTo);

		AdminPeriodUnit seriesUnit = bucketer.chartUnitFor(
				ChronoUnit.DAYS.between(range.from(), range.to()) + 1);
		OffsetDateTime rollupUpdatedAt = usageDaily.lastRefreshedAt();

		return new AdminStats(
				currentTime.nowOffsetDateTime().toLocalDateTime(),
				rollupUpdatedAt == null ? null : rollupUpdatedAt.toLocalDateTime(),
				new AdminRangeView(range.from(), range.to(), range.unit().getCode()),
				rollupTotals.totals(days,
						activeUsersBuilder.activeInWindow(activeHistory, range.from()),
						activeUsersBuilder.newInWindow(activeHistory, range.from()),
						jobs.countInFlight(), jobs.countFailed()),
				savingsCalculator.calculate(days),
				rollupUserStats.build(userRows, jobs.listOwners()),
				rollupTotals.byType(days),
				trendBuilder.build(days, seriesUnit),
				seriesUnit.getCode(),
				rollupTokenTotals.totals(days, unattributed, byLabel),
				trendBuilder.build(days, AdminPeriodUnit.WEEK),
				trendBuilder.build(days, AdminPeriodUnit.MONTH),
				withinWindow(activeUsersBuilder.build(activeHistory, AdminPeriodUnit.WEEK), range),
				withinWindow(activeUsersBuilder.build(activeHistory, AdminPeriodUnit.MONTH), range),
				tokenAggregator.byLabel(byLabel),
				failureAssembler.recentFailures(jobs.listRecentIssues(FAILURE_LIMIT), FAILURE_LIMIT));
	}

	/**
	 * Trims an active-user series back to the buckets the window covers.
	 *
	 * <p>The series is built over a longer history so that first-time users can be recognised, but the
	 * dashboard must not then show months the viewer did not ask about. A bucket the window covers
	 * only partly is kept: dropping it would hide activity that did happen inside the window.
	 *
	 * @param series the full series, oldest first
	 * @param range  the window being reported on
	 * @return the buckets overlapping the window
	 */
	List<AdminActiveUsersPeriod> withinWindow(List<AdminActiveUsersPeriod> series, AdminDateRange range) {
		return series.stream()
				.filter(period -> !period.start().isAfter(range.to()))
				.filter(period -> !period.start().isBefore(range.from().withDayOfMonth(1)))
				.toList();
	}
}
