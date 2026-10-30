package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.helpers.DeckStyleTokenHelper;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Spring bean implementation of {@link DeckStyleTokenHelper}.
 */
@Component
@RequiredArgsConstructor
public class DeckStyleTokenHelperImpl implements DeckStyleTokenHelper {

	/** Token the campaign period is derived from: the flight the media plan slide prints. */
	static final String FLIGHT_DATES_TOKEN = "{{flight_dates}}";

	/** Strategic framework subtitle, e.g. "Q3 & Q4 2025". */
	static final String CAMPAIGN_PERIOD_TOKEN = "{{campaign_period}}";

	/** Token the geo summary is derived from: the market list the media plan slide prints. */
	static final String GEO_LOCATIONS_TOKEN = "{{geo_locations}}";

	/** Media plan slide's short geo line, e.g. "9 priority markets". */
	static final String GEO_SUMMARY_TOKEN = "{{geo_summary}}";

	/** Em dash written when a value cannot be derived, matching the deck's other unresolved values. */
	static final String DASH = "—";

	/** KPI type of a click-led tactic; every other non-blank KPI type is completion-led (VCR, ACR, …). */
	static final String CLICK_KPI_TYPE = "CTR";

	/** Top-content tile label for a click-led tactic. */
	static final String CLICKS_LABEL = "Clicks";

	/** Top-content tile label for a completion-led tactic. */
	static final String COMPLETED_VIEWS_LABEL = "Completed Views";

	/** Wording of a multi-market geo summary, filled with the market count. */
	static final String MARKETS_SUMMARY = "%d priority markets";

	/** Months in one quarter. */
	static final int MONTHS_PER_QUARTER = 3;

	/** Quarter count from which a same-year period is written as a range ("Q1 – Q3") rather than a pair. */
	static final int RANGE_FROM_QUARTERS = 3;

	/**
	 * Separates the two ends of a flight range. A spaced hyphen only, so an ISO date such as
	 * {@code 2025-10-01} is never split apart.
	 */
	static final Pattern FLIGHT_SEPARATOR = Pattern.compile("\\s*[\\u2013\\u2014]\\s*|\\s+-\\s+");

	/** A four-digit year inside a printed date. */
	static final Pattern YEAR = Pattern.compile("\\b\\d{4}\\b");

	/** Separates the markets of a geo list: commas, semicolons, an ampersand or a spelled-out "and". */
	static final Pattern MARKET_SEPARATOR = Pattern.compile("\\s*(?:[,;&]|\\band\\b)\\s*");

	private final SheetRowHelper sheetUtils;

	@Override
	public void fillDeckStyleTokens(Map<String, String> flatReplacements, int tacticCount) {
		if (flatReplacements == null) {
			return;
		}
		putIfBlank(flatReplacements, CAMPAIGN_PERIOD_TOKEN, campaignPeriod(flatReplacements.get(FLIGHT_DATES_TOKEN)));
		putIfBlank(flatReplacements, GEO_SUMMARY_TOKEN, geoSummary(flatReplacements.get(GEO_LOCATIONS_TOKEN)));
		for (int n = 1; n <= tacticCount; n++) {
			fillTopContentKpi(flatReplacements, n);
		}
	}

	/**
	 * Writes one tactic's top-content KPI label and value: clicks for a click-led tactic, completed views for
	 * a completion-led one, chosen by the KPI type the tactic slide already prints.
	 *
	 * @param flatReplacements resolved token → value map, mutated in place
	 * @param n                1-based tactic number
	 */
	void fillTopContentKpi(Map<String, String> flatReplacements, int n) {
		String prefix = "{{tactic " + n;
		boolean completionLed = isCompletionLed(flatReplacements.get(prefix + " KPI type}}"));
		String value = flatReplacements.get(prefix + (completionLed
				? " top creative completions}}" : " top creative clicks}}"));
		putIfBlank(flatReplacements, prefix + " top creative kpi label}}",
				completionLed ? COMPLETED_VIEWS_LABEL : CLICKS_LABEL);
		putIfBlank(flatReplacements, prefix + " top creative kpi}}", isBlank(value) ? DASH : value.trim());
	}

	/**
	 * Tells whether a tactic's KPI type is completion-led. A blank or dashed type counts as click-led, the
	 * tile's long-standing default.
	 *
	 * @param kpiType the tactic's printed KPI type, may be {@code null}
	 * @return {@code true} for a completion KPI such as VCR or ACR
	 */
	boolean isCompletionLed(String kpiType) {
		return !isBlank(kpiType) && !CLICK_KPI_TYPE.equalsIgnoreCase(kpiType.trim());
	}

	/**
	 * Names the calendar quarters a flight covers, e.g. {@code "Oct 1 – Dec 31, 2025"} → {@code "Q4 2025"},
	 * {@code "Jul 1 – Dec 31, 2025"} → {@code "Q3 & Q4 2025"}, {@code "Nov 1, 2025 – Feb 28, 2026"} →
	 * {@code "Q4 2025 & Q1 2026"}.
	 *
	 * @param flightDates the flight as printed, a single date or a range whose start may omit its year
	 * @return the quarter label, or an em dash when the flight cannot be read
	 */
	String campaignPeriod(String flightDates) {
		LocalDate[] range = parseFlight(flightDates);
		if (range == null) {
			return DASH;
		}
		List<int[]> quarters = quartersBetween(range[0], range[1]);
		int[] first = quarters.getFirst();
		int[] last = quarters.getLast();
		if (quarters.size() == 1) {
			return quarterLabel(first) + " " + first[0];
		}
		if (first[0] == last[0]) {
			String joiner = quarters.size() >= RANGE_FROM_QUARTERS ? " – " : " & ";
			return quarterLabel(first) + joiner + quarterLabel(last) + " " + last[0];
		}
		String joiner = quarters.size() > 2 ? " – " : " & ";
		return quarterLabel(first) + " " + first[0] + joiner + quarterLabel(last) + " " + last[0];
	}

	/**
	 * Reads a printed flight into its first and last day. A start written without its year (the deck's own
	 * {@code "Oct 1 – Dec 31, 2025"} format) takes the end's year, or the year before when that would put it
	 * after the end.
	 *
	 * @param flightDates the flight as printed
	 * @return {@code {start, end}}, or {@code null} when either end cannot be parsed
	 */
	LocalDate[] parseFlight(String flightDates) {
		if (isBlank(flightDates) || DASH.equals(flightDates.trim())) {
			return null;
		}
		String[] ends = FLIGHT_SEPARATOR.split(flightDates.trim(), 2);
		LocalDate end = sheetUtils.parseDate(ends[ends.length - 1]);
		if (end == null) {
			return null;
		}
		if (ends.length == 1) {
			return new LocalDate[]{end, end};
		}
		// The date parser fills a missing year in on its own, so a year-less start is given the end's year
		// explicitly rather than trusting whatever year the parser picked.
		LocalDate start;
		if (YEAR.matcher(ends[0]).find()) {
			start = sheetUtils.parseDate(ends[0]);
		} else {
			start = sheetUtils.parseDate(ends[0] + ", " + end.getYear());
			if (start != null && start.isAfter(end)) {
				start = start.minusYears(1);
			}
		}
		return start == null || start.isAfter(end) ? null : new LocalDate[]{start, end};
	}

	/**
	 * Lists the calendar quarters from the start's to the end's, inclusive.
	 *
	 * @param start first day of the flight
	 * @param end   last day of the flight, not before {@code start}
	 * @return {@code {year, quarter}} pairs in order, never empty
	 */
	List<int[]> quartersBetween(LocalDate start, LocalDate end) {
		List<int[]> quarters = new ArrayList<>();
		int year = start.getYear();
		int quarter = quarterOf(start);
		int lastYear = end.getYear();
		int lastQuarter = quarterOf(end);
		while (year < lastYear || year == lastYear && quarter <= lastQuarter) {
			quarters.add(new int[]{year, quarter});
			quarter++;
			if (quarter > 4) {
				quarter = 1;
				year++;
			}
		}
		return quarters;
	}

	/**
	 * Returns the calendar quarter a date falls in.
	 *
	 * @param date the date
	 * @return 1..4
	 */
	int quarterOf(LocalDate date) {
		return (date.getMonthValue() - 1) / MONTHS_PER_QUARTER + 1;
	}

	/**
	 * Renders a quarter without its year, e.g. {@code "Q3"}.
	 *
	 * @param yearQuarter {@code {year, quarter}}
	 * @return the label
	 */
	String quarterLabel(int[] yearQuarter) {
		return "Q" + yearQuarter[1];
	}

	/**
	 * Shortens a market list to the line the media plan slide prints beside "Geo": the market itself when
	 * there is only one ("National"), otherwise the count ("9 priority markets").
	 *
	 * @param geoLocations the market list as printed, e.g. {@code "NYC, ATL, MIA & CHI."}
	 * @return the summary, or an em dash when the list is empty
	 */
	String geoSummary(String geoLocations) {
		if (isBlank(geoLocations) || DASH.equals(geoLocations.trim())) {
			return DASH;
		}
		String list = geoLocations.trim();
		if (list.endsWith(".")) {
			list = list.substring(0, list.length() - 1);
		}
		List<String> markets = Arrays.stream(MARKET_SEPARATOR.split(list)).filter(m -> !m.isBlank()).toList();
		if (markets.isEmpty()) {
			return DASH;
		}
		return markets.size() == 1 ? markets.getFirst() : String.format(MARKETS_SUMMARY, markets.size());
	}

	/**
	 * Writes a value unless the token already carries one.
	 *
	 * @param flatReplacements resolved token → value map, mutated in place
	 * @param token            the {@code {{token}}} key
	 * @param value            the derived value
	 */
	void putIfBlank(Map<String, String> flatReplacements, String token, String value) {
		if (isBlank(flatReplacements.get(token))) {
			flatReplacements.put(token, value);
		}
	}

	/**
	 * Tells whether a value is missing or whitespace only.
	 *
	 * @param value the value, may be {@code null}
	 * @return {@code true} when there is nothing to print
	 */
	boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
