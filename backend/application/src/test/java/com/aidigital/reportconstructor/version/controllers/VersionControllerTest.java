package com.aidigital.reportconstructor.version.controllers;

import com.aidigital.reportconstructor.api.v1.model.VersionV1;
import com.aidigital.reportconstructor.mappers.version.VersionApiMapper;
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
import com.aidigital.reportconstructor.service.version.dto.GitVersionInfo;
import com.aidigital.reportconstructor.service.version.services.GitVersionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC slice tests for {@link VersionController}: the running build's commit is served to signed-in users.
 */
@WebMvcTest(controllers = VersionController.class)
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
class VersionControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private JwtDecoder jwtDecoder;
	@MockitoBean
	private GitVersionService gitVersionService;
	@MockitoBean
	private VersionApiMapper mapper;

	@Test
	void shouldServeTheRunningCommitTest() throws Exception {
		// Given:
		GitVersionInfo info = new GitVersionInfo("4ea6920", "2026-11-13T10:00:00Z");
		VersionV1 dto = new VersionV1();
		dto.setCommitId("4ea6920");
		dto.setCommitTime("2026-11-13T10:00:00Z");
		when(gitVersionService.current()).thenReturn(info);
		when(mapper.toVersion(same(info))).thenReturn(dto);

		// When:
		ResultActions response = mvc.perform(get("/api/v1/version").with(jwt().jwt(j -> j
				.subject("user_123")
				.claim("user_id", "user_123")
				.claim("email", "alice@aidigital.com"))));

		// Then:
		response.andExpect(status().isOk())
				.andExpect(jsonPath("$.commitId").value("4ea6920"))
				.andExpect(jsonPath("$.commitTime").value("2026-11-13T10:00:00Z"));
	}
}
