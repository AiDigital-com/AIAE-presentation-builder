package com.aidigital.reportconstructor.config;

import org.junit.jupiter.api.Test;
import org.zalando.logbook.Strategy;
import org.zalando.logbook.core.DefaultStrategy;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LogbookConfigTest {

	@Test
	void shouldLogMetadataOnlyByDefaultTest() {
		// Given: default properties, as bound in production
		LogbookProperties props = new LogbookProperties();

		// When:
		Optional<Strategy> override = new LogbookConfig().bodyLoggingOverride(props);

		// Then: the metadata-only default stays, so bodies (prompts, sheet contents, OAuth tokens) are never written
		assertThat(override).isEmpty();
	}

	@Test
	void shouldLogBodiesOnlyWhenExplicitlyEnabledTest() {
		// Given: a local debugging session that opted into bodies
		LogbookProperties props = new LogbookProperties();
		props.setIncludeBodies(true);

		// When:
		Optional<Strategy> override = new LogbookConfig().bodyLoggingOverride(props);

		// Then:
		assertThat(override).containsInstanceOf(DefaultStrategy.class);
	}
}
