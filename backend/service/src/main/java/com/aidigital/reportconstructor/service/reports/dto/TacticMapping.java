package com.aidigital.reportconstructor.service.reports.dto;

/**
 * One media-plan tactic's row in the line-item mapping table.
 *
 * @param tacticNum     1-based report slot of the tactic
 * @param planTacticNum 1-based position in the media plan; equals {@code tacticNum} until the user drops rows
 * @param tacticName    the tactic as written in the media plan
 * @param lineItemId    the suggested line-item id, or {@code null} when none was matched
 * @param namingSample  the suggested line item's naming, or {@code null}
 * @param autoMatched   whether the suggestion was an automatic (confident) match
 */
public record TacticMapping(
		int tacticNum,
		int planTacticNum,
		String tacticName,
		String lineItemId,
		String namingSample,
		boolean autoMatched) {
}
