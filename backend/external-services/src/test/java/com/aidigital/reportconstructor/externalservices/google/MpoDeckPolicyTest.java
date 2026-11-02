package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.TemplateVariant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MpoDeckPolicyTest {

	@Test
	void shouldCloneTheEomMpoTemplateForAnEomMpoReportTest() {
		// Given
		MpoDeckProperties props = new MpoDeckProperties();
		props.setEocSlidesTemplateId("mpo-eoc");
		props.setEomSlidesTemplateId(" mpo-eom ");
		MpoDeckPolicy policy = new MpoDeckPolicy(props);

		// When
		String templateId = policy.templateIdOr(TemplateVariant.MPO, "EOM", "standard-eom");

		// Then: the configured id, trimmed
		assertThat(templateId).isEqualTo("mpo-eom");
	}

	@Test
	void shouldKeepTheStandardTemplateForAStandardReportTest() {
		// Given: MPO templates are configured, but the standard design is requested
		MpoDeckProperties props = new MpoDeckProperties();
		props.setEocSlidesTemplateId("mpo-eoc");
		props.setEomSlidesTemplateId("mpo-eom");
		MpoDeckPolicy policy = new MpoDeckPolicy(props);

		// When
		String templateId = policy.templateIdOr(TemplateVariant.STANDARD, "EOC", "standard-eoc");

		// Then
		assertThat(templateId).isEqualTo("standard-eoc");
	}

	@Test
	void shouldFallBackToTheStandardTemplateWhenTheMpoIdIsBlankTest() {
		// Given: an environment where the MPO template is not switched on yet
		MpoDeckProperties props = new MpoDeckProperties();
		props.setEocSlidesTemplateId("  ");
		MpoDeckPolicy policy = new MpoDeckPolicy(props);

		// When
		String templateId = policy.templateIdOr(TemplateVariant.MPO, "EOC", "standard-eoc");

		// Then: no attempt to clone a blank id
		assertThat(templateId).isEqualTo("standard-eoc");
	}

	@Test
	void shouldMarkUnsetTemplatesInTheStartupDescriptionTest() {
		// Given
		MpoDeckProperties props = new MpoDeckProperties();
		props.setEocSlidesTemplateId("mpo-eoc");
		props.setEomSlidesTemplateId(null);
		MpoDeckPolicy policy = new MpoDeckPolicy(props);

		// When
		String description = policy.describeTemplates();

		// Then
		assertThat(description).isEqualTo("eoc=mpo-eoc eom=(standard)");
	}

	@Test
	void shouldUseTheEocMpoSlidesOnlyForADeckClonedFromTheEocMpoTemplateTest() {
		// Given: only the EOC MPO template is switched on
		MpoDeckProperties props = new MpoDeckProperties();
		props.setEocSlidesTemplateId("mpo-eoc");
		MpoDeckPolicy policy = new MpoDeckPolicy(props);

		// When-Then: an EOC MPO deck uses them; a standard deck and an EOM deck never do
		assertThat(policy.usesEocSlides(TemplateVariant.MPO, "EOC")).isTrue();
		assertThat(policy.usesEocSlides(TemplateVariant.STANDARD, "EOC")).isFalse();
		assertThat(policy.usesEocSlides(null, "EOC")).isFalse();
		assertThat(policy.usesEocSlides(TemplateVariant.MPO, "EOM")).isFalse();
	}

	@Test
	void shouldCarryTheMpoNarrativeOnlyOnADeckClonedFromAnMpoTemplateTest() {
		// Given: only the EOC MPO template is switched on
		MpoDeckProperties props = new MpoDeckProperties();
		props.setEocSlidesTemplateId("mpo-eoc");
		MpoDeckPolicy policy = new MpoDeckPolicy(props);

		// When-Then: an EOC MPO deck carries it; a standard deck and an EOM MPO request (which falls back to the
		// standard EOM template) do not
		assertThat(policy.usesMpoTemplate(TemplateVariant.MPO, "EOC")).isTrue();
		assertThat(policy.usesMpoTemplate(TemplateVariant.STANDARD, "EOC")).isFalse();
		assertThat(policy.usesMpoTemplate(TemplateVariant.MPO, "EOM")).isFalse();
	}

	@Test
	void shouldKeepTheStandardSlidesWhenTheEocMpoTemplateIsNotSwitchedOnTest() {
		// Given: an environment with no EOC MPO template, where an MPO request clones the standard deck
		MpoDeckPolicy policy = new MpoDeckPolicy(new MpoDeckProperties());

		// When
		boolean usesMpoSlides = policy.usesEocSlides(TemplateVariant.MPO, "EOC");

		// Then: the deck that was actually cloned keeps its own (standard) ids
		assertThat(usesMpoSlides).isFalse();
	}
}
