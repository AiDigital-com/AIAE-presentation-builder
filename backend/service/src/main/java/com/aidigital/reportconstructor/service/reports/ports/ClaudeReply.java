package com.aidigital.reportconstructor.service.reports.ports;

/**
 * A plain-text reply from Claude.
 *
 * @param text the reply's text blocks concatenated, or {@code null} when the reply carried no text
 */
public record ClaudeReply(String text) {
}
