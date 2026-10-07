package com.aidigital.reportconstructor.service.reports.dto;

/**
 * The five breakdown sections' tables and per-tactic Claude inputs, read together from the reviewed
 * workbook.
 *
 * <p>They travel as one value because they are read as one: the five reads are pure Google-Sheet
 * calls with no Claude work between them, so they run concurrently and are joined before anything
 * downstream can use any of them. Each read catches its own failures, so a section that fails yields
 * an empty bundle entry rather than aborting the other four.
 *
 * @param publisher the Top Publishers section
 * @param creative  the Creative section
 * @param geo       the Geo section
 * @param audience  the Audience section
 * @param device    the Device section
 */
public record BreakdownSectionBundle(
		BreakdownSectionInputs<PublisherObservationInput> publisher,
		BreakdownSectionInputs<CreativeTakeawayInput> creative,
		BreakdownSectionInputs<GeoInsightInput> geo,
		BreakdownSectionInputs<AudienceInsightInput> audience,
		BreakdownSectionInputs<DeviceInsightInput> device
) {
}
