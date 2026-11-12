package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.diagnostics.ClaudeFailureLog;
import com.aidigital.reportconstructor.service.reports.diagnostics.ClaudeFailureScope;
import com.aidigital.reportconstructor.service.reports.dto.TacticPacing;
import com.aidigital.reportconstructor.service.reports.dto.TacticPacingInput;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughts;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughtsInput;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageScope;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Fans per-tactic Claude calls out on the shared virtual-thread executor under the run's usage and failure scopes, and
 * joins the results.
 */
@Component
@RequiredArgsConstructor
public class ClaudeCallDispatcher {

	private final ClaudeFailureLog failureLog;
	private final ClaudeUsageTracker usageTracker;
	private final AsyncTaskExecutor applicationTaskExecutor;

	/**
	 * Dispatches one breakdown section's dedicated per-tactic calls — one call per tactic that enabled the
	 * section — on the shared virtual-thread executor and under the same usage scope the sheet reads use, so each
	 * call's tokens are still billed to this job even though it runs off the request thread. It returns the
	 * still-running futures without joining, so the caller can dispatch every section up front and let them all
	 * run in parallel (bounded by the transport's shared Claude concurrency limit) before joining any of them.
	 *
	 * @param inputs     the section's per-tactic inputs (tactic number → the tactic's section input)
	 * @param usageScope the usage scope to run each call under so its tokens are billed to this job
	 * @param call       invokes the section's Claude call for one tactic's input, returning its validated strings
	 * @param <T>        the section's per-tactic input type
	 * @return tactic number → the running future of its section call
	 */
	public <T> Map<Integer, CompletableFuture<List<String>>> dispatchSection(
			Map<Integer, T> inputs, ClaudeUsageScope usageScope, Function<T, List<String>> call) {
		Map<Integer, CompletableFuture<List<String>>> futures = new LinkedHashMap<>();
		// Both scopes are read here, on the run's own thread, and bound around each call on its worker thread —
		// otherwise a section rejected off-thread would leave neither its tokens nor its reason behind.
		ClaudeFailureScope failureScope = failureLog.current();
		inputs.forEach((tacticNum, input) -> futures.put(tacticNum, CompletableFuture.supplyAsync(
				usageTracker.inScope(usageScope, failureLog.inScope(failureScope, () -> call.apply(input))),
				applicationTaskExecutor)));
		return futures;
	}

	/**
	 * Dispatches the Step-3 per-tactic thoughts calls — one call per qualifying tactic — on the shared
	 * virtual-thread executor and under the same usage and failure scopes {@link #dispatchSection} uses, so each
	 * call's tokens are still billed to this job and its rejection reasons still reach the report card even
	 * though it runs off the request thread. It returns the still-running futures in input order without joining,
	 * so every tactic runs in parallel (bounded by the transport's shared Claude concurrency limit) instead of
	 * one after another.
	 *
	 * @param claude     the run's report-type-specific Claude client
	 * @param inputs     one input per qualifying tactic, in slide order
	 * @param usageScope the usage scope to run each call under so its tokens are billed to this job
	 * @param brief      free-text campaign brief passed through to every call
	 * @return the running futures, in input order
	 */
	public List<CompletableFuture<TacticThoughts>> dispatchThoughts(
			ClaudeClient claude, List<TacticThoughtsInput> inputs, ClaudeUsageScope usageScope, String brief) {
		List<CompletableFuture<TacticThoughts>> futures = new ArrayList<>();
		// Both scopes are read here, on the run's own thread, and bound around each call on its worker thread —
		// otherwise a rejected tactic would leave neither its tokens nor its reason behind.
		ClaudeFailureScope failureScope = failureLog.current();
		for (TacticThoughtsInput input : inputs) {
			futures.add(CompletableFuture.supplyAsync(
					usageTracker.inScope(usageScope, failureLog.inScope(failureScope,
							() -> claude.tacticThoughts(input, brief))),
					applicationTaskExecutor));
		}
		return futures;
	}

	/**
	 * Dispatches one pacing-narrative call per tactic on the shared virtual-thread executor, under the same
	 * usage and failure scopes as every other per-tactic call, and returns the running futures without
	 * joining so the tactics run in parallel.
	 *
	 * @param claude     the run's report-type-specific Claude client
	 * @param inputs     one input per tactic, in slide order
	 * @param usageScope the usage scope to run each call under so its tokens are billed to this job
	 * @param brief      free-text campaign brief passed through to every call
	 * @return the running futures, in input order
	 */
	public List<CompletableFuture<TacticPacing>> dispatchPacing(
			ClaudeClient claude, List<TacticPacingInput> inputs, ClaudeUsageScope usageScope, String brief) {
		List<CompletableFuture<TacticPacing>> futures = new ArrayList<>();
		ClaudeFailureScope failureScope = failureLog.current();
		for (TacticPacingInput input : inputs) {
			futures.add(CompletableFuture.supplyAsync(
					usageTracker.inScope(usageScope, failureLog.inScope(failureScope,
							() -> claude.tacticPacing(input, brief))),
					applicationTaskExecutor));
		}
		return futures;
	}

	/**
	 * Joins the dispatched pacing-narrative futures, dropping the tactics whose call produced no usable
	 * reply so the writer dashes their tokens.
	 *
	 * @param futures the running futures from {@link #dispatchPacing}, in input order
	 * @return one entry per tactic that produced a usable reply, in input order
	 */
	public List<TacticPacing> joinPacing(List<CompletableFuture<TacticPacing>> futures) {
		List<TacticPacing> narratives = new ArrayList<>();
		for (CompletableFuture<TacticPacing> future : futures) {
			TacticPacing value = future.join();
			if (value != null) {
				narratives.add(value);
			}
		}
		return narratives;
	}

	/**
	 * Joins the dispatched Step-3 futures into the thoughts list the token writer and the campaign digests
	 * expect, in input order. A tactic whose call produced no usable reply ({@code null}) is left out, so its
	 * slide ships those tokens blank rather than carrying invented copy.
	 *
	 * @param futures the running futures from {@link #dispatchThoughts}, in input order
	 * @return one entry per tactic that produced a usable reply, in input order
	 */
	public List<TacticThoughts> joinThoughts(List<CompletableFuture<TacticThoughts>> futures) {
		List<TacticThoughts> thoughts = new ArrayList<>();
		for (CompletableFuture<TacticThoughts> future : futures) {
			TacticThoughts value = future.join();
			if (value != null) {
				thoughts.add(value);
			}
		}
		return thoughts;
	}

	/**
	 * Joins one section's dispatched futures into the {@code tactic → bullets} map the section write helpers
	 * expect. A tactic whose call returned no usable reply (an empty list) is left out, so its slide ships blank
	 * (and the write helper surfaces that as a warning) rather than carrying invented copy.
	 *
	 * @param futures tactic number → the running future of its section call, from {@link #dispatchSection}
	 * @return tactic number → its validated strings, only for tactics that returned a usable reply
	 */
	public Map<Integer, List<String>> joinSection(Map<Integer, CompletableFuture<List<String>>> futures) {
		Map<Integer, List<String>> out = new LinkedHashMap<>();
		futures.forEach((tacticNum, future) -> {
			List<String> value = future.join();
			if (value != null && !value.isEmpty()) {
				out.put(tacticNum, value);
			}
		});
		return out;
	}
}
