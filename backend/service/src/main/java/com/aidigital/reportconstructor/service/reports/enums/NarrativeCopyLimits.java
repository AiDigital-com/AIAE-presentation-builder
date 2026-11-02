package com.aidigital.reportconstructor.service.reports.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Character budgets of the campaign-level Claude copy, which differ by deck design: the standard templates
 * draw wide text boxes, the MPO templates far narrower ones.
 *
 * <p>Each budget is enforced on Claude's reply (and by the compression call), and the prompts quote it so
 * the model aims for it. A run picks one profile up front from the deck it builds, so the prompt and the
 * cap can never disagree within a run. Per-tactic MPO copy that already had its own budgets (thoughts,
 * pacing takeaways, the MPO narrative) keeps them and is not listed here.
 */
@Getter
@RequiredArgsConstructor
public enum NarrativeCopyLimits {

	/** The standard EOC/EOM templates every report has used so far. */
	STANDARD(240, 230, 180, 220, 470, 700, 340, 150, 150, 140, 60, 200, 250, 70),

	/** The MPO templates, whose boxes hold roughly half the copy of the standard ones. */
	COMPACT(120, 110, 90, 100, 230, 400, 200, 60, 60, 110, 28, 85, 190, 60);

	/** Cap on one strategic insight's overview. */
	private final int strategicOverviewLimit;

	/** Overview budget quoted to Claude: a little under the cap, so a slight overrun still fits it. */
	private final int strategicOverviewPromptLimit;

	/** Earliest position a sentence-ending period may sit at for the overview cap to cut there. */
	private final int strategicOverviewMinSentenceEnd;

	/** Cap on one analytical "thoughts on performance" paragraph. */
	private final int thoughtLimit;

	/** Cap on the closing performance story that follows the analytical thoughts. */
	private final int storyLimit;

	/** Budget of the analytical thoughts taken together, quoted to Claude in the campaign-results prompt. */
	private final int thoughtsTotalLimit;

	/** Cap on the EOM north-star supporting paragraph. */
	private final int extendedNorthStarLimit;

	/** Cap on the EOM horizon block. */
	private final int horizonLimit;

	/** Cap on the EOM north-star slide's audience-segments line. */
	private final int eomAudienceSegmentsLimit;

	/** Cap on one EOM pacing-dashboard takeaway. */
	private final int pacingTakeawayLimit;

	/** Cap on one heading of the EOM "what we did" slide. */
	private final int whatWeDidHeadingLimit;

	/** Cap on one body text of the EOM "what we did" slide. */
	private final int whatWeDidTextLimit;

	/** Cap on the EOM updated-projection line. */
	private final int updatedProjectionLimit;

	/** Cap on one line of the EOM "focus next month" slide. */
	private final int focusItemLimit;

	/**
	 * Picks the profile for a run.
	 *
	 * @param mpoDeck whether the run builds its deck on an MPO template
	 * @return {@link #COMPACT} for an MPO deck, {@link #STANDARD} otherwise
	 */
	public static NarrativeCopyLimits forDeck(boolean mpoDeck) {
		return mpoDeck ? COMPACT : STANDARD;
	}
}
