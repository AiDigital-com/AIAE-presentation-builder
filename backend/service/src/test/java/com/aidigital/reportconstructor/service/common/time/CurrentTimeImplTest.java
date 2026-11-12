package com.aidigital.reportconstructor.service.common.time;

import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentTimeImplTest {

	@Test
	void shouldReportUtcAndDeriveEveryViewFromOneInstantTest() {
		// Given:
		Instant fixed = Instant.parse("2026-11-12T23:30:00Z");
		CurrentTime currentTime = new CurrentTime() {
			@Override
			public Instant nowInstant() {
				return fixed;
			}

			@Override
			public ZoneOffset getDefaultTimeZone() {
				return new CurrentTimeImpl().getDefaultTimeZone();
			}
		};

		// When-Then: offset, local date-time and local date all describe the same UTC instant
		assertThat(currentTime.getDefaultTimeZone()).isEqualTo(ZoneOffset.UTC);
		assertThat(currentTime.nowOffsetDateTime().toInstant()).isEqualTo(fixed);
		assertThat(currentTime.nowOffsetDateTime().getOffset()).isEqualTo(ZoneOffset.UTC);
		assertThat(currentTime.nowLocalDateTime()).hasToString("2026-11-12T23:30");
		assertThat(currentTime.nowLocalDate()).hasToString("2026-11-12");
	}

	@Test
	void shouldReturnTheSystemInstantTest() {
		// Given:
		CurrentTimeImpl currentTime = new CurrentTimeImpl();
		Instant before = Instant.now();

		// When:
		Instant now = currentTime.nowInstant();

		// Then:
		assertThat(now).isBetween(before, Instant.now());
	}
}
