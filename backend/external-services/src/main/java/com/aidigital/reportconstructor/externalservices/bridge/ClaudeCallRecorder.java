package com.aidigital.reportconstructor.externalservices.bridge;

import com.aidigital.reportconstructor.externalservices.anthropic.AnthropicCallListener;
import com.aidigital.reportconstructor.service.reports.diagnostics.ClaudeFailureLog;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Feeds what the Anthropic client learns about each call into the running report: token usage into the
 * service's {@link ClaudeUsageTracker} (the admin token dashboard) and rejected replies into its
 * {@link ClaudeFailureLog} (the result card).
 */
@Component
@RequiredArgsConstructor
public class ClaudeCallRecorder implements AnthropicCallListener {

	private final ClaudeUsageTracker usageTracker;
	private final ClaudeFailureLog failureLog;

	@Override
	public void usage(String label, long inputTokens, long outputTokens, long cacheWriteTokens, long cacheReadTokens,
			String model) {
		usageTracker.record(label, inputTokens, outputTokens, cacheWriteTokens, cacheReadTokens, model);
	}

	@Override
	public void estimatedUsage(String label, long estimatedInputTokens, String model) {
		usageTracker.recordEstimated(label, estimatedInputTokens, model);
	}

	@Override
	public void replyProblem(String label, String detail) {
		failureLog.record(label, detail);
	}
}
