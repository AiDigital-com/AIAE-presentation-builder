package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.TacticThoughts;
import org.springframework.stereotype.Component;

/**
 * Decides whether a tactic-thoughts reply is complete, and which of two attempts to keep.
 *
 * <p>Split out of {@link RealClaudeClient}, which had grown past the file-length limit. This is the
 * one judgement in the thoughts flow that is pure policy rather than transport: it has no HTTP, no
 * prompt and no parsing, so it is the part worth being able to read and test on its own.
 */
@Component
public class TacticThoughtsCompleteness {

	/** Four analytical thoughts plus the closing story — a complete reply fills all five. */
	private static final int THOUGHTS_SLOTS = 5;

	/**
	 * Reports whether a thoughts reply filled every slot the slide carries, which is the only shape worth
	 * accepting without a retry. Counted after normalization, so a thought that survived the call but was
	 * dropped as blank by the length pass counts as missing.
	 *
	 * @param thoughts the parsed thoughts, or {@code null} when the call produced nothing usable
	 * @return {@code true} when all {@link #THOUGHTS_SLOTS} slots are present and non-blank
	 */
	boolean isCompleteThoughts(TacticThoughts thoughts) {
		return countThoughts(thoughts) == THOUGHTS_SLOTS;
	}

	/**
	 * Counts the non-blank thoughts a reply carries, tolerating a {@code null} reply and the {@code null}
	 * entries {@link ClaudeResponseNormalizer#normalizeC} leaves behind for blanks.
	 *
	 * @param thoughts the parsed thoughts, or {@code null}
	 * @return how many slide-ready thoughts it holds
	 */
	int countThoughts(TacticThoughts thoughts) {
		if (thoughts == null || thoughts.thoughts() == null) {
			return 0;
		}
		int filled = 0;
		for (String thought : thoughts.thoughts()) {
			if (thought != null && !thought.isBlank()) {
				filled++;
			}
		}
		return filled;
	}

	/**
	 * Picks the attempt that carries more slide-ready thoughts, so a partial reply is never thrown away in
	 * favour of an emptier one. Ties go to the first attempt; {@code null} is returned only when neither
	 * attempt carries a single thought.
	 *
	 * @param first  the first attempt's thoughts, or {@code null}
	 * @param second the retry's thoughts, or {@code null}
	 * @return the fuller of the two, or {@code null} when both are empty
	 */
	TacticThoughts fullerThoughts(TacticThoughts first, TacticThoughts second) {
		int firstCount = countThoughts(first);
		int secondCount = countThoughts(second);
		if (firstCount == 0 && secondCount == 0) {
			return null;
		}
		return secondCount > firstCount ? second : first;
	}
}
