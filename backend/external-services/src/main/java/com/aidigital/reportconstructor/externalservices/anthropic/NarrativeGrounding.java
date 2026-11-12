package com.aidigital.reportconstructor.externalservices.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps client-facing narrative copy tied to the campaign's own data, in two ways.
 *
 * <p>First, {@link #withRules} adds one shared block of grounding, balance and recommendation rules to every
 * narrative prompt. Before it existed each prompt carried its own partial copy of these rules, and none of them
 * stopped claims about data the prompt never supplied (invalid traffic, creative lengths, a second creative),
 * negative-heavy framing, or drastic and platform-impossible recommendations — the issues account managers
 * reported most.
 *
 * <p>Second, {@link #unsupportedFigures} lists the figures a reply cites that the prompt's data neither
 * contains nor yields as a simple difference, share, percent change or multiple of two of its figures. The
 * comparison allows for rounding at the precision the figure is written in, so {@code "1.83M"} matches
 * {@code 1,831,381} and {@code "97%"} matches {@code 97.40%}. Small counts, years, dates and clock times are
 * not treated as figures.
 */
@Component
@RequiredArgsConstructor
public class NarrativeGrounding {

	/**
	 * The shared rules every narrative prompt carries. Placed before the prompt's cache breakpoint so it is
	 * cached with the rest of the static instructions and read before the data.
	 */
	static final String RULES = "\n\nGROUNDING, BALANCE AND RECOMMENDATION RULES — these override anything above "
			+ "that conflicts with them:\n"
			+ "1. FIGURES: every number you write must appear in the data you were given, or be a simple "
			+ "difference, share, percent change or multiple of two figures there. Never estimate, extrapolate or "
			+ "invent a figure; if the data has no figure for a point, make the point without one.\n"
			+ "2. ONLY WHAT THE DATA CONTAINS: never mention creatives, creative lengths or formats, placements, "
			+ "devices, markets, audiences, invalid traffic (IVT), fraud, brand safety, viewability, conversions "
			+ "or attribution unless the data names them. One creative in the data means no creative comparison. "
			+ "Never claim an optimisation or action that the change log does not record.\n"
			+ "3. SPECIFIC, NOT GENERIC: every sentence names a tactic, audience, market or figure from THIS "
			+ "campaign. A sentence that would fit any campaign report must be rewritten or dropped.\n"
			+ "4. BALANCE: lead with what the campaign achieved. State each shortfall once, plainly, with its "
			+ "figure and in proportion to its size — never let shortfalls dominate a field, and never invent a "
			+ "positive to offset one.\n"
			+ "5. RECOMMENDATION RULES: recommendations are incremental and tied to a figure in the data — "
			+ "re-weight budget between tactics by at most about 20%, adjust bids, frequency caps, targeting or "
			+ "creative rotation. Never recommend pausing, removing or excluding a whole tactic, channel, device "
			+ "or market. Use only levers the platform offers for that channel: no click-based advice for CTV, "
			+ "DOOH or audio, no device targeting for DOOH, no audience-level targeting for DOOH screens.\n"
			+ "6. OUTPUT: return ONLY the JSON object asked for — no preamble, working-out or commentary before or "
			+ "after it. Never refuse and never flag thin, missing or odd input (e.g. a brief that is only a label "
			+ "or file path): write the best copy the campaign figures support, and leave a field empty only when "
			+ "nothing in the data supports it.\n";

	/**
	 * Openings and phrases that mark a reply as a request for more input or a refusal rather than the content
	 * asked for — e.g. a brief digest answering "Please provide the full campaign brief…". Matched
	 * case-insensitively anywhere in the reply.
	 */
	static final Pattern REFUSAL = Pattern.compile("(?i)^\\s*(please (provide|share|send)|could you (please )?"
			+ "(provide|share)|i need (the|more)|i(?: am|'m) (unable|not able)|i (cannot|can't|can not))"
			+ "|once you (share|provide|send)|provide the (full|complete|actual)|does not contain the "
			+ "(facts|information|details)|appears to be (only )?a (file path|label|title)");

	/** Retry note sent when a reply cited figures the data does not support. */
	static final String RETRY_NOTE = "\n\nIMPORTANT: your previous reply cited figures that are not in the data "
			+ "above and cannot be derived from it: %s. Rewrite the reply using ONLY figures present in the data "
			+ "(or simple differences, shares or multiples of them), drop any claim you cannot support with the "
			+ "data, and return the same JSON shape with no prose and no backticks.";

	/**
	 * A figure as written: optional currency, digits with optional thousands separators and decimals, and an
	 * optional unit ({@code %}, {@code pp}, or a k/M/B magnitude). Group 1 is the integer part, group 2 the
	 * decimals, group 3 the unit.
	 */
	static final Pattern FIGURE = Pattern.compile(
			"(?<![\\w.])[$€£]?(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d+))?\\s?(%|pp|[kKmMbB](?![a-zA-Z]))?");

	/** Text right after a figure that marks it as a clock time (e.g. {@code "6 PM"}), not a metric. */
	static final Pattern CLOCK_AFTER = Pattern.compile("^\\s?(?i:am|pm)\\b");

	/** Text right before a figure that marks it as a calendar day (e.g. {@code "Mar 15"}), not a metric. */
	static final Pattern MONTH_BEFORE = Pattern.compile(
			"(?i)\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?\\s$");

	/** Bare whole numbers up to this size are counts ("3 tactics", "top 5"), not figures to verify. */
	static final double SMALL_COUNT = 10;

	/** Lowest and highest bare four-digit numbers read as calendar years, not figures. */
	static final int YEAR_MIN = 1900;
	static final int YEAR_MAX = 2100;

	/** Relative slack allowed on a figure derived from two others, which compounds both figures' rounding. */
	static final double DERIVED_SLACK = 0.005;

	/** Upper bound on distinct input figures scanned for derived matches, keeping the pair scan cheap. */
	static final int MAX_INPUT_FIGURES = 600;

	/** Percentage scale used by shares and percent changes. */
	static final double PERCENT = 100.0;

	/** Separator between the unsupported figures named in a retry note or report-card warning. */
	static final String LIST_SEPARATOR = ", ";

	private final AnthropicProperties properties;

	/**
	 * Adds the shared grounding, balance and recommendation rules to a narrative prompt. When the prompt carries a
	 * cache breakpoint the rules go just before it, alongside the other static instructions; otherwise they are
	 * appended at the end.
	 *
	 * @param prompt the built prompt, possibly {@code null}
	 * @return the prompt with the rules added, or {@code null} for a {@code null} prompt
	 */
	public String withRules(String prompt) {
		if (prompt == null) {
			return null;
		}
		int marker = prompt.indexOf(AnthropicMessagesClient.CACHE_BREAKPOINT);
		if (marker < 0) {
			return prompt + RULES;
		}
		return prompt.substring(0, marker) + RULES + "\n" + prompt.substring(marker);
	}

	/**
	 * Whether a plain-text reply is a refusal or a request for more input instead of the asked-for content.
	 * A digest call given a brief that is only a label answers "Please provide the full campaign brief…"; saved
	 * as the digest, that text was written into the workbook as the RFP and fed to every later call as the brief.
	 *
	 * @param text the reply text, possibly {@code null}
	 * @return {@code true} when the reply asks for input or refuses rather than answering
	 */
	public boolean isRefusal(String text) {
		return text != null && REFUSAL.matcher(text).find();
	}

	/**
	 * Whether replies should be checked for unsupported figures, per {@code external.anthropic.figure-check}.
	 *
	 * @return {@code true} when the check runs
	 */
	public boolean isCheckEnabled() {
		return properties.isFigureCheck();
	}

	/**
	 * Lists the figures a reply cites that the prompt's data does not support, in the order they first appear.
	 *
	 * @param prompt the prompt the reply answered (its figures are the supported set)
	 * @param reply  the parsed reply; every text value in it is checked, keys are not
	 * @return the unsupported figures as written in the reply, without duplicates; empty when all are supported
	 */
	public List<String> unsupportedFigures(String prompt, JsonNode reply) {
		if (prompt == null || reply == null) {
			return List.of();
		}
		List<NarrativeFigure> inputs = figures(prompt);
		Set<String> unsupported = new LinkedHashSet<>();
		for (String text : textValues(reply)) {
			for (NarrativeFigure figure : figures(text)) {
				if (!isSupported(figure, inputs)) {
					unsupported.add(figure.text());
				}
			}
		}
		return new ArrayList<>(unsupported);
	}

	/**
	 * Builds the retry note naming the unsupported figures.
	 *
	 * @param unsupported the figures the previous reply could not support
	 * @return the note, ready to append to the prompt
	 */
	public String retryNote(List<String> unsupported) {
		return String.format(Locale.ROOT, RETRY_NOTE, String.join(LIST_SEPARATOR, unsupported));
	}

	/**
	 * Builds the report-card warning for figures that survived the retry.
	 *
	 * @param unsupported the figures still unsupported
	 * @return a user-facing warning naming them
	 */
	public String warning(List<String> unsupported) {
		return "the AI copy cites figures not found in the report data (" + String.join(LIST_SEPARATOR, unsupported)
				+ ") — check them before sending.";
	}

	/**
	 * Whether a reply figure matches an input figure directly or as a simple derivation of two of them.
	 *
	 * @param figure the reply figure
	 * @param inputs the prompt's figures
	 * @return {@code true} when the figure is supported
	 */
	boolean isSupported(NarrativeFigure figure, List<NarrativeFigure> inputs) {
		for (NarrativeFigure input : inputs) {
			if (Math.abs(input.value() - figure.value()) <= figure.tolerance() + input.tolerance()) {
				return true;
			}
		}
		return isDerivable(figure, inputs);
	}

	/**
	 * Whether a reply figure equals a difference, share, percent change or multiple of two input figures,
	 * within the reply figure's rounding plus {@link #DERIVED_SLACK}.
	 *
	 * @param figure the reply figure
	 * @param inputs the prompt's figures
	 * @return {@code true} when some pair of inputs yields the figure
	 */
	boolean isDerivable(NarrativeFigure figure, List<NarrativeFigure> inputs) {
		double target = Math.abs(figure.value());
		double tolerance = figure.tolerance() + DERIVED_SLACK * target;
		int n = Math.min(inputs.size(), MAX_INPUT_FIGURES);
		for (int i = 0; i < n; i++) {
			double a = inputs.get(i).value();
			for (int j = 0; j < n; j++) {
				double b = inputs.get(j).value();
				if (i == j || b == 0) {
					continue;
				}
				if (Math.abs(Math.abs(a - b) - target) <= tolerance
						|| Math.abs(a / b * PERCENT - target) <= tolerance
						|| Math.abs(Math.abs((a - b) / b * PERCENT) - target) <= tolerance
						|| Math.abs(a / b - target) <= tolerance) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Extracts the figures from a text, skipping small counts, years, calendar days and clock times. Input and
	 * reply text go through the same extraction so both sides are read alike.
	 *
	 * @param text the text to scan
	 * @return the figures in order of appearance
	 */
	List<NarrativeFigure> figures(String text) {
		List<NarrativeFigure> out = new ArrayList<>();
		Matcher m = FIGURE.matcher(text);
		while (m.find()) {
			String integer = m.group(1).replace(",", "");
			String decimals = m.group(2);
			String unit = m.group(3);
			double value = Double.parseDouble(decimals == null ? integer : integer + "." + decimals);
			if (isNotAFigure(text, m, value, decimals, unit)) {
				continue;
			}
			double multiplier = multiplier(unit);
			double step = decimals == null ? 1.0 : Math.pow(10, -decimals.length());
			out.add(new NarrativeFigure(m.group().trim(), value * multiplier, step / 2 * multiplier));
		}
		return out;
	}

	/**
	 * Whether a matched number is something other than a metric: a small bare count, a bare year, a calendar
	 * day after a month name, or a clock time.
	 *
	 * @param text     the scanned text
	 * @param m        the matcher positioned on the number
	 * @param value    the number's value without its unit
	 * @param decimals the decimal digits, or {@code null}
	 * @param unit     the unit, or {@code null}
	 * @return {@code true} when the number should not be checked
	 */
	boolean isNotAFigure(String text, Matcher m, double value, String decimals, String unit) {
		boolean bare = decimals == null && unit == null && !m.group().startsWith("$")
				&& !m.group().startsWith("€") && !m.group().startsWith("£");
		if (bare && value <= SMALL_COUNT) {
			return true;
		}
		if (bare && value >= YEAR_MIN && value <= YEAR_MAX && !m.group(1).contains(",")) {
			return true;
		}
		if (CLOCK_AFTER.matcher(text.substring(m.end())).find()) {
			return true;
		}
		return bare && MONTH_BEFORE.matcher(text.substring(Math.max(0, m.start() - 12), m.start())).find();
	}

	/**
	 * The magnitude a unit suffix stands for.
	 *
	 * @param unit the unit, or {@code null}
	 * @return 1e3 for k, 1e6 for M, 1e9 for B, otherwise 1
	 */
	double multiplier(String unit) {
		if (unit == null) {
			return 1.0;
		}
		return switch (unit.toLowerCase(Locale.ROOT)) {
			case "k" -> 1e3;
			case "m" -> 1e6;
			case "b" -> 1e9;
			default -> 1.0;
		};
	}

	/**
	 * Collects every text value in a reply, depth-first, ignoring keys.
	 *
	 * @param node the reply node
	 * @return the text values
	 */
	List<String> textValues(JsonNode node) {
		List<String> out = new ArrayList<>();
		if (node.isTextual()) {
			out.add(node.asText());
		} else if (node.isContainerNode()) {
			node.forEach(child -> out.addAll(textValues(child)));
		}
		return out;
	}
}
