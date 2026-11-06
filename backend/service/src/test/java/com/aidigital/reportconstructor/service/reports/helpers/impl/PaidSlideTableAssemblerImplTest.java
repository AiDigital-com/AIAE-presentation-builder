package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.engine.PaidValueFormatter;
import com.aidigital.reportconstructor.service.reports.enums.PaidChannelKind;
import com.aidigital.reportconstructor.service.reports.enums.PaidTableMarker;
import com.aidigital.reportconstructor.service.reports.model.PaidChannelData;
import com.aidigital.reportconstructor.service.reports.model.PaidMetrics;
import com.aidigital.reportconstructor.service.reports.model.PaidRow;
import com.aidigital.reportconstructor.service.reports.model.PaidSlideTable;
import com.aidigital.reportconstructor.service.reports.model.PaidSlidesFill;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PaidSlideTableAssemblerImplTest {

	private final PaidSlideTableAssemblerImpl assembler = new PaidSlideTableAssemblerImpl(new PaidValueFormatter());

	@Test
	void shouldBuildTheSearchTablesAndMarkSocialAbsentTest() {
		// Given: a Search export with two campaigns, one group, one keyword and two PMax placements
		PaidMetrics brand = new PaidMetrics(310_000, 12_400, 19_840, 1_042);
		PaidMetrics nonBrand = new PaidMetrics(820_000, 19_680, 56_088, 984);
		PaidChannelData search = new PaidChannelData(PaidChannelKind.SEARCH, brand.plus(nonBrand), null, List.of(),
				List.of(new PaidRow("Brand", "", brand), new PaidRow("NonBrand", "", nonBrand)), List.of(),
				List.of(new PaidRow("Brand Exact", "Brand", brand)),
				List.of(new PaidRow("workflow automation", "Brand", brand)),
				List.of(new PaidRow("Search", "", new PaidMetrics(100, 10, 300, 2)),
						new PaidRow("Display", "", new PaidMetrics(100, 0, 100, 0))),
				List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

		// When:
		PaidSlidesFill fill = assembler.assemble(List.of(search), false);

		// Then: no EOM metric table on an EOC deck; Social has no export
		Map<PaidTableMarker, PaidSlideTable> tables = fill.tables().stream()
				.collect(Collectors.toMap(PaidSlideTable::marker, Function.identity()));
		assertThat(fill.absentChannels()).containsExactly(PaidChannelKind.SOCIAL);
		assertThat(tables).containsOnlyKeys(PaidTableMarker.SEARCH_CAMPAIGNS, PaidTableMarker.SEARCH_GROUPS,
				PaidTableMarker.SEARCH_KEYWORDS, PaidTableMarker.SEARCH_PMAX);
		PaidSlideTable campaigns = tables.get(PaidTableMarker.SEARCH_CAMPAIGNS);
		assertThat(campaigns.bodyRows().get(0))
				.containsExactly("BRAND", "310,000", "12,400", "4.0%", "$1.60", "1,042", "$19.04", "$19,840");
		assertThat(campaigns.totalRow())
				.containsExactly("TOTAL", "1,130,000", "32,080", "2.8%", "$2.37", "2,026", "$37.48", "$75,928");
		assertThat(tables.get(PaidTableMarker.SEARCH_GROUPS).bodyRows().get(0).get(0)).isEqualTo("Brand Exact");
		assertThat(tables.get(PaidTableMarker.SEARCH_GROUPS).hasTotalRow()).isFalse();
		assertThat(tables.get(PaidTableMarker.SEARCH_KEYWORDS).bodyRows().get(0))
				.startsWith("1", "workflow automation");
		PaidSlideTable pmax = tables.get(PaidTableMarker.SEARCH_PMAX);
		assertThat(pmax.bodyRows().get(0)).endsWith("$300", "75.0%");
		assertThat(pmax.bodyRows().get(1)).containsExactly(
				"DISPLAY", "100", "0", "0.0%", "—", "0", "—", "$100", "25.0%");
		assertThat(pmax.totalRow()).containsExactly(
				"TOTAL", "200", "10", "5.0%", "$40.00", "2", "$200.00", "$400", "100%");
	}

	@Test
	void shouldBuildTheSocialEomTablesWithDashedTotalsForMissingTabsTest() {
		// Given: an EOM Social export with one creative and neither audience nor placement tab
		PaidMetrics reel = new PaidMetrics(72_000, 3_744, 9_548, 327);
		PaidChannelData social = new PaidChannelData(PaidChannelKind.SOCIAL, reel, null, List.of(), List.of(),
				List.of(new PaidRow("Pool Reel", "", reel)), List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(), List.of());

		// When:
		PaidSlidesFill fill = assembler.assemble(List.of(social), true);

		// Then:
		Map<PaidTableMarker, PaidSlideTable> tables = fill.tables().stream()
				.collect(Collectors.toMap(PaidSlideTable::marker, Function.identity()));
		assertThat(fill.absentChannels()).containsExactly(PaidChannelKind.SEARCH);
		assertThat(tables.get(PaidTableMarker.SOCIAL_EOM_METRICS).bodyRows()).containsExactly(
				List.of("Clicks", "—", "3,744", "—", "—"),
				List.of("CTR", "—", "5.2%", "—", "—"),
				List.of("CPC", "—", "$2.55", "—", "—"));
		assertThat(tables.get(PaidTableMarker.SOCIAL_CREATIVES).bodyRows().get(0))
				.containsExactly("POOL REEL", "72,000", "3,744", "5.2%", "$2.55", "$9,548", "327", "$29.20");
		assertThat(tables.get(PaidTableMarker.SOCIAL_AUDIENCES).bodyRows()).isEmpty();
		assertThat(tables.get(PaidTableMarker.SOCIAL_AUDIENCES).totalRow())
				.containsExactly("TOTAL", "—", "—", "—", "—", "—", "—", "—", "—");
		assertThat(tables.get(PaidTableMarker.SOCIAL_PLACEMENTS).totalRow()).hasSize(8);
	}

	@Test
	void shouldMarkBothChannelsAbsentWithoutExportsTest() {
		// Given: no parsed export

		// When:
		PaidSlidesFill fill = assembler.assemble(List.of(), false);

		// Then:
		assertThat(fill.tables()).isEmpty();
		assertThat(fill.absentChannels()).containsExactlyInAnyOrder(PaidChannelKind.SEARCH, PaidChannelKind.SOCIAL);
	}
}
