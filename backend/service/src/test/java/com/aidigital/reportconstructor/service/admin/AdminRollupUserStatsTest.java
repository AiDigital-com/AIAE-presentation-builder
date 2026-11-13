package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.projections.JobOwner;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyUserRow;
import com.aidigital.reportconstructor.service.admin.dto.AdminUserStat;
import com.aidigital.reportconstructor.service.common.text.DisplayNameHelper;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeCostCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRollupUserStatsTest {

	@Mock
	private ClaudeCostCalculator costCalculator;

	@Mock
	private DisplayNameHelper displayNameHelper;

	@Test
	void shouldBuildOneRowPerUserSortedByReportCountTest() {
		// Given: u1 has a countable day and an intermediate sheet step; u2 has no recorded last activity;
		// u3 has no owner row at all; u9 owns jobs but has no usage in the window.
		AdminRollupUserStats stats = new AdminRollupUserStats(
				new RollupUsageMath(costCalculator, new ReportCountPolicy()), displayNameHelper);
		List<UsageDailyUserRow> rows = List.of(
				new UsageDailyUserRow("u1", "EOM", "SLIDES", "m", 3L, 3L, 3L, 100L, 50L, 10L, 5L, 30L, 3L, 90L),
				new UsageDailyUserRow("u1", "EOM", "SHEET", "m", 1L, 1L, 1L, 20L, 0L, 0L, 0L, 0L, 0L, 10L),
				new UsageDailyUserRow("u2", "EOC", "SLIDES", "m", 1L, 1L, 1L, 10L, 5L, 0L, 0L, 5L, 1L, 20L),
				new UsageDailyUserRow("u3", "PDI", "SLIDES", "m", 5L, 5L, 1L, 1L, 1L, null, null, 8L, 5L, 5L));
		List<JobOwner> owners = List.of(
				new JobOwner("u1", "jane.doe@aidigital.com", OffsetDateTime.parse("2026-04-02T09:30:00Z")),
				new JobOwner("u2", "bob@aidigital.com", null),
				new JobOwner("u9", "idle@aidigital.com", OffsetDateTime.parse("2026-01-01T00:00:00Z")));
		when(costCalculator.costUsd(100L, 50L, 10L, 5L, "m")).thenReturn(1.0d);
		when(costCalculator.costUsd(20L, 0L, 0L, 0L, "m")).thenReturn(0.25d);
		when(costCalculator.costUsd(10L, 5L, 0L, 0L, "m")).thenReturn(0.125d);
		when(costCalculator.costUsd(1L, 1L, 0L, 0L, "m")).thenReturn(0.5d);
		when(displayNameHelper.fromEmail("jane.doe@aidigital.com")).thenReturn("Jane Doe");
		when(displayNameHelper.fromEmail("bob@aidigital.com")).thenReturn("Bob");
		when(displayNameHelper.fromEmail(null)).thenReturn(null);

		// When:
		List<AdminUserStat> result = stats.build(rows, owners);

		// Then: the sheet step adds tokens and spend but not reports; users without usage are absent.
		assertThat(result).containsExactly(
				new AdminUserStat("u3", null, null, 5, 8L, null, 1L, 1L, 0L, 2L, 0.5d),
				new AdminUserStat("u1", "jane.doe@aidigital.com", "Jane Doe", 3, 30L,
						LocalDateTime.of(2026, 4, 2, 9, 30), 120L, 50L, 15L, 185L, 1.25d),
				new AdminUserStat("u2", "bob@aidigital.com", "Bob", 1, 5L, null, 10L, 5L, 0L, 15L, 0.125d));
	}

	@Test
	void shouldReturnNoRowsWhenThereIsNoUsageTest() {
		// Given:
		AdminRollupUserStats stats = new AdminRollupUserStats(
				new RollupUsageMath(costCalculator, new ReportCountPolicy()), displayNameHelper);

		// When:
		List<AdminUserStat> result = stats.build(List.of(), List.of());

		// Then:
		assertThat(result).isEmpty();
	}
}
