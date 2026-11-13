package com.aidigital.reportconstructor.web;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MVC tests for {@link SpaFallbackController}: client routes reach the SPA shell handler,
 * API/static paths never do, and the shell is served only when the frontend build is on the classpath.
 */
class SpaFallbackControllerTest {

	@Test
	void shouldRouteRootAndIndexToTheShellTest() throws Exception {
		// Given:
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new SpaFallbackController()).build();

		// When:
		ResultActions root = mvc.perform(get("/"));
		ResultActions index = mvc.perform(get("/index.html"));

		// Then: no frontend is packaged in the test classpath, so the shell answers 404
		root.andExpect(handler().methodName("root")).andExpect(status().isNotFound());
		index.andExpect(handler().methodName("root")).andExpect(status().isNotFound());
	}

	@Test
	void shouldRouteSingleSegmentClientRoutesToTheShellTest() throws Exception {
		// Given:
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new SpaFallbackController()).build();

		// When:
		ResultActions reports = mvc.perform(get("/reports"));
		ResultActions login = mvc.perform(get("/login"));

		// Then:
		reports.andExpect(handler().methodName("spaSingleSegment")).andExpect(status().isNotFound());
		login.andExpect(handler().methodName("spaSingleSegment")).andExpect(status().isNotFound());
	}

	@Test
	void shouldRouteNestedClientRoutesToTheShellTest() throws Exception {
		// Given:
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new SpaFallbackController()).build();

		// When:
		ResultActions response = mvc.perform(get("/reports/123/edit"));

		// Then:
		response.andExpect(handler().methodName("spaNested")).andExpect(status().isNotFound());
	}

	@Test
	void shouldNeverServeTheShellForApiOrStaticPathsTest() throws Exception {
		// Given:
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new SpaFallbackController()).build();

		// When-Then: excluded prefixes and file-like paths are left to other handlers
		for (String path : new String[] {"/api/v1/reports", "/api", "/actuator/health", "/assets/app.js",
				"/swagger-ui/index.html", "/v3/api-docs", "/error", "/favicon.ico", "/vite.svg"}) {
			mvc.perform(get(path))
					.andExpect(status().isNotFound())
					.andExpect(result -> assertThat(result.getHandler()).as(path).isNull());
		}
	}

	@Test
	void shouldAnswerNotFoundWhenTheFrontendIsNotPackagedTest() {
		// Given: the test classpath carries no static/index.html
		SpaFallbackController controller = new SpaFallbackController();

		// When:
		ResponseEntity<Resource> response = controller.indexHtml();

		// Then:
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNull();
	}

	@Test
	void shouldServeTheShellAsHtmlWhenTheFrontendIsPackagedTest() throws Exception {
		// Given: a built SPA shell placed on the test classpath for the duration of this test
		Path classpathRoot = Path.of(Objects.requireNonNull(getClass().getResource("/")).toURI());
		Path staticDir = classpathRoot.resolve("static");
		Path index = staticDir.resolve("index.html");
		boolean createdDir = !Files.exists(staticDir);
		Files.createDirectories(staticDir);
		Files.writeString(index, "<!doctype html><div id=\"root\"></div>", StandardCharsets.UTF_8);
		SpaFallbackController controller = new SpaFallbackController();

		// When:
		ResponseEntity<Resource> response;
		try {
			response = controller.indexHtml();
		} finally {
			Files.deleteIfExists(index);
			if (createdDir) {
				Files.deleteIfExists(staticDir);
			}
		}

		// Then:
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getFilename()).isEqualTo("index.html");
	}
}
