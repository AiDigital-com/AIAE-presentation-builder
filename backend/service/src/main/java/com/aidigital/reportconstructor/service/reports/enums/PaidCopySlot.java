package com.aidigital.reportconstructor.service.reports.enums;

import java.util.Arrays;
import java.util.List;

/**
 * The Claude-written copy slots of the MPO deck's Paid Search ({@code ps_}) and Paid Social ({@code pso_}) slides.
 * Each constant is one slot family: a single token ({@code {{ps_verdict}}}) or a numbered run
 * ({@code {{ps_kw_takeaway_1}}} … {@code _3}), optionally with a separate heading token per entry
 * ({@code {{ps_pmax_take_1_title}}}). Character limits follow the copy of the MPO sample deck the slides were
 * copied from, so the text fits the same boxes.
 */
public enum PaidCopySlot {

	SEARCH_VERDICT(PaidChannelKind.SEARCH, "verdict", 1, false, 115, 0, ReportScope.BOTH,
			"one-sentence verdict under the PAID SEARCH overview headline: clicks, conversions and CPA in one "
					+ "line, e.g. \"Google Ads delivered 14.9K clicks and 1,248 conversions at a $36.40 CPA.\""),
	SEARCH_CAMP_TAKEAWAY(PaidChannelKind.SEARCH, "camp_takeaway", 3, false, 65, 0, ReportScope.BOTH,
			"Campaign Breakdown slide (Brand / Non-brand / PMax): one short line each, e.g. \"Brand led efficiency "
					+ "with the lowest CPA.\""),
	SEARCH_CAMP_RECO(PaidChannelKind.SEARCH, "camp_reco", 1, false, 92, 0, ReportScope.BOTH,
			"Campaign Breakdown recommendation; the slide already prints \"Recommendation: \" before it, so start "
					+ "with the verb"),
	SEARCH_GROUP_CALLOUT(PaidChannelKind.SEARCH, "grp_callout", 3, false, 85, 0, ReportScope.BOTH,
			"Group Breakdown callouts, each \"Label: evidence\", in this order: efficiency leader, scale driver, "
					+ "watch-out, e.g. \"Efficiency leader: Brand Exact delivered 620 conversions at a $13.84 CPA.\""),
	SEARCH_KEYWORD_TAKEAWAY(PaidChannelKind.SEARCH, "kw_takeaway", 3, false, 105, 0, ReportScope.BOTH,
			"Top 10 Keywords takeaways, each \"Label: evidence\", e.g. \"High-intent wins: \\\"workflow "
					+ "automation\\\" drove 67 conversions at a $26.65 CPA.\""),
	SEARCH_PMAX_TAKE(PaidChannelKind.SEARCH, "pmax_take", 4, true, 55, 24, ReportScope.BOTH,
			"PMax Placement tiles in this order: volume leader, efficiency leader, recommended action, watch-out; "
					+ "title e.g. \"Search Volume Leader\", text e.g. \"620 conversions (44% of PMax total).\""),
	SEARCH_AUDIENCE_TAKEAWAY(PaidChannelKind.SEARCH, "aud_takeaway", 3, false, 60, 0, ReportScope.BOTH,
			"Audience Analysis (age and gender conversions) takeaways, one short line each, e.g. \"25–34 was the "
					+ "top segment at 28% of conversions.\""),
	SEARCH_AUDIENCE_RECO(PaidChannelKind.SEARCH, "aud_reco", 1, false, 90, 0, ReportScope.BOTH,
			"Audience Analysis recommendation; the slide already prints \"Recommendation: \" before it, so start "
					+ "with the verb"),
	SEARCH_HIGHLIGHT(PaidChannelKind.SEARCH, "highlight", 4, true, 140, 32, ReportScope.EOC,
			"PAID SEARCH PERFORMANCE HIGHLIGHTS: four distinct findings (campaigns, keywords, PMax, audience or "
					+ "ads), title a short headline in Title Case, text one evidence-led sentence"),
	SEARCH_RECO(PaidChannelKind.SEARCH, "reco", 4, true, 105, 33, ReportScope.EOC,
			"PAID SEARCH RECOMMENDATIONS: four next steps (tracking, budget, search terms/match types, ad copy "
					+ "or assets), title a short headline in Title Case, text one actionable sentence"),
	SEARCH_EOM_WORKED(PaidChannelKind.SEARCH, "eom_worked", 1, false, 110, 0, ReportScope.EOM, "What worked",
			"EOM metric slide \"What worked\": the month's strongest result, with its figure"),
	SEARCH_EOM_TOP_WINDOW(PaidChannelKind.SEARCH, "eom_top_window", 1, false, 110, 0, ReportScope.EOM, "Top window",
			"EOM metric slide \"Top window\": the best-performing campaign, group or keyword this month"),
	SEARCH_EOM_OPTIMISE(PaidChannelKind.SEARCH, "eom_optimise", 1, false, 110, 0, ReportScope.EOM, "Optimise",
			"EOM metric slide \"Optimise\": the weakest spot and what to change"),
	SEARCH_EOM_NEXT_MONTH(PaidChannelKind.SEARCH, "eom_next_month", 1, false, 110, 0, ReportScope.EOM, "Next month",
			"EOM metric slide \"Next month\": the plan for the coming month"),

	SOCIAL_VERDICT(PaidChannelKind.SOCIAL, "verdict", 1, false, 115, 0, ReportScope.BOTH,
			"one-sentence verdict under the PAID SOCIAL overview headline: clicks, conversions and CPA in one "
					+ "line, e.g. \"Meta Ads delivered 14.9K clicks and 1,248 conversions at a $36.40 CPA.\""),
	SOCIAL_CREATIVE_RECO(PaidChannelKind.SOCIAL, "cr_reco", 1, false, 72, 0, ReportScope.BOTH,
			"Creative Performance recommendation; the slide already prints \"Recommendation: \" before it, so "
					+ "start with the verb, e.g. \"Scale Pool Escape; refresh Guest Story.\""),
	SOCIAL_AUDIENCE_TAKE(PaidChannelKind.SOCIAL, "aud_take", 4, true, 55, 24, ReportScope.BOTH,
			"Audience Targeting tiles in this order: efficiency leader, scale driver, recommended action, "
					+ "watch-out; title e.g. \"Retargeting Efficiency\", text e.g. \"420 conversions at a "
					+ "$19.31 CPA.\""),
	SOCIAL_PLACEMENT_SUMMARY(PaidChannelKind.SOCIAL, "pl_summary", 1, false, 95, 0, ReportScope.BOTH,
			"Placement Breakdown summary sentence, e.g. \"Reels drove half of all conversions, while Feed added "
					+ "the strongest secondary volume.\""),
	SOCIAL_PLACEMENT_TAKE(PaidChannelKind.SOCIAL, "pl_take", 4, true, 55, 24, ReportScope.BOTH,
			"Placement Breakdown tiles in this order: volume leader, support placement, recommended action, "
					+ "watch-out; title e.g. \"Reels Volume Leader\", text e.g. \"628 conversions at a $23.41 CPA.\""),
	SOCIAL_HIGHLIGHT(PaidChannelKind.SOCIAL, "highlight", 4, true, 125, 32, ReportScope.EOC,
			"PAID SOCIAL PERFORMANCE HIGHLIGHTS: four distinct findings (audiences, placements, creatives, "
					+ "totals), title a short headline in Title Case, text one evidence-led sentence"),
	SOCIAL_RECO(PaidChannelKind.SOCIAL, "reco", 4, true, 110, 33, ReportScope.EOC,
			"PAID SOCIAL RECOMMENDATIONS: four next steps (Pixel/CAPI tracking, budget stability, audiences and "
					+ "exclusions, creative), title a short headline in Title Case, text one actionable sentence"),
	SOCIAL_EOM_WORKED(PaidChannelKind.SOCIAL, "eom_worked", 1, false, 110, 0, ReportScope.EOM, "What worked",
			"EOM metric slide \"What worked\": the month's strongest result, with its figure"),
	SOCIAL_EOM_TOP_CREATIVE(PaidChannelKind.SOCIAL, "eom_top_creative", 1, false, 110, 0, ReportScope.EOM,
			"Top creative",
			"EOM metric slide \"Top creative\": the month's best creative and its figures"),
	SOCIAL_EOM_OPTIMISE(PaidChannelKind.SOCIAL, "eom_optimise", 1, false, 110, 0, ReportScope.EOM, "Optimise",
			"EOM metric slide \"Optimise\": the weakest spot and what to change"),
	SOCIAL_EOM_NEXT_MONTH(PaidChannelKind.SOCIAL, "eom_next_month", 1, false, 110, 0, ReportScope.EOM, "Next month",
			"EOM metric slide \"Next month\": the plan for the coming month");

	private final PaidChannelKind channel;
	private final String key;
	private final int count;
	private final boolean titled;
	private final int limit;
	private final int titleLimit;
	private final ReportScope scope;
	private final String slideLabel;
	private final String guidance;

	/**
	 * A slot whose box carries no printed label of its own.
	 *
	 * @param channel    the paid channel
	 * @param key        the token name without prefix and number
	 * @param count      the entry count
	 * @param titled     whether each entry has a heading token
	 * @param limit      the text's character budget
	 * @param titleLimit the heading's character budget
	 * @param scope      the report types carrying the slot
	 * @param guidance   what the copy says, for the prompt
	 */
	PaidCopySlot(PaidChannelKind channel, String key, int count, boolean titled, int limit, int titleLimit,
			ReportScope scope, String guidance) {
		this(channel, key, count, titled, limit, titleLimit, scope, "", guidance);
	}

	/**
	 * A slot whose box sits under a label the slide prints itself (the EOM metric slide's "What worked").
	 *
	 * @param channel    the paid channel
	 * @param key        the token name without prefix and number
	 * @param count      the entry count
	 * @param titled     whether each entry has a heading token
	 * @param limit      the text's character budget
	 * @param titleLimit the heading's character budget
	 * @param scope      the report types carrying the slot
	 * @param slideLabel the label printed above the box, which the copy must not repeat
	 * @param guidance   what the copy says, for the prompt
	 */
	PaidCopySlot(PaidChannelKind channel, String key, int count, boolean titled, int limit, int titleLimit,
			ReportScope scope, String slideLabel, String guidance) {
		this.channel = channel;
		this.key = key;
		this.count = count;
		this.titled = titled;
		this.limit = limit;
		this.titleLimit = titleLimit;
		this.scope = scope;
		this.slideLabel = slideLabel;
		this.guidance = guidance;
	}

	/**
	 * The label the slide prints above this slot's box, which the copy must not start with.
	 *
	 * @return the label, e.g. {@code "What worked"}; empty when the box has none
	 */
	public String slideLabel() {
		return slideLabel;
	}

	/**
	 * The slot families one channel's slides carry in one report type.
	 *
	 * @param channel the paid channel
	 * @param eom     whether the deck is an EOM report
	 * @return the channel's slots, in declaration (slide) order
	 */
	public static List<PaidCopySlot> forChannel(PaidChannelKind channel, boolean eom) {
		return Arrays.stream(values())
				.filter(s -> s.channel == channel && s.scope.includes(eom))
				.toList();
	}

	/**
	 * The channel whose slides carry this slot.
	 *
	 * @return the paid channel
	 */
	public PaidChannelKind channel() {
		return channel;
	}

	/**
	 * The token name without the channel prefix and entry number.
	 *
	 * @return the key, e.g. {@code pmax_take}
	 */
	public String key() {
		return key;
	}

	/**
	 * How many numbered entries the family has.
	 *
	 * @return the entry count; 1 with {@link #numbered()} false means a single token
	 */
	public int count() {
		return count;
	}

	/**
	 * Whether each entry also has a heading token ({@code key_N_title}).
	 *
	 * @return {@code true} for a titled family
	 */
	public boolean titled() {
		return titled;
	}

	/**
	 * The character budget of one entry's text.
	 *
	 * @return the limit
	 */
	public int limit() {
		return limit;
	}

	/**
	 * The character budget of one entry's heading.
	 *
	 * @return the limit; 0 when {@link #titled()} is false
	 */
	public int titleLimit() {
		return titleLimit;
	}

	/**
	 * What the copy says, written for the prompt.
	 *
	 * @return the guidance
	 */
	public String guidance() {
		return guidance;
	}

	/**
	 * Whether the entries carry a number ({@code kw_takeaway_1}) rather than being one bare token
	 * ({@code verdict}).
	 *
	 * @return {@code true} for a numbered family
	 */
	public boolean numbered() {
		return count > 1 || titled;
	}
}
