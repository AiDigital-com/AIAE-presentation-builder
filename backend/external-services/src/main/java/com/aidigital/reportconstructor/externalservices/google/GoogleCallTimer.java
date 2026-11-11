package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.observability.external.ExternalCallTimer;
import com.google.api.client.googleapis.services.AbstractGoogleClient;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

/**
 * Executes one Google SDK request (Sheets, Slides, Drive) through {@link ExternalCallTimer}. Every Google
 * {@code execute()} goes through here, directly or via {@link GoogleRequestRetrier} (which times each
 * attempt). Tags stay low-cardinality: the client is the API ({@code google-sheets}) and the operation is
 * the SDK request type ({@code spreadsheets.values.get}), never ids, ranges or URLs.
 */
@Component
@RequiredArgsConstructor
public class GoogleCallTimer {

	/** Prefix of every Google client tag. */
	static final String CLIENT_PREFIX = "google-";

	/** Tag value used when the client or operation cannot be derived. */
	static final String UNKNOWN = "unknown";

	private static final char NESTED_CLASS_SEPARATOR = '$';
	private static final char OPERATION_SEPARATOR = '.';

	private final ExternalCallTimer externalCallTimer;

	/**
	 * Executes the request once (no retries) and records its duration and outcome.
	 *
	 * @param request built Google SDK request
	 * @param <T>     response type
	 * @return the response
	 * @throws IOException when the request fails
	 */
	public <T> T execute(AbstractGoogleClientRequest<T> request) throws IOException {
		return externalCallTimer.recordChecked(clientName(request), operationName(request), request::execute);
	}

	/**
	 * Derives the client tag from the request's API client class ({@code Sheets} becomes
	 * {@code google-sheets}).
	 *
	 * @param request Google SDK request
	 * @return low-cardinality client name
	 */
	String clientName(AbstractGoogleClientRequest<?> request) {
		AbstractGoogleClient client = request.getAbstractGoogleClient();
		String api = client == null ? UNKNOWN : client.getClass().getSimpleName().toLowerCase(Locale.ROOT);
		return CLIENT_PREFIX + api;
	}

	/**
	 * Derives the operation tag from the SDK request type: {@code Sheets$Spreadsheets$Values$Get} becomes
	 * {@code spreadsheets.values.get}.
	 *
	 * @param request Google SDK request
	 * @return low-cardinality operation name
	 */
	String operationName(AbstractGoogleClientRequest<?> request) {
		String className = request.getClass().getName();
		int nested = className.indexOf(NESTED_CLASS_SEPARATOR);
		if (nested < 0) {
			return UNKNOWN;
		}
		return className.substring(nested + 1)
				.replace(NESTED_CLASS_SEPARATOR, OPERATION_SEPARATOR)
				.toLowerCase(Locale.ROOT);
	}
}
