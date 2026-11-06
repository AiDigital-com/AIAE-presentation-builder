package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.engine.PaidExportCollector;
import com.aidigital.reportconstructor.service.reports.engine.PaidValueFormatter;
import com.aidigital.reportconstructor.service.reports.enums.PaidChannelKind;
import com.aidigital.reportconstructor.service.reports.enums.ReportFlavor;
import com.aidigital.reportconstructor.service.reports.helpers.PaidTokenHelper;
import com.aidigital.reportconstructor.service.reports.model.PaidAd;
import com.aidigital.reportconstructor.service.reports.model.PaidChannelData;
import com.aidigital.reportconstructor.service.reports.model.PaidMetrics;
import com.aidigital.reportconstructor.service.reports.model.PaidRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.aidigital.reportconstructor.service.reports.engine.PaidValueFormatter.DASH;

/**
 * Default {@link PaidTokenHelper}: parses the exports through {@link PaidExportCollector} and formats the figures
 * the paid slides print through {@link PaidValueFormatter}, in the formats of the MPO sample decks. Every token of a
 * present channel is written, with an em-dash where the export has no figure (e.g. a missing breakdown tab), so a
 * present channel never shows a raw token.
 */
@Component
@RequiredArgsConstructor
public class PaidTokenHelperImpl implements PaidTokenHelper {

	/** Campaign rows on the Search campaign slide (and legend slots on its group slide and donut). */
	private static final int SEARCH_CAMPAIGN_SLOTS = 3;
	/** Ad cards on the Search Top Performing Ads slide. */
	private static final int SEARCH_AD_SLOTS = 3;
	/** Legend slots of the Social audience-type donut. */
	private static final int SOCIAL_DONUT_SLOTS = 3;
	/** Legend slots of the Social placement conversion-mix donut. */
	private static final int SOCIAL_PLACEMENT_MIX_SLOTS = 5;
	/** The age bands of the Search audience slide, in slide order (export spelling, spaces removed). */
	private static final List<String> AGE_BANDS = List.of("18-24", "25-34", "35-44", "45-54", "55-64", "65+");

	private static final String MALE = "male";
	private static final String FEMALE = "female";
	/** Label separator in Meta names: {@code "Noventra Meta - LAL"}, {@code "Aviation - Reel"}. */
	private static final String NAME_PART_SEPARATOR = " - ";
	/** Ad-card name suffix: the Search export only has responsive search ads. */
	private static final String RSA_SUFFIX = " RSA";
	private static final String HEADLINE_SEPARATOR = " | ";
	private static final String EN_DASH = "–";
	private static final String EOC_PERIOD_LABEL = "the full flight";
	private static final String BADGE_BEST_OVERALL = "BEST OVERALL";
	private static final String BADGE_LOWEST_CPA = "LOWEST CPA";
	private static final String BADGE_HIGHEST_CTR = "HIGHEST CTR";
	private static final String BADGE_STRONG_CONVERTER = "STRONG CONVERTER";

	private final PaidExportCollector collector;
	private final PaidValueFormatter fmt;

	@Override
	public List<PaidChannelData> fillPaidTokens(Map<String, String> flat, GeneratePayload payload) {
		FlightDates window = payload.dateFilter() == null ? null : payload.dateFilter().toWindow();
		boolean eom = ReportFlavor.EOM.getCode().equalsIgnoreCase(payload.reportType());
		List<PaidChannelData> channels = new ArrayList<>();
		collector.collect(PaidChannelKind.SEARCH, payload.paidSearchTabs(), window).ifPresent(d -> {
			flat.putAll(searchTokens(d, eom, window));
			channels.add(d);
		});
		collector.collect(PaidChannelKind.SOCIAL, payload.paidSocialTabs(), window).ifPresent(d -> {
			flat.putAll(socialTokens(d, eom, window));
			channels.add(d);
		});
		return channels;
	}

	/**
	 * Builds every Paid Search data token.
	 *
	 * @param d      the parsed Google Ads export
	 * @param eom    whether the deck is an EOM report
	 * @param window the report window, or {@code null}
	 * @return the tokens, keyed {@code {{ps_…}}}
	 */
	Map<String, String> searchTokens(PaidChannelData d, boolean eom, FlightDates window) {
		Map<String, String> t = commonTokens(d, eom, window);
		String p = d.kind().tokenPrefix();
		for (int n = 1; n <= SEARCH_CAMPAIGN_SLOTS; n++) {
			String name = n <= d.campaigns().size() ? d.campaigns().get(n - 1).name() : "";
			t.put(token(p, "donut_" + n), name);
			t.put(token(p, "camp_" + n + "_name"), name);
			PaidMetrics row = n <= d.campaigns().size() ? d.campaigns().get(n - 1).metrics() : null;
			putCampaignRow(t, p, "camp_" + n + "_", row);
		}
		putCampaignRow(t, p, "camp_total_", d.total());
		Optional<PaidRow> topCampaign = d.campaigns().stream().findFirst();
		t.put(token(p, "camp_top_conv"), topCampaign.map(r -> fmt.count(r.metrics().conversions())).orElse(DASH));
		t.put(token(p, "camp_top_conv_name"), topCampaign.map(PaidRow::name).orElse(DASH));
		putBest(t, p, "camp_best_cpa", d.campaigns(), PaidMetrics::cpa);
		putBest(t, p, "camp_best_cpc", d.campaigns(), PaidMetrics::cpc);

		t.put(token(p, "pmax_conv"), d.pmaxPlacements().isEmpty() ? DASH : fmt.count(conversions(d.pmaxPlacements())));
		putBestValue(t, p, "pmax_best_cpc", d.pmaxPlacements(), PaidMetrics::cpc);
		putBestValue(t, p, "pmax_best_cpa", d.pmaxPlacements(), PaidMetrics::cpa);
		putTop(t, p, "pmax_top", d.pmaxPlacements(), PaidRow::name);

		putAges(t, p, d.ages());
		for (int n = 1; n <= SEARCH_AD_SLOTS; n++) {
			putAd(t, p, n, d.ads());
		}
		return t;
	}

	/**
	 * Builds every Paid Social data token.
	 *
	 * @param d      the parsed Meta export
	 * @param eom    whether the deck is an EOM report
	 * @param window the report window, or {@code null}
	 * @return the tokens, keyed {@code {{pso_…}}}
	 */
	Map<String, String> socialTokens(PaidChannelData d, boolean eom, FlightDates window) {
		Map<String, String> t = commonTokens(d, eom, window);
		String p = d.kind().tokenPrefix();
		for (int n = 1; n <= SOCIAL_DONUT_SLOTS; n++) {
			t.put(token(p, "donut_" + n), n <= d.audiences().size() ? lastPart(d.audiences().get(n - 1).name()) : "");
		}

		Optional<PaidRow> topCreative = d.creatives().stream().findFirst();
		t.put(token(p, "ad_conv"), topCreative.map(r -> fmt.count(r.metrics().conversions())).orElse(DASH));
		t.put(token(p, "ad_clicks"), topCreative.map(r -> fmt.count(r.metrics().clicks())).orElse(DASH));
		t.put(token(p, "ad_cpa"), topCreative.map(r -> fmt.money(r.metrics().cpa())).orElse(DASH));
		t.put(token(p, "ad_audience"), d.audiences().stream().findFirst().map(r -> lastPart(r.name())).orElse(DASH));
		t.put(token(p, "ad_format"), topCreative.map(r -> lastPart(r.name())).orElse(DASH));

		putBestValue(t, p, "cr_best_cpc", d.creatives(), PaidMetrics::cpc);
		putBestValue(t, p, "cr_best_cpa", d.creatives(), PaidMetrics::cpa);
		putTop(t, p, "cr_top", d.creatives(), PaidRow::name);
		t.put(token(p, "cr_top_format"), topCreative.map(r -> lastPart(r.name())).orElse(DASH));
		t.put(token(p, "cr_top_ctr"), topCreative.map(r -> fmt.pct1(r.metrics().ctr())).orElse(DASH));
		t.put(token(p, "cr_top_conv"), topCreative.map(r -> fmt.count(r.metrics().conversions())).orElse(DASH));
		t.put(token(p, "cr_top_cpa"), topCreative.map(r -> fmt.money(r.metrics().cpa())).orElse(DASH));

		putBestValue(t, p, "aud_best_cpc", d.audiences(), PaidMetrics::cpc);
		putBestValue(t, p, "aud_best_cpa", d.audiences(), PaidMetrics::cpa);
		putTop(t, p, "aud_top", d.audiences(), r -> lastPart(r.name()));

		putBestValue(t, p, "pl_best_cpa", d.placements(), PaidMetrics::cpa);
		putBestValue(t, p, "pl_best_cpc", d.placements(), PaidMetrics::cpc);
		putTop(t, p, "pl_top", d.placements(), PaidRow::name);
		double placementConv = conversions(d.placements());
		for (int n = 1; n <= SOCIAL_PLACEMENT_MIX_SLOTS; n++) {
			String mix = "";
			if (n <= d.placements().size()) {
				PaidRow r = d.placements().get(n - 1);
				mix = r.name() + " " + fmt.share1(r.metrics().conversions(), placementConv);
			}
			t.put(token(p, "pl_mix_" + n), mix);
		}
		return t;
	}

	/**
	 * Builds the tokens both channels share: headline totals, plan clicks, period label, EOM month-on-month and
	 * the gender split.
	 *
	 * @param d      the parsed export
	 * @param eom    whether the deck is an EOM report
	 * @param window the report window, or {@code null}
	 * @return the tokens, in a mutable insertion-ordered map
	 */
	Map<String, String> commonTokens(PaidChannelData d, boolean eom, FlightDates window) {
		String p = d.kind().tokenPrefix();
		PaidMetrics total = d.total();
		Map<String, String> t = new LinkedHashMap<>();
		t.put(token(p, "spend"), fmt.moneyCompact(total.cost()));
		t.put(token(p, "imps"), fmt.compact(total.impressions()));
		t.put(token(p, "clicks"), fmt.compact(total.clicks()));
		t.put(token(p, "ctr"), fmt.pct1(total.ctr()));
		t.put(token(p, "cpc"), fmt.money(total.cpc()));
		t.put(token(p, "conv"), fmt.count(total.conversions()));
		t.put(token(p, "cpa"), fmt.money(total.cpa()));
		// Plan clicks need a source the exports do not carry (plan open question 2); dashed until decided.
		t.put(token(p, "clicks_plan"), DASH);
		t.put(token(p, "period_label"),
				eom && window != null ? fmt.month(YearMonth.from(window.start())) : EOC_PERIOD_LABEL);
		Double previous = d.previousMonth() == null ? null : d.previousMonth().conversions();
		t.put(token(p, "mom_conv"), eom ? fmt.growth(total.conversions(), previous) : DASH);

		PaidMetrics male = gender(d.genders(), MALE);
		PaidMetrics female = gender(d.genders(), FEMALE);
		double imps = male.impressions() + female.impressions();
		double conv = male.conversions() + female.conversions();
		boolean genders = !d.genders().isEmpty();
		t.put(token(p, "male"), fmt.share(male.impressions(), imps));
		t.put(token(p, "female"), fmt.share(female.impressions(), imps));
		t.put(token(p, "male_conv"), genders ? fmt.count(male.conversions()) : DASH);
		t.put(token(p, "female_conv"), genders ? fmt.count(female.conversions()) : DASH);
		t.put(token(p, "male_conv_share"), fmt.share(male.conversions(), conv));
		t.put(token(p, "female_conv_share"), fmt.share(female.conversions(), conv));
		return t;
	}

	/**
	 * Writes the six age-band conversion shares and the top band of the Search audience slide.
	 *
	 * @param t    the token map
	 * @param p    the channel token prefix
	 * @param ages the export's age rows
	 */
	void putAges(Map<String, String> t, String p, List<PaidRow> ages) {
		Map<String, PaidMetrics> byBand = new LinkedHashMap<>();
		for (String band : AGE_BANDS) {
			byBand.put(band, PaidMetrics.ZERO);
		}
		for (PaidRow r : ages) {
			String band = r.name().replace(" ", "").replace(EN_DASH, "-");
			byBand.computeIfPresent(band, (k, m) -> m.plus(r.metrics()));
		}
		double total = byBand.values().stream().mapToDouble(PaidMetrics::conversions).sum();
		for (int n = 1; n <= AGE_BANDS.size(); n++) {
			t.put(token(p, "age_" + n + "_share"), fmt.share(byBand.get(AGE_BANDS.get(n - 1)).conversions(), total));
		}
		Optional<Map.Entry<String, PaidMetrics>> top = total > 0
				? byBand.entrySet().stream().max(Comparator.comparingDouble(e -> e.getValue().conversions()))
				: Optional.empty();
		t.put(token(p, "age_top_name"), top.map(e -> e.getKey().replace("-", EN_DASH)).orElse(DASH));
		t.put(token(p, "age_top_share"), top.map(e -> fmt.share(e.getValue().conversions(), total)).orElse(DASH));
	}

	/**
	 * Writes one Search ad card ({@code ps_ad_N_*}); a slot beyond the export's ads is dashed.
	 *
	 * @param t   the token map
	 * @param p   the channel token prefix
	 * @param n   the 1-based card number
	 * @param ads the export's ads, ranked
	 */
	void putAd(Map<String, String> t, String p, int n, List<PaidAd> ads) {
		String k = "ad_" + n + "_";
		if (n > ads.size()) {
			for (String field : List.of("badge", "name", "campaign", "group", "url", "headline", "desc", "clicks",
					"ctr", "conv", "cpa")) {
				t.put(token(p, k + field), DASH);
			}
			return;
		}
		PaidAd ad = ads.get(n - 1);
		String label = ad.group().isEmpty() ? ad.campaign() : ad.group();
		t.put(token(p, k + "badge"), badge(n, ad, ads.subList(0, Math.min(ads.size(), SEARCH_AD_SLOTS))));
		t.put(token(p, k + "name"), label.toUpperCase(Locale.ROOT) + RSA_SUFFIX);
		t.put(token(p, k + "campaign"), ad.campaign());
		t.put(token(p, k + "group"), ad.group());
		t.put(token(p, k + "url"), ad.landingPage());
		t.put(token(p, k + "headline"), String.join(HEADLINE_SEPARATOR, ad.headlines().stream().limit(2).toList()));
		t.put(token(p, k + "desc"), ad.descriptions().isEmpty() ? "" : ad.descriptions().get(0));
		t.put(token(p, k + "clicks"), fmt.count(ad.metrics().clicks()));
		t.put(token(p, k + "ctr"), fmt.pct1(ad.metrics().ctr()));
		t.put(token(p, k + "conv"), fmt.count(ad.metrics().conversions()));
		t.put(token(p, k + "cpa"), fmt.money(ad.metrics().cpa()));
	}

	/**
	 * Writes one row of the Search Campaign Breakdown table, whose cells carry tokens
	 * ({@code {{ps_camp_1_imps}}} … {@code {{ps_camp_total_spend}}}); a row beyond the export's campaigns is dashed.
	 *
	 * @param t      the token map
	 * @param p      the channel token prefix
	 * @param prefix the row's token prefix, e.g. {@code camp_1_} or {@code camp_total_}
	 * @param m      the row's figures, or {@code null} for an empty row
	 */
	void putCampaignRow(Map<String, String> t, String p, String prefix, PaidMetrics m) {
		t.put(token(p, prefix + "imps"), m == null ? DASH : fmt.count(m.impressions()));
		t.put(token(p, prefix + "clicks"), m == null ? DASH : fmt.count(m.clicks()));
		t.put(token(p, prefix + "ctr"), m == null ? DASH : fmt.pct1(m.ctr()));
		t.put(token(p, prefix + "cpc"), m == null ? DASH : fmt.money(m.cpc()));
		t.put(token(p, prefix + "conv"), m == null ? DASH : fmt.count(m.conversions()));
		t.put(token(p, prefix + "cpa"), m == null ? DASH : fmt.money(m.cpa()));
		t.put(token(p, prefix + "spend"), m == null ? DASH : fmt.moneyCompact(m.cost()));
	}

	/**
	 * Picks an ad card's badge: the top ad is the best overall; a runner-up is named for the rate it wins among
	 * the cards shown (lowest CPA, then highest CTR), and is a strong converter otherwise.
	 *
	 * @param n     the 1-based card number
	 * @param ad    the card's ad
	 * @param shown the ads on the slide
	 * @return the badge text
	 */
	String badge(int n, PaidAd ad, List<PaidAd> shown) {
		if (n == 1) {
			return BADGE_BEST_OVERALL;
		}
		if (ad.metrics().cpa() != null && shown.stream().map(a -> a.metrics().cpa())
				.filter(v -> v != null).allMatch(v -> v >= ad.metrics().cpa())) {
			return BADGE_LOWEST_CPA;
		}
		if (ad.metrics().ctr() != null && shown.stream().map(a -> a.metrics().ctr())
				.filter(v -> v != null).allMatch(v -> v <= ad.metrics().ctr())) {
			return BADGE_HIGHEST_CTR;
		}
		return BADGE_STRONG_CONVERTER;
	}

	/**
	 * Writes a "best rate" tile pair: the lowest value of the rate among the rows and the row it belongs to.
	 *
	 * @param t    the token map
	 * @param p    the channel token prefix
	 * @param base the tile's token base, e.g. {@code camp_best_cpa} (the name goes to {@code base + "_name"})
	 * @param rows the rows to search
	 * @param rate the rate to minimise
	 */
	void putBest(Map<String, String> t, String p, String base, List<PaidRow> rows,
			Function<PaidMetrics, Double> rate) {
		Optional<PaidRow> best = lowest(rows, rate);
		t.put(token(p, base), best.map(r -> fmt.money(rate.apply(r.metrics()))).orElse(DASH));
		t.put(token(p, base + "_name"), best.map(PaidRow::name).orElse(DASH));
	}

	/**
	 * Writes a "best rate" tile that shows only the value.
	 *
	 * @param t    the token map
	 * @param p    the channel token prefix
	 * @param key  the tile's token name
	 * @param rows the rows to search
	 * @param rate the rate to minimise
	 */
	void putBestValue(Map<String, String> t, String p, String key, List<PaidRow> rows,
			Function<PaidMetrics, Double> rate) {
		t.put(token(p, key), lowest(rows, rate).map(r -> fmt.money(rate.apply(r.metrics()))).orElse(DASH));
	}

	/**
	 * Writes a "top row" tile: the first (highest-converting) row's label, upper-cased as the tile prints it, and
	 * its share of the rows' conversions.
	 *
	 * @param t     the token map
	 * @param p     the channel token prefix
	 * @param base  the tile's token base; writes {@code base_name} and {@code base_share}
	 * @param rows  the ranked rows
	 * @param label how to label the row
	 */
	void putTop(Map<String, String> t, String p, String base, List<PaidRow> rows, Function<PaidRow, String> label) {
		Optional<PaidRow> top = rows.stream().findFirst();
		double total = conversions(rows);
		t.put(token(p, base + "_name"), top.map(r -> label.apply(r).toUpperCase(Locale.ROOT)).orElse(DASH));
		t.put(token(p, base + "_share"), top.map(r -> fmt.share(r.metrics().conversions(), total)).orElse(DASH));
	}

	/**
	 * @param rows the rows to search
	 * @param rate the rate to minimise
	 * @return the row with the lowest non-empty rate, or empty when no row has one
	 */
	Optional<PaidRow> lowest(List<PaidRow> rows, Function<PaidMetrics, Double> rate) {
		return rows.stream()
				.filter(r -> rate.apply(r.metrics()) != null)
				.min(Comparator.comparingDouble(r -> rate.apply(r.metrics())));
	}

	/**
	 * @param genders the export's gender rows
	 * @param name    the gender to find, case-insensitively
	 * @return that gender's delivery, or {@link PaidMetrics#ZERO} when absent
	 */
	PaidMetrics gender(List<PaidRow> genders, String name) {
		return genders.stream().filter(r -> r.name().equalsIgnoreCase(name)).map(PaidRow::metrics)
				.reduce(PaidMetrics.ZERO, PaidMetrics::plus);
	}

	/**
	 * @param rows the rows to sum
	 * @return the rows' total conversions
	 */
	double conversions(List<PaidRow> rows) {
		return rows.stream().mapToDouble(r -> r.metrics().conversions()).sum();
	}

	/**
	 * @param name a Meta name such as {@code "Noventra Meta - LAL"} or {@code "Aviation - Reel"}
	 * @return the part after the last {@code " - "} ({@code "LAL"}, {@code "Reel"}), or the whole name
	 */
	String lastPart(String name) {
		int i = name.lastIndexOf(NAME_PART_SEPARATOR);
		return i < 0 ? name : name.substring(i + NAME_PART_SEPARATOR.length());
	}

	/**
	 * @param prefix the channel token prefix
	 * @param name   the token name without prefix
	 * @return the placeholder key, e.g. {@code {{ps_spend}}}
	 */
	String token(String prefix, String name) {
		return "{{" + prefix + name + "}}";
	}
}
