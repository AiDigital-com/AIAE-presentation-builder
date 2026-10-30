package com.aidigital.reportconstructor.service.reports.dto;

/**
 * One card of the MPO media-plan slide's "Proposal overview" column.
 *
 * @param point    the card's bold heading, {@code {{proposal point N}}}
 * @param overview the card's body, {@code {{proposal overview N}}}
 */
public record MpoProposalPoint(
		String point,
		String overview
) {
}
