package com.aidigital.reportconstructor.service.reports.dto;

/**
 * Which deck design a report is built on. Independent of the report type: both EOC and EOM have a
 * standard and an MPO design, and the variant only decides which template is cloned.
 */
public enum TemplateVariant {

	/**
	 * The template every report has used so far (the default).
	 */
	STANDARD,

	/**
	 * The "EOC Standard – MPO" / "EOM_Report MPO" design. Falls back to {@link #STANDARD}'s template
	 * wherever no MPO template is configured for the report type.
	 */
	MPO
}
