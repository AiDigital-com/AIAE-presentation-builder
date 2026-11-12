package com.aidigital.reportconstructor.externalservices.google.charts.model;

import java.util.List;

/**
 * One per-tactic breakdown chart to render: which chart series and tactic it belongs to, and the slices
 * that feed it.
 *
 * <p>The target slide and its embedded chart are not named here: the real provider derives the duplicated
 * breakdown slide's deterministic object id from the series' section + {@code tacticNum} (the same scheme
 * {@code addBreakdownSlides} used to create it), then picks the chart on that slide by the source workbook
 * it is linked to. The source-template spreadsheet id and the chart id within it are provider
 * configuration keyed by {@code seriesCode}, so they stay out of the service layer.
 *
 * <p>The Google integration's copy of the service type of the same shape; the bridge maps between
 * them and {@code ChartModelMapperTest} pins the values.
 *
 * @param seriesCode the chart series' code (see {@code DeckBreakdownSeries}) — a section with two charts
 *                   contributes two jobs per tactic; an unknown code is ignored by the provider
 * @param tacticNum  the 1-based tactic number whose breakdown slide this chart lands on
 * @param slices     the chart's category slices, in sheet order; a job with no positive slice is skipped by
 *                   the caller so no empty chart is drawn
 */
public record BreakdownChart(String seriesCode, int tacticNum, List<ChartSlice> slices) {
}
