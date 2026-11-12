package com.aidigital.reportconstructor.observability.external;

/**
 * An outbound SDK call that may throw a checked exception, timed by {@link ExternalCallTimer}.
 *
 * @param <T> result type
 * @param <E> checked exception type the call may throw
 */
@FunctionalInterface
public interface ExternalCall<T, E extends Exception> {

	/**
	 * Performs the call.
	 *
	 * @return the call's result
	 * @throws E when the underlying SDK call fails
	 */
	T call() throws E;
}
