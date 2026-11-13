package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.common.time.CurrentTime;
import com.aidigital.reportconstructor.service.mappers.reports.ClaudeUsageEventEntityMapper;
import com.aidigital.reportconstructor.service.reports.diagnostics.ClaudeFailureScope;
import com.aidigital.reportconstructor.service.reports.diagnostics.impl.ClaudeFailureLogImpl;
import com.aidigital.reportconstructor.service.reports.dto.TacticPacing;
import com.aidigital.reportconstructor.service.reports.dto.TacticPacingInput;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughts;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughtsInput;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageEventService;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageScope;
import com.aidigital.reportconstructor.service.reports.usage.impl.ClaudeUsageTrackerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaudeCallDispatcherTest {

	@Mock
	private ClaudeClient claude;

	@Mock
	private ClaudeUsageEventService usageEvents;

	@Mock
	private CurrentTime currentTime;

	@Mock
	private ClaudeUsageEventEntityMapper eventMapper;

	@Test
	void shouldRunThoughtsCallsUnderTheRunScopesAndDropEmptyRepliesTest() {
		// Given: a run with bound failure and usage scopes, and two tactics — one answers, one does not.
		ClaudeFailureLogImpl failureLog = new ClaudeFailureLogImpl();
		ClaudeUsageTrackerImpl usageTracker = new ClaudeUsageTrackerImpl(usageEvents, currentTime, eventMapper);
		ClaudeCallDispatcher dispatcher = new ClaudeCallDispatcher(
				failureLog, usageTracker, new SimpleAsyncTaskExecutor("thoughts-"));
		ClaudeFailureScope failureScope = failureLog.begin();
		ClaudeUsageScope usageScope = new ClaudeUsageScope(42L, "user-1", "jane@aidigital.com");
		TacticThoughtsInput first = new TacticThoughtsInput(1, "Display", "overview", List.of(), List.of(),
				List.of(), List.of(), List.of(), false);
		TacticThoughtsInput second = new TacticThoughtsInput(2, "Video", "overview", List.of(), List.of(),
				List.of(), List.of(), List.of(), false);
		TacticThoughts answered = new TacticThoughts(1, List.of("CTR beat goal"));
		Set<Object> seenScopes = ConcurrentHashMap.newKeySet();
		when(claude.tacticThoughts(same(first), eq("brief"))).thenAnswer(invocation -> {
			seenScopes.add(usageTracker.current());
			seenScopes.add(failureLog.current());
			return answered;
		});
		when(claude.tacticThoughts(same(second), eq("brief"))).thenReturn(null);

		// When:
		List<CompletableFuture<TacticThoughts>> futures =
				dispatcher.dispatchThoughts(claude, List.of(first, second), usageScope, "brief");
		List<TacticThoughts> thoughts = dispatcher.joinThoughts(futures);

		// Then: the worker saw the run's own scopes, and the tactic with no reply is left out.
		assertThat(futures).hasSize(2);
		assertThat(thoughts).containsExactly(answered);
		assertThat(seenScopes).containsExactlyInAnyOrder(usageScope, failureScope);
	}

	@Test
	void shouldRunPacingCallsUnderTheRunScopesAndDropEmptyRepliesTest() {
		// Given:
		ClaudeFailureLogImpl failureLog = new ClaudeFailureLogImpl();
		ClaudeUsageTrackerImpl usageTracker = new ClaudeUsageTrackerImpl(usageEvents, currentTime, eventMapper);
		ClaudeCallDispatcher dispatcher = new ClaudeCallDispatcher(
				failureLog, usageTracker, new SimpleAsyncTaskExecutor("pacing-"));
		ClaudeFailureScope failureScope = failureLog.begin();
		ClaudeUsageScope usageScope = new ClaudeUsageScope(42L, "user-1", "jane@aidigital.com");
		TacticPacingInput first = new TacticPacingInput(1, "Display", "CPM", List.of());
		TacticPacingInput second = new TacticPacingInput(2, "Video", "CPV", List.of());
		TacticPacing answered = new TacticPacing(2, "worked", "watch", "act", "next");
		Set<Object> seenScopes = ConcurrentHashMap.newKeySet();
		when(claude.tacticPacing(same(first), eq("brief"))).thenReturn(null);
		when(claude.tacticPacing(same(second), eq("brief"))).thenAnswer(invocation -> {
			seenScopes.add(usageTracker.current());
			seenScopes.add(failureLog.current());
			return answered;
		});

		// When:
		List<CompletableFuture<TacticPacing>> futures =
				dispatcher.dispatchPacing(claude, List.of(first, second), usageScope, "brief");
		List<TacticPacing> narratives = dispatcher.joinPacing(futures);

		// Then:
		assertThat(futures).hasSize(2);
		assertThat(narratives).containsExactly(answered);
		assertThat(seenScopes).containsExactlyInAnyOrder(usageScope, failureScope);
	}

	@Test
	void shouldReturnNothingForNoTacticsTest() {
		// Given:
		ClaudeFailureLogImpl failureLog = new ClaudeFailureLogImpl();
		ClaudeUsageTrackerImpl usageTracker = new ClaudeUsageTrackerImpl(usageEvents, currentTime, eventMapper);
		ClaudeCallDispatcher dispatcher = new ClaudeCallDispatcher(
				failureLog, usageTracker, new SimpleAsyncTaskExecutor("none-"));
		ClaudeUsageScope usageScope = new ClaudeUsageScope(42L, "user-1", "jane@aidigital.com");

		// When:
		List<TacticThoughts> thoughts = dispatcher.joinThoughts(
				dispatcher.dispatchThoughts(claude, List.of(), usageScope, "brief"));
		List<TacticPacing> narratives = dispatcher.joinPacing(
				dispatcher.dispatchPacing(claude, List.of(), usageScope, "brief"));

		// Then:
		assertThat(thoughts).isEmpty();
		assertThat(narratives).isEmpty();
	}

	@Test
	void shouldDispatchSectionCallsPerTacticAndKeepOnlyUsableRepliesTest() {
		// Given: three tactics — one with bullets, one with an empty reply, one with no reply.
		ClaudeFailureLogImpl failureLog = new ClaudeFailureLogImpl();
		ClaudeUsageTrackerImpl usageTracker = new ClaudeUsageTrackerImpl(usageEvents, currentTime, eventMapper);
		ClaudeCallDispatcher dispatcher = new ClaudeCallDispatcher(
				failureLog, usageTracker, new SimpleAsyncTaskExecutor("section-"));
		ClaudeFailureScope failureScope = failureLog.begin();
		ClaudeUsageScope usageScope = new ClaudeUsageScope(42L, "user-1", "jane@aidigital.com");
		Map<Integer, String> inputs = new LinkedHashMap<>();
		inputs.put(1, "geo");
		inputs.put(2, "device");
		inputs.put(3, "audience");
		Map<String, List<String>> replies = new HashMap<>();
		replies.put("geo", List.of("NY led delivery"));
		replies.put("device", List.of());
		Set<Object> seenScopes = ConcurrentHashMap.newKeySet();

		// When:
		Map<Integer, CompletableFuture<List<String>>> futures = dispatcher.dispatchSection(inputs, usageScope,
				input -> {
					seenScopes.add(usageTracker.current());
					seenScopes.add(failureLog.current());
					return replies.get(input);
				});
		Map<Integer, List<String>> bullets = dispatcher.joinSection(futures);

		// Then: every tactic was dispatched under the run's scopes, only the usable reply is kept.
		assertThat(futures).containsOnlyKeys(1, 2, 3);
		assertThat(bullets).containsExactly(Map.entry(1, List.of("NY led delivery")));
		assertThat(seenScopes).containsExactlyInAnyOrder(usageScope, failureScope);
	}
}
