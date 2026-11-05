package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.PaidCopyField;
import com.aidigital.reportconstructor.service.reports.dto.PaidNarrativeInput;
import com.aidigital.reportconstructor.service.reports.engine.PaidValueFormatter;
import com.aidigital.reportconstructor.service.reports.enums.PaidChannelKind;
import com.aidigital.reportconstructor.service.reports.enums.PaidCopySlot;
import com.aidigital.reportconstructor.service.reports.enums.PaidExportTab;
import com.aidigital.reportconstructor.service.reports.helpers.PaidNarrativeAssembler;
import com.aidigital.reportconstructor.service.reports.model.PaidAd;
import com.aidigital.reportconstructor.service.reports.model.PaidChannelData;
import com.aidigital.reportconstructor.service.reports.model.PaidMetrics;
import com.aidigital.reportconstructor.service.reports.model.PaidMonth;
import com.aidigital.reportconstructor.service.reports.model.PaidRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.aidigital.reportconstructor.service.reports.engine.PaidValueFormatter.DASH;

/**
 * Default {@link PaidNarrativeAssembler}: the prompt sees the export's figures in the same formats the slides print
 * them, so the copy quotes exactly what sits beside it.
 */
@Component
@RequiredArgsConstructor
public class PaidNarrativeAssemblerImpl implements PaidNarrativeAssembler {

	/** Rows of a breakdown sent to the prompt: the slides' largest table (Group Breakdown) shows 15. */
	static final int MAX_ROWS = 15;
	/** Ads sent to the prompt: the Top Performing Ads slide shows 3 cards. */
	static final int MAX_ADS = 3;
	static final String TITLE_SUFFIX = "_title";
	static final String TITLE_GUIDANCE = "heading of entry ";
	static final String TEXT_GUIDANCE = "text of entry ";
	static final String NUMBER_SEPARATOR = "_";

	private final PaidValueFormatter fmt;

	@Override
	public List<PaidCopyField> fields(PaidChannelKind kind, boolean eom) {
		List<PaidCopyField> fields = new ArrayList<>();
		for (PaidCopySlot slot : PaidCopySlot.forChannel(kind, eom)) {
			if (!slot.numbered()) {
				fields.add(new PaidCopyField(slot.key(), slot.limit(), slot.guidance()));
				continue;
			}
			for (int n = 1; n <= slot.count(); n++) {
				String key = slot.key() + NUMBER_SEPARATOR + n;
				if (slot.titled()) {
					fields.add(new PaidCopyField(key + TITLE_SUFFIX, slot.titleLimit(),
							TITLE_GUIDANCE + n + " — " + slot.guidance()));
				}
				fields.add(new PaidCopyField(key, slot.limit(),
						(slot.titled() ? TEXT_GUIDANCE + n + " — " : "entry " + n + " — ") + slot.guidance()));
			}
		}
		return fields;
	}

	@Override
	public PaidNarrativeInput toInput(PaidChannelData data, boolean eom, String periodLabel) {
		List<String> facts = new ArrayList<>();
		facts.add(metricsLine("TOTAL", data.total()));
		if (eom && data.previousMonth() != null) {
			facts.add(metricsLine("PREVIOUS MONTH TOTAL", data.previousMonth()));
		}
		for (PaidMonth m : data.months()) {
			facts.add(metricsLine("MONTH " + fmt.month(m.month()), m.metrics()));
		}
		addRows(facts, "CAMPAIGN TYPE", data.campaigns());
		addRows(facts, "AD GROUP", data.groups());
		addRows(facts, "KEYWORD", data.keywords());
		addRows(facts, "PMAX PLACEMENT", data.pmaxPlacements());
		addRows(facts, "AUDIENCE", data.audiences());
		addRows(facts, "PLACEMENT", data.placements());
		addRows(facts, "CREATIVE", data.creatives());
		addRows(facts, "AGE", data.ages());
		addRows(facts, "GENDER", data.genders());
		data.ads().stream().limit(MAX_ADS).forEach(ad -> facts.add(adLine(ad)));
		if (!data.missingTabs().isEmpty()) {
			facts.add("MISSING FROM THE EXPORT (no data, do not write about it): " + data.missingTabs().stream()
					.map(PaidExportTab::tabName).collect(Collectors.joining(", ")));
		}
		return new PaidNarrativeInput(data.kind().displayName(), periodLabel, facts, fields(data.kind(), eom));
	}

	@Override
	public void write(Map<String, String> flat, PaidChannelKind kind, boolean eom, Map<String, String> copy) {
		for (PaidCopyField field : fields(kind, eom)) {
			String text = copy == null ? null : copy.get(field.key());
			flat.put("{{" + kind.tokenPrefix() + field.key() + "}}", text == null || text.isBlank() ? DASH : text);
		}
	}

	/**
	 * Adds one line per breakdown row, up to {@link #MAX_ROWS}.
	 *
	 * @param facts the lines being built
	 * @param label the breakdown's label, e.g. {@code KEYWORD}
	 * @param rows  the breakdown's rows, ranked
	 */
	void addRows(List<String> facts, String label, List<PaidRow> rows) {
		rows.stream().limit(MAX_ROWS).forEach(r -> facts.add(metricsLine(label + " " + rowName(r), r.metrics())));
	}

	/**
	 * Names a breakdown row with its parent (campaign or ad set) when it has one.
	 *
	 * @param row the row
	 * @return e.g. {@code "Brand Exact (Brand)"}
	 */
	String rowName(PaidRow row) {
		return row.parent() == null || row.parent().isBlank() ? row.name() : row.name() + " (" + row.parent() + ")";
	}

	/**
	 * Renders one set of figures as a prompt line.
	 *
	 * @param label what the figures belong to
	 * @param m     the figures
	 * @return e.g. {@code "TOTAL: impressions 104K, clicks 1.2K, CTR 1.1%, …"}
	 */
	String metricsLine(String label, PaidMetrics m) {
		return label + ": impressions " + fmt.compact(m.impressions())
				+ ", clicks " + fmt.compact(m.clicks())
				+ ", CTR " + fmt.pct1(m.ctr())
				+ ", CPC " + fmt.money(m.cpc())
				+ ", conversions " + fmt.count(m.conversions())
				+ ", CPA " + fmt.money(m.cpa())
				+ ", CR " + fmt.pct1(m.cr())
				+ ", spend " + fmt.moneyCompact(m.cost());
	}

	/**
	 * Renders one Search ad as a prompt line.
	 *
	 * @param ad the ad
	 * @return the ad's group, headlines and figures
	 */
	String adLine(PaidAd ad) {
		return metricsLine("AD in " + ad.campaign() + " / " + ad.group() + " — headlines \""
				+ String.join(" | ", ad.headlines()) + "\"", ad.metrics());
	}
}
