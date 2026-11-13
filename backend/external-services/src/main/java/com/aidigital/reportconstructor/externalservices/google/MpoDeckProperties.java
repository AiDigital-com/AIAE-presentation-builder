package com.aidigital.reportconstructor.externalservices.google;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed configuration for the MPO deck design, bound from {@code external.mpo-deck.*}. Kept apart from
 * {@link GoogleProperties} and {@link EomDeckProperties} for the same reason those two are apart: the MPO
 * design is switched on per environment, and its settings must not be able to reach the standard decks.
 *
 * <p>Each template id is the switch for its report type: blank means an MPO request for that report type
 * keeps being built on the standard template, which is the safe no-op default while the MPO templates are
 * still being marked up.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "external.mpo-deck")
public class MpoDeckProperties {

	/**
	 * Source deck copied for EOC reports on the MPO design ("EOC MPO – template"). Blank falls back to the
	 * standard EOC template.
	 */
	private String eocSlidesTemplateId = "";

	/**
	 * Source deck copied for EOM reports on the MPO design ("EOM MPO – template"). Blank falls back to the
	 * standard EOM template.
	 */
	private String eomSlidesTemplateId = "";

	/**
	 * Slide object ids of the EOC MPO template. Used instead of the standard ids under
	 * {@code external.google} whenever an EOC deck is cloned from {@link #eocSlidesTemplateId}; a blank id
	 * switches the pass that reads it off, it never falls back to the standard template's id.
	 */
	private MpoDeckSlideIds eocSlides = new MpoDeckSlideIds();

	/**
	 * Source workbooks of the pacing charts on the MPO tactic slides, used instead of the standard
	 * {@code external.google.charts} workbooks for every deck cloned from an MPO template.
	 */
	private MpoDeckChartSources tacticCharts = new MpoDeckChartSources();

	/**
	 * Source workbooks of the breakdown charts on the MPO audience and device slides, used instead of the
	 * standard {@code external.google.breakdown-charts} workbooks for every deck cloned from an MPO template.
	 */
	private MpoDeckBreakdownCharts breakdownCharts = new MpoDeckBreakdownCharts();
}
