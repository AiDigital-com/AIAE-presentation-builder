package com.aidigital.reportconstructor.observability.external;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Bounded set of {@code outcome} tag values recorded on the {@code external.client.requests} timer.
 */
@Getter
@RequiredArgsConstructor
public enum ExternalCallOutcome {

	/** The call completed; for HTTP, a non-error status. */
	SUCCESS("success"),

	/** An SDK-managed call threw. */
	ERROR("error"),

	/** The HTTP call returned a 4xx status. */
	CLIENT_ERROR("client_error"),

	/** The HTTP call returned a 5xx status. */
	SERVER_ERROR("server_error"),

	/** The HTTP call failed with an I/O error before a response arrived. */
	IO_ERROR("io_error"),

	/** The HTTP call failed with an unexpected runtime exception. */
	RUNTIME_ERROR("runtime_error");

	/** Value written to the {@code outcome} tag. */
	private final String tagValue;
}
