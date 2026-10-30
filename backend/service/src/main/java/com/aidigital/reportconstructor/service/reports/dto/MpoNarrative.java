package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * The copy only the MPO deck design carries: the media-plan slide's HHI tile and two proposal cards, the
 * four-stage funnel slide, and the recommendations slide's headline. A field the call did not produce is
 * {@code null} (or the list is shorter), and its tokens are dashed rather than left raw.
 *
 * @param audienceHhi             the household-income target, {@code {{audience_hhi}}}; {@code null} when
 *                                neither the brief nor the plan states one
 * @param proposalPoints          the two proposal cards in slide order, never {@code null}
 * @param funnelStages            the four funnel stages, upper funnel first, never {@code null}
 * @param recommendationsHeadline the recommendations slide's headline, {@code {{recommendations headline}}}
 * @param resultsOverviews        each tactic group's "Our results" overview, tightened to fit the MPO box, in
 *                                the input's group order; an entry is {@code null} where nothing was written.
 *                                Never {@code null}
 */
public record MpoNarrative(
		String audienceHhi,
		List<MpoProposalPoint> proposalPoints,
		List<MpoFunnelStage> funnelStages,
		String recommendationsHeadline,
		List<String> resultsOverviews
) {
}
