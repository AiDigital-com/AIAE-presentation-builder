package com.aidigital.reportconstructor.externalservices.google.sheets.impl;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Fake Google HTTP transport for Sheets tests: answers spreadsheet-metadata calls with one response and
 * {@code values} calls with another, and records every requested URL.
 */
class RoutingSheetsTransport extends MockHttpTransport {

	private final int metaStatus;
	private final String metaBody;
	private final int valuesStatus;
	private final String valuesBody;
	private final List<String> urls = new ArrayList<>();

	/**
	 * Creates the transport.
	 *
	 * @param metaStatus   HTTP status for {@code spreadsheets.get}
	 * @param metaBody     JSON body for {@code spreadsheets.get}
	 * @param valuesStatus HTTP status for {@code spreadsheets.values.get}
	 * @param valuesBody   JSON body for {@code spreadsheets.values.get}
	 */
	RoutingSheetsTransport(int metaStatus, String metaBody, int valuesStatus, String valuesBody) {
		this.metaStatus = metaStatus;
		this.metaBody = metaBody;
		this.valuesStatus = valuesStatus;
		this.valuesBody = valuesBody;
	}

	@Override
	public LowLevelHttpRequest buildRequest(String method, String url) {
		urls.add(url);
		boolean values = url.contains("/values/");
		return new MockLowLevelHttpRequest(url) {
			@Override
			public LowLevelHttpResponse execute() {
				return new MockLowLevelHttpResponse()
						.setStatusCode(values ? valuesStatus : metaStatus)
						.setContentType("application/json")
						.setContent(values ? valuesBody : metaBody);
			}
		};
	}

	/**
	 * Returns every URL requested so far, in order.
	 *
	 * @return requested URLs
	 */
	List<String> urls() {
		return urls;
	}
}
