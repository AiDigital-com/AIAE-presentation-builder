package com.aidigital.reportconstructor.externalservices.google.sheets;

/**
 * Raised by {@link SheetsClient} when Google answers with an error or cannot be reached.
 */
public class SheetsExternalException extends RuntimeException {

	/** HTTP status Google answered with, or {@code null} for a transport failure. */
	private final Integer statusCode;

	/** Google's own error message, or the transport error message; may be {@code null}. */
	private final String providerMessage;

	/**
	 * Creates the exception.
	 *
	 * @param statusCode      HTTP status Google answered with, or {@code null} for a transport failure
	 * @param providerMessage Google's error message (or the transport message); may be {@code null}
	 * @param cause           underlying SDK or I/O failure
	 */
	public SheetsExternalException(Integer statusCode, String providerMessage, Throwable cause) {
		super(statusCode == null
				? "Google Sheets request failed: " + providerMessage
				: "Google Sheets answered HTTP " + statusCode + ": " + providerMessage, cause);
		this.statusCode = statusCode;
		this.providerMessage = providerMessage;
	}

	/**
	 * Returns the HTTP status Google answered with.
	 *
	 * @return the status, or {@code null} when the request never got an HTTP answer
	 */
	public Integer statusCode() {
		return statusCode;
	}

	/**
	 * Returns Google's error message, or the transport error message.
	 *
	 * @return the message; may be {@code null}
	 */
	public String providerMessage() {
		return providerMessage;
	}
}
