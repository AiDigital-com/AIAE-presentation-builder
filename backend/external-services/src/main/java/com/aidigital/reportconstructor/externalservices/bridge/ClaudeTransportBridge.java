package com.aidigital.reportconstructor.externalservices.bridge;

import com.aidigital.reportconstructor.externalservices.anthropic.AnthropicClient;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeReply;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeTransport;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Implements the service's {@link ClaudeTransport} port on top of the Anthropic integration's
 * {@link AnthropicClient}. Present exactly when the client is — when an Anthropic API key is configured — so the
 * service's real Claude logic is wired only then and its stubs otherwise.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${external.anthropic.api-key:}' != ''")
public class ClaudeTransportBridge implements ClaudeTransport {

	private final AnthropicClient client;

	@Override
	public JsonNode callJsonObject(String prompt, int maxTokens, int timeoutSec, String label, boolean allowPartial) {
		return client.callJsonObject(prompt, maxTokens, timeoutSec, label, allowPartial);
	}

	@Override
	public JsonNode callJsonArray(String prompt, int maxTokens, int timeoutSec, String label, boolean allowPartial) {
		return client.callJsonArray(prompt, maxTokens, timeoutSec, label, allowPartial);
	}

	@Override
	public ClaudeReply callText(String prompt, int maxTokens, int timeoutSec, String label) {
		JsonNode response = client.callRaw(prompt, maxTokens, timeoutSec, label);
		return response == null ? null : new ClaudeReply(client.replyText(response));
	}
}
