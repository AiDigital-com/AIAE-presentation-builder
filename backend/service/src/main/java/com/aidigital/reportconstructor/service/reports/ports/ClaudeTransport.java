package com.aidigital.reportconstructor.service.reports.ports;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Sends one prompt to Claude and returns the reply. The report's Claude logic — what to ask, how to read the
 * answer — lives in the service and talks to the model only through this port; the integration behind it owns
 * authentication, concurrency, retries, token accounting and repairing truncated JSON.
 *
 * <p>Every method returns {@code null} instead of throwing when no usable reply could be obtained.
 */
public interface ClaudeTransport {

	/**
	 * Marks where the cacheable, stable part of a prompt ends. Text before it is sent as a prompt-cached block,
	 * text after it as the per-call variable block; a prompt without it is sent as one uncached block.
	 */
	String CACHE_BREAKPOINT = "\u0000CACHE_BREAKPOINT\u0000";

	/**
	 * Sends a prompt and parses the JSON object of the reply.
	 *
	 * @param prompt       the full prompt, optionally carrying one {@link #CACHE_BREAKPOINT}
	 * @param maxTokens    cap on tokens the model may generate
	 * @param timeoutSec   per-request timeout in seconds
	 * @param label        short tag identifying this call in logs, usage and failure reports
	 * @param allowPartial when {@code true}, a reply cut off by the token budget is repaired and kept
	 * @return the parsed object, or {@code null} on failure
	 */
	JsonNode callJsonObject(String prompt, int maxTokens, int timeoutSec, String label, boolean allowPartial);

	/**
	 * Sends a prompt expecting a bare top-level JSON array reply.
	 *
	 * @param prompt       the full prompt, optionally carrying one {@link #CACHE_BREAKPOINT}
	 * @param maxTokens    cap on tokens the model may generate
	 * @param timeoutSec   per-request timeout in seconds
	 * @param label        short tag identifying this call in logs, usage and failure reports
	 * @param allowPartial when {@code true}, a reply cut off by the token budget is repaired and kept
	 * @return the parsed array, or {@code null} when no usable array could be recovered
	 */
	JsonNode callJsonArray(String prompt, int maxTokens, int timeoutSec, String label, boolean allowPartial);

	/**
	 * Sends a prompt and returns the reply's plain text.
	 *
	 * @param prompt     the full prompt
	 * @param maxTokens  cap on tokens the model may generate
	 * @param timeoutSec per-request timeout in seconds
	 * @param label      short tag identifying this call in logs, usage and failure reports
	 * @return the reply, or {@code null} when none arrived; a reply's text is {@code null} when it carried none
	 */
	ClaudeReply callText(String prompt, int maxTokens, int timeoutSec, String label);
}
