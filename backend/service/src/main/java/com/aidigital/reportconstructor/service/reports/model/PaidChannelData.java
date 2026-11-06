package com.aidigital.reportconstructor.service.reports.model;

import com.aidigital.reportconstructor.service.reports.enums.PaidChannelKind;
import com.aidigital.reportconstructor.service.reports.enums.PaidExportTab;

import java.util.List;

/**
 * Everything the Paid Search or Paid Social slides print, parsed from one paid-media export. The FULL-FLIGHT
 * figures ({@code total}, {@code campaigns}, {@code creatives}, {@code months}) honour the report's date window;
 * the breakdown tabs are pre-aggregated by the export for its whole range. Ranked lists are sorted by
 * conversions, then clicks, highest first; age bands and genders keep the export's order. A list the channel has
 * no slide for, or whose tab is missing, is empty.
 *
 * @param kind           which channel this is
 * @param total          delivery inside the report window
 * @param previousMonth  delivery in the calendar month before the window starts (EOM month-on-month), or
 *                       {@code null} when there is no window or no delivery that month
 * @param months         delivery per calendar month inside the window, in date order
 * @param campaigns      Search campaigns ({@code Level 1 Naming}: Brand / NonBrand / Performance Max), ranked
 * @param creatives      Social creatives (FULL-FLIGHT {@code Creative}), ranked
 * @param groups         Search ad groups with their campaign as parent, ranked
 * @param keywords       Search keywords with their campaign as parent, ranked
 * @param pmaxPlacements Performance Max channels (Search, Display, Discover...), ranked
 * @param ages           age bands, in export order, summed across ad sets
 * @param genders        genders, in export order, summed across ad sets
 * @param ads            Search ads, ranked
 * @param audiences      Social audiences (ad sets), ranked
 * @param placements     Social placements summed across platforms and devices, ranked
 * @param missingTabs    breakdown tabs this channel reads that the export lacks or that have no usable header
 */
public record PaidChannelData(
		PaidChannelKind kind,
		PaidMetrics total,
		PaidMetrics previousMonth,
		List<PaidMonth> months,
		List<PaidRow> campaigns,
		List<PaidRow> creatives,
		List<PaidRow> groups,
		List<PaidRow> keywords,
		List<PaidRow> pmaxPlacements,
		List<PaidRow> ages,
		List<PaidRow> genders,
		List<PaidAd> ads,
		List<PaidRow> audiences,
		List<PaidRow> placements,
		List<PaidExportTab> missingTabs
) {
}
