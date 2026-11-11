package com.aidigital.reportconstructor.config;

import org.junit.jupiter.api.Test;
import org.zalando.logbook.Strategy;
import org.zalando.logbook.core.DefaultStrategy;
import org.zalando.logbook.core.WithoutBodyStrategy;

import static org.assertj.core.api.Assertions.assertThat;

class LogbookConfigTest {

	@Test
	void shouldLogMetadataOnlyByDefaultTest() {
		// Given: default properties, as bound in production
		LogbookProperties props = new LogbookProperties();

		// When:
		Strategy strategy = new LogbookConfig().strategy(props);

		// Then: bodies (prompts, sheet contents, OAuth tokens) are never written
		assertThat(strategy).isInstanceOf(WithoutBodyStrategy.class);
	}

	@Test
	void shouldLogBodiesOnlyWhenExplicitlyEnabledTest() {
		// Given: a local debugging session that opted into bodies
		LogbookProperties props = new LogbookProperties();
		props.setIncludeBodies(true);

		// When:
		Strategy strategy = new LogbookConfig().strategy(props);

		// Then:
		assertThat(strategy).isInstanceOf(DefaultStrategy.class);
	}
}
