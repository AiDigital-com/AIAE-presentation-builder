package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.TemplateVariant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The one place that answers "which template does an MPO deck clone?". Configuration comes from
 * {@link MpoDeckProperties}; with an id left blank, an MPO request for that report type is built on the
 * standard template, so switching the design on is purely a configuration change per environment.
 */
@Component
@RequiredArgsConstructor
public class MpoDeckPolicy {

	/** Startup-log marker for a report type whose MPO requests fall back to the standard template. */
	private static final String STANDARD_FALLBACK_MARKER = "(standard)";

	private final MpoDeckProperties props;

	/**
	 * Resolves the template to clone: the configured MPO template for the report type when the MPO design
	 * is requested and one is configured, otherwise the caller's standard template.
	 *
	 * @param variant           requested deck design, may be {@code null} (read as standard)
	 * @param reportType        report template code ({@code "EOC"}/{@code "EOM"}), may be {@code null}
	 * @param standardTemplateId the template the standard design clones for this report type
	 * @return the template id to clone
	 */
	public String templateIdOr(TemplateVariant variant, String reportType, String standardTemplateId) {
		if (variant != TemplateVariant.MPO) {
			return standardTemplateId;
		}
		String mpoTemplateId = mpoTemplateIdFor(reportType);
		return mpoTemplateId.isEmpty() ? standardTemplateId : mpoTemplateId;
	}

	/**
	 * Tells whether a deck is cloned from an MPO template at all — EOC or EOM — and so carries the MPO-only
	 * narrative slots. Follows {@link #templateIdOr} exactly: an MPO request that fell back to the standard
	 * template is a standard deck.
	 *
	 * @param variant    requested deck design, may be {@code null} (read as standard)
	 * @param reportType report template code ({@code "EOC"}/{@code "EOM"}), may be {@code null}
	 * @return {@code true} when an MPO template is configured for the report type and this deck clones it
	 */
	public boolean usesMpoTemplate(TemplateVariant variant, String reportType) {
		return variant == TemplateVariant.MPO && !mpoTemplateIdFor(reportType).isEmpty();
	}

	/**
	 * Tells whether a deck is cloned from the EOC MPO template, and so must be worked on with that
	 * template's slide ids rather than the standard ones. Follows {@link #templateIdOr} exactly: an MPO
	 * request that fell back to the standard template keeps the standard ids.
	 *
	 * @param variant    requested deck design, may be {@code null} (read as standard)
	 * @param reportType report template code ({@code "EOC"}/{@code "EOM"}), may be {@code null}
	 * @return {@code true} when the EOC MPO template is configured and this deck clones it
	 */
	public boolean usesEocSlides(TemplateVariant variant, String reportType) {
		return variant == TemplateVariant.MPO
				&& !EomDeckPolicy.EOM_REPORT_TYPE.equals(reportType)
				&& !mpoTemplateIdFor(reportType).isEmpty();
	}

	/**
	 * Returns the configured slide ids of the EOC MPO template.
	 *
	 * @return the EOC MPO slide ids, never {@code null}
	 */
	public MpoDeckSlideIds eocSlideIds() {
		return props.getEocSlides() == null ? new MpoDeckSlideIds() : props.getEocSlides();
	}

	/**
	 * Returns the configured pacing-chart source workbooks of the MPO tactic slides.
	 *
	 * @return the MPO tactic chart sources, never {@code null}
	 */
	public MpoDeckChartSources tacticChartSources() {
		return props.getTacticCharts() == null ? new MpoDeckChartSources() : props.getTacticCharts();
	}

	/**
	 * Returns the configured breakdown-chart source workbooks of the MPO audience and device slides.
	 *
	 * @return the MPO breakdown chart sources, never {@code null}
	 */
	public MpoDeckBreakdownCharts breakdownChartSources() {
		return props.getBreakdownCharts() == null ? new MpoDeckBreakdownCharts() : props.getBreakdownCharts();
	}

	/**
	 * Returns the configured MPO template id for a report type.
	 *
	 * @param reportType report template code ({@code "EOC"}/{@code "EOM"}), may be {@code null}
	 * @return the trimmed template id, or an empty string when none is configured
	 */
	String mpoTemplateIdFor(String reportType) {
		return trimmed(EomDeckPolicy.EOM_REPORT_TYPE.equals(reportType)
				? props.getEomSlidesTemplateId()
				: props.getEocSlidesTemplateId());
	}

	/**
	 * Renders the MPO templates for the startup log, next to the standard and EOM ones.
	 *
	 * @return the configured EOC and EOM MPO template ids, each marked when unset
	 */
	public String describeTemplates() {
		return "eoc=" + describe(trimmed(props.getEocSlidesTemplateId()))
				+ " eom=" + describe(trimmed(props.getEomSlidesTemplateId()));
	}

	/**
	 * Trims a configured id, treating {@code null} as unset.
	 *
	 * @param id the configured id, may be {@code null}
	 * @return the trimmed id, or an empty string when unset
	 */
	String trimmed(String id) {
		return id == null ? "" : id.trim();
	}

	/**
	 * Renders one template id for the startup log.
	 *
	 * @param templateId the trimmed template id, possibly empty
	 * @return the id, or a marker saying the standard template is used instead
	 */
	String describe(String templateId) {
		return templateId.isEmpty() ? STANDARD_FALLBACK_MARKER : templateId;
	}
}
