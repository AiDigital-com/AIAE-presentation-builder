package com.aidigital.reportconstructor.externalservices.google;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * The object ids one MPO template's slide passes work against, mirroring the standard ones under
 * {@code external.google}. Bound as a property group of {@link MpoDeckProperties}. Only the master
 * tactic-slide model is supported: an MPO template has no 28 drawn tactic slots to trim.
 */
@Getter
@Setter
public class MpoDeckSlideIds {

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
