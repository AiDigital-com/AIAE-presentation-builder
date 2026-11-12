package com.aidigital.reportconstructor.externalservices.google;

/**
 * Raised by the Google integration when a Slides, Sheets or Drive operation fails. The message is written
 * for the person who ran the report; the bridge passes it through unchanged as the job's error.
 */
public class GoogleApiException extends RuntimeException {

	/**
	 * Creates the exception with a user-facing message.
	 *
	 * @param message failure description, shown as the job error
	 */
	public GoogleApiException(String message) {
		super(message);
	}
}
