package com.aidigital.reportconstructor.externalservices.bridge;

import com.aidigital.reportconstructor.externalservices.anthropic.AnthropicCallListener;
import com.aidigital.reportconstructor.service.reports.claude.PromptTokenEstimator;
import com.aidigital.reportconstructor.service.reports.diagnostics.ClaudeFailureLog;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Feeds what the Anthropic client learns about each call into the running report: token usage into the
 * service's {@link ClaudeUsageTracker} (the admin token dashboard) and rejected replies into its
 * {@link ClaudeFailureLog} (the result card). A call whose reply never arrived is booked at the
 * {@link PromptTokenEstimator}'s estimate of its prompt.
 */
@Component
@RequiredArgsConstructor
public class ClaudeCallRecorder implements AnthropicCallListener {

	private final ClaudeUsageTracker usageTracker;
	private final ClaudeFailureLog failureLog;
	private final PromptTokenEstimator tokenEstimator;

	@Override
	public void usage(String label, long inputTokens, long outputTokens, long cacheWriteTokens, long cacheReadTokens,
			String model) {
		usageTracker.record(label, inputTokens, outputTokens, cacheWriteTokens, cacheReadTokens, model);
	}

	@Override
	public void unansweredCall(String label, String prompt, String model) {
		usageTracker.recordEstimated(label, tokenEstimator.estimateTokens(prompt), model);
	}

	@Override
	public void replyProblem(String label, String detail) {
		failureLog.record(label, detail);
	}
}
