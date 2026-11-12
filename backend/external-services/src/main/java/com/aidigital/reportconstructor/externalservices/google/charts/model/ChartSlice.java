package com.aidigital.reportconstructor.externalservices.google.charts.model;

/**
 * One slice of a per-tactic breakdown chart: a category label and the number plotted for it.
 *
 * <p>Only the one number is carried — the breakdown chart's other column ("share of voice") is a live
 * formula in the linked source spreadsheet, so writing the value alone is enough to make the chart
 * render. What the number means depends on the series it belongs to: delivered impressions for the age
 * and device charts, the affinity index for the audience-segment chart.
 *
 * <p>For a series whose labels the workbook already prints (age buckets, device names) the label is
 * matched against the workbook's category column, tolerating the {@code CTV} &harr; {@code Connected TV}
 * spelling difference; for the audience-segment series the label is written into that column, because a
 * campaign's segment names cannot be pre-printed.
 *
 * <p>The Google integration's copy of the service type of the same shape; the bridge maps between
 * them and {@code ChartModelMapperTest} pins the values.
 *
 * @param label the category label as read from the workbook's "Breakdowns" tab (e.g. {@code "Mobile"},
 *              {@code "25-34"}, or an audience segment name)
 * @param value the number plotted for the category; slices at or below zero are dropped by the caller so
 *              an unparseable or blank cell never zeroes the chart
 */
public record ChartSlice(String label, double value) {
}
