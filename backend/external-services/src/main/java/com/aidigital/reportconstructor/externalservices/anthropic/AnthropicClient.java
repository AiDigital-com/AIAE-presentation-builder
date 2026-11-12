package com.aidigital.reportconstructor.externalservices.anthropic;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Narrow interface over the Anthropic Messages API: send one prompt, get the reply back as JSON. The
 * implementation owns authentication, the pooled HTTP client, concurrency limiting, retries, truncation
 * salvage and JSON repair; what the prompt says and what the reply means belong to the caller.
 *
 * <p>Every method returns {@code null} instead of throwing when no usable reply could be obtained. Token usage
 * and rejected replies are reported to the {@link AnthropicCallListener}.
 */
public interface AnthropicClient {

	/**
	 * Sends a prompt and parses the model's JSON object from the text reply, stripping Markdown code fences and
	 * optionally repairing output cut off by the token budget.
	 *
	 * @param prompt       the full user prompt, sent as the single message
	 * @param maxTokens    cap on tokens the model may generate
	 * @param timeoutSec   per-request HTTP timeout in seconds
	 * @param label        short tag identifying this call in logs, usage and failure reports
	 * @param allowPartial when {@code true}, accepts truncated output and repairs the trailing JSON
	 * @return the parsed object, or {@code null} on failure
	 */
	JsonNode callJsonObject(String prompt, int maxTokens, int timeoutSec, String label, boolean allowPartial);

	/**
	 * Sends a prompt expecting a bare top-level JSON array reply.
	 *
	 * @param prompt       the full user prompt, sent as the single message
	 * @param maxTokens    cap on tokens the model may generate
	 * @param timeoutSec   per-request HTTP timeout in seconds
	 * @param label        short tag identifying this call in logs, usage and failure reports
	 * @param allowPartial when {@code true}, accepts truncated output and repairs the trailing JSON
	 * @return the parsed array, or {@code null} when no usable array could be recovered
	 */
	JsonNode callJsonArray(String prompt, int maxTokens, int timeoutSec, String label, boolean allowPartial);

	/**
	 * Sends a prompt and returns the raw Messages API response body.
	 *
	 * @param prompt     the full user prompt, sent as the single message
	 * @param maxTokens  cap on tokens the model may generate
	 * @param timeoutSec per-request HTTP timeout in seconds
	 * @param label      short tag identifying this call in logs, usage and failure reports
	 * @return the response body, or {@code null} on failure
	 */
	JsonNode callRaw(String prompt, int maxTokens, int timeoutSec, String label);

	/**
	 * Concatenates the text blocks of a Messages API response.
	 *
	 * @param response a response body from {@link #callRaw}
	 * @return the combined text, or {@code null} when the response carries no content
	 */
	String replyText(JsonNode response);
}
