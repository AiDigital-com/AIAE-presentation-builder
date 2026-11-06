package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.engine.PaidValueFormatter;
import com.aidigital.reportconstructor.service.reports.enums.PaidChartMarker;
import com.aidigital.reportconstructor.service.reports.helpers.PaidSlideChartAssembler;
import com.aidigital.reportconstructor.service.reports.model.PaidChannelData;
import com.aidigital.reportconstructor.service.reports.model.PaidMonth;
import com.aidigital.reportconstructor.service.reports.model.PaidRow;
import com.aidigital.reportconstructor.service.reports.model.PaidSlideChart;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Default {@link PaidSlideChartAssembler}. The monthly charts plot the FULL-FLIGHT months inside the report window,
 * labelled like the sample ({@code "Oct"}), or with the year ({@code "Oct 26"}) when the months span more than one
 * year. The donuts split conversions in the order the legend tokens list their rows, so slice colour and legend
 * entry match; rows past the legend are summed into one "Other" slice, so the slice shares stay the shares of the
 * whole.
 */
@Component
@RequiredArgsConstructor
public class PaidSlideChartAssemblerImpl implements PaidSlideChartAssembler {

	/** Label of the slice that sums the rows past a donut's legend. */
	private static final String OTHER_LABEL = "Other";
	/** Month label with the year, used when the plotted months cross a year boundary. */
	private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yy", Locale.US);
	/** Turns a CTR in percent ({@code 4.6}) into the fraction the chart's percent format expects. */
	private static final double PERCENT = 100d;

	private final PaidValueFormatter fmt;

	@Override
	public List<PaidSlideChart> assemble(List<PaidChannelData> channels) {
		List<PaidSlideChart> charts = new ArrayList<>();
		for (PaidChannelData d : channels) {
			for (PaidChartMarker marker : PaidChartMarker.values()) {
				if (marker.channel() == d.kind()) {
					charts.add(chart(marker, d));
				}
			}
		}
		return charts;
	}

	/**
	 * @param marker the chart to build
	 * @param d      the channel's parsed export
	 * @return the chart's data
	 */
	private PaidSlideChart chart(PaidChartMarker marker, PaidChannelData d) {
		return switch (marker) {
			case SEARCH_CLICKS_CTR, SOCIAL_CLICKS_CTR -> clicksCtr(marker, d.months());
			case SEARCH_SPEND, SOCIAL_SPEND -> spend(marker, d.months());
			case SEARCH_CONVERSION_DONUT -> donut(marker, d.campaigns());
			case SOCIAL_AUDIENCE_DONUT -> donut(marker, d.audiences());
			case SOCIAL_PLACEMENT_DONUT -> donut(marker, d.placements());
		};
	}

	/**
	 * @param marker the clicks &amp; CTR chart of a channel
	 * @param months the delivery per month inside the window
	 * @return monthly clicks as the columns and monthly CTR (a fraction) as the line
	 */
	private PaidSlideChart clicksCtr(PaidChartMarker marker, List<PaidMonth> months) {
		List<Double> clicks = new ArrayList<>();
		List<Double> ctr = new ArrayList<>();
		for (PaidMonth m : months) {
			clicks.add(m.metrics().clicks());
			Double rate = m.metrics().ctr();
			ctr.add(rate == null ? 0d : rate / PERCENT);
		}
		return new PaidSlideChart(marker, monthLabels(months), clicks, ctr, null);
	}

	/**
	 * @param marker the spend chart of a channel
	 * @param months the delivery per month inside the window
	 * @return monthly spend as the columns
	 */
	private PaidSlideChart spend(PaidChartMarker marker, List<PaidMonth> months) {
		List<Double> cost = months.stream().map(m -> m.metrics().cost()).toList();
		return new PaidSlideChart(marker, monthLabels(months), cost, List.of(), null);
	}

	/**
	 * @param marker a donut chart
	 * @param rows   the rows its legend lists, in legend order
	 * @return conversions per legend row, then one "Other" slice for any rows past the legend
	 */
	PaidSlideChart donut(PaidChartMarker marker, List<PaidRow> rows) {
		List<String> labels = new ArrayList<>();
		List<Double> values = new ArrayList<>();
		double other = 0;
		double total = 0;
		for (int i = 0; i < rows.size(); i++) {
			double conv = rows.get(i).metrics().conversions();
			total += conv;
			if (i < marker.legendSlots()) {
				labels.add(rows.get(i).name());
				values.add(conv);
			} else {
				other += conv;
			}
		}
		if (other > 0) {
			labels.add(OTHER_LABEL);
			values.add(other);
		}
		return new PaidSlideChart(marker, labels, values, List.of(), fmt.count(total));
	}

	/**
	 * @param months the plotted months, in date order
	 * @return {@code "Oct"}-style labels, or {@code "Oct 26"} when the months span more than one year
	 */
	List<String> monthLabels(List<PaidMonth> months) {
		boolean multiYear = months.stream().map(m -> m.month().getYear()).distinct().count() > 1;
		return months.stream()
				.map(m -> multiYear
						? m.month().format(MONTH_YEAR)
						: m.month().getMonth().getDisplayName(TextStyle.SHORT, Locale.US))
				.toList();
	}
}
