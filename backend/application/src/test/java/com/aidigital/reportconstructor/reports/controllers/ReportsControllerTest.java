package com.aidigital.reportconstructor.reports.controllers;

import com.aidigital.reportconstructor.api.v1.model.ReportListV1;
import com.aidigital.reportconstructor.api.v1.model.ReportResumeV1;
import com.aidigital.reportconstructor.api.v1.model.ReportSummaryV1;
import com.aidigital.reportconstructor.api.v1.model.ReportTypeV1;
import com.aidigital.reportconstructor.api.v1.model.TemplateVariantV1;
import com.aidigital.reportconstructor.mappers.reports.ReportsApiMapper;
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
import com.aidigital.reportconstructor.service.reports.dto.ReportHistory;
import com.aidigital.reportconstructor.service.reports.dto.ReportResume;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;
import com.aidigital.reportconstructor.service.reports.dto.TemplateVariant;
import com.aidigital.reportconstructor.service.reports.services.ReportHistoryService;
import com.aidigital.reportconstructor.service.reports.services.SheetAdoptionService;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC slice tests for {@link ReportsController}: routing, status codes, payloads and the caller-scoped
 * arguments forwarded to the history and adoption services.
 */
@WebMvcTest(controllers = ReportsController.class)
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
class ReportsControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private JwtDecoder jwtDecoder;
	@MockitoBean
	private ReportHistoryService reportHistory;
	@MockitoBean
	private SheetAdoptionService sheetAdoption;
	@MockitoBean
	private ReportsApiMapper mapper;

	@Test
	void shouldListTheCallersOwnReportsTest() throws Exception {
		// Given: the caller's history and its mapped envelope
		ReportHistory history = new ReportHistory(List.of(new ReportSummary(7L, "EOC", "done", "EOC report",
				null, null, "https://sheet/7", null, null, null, null, null, 0, 0, 0, 0d, false)));
		ReportSummaryV1 row = new ReportSummaryV1();
		row.setJobId(7L);
		row.setTitle("EOC report");
		ReportListV1 dto = new ReportListV1();
		dto.setTotal(1);
		dto.setReports(List.of(row));
		when(reportHistory.historyForOwner("user_123")).thenReturn(history);
		when(mapper.toReportList(same(history))).thenReturn(dto);

		// When:
		ResultActions response = mvc.perform(get("/api/v1/reports").with(jwt().jwt(j -> j
				.subject("user_123")
				.claim("user_id", "user_123")
				.claim("email", "alice@aidigital.com"))));

		// Then:
		response.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.reports[0].jobId").value(7))
				.andExpect(jsonPath("$.reports[0].title").value("EOC report"));
	}

	@Test
	void shouldReturnTheResumableDraftOfTheCallerTest() throws Exception {
		// Given:
		ReportResume resume = new ReportResume(7L, "https://sheet/7", null, null, null);
		ReportResumeV1 dto = new ReportResumeV1();
		dto.setJobId(7L);
		dto.setSheetUrl("https://sheet/7");
		dto.setReportType(ReportTypeV1.EOC);
		when(reportHistory.resumeForOwner("user_123", 7L)).thenReturn(resume);
		when(mapper.toResume(same(resume))).thenReturn(dto);

		// When:
		ResultActions response = mvc.perform(get("/api/v1/reports/7/resume").with(jwt().jwt(j -> j
				.subject("user_123")
				.claim("user_id", "user_123")
				.claim("email", "alice@aidigital.com"))));

		// Then:
		response.andExpect(status().isOk())
				.andExpect(jsonPath("$.jobId").value(7))
				.andExpect(jsonPath("$.sheetUrl").value("https://sheet/7"))
				.andExpect(jsonPath("$.reportType").value("EOC"));
	}

	@Test
	void shouldAdoptTheSheetForTheCallerWithTheRequestedDesignTest() throws Exception {
		// Given: the request names an MPO deck for an EOM workbook
		ReportResume draft = new ReportResume(11L, "https://docs.google.com/spreadsheets/d/abc", null, null, null);
		ReportResumeV1 dto = new ReportResumeV1();
		dto.setJobId(11L);
		dto.setTemplateVariant(TemplateVariantV1.MPO);
		when(mapper.toTemplateVariant(TemplateVariantV1.MPO)).thenReturn(TemplateVariant.MPO);
		when(sheetAdoption.adopt("user_123", "user_123", "alice@aidigital.com",
				"https://docs.google.com/spreadsheets/d/abc", "EOM", TemplateVariant.MPO)).thenReturn(draft);
		when(mapper.toResume(same(draft))).thenReturn(dto);

		// When:
		ResultActions response = mvc.perform(post("/api/v1/reports/adopt-sheet")
				.with(jwt().jwt(j -> j
						.subject("user_123")
						.claim("user_id", "user_123")
						.claim("email", "Alice@AIDigital.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"sheetUrl\":\"https://docs.google.com/spreadsheets/d/abc\","
						+ "\"reportType\":\"EOM\",\"templateVariant\":\"MPO\"}"));

		// Then: the normalised caller email and the wire report-type code reach the service
		response.andExpect(status().isOk())
				.andExpect(jsonPath("$.jobId").value(11))
				.andExpect(jsonPath("$.templateVariant").value("MPO"));
	}

	@Test
	void shouldDismissTheCallersReportWithNoContentTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(post("/api/v1/reports/7/dismiss").with(jwt().jwt(j -> j
				.subject("user_123")
				.claim("user_id", "user_123")
				.claim("email", "alice@aidigital.com"))));

		// Then:
		response.andExpect(status().isNoContent())
				.andExpect(content().string(""));
		verify(reportHistory).dismissForOwner("user_123", 7L);
	}

	@Test
	void shouldRejectAnonymousHistoryRequestTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(get("/api/v1/reports"));

		// Then:
		response.andExpect(status().isUnauthorized());
		verifyNoInteractions(reportHistory, sheetAdoption, mapper);
	}
}
