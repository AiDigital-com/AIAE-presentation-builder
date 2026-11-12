package com.aidigital.reportconstructor.externalservices.bridge;

import com.aidigital.reportconstructor.externalservices.google.GoogleApiException;
import com.aidigital.reportconstructor.externalservices.google.deck.DeckBreakdown;
import com.aidigital.reportconstructor.externalservices.google.deck.DeckTemplate;
import com.aidigital.reportconstructor.externalservices.google.slides.SlidesDeckClient;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.common.error.ValidationParameter;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.TemplateVariant;
import com.aidigital.reportconstructor.service.reports.helpers.impl.BreakdownThoughtsGateImpl;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlidesProviderBridgeTest {

	@Test
	void shouldHandTheTacticsPastTheThoughtsGateDownWithTheMappedSectionsTest() {
		// Given: tactic 1 enables three breakdowns (past the ">2" gate), tactic 2 enables two
		SlidesDeckClient client = mock(SlidesDeckClient.class);
		SlidesProviderBridge bridge =
				new SlidesProviderBridge(client, new DeckModelMapper(), new BreakdownThoughtsGateImpl());
		Map<Integer, Set<BreakdownType>> enabled = new LinkedHashMap<>();
		enabled.put(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS, BreakdownType.GEO, BreakdownType.DEVICE));
		enabled.put(2, EnumSet.of(BreakdownType.TOP_PUBLISHERS, BreakdownType.GEO));
		Map<String, String> values = Map.of("{{publisher_1.1}}", "YouTube");

		// When:
		bridge.addBreakdownSlides("deck-1", enabled, values, "EOC", TemplateVariant.MPO, "ya29.user");

		// Then:
		Map<Integer, Set<DeckBreakdown>> expected = new LinkedHashMap<>();
		expected.put(1, EnumSet.of(DeckBreakdown.TOP_PUBLISHERS, DeckBreakdown.GEO, DeckBreakdown.DEVICE));
		expected.put(2, EnumSet.of(DeckBreakdown.TOP_PUBLISHERS, DeckBreakdown.GEO));
		verify(client).addBreakdownSlides("deck-1", expected, values, Set.of(1), "EOC", DeckTemplate.MPO,
				"ya29.user");
	}

	@Test
	void shouldReturnTheDeckUrlWithTheTemplateMappedTest() {
		// Given:
		SlidesDeckClient client = mock(SlidesDeckClient.class);
		when(client.createDeck("job-1", "Deck", Map.of(), "EOM", DeckTemplate.STANDARD, null))
				.thenReturn("https://docs.google.com/presentation/d/new/edit");
		SlidesProviderBridge bridge =
				new SlidesProviderBridge(client, new DeckModelMapper(), new BreakdownThoughtsGateImpl());

		// When:
		String url = bridge.createDeck("job-1", "Deck", Map.of(), "EOM", TemplateVariant.STANDARD, null);

		// Then:
		assertThat(url).isEqualTo("https://docs.google.com/presentation/d/new/edit");
	}

	@Test
	void shouldTurnAGoogleFailureIntoTheSameJobErrorTest() {
		// Given:
		SlidesDeckClient client = mock(SlidesDeckClient.class);
		doThrow(new GoogleApiException("Google Slides trimTactics failed: 500"))
				.when(client).trimTactics("deck-1", 3, "EOC", null, null);
		SlidesProviderBridge bridge =
				new SlidesProviderBridge(client, new DeckModelMapper(), new BreakdownThoughtsGateImpl());

		// When-Then: the message reaches the job exactly as the provider used to raise it
		assertThatThrownBy(() -> bridge.trimTactics("deck-1", 3, "EOC", null, null))
				.asInstanceOf(type(AppException.class))
				.satisfies(ex -> {
					assertThat(ex.getCode()).isEqualTo(ErrorReason.C000.getCode());
					assertThat(ex.getValidationMessage().getParameters()).containsExactly(
							new ValidationParameter("param0", "Google Slides trimTactics failed: 500"));
				});
	}

	@Test
	void shouldPassTheTemplateQuestionsThroughTest() {
		// Given:
		SlidesDeckClient client = mock(SlidesDeckClient.class);
		when(client.carriesMpoNarrative("EOC", DeckTemplate.MPO)).thenReturn(true);
		when(client.carriesTacticThoughts("EOC", null)).thenReturn(true);
		when(client.countSlides("deck-1", null)).thenReturn(42);
		SlidesProviderBridge bridge =
				new SlidesProviderBridge(client, new DeckModelMapper(), new BreakdownThoughtsGateImpl());

		// When-Then:
		assertThat(bridge.carriesMpoNarrative("EOC", TemplateVariant.MPO)).isTrue();
		assertThat(bridge.carriesTacticThoughts("EOC", null)).isTrue();
		assertThat(bridge.countSlides("deck-1", null)).isEqualTo(42);
	}
}
