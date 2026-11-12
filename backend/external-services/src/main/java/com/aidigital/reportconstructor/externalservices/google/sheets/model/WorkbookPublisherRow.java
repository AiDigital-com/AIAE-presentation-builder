package com.aidigital.reportconstructor.externalservices.google.sheets.model;

/**
 * One hand-entered row of a tactic's "Top Publishers" table on the generated workbook's
 * "Breakdowns" tab, read back verbatim so the deck shows exactly what the user reviewed.
 *
 * <p>Every field is the raw trimmed cell string rather than a parsed number: the values are
 * copied straight onto the slide, so re-formatting them here would make the deck disagree with
 * the sheet the user signed off on.
 *
 * <p>The Google integration's copy of the service type of the same shape; the bridge maps between
 * them and {@code WorkbookModelMapperTest} pins the components.
 *
 * @param name          the publisher name, from the {@code Publisher} column
 * @param impressions   the delivered impressions, from the {@code Impressions} column
 * @param shareOfVoice  the share of voice, from the {@code Share of voice} column
 */
public record WorkbookPublisherRow(String name, String impressions, String shareOfVoice) {
}
