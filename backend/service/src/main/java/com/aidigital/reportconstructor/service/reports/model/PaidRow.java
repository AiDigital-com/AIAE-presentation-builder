package com.aidigital.reportconstructor.service.reports.model;

/**
 * One row of a paid-media breakdown: a campaign, ad group, keyword, placement, audience, creative, age band or
 * gender, with its delivery.
 *
 * @param name    the row's label as the export spells it
 * @param parent  the campaign the row belongs to (groups, keywords), or {@code ""} when the breakdown has none
 * @param metrics the row's delivery
 */
public record PaidRow(String name, String parent, PaidMetrics metrics) {
}
