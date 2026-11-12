package com.aidigital.reportconstructor.service.integration;

import com.aidigital.reportconstructor.externalservices.anthropic.AnthropicClient;
import com.aidigital.reportconstructor.externalservices.anthropic.AnthropicMessagesClient;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeReply;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaudeTransportBridgeTest {

	@Test
	void shouldUseTheSameCacheBreakpointAsTheTransportTest() {
		// Given: the service's prompts mark the cacheable prefix; the transport splits on the same marker

		// When-Then:
		assertThat(ClaudeTransport.CACHE_BREAKPOINT).isEqualTo(AnthropicMessagesClient.CACHE_BREAKPOINT);
	}

	@Test
	void shouldReturnTheReplyTextOfARawCallTest() throws Exception {
		// Given:
		AnthropicClient client = mock(AnthropicClient.class);
		JsonNode response = new ObjectMapper().readTree("{\"content\":[{\"type\":\"text\",\"text\":\"Dallas\"}]}");
		when(client.callRaw("prompt", 60, 30, "Geo")).thenReturn(response);
		when(client.replyText(response)).thenReturn("Dallas");
		ClaudeTransportBridge bridge = new ClaudeTransportBridge(client);

		// When:
		ClaudeReply reply = bridge.callText("prompt", 60, 30, "Geo");

		// Then:
		assertThat(reply).isEqualTo(new ClaudeReply("Dallas"));
	}

	@Test
	void shouldReturnNoReplyWhenNoneArrivedTest() {
		// Given: the transport gave up (it has already logged and booked the call)
		AnthropicClient client = mock(AnthropicClient.class);
		ClaudeTransportBridge bridge = new ClaudeTransportBridge(client);

		// When:
		ClaudeReply reply = bridge.callText("prompt", 60, 30, "Geo");

		// Then: no reply at all, as opposed to a reply without text
		assertThat(reply).isNull();
	}

	@Test
	void shouldPassJsonCallsThroughTest() throws Exception {
		// Given:
		AnthropicClient client = mock(AnthropicClient.class);
		JsonNode object = new ObjectMapper().readTree("{\"a\": 1}");
		JsonNode array = new ObjectMapper().readTree("[\"x\"]");
		when(client.callJsonObject("p", 500, 30, "BatchB", false)).thenReturn(object);
		when(client.callJsonArray("p", 900, 60, "Section", true)).thenReturn(array);
		ClaudeTransportBridge bridge = new ClaudeTransportBridge(client);

		// When-Then:
		assertThat(bridge.callJsonObject("p", 500, 30, "BatchB", false)).isSameAs(object);
		assertThat(bridge.callJsonArray("p", 900, 60, "Section", true)).isSameAs(array);
	}
}
