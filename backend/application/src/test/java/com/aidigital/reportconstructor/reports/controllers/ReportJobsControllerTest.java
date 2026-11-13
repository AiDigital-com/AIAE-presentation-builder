package com.aidigital.reportconstructor.reports.controllers;

import com.aidigital.reportconstructor.api.v1.model.GenerateRequestV1;
import com.aidigital.reportconstructor.api.v1.model.GenerationTargetV1;
import com.aidigital.reportconstructor.api.v1.model.ReportJobCreatedV1;
import com.aidigital.reportconstructor.api.v1.model.ReportJobStatusV1;
import com.aidigital.reportconstructor.api.v1.model.ReportJobV1;
import com.aidigital.reportconstructor.api.v1.model.ReportTypeV1;
import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.mappers.reports.PlaceholdersApiMapper;
import com.aidigital.reportconstructor.mappers.reports.ReportJobsApiMapper;
import com.aidigital.reportconstructor.security.AppUserFactory;
import com.aidigital.reportconstructor.security.AuthProperties;
import com.aidigital.reportconstructor.security.AuthorizedPartyMatcher;
import com.aidigital.reportconstructor.security.ClerkJwtClaimsValidator;
import com.aidigital.reportconstructor.security.ClerkPublishableKeyDecoder;
import com.aidigital.reportconstructor.security.CompanyEmailDomainAuthorizationManager;
import com.aidigital.reportconstructor.security.CorsOriginPatternNormalizer;
import com.aidigital.reportconstructor.security.SecurityConfig;
import com.aidigital.reportconstructor.security.SecurityProperties;
import com.aidigital.reportconstructor.service.common.time.CurrentTimeImpl;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.GenerationTarget;
import com.aidigital.reportconstructor.service.reports.dto.ProgressView;
import com.aidigital.reportconstructor.service.reports.services.ReportGenerationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC slice tests for {@link ReportJobsController}: job creation answers 202 with the new id, and polling
 * is scoped to the caller.
 */
@WebMvcTest(controllers = ReportJobsController.class)
@EnableConfigurationProperties({AuthProperties.class, SecurityProperties.class})
@Import({
		SecurityConfig.class,
		AppUserFactory.class,
		AuthorizedPartyMatcher.class,
		ClerkJwtClaimsValidator.class,
		ClerkPublishableKeyDecoder.class,
		CompanyEmailDomainAuthorizationManager.class,
		CorsOriginPatternNormalizer.class,
		CurrentTimeImpl.class
})
class ReportJobsControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private JwtDecoder jwtDecoder;
	@MockitoBean
	private ReportGenerationService reportGeneration;
	@MockitoBean
	private ReportJobsApiMapper mapper;
	@MockitoBean
	private PlaceholdersApiMapper payloadMapper;

	@Test
	void shouldStartAJobForTheCallerAndAnswerAcceptedTest() throws Exception {
		// Given: a sheet build request and the payload/target the mapper derives from it
		GeneratePayload payload = new GeneratePayload("Q3 brief", "EOC", "1.2M", null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null);
		ReportJobEntity job = new ReportJobEntity();
		job.setId(42L);
		ReportJobCreatedV1 created = new ReportJobCreatedV1();
		created.setJobId(42L);
		when(payloadMapper.toPayload(argThat((GenerateRequestV1 r) -> "Q3 brief".equals(r.getBrief()))))
				.thenReturn(payload);
		when(payloadMapper.toTarget(GenerationTargetV1.SHEET)).thenReturn(GenerationTarget.SHEET);
		when(reportGeneration.start("user_123", "user_123", "alice@aidigital.com", payload, GenerationTarget.SHEET,
				"https://plan", "https://elevate")).thenReturn(job);
		when(mapper.toCreated(same(job))).thenReturn(created);

		// When:
		ResultActions response = mvc.perform(post("/api/v1/report-jobs")
				.with(jwt().jwt(j -> j
						.subject("user_123")
						.claim("user_id", "user_123")
						.claim("email", "alice@aidigital.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"brief\":\"Q3 brief\",\"reportType\":\"EOC\",\"marketVolume\":\"1.2M\","
						+ "\"target\":\"SHEET\",\"mediaPlanUrl\":\"https://plan\","
						+ "\"elevateUrl\":\"https://elevate\"}"));

		// Then:
		response.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.jobId").value(42));
		ArgumentCaptor<GenerateRequestV1> body = ArgumentCaptor.forClass(GenerateRequestV1.class);
		verify(payloadMapper).toPayload(body.capture());
		assertThat(body.getValue().getReportType()).isEqualTo(ReportTypeV1.EOC);
		assertThat(body.getValue().getMarketVolume()).isEqualTo("1.2M");
		assertThat(body.getValue().getTarget()).isEqualTo(GenerationTargetV1.SHEET);
	}

	@Test
	void shouldReturnTheCallersJobProgressTest() throws Exception {
		// Given:
		ProgressView view = new ProgressView(3, 7, "Writing copy", "running", "", "", List.of());
		ReportJobV1 dto = new ReportJobV1();
		dto.setJobId(42L);
		dto.setStatus(ReportJobStatusV1.RUNNING);
		dto.setStep(3);
		dto.setTotal(7);
		dto.setLabel("Writing copy");
		when(reportGeneration.progress("user_123", 42L)).thenReturn(view);
		when(mapper.toJob(42L, view)).thenReturn(dto);

		// When:
		ResultActions response = mvc.perform(get("/api/v1/report-jobs/42").with(jwt().jwt(j -> j
				.subject("user_123")
				.claim("user_id", "user_123")
				.claim("email", "alice@aidigital.com"))));

		// Then:
		response.andExpect(status().isOk())
				.andExpect(jsonPath("$.jobId").value(42))
				.andExpect(jsonPath("$.status").value("running"))
				.andExpect(jsonPath("$.step").value(3))
				.andExpect(jsonPath("$.total").value(7))
				.andExpect(jsonPath("$.label").value("Writing copy"));
	}

	@Test
	void shouldForbidPollingByAForeignDomainCallerTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(get("/api/v1/report-jobs/42").with(jwt().jwt(j -> j
				.subject("user_999")
				.claim("user_id", "user_999")
				.claim("email", "mallory@attacker.example"))));

		// Then:
		response.andExpect(status().isForbidden());
		verifyNoInteractions(reportGeneration, mapper, payloadMapper);
	}
}
