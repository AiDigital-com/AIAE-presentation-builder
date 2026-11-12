package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.common.time.CurrentTimeImpl;
import com.aidigital.reportconstructor.service.reports.dto.DateFilter;
import com.aidigital.reportconstructor.service.reports.dto.DateFilterMode;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.TemplateVariant;
import com.aidigital.reportconstructor.service.reports.engine.PaidExportCollector;
import com.aidigital.reportconstructor.service.reports.engine.PaidExportTableReader;
import com.aidigital.reportconstructor.service.reports.engine.PaidValueFormatter;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaidTokenHelperImplTest {

	@Test
	void shouldFillTheSearchTokensFromTheGoogleAdsExportTest() {
		// Given: an EOC request with a Google Ads export (FULL-FLIGHT, PMax, age, gender and two ads)
		PaidTokenHelperImpl helper = new PaidTokenHelperImpl(new PaidExportCollector(
				new PaidExportTableReader(new ReportNumberParserImpl(), new SheetRowHelperImpl(new CurrentTimeImpl()))),
				new PaidValueFormatter());
		Map<String, List<List<String>>> search = Map.of(
				"FULL-FLIGHT PERFORMANCE", List.of(
						List.of("Date", "Level 1 Naming", "Impressions", "Clicks", "Cost", "Conversions"),
						List.of("2026-10-07", "Brand", "1,000", "100", "$500.00", "40"),
						List.of("2026-10-07", "NonBrand", "3,000", "60", "$300.00", "10"),
						List.of("2026-10-08", "Performance Max", "100,000", "1,000", "$200.00", "50")),
				"PMAX PLACEMENT BREAKDOWN", List.of(
						List.of("PMAX CHANNEL BREAKDOWN"),
						List.of("Channel", "Impressions", "Clicks", "CTR", "CPC", "Conversions", "CPA", "Cost"),
						List.of("Google Search", "48", "4", "8.33%", "$8.12", "30", "$1.08", "$32.46"),
						List.of("Gmail", "0", "0", "0.00%", "$0.00", "0", "$0.00", "$0.00"),
						List.of("Discover", "57,218", "1,745", "3.05%", "$5.22", "10", "$911.65", "$9,116.51")),
				"AGE DISTRIBUTION", List.of(
						List.of("AGE DISTRIBUTION"),
						List.of("Age", "Impressions", "Clicks", "CTR", "Conversions", "CPC", "CPA", "Cost"),
						List.of("18 - 24", "10", "1", "", "10", "", "", "$1"),
						List.of("25 - 34", "10", "1", "", "30", "", "", "$1"),
						List.of("65+", "10", "1", "", "10", "", "", "$1"),
						List.of("Unknown", "10", "1", "", "50", "", "", "$1")),
				"GENDER SPLIT", List.of(
						List.of("GENDER SPLIT"),
						List.of("Gender", "Impressions", "Clicks", "CTR", "Conversions", "CPC", "CPA", "Cost"),
						List.of("Female", "510", "1", "", "54", "", "", "$1"),
						List.of("Male", "490", "1", "", "46", "", "", "$1"),
						List.of("Unknown", "9,000", "1", "", "900", "", "", "$1")),
				"AD BREAKDOWN", List.of(
						List.of("AD BREAKDOWN"),
						List.of("Campaign", "Group", "Ad ID", "Headline 1", "Headline 2", "Headline 3",
								"Description 1", "Description 2", "Landing Page", "Impressions", "Clicks", "CTR",
								"CPC", "Conversions", "CPA", "Cost"),
						List.of("Brand", "Black & Orange Sale", "1", "Velmora Sale", "Up to 25% off", "Shop now",
								"Shop personal safety.", "Free shipping.", "example.com", "71", "9", "", "",
								"8", "", "$76.00"),
						List.of("Brand", "Brand", "2", "Velmora", "", "", "Official store.", "", "example.com",
								"1,363", "141", "", "", "130", "", "$713.39")));
		GeneratePayload payload = new GeneratePayload("brief", "EOC", null, List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(), null, null, null, null, null, null, TemplateVariant.MPO,
				search, Map.of());
		Map<String, String> flat = new HashMap<>();

		// When:
		helper.fillPaidTokens(flat, payload);

		// Then: headline figures are FULL-FLIGHT totals in the sample deck's formats
		assertThat(flat)
				.containsEntry("{{ps_spend}}", "$1.0K")
				.containsEntry("{{ps_imps}}", "104K")
				.containsEntry("{{ps_clicks}}", "1.2K")
				.containsEntry("{{ps_ctr}}", "1.1%")
				.containsEntry("{{ps_conv}}", "100")
				.containsEntry("{{ps_cpa}}", "$10.00")
				.containsEntry("{{ps_clicks_plan}}", "—")
				.containsEntry("{{ps_period_label}}", "the full flight")
				.containsEntry("{{ps_mom_conv}}", "—");

		// Then: the Campaign Breakdown table cells carry each campaign type (ranked by conversions) and the total
		assertThat(flat)
				.containsEntry("{{ps_camp_1_name}}", "Performance Max")
				.containsEntry("{{ps_camp_1_imps}}", "100,000")
				.containsEntry("{{ps_camp_1_clicks}}", "1,000")
				.containsEntry("{{ps_camp_1_ctr}}", "1.0%")
				.containsEntry("{{ps_camp_1_cpc}}", "$0.20")
				.containsEntry("{{ps_camp_1_conv}}", "50")
				.containsEntry("{{ps_camp_1_cpa}}", "$4.00")
				.containsEntry("{{ps_camp_1_spend}}", "$200")
				.containsEntry("{{ps_camp_total_imps}}", "104,000")
				.containsEntry("{{ps_camp_total_conv}}", "100")
				.containsEntry("{{ps_camp_total_spend}}", "$1.0K");
		// And: campaigns rank by conversions; the best-CPA tile names its campaign
		assertThat(flat)
				.containsEntry("{{ps_camp_1_name}}", "Performance Max")
				.containsEntry("{{ps_donut_3}}", "NonBrand")
				.containsEntry("{{ps_camp_top_conv}}", "50")
				.containsEntry("{{ps_camp_best_cpa}}", "$4.00")
				.containsEntry("{{ps_camp_best_cpa_name}}", "Performance Max")
				.containsEntry("{{ps_camp_best_cpc}}", "$0.20");
		// And: PMax tiles, age shares over the six bands (Unknown left out), gender split between male and female
		assertThat(flat)
				.containsEntry("{{ps_pmax_conv}}", "40")
				.containsEntry("{{ps_pmax_best_cpa}}", "$1.08")
				.containsEntry("{{ps_pmax_top_name}}", "GOOGLE SEARCH")
				.containsEntry("{{ps_pmax_top_share}}", "75%")
				.containsEntry("{{ps_age_1_share}}", "20%")
				.containsEntry("{{ps_age_2_share}}", "60%")
				.containsEntry("{{ps_age_3_share}}", "0%")
				.containsEntry("{{ps_age_top_name}}", "25–34")
				.containsEntry("{{ps_male}}", "49%")
				.containsEntry("{{ps_female_conv}}", "54")
				.containsEntry("{{ps_female_conv_share}}", "54%");
		// And: ad cards rank by conversions; the third slot has no ad and is dashed
		assertThat(flat)
				.containsEntry("{{ps_ad_1_badge}}", "BEST OVERALL")
				.containsEntry("{{ps_ad_1_name}}", "BRAND RSA")
				.containsEntry("{{ps_ad_1_headline}}", "Velmora")
				.containsEntry("{{ps_ad_1_cpa}}", "$5.49")
				.containsEntry("{{ps_ad_2_headline}}", "Velmora Sale | Up to 25% off")
				.containsEntry("{{ps_ad_2_desc}}", "Shop personal safety.")
				.containsEntry("{{ps_ad_2_badge}}", "HIGHEST CTR")
				.containsEntry("{{ps_ad_3_conv}}", "—");
		// And: no Social token without a Social export
		assertThat(flat.keySet()).noneMatch(k -> k.startsWith("{{pso_"));
	}

	@Test
	void shouldFillTheSocialTokensAndTheEomMonthOnMonthTest() {
		// Given: an EOM request for October with a Meta export (FULL-FLIGHT in September and October)
		PaidTokenHelperImpl helper = new PaidTokenHelperImpl(new PaidExportCollector(
				new PaidExportTableReader(new ReportNumberParserImpl(), new SheetRowHelperImpl(new CurrentTimeImpl()))),
				new PaidValueFormatter());
		Map<String, List<List<String>>> social = Map.of(
				"FULL-FLIGHT PERFORMANCE", List.of(
						List.of("Date", "Creative", "Impressions", "Clicks", "Cost", "Conversions"),
						List.of("2026-09-15", "Auto - Reel", "100", "10", "$10.00", "50"),
						List.of("2026-10-02", "Aviation - Carousel", "2,000", "40", "$80.00", "20"),
						List.of("2026-10-03", "Auto - Reel", "1,000", "10", "$40.00", "40")),
				"AUDIENCE TARGETING BREAKDOWN", List.of(
						List.of("AUDIENCE TARGETING BREAKDOWN"),
						List.of("Ad Set", "Impressions", "Clicks", "CTR", "CPC", "Conversions", "CPA", "Cost"),
						List.of("Noventra Meta - LAL", "411,573", "2,528", "", "", "30", "", "$2,813.97"),
						List.of("Noventra Meta - RETARGETING", "128,617", "1,579", "", "", "27", "", "$1,758.73"),
						List.of("META TOTAL", "540,190", "4,107", "", "", "57", "", "$4,572.70")),
				"PLACEMENT BREAKDOWN", List.of(
						List.of("PLACEMENT BREAKDOWN"),
						List.of("Ad set", "Platform", "Placement", "Device platform", "Impressions", "Clicks", "CTR",
								"CPC", "Conversions", "CPA", "Cost"),
						List.of("Noventra Meta", "Instagram", "Instagram Reels", "In-app", "10", "1", "", "", "3",
								"", "$9"),
						List.of("Noventra Meta", "Facebook", "Feed", "In-app", "10", "1", "", "", "1", "", "$9")));
		GeneratePayload payload = new GeneratePayload("brief", "EOM", null, List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(), null, null,
				new DateFilter(DateFilterMode.RANGE, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
				null, null, null, TemplateVariant.MPO, Map.of(), social);
		Map<String, String> flat = new HashMap<>();

		// When:
		helper.fillPaidTokens(flat, payload);

		// Then: totals cover October only; month-on-month compares with September
		assertThat(flat)
				.containsEntry("{{pso_conv}}", "60")
				.containsEntry("{{pso_spend}}", "$120")
				.containsEntry("{{pso_period_label}}", "October 2026")
				.containsEntry("{{pso_mom_conv}}", "+20%");
		// And: creatives come from FULL-FLIGHT; the format and audience type are the name's last part
		assertThat(flat)
				.containsEntry("{{pso_cr_top_name}}", "AUTO - REEL")
				.containsEntry("{{pso_cr_top_format}}", "Reel")
				.containsEntry("{{pso_cr_top_share}}", "67%")
				.containsEntry("{{pso_ad_format}}", "Reel")
				.containsEntry("{{pso_ad_cpa}}", "$1.00")
				.containsEntry("{{pso_ad_audience}}", "LAL")
				.containsEntry("{{pso_donut_2}}", "RETARGETING")
				.containsEntry("{{pso_aud_top_name}}", "LAL")
				.containsEntry("{{pso_aud_top_share}}", "53%");
		// And: the placement mix lists placements with one-decimal shares; empty slots stay blank
		assertThat(flat)
				.containsEntry("{{pso_pl_top_name}}", "INSTAGRAM REELS")
				.containsEntry("{{pso_pl_mix_1}}", "Instagram Reels 75.0%")
				.containsEntry("{{pso_pl_mix_2}}", "Feed 25.0%")
				.containsEntry("{{pso_pl_mix_3}}", "")
				.containsEntry("{{pso_male}}", "—");
		assertThat(flat.keySet()).noneMatch(k -> k.startsWith("{{ps_"));
	}

	@Test
	void shouldWriteNothingWithoutAnExportTest() {
		// Given: an MPO request with no paid export connected
		PaidTokenHelperImpl helper = new PaidTokenHelperImpl(new PaidExportCollector(
				new PaidExportTableReader(new ReportNumberParserImpl(), new SheetRowHelperImpl(new CurrentTimeImpl()))),
				new PaidValueFormatter());
		GeneratePayload payload = new GeneratePayload("brief", "EOC", null, List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(), null, null, null, null, null, null, TemplateVariant.MPO);
		Map<String, String> flat = new HashMap<>();

		// When:
		helper.fillPaidTokens(flat, payload);

		// Then:
		assertThat(flat).isEmpty();
	}
}
