package com.aidigital.reportconstructor.externalservices.anthropic;

/**
 * Receives what the {@link AnthropicClient} learns about each call: the tokens it used and why a reply could
 * not be used. Owned by the Anthropic integration; the application provides the implementation (today: the
 * run's token accounting and its failure log). Implementations must not throw.
 */
public interface AnthropicCallListener {

	/**
	 * Reports the token usage of a call that got a reply.
	 *
	 * @param label            the call's tag
	 * @param inputTokens      uncached input tokens
	 * @param outputTokens     generated tokens
	 * @param cacheWriteTokens input tokens written to the prompt cache
	 * @param cacheReadTokens  input tokens read from the prompt cache
	 * @param model            the model that answered
	 */
	void usage(String label, long inputTokens, long outputTokens, long cacheWriteTokens, long cacheReadTokens,
			String model);

	/**
	 * Reports a call whose request was sent but whose reply never arrived, so its cost can only be estimated
	 * from the prompt.
	 *
	 * @param label  the call's tag
	 * @param prompt the prompt that was sent
	 * @param model  the model the request was sent to
	 */
	void unansweredCall(String label, String prompt, String model);

	/**
	 * Reports why a reply was rejected or only partly used, in words meant for the person who ran the report.
	 *
	 * @param label  the call's tag
	 * @param detail what went wrong
	 */
	void replyProblem(String label, String detail);
}
