package com.aidigital.reportconstructor.service.reports.claude;

/**
 * Prompt text and budgets of the paid-media narrative call, which writes the copy of the MPO deck's Paid Search and
 * Paid Social slides. Kept apart from {@link ClaudeBatchPromptBuilder}, which is at its file-length limit.
 */
final class PaidPromptSpec {

	/** Log/warning label of the paid-media narrative call. */
	static final String PAID_NARRATIVE_LABEL = "PaidNarrative";

	/** Failure-log message of a paid narrative reply that carried no usable field. */
	static final String PAID_NARRATIVE_EMPTY_REASON = "reply carried no usable field; its slots ship dashed";

	/**
	 * Output budget of one channel's call: up to ~45 fields averaging ~80 characters plus JSON keys, about 5,000
	 * characters, with headroom so the last field is never cut off mid-object.
	 */
	static final int PAID_NARRATIVE_MAX_TOKENS = 3000;

	/** Opens the prompt: who writes, and for which slides. */
	static final String PAID_NARRATIVE_ROLE =
			"You are a senior performance-marketing strategist finishing an end-of-campaign report deck. The %s "
			+ "section's figures are already on the slides; you write its remaining copy: the overview verdict, "
			+ "the takeaways and tiles of each breakdown slide, and the highlights and recommendations. You write "
			+ "on behalf of the team that ran the campaign: confident, specific, client-friendly.\n\n";

	/** The closing output rules; ends in a blank line. */
	static final String PAID_NARRATIVE_OUTPUT_RULES =
			"Rules: quote only figures listed below, exactly as written; never invent a metric, a campaign, a "
			+ "keyword, a placement or an audience. Each field names the real row it is about. Keep to each "
			+ "field's character limit. A heading is Title Case without a full stop. No bullet characters, no "
			+ "markdown, no emoji. If a breakdown is missing from the export, write its fields about the totals "
			+ "instead of guessing.\n\n"
			+ "Return ONLY a JSON object whose keys are exactly the field keys above and whose values are strings "
			+ "— write NO preamble, reasoning or commentary before or after it; do NOT refuse and do NOT flag data "
			+ "problems.\n\n";

	/**
	 * Not instantiable: this class only holds constants.
	 */
	private PaidPromptSpec() {
	}
}
