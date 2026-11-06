package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.engine.PaidValueFormatter;
import com.aidigital.reportconstructor.service.reports.enums.PaidChannelKind;
import com.aidigital.reportconstructor.service.reports.enums.PaidTableMarker;
import com.aidigital.reportconstructor.service.reports.helpers.PaidSlideTableAssembler;
import com.aidigital.reportconstructor.service.reports.model.PaidChannelData;
import com.aidigital.reportconstructor.service.reports.model.PaidMetrics;
import com.aidigital.reportconstructor.service.reports.model.PaidRow;
import com.aidigital.reportconstructor.service.reports.model.PaidSlideTable;
import com.aidigital.reportconstructor.service.reports.model.PaidSlidesFill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.aidigital.reportconstructor.service.reports.engine.PaidValueFormatter.DASH;

/**
 * Default {@link PaidSlideTableAssembler}. Cells are formatted the way the sample decks print their tables: full
 * counts ({@code 12,400}), one-decimal rates ({@code 4.0%}), cent-precise unit costs ({@code $1.60}) and whole-dollar
 * spend ({@code $19,840}). Row labels are upper-cased where the sample table prints them in caps (campaigns,
 * placements, creatives, audiences); ad groups and keywords keep the export's spelling.
 */
@Component
@RequiredArgsConstructor
public class PaidSlideTableAssemblerImpl implements PaidSlideTableAssembler {

	/** Label of every TOTAL row. */
	private static final String TOTAL_LABEL = "TOTAL";
	/** Spend share printed on a TOTAL row. */
	private static final String FULL_SHARE = "100%";
	/** Row labels of the EOM metric master table, in template order. */
	private static final String CLICKS_LABEL = "Clicks";
	private static final String CTR_LABEL = "CTR";
	private static final String CPC_LABEL = "CPC";
	/** Cells of a standard metric row after the label: impr, clicks, CTR, CPC, conv, CPA, spend. */
	private static final int METRIC_CELLS = 7;

	private final PaidValueFormatter fmt;

	@Override
	public PaidSlidesFill assemble(List<PaidChannelData> channels, boolean eom) {
		List<PaidSlideTable> tables = new ArrayList<>();
		Set<PaidChannelKind> absent = EnumSet.allOf(PaidChannelKind.class);
		for (PaidChannelData d : channels) {
			absent.remove(d.kind());
			tables.addAll(d.kind() == PaidChannelKind.SEARCH ? searchTables(d, eom) : socialTables(d, eom));
		}
		return new PaidSlidesFill(tables, absent);
	}

	/**
	 * @param d   the parsed Google Ads export
	 * @param eom whether the deck is an EOM report
	 * @return the Paid Search tables, in slide order
	 */
	List<PaidSlideTable> searchTables(PaidChannelData d, boolean eom) {
		List<PaidSlideTable> tables = new ArrayList<>();
		if (eom) {
			tables.add(metricTable(PaidTableMarker.SEARCH_EOM_METRICS, d.total()));
		}
		tables.add(new PaidSlideTable(PaidTableMarker.SEARCH_CAMPAIGNS,
				d.campaigns().stream().map(r -> standardRow(upper(r.name()), r.metrics())).toList(),
				totalRow(d.campaigns(), d.total())));
		tables.add(new PaidSlideTable(PaidTableMarker.SEARCH_GROUPS,
				d.groups().stream().map(r -> standardRow(r.name(), r.metrics())).toList(), null));
		List<List<String>> keywords = new ArrayList<>();
		for (int i = 0; i < d.keywords().size(); i++) {
			PaidRow r = d.keywords().get(i);
			List<String> row = new ArrayList<>();
			row.add(String.valueOf(i + 1));
			row.addAll(standardRow(r.name(), r.metrics()));
			keywords.add(row);
		}
		tables.add(new PaidSlideTable(PaidTableMarker.SEARCH_KEYWORDS, keywords, null));
		tables.add(shareTable(PaidTableMarker.SEARCH_PMAX, d.pmaxPlacements()));
		return tables;
	}

	/**
	 * @param d   the parsed Meta export
	 * @param eom whether the deck is an EOM report
	 * @return the Paid Social tables, in slide order
	 */
	List<PaidSlideTable> socialTables(PaidChannelData d, boolean eom) {
		List<PaidSlideTable> tables = new ArrayList<>();
		if (eom) {
			tables.add(metricTable(PaidTableMarker.SOCIAL_EOM_METRICS, d.total()));
		}
		List<List<String>> creatives = d.creatives().stream()
				.map(r -> creativeRow(upper(r.name()), r.metrics())).toList();
		tables.add(new PaidSlideTable(PaidTableMarker.SOCIAL_CREATIVES, creatives,
				d.creatives().isEmpty() ? dashedTotal(METRIC_CELLS) : creativeRow(TOTAL_LABEL, d.total())));
		tables.add(shareTable(PaidTableMarker.SOCIAL_AUDIENCES, d.audiences()));
		tables.add(new PaidSlideTable(PaidTableMarker.SOCIAL_PLACEMENTS,
				d.placements().stream().map(r -> standardRow(upper(r.name()), r.metrics())).toList(),
				totalRow(d.placements(), sum(d.placements()))));
		return tables;
	}

	/**
	 * Builds a breakdown table whose last column is each row's share of the breakdown's spend (PMax placements,
	 * Social audiences), with a TOTAL row summed over every row of the breakdown.
	 *
	 * @param marker the table
	 * @param rows   the breakdown rows, ranked
	 * @return the table
	 */
	PaidSlideTable shareTable(PaidTableMarker marker, List<PaidRow> rows) {
		PaidMetrics total = sum(rows);
		List<List<String>> body = new ArrayList<>();
		for (PaidRow r : rows) {
			List<String> row = standardRow(upper(r.name()), r.metrics());
			row.add(fmt.share1(r.metrics().cost(), total.cost()));
			body.add(row);
		}
		List<String> totalRow;
		if (rows.isEmpty()) {
			totalRow = dashedTotal(METRIC_CELLS + 1);
		} else {
			totalRow = standardRow(TOTAL_LABEL, total);
			totalRow.add(total.cost() > 0 ? FULL_SHARE : DASH);
		}
		return new PaidSlideTable(marker, body, totalRow);
	}

	/**
	 * Builds the EOM metric master table. The CTD and EOC goals come from the plan, which no input carries yet
	 * (open question 2 of the task plan), so the goal and "vs goal" cells are dashed.
	 *
	 * @param marker the table
	 * @param total  the channel's delivery inside the report window
	 * @return the table, rows Clicks, CTR, CPC
	 */
	PaidSlideTable metricTable(PaidTableMarker marker, PaidMetrics total) {
		List<List<String>> rows = List.of(
				List.of(CLICKS_LABEL, DASH, fmt.count(total.clicks()), DASH, DASH),
				List.of(CTR_LABEL, DASH, fmt.pct1(total.ctr()), DASH, DASH),
				List.of(CPC_LABEL, DASH, fmt.money(total.cpc()), DASH, DASH));
		return new PaidSlideTable(marker, rows, null);
	}

	/**
	 * @param rows  the table's body rows
	 * @param total the figures the TOTAL row prints
	 * @return the TOTAL row, or a dashed one when the table has no rows
	 */
	List<String> totalRow(List<PaidRow> rows, PaidMetrics total) {
		return rows.isEmpty() ? dashedTotal(METRIC_CELLS) : standardRow(TOTAL_LABEL, total);
	}

	/**
	 * @param label the row's first cell
	 * @param m     the row's figures
	 * @return label, impr, clicks, CTR, CPC, conv, CPA, spend (mutable, so a share cell can be appended)
	 */
	List<String> standardRow(String label, PaidMetrics m) {
		return new ArrayList<>(List.of(label, fmt.count(m.impressions()), fmt.count(m.clicks()), fmt.pct1(m.ctr()),
				fmt.money(m.cpc()), fmt.count(m.conversions()), fmt.money(m.cpa()), fmt.moneyWhole(m.cost())));
	}

	/**
	 * @param label the row's first cell
	 * @param m     the row's figures
	 * @return label, impr, clicks, CTR, CPC, spend, conv, CPA: the Creative Performance column order
	 */
	List<String> creativeRow(String label, PaidMetrics m) {
		return List.of(label, fmt.count(m.impressions()), fmt.count(m.clicks()), fmt.pct1(m.ctr()),
				fmt.money(m.cpc()), fmt.moneyWhole(m.cost()), fmt.count(m.conversions()), fmt.money(m.cpa()));
	}

	/**
	 * @param cells the number of cells after the label
	 * @return a TOTAL row with every figure dashed, for a table the export has no rows for
	 */
	List<String> dashedTotal(int cells) {
		List<String> row = new ArrayList<>();
		row.add(TOTAL_LABEL);
		row.addAll(Collections.nCopies(cells, DASH));
		return row;
	}

	/**
	 * @param rows breakdown rows
	 * @return their summed delivery
	 */
	PaidMetrics sum(List<PaidRow> rows) {
		PaidMetrics total = PaidMetrics.ZERO;
		for (PaidRow r : rows) {
			total = total.plus(r.metrics());
		}
		return total;
	}

	/**
	 * @param name a row label
	 * @return the label in capitals, as the sample tables print it
	 */
	String upper(String name) {
		return name.toUpperCase(Locale.ROOT);
	}
}
