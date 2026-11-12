package com.aidigital.reportconstructor.service.integration;

import com.aidigital.reportconstructor.service.reports.claude.PromptTokenEstimator;
import com.aidigital.reportconstructor.service.reports.diagnostics.ClaudeFailureLog;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageTracker;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ClaudeCallRecorderTest {

	@Test
	void shouldBookUsageOnTheRunsTokenAccountingTest() {
		// Given:
		ClaudeUsageTracker tracker = mock(ClaudeUsageTracker.class);
		ClaudeFailureLog failureLog = mock(ClaudeFailureLog.class);
		PromptTokenEstimator estimator = new PromptTokenEstimator();
		ClaudeCallRecorder recorder = new ClaudeCallRecorder(tracker, failureLog, estimator);

		// When:
		recorder.usage("BatchC", 1200, 340, 90, 8000, "claude-sonnet-4-6");
		recorder.unansweredCall("BatchB", "Summarise the campaign.", "claude-sonnet-4-6");

		// Then: an unanswered call is booked at the prompt's estimated size
		verify(tracker).record("BatchC", 1200, 340, 90, 8000, "claude-sonnet-4-6");
		verify(tracker).recordEstimated("BatchB", estimator.estimateTokens("Summarise the campaign."),
				"claude-sonnet-4-6");
		verifyNoInteractions(failureLog);
	}

	@Test
	void shouldPutRejectedRepliesOnTheResultCardTest() {
		// Given:
		ClaudeUsageTracker tracker = mock(ClaudeUsageTracker.class);
		ClaudeFailureLog failureLog = mock(ClaudeFailureLog.class);
		ClaudeCallRecorder recorder = new ClaudeCallRecorder(tracker, failureLog, new PromptTokenEstimator());

		// When:
		recorder.replyProblem("BatchA", "reply was not valid JSON (object, 12 chars); it began: Sorry");

		// Then:
		verify(failureLog).record("BatchA", "reply was not valid JSON (object, 12 chars); it began: Sorry");
		verifyNoInteractions(tracker);
	}
}
