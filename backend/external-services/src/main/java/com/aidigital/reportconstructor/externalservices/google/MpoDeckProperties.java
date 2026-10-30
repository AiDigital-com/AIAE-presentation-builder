package com.aidigital.reportconstructor.externalservices.google;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

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
	private SlideIds eocSlides = new SlideIds();

	/**
	 * The object ids one template's slide passes work against, mirroring the standard ones under
	 * {@code external.google}. Only the master tactic-slide model is supported: an MPO template has no
	 * 28 drawn tactic slots to trim.
	 */
	@Getter
	@Setter
	public static class SlideIds {

		/** 1-based tactic group → that group's summary table (the "Platforms" table). */
		private Map<Integer, String> summaryTableObjectIds = Map.of();

		/** 1-based tactic group → that group's summary slide, when separate from its results slide. */
		private Map<Integer, String> summarySlideObjectIds = Map.of();

		/** 1-based tactic group → that group's results slide, deleted whole when the group is empty. */
		private Map<Integer, String> resultsSlideObjectIds = Map.of();

		/** The generic main tactic slide, duplicated once per tactic. */
		private String tacticMasterSlideObjectId = "";

		/** Breakdown wire code ({@code tp}, {@code ca}, {@code geo}, {@code aud}, {@code dev}) → master slide. */
		private Map<String, String> breakdownMasterSlideObjectIds = Map.of();

		/** The "Thoughts on tactic performance" master; blank because the MPO design dropped that slide. */
		private String thoughtsMasterSlideObjectId = "";
	}
}
