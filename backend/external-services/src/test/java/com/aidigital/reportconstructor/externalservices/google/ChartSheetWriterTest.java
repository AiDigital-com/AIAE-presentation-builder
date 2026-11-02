package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChartSheetWriterTest {

	private final ChartSheetWriter writer = new ChartSheetWriter();

	@Test
	void dailyComboRateRatio_isDecimalNotPercentPoints() {
		assertThat(writer.dailyComboRateRatio(1000, 25)).isEqualTo(0.025);
		double legacyBugPercentPoints = (25.0 / 1000.0) * 100.0;
		assertThat(writer.dailyComboRateRatio(1000, 25)).isNotEqualTo(legacyBugPercentPoints);
	}

	@Test
	void shouldWriteDailyRateColumn_falseLeavesColumnCToTemplateFormula() {
		assertThat(writer.shouldWriteDailyRateColumn()).isFalse();
	}

	@Test
	void normalizeBreakdownLabel_foldsCtvSpellingsAndStripsCaseAndSpaceTest() {
		// Given-Then: the "Breakdowns" tab writes "CTV" while the chart source uses "Connected TV" — both
		// must resolve to the same key, and age buckets pass through unchanged
		assertThat(writer.normalizeBreakdownLabel("CTV")).isEqualTo("connectedtv");
		assertThat(writer.normalizeBreakdownLabel("Connected TV")).isEqualTo("connectedtv");
		assertThat(writer.normalizeBreakdownLabel("Mobile")).isEqualTo("mobile");
		assertThat(writer.normalizeBreakdownLabel(" 25-34 ")).isEqualTo("25-34");
		assertThat(writer.normalizeBreakdownLabel(null)).isEmpty();
	}

	@Test
	void shouldWriteMatchedDevicesAndClearTheSampleOnesTest() {
		// Given: the device workbook's column A — header, three sample devices, a gap, then a stray cell — and
		// a tactic that served mobile and desktop only
		List<List<Object>> columnA = List.of(
				List.of("Device"), List.of("Mobile"), List.of("Desktop"), List.of("Tablet"), List.of(),
				List.of("#REF!"));
		Map<String, Long> imps = Map.of("mobile", 48_215L, "desktop", 32_144L);

		// When:
		List<ValueRange> updates = writer.breakdownImpressionUpdates(columnA, "Sheet1", imps);

		// Then: the two matches get their impressions, the tablet sample is blanked, the header and the cell
		// below the block stay as they are
		assertThat(updates).extracting(ValueRange::getRange)
				.containsExactly("Sheet1!B2", "Sheet1!B3", "Sheet1!B4");
		assertThat(updates).extracting(vr -> vr.getValues().getFirst().getFirst())
				.containsExactly(48_215L, 32_144L, "");
	}

	@Test
	void shouldPlanOnlyBlanksWhenNoCategoryMatchesTest() {
		// Given: age buckets offered to a workbook that lists devices
		List<List<Object>> columnA = List.of(List.of("Device"), List.of("Mobile"), List.of("Desktop"));
		Map<String, Long> imps = Map.of("25-34", 1_000L);

		// When:
		List<ValueRange> updates = writer.breakdownImpressionUpdates(columnA, "Sheet1", imps);

		// Then: nothing carries a value, which is how the writer knows to skip the write and report no match
		assertThat(updates).extracting(vr -> vr.getValues().getFirst().getFirst()).containsOnly("");
	}

	@Test
	void rateColumnValuesForPivot_emptyWhenTemplateOwnsFormula() {
		Pivot pivot = pivotRow(1000, 25, 0);
		assertThat(writer.rateColumnValuesForPivot(pivot, true)).isEmpty();
	}

	@Test
	void rateColumnValuesForPivot_whenForcedWriteUsesRatioNotTimes100() {
		Pivot pivot = pivotRow(1000, 25, 0);
		Optional<List<List<Object>>> rates = writer.rateColumnValuesForPivot(pivot, true, true);
		assertThat(rates).isPresent();
		assertThat(rates.get().getFirst().getFirst()).isEqualTo(0.025);
	}

	private static Pivot pivotRow(double imps, double clicks, double completions) {
		LinkedHashMap<String, double[]> data = new LinkedHashMap<>();
		data.put("2026-01-01", new double[]{imps, clicks, completions});
		return new Pivot(data, clicks > 0, completions > 0);
	}
}
