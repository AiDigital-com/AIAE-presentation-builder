package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.AudienceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownBullets;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSectionBundle;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSectionInputs;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTakeawayInput;
import com.aidigital.reportconstructor.service.reports.dto.DeviceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.GeoInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.PublisherObservationInput;
import com.aidigital.reportconstructor.service.reports.dto.SheetChartData;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageScope;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

/**
 * Reads, gates and writes the per-tactic breakdown sections (publisher, creative, geo, audience, device) of a reviewed
 * workbook, and the pacing series the EOM narrative reasons over.
 */
@Component
@RequiredArgsConstructor
public class BreakdownSectionWorkflow {

	private final PublisherBreakdownHelper publisherBreakdown;
	private final CreativeBreakdownHelper creativeBreakdown;
	private final GeoBreakdownHelper geoBreakdown;
	private final AudienceBreakdownHelper audienceBreakdown;
	private final DeviceBreakdownHelper deviceBreakdown;
	private final ClaudeUsageTracker usageTracker;
	private final AsyncTaskExecutor applicationTaskExecutor;
	private final SlidesProvider slides;
	private final BreakdownThoughtsGate thoughtsGate;
	private final BreakdownSelectionResolver breakdownResolver;
	private final TacticExtractionHelper tacticExtraction;
	private final SheetChartDataReader sheetChartData;

	/**
	 * Merges every breakdown section's data tokens into one ordered, mutable token map.
	 *
	 * <p>Each section only emits tokens for its own tactics, so the merge cannot collide.
	 *
	 * @param sections the breakdown section reads for the run
	 * @return a new mutable map holding the publisher, creative, geo, audience and device data tokens
	 */
	public Map<String, String> mergeSectionDataValues(BreakdownSectionBundle sections) {
		Map<String, String> values = new LinkedHashMap<>();
		values.putAll(sections.publisher().dataValues());
		values.putAll(sections.creative().dataValues());
		values.putAll(sections.geo().dataValues());
		values.putAll(sections.audience().dataValues());
		values.putAll(sections.device().dataValues());
		return values;
	}

	/**
	 * Builds the token map the breakdown copies are filled from: every breakdown token, backed by the deck's
	 * resolved map for any token the breakdown sections do not emit themselves.
	 *
	 * <p>The copies are duplicated after the deck-wide placeholder pass has run, so a tactic token a breakdown
	 * master reuses from the main tactic slide (the MPO audience slide's {@code {{tactic n reach}}} or
	 * {@code {{tactic n KPI}}}) would otherwise only be renumbered and ship raw. A breakdown value still wins
	 * over the deck's value for the same token.
	 *
	 * @param breakdownValues  the breakdown slides' own tokens
	 * @param flatReplacements the deck's resolved token map
	 * @return a new map holding both, breakdown values taking precedence
	 */
	public Map<String, String> withDeckFallback(Map<String, String> breakdownValues, Map<String,
			String> flatReplacements) {
		Map<String, String> merged = new LinkedHashMap<>(flatReplacements);
		merged.putAll(breakdownValues);
		return merged;
	}

	/**
	 * Reads all five breakdown sections from the reviewed workbook at once.
	 *
	 * <p>These are pure Google-Sheet reads with no Claude call, so they run concurrently on the shared
	 * virtual-thread executor. Each helper catches its own failures, so one section failing cannot
	 * abort the others — it comes back empty and the deck ships without that breakdown's copy.
	 *
	 * @param payload         the generation request, carrying the sheet URL and the selections
	 * @param prelim          the seed placeholder map the readers resolve tactic names against
	 * @param usageScope      the Claude usage scope the async reads must stay inside
	 * @param userGoogleToken the caller's Google OAuth token, or null to use the service account
	 * @return all five sections, joined
	 */
	public BreakdownSectionBundle readBreakdownSections(
			GeneratePayload payload, Map<String, String> prelim, ClaudeUsageScope usageScope,
			String userGoogleToken) {
		CompletableFuture<BreakdownSectionInputs<PublisherObservationInput>> pubF = CompletableFuture.supplyAsync(
				usageTracker.inScope(usageScope, () -> publisherBreakdown.readPublisherInputs(
						payload.sheetUrl(), payload.breakdownSelections(), prelim, userGoogleToken)),
				applicationTaskExecutor);
		CompletableFuture<BreakdownSectionInputs<CreativeTakeawayInput>> creF = CompletableFuture.supplyAsync(
				usageTracker.inScope(usageScope, () -> creativeBreakdown.readCreativeInputs(
						payload.sheetUrl(), payload.breakdownSelections(), prelim, userGoogleToken)),
				applicationTaskExecutor);
		CompletableFuture<BreakdownSectionInputs<GeoInsightInput>> geoF = CompletableFuture.supplyAsync(
				usageTracker.inScope(usageScope, () -> geoBreakdown.readGeoInputs(
						payload.sheetUrl(), payload.breakdownSelections(), prelim, userGoogleToken)),
				applicationTaskExecutor);
		CompletableFuture<BreakdownSectionInputs<AudienceInsightInput>> audF = CompletableFuture.supplyAsync(
				usageTracker.inScope(usageScope, () -> audienceBreakdown.readAudienceInputs(
						payload.sheetUrl(), payload.breakdownSelections(), prelim, userGoogleToken)),
				applicationTaskExecutor);
		CompletableFuture<BreakdownSectionInputs<DeviceInsightInput>> devF = CompletableFuture.supplyAsync(
				usageTracker.inScope(usageScope, () -> deviceBreakdown.readDeviceInputs(
						payload.sheetUrl(), payload.breakdownSelections(), prelim, userGoogleToken)),
				applicationTaskExecutor);
		return new BreakdownSectionBundle(
				pubF.join(), creF.join(), geoF.join(), audF.join(), devF.join());
	}

	/**
	 * Writes every breakdown's Claude copy into the breakdown token map.
	 *
	 * @param breakdownValues the accumulating breakdown token map
	 * @param pub             the publisher breakdown's tactics and inputs
	 * @param cre             the creative breakdown's tactics and inputs
	 * @param geo             the geo breakdown's tactics and inputs
	 * @param aud             the audience breakdown's tactics and inputs
	 * @param dev             the device breakdown's tactics and inputs
	 * @param bullets         Claude's per-breakdown replies
	 * @param prelim          the resolved placeholder map, source of the tactic names in warnings
	 * @return every warning the five writers raised, in breakdown order
	 */
	public List<String> writeBreakdownInsights(
			Map<String, String> breakdownValues,
			BreakdownSectionInputs<PublisherObservationInput> pub,
			BreakdownSectionInputs<CreativeTakeawayInput> cre,
			BreakdownSectionInputs<GeoInsightInput> geo,
			BreakdownSectionInputs<AudienceInsightInput> aud,
			BreakdownSectionInputs<DeviceInsightInput> dev,
			BreakdownBullets bullets, Map<String, String> prelim) {
		List<String> jobWarnings = new ArrayList<>();
		jobWarnings.addAll(publisherBreakdown.writePublisherObservations(breakdownValues, pub.tactics(),
				pub.inputs().keySet(), bullets.publisher(), prelim));
		jobWarnings.addAll(creativeBreakdown.writeCreativeTakeaways(breakdownValues, cre.tactics(),
				cre.inputs().keySet(), bullets.creative(), prelim));
		jobWarnings.addAll(geoBreakdown.writeGeoInsights(breakdownValues, geo.tactics(),
				geo.inputs().keySet(), bullets.geo(), prelim));
		jobWarnings.addAll(audienceBreakdown.writeAudienceInsights(breakdownValues, aud.tactics(),
				aud.inputs().keySet(), bullets.audience(), prelim));
		jobWarnings.addAll(deviceBreakdown.writeDeviceInsights(breakdownValues, dev.tactics(),
				dev.inputs().keySet(), bullets.device(), prelim));
		return jobWarnings;
	}

	/**
	 * Reads every tactic's daily and monthly pacing series back out of the reviewed sheet grid.
	 *
	 * <p>Same reader, same grid and same KPI-type derivation the chart step uses later in the run, so the
	 * numbers the narrative reasons over are exactly the ones the pacing charts will plot. It is read here
	 * rather than reused from the chart step because the narrative runs first, and both series come from the
	 * one read the reader already does.
	 *
	 * @param grid        the reviewed workbook's first tab
	 * @param data        parsed campaign data supplying the tactic names the KPI types are derived from
	 * @param tacticCount number of active tactics on the sheet
	 * @return both pacing pivot maps; empty when the grid carries no pacing blocks
	 */
	public SheetChartData readPacing(List<List<String>> grid, CampaignData data, int tacticCount) {
		Map<Integer, String> kpiTypes = new LinkedHashMap<>();
		for (int n = 1; n <= tacticCount; n++) {
			Tactic tactic = data == null || data.tactics() == null ? null : data.tactics().get(n);
			kpiTypes.put(n, tacticExtraction.getTacticKpiSeries(tactic == null ? null : tactic.name()));
		}
		return sheetChartData.read(grid, tacticCount, kpiTypes);
	}

	/**
	 * The tactic numbers the Step-2 conclusions call covers: every tactic on the reviewed sheet, plus any that
	 * only a breakdown section knows about, in ascending order. Every sheet tactic is included so its overview —
	 * and, through the digests, the whole downstream campaign-results narrative — is always produced from the
	 * EOC sheet figures + brief, even when no breakdown was selected.
	 *
	 * @param data the parsed campaign data whose tactic keys are the full tactic set
	 * @param pub  the publisher section inputs
	 * @param cre  the creative section inputs
	 * @param geo  the geo section inputs
	 * @param aud  the audience section inputs
	 * @param dev  the device section inputs
	 * @return every covered tactic number, ascending
	 */
	public List<Integer> conclusionTacticNums(
			CampaignData data,
			BreakdownSectionInputs<PublisherObservationInput> pub,
			BreakdownSectionInputs<CreativeTakeawayInput> cre,
			BreakdownSectionInputs<GeoInsightInput> geo,
			BreakdownSectionInputs<AudienceInsightInput> aud,
			BreakdownSectionInputs<DeviceInsightInput> dev) {
		Set<Integer> tactics = new TreeSet<>();
		if (data != null && data.tactics() != null) {
			tactics.addAll(data.tactics().keySet());
		}
		tactics.addAll(pub.inputs().keySet());
		tactics.addAll(cre.inputs().keySet());
		tactics.addAll(geo.inputs().keySet());
		tactics.addAll(aud.inputs().keySet());
		tactics.addAll(dev.inputs().keySet());
		return List.copyOf(tactics);
	}

	/**
	 * Picks the tactics the Step-3 "thoughts on tactic performance" calls run for: those past the
	 * "&gt; 2 breakdowns" gate, and none at all when the deck being built has no thoughts slide.
	 *
	 * @param payload     the inbound generation payload carrying the breakdown selections and report type
	 * @param tacticCount the active tactic count
	 * @return the qualifying 1-based tactic numbers, empty when the deck carries no thoughts slide
	 */
	public Set<Integer> thoughtsTactics(GeneratePayload payload, int tacticCount) {
		// A template without the thoughts slide (the MPO-style EOC deck) has nowhere to put this copy, so no
		// tactic qualifies and the calls are never paid for.
		if (!slides.carriesTacticThoughts(payload.reportType(), payload.templateVariant())) {
			return Set.of();
		}
		return thoughtsGate.qualifyingTactics(qualifyingSelections(payload, tacticCount));
	}

	/**
	 * Resolves the request's breakdown selections into the clamped enabled-sections map, matching how the
	 * slide-insertion step clamps: only real tactics (1..{@code tacticCount}) with at least one section enabled.
	 * This is the map the "&gt; 2 breakdowns" gate reads, so Step 3 and the thoughts slide qualify the same
	 * tactics.
	 *
	 * @param payload     the inbound generation payload carrying the breakdown selections
	 * @param tacticCount the active tactic count
	 * @return the clamped 1-based tactic number → enabled breakdown sections map
	 */
	public Map<Integer, Set<BreakdownType>> qualifyingSelections(GeneratePayload payload, int tacticCount) {
		Map<Integer, Set<BreakdownType>> enabledByTactic = new LinkedHashMap<>();
		breakdownResolver.resolve(payload.breakdownSelections()).forEach((tacticNum, enabled) -> {
			if (tacticNum != null && tacticNum >= 1 && tacticNum <= tacticCount && !enabled.isEmpty()) {
				enabledByTactic.put(tacticNum, enabled);
			}
		});
		return enabledByTactic;
	}
}
