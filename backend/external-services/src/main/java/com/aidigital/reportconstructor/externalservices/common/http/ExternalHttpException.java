package com.aidigital.reportconstructor.externalservices.common.http;

import lombok.Getter;

/**
 * Runtime exception for an HTTP-level failure of a third-party call. Carries the status code and the raw
 * response body so callers can route (retry, fall back, map to an application error) without re-parsing
 * transport details. The body is for decisions, never for logging: it may echo request content.
 */
@Getter
public class ExternalHttpException extends RuntimeException {

	/** Status code used when the failure happened before any response arrived. */
	public static final int NO_STATUS = -1;

	private static final String NO_BODY = "";

	/** HTTP status returned by the external service, or {@link #NO_STATUS}. */
	private final int statusCode;

	/** Raw response body, never {@code null}; empty when no response arrived. */
	private final String responseBody;

	/**
	 * Creates an exception from a non-2xx response.
	 *
	 * @param message      description naming the external service
	 * @param statusCode   HTTP response status
	 * @param responseBody raw response body; {@code null} is stored as empty
	 */
	public ExternalHttpException(String message, int statusCode, String responseBody) {
		super(message);
		this.statusCode = statusCode;
		this.responseBody = responseBody == null ? NO_BODY : responseBody;
	}

	/**
	 * Creates an exception wrapping an I/O or timeout failure that happened before a response arrived.
	 *
	 * @param message description naming the external service
	 * @param cause   underlying exception
	 */
	public ExternalHttpException(String message, Throwable cause) {
		super(message, cause);
		this.statusCode = NO_STATUS;
		this.responseBody = NO_BODY;
	}
}
