package com.aidigital.reportconstructor.reports.controllers;

import com.aidigital.reportconstructor.api.v1.model.LineItemMatchResultV1;
import com.aidigital.reportconstructor.api.v1.model.MappingEntryV1;
import com.aidigital.reportconstructor.mappers.reports.LineItemMatchApiMapper;
import com.aidigital.reportconstructor.security.AppUserFactory;
import com.aidigital.reportconstructor.security.AuthProperties;
import com.aidigital.reportconstructor.security.AuthorizedPartyMatcher;
import com.aidigital.reportconstructor.security.ClerkJwtClaimsValidator;
import com.aidigital.reportconstructor.security.ClerkPublishableKeyDecoder;
import com.aidigital.reportconstructor.security.CompanyEmailDomainAuthorizationManager;
import com.aidigital.reportconstructor.security.CorsOriginPatternNormalizer;
import com.aidigital.reportconstructor.security.SecurityConfig;
import com.aidigital.reportconstructor.security.SecurityProperties;
import com.aidigital.reportconstructor.service.common.security.AppUser;
import com.aidigital.reportconstructor.service.common.time.CurrentTimeImpl;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchView;
import com.aidigital.reportconstructor.service.reports.services.LineItemMatchViewService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC slice tests for {@link LineItemMatchingController}: the posted grids and the caller reach the matcher.
 */
@WebMvcTest(controllers = LineItemMatchingController.class)
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
class LineItemMatchingControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private JwtDecoder jwtDecoder;
	@MockitoBean
	private LineItemMatchViewService matcher;
	@MockitoBean
	private LineItemMatchApiMapper mapper;

	@Test
	void shouldMatchThePostedGridsForTheCallerTest() throws Exception {
		// Given: one BigQuery row, one plan row, and the caller built from the JWT
		List<List<String>> bqRows = List.of(List.of("LI-1", "DSP_Display_Q3"));
		List<List<String>> planRows = List.of(List.of("1", "Display"));
		AppUser caller = new AppUser("user_123", "alice@aidigital.com", "Alice Example");
		LineItemMatchView view = new LineItemMatchView(List.of(), List.of("LI-1"), Map.of(), Map.of(), true, 1);
		MappingEntryV1 entry = new MappingEntryV1();
		entry.setTacticNum(1);
		entry.setLineItemId("LI-1");
		LineItemMatchResultV1 dto = new LineItemMatchResultV1();
		dto.setMapping(List.of(entry));
		dto.setUniqueIds(List.of("LI-1"));
		dto.setAutoMatched(true);
		dto.setAutoMatchCount(1);
		when(matcher.matchView(bqRows, planRows, caller)).thenReturn(view);
		when(mapper.toResult(same(view))).thenReturn(dto);

		// When:
		ResultActions response = mvc.perform(post("/api/v1/line-items/match")
				.with(jwt().jwt(j -> j
						.subject("user_123")
						.claim("user_id", "user_123")
						.claim("email", "alice@aidigital.com")
						.claim("full_name", "Alice Example")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"bqRows\":[[\"LI-1\",\"DSP_Display_Q3\"]],\"planRows\":[[\"1\",\"Display\"]]}"));

		// Then:
		response.andExpect(status().isOk())
				.andExpect(jsonPath("$.mapping[0].tacticNum").value(1))
				.andExpect(jsonPath("$.mapping[0].lineItemId").value("LI-1"))
				.andExpect(jsonPath("$.uniqueIds[0]").value("LI-1"))
				.andExpect(jsonPath("$.autoMatched").value(true))
				.andExpect(jsonPath("$.autoMatchCount").value(1));
	}
}
