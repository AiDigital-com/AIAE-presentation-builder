package com.aidigital.reportconstructor.mappers.reports;

import com.aidigital.reportconstructor.api.v1.model.IdNamingV1;
import com.aidigital.reportconstructor.api.v1.model.LineItemMatchResultV1;
import com.aidigital.reportconstructor.api.v1.model.MappingEntryV1;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchView;
import com.aidigital.reportconstructor.service.reports.dto.TacticMapping;
import com.aidigital.reportconstructor.service.reports.services.LineItemMeta;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LineItemMatchApiMapper} through its generated implementation.
 */
class LineItemMatchApiMapperTest {

	@Test
	void shouldMapTheWholeMatchingViewTest() {
		// Given: one matched tactic, one naming entry (plus one unknown id) and one channel group
		LineItemMatchApiMapperImpl mapper = new LineItemMatchApiMapperImpl();
		Map<String, LineItemMeta> namings = new LinkedHashMap<>();
		namings.put("LI-1", new LineItemMeta("LI-1", "DSP_Display_Q3", "Display", "Prospecting"));
		namings.put("LI-2", null);
		LineItemMatchView view = new LineItemMatchView(
				Arrays.asList(new TacticMapping(1, 3, "Display Prospecting", "LI-1", "DSP_Display_Q3", true), null),
				List.of("LI-1", "LI-2"),
				namings,
				Map.of("Display", List.of("LI-1")),
				true,
				1);

		// When:
		LineItemMatchResultV1 dto = mapper.toResult(view);

		// Then:
		assertThat(dto.getMapping()).hasSize(2);
		MappingEntryV1 entry = dto.getMapping().get(0);
		assertThat(entry.getTacticNum()).isEqualTo(1);
		assertThat(entry.getPlanTacticNum()).isEqualTo(3);
		assertThat(entry.getTacticName()).isEqualTo("Display Prospecting");
		assertThat(entry.getLineItemId()).isEqualTo("LI-1");
		assertThat(entry.getNamingSample()).isEqualTo("DSP_Display_Q3");
		assertThat(entry.getAutoMatched()).isTrue();
		assertThat(entry.getExpectedChannel()).isNull();
		assertThat(entry.getUnitPrice()).isNull();
		assertThat(entry.getMonthlyBudget()).isNull();
		assertThat(entry.getRateType()).isNull();
		assertThat(dto.getMapping().get(1)).isNull();
		assertThat(dto.getUniqueIds()).containsExactly("LI-1", "LI-2");
		assertThat(dto.getIdNamings()).containsOnlyKeys("LI-1", "LI-2");
		IdNamingV1 naming = dto.getIdNamings().get("LI-1");
		assertThat(naming.getNaming()).isEqualTo("DSP_Display_Q3");
		assertThat(naming.getChannel()).isEqualTo("Display");
		assertThat(naming.getTactic()).isEqualTo("Prospecting");
		assertThat(dto.getIdNamings().get("LI-2")).isNull();
		assertThat(dto.getChannelToIds()).containsExactly(Map.entry("Display", List.of("LI-1")));
		assertThat(dto.getWarnings()).isEmpty();
		assertThat(dto.getAutoMatched()).isTrue();
		assertThat(dto.getAutoMatchCount()).isEqualTo(1);
	}

	@Test
	void shouldMapAViewWithoutCollectionsTest() {
		// Given: nothing matched and no collections were produced
		LineItemMatchApiMapperImpl mapper = new LineItemMatchApiMapperImpl();
		LineItemMatchView view = new LineItemMatchView(null, null, null, null, false, 0);

		// When:
		LineItemMatchResultV1 dto = mapper.toResult(view);

		// Then: list/map fields the view never set keep their empty defaults or are null
		assertThat(dto.getMapping()).isNull();
		assertThat(dto.getUniqueIds()).isEmpty();
		assertThat(dto.getIdNamings()).isNull();
		assertThat(dto.getChannelToIds()).isEmpty();
		assertThat(dto.getAutoMatched()).isFalse();
		assertThat(dto.getAutoMatchCount()).isZero();
	}

	@Test
	void shouldReturnNullForNullInputsTest() {
		// Given:
		LineItemMatchApiMapperImpl mapper = new LineItemMatchApiMapperImpl();

		// When-Then:
		assertThat(mapper.toResult(null)).isNull();
		assertThat(mapper.toEntry(null)).isNull();
		assertThat(mapper.toIdNaming(null)).isNull();
	}

	@Test
	void shouldMapAnUnmatchedTacticRowTest() {
		// Given: a plan tactic with no line item found
		LineItemMatchApiMapperImpl mapper = new LineItemMatchApiMapperImpl();
		TacticMapping row = new TacticMapping(2, 2, "CTV", null, null, false);

		// When:
		MappingEntryV1 entry = mapper.toEntry(row);

		// Then:
		assertThat(entry.getTacticNum()).isEqualTo(2);
		assertThat(entry.getPlanTacticNum()).isEqualTo(2);
		assertThat(entry.getTacticName()).isEqualTo("CTV");
		assertThat(entry.getLineItemId()).isNull();
		assertThat(entry.getNamingSample()).isNull();
		assertThat(entry.getAutoMatched()).isFalse();
	}
}
