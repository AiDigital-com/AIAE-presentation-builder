// LogbookConfig — JSON HTTP request/response logging with required masking.
// Binds app.logbook.* properties through LogbookProperties (see application.yml baseline). Logbook's
// permissive defaults are NOT enough — this config enforces the company
// rules (Authorization/JWT masked; secret-like JSON fields masked;
// health/spec/swagger/options excluded).

package com.aidigital.reportconstructor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.BodyFilter;
import org.zalando.logbook.HeaderFilter;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Strategy;
import org.zalando.logbook.core.BodyFilters;
import org.zalando.logbook.core.Conditions;
import org.zalando.logbook.core.DefaultHttpLogWriter;
import org.zalando.logbook.core.DefaultSink;
import org.zalando.logbook.core.DefaultStrategy;
import org.zalando.logbook.core.HeaderFilters;
import org.zalando.logbook.core.WithoutBodyStrategy;
import org.zalando.logbook.json.JsonBodyFilters;
import org.zalando.logbook.json.JsonHttpLogFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Configures structured HTTP logging and masking for Logbook.
 */
@Configuration
@EnableConfigurationProperties(LogbookProperties.class)
public class LogbookConfig {

	/**
	 * Builds the Logbook instance used by the servlet filter.
	 *
	 * @param props bound masking and exclusion settings
	 * @return configured Logbook instance
	 */
	@Bean
	Logbook logbook(LogbookProperties props) {
		HeaderFilter headerFilter = HeaderFilters.replaceHeaders(
				name -> props.getHeadersToCensor().stream()
						.anyMatch(h -> h.equalsIgnoreCase(name)),
				props.getCensoredReplacement()
		);

		List<BodyFilter> bodyFilters = new ArrayList<>();
		for (String pattern : props.getJsonFieldsToCensor()) {
			bodyFilters.add(JsonBodyFilters.replaceJsonStringProperty(
					Set.of(pattern), props.getCensoredReplacement()));
		}
		BodyFilter combinedBodyFilter = bodyFilters.stream()
				.reduce(BodyFilter::merge)
				.orElse(BodyFilters.defaultValue());

		// Exclude noisy infrastructure (actuator, swagger, OpenAPI spec,
		// health probes, all OPTIONS). Each entry: path → list of methods.
		List<Predicate<HttpRequest>> exclusions = props.getExcluded().entrySet().stream()
				.flatMap(e -> e.getValue().stream()
						.map(method -> {
							Predicate<HttpRequest> pathMatches = Conditions.requestTo(e.getKey());
							Predicate<HttpRequest> methodMatches = Conditions.requestWithMethod(method);
							return pathMatches.and(methodMatches);
						}))
				.toList();

		return Logbook.builder()
				.condition(Conditions.exclude(exclusions))
				.strategy(strategy(props))
				.headerFilter(headerFilter)
				.bodyFilter(combinedBodyFilter)
				.sink(new DefaultSink(new JsonHttpLogFormatter(), new DefaultHttpLogWriter()))
				.build();
	}

	/**
	 * Picks the logging strategy: metadata-only (no bodies) unless bodies are explicitly enabled. Applies to
	 * the servlet filter and to every outbound client built by {@code PooledRestClientFactory}.
	 *
	 * @param props bound logging settings
	 * @return {@link WithoutBodyStrategy} by default, {@link DefaultStrategy} when bodies are enabled
	 */
	Strategy strategy(LogbookProperties props) {
		return props.isIncludeBodies() ? new DefaultStrategy() : new WithoutBodyStrategy();
	}

}
