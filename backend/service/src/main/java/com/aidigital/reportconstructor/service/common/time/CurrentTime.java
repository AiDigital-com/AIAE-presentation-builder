package com.aidigital.reportconstructor.service.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Provides application time from an injectable boundary, so production code never calls {@code now()} directly
 * and tests can pin the clock.
 */
public interface CurrentTime {

	/** Latest time of day that survives a round trip through a microsecond-precision column. */
	LocalTime MAX_TIME = LocalTime.MAX.truncatedTo(ChronoUnit.MICROS);

	/**
	 * Returns the current instant.
	 *
	 * @return current instant from the application clock
	 */
	Instant nowInstant();

	/**
	 * Returns the default zone used for local and offset date/time values.
	 *
	 * @return default application zone offset
	 */
	ZoneOffset getDefaultTimeZone();

	/**
	 * Returns the current date-time with the default application offset.
	 *
	 * @return current offset date-time
	 */
	default OffsetDateTime nowOffsetDateTime() {
		return OffsetDateTime.ofInstant(this.nowInstant(), this.getDefaultTimeZone());
	}

	/**
	 * Returns the current local date-time in the default application zone.
	 *
	 * @return current local date-time
	 */
	default LocalDateTime nowLocalDateTime() {
		return LocalDateTime.ofInstant(this.nowInstant(), this.getDefaultTimeZone());
	}

	/**
	 * Returns the current local date in the default application zone.
	 *
	 * @return current local date
	 */
	default LocalDate nowLocalDate() {
		return this.nowLocalDateTime().toLocalDate();
	}

	/**
	 * Returns the current local time in the default application zone.
	 *
	 * @return current local time
	 */
	default LocalTime nowLocalTime() {
		return this.nowLocalDateTime().toLocalTime();
	}
}
