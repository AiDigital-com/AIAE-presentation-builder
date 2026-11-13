package com.aidigital.reportconstructor.error;

import com.aidigital.reportconstructor.service.common.time.CurrentTimeImpl;
import com.aidigital.reportconstructor.api.v1.model.ApiErrorV1;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new CurrentTimeImpl());

	@Test
	void shouldMapAppExceptionCodesToHttpStatusesTest() {
		// Given / When / Then: each ErrorReason prefix maps to its HTTP status
		assertThat(status(ErrorReason.C001)).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(status(ErrorReason.C004)).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(status(ErrorReason.C005)).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(status(ErrorReason.C006)).isEqualTo(HttpStatus.CONFLICT);
		assertThat(status(ErrorReason.C007)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(status(ErrorReason.C000)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(status(ErrorReason.C002)).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	private HttpStatus status(ErrorReason reason) {
		return (HttpStatus) handler.handleAppException(new AppException(reason, "x")).getStatusCode();
	}

	@Test
	void shouldPopulateApiErrorBodyTest() {
		// When:
		ResponseEntity<ApiErrorV1> resp = handler.handleAppException(new AppException(ErrorReason.C001, 42L));

		// Then:
		ApiErrorV1 body = resp.getBody();
		assertThat(body).isNotNull();
		assertThat(body.getCode()).isEqualTo("C001");
		assertThat(body.getMessage()).contains("Resource not found");
		assertThat(body.getTimestamp()).isNotNull();
		assertThat(body.getParameters()).isNotEmpty();
	}

	@Test
	void shouldMapValidationToBadRequestTest() {
		// When:
		ResponseEntity<ApiErrorV1> resp = handler.handleValidation(new ConstraintViolationException("bad", null));

		// Then:
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(resp.getBody().getCode()).isEqualTo("C002");
	}

	@Test
	void shouldMapAuthAndAccessDeniedTest() {
		// When / Then:
		assertThat(handler.handleAuth(new BadCredentialsException("nope")).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(handler.handleAccessDenied(new AccessDeniedException("no")).getStatusCode())
				.isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void shouldTreatAMissingCodeAsInternalErrorAndUnknownCodesAsBadRequestTest() {
		// Given:
		GlobalExceptionHandler subject = new GlobalExceptionHandler(new CurrentTimeImpl());

		// When-Then:
		assertThat(subject.statusForCode(null)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(subject.statusForCode("D042")).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldMapMissingStaticResourceToNotFoundTest() {
		// Given:
		GlobalExceptionHandler subject = new GlobalExceptionHandler(new CurrentTimeImpl());
		NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "vite.svg");

		// When:
		ResponseEntity<ApiErrorV1> resp = subject.handleNoResource(ex);

		// Then:
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(resp.getBody().getCode()).isEqualTo("C001");
		assertThat(resp.getBody().getParameters()).singleElement()
				.satisfies(p -> {
					assertThat(p.getCode()).isEqualTo("detail");
					assertThat(p.getValue()).isEqualTo(ex.getMessage());
				});
	}

	@Test
	void shouldCarryTheRequestCorrelationIdOnTheErrorBodyTest() {
		// Given: a correlation id put on the MDC by the request filter
		GlobalExceptionHandler subject = new GlobalExceptionHandler(new CurrentTimeImpl());
		MDC.put("correlationId", "corr-123");

		// When:
		ResponseEntity<ApiErrorV1> resp;
		try {
			resp = subject.handleAccessDenied(new AccessDeniedException("admins only"));
		} finally {
			MDC.remove("correlationId");
		}

		// Then:
		assertThat(resp.getBody().getCorrelationId()).isEqualTo("corr-123");
		assertThat(resp.getBody().getCode()).isEqualTo("C004");
		assertThat(resp.getBody().getParameters()).singleElement()
				.satisfies(p -> assertThat(p.getValue()).isEqualTo("admins only"));
	}

	@Test
	void shouldMapUnknownToInternalServerErrorTest() {
		// When:
		ResponseEntity<ApiErrorV1> resp = handler.handleUnknown(new RuntimeException("boom"));

		// Then:
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(resp.getBody().getCode()).isEqualTo("C000");
	}
}
