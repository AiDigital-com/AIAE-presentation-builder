package com.aidigital.reportconstructor.service.reports.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NarrativeCopyLimitsTest {

	@Test
	void shouldPickTheCompactBudgetsOnlyForAnMpoDeckTest() {
		// Given:
		NarrativeCopyLimitsResolver resolver = new NarrativeCopyLimitsResolver();

		// When-Then: an MPO deck gets the narrow boxes' budgets, every other deck the standard ones
		assertThat(resolver.forDeck(true)).isEqualTo(NarrativeCopyLimits.COMPACT);
		assertThat(resolver.forDeck(false)).isEqualTo(NarrativeCopyLimits.STANDARD);
	}

	@Test
	void shouldQuoteEveryOverviewBudgetUnderItsCapTest() {
		// Given:
		NarrativeCopyLimits[] profiles = NarrativeCopyLimits.values();

		// When-Then: a reply that lands slightly over the quoted budget still fits the cap enforced on it
		for (NarrativeCopyLimits profile : profiles) {
			assertThat(profile.getStrategicOverviewPromptLimit()).isLessThan(profile.getStrategicOverviewLimit());
			assertThat(profile.getStrategicOverviewMinSentenceEnd()).isLessThan(profile.getStrategicOverviewLimit());
		}
	}
}
