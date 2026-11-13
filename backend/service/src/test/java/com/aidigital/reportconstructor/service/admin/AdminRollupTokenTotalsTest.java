package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.projections.ClaudeLabelUsage;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyBucket;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenTotals;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeCostCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRollupTokenTotalsTest {

	@Mock
	private ClaudeCostCalculator costCalculator;

	@Test
	void shouldCombineRollupUnattributedAndLostCallsIntoTheHeadlineTest() {
		// Given: a countable slide-deck day, the same flow's intermediate sheet step (null aggregates
		// included), an unattributed measured call plus an estimated one, and a lost call by label.
		AdminRollupTokenTotals totals = new AdminRollupTokenTotals(
				new RollupUsageMath(costCalculator, new ReportCountPolicy()), costCalculator);
		LocalDate day = LocalDate.of(2026, 5, 1);
		UsageDailyBucket slides = new UsageDailyBucket(day, "EOM", "SLIDES", "m1",
				2L, 2L, 0L, 3L, 100L, 50L, 10L, 5L, 12L, 2L, 60L);
		UsageDailyBucket sheetStep = new UsageDailyBucket(day, "EOM", "SHEET", "m1",
				2L, 2L, 0L, 1L, null, 20L, null, null, 0L, 0L, 30L);
		ClaudeLabelUsage unattributedMeasured = new ClaudeLabelUsage(
				"line-item-match", "recorded", "m2", 2L, 30L, 10L, 0L, 0L);
		ClaudeLabelUsage unattributedEstimated = new ClaudeLabelUsage(
				"line-item-match", "estimated", "m2", 9L, 900L, 0L, 0L, 0L);
		ClaudeLabelUsage measuredByLabel = new ClaudeLabelUsage(
				"thoughts", "recorded", "m3", 7L, 700L, 70L, 0L, 0L);
		ClaudeLabelUsage lostByLabel = new ClaudeLabelUsage(
				"thoughts", "estimated", "m3", 4L, 400L, null, null, null);
		when(costCalculator.costUsd(100L, 50L, 10L, 5L, "m1")).thenReturn(1.0d);
		when(costCalculator.costUsd(0L, 20L, 0L, 0L, "m1")).thenReturn(0.5d);
		when(costCalculator.costUsd(30L, 10L, 0L, 0L, "m2")).thenReturn(0.25d);
		when(costCalculator.costUsd(400L, 0L, 0L, 0L, "m3")).thenReturn(2.0d);

		// When:
		AdminTokenTotals result = totals.totals(
				List.of(slides, sheetStep),
				List.of(unattributedMeasured, unattributedEstimated),
				List.of(measuredByLabel, lostByLabel));

		// Then: the sheet step adds tokens but not reports; unattributed measured spend joins the headline;
		// the estimated rows stay out of it and are reported beside it instead.
		assertThat(result).isEqualTo(new AdminTokenTotals(
				2, 6L, 130L, 80L, 10L, 5L, 225L, 1.75d,
				112L, 72L, 40L, 0.875d,
				4L, 400L, 2.0d,
				2L, 40L, 0.25d));
	}

	@Test
	void shouldReportZeroAveragesWhenNoReportCarriesUsageTest() {
		// Given: nothing at all in the window.
		AdminRollupTokenTotals totals = new AdminRollupTokenTotals(
				new RollupUsageMath(costCalculator, new ReportCountPolicy()), costCalculator);

		// When:
		AdminTokenTotals result = totals.totals(List.of(), List.of(), List.of());

		// Then: averages fall back to zero rather than dividing by zero.
		assertThat(result).isEqualTo(new AdminTokenTotals(
				0, 0L, 0L, 0L, 0L, 0L, 0L, 0d,
				0L, 0L, 0L, 0d,
				0L, 0L, 0d,
				0L, 0L, 0d));
		verifyNoInteractions(costCalculator);
	}
}
