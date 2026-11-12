package com.aidigital.reportconstructor.service.common.time;

import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;

/**
 * UTC implementation of the application time boundary. Production already runs the JVM in UTC
 * ({@code -Duser.timezone=UTC} in {@code backend/Dockerfile}), so this matches what the former direct
 * {@code now()} calls produced there.
 */
@Service
public class CurrentTimeImpl implements CurrentTime {

	/**
	 * Returns the current instant from the system clock.
	 *
	 * @return current instant
	 */
	@Override
	public Instant nowInstant() {
		return Instant.now();
	}

	/**
	 * Returns UTC as the application default time zone.
	 *
	 * @return UTC zone offset
	 */
	@Override
	public ZoneOffset getDefaultTimeZone() {
		return ZoneOffset.UTC;
	}
}
