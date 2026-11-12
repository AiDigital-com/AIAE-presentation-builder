package com.aidigital.reportconstructor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Binds {@code app.logbook.*} from application.yml: which headers and JSON fields Logbook masks, the
 * replacement text, and which path/method pairs are excluded from HTTP logging. Defaults mirror the
 * company logging rules so the application is compliant without overrides.
 */
@Getter
@Setter
@ConfigurationProperties("app.logbook")
public class LogbookProperties {

	/**
	 * Whether request/response bodies are logged. Off by default so inbound and outbound HTTP logging is
	 * metadata-only: outbound bodies carry Claude prompts and replies, sheet contents and OAuth tokens.
	 */
	private boolean includeBodies;

	/** Text written in place of a masked header or JSON value. */
	private String censoredReplacement = "XXX";

	/** Header names (case-insensitive) whose values are masked. */
	private List<String> headersToCensor = List.of(
			"Authorization", "Cookie", "Set-Cookie",
			"X-API-Key", "AccessKey", "Proxy-Authorization");

	/** JSON property-name patterns whose string values are masked. */
	private List<String> jsonFieldsToCensor = List.of(
			".*password.*", ".*token.*", ".*secret.*", ".*key.*",
			".*credential.*", ".*authorization.*",
			"privateKey", "clientSecret", "serviceAccount");

	/** Path pattern to the HTTP methods excluded from logging on that path. */
	private Map<String, List<String>> excluded = Map.of(
			"/**/actuator/**", List.of("GET"),
			"/**/swagger-ui/**", List.of("GET"),
			"/**/specs/**", List.of("GET"),
			"/health", List.of("GET"),
			"/**", List.of("OPTIONS"));
}
