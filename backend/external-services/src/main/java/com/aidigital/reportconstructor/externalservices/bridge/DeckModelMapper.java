package com.aidigital.reportconstructor.externalservices.bridge;

import com.aidigital.reportconstructor.externalservices.google.deck.DeckBreakdown;
import com.aidigital.reportconstructor.externalservices.google.deck.DeckTemplate;
import com.aidigital.reportconstructor.externalservices.google.deck.PaidDeckChannel;
import com.aidigital.reportconstructor.externalservices.google.deck.PaidDeckTableMarker;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PaidDeckFill;
import com.aidigital.reportconstructor.externalservices.google.deck.model.PaidDeckTable;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.TemplateVariant;
import com.aidigital.reportconstructor.service.reports.model.PaidSlideTable;
import com.aidigital.reportconstructor.service.reports.model.PaidSlidesFill;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps the service's deck vocabulary onto the Google integration's own copies. Enums are matched by constant
 * name; {@code DeckModelMapperTest} pins that both sides declare the same constants with the same values.
 * {@code null} stays {@code null} and map/collection order is kept, because the deck code reads both.
 */
@Component
public class DeckModelMapper {

	/**
	 * Maps a template variant.
	 *
	 * @param variant the service's template variant; may be {@code null} (read as standard downstream)
	 * @return the matching deck template, or {@code null}
	 */
	public DeckTemplate template(TemplateVariant variant) {
		return variant == null ? null : DeckTemplate.valueOf(variant.name());
	}

	/**
	 * Maps a breakdown type.
	 *
	 * @param type the service's breakdown type; may be {@code null}
	 * @return the matching deck breakdown, or {@code null}
	 */
	public DeckBreakdown breakdown(BreakdownType type) {
		return type == null ? null : DeckBreakdown.valueOf(type.name());
	}

	/**
	 * Maps the per-tactic enabled breakdown sections, keeping the tactic order and {@code null} entries.
	 *
	 * @param enabledByTactic 1-based tactic number → enabled sections; may be {@code null}
	 * @return the same map in deck terms, or {@code null}
	 */
	public Map<Integer, Set<DeckBreakdown>> breakdownsByTactic(Map<Integer, Set<BreakdownType>> enabledByTactic) {
		if (enabledByTactic == null) {
			return null;
		}
		Map<Integer, Set<DeckBreakdown>> mapped = new LinkedHashMap<>();
		enabledByTactic.forEach((tacticNum, sections) -> mapped.put(tacticNum, breakdowns(sections)));
		return mapped;
	}

	/**
	 * Maps a set of breakdown sections.
	 *
	 * @param sections the service's sections; may be {@code null}
	 * @return the same sections in deck terms, or {@code null}
	 */
	public Set<DeckBreakdown> breakdowns(Set<BreakdownType> sections) {
		if (sections == null) {
			return null;
		}
		Set<DeckBreakdown> mapped = EnumSet.noneOf(DeckBreakdown.class);
		for (BreakdownType type : sections) {
			mapped.add(breakdown(type));
		}
		return mapped;
	}

	/**
	 * Maps the paid-slides content of an MPO deck.
	 *
	 * @param fill the service's paid-slides fill; may be {@code null}
	 * @return the same content in deck terms, or {@code null}
	 */
	public PaidDeckFill paidFill(PaidSlidesFill fill) {
		if (fill == null) {
			return null;
		}
		List<PaidDeckTable> tables = fill.tables().stream().map(this::paidTable).toList();
		Set<PaidDeckChannel> absent = new LinkedHashSet<>();
		fill.absentChannels().forEach(channel -> absent.add(PaidDeckChannel.valueOf(channel.name())));
		return new PaidDeckFill(tables, absent);
	}

	/**
	 * Maps one paid marker table.
	 *
	 * @param table the service's table
	 * @return the same table in deck terms
	 */
	public PaidDeckTable paidTable(PaidSlideTable table) {
		return new PaidDeckTable(PaidDeckTableMarker.valueOf(table.marker().name()), table.bodyRows(),
				table.totalRow());
	}
}
