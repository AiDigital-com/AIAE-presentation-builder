package com.aidigital.reportconstructor.externalservices.anthropic;

/**
 * Fixed text and character budgets of the MPO narrative call, shared by {@link ClaudeBatchPromptBuilder}, which
 * quotes the budgets to Claude, and {@link RealClaudeClient}, which normalizes the reply to the same numbers.
 *
 * <p>The budgets are sized to the "EOC Standard – MPO" sample deck's own copy so the generated text fits the
 * boxes that copy was drawn for.
 */
final class MpoPromptSpec {

	/** HHI tile ("$250K"). */
	static final int MPO_HHI_LIMIT = 20;

	/** Proposal card heading ("Long-Lead Awareness"). */
	static final int MPO_POINT_LIMIT = 30;

	/** Proposal card body (~130 characters in the sample). */
	static final int MPO_OVERVIEW_LIMIT = 150;

	/** Funnel band stage name ("THE CONSIDERATION"). */
	static final int MPO_STAGE_LIMIT = 20;

	/** Funnel band caption ("Maximize reach to build RTG audiences."). */
	static final int MPO_STAGE_LABEL_LIMIT = 40;

	/** Funnel card headline ("Capture the market."). */
	static final int MPO_FUNNEL_TITLE_LIMIT = 28;

	/** Funnel card evidence line ("2.27M Total Unique Reach at a 5.91 Frequency."). */
	static final int MPO_FUNNEL_DATA_LIMIT = 70;

	/** Funnel card story (~100 characters in the sample). */
	static final int MPO_FUNNEL_STORY_LIMIT = 120;

	/** Recommendations slide headline ("TURNING CURIOSITY INTO BOOKINGS"). */
	static final int MPO_HEADLINE_LIMIT = 40;

	/**
	 * A tactic group's "Our results" overview on the MPO deck. Its box holds three lines at 14 pt; the standard
	 * deck's results-overview budget runs into the table header below it.
	 */
	static final int MPO_RESULTS_OVERVIEW_LIMIT = 200;

	/** Number of tactic groups, each with its own "Our results" slide. */
	static final int MPO_RESULTS_GROUPS = 4;

	/** Number of proposal cards the MPO deck draws. */
	static final int MPO_PROPOSAL_POINTS = 2;

	/** Number of funnel stages the MPO deck draws. */
	static final int MPO_FUNNEL_STAGES = 4;

	/** Who is writing and which slides the MPO narrative fills; ends in a blank line. */
	static final String MPO_NARRATIVE_ROLE =
			"You are a senior digital media strategist finishing an end-of-campaign report deck. Every other "
			+ "slide is already written; you write the few remaining fields of the media-plan overview, the "
			+ "\"How channels work together across the funnel\" slide and the recommendations slide. You "
			+ "write on behalf of the team that ran the campaign: confident, specific, client-friendly.\n\n";

	/** The closing output rules for the MPO narrative call; ends in a blank line. */
	static final String MPO_NARRATIVE_OUTPUT_RULES =
			"Quote only figures given below, exactly as written; never invent a metric, a tactic or a market. "
			+ "When the campaign has fewer tactics than stages, a tactic may anchor more than one stage, or a "
			+ "stage may rest on a campaign total. No bullet characters, no markdown.\n\n"
			+ "Return ONLY a JSON object: {\"audience_hhi\": \"...\", \"proposal_points\": [{\"point\": "
			+ "\"...\", \"overview\": \"...\"}], \"funnel_stages\": [{\"stage\": \"...\", \"label\": \"...\", "
			+ "\"title\": \"...\", \"data\": \"...\", \"story\": \"...\"}], \"recommendations_headline\": "
			+ "\"...\", \"results_overviews\": [\"...\", \"...\", \"...\", \"...\"]} — write NO preamble, "
			+ "reasoning or commentary before or after it; do NOT refuse and do NOT flag data problems.\n\n";

	/**
	 * Not instantiable: this class only holds constants.
	 */
	private MpoPromptSpec() {
	}
}
