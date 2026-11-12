package com.aidigital.reportconstructor;

import com.aidigital.reportconstructor.service.reports.claude.ClaudePromptProperties;
import com.aidigital.reportconstructor.service.reports.claude.EomClaudeClientConfig;
import com.aidigital.reportconstructor.service.reports.claude.RealClaudeClient;
import com.aidigital.reportconstructor.service.reports.claude.RealLineItemMatchAssistant;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeTransport;
import com.aidigital.reportconstructor.service.reports.ports.LineItemMatchAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring check for the Claude path with an Anthropic API key configured: the service's Claude logic must be the
 * active implementation, reaching the model through the transport port (the integration's bridge). No request is
 * sent — building the beans needs no network.
 */
@SpringBootTest(properties = {"external.anthropic.api-key=test-key", "external.anthropic.section-retries=3"})
@ActiveProfiles("test")
class ClaudeWiringSmokeTest {

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Autowired
	private ApplicationContext context;

	@Test
	void shouldWireTheServicesClaudeLogicOverTheTransportWhenAKeyIsSetTest() {
		// Given: the context started with an API key

		// When:
		ClaudeClient primary = context.getBean(ClaudeClient.class);
		Object eom = context.getBean(EomClaudeClientConfig.EOM_CLAUDE_CLIENT);

		// Then: both flavours are the real client, the transport port is present, and the prompt settings
		// still bind from the unchanged external.anthropic keys
		assertThat(primary).isInstanceOf(RealClaudeClient.class);
		assertThat(eom).isInstanceOf(RealClaudeClient.class).isNotSameAs(primary);
		assertThat(context.getBeansOfType(ClaudeTransport.class)).hasSize(1);
		assertThat(context.getBean(LineItemMatchAssistant.class)).isInstanceOf(RealLineItemMatchAssistant.class);
		assertThat(context.getBean(ClaudePromptProperties.class).getSectionRetries()).isEqualTo(3);
	}
}
