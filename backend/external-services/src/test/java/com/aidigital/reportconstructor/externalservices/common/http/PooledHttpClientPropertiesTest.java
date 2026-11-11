package com.aidigital.reportconstructor.externalservices.common.http;

import com.aidigital.reportconstructor.externalservices.common.http.config.PooledHttpClientProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PooledHttpClientPropertiesTest {

	@Test
	void shouldDefaultToBoundedPoolsAndExplicitTimeoutsTest() {
		// Given:

		// When:
		PooledHttpClientProperties props = new PooledHttpClientProperties();

		// Then:
		assertThat(props.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
		assertThat(props.getResponseTimeout()).isEqualTo(Duration.ofSeconds(30));
		assertThat(props.getConnectionRequestTimeout()).isEqualTo(Duration.ofSeconds(5));
		assertThat(props.getMaxTotalConnections()).isEqualTo(50);
		assertThat(props.getMaxConnectionsPerRoute()).isEqualTo(10);
		assertThat(props.getKeepAliveDuration()).isEqualTo(Duration.ofSeconds(60));
		assertThat(props.getIdleEvictionDuration()).isEqualTo(Duration.ofSeconds(30));
	}

	@Test
	void shouldRejectMissingTimeoutsAndNonPositivePoolSizesTest() {
		// Given:
		PooledHttpClientProperties props = new PooledHttpClientProperties();
		props.setResponseTimeout(null);
		props.setMaxTotalConnections(0);
		props.setMaxConnectionsPerRoute(-1);

		// When:
		Set<ConstraintViolation<PooledHttpClientProperties>> violations;
		try (ValidatorFactory validators = Validation.buildDefaultValidatorFactory()) {
			Validator validator = validators.getValidator();
			violations = validator.validate(props);
		}

		// Then:
		assertThat(violations)
				.extracting(violation -> violation.getPropertyPath().toString())
				.containsExactlyInAnyOrder("responseTimeout", "maxTotalConnections", "maxConnectionsPerRoute");
	}
}
