package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.MpoFunnelStage;
import com.aidigital.reportconstructor.service.reports.dto.MpoNarrative;
import com.aidigital.reportconstructor.service.reports.dto.MpoNarrativeInput;
import com.aidigital.reportconstructor.service.reports.dto.MpoProposalPoint;
import com.aidigital.reportconstructor.service.reports.helpers.MpoNarrativeAssembler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Default {@link MpoNarrativeAssembler}: reads the deck's campaign and tactic figures out of the placeholder
 * map, and writes the HHI, proposal-card, funnel and recommendations-headline tokens back onto it.
 */
@Component
public class MpoNarrativeAssemblerImpl implements MpoNarrativeAssembler {

	/** Value an unresolved or unwritten narrative token flattens to. */
	static final String DASH = "—";

	/** Number of proposal cards on the MPO media-plan slide. */
	static final int PROPOSAL_POINTS = 2;

	/** Number of stages on the MPO funnel slide. */
	static final int FUNNEL_STAGES = 4;

	/** The HHI tile's token. */
	static final String HHI_TOKEN = "{{audience_hhi}}";

	/** The recommendations slide's headline token. */
	static final String HEADLINE_TOKEN = "{{recommendations headline}}";

	/** A proposal card's heading, taking the 1-based card number. */
	static final String PROPOSAL_POINT_TOKEN = "{{proposal point %d}}";

	/** A proposal card's body, taking the 1-based card number. */
	static final String PROPOSAL_OVERVIEW_TOKEN = "{{proposal overview %d}}";

	/** A funnel band's stage name, taking the 1-based stage number. */
	static final String FUNNEL_STAGE_TOKEN = "{{funnel stage %d}}";

	/** A funnel band's caption, taking the 1-based stage number. */
	static final String FUNNEL_LABEL_TOKEN = "{{funnel stage %d label}}";

	/** A funnel card's headline, taking the 1-based stage number. */
	static final String FUNNEL_TITLE_TOKEN = "{{funnel title %d}}";

	/** A funnel card's evidence line, taking the 1-based stage number. */
	static final String FUNNEL_DATA_TOKEN = "{{funnel data %d}}";

	/** A funnel card's reading of the evidence, taking the 1-based stage number. */
	static final String FUNNEL_STORY_TOKEN = "{{funnel story %d}}";

	/** A tactic group's "Our results" overview, taking the 1-based group number. */
	static final String RESULTS_OVERVIEW_TOKEN = "{{Our results overview %d}}";

	/** Number of tactic groups, each with its own "Our results" slide and overview. */
	static final int RESULTS_GROUPS = 4;

	/** Number of strategic-framework cards and of optimisation recommendations the deck carries. */
	static final int STRATEGY_CARDS = 4;

	/** Campaign-level facts in prompt order: the label the model reads, then the token it is read from. */
	static final List<Map.Entry<String, String>> CAMPAIGN_FACTS = List.of(
			Map.entry("Client", "{{client_name}}"),
			Map.entry("Flight", "{{flight_dates}}"),
			Map.entry("Campaign period", "{{campaign_period}}"),
			Map.entry("Total investment", "{{total_investment}}"),
			Map.entry("Audience age", "{{audience_age}}"),
			Map.entry("Audience segments", "{{audience_segments}}"),
			Map.entry("Targeted locations", "{{geo_locations}}"),
			Map.entry("Primary KPIs", "{{primary_kpis}}"),
			Map.entry("Funnel strategy", "{{funnel_stages}}"),
			Map.entry("Tactics", "{{tactics_list}}"),
			Map.entry("Total impressions", "{{total imps}}"),
			Map.entry("Total CTR", "{{total ctr}}"),
			Map.entry("Total VCR", "{{total vcr}}"),
			Map.entry("Market captured (reach)", "{{reach_f_pres}}"),
			Map.entry("Total addressable market", "{{market volume}}"),
			Map.entry("Proposal overview", "{{proposal overview}}"));

	/** Per-tactic facts in prompt order: the label, then the token suffix after {@code "{{tactic N"}. */
	static final List<Map.Entry<String, String>> TACTIC_FACTS = List.of(
			Map.entry("funnel goal", " goal}}"),
			Map.entry("impressions", " imps}}"),
			Map.entry("CTR", " ctr}}"),
			Map.entry("VCR", " vcr}}"),
			Map.entry("reach", " reach}}"),
			Map.entry("frequency", " f}}"),
			Map.entry("spend", " spend}}"),
			Map.entry("benchmark", " – bench}}"));

	@Override
	public MpoNarrativeInput toInput(Map<String, String> flat, int tacticCount) {
		Map<String, String> map = flat == null ? Map.of() : flat;
		List<String> campaign = new ArrayList<>();
		CAMPAIGN_FACTS.forEach(fact -> addFact(campaign, fact.getKey(), map.get(fact.getValue())));
		for (int i = 1; i <= STRATEGY_CARDS; i++) {
			addFact(campaign, "Strategic insight " + i,
					joined(map.get("{{Strategic point " + i + "}}"), map.get("{{Strategic overview " + i + "}}")));
		}
		for (int i = 1; i <= STRATEGY_CARDS; i++) {
			addFact(campaign, "Recommendation " + i,
					joined(map.get("{{recommendation " + i + "}}"), map.get("{{recommendation " + i + " text}}")));
		}
		List<String> tactics = new ArrayList<>();
		for (int n = 1; n <= tacticCount; n++) {
			String line = tacticLine(map, n);
			if (line != null) {
				tactics.add(line);
			}
		}
		List<String> overviews = new ArrayList<>();
		for (int g = 1; g <= RESULTS_GROUPS; g++) {
			overviews.add(present(map.get(String.format(RESULTS_OVERVIEW_TOKEN, g))));
		}
		return new MpoNarrativeInput(campaign, tactics, overviews);
	}

	/**
	 * Renders one tactic's figures as a single prompt line, naming the KPI it is judged on.
	 *
	 * @param flat the resolved placeholder map
	 * @param n    the 1-based tactic number
	 * @return the line, or {@code null} when the tactic has no name and no figures
	 */
	String tacticLine(Map<String, String> flat, int n) {
		String prefix = "{{tactic " + n;
		List<String> parts = new ArrayList<>();
		TACTIC_FACTS.forEach(fact -> {
			String value = present(flat.get(prefix + fact.getValue()));
			if (value != null) {
				parts.add(fact.getKey() + " " + value);
			}
		});
		String kpiType = present(flat.get(prefix + " KPI type}}"));
		String kpi = present(flat.get(prefix + " KPI}}"));
		if (kpiType != null && kpi != null) {
			parts.add("KPI " + kpiType + " " + kpi);
		}
		String name = present(flat.get(prefix + "}}"));
		if (name == null && parts.isEmpty()) {
			return null;
		}
		return "Tactic " + n + " — " + (name == null ? "unnamed" : name) + ": " + String.join("; ", parts);
	}

	@Override
	public void write(Map<String, String> flat, MpoNarrative narrative) {
		if (flat == null) {
			return;
		}
		put(flat, HHI_TOKEN, narrative == null ? null : narrative.audienceHhi());
		put(flat, HEADLINE_TOKEN, narrative == null ? null : narrative.recommendationsHeadline());
		List<MpoProposalPoint> points = narrative == null || narrative.proposalPoints() == null
				? List.of() : narrative.proposalPoints();
		for (int i = 1; i <= PROPOSAL_POINTS; i++) {
			MpoProposalPoint point = i <= points.size() ? points.get(i - 1) : null;
			put(flat, String.format(PROPOSAL_POINT_TOKEN, i), point == null ? null : point.point());
			put(flat, String.format(PROPOSAL_OVERVIEW_TOKEN, i), point == null ? null : point.overview());
		}
		List<MpoFunnelStage> stages = narrative == null || narrative.funnelStages() == null
				? List.of() : narrative.funnelStages();
		for (int i = 1; i <= FUNNEL_STAGES; i++) {
			MpoFunnelStage stage = i <= stages.size() ? stages.get(i - 1) : null;
			put(flat, String.format(FUNNEL_STAGE_TOKEN, i), stage == null ? null : stage.stage());
			put(flat, String.format(FUNNEL_LABEL_TOKEN, i), stage == null ? null : stage.label());
			put(flat, String.format(FUNNEL_TITLE_TOKEN, i), stage == null ? null : stage.title());
			put(flat, String.format(FUNNEL_DATA_TOKEN, i), stage == null ? null : stage.data());
			put(flat, String.format(FUNNEL_STORY_TOKEN, i), stage == null ? null : stage.story());
		}
		List<String> overviews = narrative == null || narrative.resultsOverviews() == null
				? List.of() : narrative.resultsOverviews();
		for (int g = 1; g <= RESULTS_GROUPS && g <= overviews.size(); g++) {
			replace(flat, String.format(RESULTS_OVERVIEW_TOKEN, g), overviews.get(g - 1));
		}
	}

	/**
	 * Replaces a token's value with its tightened rewrite. Unlike {@link #put} this overwrites on purpose: the
	 * overview the deck already carries was written for the standard template's larger box and overflows the
	 * MPO one, so the shorter version must win. With no rewrite, the original is kept rather than dashed.
	 *
	 * @param flat  the placeholder map to fill, mutated in place
	 * @param token the full token
	 * @param value the rewrite, or {@code null} when the call produced none
	 */
	void replace(Map<String, String> flat, String token, String value) {
		String copy = present(value);
		if (copy != null) {
			flat.put(token, copy);
		}
	}

	/**
	 * Writes one token, leaving a value the map already carries in place and dashing a missing one, so the
	 * slide never prints a token's own name.
	 *
	 * @param flat  the placeholder map to fill, mutated in place
	 * @param token the full token
	 * @param value the generated copy, or {@code null} when the call produced none
	 */
	void put(Map<String, String> flat, String token, String value) {
		if (present(flat.get(token)) != null) {
			return;
		}
		String copy = present(value);
		flat.put(token, copy == null ? DASH : copy);
	}

	/**
	 * Appends one {@code "Label: value"} fact when the value is present.
	 *
	 * @param facts the lines being built
	 * @param label the label the model reads
	 * @param value the resolved value, possibly missing, blank or a dash
	 */
	void addFact(List<String> facts, String label, String value) {
		String present = present(value);
		if (present != null) {
			facts.add(label + ": " + present);
		}
	}

	/**
	 * Joins a heading and its body into one fact, keeping whichever half is present.
	 *
	 * @param heading the bold lead, may be missing
	 * @param body    the body text, may be missing
	 * @return {@code "heading: body"}, the present half alone, or {@code null} when both are missing
	 */
	String joined(String heading, String body) {
		String h = present(heading);
		String b = present(body);
		if (h == null) {
			return b;
		}
		return b == null ? h : h + ": " + b;
	}

	/**
	 * Treats a missing, blank, dash or still-raw token value as absent.
	 *
	 * @param value the value, possibly {@code null}
	 * @return the trimmed value, or {@code null} when it carries nothing
	 */
	String present(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty() || DASH.equals(trimmed) || trimmed.startsWith("{{")) {
			return null;
		}
		return trimmed;
	}
}
