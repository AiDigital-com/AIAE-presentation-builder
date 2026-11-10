package com.aidigital.reportconstructor.externalservices.anthropic;

/**
 * One figure found in prompt or reply text, as {@link NarrativeGrounding} compares them.
 *
 * @param text      the figure exactly as written, e.g. {@code "$27.8K"}, for naming it in a retry or warning
 * @param value     its value with any k/M/B magnitude applied ({@code %} and {@code pp} figures stay as written)
 * @param tolerance half a unit of the precision it is written in, so a rounded figure still matches its source
 */
public record NarrativeFigure(String text, double value, double tolerance) {
	// pure data
}
