package com.aidigital.reportconstructor.admin.controllers;

import com.aidigital.reportconstructor.mappers.admin.AdminManagementApiMapperImpl;
import com.aidigital.reportconstructor.mappers.admin.AdminReportsApiMapperImpl;
import com.aidigital.reportconstructor.mappers.admin.AdminStatsApiMapperImpl;
import com.aidigital.reportconstructor.mappers.reports.ReportsApiMapperImpl;
import com.aidigital.reportconstructor.security.AppUserFactory;
import com.aidigital.reportconstructor.security.AuthProperties;
import com.aidigital.reportconstructor.security.AuthorizedPartyMatcher;
import com.aidigital.reportconstructor.security.ClerkJwtClaimsValidator;
import com.aidigital.reportconstructor.security.ClerkPublishableKeyDecoder;
import com.aidigital.reportconstructor.security.CompanyEmailDomainAuthorizationManager;
import com.aidigital.reportconstructor.security.CorsOriginPatternNormalizer;
import com.aidigital.reportconstructor.security.SecurityConfig;
import com.aidigital.reportconstructor.security.SecurityProperties;
import com.aidigital.reportconstructor.service.admin.dto.AdminEntry;
import com.aidigital.reportconstructor.service.admin.dto.AdminList;
import com.aidigital.reportconstructor.service.admin.dto.AdminRangeView;
import com.aidigital.reportconstructor.service.admin.dto.AdminReportPage;
import com.aidigital.reportconstructor.service.admin.dto.AdminStats;
import com.aidigital.reportconstructor.service.admin.dto.AdminTotals;
import com.aidigital.reportconstructor.service.admin.enums.AdminPeriodUnit;
import com.aidigital.reportconstructor.service.admin.services.AdminFailuresService;
import com.aidigital.reportconstructor.service.admin.services.AdminManagementService;
import com.aidigital.reportconstructor.service.admin.services.AdminReportsService;
import com.aidigital.reportconstructor.service.admin.services.AdminStatsService;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.common.time.CurrentTimeImpl;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice for the admin dashboard endpoints: services are mocked, the real MapStruct
 * mappers render the payload, and every call is checked for the caller email it forwards.
 */
@WebMvcTest(controllers = AdminController.class)
@EnableConfigurationProperties({AuthProperties.class, SecurityProperties.class})
@Import({
		SecurityConfig.class,
		AppUserFactory.class,
		AuthorizedPartyMatcher.class,
		ClerkJwtClaimsValidator.class,
		ClerkPublishableKeyDecoder.class,
		CompanyEmailDomainAuthorizationManager.class,
		CorsOriginPatternNormalizer.class,
		CurrentTimeImpl.class,
		AdminStatsApiMapperImpl.class,
		AdminManagementApiMapperImpl.class,
		AdminReportsApiMapperImpl.class,
		ReportsApiMapperImpl.class
})
class AdminControllerTest {

	/** Email carried by the authenticated caller's JWT. */
	private static final String CALLER_EMAIL = "alice@aidigital.com";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private JwtDecoder jwtDecoder;
	@MockitoBean
	private AdminStatsService adminStats;
	@MockitoBean
	private AdminReportsService adminReports;
	@MockitoBean
	private AdminManagementService adminManagement;
	@MockitoBean
	private AdminFailuresService adminFailures;

	@Test
	void shouldReturnStatsForTheRequestedWindowTest() throws Exception {
		// Given:
		AdminStats stats = new AdminStats(LocalDateTime.of(2026, 8, 6, 10, 15), null,
				new AdminRangeView(LocalDate.of(2026, 7, 8), LocalDate.of(2026, 8, 6),
						AdminPeriodUnit.WEEK.getCode()),
				new AdminTotals(12, 5, 2, 1, 3), null, List.of(), List.of(), List.of(),
				AdminPeriodUnit.DAY.getCode(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
		when(adminStats.statsFor(eq(CALLER_EMAIL), eq(LocalDate.of(2026, 7, 8)), eq(LocalDate.of(2026, 8, 6))))
				.thenReturn(stats);

		// When:
		ResultActions response = mvc.perform(get("/api/v1/admin/stats")
				.param("from", "2026-07-08").param("to", "2026-08-06").with(caller()));

		// Then:
		response.andExpect(status().isOk())
				.andExpect(jsonPath("$.updatedAt").value("2026-08-06T10:15:00"))
				.andExpect(jsonPath("$.range.from").value("2026-07-08"))
				.andExpect(jsonPath("$.range.to").value("2026-08-06"))
				.andExpect(jsonPath("$.range.suggestedUnit").value("week"))
				.andExpect(jsonPath("$.seriesUnit").value("day"))
				.andExpect(jsonPath("$.totals.reports").value(12))
				.andExpect(jsonPath("$.totals.activeUsers").value(5))
				.andExpect(jsonPath("$.totals.failed").value(3));
	}

	@Test
	void shouldForwardAbsentStatsDatesAsNullTest() throws Exception {
		// Given:
		AdminStats stats = new AdminStats(LocalDateTime.of(2026, 8, 6, 10, 15), null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null);
		when(adminStats.statsFor(eq(CALLER_EMAIL), eq(null), eq(null))).thenReturn(stats);

		// When:
		ResultActions response = mvc.perform(get("/api/v1/admin/stats").with(caller()));

		// Then:
		response.andExpect(status().isOk())
				.andExpect(jsonPath("$.updatedAt").value("2026-08-06T10:15:00"));
		verify(adminStats).statsFor(eq(CALLER_EMAIL), eq(null), eq(null));
	}

	@Test
	void shouldReturnForbiddenWhenTheCallerIsNotAnAdminTest() throws Exception {
		// Given:
		when(adminStats.statsFor(eq(CALLER_EMAIL), eq(null), eq(null)))
				.thenThrow(new AppException(ErrorReason.C004, "Admin access required"));

		// When:
		ResultActions response = mvc.perform(get("/api/v1/admin/stats").with(caller()));

		// Then:
		response.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("C004"));
	}

	@Test
	void shouldRejectUnauthenticatedStatsRequestTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(get("/api/v1/admin/stats"));

		// Then:
		response.andExpect(status().isUnauthorized());
		verifyNoInteractions(adminStats);
	}

	@Test
	void shouldClearAllFailuresTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(delete("/api/v1/admin/failures").with(caller()));

		// Then:
		response.andExpect(status().isNoContent())
				.andExpect(content().string(""));
		verify(adminFailures).clearFailures(eq(CALLER_EMAIL));
	}

	@Test
	void shouldResolveOneFailureByJobIdTest() throws Exception {
		// When:
		ResultActions response = mvc.perform(delete("/api/v1/admin/failures/42").with(caller()));

		// Then:
		response.andExpect(status().isNoContent());
		verify(adminFailures).resolveFailure(eq(CALLER_EMAIL), eq(42L));
	}

	@Test
	void shouldReturnNotFoundWhenTheFailureDoesNotExistTest() throws Exception {
		// Given:
		doThrow(new AppException(ErrorReason.C001, "No such job"))
				.when(adminFailures).resolveFailure(eq(CALLER_EMAIL), eq(404L));

		// When:
		ResultActions response = mvc.perform(delete("/api/v1/admin/failures/404").with(caller()));

		// Then:
		response.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("C001"));
	}

	@Test
	void shouldListAPageOfReportsWithTheRequestedOrderTest() throws Exception {
		// Given:
		ReportSummary row = new ReportSummary(5L, "EOM", "done", "July EOM", LocalDateTime.of(2026, 8, 1, 9, 0),
				"https://slides/5", null, "deck.pptx", null, null, "bob@aidigital.com", "Bob",
				10L, 20L, 30L, 0.5, false);
		AdminReportPage page = new AdminReportPage(120L, 2, 25, true, List.of(row));
		when(adminReports.allReports(eq(CALLER_EMAIL), eq(2), eq(25), eq("tokens"), eq("asc"))).thenReturn(page);

		// When: the documented wire values the frontend sends (ApiEnumParameterConfig binds them)
		ResultActions response = mvc.perform(get("/api/v1/admin/reports")
				.param("page", "2").param("size", "25").param("sort", "tokens").param("dir", "asc")
				.with(caller()));

		// Then:
		response.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(120))
				.andExpect(jsonPath("$.page").value(2))
				.andExpect(jsonPath("$.size").value(25))
				.andExpect(jsonPath("$.hasMore").value(true))
				.andExpect(jsonPath("$.reports.length()").value(1))
				.andExpect(jsonPath("$.reports[0].jobId").value(5))
				.andExpect(jsonPath("$.reports[0].status").value("done"))
				.andExpect(jsonPath("$.reports[0].ownerEmail").value("bob@aidigital.com"))
				.andExpect(jsonPath("$.reports[0].totalTokens").value(30));
	}

	@Test
	void shouldRejectAnUndocumentedSortValueWithoutCallingTheServiceTest() throws Exception {
		// When: a column the contract does not list
		ResultActions response = mvc.perform(get("/api/v1/admin/reports")
				.param("sort", "revenue").with(caller()));

		// Then:
		response.andExpect(status().isBadRequest());
		verifyNoInteractions(adminReports);
	}

	@Test
	void shouldListReportsWithDefaultPagingAndNoOrderTest() throws Exception {
		// Given:
		AdminReportPage page = new AdminReportPage(0L, 0, 50, false, List.of());
		when(adminReports.allReports(eq(CALLER_EMAIL), eq(0), eq(50), eq(null), eq(null))).thenReturn(page);

		// When:
		ResultActions response = mvc.perform(get("/api/v1/admin/reports").with(caller()));

		// Then:
		response.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(0))
				.andExpect(jsonPath("$.size").value(50))
				.andExpect(jsonPath("$.hasMore").value(false))
				.andExpect(jsonPath("$.reports").isEmpty());
		verify(adminReports).allReports(eq(CALLER_EMAIL), eq(0), eq(50), eq(null), eq(null));
	}

	@Test
	void shouldListAdminsTest() throws Exception {
		// Given:
		AdminList admins = new AdminList(List.of(
				new AdminEntry("root@aidigital.com", "config", null, null, false),
				new AdminEntry("dave@aidigital.com", "managed", "root@aidigital.com",
						LocalDateTime.of(2026, 8, 2, 14, 30), true)));
		when(adminManagement.listAdmins(eq(CALLER_EMAIL))).thenReturn(admins);

		// When:
		ResultActions response = mvc.perform(get("/api/v1/admin/admins").with(caller()));

		// Then:
		response.andExpect(status().isOk())
				.andExpect(jsonPath("$.admins.length()").value(2))
				.andExpect(jsonPath("$.admins[0].email").value("root@aidigital.com"))
				.andExpect(jsonPath("$.admins[0].source").value("config"))
				.andExpect(jsonPath("$.admins[0].removable").value(false))
				.andExpect(jsonPath("$.admins[1].email").value("dave@aidigital.com"))
				.andExpect(jsonPath("$.admins[1].source").value("managed"))
				.andExpect(jsonPath("$.admins[1].addedByEmail").value("root@aidigital.com"))
				.andExpect(jsonPath("$.admins[1].createdAt").value("2026-08-02T14:30:00"))
				.andExpect(jsonPath("$.admins[1].removable").value(true));
	}

	@Test
	void shouldAddAdminFromTheRequestBodyTest() throws Exception {
		// Given:
		AdminList admins = new AdminList(List.of(new AdminEntry("erin@aidigital.com", "managed",
				CALLER_EMAIL, LocalDateTime.of(2026, 8, 6, 11, 0), true)));
		when(adminManagement.addAdmin(eq(CALLER_EMAIL), eq("erin@aidigital.com"))).thenReturn(admins);

		// When:
		ResultActions response = mvc.perform(post("/api/v1/admin/admins").with(caller())
				.contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"erin@aidigital.com\"}"));

		// Then:
		response.andExpect(status().isOk())
				.andExpect(jsonPath("$.admins[0].email").value("erin@aidigital.com"))
				.andExpect(jsonPath("$.admins[0].addedByEmail").value(CALLER_EMAIL));
		verify(adminManagement).addAdmin(eq(CALLER_EMAIL), eq("erin@aidigital.com"));
	}

	@Test
	void shouldRemoveAdminNamedInThePathTest() throws Exception {
		// Given:
		AdminList admins = new AdminList(List.of(new AdminEntry("root@aidigital.com", "config", null, null, false)));
		when(adminManagement.removeAdmin(eq(CALLER_EMAIL), eq("dave@aidigital.com"))).thenReturn(admins);

		// When:
		ResultActions response = mvc.perform(delete("/api/v1/admin/admins/dave@aidigital.com").with(caller()));

		// Then:
		response.andExpect(status().isOk())
				.andExpect(jsonPath("$.admins.length()").value(1))
				.andExpect(jsonPath("$.admins[0].email").value("root@aidigital.com"));
		verify(adminManagement).removeAdmin(eq(CALLER_EMAIL), eq("dave@aidigital.com"));
	}

	@Test
	void shouldReturnBadRequestWhenRemovingARootAdminTest() throws Exception {
		// Given:
		when(adminManagement.removeAdmin(eq(CALLER_EMAIL), eq("root@aidigital.com")))
				.thenThrow(new AppException(ErrorReason.C002, "Configured (root) admins cannot be removed here"));

		// When:
		ResultActions response = mvc.perform(delete("/api/v1/admin/admins/root@aidigital.com").with(caller()));

		// Then:
		response.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C002"));
	}

	/**
	 * An authenticated company caller, so security passes and the controller decides.
	 *
	 * @return the JWT request post-processor
	 */
	RequestPostProcessor caller() {
		return jwt().jwt(j -> j
				.subject("user_123")
				.claim("user_id", "user_123")
				.claim("email", CALLER_EMAIL)
				.claim("full_name", "Alice Example"));
	}
}
