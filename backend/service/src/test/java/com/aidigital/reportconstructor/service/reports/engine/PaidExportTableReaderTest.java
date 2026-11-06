package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.enums.PaidExportColumn;
import com.aidigital.reportconstructor.service.reports.enums.PaidExportTab;
import com.aidigital.reportconstructor.service.reports.helpers.impl.ReportNumberParserImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.SheetRowHelperImpl;
import com.aidigital.reportconstructor.service.reports.model.PaidMetrics;
import com.aidigital.reportconstructor.service.reports.model.PaidTable;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PaidExportTableReaderTest {

	@Test
	void shouldReadTheSearchFullFlightTabByHeaderTextTest() {
		// Given: the Google Ads FULL-FLIGHT layout (cut from the real export): header, zero row, daily rows, padding
		PaidExportTableReader reader = new PaidExportTableReader(new ReportNumberParserImpl(), new SheetRowHelperImpl());
		List<String> header = List.of("Table Info", "Date", "Goal", "Channel", "Channel_Short_Name", "Level 1 Naming",
				"Impressions", "Clicks", "Cost", "Conversions", "CPC", "CPA_Conversions");
		Map<String, List<List<String>>> tabs = Map.of("FULL-FLIGHT PERFORMANCE", List.of(
				header,
				List.of("", "", "", "", "", "", "0", "0", "$0.00", "0", "$0.00", "0"),
				List.of("", "2026-10-07", "Conversions", "Google Search", "Google Search", "Brand",
						"2,296", "34", "$9.34", "24", "$0.27", ""),
				List.of("", "", "", "", "", "", "", "", "", "", "", "")));

		// When:
		Optional<PaidTable> table = reader.read(tabs, PaidExportTab.FULL_FLIGHT);

		// Then: columns are found by exact header text, so Channel and Conversions skip their look-alikes
		assertThat(table).isPresent();
		assertThat(table.get().columns())
				.containsEntry(PaidExportColumn.DATE, 1)
				.containsEntry(PaidExportColumn.CHANNEL, 3)
				.containsEntry(PaidExportColumn.LEVEL_1_NAMING, 5)
				.containsEntry(PaidExportColumn.CONVERSIONS, 9);
		// And: the blank padding is gone; the undated zero row stays (the collector drops it by its missing date)
		assertThat(table.get().rows()).hasSize(2);
		List<String> day = table.get().rows().get(1);
		assertThat(reader.metrics(table.get(), day)).isEqualTo(new PaidMetrics(2296, 34, 9.34, 24));
		assertThat(reader.date(table.get(), day)).isEqualTo(LocalDate.of(2026, 10, 7));
		assertThat(reader.date(table.get(), table.get().rows().get(0))).isNull();
	}

	@Test
	void shouldFindABreakdownHeaderUnderItsTitleAndDropTheTotalRowTest() {
		// Given: a Meta breakdown tab: title row, header row (mixed case), data, the export's own TOTAL row
		PaidExportTableReader reader = new PaidExportTableReader(new ReportNumberParserImpl(), new SheetRowHelperImpl());
		Map<String, List<List<String>>> tabs = Map.of(" audience targeting breakdown ", List.of(
				List.of("AUDIENCE TARGETING BREAKDOWN"),
				List.of("Ad Set", "Impressions", "Clicks", "CTR", "CPC", "Conversions", "CPA", "Cost"),
				List.of("Noventra Meta - LAL", "411,573", "2,528", "0.61%", "$1.11", "30", "$93.80", "$2,813.97"),
				List.of("META TOTAL", "1,286,167", "7,899", "0.61%", "$1.11", "90", "$97.71", "$8,793.67")));

		// When: the tab is looked up by its canonical name
		Optional<PaidTable> table = reader.read(tabs, PaidExportTab.AUDIENCE_TARGETING_BREAKDOWN);

		// Then:
		assertThat(table).isPresent();
		assertThat(table.get().rows()).hasSize(1);
		List<String> row = table.get().rows().get(0);
		assertThat(reader.text(table.get(), row, PaidExportColumn.AD_SET)).isEqualTo("Noventra Meta - LAL");
		assertThat(reader.metrics(table.get(), row)).isEqualTo(new PaidMetrics(411_573, 2_528, 2_813.97, 30));
	}

	@Test
	void shouldRejectATabWithoutTheRequiredColumnsTest() {
		// Given: a FULL-FLIGHT tab with no Conversions column
		PaidExportTableReader reader = new PaidExportTableReader(new ReportNumberParserImpl(), new SheetRowHelperImpl());
		Map<String, List<List<String>>> tabs = Map.of("FULL-FLIGHT PERFORMANCE", List.of(
				List.of("Date", "Impressions", "Clicks", "Cost"),
				List.of("2026-10-07", "59", "6", "$34.57")));

		// When:
		Optional<PaidTable> table = reader.read(tabs, PaidExportTab.FULL_FLIGHT);

		// Then:
		assertThat(table).isEmpty();
	}

	@Test
	void shouldReadAnUnformattedDateSerialTest() {
		// Given: a Date cell that came through as a sheet serial number
		PaidExportTableReader reader = new PaidExportTableReader(new ReportNumberParserImpl(), new SheetRowHelperImpl());
		PaidTable table = new PaidTable(Map.of(PaidExportColumn.DATE, 0), List.of());

		// When:
		LocalDate date = reader.date(table, List.of("46302"));

		// Then:
		assertThat(date).isEqualTo(LocalDate.of(2026, 10, 7));
	}
}
