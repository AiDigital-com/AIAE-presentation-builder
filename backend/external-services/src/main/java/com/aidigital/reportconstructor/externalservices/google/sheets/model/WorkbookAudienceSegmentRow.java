package com.aidigital.reportconstructor.externalservices.google.sheets.model;

/**
 * One hand-entered row of a tactic's "TOP AUDIENCE SEGMENTS" sub-table on the generated workbook's
 * "Breakdowns" tab, read back verbatim so the deck shows exactly what the user reviewed.
 *
 * <p>The five filled rows land on the slide's renumbered {@code {{aud_N_x}}} (segment name) and
 * {@code {{aud_in_N_x}}} (affinity index) tokens. Both fields are the raw trimmed cell string rather
 * than a parsed number, for the same reason {@link WorkbookGeoRow}'s are: the values are copied straight onto
 * the slide, so re-formatting them here would make the deck disagree with the sheet.
 *
 * <p>The Google integration's copy of the service type of the same shape; the bridge maps between
 * them and {@code WorkbookModelMapperTest} pins the components.
 *
 * @param segment       the audience segment name, from the {@code Segment} column
 * @param affinityIndex the segment's affinity index, from the {@code Affinity index} column
 *                      ({@code 100} = campaign average)
 */
public record WorkbookAudienceSegmentRow(String segment, String affinityIndex) {
}
