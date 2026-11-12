package com.aidigital.reportconstructor.service.integration;

import com.aidigital.reportconstructor.externalservices.google.deck.DeckBreakdown;
import com.aidigital.reportconstructor.externalservices.google.deck.DeckMarkers;
import com.aidigital.reportconstructor.externalservices.google.deck.DeckTemplate;
import com.aidigital.reportconstructor.externalservices.google.deck.PaidDeckChannel;
import com.aidigital.reportconstructor.externalservices.google.deck.PaidDeckTableMarker;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PaidDeckFill;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PaidDeckTable;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.TemplateVariant;
import com.aidigital.reportconstructor.service.reports.enums.PaidChannelKind;
import com.aidigital.reportconstructor.service.reports.enums.PaidTableMarker;
import com.aidigital.reportconstructor.service.reports.model.PaidSlideTable;
import com.aidigital.reportconstructor.service.reports.model.PaidSlidesFill;
import com.aidigital.reportconstructor.service.reports.ports.DeckMarkup;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DeckModelMapperTest {

	@Test
	void shouldDeclareTheSameBreakdownsInTheSameOrderWithTheSameCodesTest() {
		// Given: the deck's copy is matched by name, and the workbook reads it by ordinal
		DeckModelMapper mapper = new DeckModelMapper();

		// When:
		List<DeckBreakdown> mapped = Arrays.stream(BreakdownType.values()).map(mapper::breakdown).toList();

		// Then:
		assertThat(mapped).containsExactly(DeckBreakdown.values());
		for (BreakdownType type : BreakdownType.values()) {
			DeckBreakdown deck = mapper.breakdown(type);
			assertThat(deck.ordinal()).isEqualTo(type.ordinal());
			assertThat(deck.code()).isEqualTo(type.code());
			assertThat(deck.anchorLabel()).isEqualTo(type.anchorLabel());
		}
	}

	@Test
	void shouldDeclareTheSameTemplateVariantsTest() {
		// Given:
		DeckModelMapper mapper = new DeckModelMapper();

		// When:
		List<DeckTemplate> mapped = Arrays.stream(TemplateVariant.values()).map(mapper::template).toList();

		// Then:
		assertThat(mapped).containsExactly(DeckTemplate.values());
		assertThat(mapper.template(null)).isNull();
	}

	@Test
	void shouldDeclareTheSamePaidMarkersAndChannelsTest() {
		// Given:
		DeckModelMapper mapper = new DeckModelMapper();

		// When:
		List<PaidDeckTableMarker> markers = Arrays.stream(PaidTableMarker.values())
				.map(marker -> mapper.paidTable(new PaidSlideTable(marker, List.of(), null)).marker())
				.toList();

		// Then: same constants, alt-text titles, channels and channel texts
		assertThat(markers).containsExactly(PaidDeckTableMarker.values());
		for (PaidTableMarker marker : PaidTableMarker.values()) {
			PaidDeckTableMarker deck = PaidDeckTableMarker.valueOf(marker.name());
			assertThat(deck.marker()).isEqualTo(marker.marker());
			assertThat(deck.channel().name()).isEqualTo(marker.channel().name());
		}
		assertThat(Arrays.stream(PaidChannelKind.values()).map(Enum::name))
				.containsExactly(Arrays.stream(PaidDeckChannel.values()).map(Enum::name).toArray(String[]::new));
		for (PaidChannelKind channel : PaidChannelKind.values()) {
			PaidDeckChannel deck = PaidDeckChannel.valueOf(channel.name());
			assertThat(deck.tokenPrefix()).isEqualTo(channel.tokenPrefix());
			assertThat(deck.displayName()).isEqualTo(channel.displayName());
			assertThat(deck.dividerTitle()).isEqualTo(channel.dividerTitle());
		}
	}

	@Test
	void shouldUseTheSameMarkupMarkersAsTheServiceWritesTest() {
		// Given: the service writes these into placeholder values; the deck code looks for them

		// When-Then:
		assertThat(DeckMarkers.HIDE_SHAPE).isEqualTo(DeckMarkup.HIDE_SHAPE);
		assertThat(DeckMarkers.LEAD_OPEN).isEqualTo(DeckMarkup.LEAD_OPEN);
		assertThat(DeckMarkers.LEAD_CLOSE).isEqualTo(DeckMarkup.LEAD_CLOSE);
	}

	@Test
	void shouldKeepTacticOrderAndNullEntriesWhenMappingEnabledSectionsTest() {
		// Given:
		DeckModelMapper mapper = new DeckModelMapper();
		Map<Integer, Set<BreakdownType>> enabled = new LinkedHashMap<>();
		enabled.put(3, EnumSet.of(BreakdownType.DEVICE, BreakdownType.GEO));
		enabled.put(1, null);
		enabled.put(2, EnumSet.noneOf(BreakdownType.class));

		// When:
		Map<Integer, Set<DeckBreakdown>> mapped = mapper.breakdownsByTactic(enabled);

		// Then:
		assertThat(mapped.keySet()).containsExactly(3, 1, 2);
		assertThat(mapped.get(3)).containsExactly(DeckBreakdown.GEO, DeckBreakdown.DEVICE);
		assertThat(mapped.get(1)).isNull();
		assertThat(mapped.get(2)).isEmpty();
		assertThat(mapper.breakdownsByTactic(null)).isNull();
	}

	@Test
	void shouldMapThePaidFillTest() {
		// Given:
		DeckModelMapper mapper = new DeckModelMapper();
		Set<PaidChannelKind> absent = new LinkedHashSet<>(List.of(PaidChannelKind.SOCIAL));
		PaidSlidesFill fill = new PaidSlidesFill(List.of(new PaidSlideTable(PaidTableMarker.SEARCH_KEYWORDS,
				List.of(List.of("shoes", "120")), List.of("Total", "120"))), absent);

		// When:
		PaidDeckFill mapped = mapper.paidFill(fill);

		// Then:
		assertThat(mapped).isEqualTo(new PaidDeckFill(List.of(new PaidDeckTable(PaidDeckTableMarker.SEARCH_KEYWORDS,
				List.of(List.of("shoes", "120")), List.of("Total", "120"))), Set.of(PaidDeckChannel.SOCIAL)));
		assertThat(mapper.paidFill(null)).isNull();
	}
}
