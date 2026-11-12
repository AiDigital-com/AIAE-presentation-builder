package com.aidigital.reportconstructor.service.reports.claude;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * How the report's Claude logic splits and retries its calls. Bound from {@code external.anthropic.*} — the same
 * keys as before these settings moved out of the integration's transport configuration, so no environment
 * changes.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "external.anthropic")
public class ClaudePromptProperties {

	/**
	 * Tactics per Step-2 per-tactic conclusions call in the slides-from-sheet flow. Default 7: that call
	 * produces only each tactic's ~190-character overview (every breakdown section has its own call), so a
	 * chunk of 7 still asks for a small reply while turning a 28-tactic deck's 28 calls into 4 — and lets the
	 * model see neighbouring tactics rather than writing every overview blind. Chunks run concurrently, so a
	 * larger chunk trades parallelism for context; raise it further only alongside a larger reply budget.
	 * Clamped to at least 1 at use.
	 */
	private int breakdownChunkSize = 7;

	/**
	 * Extra attempts a per-section pilot call makes when its reply fails the positional contract (not a JSON
	 * array of exactly the expected count of non-blank strings) before the tactic's section ships blank.
	 * Default 1 means one retry, two sends in total: a deterministic rejection reproduces on the re-send, so a
	 * third identical prompt mostly burns tokens. Clamped to at least 0 (no retry) at use.
	 */
	private int sectionRetries = 1;
}
