package com.aidigital.reportconstructor.observability.external;

/**
 * Metric name and tag keys shared by every outbound-call timer, so HTTP and SDK calls land in one series.
 */
public final class ExternalCallTags {

	/** Timer name for every outbound third-party call. */
	public static final String METRIC_NAME = "external.client.requests";

	/** Tag carrying the logical, low-cardinality client name (for example {@code anthropic}). */
	public static final String CLIENT = "client";

	/** Tag carrying the low-cardinality operation (HTTP method or SDK operation). */
	public static final String OPERATION = "operation";

	/** Tag carrying an {@link ExternalCallOutcome} value. */
	public static final String OUTCOME = "outcome";

	private ExternalCallTags() {
	}
}
