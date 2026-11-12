package com.aidigital.reportconstructor.api;

import com.aidigital.reportconstructor.admin.controllers.AdminController;
import com.aidigital.reportconstructor.mappers.admin.AdminManagementApiMapper;
import com.aidigital.reportconstructor.mappers.admin.AdminReportsApiMapper;
import com.aidigital.reportconstructor.mappers.admin.AdminStatsApiMapper;
import com.aidigital.reportconstructor.mappers.reports.DataSourceApiMapper;
import com.aidigital.reportconstructor.mappers.reports.LineItemMatchApiMapper;
import com.aidigital.reportconstructor.mappers.reports.PlaceholdersApiMapper;
import com.aidigital.reportconstructor.mappers.reports.ReportJobsApiMapper;
import com.aidigital.reportconstructor.mappers.reports.ReportsApiMapper;
import com.aidigital.reportconstructor.reports.controllers.DataSourceController;
import com.aidigital.reportconstructor.reports.controllers.LineItemMatchingController;
import com.aidigital.reportconstructor.reports.controllers.PlaceholdersController;
import com.aidigital.reportconstructor.reports.controllers.ReportJobsController;
import com.aidigital.reportconstructor.reports.controllers.ReportsController;
import com.aidigital.reportconstructor.security.AppUserFactory;
import com.aidigital.reportconstructor.security.AuthProperties;
import com.aidigital.reportconstructor.security.AuthorizedPartyMatcher;
import com.aidigital.reportconstructor.security.ClerkJwtClaimsValidator;
import com.aidigital.reportconstructor.security.ClerkPublishableKeyDecoder;
import com.aidigital.reportconstructor.security.CompanyEmailDomainAuthorizationManager;
import com.aidigital.reportconstructor.security.CorsOriginPatternNormalizer;
import com.aidigital.reportconstructor.security.SecurityConfig;
import com.aidigital.reportconstructor.security.SecurityProperties;
import com.aidigital.reportconstructor.service.admin.services.AdminFailuresService;
import com.aidigital.reportconstructor.service.admin.services.AdminManagementService;
import com.aidigital.reportconstructor.service.admin.services.AdminReportsService;
import com.aidigital.reportconstructor.service.admin.services.AdminStatsService;
import com.aidigital.reportconstructor.service.common.time.CurrentTimeImpl;
import com.aidigital.reportconstructor.service.reports.ports.SheetQueryService;
import com.aidigital.reportconstructor.service.reports.services.LineItemMatchViewService;
import com.aidigital.reportconstructor.service.reports.services.PlaceholderResolverService;
import com.aidigital.reportconstructor.service.reports.services.ReportGenerationService;
import com.aidigital.reportconstructor.service.reports.services.ReportHistoryService;
import com.aidigital.reportconstructor.service.reports.services.SheetAdoptionService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One negative test per constrained API operation: a request that breaks the OpenAPI contract is
 * rejected with 400 and the {@code C002} error body before any controller or service code runs.
 * Services and mappers are mocks with no stubbing — reaching them would mean validation let the
 * request through.
 */
@WebMvcTest(controllers = {
		DataSourceController.class,
		LineItemMatchingController.class,
		PlaceholdersController.class,
		ReportJobsController.class,
		ReportsController.class,
		AdminController.class
})
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
class ApiRequestValidationTest {

	/** A 324-character address, over the 320-character email bound. */
	private static final String TOO_LONG_EMAIL = "a".repeat(310) + "@aidigital.com";

	/** A sheet cell one character over the 50,000-character cell bound. */
	private static final String OVERLONG_CELL = "x".repeat(50_001);

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
	private DataSourceApiMapper dataSourceMapper;
	@MockitoBean
	private LineItemMatchViewService lineItemMatch;
	@MockitoBean
	private LineItemMatchApiMapper lineItemMatchMapper;
	@MockitoBean
	private PlaceholderResolverService placeholders;
	@MockitoBean
	private PlaceholdersApiMapper placeholdersMapper;
	@MockitoBean
	private ReportGenerationService reportGeneration;
	@MockitoBean
	private ReportJobsApiMapper reportJobsMapper;
	@MockitoBean
	private ReportHistoryService reportHistory;
	@MockitoBean
	private SheetAdoptionService sheetAdoption;
	@MockitoBean
	private ReportsApiMapper reportsMapper;
	@MockitoBean
	private AdminStatsService adminStats;
	@MockitoBean
	private AdminReportsService adminReports;
	@MockitoBean
	private AdminManagementService adminManagement;
	@MockitoBean
	private AdminFailuresService adminFailures;
	@MockitoBean
	private AdminStatsApiMapper adminStatsMapper;
	@MockitoBean
	private AdminManagementApiMapper adminManagementMapper;
	@MockitoBean
	private AdminReportsApiMapper adminReportsMapper;

	@Test
	void shouldRejectSheetReadWithEmptyTabTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(json(post("/api/v1/sheets/read"),
				"{\"url\":\"https://docs.google.com/spreadsheets/d/x\",\"tab\":\"\"}"));

		// Then:
		response.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void shouldRejectSheetSummaryWithEmptyUrlTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(json(post("/api/v1/sheets/summary"), "{\"sheetUrl\":\"\"}"));

		// Then:
		response.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void shouldRejectLineItemMatchWithOverlongCellTest() throws Exception {
		// Given: one cell over Google's 50,000-character cell limit
		String body = "{\"bqRows\":[],\"planRows\":[[\"" + OVERLONG_CELL + "\"]]}";

		// When:
		ResultActions response = mvc.perform(json(post("/api/v1/line-items/match"), body));

		// Then:
		response.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void shouldRejectPlaceholderPreviewWithUnknownReportTypeTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(json(post("/api/v1/placeholders/preview"),
				"{\"brief\":\"Brief\",\"reportType\":\"QBR\"}"));

		// Then:
		response.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void shouldRejectDateRangeWithOverlongCellTest() throws Exception {
		// Given: one cell over Google's 50,000-character cell limit
		String body = "{\"adjRows\":[[\"" + OVERLONG_CELL + "\"]]}";

		// When:
		ResultActions response = mvc.perform(json(post("/api/v1/placeholders/date-range"), body));

		// Then:
		response.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void shouldRejectReportJobWithOverlongMarketVolumeTest() throws Exception {
		// Given:
		String body = "{\"brief\":\"Brief\",\"reportType\":\"EOC\",\"marketVolume\":\"" + "9".repeat(101) + "\"}";

		// When:
		ResultActions response = mvc.perform(json(post("/api/v1/report-jobs"), body));

		// Then:
		response.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void shouldRejectReportJobPollWithNonNumericIdTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(get("/api/v1/report-jobs/abc").with(caller()));

		// Then:
		response.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void shouldRejectReportResumeWithNonNumericIdTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(get("/api/v1/reports/abc/resume").with(caller()));

		// Then:
		response.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void shouldRejectAdoptSheetWithEmptyUrlTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(json(post("/api/v1/reports/adopt-sheet"),
				"{\"sheetUrl\":\"\",\"reportType\":\"EOC\"}"));

		// Then:
		response.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void shouldRejectDismissWithNonNumericIdTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(post("/api/v1/reports/abc/dismiss").with(caller()));

		// Then:
		response.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void shouldRejectAdminStatsWithMalformedDateTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(get("/api/v1/admin/stats").param("from", "yesterday").with(caller()));

		// Then:
		response.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void shouldRejectResolveFailureWithNonNumericIdTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(delete("/api/v1/admin/failures/abc").with(caller()));

		// Then:
		response.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void shouldRejectAdminReportsPageAboveTheSizeCapTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(get("/api/v1/admin/reports").param("size", "500").with(caller()));

		// Then:
		response.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void shouldRejectAdminReportsWithUnknownSortColumnTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(get("/api/v1/admin/reports").param("sort", "password").with(caller()));

		// Then:
		response.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void shouldRejectAddAdminWithMalformedEmailTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(json(post("/api/v1/admin/admins"), "{\"email\":\"not-an-email\"}"));

		// Then:
		response.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void shouldRejectRemoveAdminWithOverlongEmailTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(delete("/api/v1/admin/admins/" + TOO_LONG_EMAIL).with(caller()));

		// Then:
		response.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C002"));
	}

	/**
	 * An authenticated company caller, so security passes and validation is what decides.
	 *
	 * @return the JWT request post-processor
	 */
	org.springframework.test.web.servlet.request.RequestPostProcessor caller() {
		return jwt().jwt(j -> j
				.subject("user_123")
				.claim("user_id", "user_123")
				.claim("email", "alice@aidigital.com")
				.claim("full_name", "Alice Example"));
	}

	/**
	 * Adds the caller and a JSON body to a request.
	 *
	 * @param request the request to complete
	 * @param body    the JSON body
	 * @return the completed request
	 */
	MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.with(caller()).contentType(MediaType.APPLICATION_JSON).content(body);
	}
}
