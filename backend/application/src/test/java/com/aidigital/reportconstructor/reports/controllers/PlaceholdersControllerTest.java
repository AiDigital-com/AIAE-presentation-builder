package com.aidigital.reportconstructor.reports.controllers;

import com.aidigital.reportconstructor.api.v1.model.DateRangeResultV1;
import com.aidigital.reportconstructor.api.v1.model.LabelPairV1;
import com.aidigital.reportconstructor.api.v1.model.PlaceholderEntryV1;
import com.aidigital.reportconstructor.api.v1.model.PreviewRequestV1;
import com.aidigital.reportconstructor.api.v1.model.PreviewResultV1;
import com.aidigital.reportconstructor.api.v1.model.PreviewStatsV1;
import com.aidigital.reportconstructor.api.v1.model.ReportTypeV1;
import com.aidigital.reportconstructor.api.v1.model.SectionV1;
import com.aidigital.reportconstructor.api.v1.model.SourceV1;
import com.aidigital.reportconstructor.mappers.reports.PlaceholdersApiMapper;
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
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.services.Labels;
import com.aidigital.reportconstructor.service.reports.services.PlaceholderResolverService;
import com.aidigital.reportconstructor.service.reports.services.PreviewResult;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlaceholdersController.class)
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
class PlaceholdersControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private PlaceholderResolverService placeholders;

	@MockitoBean
	private PlaceholdersApiMapper mapper;

	@Test
	void shouldResolveAPreviewThroughTheServiceAndReturnTheMappedResultTest() throws Exception {
		// Given: the request DTO Jackson will bind, and the service-layer round trip it maps through
		PreviewRequestV1 expectedBody = new PreviewRequestV1()
				.brief("The brief")
				.reportType(ReportTypeV1.EOC)
				.adjRows(List.of(List.of("Date", "Spend")));
		GeneratePayload payload = new GeneratePayload("The brief", "EOC", null, List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(), List.of(), null, null, null, null);
		PreviewResult result = new PreviewResult(List.of(), new Labels(List.of(), List.of()), 1, 2, 3, 4);
		PreviewResultV1 response = new PreviewResultV1()
				.sections(List.of(new SectionV1()
						.title("Performance")
						.placeholders(List.of(new PlaceholderEntryV1()
								.key("{{CTR}}").label("CTR").value("0.5%").source(SourceV1.CLAUDE)))))
				.allLabels(Map.of("sheet", List.of(new LabelPairV1().label("Impressions").value("100"))))
				.stats(new PreviewStatsV1().found(1).total(2))
				.sheetCount(3)
				.adjCount(4);
		when(mapper.toPayload(eq(expectedBody))).thenReturn(payload);
		when(placeholders.resolve(same(payload))).thenReturn(result);
		when(mapper.toPreviewResponse(same(result))).thenReturn(response);

		// When:
		ResultActions actions = mvc.perform(post("/api/v1/placeholders/preview")
				.with(caller())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"brief\":\"The brief\",\"reportType\":\"EOC\",\"adjRows\":[[\"Date\",\"Spend\"]]}"));

		// Then:
		actions.andExpect(status().isOk())
				.andExpect(jsonPath("$.sections[0].title").value("Performance"))
				.andExpect(jsonPath("$.sections[0].placeholders[0].key").value("{{CTR}}"))
				.andExpect(jsonPath("$.sections[0].placeholders[0].value").value("0.5%"))
				.andExpect(jsonPath("$.sections[0].placeholders[0].source").value("claude"))
				.andExpect(jsonPath("$.allLabels.sheet[0].label").value("Impressions"))
				.andExpect(jsonPath("$.stats.found").value(1))
				.andExpect(jsonPath("$.stats.total").value(2))
				.andExpect(jsonPath("$.sheetCount").value(3))
				.andExpect(jsonPath("$.adjCount").value(4));
		verify(mapper).toPayload(eq(expectedBody));
		verify(placeholders).resolve(same(payload));
		verify(mapper).toPreviewResponse(same(result));
	}

	@Test
	void shouldDetectTheDateRangeFromTheAdjRowsTest() throws Exception {
		// Given:
		List<List<String>> adjRows = List.of(List.of("Date", "Spend"), List.of("2026-03-01", "5"));
		FlightDates range = new FlightDates(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
		when(placeholders.detectDateRange(eq(adjRows))).thenReturn(range);
		when(mapper.toDateRangeResponse(same(range))).thenReturn(new DateRangeResultV1()
				.start(LocalDate.of(2026, 3, 1))
				.end(LocalDate.of(2026, 3, 31)));

		// When:
		ResultActions actions = mvc.perform(post("/api/v1/placeholders/date-range")
				.with(caller())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"adjRows\":[[\"Date\",\"Spend\"],[\"2026-03-01\",\"5\"]]}"));

		// Then:
		actions.andExpect(status().isOk())
				.andExpect(jsonPath("$.start").value("2026-03-01"))
				.andExpect(jsonPath("$.end").value("2026-03-31"));
		verify(placeholders).detectDateRange(eq(adjRows));
		verify(mapper).toDateRangeResponse(same(range));
	}

	@Test
	void shouldRejectAnUnauthenticatedPreviewWithoutCallingTheServiceTest() throws Exception {
		// When:
		ResultActions actions = mvc.perform(post("/api/v1/placeholders/preview")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"brief\":\"The brief\",\"reportType\":\"EOC\"}"));

		// Then:
		actions.andExpect(status().isUnauthorized());
		verifyNoInteractions(placeholders, mapper);
	}

	/**
	 * Builds an authenticated company-domain caller, as in {@code ApiRequestValidationTest}.
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
}
