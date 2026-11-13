package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fake Google HTTP transport for the Sheets/Drive provider tests: answers each request with the response
 * of the first registered route whose URL fragment the request URL contains, and records every request's
 * method, URL and (decoded) body in order, so a test can assert exactly what was sent to Google.
 */
class RecordingGoogleTransport extends MockHttpTransport {

	private final List<String> routeFragments = new ArrayList<>();
	private final List<Integer> routeStatuses = new ArrayList<>();
	private final List<String> routeBodies = new ArrayList<>();
	private final List<String> methods = new ArrayList<>();
	private final List<String> urls = new ArrayList<>();
	private final List<String> bodies = new ArrayList<>();

	/**
	 * Registers a response for every request whose URL contains the given fragment. Routes are matched in
	 * registration order; an unmatched request is answered with HTTP 418 so a test never passes silently.
	 *
	 * @param urlFragment substring of the request URL to match
	 * @param status      HTTP status to answer with
	 * @param body        JSON body to answer with
	 * @return this transport, for chaining
	 */
	RecordingGoogleTransport respond(String urlFragment, int status, String body) {
		routeFragments.add(urlFragment);
		routeStatuses.add(status);
		routeBodies.add(body);
		return this;
	}

	@Override
	public LowLevelHttpRequest buildRequest(String method, String url) {
		int route = routeFor(url);
		return new MockLowLevelHttpRequest(url) {
			@Override
			public LowLevelHttpResponse execute() throws IOException {
				methods.add(method);
				urls.add(url);
				bodies.add(getStreamingContent() == null ? "" : getContentAsString());
				return new MockLowLevelHttpResponse()
						.setStatusCode(route < 0 ? 418 : routeStatuses.get(route))
						.setContentType("application/json")
						.setContent(route < 0 ? "{}" : routeBodies.get(route));
			}
		};
	}

	/**
	 * Finds the first route whose fragment the URL contains.
	 *
	 * @param url request URL
	 * @return the route index, or {@code -1} when none matches
	 */
	int routeFor(String url) {
		for (int i = 0; i < routeFragments.size(); i++) {
			if (url.contains(routeFragments.get(i))) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Returns the HTTP method of every executed request, in order.
	 *
	 * @return request methods
	 */
	List<String> methods() {
		return methods;
	}

	/**
	 * Returns the URL of every executed request, in order.
	 *
	 * @return request URLs
	 */
	List<String> urls() {
		return urls;
	}

	/**
	 * Returns the decoded body of every executed request, in order ({@code ""} for body-less requests).
	 *
	 * @return request bodies
	 */
	List<String> bodies() {
		return bodies;
	}
}
