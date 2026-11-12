package com.aidigital.reportconstructor.service.reports.claude;

/**
 * Lead-phrase instructions of the Top Publishers, Creative analysis and Geo analysis prompts, shared by
 * {@link ClaudeBatchPromptBuilder}.
 *
 * <p>On those slides each takeaway opens with a short phrase in bold brand blue and continues in regular body
 * text ("Curated the Buy — Whitelisted 15 premium domains…"). Claude marks that phrase with {@code [[…]]}; the
 * deck markup pass styles it and removes the brackets once every value is on the slides.
 */
final class LeadPhraseSpec {

	/** Publisher observations: a two-to-four word headline, an em dash, then one sentence. */
	static final String HEADLINE = "Each one opens with a 2-4 word headline wrapped in [[double brackets]], then "
			+ "\" — \" and ONE complete sentence, e.g. \"[[Curated the Buy]] — Whitelisted 15 premium domains to "
			+ "control adjacency: 39.9% of impr at $6.36 CPM.\" The headline and brackets count toward the limit.";

	/** Creative and geo strings: the key fact as a short lead clause that the sentence runs on from. */
	static final String CLAUSE = "Each string opens with its key fact as a 3-7 word lead clause wrapped in "
			+ "[[double brackets]], and the sentence runs on from it, e.g. \"[[The 300x250 carried 62.7% of "
			+ "delivery]] (233,621 impr) at a 0.20% CTR.\" The brackets count toward the limit. ";

	/**
	 * Creative strings: all four take a lead clause, the optimisation included — it sits in the same KEY TAKEAWAYS
	 * list as the three insights, so a plain fourth bullet would read as a styling slip.
	 */
	static final String CREATIVE = CLAUSE + "This applies to ALL four strings: string 4 (the optimisation) also "
			+ "opens with its own bracketed lead clause naming the action, e.g. \"[[Raise HomeLoan and AutoLoan "
			+ "spend]] up to 20% next flight and refresh Checking's creative.\" ";

	/** Geo strings: the insights take a lead clause, the recommendation (under its own label) does not. */
	static final String GEO = CLAUSE + "This applies to strings 1-3 only: string 4 has NO brackets. ";

	/**
	 * Holds constants only.
	 */
	private LeadPhraseSpec() {
	}
}
