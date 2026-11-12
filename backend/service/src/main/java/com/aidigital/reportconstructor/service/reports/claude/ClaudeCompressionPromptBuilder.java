package com.aidigital.reportconstructor.service.reports.claude;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds the Batch D (compression) prompt {@link ClaudeCompressionService} sends. Kept apart from the report
 * prompt builders: it is the same for every report type and deck design, and it only rewrites copy those
 * prompts already produced.
 */
@Component
@ConditionalOnExpression("'${external.anthropic.api-key:}' != ''")
public class ClaudeCompressionPromptBuilder {

	/**
	 * Share of a field's real character budget quoted to Claude, so a reply that lands slightly over the
	 * number it was given still fits the budget actually enforced and skips another compression call. The
	 * report prompt builders quote their budgets through the same ratio.
	 */
	static final double PROMPT_BUFFER_RATIO = 0.8;

	/**
	 * Builds the prompt asking Claude to shrink each oversized field to its character budget while preserving
	 * meaning, or empty when there are no fields to compress. The limit quoted to Claude is
	 * {@link #PROMPT_BUFFER_RATIO} of the field's real budget — not the budget actually enforced — so the
	 * rewrite has headroom to still fit after the hard-truncation safety net runs.
	 *
	 * @param fields oversized fields to compress, each carrying its own raw text and character budget
	 * @return the compression prompt requesting a JSON object keyed by each field's {@code key}, or empty when
	 * {@code fields} is empty
	 */
	Optional<String> buildCompressionPrompt(List<ClaudeCompressionField> fields) {
		if (fields.isEmpty()) {
			return Optional.empty();
		}
		List<String> entries = new ArrayList<>();
		List<String> keys = new ArrayList<>();
		for (ClaudeCompressionField field : fields) {
			int promptLimit = Math.max(1, (int) (field.maxChars() * PROMPT_BUFFER_RATIO));
			keys.add("\"" + field.key() + "\"");
			entries.add("- key: \"" + field.key() + "\", limit: " + promptLimit + " characters\n"
					+ "  text: \"" + field.text().replace("\"", "'") + "\"");
		}
		return Optional.of(
				"You are editing client-facing campaign-report copy that is too long for its layout slot.\n\n"
						+ "For EACH field below, REWRITE it as a complete, self-contained thought that fits within " +
						"its character limit — do not just truncate the original. Preserve the key meaning and " +
						"business message as closely as possible: cut whole secondary clauses or examples before " +
						"cutting the main point, then re-word what remains so it reads as a finished sentence.\n\n"
						+ "Fields:\n" + String.join("\n", entries) + "\n\n"
						+ "Return ONLY a JSON object mapping each key to its rewritten text, with no other keys:\n"
						+ "{" + String.join(", ", keys) + "}\n\n"
						+ "Rules:\n"
						+ "- Every value's length MUST be at or under that field's character limit — count " +
						"characters, not words.\n"
						+ "- CRITICAL: every value MUST end with a period (or other sentence-ending punctuation) " +
						"and read as a complete thought. NEVER end mid-clause, mid-list, or on a dangling comma, " +
						"dash, or conjunction (e.g. \"and\", \"to ensure\", \"such as\"). If the full original idea " +
						"does not fit, drop an entire trailing clause/example rather than trimming it down to a " +
						"fragment.\n"
						+ "- Never cut off mid-word.\n"
						+ "- If a text opens with a lead phrase wrapped in [[double brackets]], the rewrite " +
						"MUST open with a lead phrase wrapped the same way (shorten it if needed).\n"
						+ "- Keep the same language (English) and tense as the original text.\n"
						+ "- Return ONLY the JSON object — no markdown, no backticks, no explanation."
		);
	}
}
