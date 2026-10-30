package com.aidigital.reportconstructor.service.reports.dto;

/**
 * One stage of the MPO "How channels work together across the funnel" slide, upper funnel first.
 *
 * @param stage the stage name on the funnel band, {@code {{funnel stage N}}}
 * @param label the band's caption, {@code {{funnel stage N label}}}
 * @param title the stage card's headline, {@code {{funnel title N}}}
 * @param data  the card's evidence line after "Data:", {@code {{funnel data N}}}
 * @param story the card's reading of that evidence after "Story:", {@code {{funnel story N}}}
 */
public record MpoFunnelStage(
		String stage,
		String label,
		String title,
		String data,
		String story
) {
}
