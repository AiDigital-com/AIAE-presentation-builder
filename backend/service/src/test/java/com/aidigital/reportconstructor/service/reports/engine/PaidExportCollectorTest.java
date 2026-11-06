package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.enums.PaidChannelKind;
import com.aidigital.reportconstructor.service.reports.enums.PaidExportTab;
import com.aidigital.reportconstructor.service.reports.helpers.impl.ReportNumberParserImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.SheetRowHelperImpl;
import com.aidigital.reportconstructor.service.reports.model.PaidChannelData;
import com.aidigital.reportconstructor.service.reports.model.PaidMetrics;
import com.aidigital.reportconstructor.service.reports.model.PaidMonth;
import com.aidigital.reportconstructor.service.reports.model.PaidRow;
import org.assertj.core.util.DoubleComparator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PaidExportCollectorTest {

	@Test
	void shouldSumSearchCampaignsFromFullFlightAndRankThemTest() {
		// Given: a Google Ads export with FULL-FLIGHT over two months, a keyword tab and a gender tab
		PaidExportCollector collector = new PaidExportCollector(
				new PaidExportTableReader(new ReportNumberParserImpl(), new SheetRowHelperImpl()));
		List<String> header = List.of("Date", "Level 1 Naming", "Impressions", "Clicks", "Cost", "Conversions");
		Map<String, List<List<String>>> tabs = Map.of(
				"FULL-FLIGHT PERFORMANCE", List.of(
						header,
						List.of("", "", "0", "0", "$0.00", "0"),
						List.of("2026-09-30", "Brand", "100", "10", "$50.00", "5"),
						List.of("2026-10-07", "Brand", "59", "6", "$34.57", "4"),
						List.of("2026-10-07", "Performance Max", "2,296", "34", "$9.34", "24"),
						List.of("2026-10-08", "NonBrand", "129", "11", "$55.62", "10")),
				"KEYWORD BREAKDOWN", List.of(
						List.of("KEYWORD BREAKDOWN"),
						List.of("Keyword", "Campaign", "Impressions", "Clicks", "CTR", "CPC", "Conversions", "Cost"),
						List.of("\"Velmora shop\"", "Brand", "137", "9", "6.57%", "$6.16", "8.02", "$55.46"),
						List.of("\"Velmora\"", "Brand", "1,894", "204", "10.77%", "$5.77", "186.96", "$1,176.82")),
				"GENDER SPLIT", List.of(
						List.of("GENDER SPLIT"),
						List.of("Gender", "Impressions", "Clicks", "CTR", "Conversions", "CPC", "CPA", "Cost"),
						List.of("Female", "1,068,428", "27,549", "2.58%", "20,137.57", "$0.39", "$0.54", "$10,834.50"),
						List.of("Male", "828,689", "21,782", "2.63%", "15,958.45", "$0.48", "$0.66", "$10,472.82"),
						List.of("GOOGLE TOTAL", "2,101,437", "53,688", "2.55%", "39,394.00", "$0.46", "$0.62",
								"$24,429.84")));

		// When: no date window (EOC, whole range)
		Optional<PaidChannelData> data = collector.collect(PaidChannelKind.SEARCH, tabs, null);

		// Then: totals are the FULL-FLIGHT sums, campaigns rank by conversions and add up to the total
		assertThat(data).isPresent();
		PaidChannelData d = data.get();
		assertThat(d.total())
				.usingRecursiveComparison().withComparatorForType(new DoubleComparator(1e-6), Double.class)
				.isEqualTo(new PaidMetrics(2_584, 61, 149.53, 43));
		assertThat(d.campaigns()).extracting(PaidRow::name).containsExactly("Performance Max", "NonBrand", "Brand");
		assertThat(d.campaigns().get(2).metrics())
				.usingRecursiveComparison().withComparatorForType(new DoubleComparator(1e-6), Double.class)
				.isEqualTo(new PaidMetrics(159, 16, 84.57, 9));
		assertThat(d.months()).extracting(PaidMonth::month)
				.containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 10));
		assertThat(d.previousMonth()).isNull();
		// And: breakdowns are ranked, keep their campaign, and the export's total row is ignored
		assertThat(d.keywords()).extracting(PaidRow::name, PaidRow::parent)
				.containsExactly(tuple("\"Velmora\"", "Brand"),
						tuple("\"Velmora shop\"", "Brand"));
		assertThat(d.genders()).extracting(PaidRow::name).containsExactly("Female", "Male");
		// And: the Search tabs this export lacks are named; Social-only lists stay empty
		assertThat(d.missingTabs()).containsExactly(PaidExportTab.GROUP_BREAKDOWN,
				PaidExportTab.PMAX_PLACEMENT_BREAKDOWN, PaidExportTab.AGE_DISTRIBUTION, PaidExportTab.AD_BREAKDOWN);
		assertThat(d.creatives()).isEmpty();
		assertThat(d.placements()).isEmpty();
	}

	@Test
	void shouldLimitTotalsToTheWindowAndSumThePreviousMonthTest() {
		// Given: an EOM window of October and FULL-FLIGHT rows in September and October
		PaidExportCollector collector = new PaidExportCollector(
				new PaidExportTableReader(new ReportNumberParserImpl(), new SheetRowHelperImpl()));
		Map<String, List<List<String>>> tabs = Map.of("FULL-FLIGHT PERFORMANCE", List.of(
				List.of("Date", "Level 1 Naming", "Impressions", "Clicks", "Cost", "Conversions"),
				List.of("2026-09-30", "Brand", "100", "10", "$50.00", "5"),
				List.of("2026-10-07", "Brand", "59", "6", "$34.57", "4"),
				List.of("2026-11-01", "Brand", "1", "1", "$1.00", "1")));
		FlightDates october = new FlightDates(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

		// When:
		Optional<PaidChannelData> data = collector.collect(PaidChannelKind.SEARCH, tabs, october);

		// Then: only the October row counts; September is the month-on-month base
		assertThat(data).isPresent();
		assertThat(data.get().total())
				.usingRecursiveComparison().withComparatorForType(new DoubleComparator(1e-6), Double.class)
				.isEqualTo(new PaidMetrics(59, 6, 34.57, 4));
		assertThat(data.get().previousMonth()).isEqualTo(new PaidMetrics(100, 10, 50, 5));
	}

	@Test
	void shouldGroupSocialCreativesAndPlacementsAcrossPlatformsTest() {
		// Given: a Meta export: FULL-FLIGHT by creative, a placement tab split by device, an age tab per ad set
		PaidExportCollector collector = new PaidExportCollector(
				new PaidExportTableReader(new ReportNumberParserImpl(), new SheetRowHelperImpl()));
		Map<String, List<List<String>>> tabs = Map.of(
				"FULL-FLIGHT PERFORMANCE", List.of(
						List.of("Date", "Channel", "Creative", "Impressions", "Clicks", "Cost", "Conversions"),
						List.of("2026-07-01", "Meta", "Auto - Reel", "52", "", "$0.00", "0"),
						List.of("2026-07-01", "Meta", "Aviation - Reel", "276", "4", "$13.63", "1"),
						List.of("2026-07-02", "Meta", "Auto - Reel", "48", "2", "$3.00", "0")),
				"PLACEMENT BREAKDOWN", List.of(
						List.of("PLACEMENT BREAKDOWN"),
						List.of("Ad set", "Platform", "Placement", "Device platform", "Impressions", "Clicks", "CTR",
								"CPC", "Conversions", "CPA", "Cost"),
						List.of("Noventra Meta", "Audience Network", "Rewarded video", "Desktop", "302", "3", "0.99%",
								"$1.23", "0", "$0.00", "$3.69"),
						List.of("Noventra Meta", "Audience Network", "Rewarded video", "In-app", "4,861", "113",
								"2.32%", "$1.51", "1", "$170.78", "$170.78")),
				"AGE DISTRIBUTION", List.of(
						List.of("AGE DISTRIBUTION"),
						List.of("Ad set", "Age", "Impressions", "Clicks", "CTR", "CPC", "Conversions", "CPA", "Cost"),
						List.of("Noventra Meta", "65+", "470,725", "2,480", "0.53%", "$1.03", "28", "$91.53",
								"$2,562.84"),
						List.of("Noventra Meta", "18-24", "34,467", "155", "0.45%", "$0.99", "2", "$76.48",
								"$152.96")));

		// When:
		Optional<PaidChannelData> data = collector.collect(PaidChannelKind.SOCIAL, tabs, null);

		// Then: creatives come from FULL-FLIGHT, placements are summed across devices, ages keep export order
		assertThat(data).isPresent();
		PaidChannelData d = data.get();
		assertThat(d.creatives()).extracting(PaidRow::name).containsExactly("Aviation - Reel", "Auto - Reel");
		assertThat(d.creatives().get(1).metrics()).isEqualTo(new PaidMetrics(100, 2, 3, 0));
		assertThat(d.placements()).extracting(PaidRow::name).containsExactly("Rewarded video");
		assertThat(d.placements().get(0).metrics())
				.usingRecursiveComparison().withComparatorForType(new DoubleComparator(1e-6), Double.class)
				.isEqualTo(new PaidMetrics(5_163, 116, 174.47, 1));
		assertThat(d.ages()).extracting(PaidRow::name).containsExactly("65+", "18-24");
		assertThat(d.missingTabs()).containsExactly(PaidExportTab.AUDIENCE_TARGETING_BREAKDOWN,
				PaidExportTab.GENDER_SPLIT);
		assertThat(d.campaigns()).isEmpty();
	}

	@Test
	void shouldSkipAnExportWithoutAUsableFullFlightTabTest() {
		// Given: an export with only a breakdown tab
		PaidExportCollector collector = new PaidExportCollector(
				new PaidExportTableReader(new ReportNumberParserImpl(), new SheetRowHelperImpl()));
		Map<String, List<List<String>>> tabs = Map.of("GENDER SPLIT", List.of(
				List.of("Gender", "Impressions", "Clicks", "Cost", "Conversions")));

		// When:
		Optional<PaidChannelData> data = collector.collect(PaidChannelKind.SEARCH, tabs, null);

		// Then:
		assertThat(data).isEmpty();
	}

	@Test
	void shouldSkipAChannelWithNoExportConnectedTest() {
		// Given: no tabs at all
		PaidExportCollector collector = new PaidExportCollector(
				new PaidExportTableReader(new ReportNumberParserImpl(), new SheetRowHelperImpl()));

		// When:
		Optional<PaidChannelData> data = collector.collect(PaidChannelKind.SOCIAL, Map.of(), null);

		// Then:
		assertThat(data).isEmpty();
	}
}
