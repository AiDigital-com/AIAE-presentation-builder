package com.aidigital.reportconstructor.mappers.admin;

import com.aidigital.reportconstructor.api.v1.model.AdminActiveUsersPeriodV1;
import com.aidigital.reportconstructor.api.v1.model.AdminFailedJobV1;
import com.aidigital.reportconstructor.api.v1.model.AdminRangeV1;
import com.aidigital.reportconstructor.api.v1.model.AdminSavingsV1;
import com.aidigital.reportconstructor.api.v1.model.AdminSeriesUnitV1;
import com.aidigital.reportconstructor.api.v1.model.AdminStatsV1;
import com.aidigital.reportconstructor.api.v1.model.AdminTokenLabelV1;
import com.aidigital.reportconstructor.api.v1.model.AdminTokenPeriodV1;
import com.aidigital.reportconstructor.api.v1.model.AdminTokenTotalsV1;
import com.aidigital.reportconstructor.api.v1.model.AdminTotalsV1;
import com.aidigital.reportconstructor.api.v1.model.AdminTrendUnitV1;
import com.aidigital.reportconstructor.api.v1.model.AdminTypeStatV1;
import com.aidigital.reportconstructor.api.v1.model.AdminUserStatV1;
import com.aidigital.reportconstructor.service.admin.dto.AdminActiveUsersPeriod;
import com.aidigital.reportconstructor.service.admin.dto.AdminFailedJob;
import com.aidigital.reportconstructor.service.admin.dto.AdminIssueSeverity;
import com.aidigital.reportconstructor.service.admin.dto.AdminRangeView;
import com.aidigital.reportconstructor.service.admin.dto.AdminSavings;
import com.aidigital.reportconstructor.service.admin.dto.AdminStats;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenLabel;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenPeriod;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenTotals;
import com.aidigital.reportconstructor.service.admin.dto.AdminTotals;
import com.aidigital.reportconstructor.service.admin.dto.AdminTypeStat;
import com.aidigital.reportconstructor.service.admin.dto.AdminUserStat;
import com.aidigital.reportconstructor.service.admin.enums.AdminPeriodUnit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the granularity codes, where the service's wire value and the generated enum's constant
 * name differ; mapping them by name turned the whole dashboard into a 500. Also covers the full
 * aggregation payload, field by field, and the null handling of every nested block and list.
 */
class AdminStatsApiMapperTest {

	@Test
	void shouldMapRangeUnitCodeToItsEnumTest() {
		// Given:
		AdminStatsApiMapper mapper = new AdminStatsApiMapperImpl();
		AdminRangeView range = new AdminRangeView(
				LocalDate.of(2026, 7, 8), LocalDate.of(2026, 8, 6), AdminPeriodUnit.WEEK.getCode());

		// When:
		AdminRangeV1 mapped = mapper.toRange(range);

		// Then:
		assertThat(mapped.getSuggestedUnit()).isEqualTo(AdminTrendUnitV1.WEEK);
		assertThat(mapped.getFrom()).isEqualTo(LocalDate.of(2026, 7, 8));
		assertThat(mapped.getTo()).isEqualTo(LocalDate.of(2026, 8, 6));
	}

	@Test
	void shouldMapEverySeriesUnitCodeToItsEnumTest() {
		// Given:
		AdminStatsApiMapper mapper = new AdminStatsApiMapperImpl();

		// When-Then:
		assertThat(mapper.toSeriesUnit(AdminPeriodUnit.DAY.getCode()))
				.isEqualTo(AdminSeriesUnitV1.DAY);
		assertThat(mapper.toSeriesUnit(AdminPeriodUnit.WEEK.getCode()))
				.isEqualTo(AdminSeriesUnitV1.WEEK);
		assertThat(mapper.toSeriesUnit(AdminPeriodUnit.MONTH.getCode()))
				.isEqualTo(AdminSeriesUnitV1.MONTH);
		assertThat(mapper.toSeriesUnit(null)).isNull();
	}

	@Test
	void shouldMapEverySuggestedUnitCodeToItsEnumTest() {
		// Given:
		AdminStatsApiMapper mapper = new AdminStatsApiMapperImpl();

		// When-Then:
		assertThat(mapper.toSuggestedUnit(AdminPeriodUnit.WEEK.getCode())).isEqualTo(AdminTrendUnitV1.WEEK);
		assertThat(mapper.toSuggestedUnit(AdminPeriodUnit.MONTH.getCode())).isEqualTo(AdminTrendUnitV1.MONTH);
		assertThat(mapper.toSuggestedUnit(null)).isNull();
	}

	@Test
	void shouldRejectDayAsSuggestedUnitTest() {
		// Given: the trend unit is week or month only; a day bucket has no trend constant
		AdminStatsApiMapper mapper = new AdminStatsApiMapperImpl();

		// When-Then:
		assertThatThrownBy(() -> mapper.toSuggestedUnit(AdminPeriodUnit.DAY.getCode()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("day");
	}

	@Test
	void shouldRejectUnknownSeriesUnitCodeTest() {
		// Given:
		AdminStatsApiMapper mapper = new AdminStatsApiMapperImpl();

		// When-Then:
		assertThatThrownBy(() -> mapper.toSeriesUnit("quarter"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("quarter");
	}

	@Test
	void shouldMapFullStatsPayloadTest() {
		// Given:
		AdminStatsApiMapper mapper = new AdminStatsApiMapperImpl();
		LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 6, 10, 15);
		LocalDateTime rollupAt = LocalDateTime.of(2026, 8, 6, 9, 0);
		AdminTokenPeriod series = new AdminTokenPeriod("2026-08-03", LocalDate.of(2026, 8, 3), "Aug 3",
				4, 100L, 200L, 30L, 330L, 1.5, null, null, null, null, null, null);
		AdminTokenPeriod week = new AdminTokenPeriod("2026-W32", LocalDate.of(2026, 8, 3), "Aug 3",
				5, 110L, 210L, 31L, 351L, 1.6, 300L, 17.0, 4, 25.0, 1.2, 33.3);
		AdminTokenPeriod month = new AdminTokenPeriod("2026-08", LocalDate.of(2026, 8, 1), "Aug 2026",
				9, 120L, 220L, 32L, 372L, 1.7, 360L, 3.3, 8, 12.5, 1.65, 3.0);
		AdminActiveUsersPeriod activeWeek = new AdminActiveUsersPeriod("2026-W32", LocalDate.of(2026, 8, 3),
				"Aug 3", 6, 2, 5, 20.0);
		AdminActiveUsersPeriod activeMonth = new AdminActiveUsersPeriod("2026-08", LocalDate.of(2026, 8, 1),
				"Aug 2026", 11, 3, null, null);
		AdminStats stats = new AdminStats(
				updatedAt,
				rollupAt,
				new AdminRangeView(LocalDate.of(2026, 7, 8), LocalDate.of(2026, 8, 6),
						AdminPeriodUnit.MONTH.getCode()),
				new AdminTotals(12, 5, 2, 1, 3),
				new AdminSavings(10, 140L, 120L, 14.0, 70.0, 2.5, 67.5, 5062.5, 75.0, 30),
				List.of(new AdminUserStat("user_1", "alice@aidigital.com", "Alice", 7, 98L,
						LocalDateTime.of(2026, 8, 5, 18, 30), 1_000L, 2_000L, 300L, 3_300L, 4.25)),
				List.of(new AdminTypeStat("EOM", 8)),
				List.of(series),
				AdminPeriodUnit.DAY.getCode(),
				new AdminTokenTotals(9, 40L, 1_100L, 2_100L, 310L, 320L, 3_830L, 12.5, 425L, 192L, 233L,
						1.39, 2L, 600L, 0.8, 3L, 150L, 0.4),
				List.of(week),
				List.of(month),
				List.of(activeWeek),
				List.of(activeMonth),
				List.of(new AdminTokenLabel("BatchC", 20L, 2_000L, 6.5, 1L)),
				List.of(new AdminFailedJob(42L, "EOC", "Q3 wrap", "https://slides/42", "bob@aidigital.com", "Bob",
						LocalDateTime.of(2026, 8, 4, 8, 0), LocalDateTime.of(2026, 8, 4, 8, 5), 3, 9,
						"Claude — executive batch (C)", AdminIssueSeverity.WARNING.getCode(), "Degraded",
						List.of("slide 4 missing copy"), 777L, 0.33)));

		// When:
		AdminStatsV1 dto = mapper.toStats(stats);

		// Then:
		assertThat(dto.getUpdatedAt()).isEqualTo(updatedAt);
		assertThat(dto.getRollupUpdatedAt()).isEqualTo(rollupAt);
		assertThat(dto.getRange().getFrom()).isEqualTo(LocalDate.of(2026, 7, 8));
		assertThat(dto.getRange().getTo()).isEqualTo(LocalDate.of(2026, 8, 6));
		assertThat(dto.getRange().getSuggestedUnit()).isEqualTo(AdminTrendUnitV1.MONTH);
		assertThat(dto.getSeriesUnit()).isEqualTo(AdminSeriesUnitV1.DAY);

		AdminTotalsV1 totals = dto.getTotals();
		assertThat(totals.getReports()).isEqualTo(12);
		assertThat(totals.getActiveUsers()).isEqualTo(5);
		assertThat(totals.getNewUsers()).isEqualTo(2);
		assertThat(totals.getRunning()).isEqualTo(1);
		assertThat(totals.getFailed()).isEqualTo(3);

		AdminSavingsV1 savings = dto.getSavings();
		assertThat(savings.getReportsCounted()).isEqualTo(10);
		assertThat(savings.getSlidesTotal()).isEqualTo(140L);
		assertThat(savings.getSlidesMeasured()).isEqualTo(120L);
		assertThat(savings.getAvgSlidesPerReport()).isEqualTo(14.0);
		assertThat(savings.getManualHours()).isEqualTo(70.0);
		assertThat(savings.getAutomationHours()).isEqualTo(2.5);
		assertThat(savings.getSavedHours()).isEqualTo(67.5);
		assertThat(savings.getSavedUsd()).isEqualTo(5062.5);
		assertThat(savings.getHourlyRateUsd()).isEqualTo(75.0);
		assertThat(savings.getMinutesPerSlide()).isEqualTo(30);

		assertThat(dto.getByUser()).singleElement().satisfies(user -> {
			assertThat(user.getUserId()).isEqualTo("user_1");
			assertThat(user.getEmail()).isEqualTo("alice@aidigital.com");
			assertThat(user.getName()).isEqualTo("Alice");
			assertThat(user.getTotal()).isEqualTo(7);
			assertThat(user.getSlides()).isEqualTo(98L);
			assertThat(user.getLastActivity()).isEqualTo(LocalDateTime.of(2026, 8, 5, 18, 30));
			assertThat(user.getInputTokens()).isEqualTo(1_000L);
			assertThat(user.getOutputTokens()).isEqualTo(2_000L);
			assertThat(user.getCacheTokens()).isEqualTo(300L);
			assertThat(user.getTotalTokens()).isEqualTo(3_300L);
			assertThat(user.getCostUsd()).isEqualTo(4.25);
		});
		assertThat(dto.getByType()).singleElement().satisfies(type -> {
			assertThat(type.getType()).isEqualTo("EOM");
			assertThat(type.getCount()).isEqualTo(8);
		});

		assertThat(dto.getSeries()).singleElement().satisfies(period -> {
			assertThat(period.getKey()).isEqualTo("2026-08-03");
			assertThat(period.getStart()).isEqualTo(LocalDate.of(2026, 8, 3));
			assertThat(period.getLabel()).isEqualTo("Aug 3");
			assertThat(period.getReports()).isEqualTo(4);
			assertThat(period.getInputTokens()).isEqualTo(100L);
			assertThat(period.getOutputTokens()).isEqualTo(200L);
			assertThat(period.getCacheTokens()).isEqualTo(30L);
			assertThat(period.getTotalTokens()).isEqualTo(330L);
			assertThat(period.getCostUsd()).isEqualTo(1.5);
			assertThat(period.getPrevTotalTokens()).isNull();
			assertThat(period.getTokensDeltaPct()).isNull();
			assertThat(period.getPrevReports()).isNull();
			assertThat(period.getReportsDeltaPct()).isNull();
			assertThat(period.getPrevCostUsd()).isNull();
			assertThat(period.getCostDeltaPct()).isNull();
		});
		assertThat(dto.getTokensByWeek()).singleElement().satisfies(period -> {
			assertThat(period.getKey()).isEqualTo("2026-W32");
			assertThat(period.getTotalTokens()).isEqualTo(351L);
			assertThat(period.getPrevTotalTokens()).isEqualTo(300L);
			assertThat(period.getTokensDeltaPct()).isEqualTo(17.0);
			assertThat(period.getPrevReports()).isEqualTo(4);
			assertThat(period.getReportsDeltaPct()).isEqualTo(25.0);
			assertThat(period.getPrevCostUsd()).isEqualTo(1.2);
			assertThat(period.getCostDeltaPct()).isEqualTo(33.3);
		});
		assertThat(dto.getTokensByMonth()).singleElement().satisfies(period -> {
			assertThat(period.getKey()).isEqualTo("2026-08");
			assertThat(period.getLabel()).isEqualTo("Aug 2026");
			assertThat(period.getReports()).isEqualTo(9);
			assertThat(period.getPrevTotalTokens()).isEqualTo(360L);
		});

		assertThat(dto.getActiveUsersWeeks()).singleElement().satisfies(period -> {
			assertThat(period.getKey()).isEqualTo("2026-W32");
			assertThat(period.getStart()).isEqualTo(LocalDate.of(2026, 8, 3));
			assertThat(period.getLabel()).isEqualTo("Aug 3");
			assertThat(period.getActiveUsers()).isEqualTo(6);
			assertThat(period.getNewUsers()).isEqualTo(2);
			assertThat(period.getPrevActive()).isEqualTo(5);
			assertThat(period.getDeltaPct()).isEqualTo(20.0);
		});
		assertThat(dto.getActiveUsersMonths()).singleElement().satisfies(period -> {
			assertThat(period.getKey()).isEqualTo("2026-08");
			assertThat(period.getActiveUsers()).isEqualTo(11);
			assertThat(period.getPrevActive()).isNull();
			assertThat(period.getDeltaPct()).isNull();
		});

		AdminTokenTotalsV1 tokens = dto.getTokens();
		assertThat(tokens.getReportsWithUsage()).isEqualTo(9);
		assertThat(tokens.getClaudeCalls()).isEqualTo(40L);
		assertThat(tokens.getInputTokens()).isEqualTo(1_100L);
		assertThat(tokens.getOutputTokens()).isEqualTo(2_100L);
		assertThat(tokens.getCacheWriteTokens()).isEqualTo(310L);
		assertThat(tokens.getCacheReadTokens()).isEqualTo(320L);
		assertThat(tokens.getTotalTokens()).isEqualTo(3_830L);
		assertThat(tokens.getCostUsd()).isEqualTo(12.5);
		assertThat(tokens.getAvgTokensPerReport()).isEqualTo(425L);
		assertThat(tokens.getAvgInputPerReport()).isEqualTo(192L);
		assertThat(tokens.getAvgOutputPerReport()).isEqualTo(233L);
		assertThat(tokens.getAvgCostPerReportUsd()).isEqualTo(1.39);
		assertThat(tokens.getUnknownCalls()).isEqualTo(2L);
		assertThat(tokens.getEstimatedTokens()).isEqualTo(600L);
		assertThat(tokens.getEstimatedCostUsd()).isEqualTo(0.8);
		assertThat(tokens.getUnattributedCalls()).isEqualTo(3L);
		assertThat(tokens.getUnattributedTokens()).isEqualTo(150L);
		assertThat(tokens.getUnattributedCostUsd()).isEqualTo(0.4);

		assertThat(dto.getByLabel()).singleElement().satisfies(label -> {
			assertThat(label.getLabel()).isEqualTo("BatchC");
			assertThat(label.getCalls()).isEqualTo(20L);
			assertThat(label.getTotalTokens()).isEqualTo(2_000L);
			assertThat(label.getCostUsd()).isEqualTo(6.5);
			assertThat(label.getUnknownCalls()).isEqualTo(1L);
		});

		assertThat(dto.getFailures()).singleElement().satisfies(job -> {
			assertThat(job.getJobId()).isEqualTo(42L);
			assertThat(job.getType()).isEqualTo("EOC");
			assertThat(job.getTitle()).isEqualTo("Q3 wrap");
			assertThat(job.getSlideUrl()).isEqualTo("https://slides/42");
			assertThat(job.getOwnerEmail()).isEqualTo("bob@aidigital.com");
			assertThat(job.getOwnerName()).isEqualTo("Bob");
			assertThat(job.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 4, 8, 0));
			assertThat(job.getFailedAt()).isEqualTo(LocalDateTime.of(2026, 8, 4, 8, 5));
			assertThat(job.getStep()).isEqualTo(3);
			assertThat(job.getTotal()).isEqualTo(9);
			assertThat(job.getStepLabel()).isEqualTo("Claude — executive batch (C)");
			assertThat(job.getSeverity()).isEqualTo("warning");
			assertThat(job.getErrorMessage()).isEqualTo("Degraded");
			assertThat(job.getWarnings()).containsExactly("slide 4 missing copy");
			assertThat(job.getTotalTokens()).isEqualTo(777L);
			assertThat(job.getCostUsd()).isEqualTo(0.33);
		});
	}

	@Test
	void shouldLeaveEveryNestedBlockAndListNullWhenTheAggregationHasNoneTest() {
		// Given:
		AdminStatsApiMapper mapper = new AdminStatsApiMapperImpl();
		AdminStats stats = new AdminStats(null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null);

		// When:
		AdminStatsV1 dto = mapper.toStats(stats);

		// Then:
		assertThat(dto.getUpdatedAt()).isNull();
		assertThat(dto.getRollupUpdatedAt()).isNull();
		assertThat(dto.getRange()).isNull();
		assertThat(dto.getTotals()).isNull();
		assertThat(dto.getSavings()).isNull();
		assertThat(dto.getByUser()).isNull();
		assertThat(dto.getByType()).isNull();
		assertThat(dto.getSeries()).isNull();
		assertThat(dto.getSeriesUnit()).isNull();
		assertThat(dto.getTokens()).isNull();
		assertThat(dto.getTokensByWeek()).isNull();
		assertThat(dto.getTokensByMonth()).isNull();
		assertThat(dto.getActiveUsersWeeks()).isNull();
		assertThat(dto.getActiveUsersMonths()).isNull();
		assertThat(dto.getByLabel()).isNull();
		assertThat(dto.getFailures()).isNull();
	}

	@Test
	void shouldKeepNullRowsInsideListsTest() {
		// Given: lists that carry a null row each
		AdminStatsApiMapper mapper = new AdminStatsApiMapperImpl();
		List<AdminTokenPeriod> periods = Collections.singletonList(null);
		AdminStats stats = new AdminStats(null, null, null, null, null,
				Collections.singletonList(null), Collections.singletonList(null),
				periods, null, null, periods, periods,
				Collections.singletonList(null), Collections.singletonList(null),
				Collections.singletonList(null), Collections.singletonList(null));

		// When:
		AdminStatsV1 dto = mapper.toStats(stats);

		// Then:
		assertThat(dto.getByUser()).containsExactly((AdminUserStatV1) null);
		assertThat(dto.getByType()).containsExactly((AdminTypeStatV1) null);
		assertThat(dto.getSeries()).containsExactly((AdminTokenPeriodV1) null);
		assertThat(dto.getTokensByWeek()).containsExactly((AdminTokenPeriodV1) null);
		assertThat(dto.getTokensByMonth()).containsExactly((AdminTokenPeriodV1) null);
		assertThat(dto.getActiveUsersWeeks()).containsExactly((AdminActiveUsersPeriodV1) null);
		assertThat(dto.getActiveUsersMonths()).containsExactly((AdminActiveUsersPeriodV1) null);
		assertThat(dto.getByLabel()).containsExactly((AdminTokenLabelV1) null);
		assertThat(dto.getFailures()).containsExactly((AdminFailedJobV1) null);
	}

	@Test
	void shouldMapFailedJobWithoutWarningsToAnEmptyWarningListTest() {
		// Given: a hard failure carries no warnings list at all
		AdminStatsApiMapper mapper = new AdminStatsApiMapperImpl();
		AdminFailedJob failedJob = new AdminFailedJob(7L, null, "Broken run", null, "carol@aidigital.com",
				"Carol", LocalDateTime.of(2026, 8, 1, 12, 0), LocalDateTime.of(2026, 8, 1, 12, 1), 2, 9,
				"Sheet read", AdminIssueSeverity.ERROR.getCode(), "Boom", null, 0L, 0.0);

		// When:
		AdminFailedJobV1 dto = mapper.toFailedJob(failedJob);

		// Then:
		assertThat(dto.getJobId()).isEqualTo(7L);
		assertThat(dto.getType()).isNull();
		assertThat(dto.getSlideUrl()).isNull();
		assertThat(dto.getSeverity()).isEqualTo("error");
		assertThat(dto.getErrorMessage()).isEqualTo("Boom");
		assertThat(dto.getWarnings()).isEmpty();
	}

	@Test
	void shouldCopyFailedJobWarningsIntoANewListTest() {
		// Given:
		AdminStatsApiMapper mapper = new AdminStatsApiMapperImpl();
		List<String> warnings = List.of("slide 2", "slide 5");
		AdminFailedJob failedJob = new AdminFailedJob(8L, "EOM", "Run", null, null, null, null, null, 9, 9,
				"Done", AdminIssueSeverity.WARNING.getCode(), "Degraded", warnings, 10L, 0.01);

		// When:
		AdminFailedJobV1 dto = mapper.toFailedJob(failedJob);

		// Then:
		assertThat(dto.getWarnings()).containsExactly("slide 2", "slide 5").isNotSameAs(warnings);
	}

	@Test
	void shouldMapRangeWithoutSuggestedUnitTest() {
		// Given:
		AdminStatsApiMapper mapper = new AdminStatsApiMapperImpl();
		AdminRangeView range = new AdminRangeView(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), null);

		// When:
		AdminRangeV1 dto = mapper.toRange(range);

		// Then:
		assertThat(dto.getFrom()).isEqualTo(LocalDate.of(2026, 8, 1));
		assertThat(dto.getTo()).isEqualTo(LocalDate.of(2026, 8, 2));
		assertThat(dto.getSuggestedUnit()).isNull();
	}

	@Test
	void shouldReturnNullForEveryNullSourceTest() {
		// Given:
		AdminStatsApiMapper mapper = new AdminStatsApiMapperImpl();

		// When-Then:
		assertThat(mapper.toStats(null)).isNull();
		assertThat(mapper.toTotals(null)).isNull();
		assertThat(mapper.toUserStat(null)).isNull();
		assertThat(mapper.toTypeStat(null)).isNull();
		assertThat(mapper.toTokenTotals(null)).isNull();
		assertThat(mapper.toTokenLabel(null)).isNull();
		assertThat(mapper.toRange(null)).isNull();
		assertThat(mapper.toSavings(null)).isNull();
		assertThat(mapper.toTokenPeriod(null)).isNull();
		assertThat(mapper.toActiveUsersPeriod(null)).isNull();
		assertThat(mapper.toFailedJob(null)).isNull();
	}
}
