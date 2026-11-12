package com.aidigital.reportconstructor.externalservices.clerk;

/**
 * Raised by {@link ClerkClient} when the Clerk Backend API answers non-2xx, cannot be reached, or
 * returns a body that cannot be parsed.
 */
public class ClerkExternalException extends RuntimeException {

	/**
	 * Creates the exception with a message only.
	 *
	 * @param message failure description, without secrets
	 */
	public ClerkExternalException(String message) {
		super(message);
	}

	/**
	 * Creates the exception wrapping the underlying failure.
	 *
	 * @param message failure description, without secrets
	 * @param cause   underlying transport or parse failure
	 */
	public ClerkExternalException(String message, Throwable cause) {
		super(message, cause);
	}
}
