package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.PaidCopyField;
import com.aidigital.reportconstructor.service.reports.dto.PaidNarrativeInput;
import com.aidigital.reportconstructor.service.reports.enums.PaidChannelKind;
import com.aidigital.reportconstructor.service.reports.model.PaidChannelData;

import java.util.List;
import java.util.Map;

/**
 * Builds the paid-media narrative call's input from a parsed export, and writes the call's reply into the
 * channel's copy tokens ({@code {{ps_verdict}}}, {@code {{pso_pl_take_1_title}}}, …).
 */
public interface PaidNarrativeAssembler {

	/**
	 * The copy fields one channel's slides carry in one report type, flattened to one entry per token.
	 *
	 * @param kind the paid channel
	 * @param eom  whether the deck is an EOM report
	 * @return the fields in slide order, keyed by token name without the channel prefix
	 */
	List<PaidCopyField> fields(PaidChannelKind kind, boolean eom);

	/**
	 * Renders a parsed export as the narrative call's input: totals, months and every breakdown the slides show.
	 *
	 * @param data        the parsed export
	 * @param eom         whether the deck is an EOM report
	 * @param periodLabel the period the figures cover, as the deck prints it
	 * @return the call's input
	 */
	PaidNarrativeInput toInput(PaidChannelData data, boolean eom, String periodLabel);

	/**
	 * Writes the reply into the channel's copy tokens. Every field is written: a field the reply left out, or a
	 * whole reply that is {@code null} (dry run or failed call), becomes an em-dash, so no raw token ships.
	 *
	 * @param flat the resolved placeholder map, mutated in place
	 * @param kind the paid channel
	 * @param eom  whether the deck is an EOM report
	 * @param copy the reply (field key → text), or {@code null}
	 */
	void write(Map<String, String> flat, PaidChannelKind kind, boolean eom, Map<String, String> copy);
}
