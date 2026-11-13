package com.aidigital.reportconstructor.reports.controllers;

import com.aidigital.reportconstructor.api.v1.model.GoogleConnectionStatusV1;
import com.aidigital.reportconstructor.api.v1.model.SheetReadResultV1;
import com.aidigital.reportconstructor.api.v1.model.SheetSummaryResultV1;
import com.aidigital.reportconstructor.api.v1.model.SheetSummaryRowV1;
import com.aidigital.reportconstructor.mappers.reports.DataSourceApiMapper;
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
import com.aidigital.reportconstructor.service.reports.dto.GoogleConnectionStatus;
import com.aidigital.reportconstructor.service.reports.dto.SheetSummary;
import com.aidigital.reportconstructor.service.reports.dto.SheetTabRead;
import com.aidigital.reportconstructor.service.reports.ports.SheetQueryService;
import com.aidigital.reportconstructor.service.reports.services.SheetSummaryQueryService;
import com.aidigital.reportconstructor.service.reports.services.SheetTabReadService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC slice tests for {@link DataSourceController}: Google connection status and sheet reads are done as
 * the calling user.
 */
@WebMvcTest(controllers = DataSourceController.class)
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
class DataSourceControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private JwtDecoder jwtDecoder;
	@MockitoBean
	private SheetQueryService sheetQuery;
	@MockitoBean
	private SheetTabReadService sheetTabRead;
	@MockitoBean
	private SheetSummaryQueryService sheetSummaryQuery;
	@MockitoBean
	private DataSourceApiMapper mapper;

	@Test
	void shouldReportTheCallersGoogleConnectionTest() throws Exception {
		// Given:
		GoogleConnectionStatus status = new GoogleConnectionStatus(true, false, "alice@aidigital.com",
				List.of("drive"), "https://connect");
		GoogleConnectionStatusV1 dto = new GoogleConnectionStatusV1();
		dto.setConnected(true);
		dto.setMockMode(false);
		dto.setEmail("alice@aidigital.com");
		dto.setScopes(List.of("drive"));
		dto.setConnectUrl("https://connect");
		when(sheetQuery.connectionStatus("alice@aidigital.com")).thenReturn(status);
		when(mapper.toStatus(same(status))).thenReturn(dto);

		// When:
		ResultActions response = mvc.perform(get("/api/v1/google/status").with(jwt().jwt(j -> j
				.subject("user_123")
				.claim("user_id", "user_123")
				.claim("email", "Alice@AIDigital.com"))));

		// Then: the lookup uses the normalised email
		response.andExpect(status().isOk())
				.andExpect(jsonPath("$.connected").value(true))
				.andExpect(jsonPath("$.mockMode").value(false))
				.andExpect(jsonPath("$.email").value("alice@aidigital.com"))
				.andExpect(jsonPath("$.scopes[0]").value("drive"))
				.andExpect(jsonPath("$.connectUrl").value("https://connect"));
	}

	@Test
	void shouldReadTheRequestedTabAsTheCallerTest() throws Exception {
		// Given:
		SheetTabRead read = new SheetTabRead(null, List.of("Overview"));
		SheetReadResultV1 dto = new SheetReadResultV1();
		dto.setOk(false);
		dto.setError("tab_not_found");
		dto.setTab("Plan");
		dto.setTabs(List.of("Overview"));
		when(sheetTabRead.readTab("https://docs.google.com/spreadsheets/d/x", "Plan", "user_123")).thenReturn(read);
		when(mapper.toReadResult(eq("Plan"), same(read))).thenReturn(dto);

		// When:
		ResultActions response = mvc.perform(post("/api/v1/sheets/read")
				.with(jwt().jwt(j -> j
						.subject("user_123")
						.claim("user_id", "user_123")
						.claim("email", "alice@aidigital.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\":\"https://docs.google.com/spreadsheets/d/x\",\"tab\":\"Plan\"}"));

		// Then:
		response.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(false))
				.andExpect(jsonPath("$.error").value("tab_not_found"))
				.andExpect(jsonPath("$.tab").value("Plan"))
				.andExpect(jsonPath("$.tabs[0]").value("Overview"));
	}

	@Test
	void shouldReadTheWorkbookSummaryAsTheCallerTest() throws Exception {
		// Given:
		SheetSummary summary = new SheetSummary(List.of(), "the brief", null);
		SheetSummaryRowV1 row = new SheetSummaryRowV1();
		row.setTactic("Display");
		SheetSummaryResultV1 dto = new SheetSummaryResultV1();
		dto.setRows(List.of(row));
		dto.setRfpInfo("the brief");
		when(sheetSummaryQuery.readSummary("https://docs.google.com/spreadsheets/d/y", "user_123"))
				.thenReturn(summary);
		when(mapper.toSummary(same(summary))).thenReturn(dto);

		// When:
		ResultActions response = mvc.perform(post("/api/v1/sheets/summary")
				.with(jwt().jwt(j -> j
						.subject("user_123")
						.claim("user_id", "user_123")
						.claim("email", "alice@aidigital.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"sheetUrl\":\"https://docs.google.com/spreadsheets/d/y\"}"));

		// Then:
		response.andExpect(status().isOk())
				.andExpect(jsonPath("$.rows[0].tactic").value("Display"))
				.andExpect(jsonPath("$.rfpInfo").value("the brief"));
	}

	@Test
	void shouldRejectAnonymousConnectionStatusRequestTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(get("/api/v1/google/status"));

		// Then:
		response.andExpect(status().isUnauthorized());
		verifyNoInteractions(sheetQuery, sheetTabRead, sheetSummaryQuery, mapper);
	}
}
