package com.aidigital.reportconstructor.observability.external;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Records SDK-managed outbound calls on the shared {@link ExternalCallTags#METRIC_NAME} timer. Spring HTTP
 * clients use {@link ExternalClientMetricsInterceptor} instead; both emit the same tags. Client and
 * operation names must be low-cardinality constants, never ids, URLs or user input.
 */
@Component
@RequiredArgsConstructor
public class ExternalCallTimer {

	/** Timer name, re-exported for callers and tests. */
	public static final String METRIC_NAME = ExternalCallTags.METRIC_NAME;

	private final MeterRegistry meterRegistry;

	/**
	 * Times a call that returns a value, recording {@code error} and rethrowing when it fails.
	 *
	 * @param client    low-cardinality client name
	 * @param operation low-cardinality operation name
	 * @param call      the call to time
	 * @param <T>       result type
	 * @return the call's result
	 */
	public <T> T record(String client, String operation, Supplier<T> call) {
		return recordChecked(client, operation, call::get);
	}

	/**
	 * Times a call that returns nothing, recording {@code error} and rethrowing when it fails.
	 *
	 * @param client    low-cardinality client name
	 * @param operation low-cardinality operation name
	 * @param call      the call to time
	 */
	public void record(String client, String operation, Runnable call) {
		record(client, operation, () -> {
			call.run();
			return null;
		});
	}

	/**
	 * Times a call that may throw a checked exception (for example an SDK {@code execute()} throwing
	 * {@link java.io.IOException}), recording {@code error} and rethrowing the original exception.
	 *
	 * @param client    low-cardinality client name
	 * @param operation low-cardinality operation name
	 * @param call      the call to time
	 * @param <T>       result type
	 * @param <E>       checked exception type
	 * @return the call's result
	 * @throws E when the call fails
	 */
	public <T, E extends Exception> T recordChecked(String client, String operation, ExternalCall<T, E> call)
			throws E {
		Timer.Sample sample = Timer.start(meterRegistry);
		ExternalCallOutcome outcome = ExternalCallOutcome.ERROR;
		try {
			T result = call.call();
			outcome = ExternalCallOutcome.SUCCESS;
			return result;
		} finally {
			stop(sample, client, operation, outcome);
		}
	}

	/**
	 * Stops the sample against the tagged timer.
	 *
	 * @param sample    started sample
	 * @param client    low-cardinality client name
	 * @param operation low-cardinality operation name
	 * @param outcome   call outcome
	 */
	void stop(Timer.Sample sample, String client, String operation, ExternalCallOutcome outcome) {
		sample.stop(Timer.builder(METRIC_NAME)
				.tag(ExternalCallTags.CLIENT, client)
				.tag(ExternalCallTags.OPERATION, operation)
				.tag(ExternalCallTags.OUTCOME, outcome.getTagValue())
				.register(meterRegistry));
	}
}
