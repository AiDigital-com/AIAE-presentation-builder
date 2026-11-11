package com.aidigital.reportconstructor.observability.external;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalCallTimerTest {

	@Test
	void shouldRecordSuccessfulSupplierTest() {
		// Given:
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		ExternalCallTimer timer = new ExternalCallTimer(registry);

		// When:
		String result = timer.record("google-drive", "files.copy", () -> "value");

		// Then:
		assertThat(result).isEqualTo("value");
		assertThat(registry.get(ExternalCallTimer.METRIC_NAME)
				.tags("client", "google-drive", "operation", "files.copy", "outcome", "success")
				.timer().count()).isEqualTo(1);
	}

	@Test
	void shouldRecordAndRethrowFailureTest() {
		// Given:
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		ExternalCallTimer timer = new ExternalCallTimer(registry);

		// When-Then:
		assertThatThrownBy(() -> timer.record("google-drive", "files.create", () -> {
			throw new IllegalStateException("downstream failed");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(registry.get(ExternalCallTimer.METRIC_NAME)
				.tags("client", "google-drive", "operation", "files.create", "outcome", "error")
				.timer().count()).isEqualTo(1);
	}

	@Test
	void shouldRecordVoidCallTest() {
		// Given:
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		ExternalCallTimer timer = new ExternalCallTimer(registry);

		// When:
		timer.record("google-drive", "permissions.create", () -> { });

		// Then:
		assertThat(registry.get(ExternalCallTimer.METRIC_NAME)
				.tags("client", "google-drive", "operation", "permissions.create", "outcome", "success")
				.timer().count()).isEqualTo(1);
	}

	@Test
	void shouldRecordAndRethrowCheckedFailureTest() {
		// Given:
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		ExternalCallTimer timer = new ExternalCallTimer(registry);
		ExternalCall<String, IOException> call = () -> {
			throw new IOException("read timed out");
		};

		// When-Then:
		assertThatThrownBy(() -> timer.recordChecked("google-sheets", "spreadsheets.get", call))
				.isInstanceOf(IOException.class)
				.hasMessage("read timed out");
		assertThat(registry.get(ExternalCallTimer.METRIC_NAME)
				.tags("client", "google-sheets", "operation", "spreadsheets.get", "outcome", "error")
				.timer().count()).isEqualTo(1);
	}

	@Test
	void shouldReturnCheckedCallResultTest() throws IOException {
		// Given:
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		ExternalCallTimer timer = new ExternalCallTimer(registry);
		ExternalCall<String, IOException> call = () -> "sheet";

		// When:
		String result = timer.recordChecked("google-sheets", "spreadsheets.get", call);

		// Then:
		assertThat(result).isEqualTo("sheet");
		assertThat(registry.get(ExternalCallTimer.METRIC_NAME)
				.tags("client", "google-sheets", "operation", "spreadsheets.get", "outcome", "success")
				.timer().count()).isEqualTo(1);
	}
}
