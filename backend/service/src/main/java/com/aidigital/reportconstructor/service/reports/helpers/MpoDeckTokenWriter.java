package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.MpoNarrative;
import com.aidigital.reportconstructor.service.reports.dto.TacticPacing;
import com.aidigital.reportconstructor.service.reports.dto.TacticPacingInput;
import com.aidigital.reportconstructor.service.reports.dto.TemplateVariant;
import com.aidigital.reportconstructor.service.reports.model.PaidChannelData;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Writes the deck sections only some designs carry — the MPO deck's paid-media figures, paid and MPO narrative, paid
 * tables and charts — and the EOM channel slides' pacing narrative. Each step first asks the slides provider whether
 * the deck being built carries those slots.
 */
@Component
@RequiredArgsConstructor
public class MpoDeckTokenWriter {

	/** Report type whose deck ships without the frequency slide (and therefore without its narrative). */
	private static final String EOM_REPORT_TYPE = "EOM";

	/** Paid-channel token (after the channel prefix) holding the period the paid figures cover. */
	private static final String PAID_PERIOD_LABEL_KEY = "period_label";

	private final SlidesProvider slides;
	private final PaidTokenHelper paidTokens;
	private final PaidNarrativeAssembler paidNarrative;
	private final ReportGenerationChartHelper chartHelper;
	private final PaidSlideTableAssembler paidTables;
	private final PaidSlideChartAssembler paidCharts;
	private final MpoNarrativeAssembler mpoNarrative;
	private final PacingNarrativeAssembler pacingNarrative;
	private final ClaudeCallDispatcher dispatcher;

	/**
	 * Generates and writes the EOM channel slides' pacing narrative: one Claude call per tactic, dispatched
	 * together on the shared executor so they run in parallel, then written onto the placeholder map.
	 *
	 * <p>Runs last among the text passes and reads the map rather than the campaign data, so every takeaway
	 * is written against the very figures the slide will print — on the two-step flow, the ones the user
	 * reviewed in the workbook. End-of-month only: no EOC slide carries these tokens, and the calls would be
	 * paid for and thrown away.
	 *
	 * <p>A dry run, or a tactic whose call comes back unusable, leaves that tactic's four tokens dashed
	 * rather than raw.
	 *
	 * @param claude      the run's report-type-specific Claude client
	 * @param flat        the resolved placeholder map, mutated in place
	 * @param tacticCount number of real tactics in the campaign
	 * @param reportType      report template code ({@code "EOC"}/{@code "EOM"}), may be {@code null}
	 * @param templateVariant requested deck design; a deck cloned from the MPO template gets the copy written
	 *                        to its channel slide's shorter slots
	 * @param live            whether this run calls Claude at all
	 * @param usageScope      the usage scope each call is billed to
	 * @param brief           free-text campaign brief passed through to every call
	 */
	public void fillPacingNarrative(
			ClaudeClient claude, Map<String, String> flat, int tacticCount, String reportType,
			TemplateVariant templateVariant, boolean live, ClaudeUsageScope usageScope, String brief) {
		if (!EOM_REPORT_TYPE.equals(reportType)) {
			return;
		}
		List<TacticPacing> narratives = List.of();
		if (live) {
			boolean compact = slides.carriesMpoNarrative(reportType, templateVariant);
			List<TacticPacingInput> inputs = pacingNarrative.toInputs(flat, tacticCount).stream()
					.map(in -> new TacticPacingInput(
							in.tacticNum(), in.tacticName(), in.kpiType(), in.metrics(), compact))
					.toList();
			narratives = dispatcher.joinPacing(dispatcher.dispatchPacing(claude, inputs, usageScope, brief));
		}
		pacingNarrative.write(flat, tacticCount, narratives);
	}

	/**
	 * Writes the MPO deck's Paid Search / Paid Social figures from the paid-media exports the request carries.
	 * Asked of the slides provider first, like the MPO narrative: a standard deck, or an MPO request that fell back
	 * to the standard template, has no paid slides, so its exports are not even parsed.
	 *
	 * @param flat    the resolved placeholder map, mutated in place
	 * @param payload the generation request, whose report type, design, window and exports decide what is written
	 * @return the parsed paid channels whose tokens were written; empty for a standard deck or without exports
	 */
	public List<PaidChannelData> fillPaidTokens(Map<String, String> flat, GeneratePayload payload) {
		if (!slides.carriesMpoNarrative(payload.reportType(), payload.templateVariant())) {
			return List.of();
		}
		return paidTokens.fillPaidTokens(flat, payload);
	}

	/**
	 * Generates and writes the Claude copy of each paid channel's slides (verdict, takeaways, tiles, highlights,
	 * recommendations): one call per channel the request carries an export for. A dry run, or a call that comes
	 * back unusable, dashes the channel's copy tokens rather than leaving them raw.
	 *
	 * @param claude   the run's report-type-specific Claude client
	 * @param flat     the resolved placeholder map, mutated in place
	 * @param channels the parsed paid channels, as returned by {@link #fillPaidTokens}
	 * @param payload  the generation request, whose report type decides EOC or EOM copy
	 * @param live     whether this run calls Claude at all
	 * @param brief    free-text campaign brief passed through to the call
	 */
	public void fillPaidNarrative(
			ClaudeClient claude, Map<String, String> flat, List<PaidChannelData> channels, GeneratePayload payload,
			boolean live, String brief) {
		boolean eom = EOM_REPORT_TYPE.equalsIgnoreCase(payload.reportType());
		for (PaidChannelData channel : channels) {
			String period = flat.get("{{" + channel.kind().tokenPrefix() + PAID_PERIOD_LABEL_KEY + "}}");
			Map<String, String> copy = live
					? claude.paidNarrative(paidNarrative.toInput(channel, eom, period), brief)
					: null;
			paidNarrative.write(flat, channel.kind(), eom, copy);
		}
	}

	/**
	 * Writes everything only the MPO deck carries, in order: the paid-media figures first, then the MPO narrative.
	 * Both steps are no-ops for a standard deck.
	 *
	 * @param claude      the run's report-type-specific Claude client
	 * @param flat        the resolved placeholder map, mutated in place
	 * @param tacticCount number of real tactics in the campaign
	 * @param payload     the generation request
	 * @param live        whether this run calls Claude at all
	 * @param brief       free-text campaign brief passed through to the narrative call
	 * @return the parsed paid channels whose tokens were written, for the deck's paid tables; empty for a standard
	 *         deck or without exports
	 */
	public List<PaidChannelData> fillMpoDeckTokens(
			ClaudeClient claude, Map<String, String> flat, int tacticCount, GeneratePayload payload, boolean live,
			String brief) {
		List<PaidChannelData> channels = fillPaidTokens(flat, payload);
		fillPaidNarrative(claude, flat, channels, payload, live, brief);
		fillMpoNarrative(claude, flat, tacticCount, payload, live, brief);
		return channels;
	}

	/**
	 * Finishes the MPO deck's Paid Search / Paid Social section once the deck exists: writes the marker tables from
	 * the parsed exports and deletes the slides of a channel without one. Asked of the slides provider first, so a
	 * standard deck, which has no paid section, is left alone.
	 *
	 * @param slideUrl        the deck created from the template
	 * @param channels        the parsed paid channels, as returned by {@link #fillMpoDeckTokens}
	 * @param payload         the generation request
	 * @param userGoogleToken the caller's Google OAuth token, or null to use the service account
	 */
	public void finishPaidSlides(
			String slideUrl, List<PaidChannelData> channels, GeneratePayload payload, String userGoogleToken) {
		if (!slides.carriesMpoNarrative(payload.reportType(), payload.templateVariant())) {
			return;
		}
		boolean eom = EOM_REPORT_TYPE.equalsIgnoreCase(payload.reportType());
		chartHelper.fillPaidSlides(slideUrl, paidTables.assemble(channels, eom), userGoogleToken);
	}

	/**
	 * Links the MPO deck's Paid Search / Paid Social charts in place of the template's sample chart images. Runs after
	 * {@link #finishPaidSlides}, so the slides of a channel without an export are already gone. Asked of the slides
	 * provider first, so a standard deck, which has no paid section, is left alone.
	 *
	 * @param slideUrl         the deck created from the template
	 * @param channels         the parsed paid channels, as returned by {@link #fillMpoDeckTokens}
	 * @param payload          the generation request
	 * @param flatReplacements resolved placeholder values, source of the campaign title for the chart files
	 * @param userGoogleToken  the caller's Google OAuth token, or null to use the service account
	 * @return the chart warnings, empty when every chart drew cleanly or the deck has no paid section
	 */
	public List<String> buildPaidCharts(
			String slideUrl, List<PaidChannelData> channels, GeneratePayload payload,
			Map<String, String> flatReplacements, String userGoogleToken) {
		if (channels == null || channels.isEmpty()
				|| !slides.carriesMpoNarrative(payload.reportType(), payload.templateVariant())) {
			return List.of();
		}
		return chartHelper.buildPaidCharts(slideUrl, paidCharts.assemble(channels), flatReplacements, userGoogleToken);
	}

	/**
	 * Generates and writes the copy only the MPO deck design carries: the HHI tile, the two proposal cards, the
	 * four-stage funnel slide and the recommendations headline. One call per report.
	 *
	 * <p>Asked of the slides provider first, so the call is only paid for when the deck is really cloned from an
	 * MPO template — an MPO request that fell back to the standard template has no slot for this copy. A dry
	 * run, or a call that comes back unusable, dashes the tokens rather than leaving them raw.
	 *
	 * @param claude      the run's report-type-specific Claude client
	 * @param flat        the resolved placeholder map, mutated in place
	 * @param tacticCount number of real tactics in the campaign
	 * @param payload     the generation request, whose report type and design decide whether this runs
	 * @param live        whether this run calls Claude at all
	 * @param brief       free-text campaign brief passed through to the call
	 */
	public void fillMpoNarrative(
			ClaudeClient claude, Map<String, String> flat, int tacticCount, GeneratePayload payload, boolean live,
			String brief) {
		if (!slides.carriesMpoNarrative(payload.reportType(), payload.templateVariant())) {
			return;
		}
		MpoNarrative narrative = live ? claude.mpoNarrative(mpoNarrative.toInput(flat, tacticCount), brief) : null;
		mpoNarrative.write(flat, narrative);
	}
}
