package com.aidigital.reportconstructor.externalservices.google;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The one place that answers "is this deck an EOM deck, and what does that change?". Every EOM-specific
 * decision about the deck goes through this bean, so the EOC path stays a straight line through
 * {@link RealSlidesProvider} and an EOM change cannot reach it by accident.
 *
 * <p>Configuration comes from {@link EomDeckProperties}. With {@code slides-template-id} blank the policy
 * reports the pre-existing behaviour — an EOM deck is the EOC deck minus the slides configured for
 * dropping — which is what production runs while the EOM template is tested in the Replit workspace.
 */
@Component
@RequiredArgsConstructor
public class EomDeckPolicy {

	/** Report type code whose decks this policy governs. */
	static final String EOM_REPORT_TYPE = "EOM";

	private final EomDeckProperties props;

	/**
	 * Tells whether a report of the given type is governed by this policy.
	 *
	 * @param reportType report template code ({@code "EOC"}/{@code "EOM"}), may be {@code null}
	 * @return {@code true} for an EOM report
	 */
	boolean appliesTo(String reportType) {
		return EOM_REPORT_TYPE.equals(reportType);
	}

	/**
	 * Tells whether the EOM deck has a template of its own, i.e. whether the new EOM template is switched
	 * on in this environment.
	 *
	 * @return {@code true} when an EOM deck template id is configured
	 */
	boolean hasOwnTemplate() {
		String templateId = props.getSlidesTemplateId();
		return templateId != null && !templateId.isBlank();
	}

	/**
	 * Resolves which template deck to clone for the given report type: the EOM template when the report is
	 * an EOM one and an EOM template is configured, otherwise the caller's default (the EOC template).
	 *
	 * @param reportType        report template code ({@code "EOC"}/{@code "EOM"}), may be {@code null}
	 * @param defaultTemplateId the template to clone when this policy does not apply
	 * @return the template id to clone
	 */
	String templateIdOr(String reportType, String defaultTemplateId) {
		if (appliesTo(reportType) && hasOwnTemplate()) {
			return props.getSlidesTemplateId().trim();
		}
		return defaultTemplateId;
	}

	/**
	 * Returns the configured object ids of the slides an EOM deck drops, as configured (normalization of
	 * the {@code id.} prefix is the caller's).
	 *
	 * @return the configured slide object ids, never {@code null}
	 */
	List<String> dropSlideObjectIds() {
		return props.getDropSlideObjectIds();
	}

	/**
	 * Returns the configured text phrases identifying the slides an EOM deck drops.
	 *
	 * @return the configured title phrases, never {@code null}
	 */
	List<String> dropSlideTitles() {
		return props.getDropSlideTitles();
	}

	/**
	 * Returns the configured heading texts of the slide blocks (a card and everything on it) an EOM deck drops.
	 *
	 * @return the configured block headings, never {@code null}
	 */
	List<String> dropElementLabels() {
		return props.getDropElementLabels();
	}

	/**
	 * Returns the heading of the tactic-slide card that grows into the dropped block's column.
	 *
	 * @return the configured heading, or an empty string when the relayout is off
	 */
	String relayoutGrowLabel() {
		return props.getRelayoutGrowLabel();
	}

	/**
	 * Returns the heading of the tactic-slide card whose top edge marks the top of the content row.
	 *
	 * @return the configured heading, or an empty string when the relayout is off
	 */
	String relayoutAnchorLabel() {
		return props.getRelayoutAnchorLabel();
	}

	/**
	 * Renders the EOM deck mode for the startup log, where the question is always "which template did this
	 * process actually resolve?" — a deck built from the wrong template looks like a code problem while it
	 * is nearly always an environment variable that never reached the process.
	 *
	 * @return the configured EOM template id, or a marker saying the EOC template is used instead
	 */
	String describeTemplate() {
		return hasOwnTemplate() ? props.getSlidesTemplateId().trim() : "(same as EOC)";
	}
}
