package com.aidigital.reportconstructor.externalservices.google.sheets.model;

/**
 * One hand-entered row of a tactic's "Creative analysis" table on the generated workbook's
 * "Breakdowns" tab, read back verbatim so the deck shows exactly what the user reviewed.
 *
 * <p>Every field is the raw trimmed cell string rather than a parsed number, for the same reason
 * {@link WorkbookPublisherRow}'s are: the values are copied straight onto the slide, so re-formatting them
 * here would make the deck disagree with the sheet the user signed off on.
 *
 * <p>The Google integration's copy of the service type of the same shape; the bridge maps between
 * them and {@code WorkbookModelMapperTest} pins the components.
 *
 * @param name        the creative name, from the {@code Creative} column
 * @param impressions the delivered impressions, from the {@code Impressions} column
 * @param ctr         the click-through rate, from the {@code CTR} column
 * @param vcr         the completion rate, from the {@code VCR} column
 * @param spend       the spend, from the {@code Spend} column
 */
public record WorkbookCreativeRow(String name, String impressions, String ctr, String vcr, String spend) {
}
