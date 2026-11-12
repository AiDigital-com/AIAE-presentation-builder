package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.externalservices.google.charts.PaidChartKind;
import com.aidigital.reportconstructor.externalservices.google.charts.model.PaidChart;
import com.google.api.services.sheets.v4.model.BasicChartSpec;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.SpreadsheetTheme;
import com.google.api.services.sheets.v4.model.ThemeColorPair;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PaidChartSheetRequestsTest {

	private final PaidChartSheetRequests requests = new PaidChartSheetRequests();

	@Test
	void shouldWriteTheMonthlyDataAndAComboChartWithCtrOnTheRightAxisTest() {
		// Given:
		PaidChart chart = new PaidChart(PaidChartKind.SEARCH_CLICKS_CTR, List.of("Oct", "Nov"),
				List.of(400d, 500d), List.of(0.04, 0.025), null);

		// When:
		List<Request> batch = requests.requests(chart, PaidChartStyle.CLICKS_CTR, 264, 143);

		// Then: theme, then a header row and one row per month, then the chart last
		assertThat(batch).hasSize(3);
		var rows = batch.get(1).getUpdateCells().getRows();
		assertThat(rows).hasSize(3);
		assertThat(rows.get(0).getValues().stream().map(c -> c.getUserEnteredValue().getStringValue()).toList())
				.containsExactly("Month", "Clicks (bars)", "CTR (line)");
		CellData ctr = rows.get(1).getValues().get(2);
		assertThat(ctr.getUserEnteredValue().getNumberValue()).isEqualTo(0.04);
		assertThat(ctr.getUserEnteredFormat().getNumberFormat().getPattern()).isEqualTo("0.0%");
		var chartRequest = batch.get(2).getAddChart().getChart();
		assertThat(chartRequest.getPosition().getOverlayPosition().getWidthPixels()).isEqualTo(264);
		BasicChartSpec spec = chartRequest.getSpec().getBasicChart();
		assertThat(spec.getChartType()).isEqualTo("COMBO");
		assertThat(spec.getHeaderCount()).isEqualTo(1);
		assertThat(spec.getSeries()).extracting("type", "targetAxis")
				.containsExactly(org.assertj.core.groups.Tuple.tuple("COLUMN", "LEFT_AXIS"),
						org.assertj.core.groups.Tuple.tuple("LINE", "RIGHT_AXIS"));
		assertThat(spec.getSeries().get(1).getSeries().getSourceRange().getSources().get(0).getEndRowIndex())
				.isEqualTo(3);
	}

	@Test
	void shouldDrawADonutWhoseSliceColoursAreTheThemeAccentsInLegendOrderTest() {
		// Given: a placement donut with an "Other" slice past the five-colour palette
		PaidChart chart = new PaidChart(PaidChartKind.SOCIAL_PLACEMENT_DONUT,
				List.of("Reels", "Feed", "Stories", "Explore", "Audience Network", "Other"),
				List.of(60d, 20d, 10d, 5d, 3d, 2d), List.of(), "100");

		// When:
		List<Request> batch = requests.requests(chart, PaidChartStyle.PLACEMENT_DONUT, 280, 280);

		// Then:
		SpreadsheetTheme theme = batch.get(0).getUpdateSpreadsheetProperties().getProperties().getSpreadsheetTheme();
		List<String> accents = theme.getThemeColors().stream()
				.filter(p -> p.getColorType().startsWith("ACCENT"))
				.map(ThemeColorPair::getColorType).collect(Collectors.toList());
		assertThat(accents).containsExactly("ACCENT1", "ACCENT2", "ACCENT3", "ACCENT4", "ACCENT5", "ACCENT6");
		ThemeColorPair accent1 = theme.getThemeColors().stream()
				.filter(p -> "ACCENT1".equals(p.getColorType())).findFirst().orElseThrow();
		assertThat(accent1.getColor().getRgbColor().getRed()).isCloseTo(0xF3 / 255f, within(1e-6f));
		assertThat(accent1.getColor().getRgbColor().getGreen()).isCloseTo(0x6C / 255f, within(1e-6f));
		var pie = batch.get(2).getAddChart().getChart().getSpec().getPieChart();
		assertThat(pie.getLegendPosition()).isEqualTo("NO_LEGEND");
		assertThat(pie.getPieHole()).isEqualTo(0.55);
		assertThat(pie.getSeries().getSourceRange().getSources().get(0).getStartRowIndex()).isEqualTo(1);
		assertThat(pie.getSeries().getSourceRange().getSources().get(0).getEndRowIndex()).isEqualTo(7);
	}
}
