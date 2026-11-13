package com.aidigital.reportconstructor.security;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the Clerk-SSO {@link JwtDecoder} wiring.
 */
class SecurityConfigTest {

	private final SecurityProperties securityProperties = new SecurityProperties();
	private final ClerkJwtClaimsValidator clerkJwtClaimsValidator =
			new ClerkJwtClaimsValidator(new AuthorizedPartyMatcher(new AuthProperties()));
	private final ClerkPublishableKeyDecoder publishableKeyDecoder =
			new ClerkPublishableKeyDecoder();
	private final CompanyEmailDomainAuthorizationManager companyEmailDomainAuthorizationManager =
			new CompanyEmailDomainAuthorizationManager(new AuthProperties());
	private final CorsOriginPatternNormalizer corsOriginPatternNormalizer =
			new CorsOriginPatternNormalizer();
	private final SecurityConfig securityConfig = new SecurityConfig(
			securityProperties, clerkJwtClaimsValidator, publishableKeyDecoder,
			companyEmailDomainAuthorizationManager, corsOriginPatternNormalizer);

	@Test
	void shouldFailFastWhenSsoIsUnconfiguredTest() {
		AuthProperties props = new AuthProperties();
		assertThatThrownBy(() -> securityConfig.buildSsoDecoder(props))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Clerk SSO is required");
	}

	@Test
	void shouldBuildSignatureValidatingJwksDecoderWhenConfiguredTest() {
		AuthProperties props = new AuthProperties();
		props.getSso().setJwkSetUri("https://clerk.example.com/.well-known/jwks.json");
		JwtDecoder decoder = securityConfig.buildSsoDecoder(props);
		assertThat(decoder).isInstanceOf(NimbusJwtDecoder.class);
	}

	@Test
	void shouldFailFastWhenSsoEndpointsAndKeyAreBlankTest() {
		// Given: every source of the endpoints is whitespace only
		AuthProperties props = new AuthProperties();
		props.getSso().setIssuerUri("  ");
		props.getSso().setJwkSetUri("  ");
		props.setPublishableKey("  ");

		// When-Then:
		assertThatThrownBy(() -> securityConfig.buildSsoDecoder(props))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Clerk SSO is required");
	}

	@Test
	void shouldResolveIssuerOnlyConfigurationThroughDiscoveryTest() {
		// Given: only an issuer, which points at a closed local port, so discovery cannot succeed
		AuthProperties props = new AuthProperties();
		props.getSso().setIssuerUri("http://127.0.0.1:1");

		// When-Then: the decoder is built from the issuer's discovery document, and an unreachable
		// issuer fails startup instead of yielding a decoder that accepts anything
		assertThatThrownBy(() -> securityConfig.buildSsoDecoder(props))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldBuildAnIssuerValidatingDecoderFromDiscoveryWhenJwksIsBlankTest() throws Exception {
		// Given: a local OIDC issuer serving its discovery document and a one-key JWK set
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		String issuer = "http://127.0.0.1:" + server.getAddress().getPort();
		RSAPublicKey key = (RSAPublicKey) KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
		Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
		String jwks = "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"k1\",\"n\":\""
				+ b64.encodeToString(key.getModulus().toByteArray()) + "\",\"e\":\""
				+ b64.encodeToString(key.getPublicExponent().toByteArray()) + "\"}]}";
		String discovery = "{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/jwks\"}";
		server.createContext("/.well-known/openid-configuration", exchange -> {
			byte[] body = discovery.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.createContext("/jwks", exchange -> {
			byte[] body = jwks.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		AuthProperties props = new AuthProperties();
		props.getSso().setIssuerUri(issuer);
		props.getSso().setJwkSetUri("  ");
		Jwt foreignIssuer = Jwt.withTokenValue("token").header("alg", "none").subject("user_1")
				.claim("user_id", "user_1").claim("iss", "https://attacker.example").build();

		// When:
		JwtDecoder decoder;
		try {
			decoder = securityConfig.buildSsoDecoder(props);
		} finally {
			server.stop(0);
		}

		// Then: the decoder came from discovery and enforces the configured issuer
		assertThat(decoder).isInstanceOf(NimbusJwtDecoder.class);
		@SuppressWarnings("unchecked")
		OAuth2TokenValidator<Jwt> validator =
				(OAuth2TokenValidator<Jwt>) ReflectionTestUtils.getField(decoder, "jwtValidator");
		assertThat(validator.validate(foreignIssuer).getErrors())
				.extracting(OAuth2Error::getDescription)
				.anySatisfy(d -> assertThat(d).contains("iss claim"));
	}

	@Test
	void shouldSkipTheIssuerCheckWhenTheIssuerIsBlankTest() {
		// Given: an explicit JWKS URI and a whitespace-only issuer
		AuthProperties props = new AuthProperties();
		props.getSso().setIssuerUri("   ");
		props.getSso().setJwkSetUri("https://clerk.example.com/.well-known/jwks.json");
		Jwt token = Jwt.withTokenValue("token").header("alg", "none").subject("user_1")
				.claim("user_id", "user_1").claim("iss", "https://any-issuer.example").build();

		// When:
		JwtDecoder decoder = securityConfig.buildSsoDecoder(props);

		// Then: no issuer validator was installed
		@SuppressWarnings("unchecked")
		OAuth2TokenValidator<Jwt> validator =
				(OAuth2TokenValidator<Jwt>) ReflectionTestUtils.getField(decoder, "jwtValidator");
		assertThat(validator.validate(token).getErrors())
				.extracting(OAuth2Error::getDescription)
				.noneSatisfy(d -> assertThat(d).contains("iss claim"));
	}

	@Test
	void shouldExposeTheSsoDecoderAsTheJwtDecoderBeanTest() {
		// Given:
		AuthProperties props = new AuthProperties();
		props.getSso().setJwkSetUri("https://clerk.example.com/.well-known/jwks.json");

		// When:
		JwtDecoder decoder = securityConfig.jwtDecoder(props);

		// Then:
		assertThat(decoder).isInstanceOf(NimbusJwtDecoder.class);
	}

	@Test
	void shouldValidateTheAudienceClaimWhenAnAudienceIsConfiguredTest() {
		// Given: a JWKS decoder that also requires the "reports-api" audience
		AuthProperties props = new AuthProperties();
		props.getSso().setJwkSetUri("https://clerk.example.com/.well-known/jwks.json");
		props.getSso().setAudience("reports-api");
		NimbusJwtDecoder decoder = (NimbusJwtDecoder) securityConfig.buildSsoDecoder(props);
		@SuppressWarnings("unchecked")
		OAuth2TokenValidator<Jwt> validator =
				(OAuth2TokenValidator<Jwt>) ReflectionTestUtils.getField(decoder, "jwtValidator");

		Jwt listToken = Jwt.withTokenValue("token").header("alg", "none").subject("user_1")
				.claim("user_id", "user_1").claim(JwtClaimNames.AUD, List.of("other", "reports-api")).build();
		Jwt stringToken = Jwt.withTokenValue("token").header("alg", "none").subject("user_1")
				.claim("user_id", "user_1").claim(JwtClaimNames.AUD, "reports-api").build();
		Jwt foreignListToken = Jwt.withTokenValue("token").header("alg", "none").subject("user_1")
				.claim("user_id", "user_1").claim(JwtClaimNames.AUD, List.of("other")).build();
		Jwt foreignStringToken = Jwt.withTokenValue("token").header("alg", "none").subject("user_1")
				.claim("user_id", "user_1").claim(JwtClaimNames.AUD, "other").build();

		// When:
		OAuth2TokenValidatorResult listAudience = validator.validate(listToken);
		OAuth2TokenValidatorResult stringAudience = validator.validate(stringToken);
		OAuth2TokenValidatorResult foreignAudience = validator.validate(foreignListToken);
		OAuth2TokenValidatorResult foreignString = validator.validate(foreignStringToken);

		// Then: only a token naming the configured audience passes the aud check
		assertThat(audienceErrors(listAudience)).isEmpty();
		assertThat(audienceErrors(stringAudience)).isEmpty();
		assertThat(audienceErrors(foreignAudience)).isNotEmpty();
		assertThat(audienceErrors(foreignString)).isNotEmpty();
	}

	@Test
	void shouldNotCheckTheAudienceWhenItIsBlankTest() {
		// Given: a blank audience means "no audience requirement"
		AuthProperties props = new AuthProperties();
		props.getSso().setJwkSetUri("https://clerk.example.com/.well-known/jwks.json");
		props.getSso().setAudience("   ");
		NimbusJwtDecoder decoder = (NimbusJwtDecoder) securityConfig.buildSsoDecoder(props);
		@SuppressWarnings("unchecked")
		OAuth2TokenValidator<Jwt> validator =
				(OAuth2TokenValidator<Jwt>) ReflectionTestUtils.getField(decoder, "jwtValidator");

		Jwt token = Jwt.withTokenValue("token").header("alg", "none").subject("user_1")
				.claim("user_id", "user_1").claim(JwtClaimNames.AUD, "anything").build();

		// When:
		OAuth2TokenValidatorResult result = validator.validate(token);

		// Then:
		assertThat(audienceErrors(result)).isEmpty();
	}

	@Test
	void shouldKeepExplicitEndpointsOverThePublishableKeyTest() {
		// Given: an explicit JWKS URI alongside a publishable key for another tenant
		AuthProperties props = new AuthProperties();
		props.getSso().setJwkSetUri("https://explicit.example.com/jwks.json");
		props.setPublishableKey("pk_test_" + java.util.Base64.getUrlEncoder().withoutPadding()
				.encodeToString("other.example.com".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

		// When:
		securityConfig.resolveSsoEndpoints(props);

		// Then: nothing is derived from the key
		assertThat(props.getSso().getJwkSetUri()).isEqualTo("https://explicit.example.com/jwks.json");
		assertThat(props.getSso().getIssuerUri()).isNull();
	}

	@Test
	void shouldKeepExplicitIssuerOverThePublishableKeyTest() {
		// Given: an explicit issuer alongside a publishable key for another tenant
		AuthProperties props = new AuthProperties();
		props.getSso().setIssuerUri("https://explicit.example.com");
		props.setPublishableKey("pk_test_" + java.util.Base64.getUrlEncoder().withoutPadding()
				.encodeToString("other.example.com".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

		// When:
		securityConfig.resolveSsoEndpoints(props);

		// Then:
		assertThat(props.getSso().getIssuerUri()).isEqualTo("https://explicit.example.com");
		assertThat(props.getSso().getJwkSetUri()).isNull();
	}

	@Test
	void shouldDeriveEndpointsWhenExplicitOnesAreBlankTest() {
		// Given: blank explicit endpoints and a publishable key
		AuthProperties props = new AuthProperties();
		props.getSso().setIssuerUri(" ");
		props.getSso().setJwkSetUri(" ");
		props.setPublishableKey("pk_test_" + java.util.Base64.getUrlEncoder().withoutPadding()
				.encodeToString("clerk.example.com".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

		// When:
		securityConfig.resolveSsoEndpoints(props);

		// Then:
		assertThat(props.getSso().getIssuerUri()).isEqualTo("https://clerk.example.com");
		assertThat(props.getSso().getJwkSetUri()).isEqualTo("https://clerk.example.com/.well-known/jwks.json");
	}

	@Test
	void shouldPinThePrincipalNameToTheUserIdClaimTest() {
		// Given: a token whose sub differs from user_id
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
				.subject("sub_value")
				.claim("user_id", "user_42")
				.build();

		// When:
		String name = securityConfig.userIdAsPrincipalConverter().convert(jwt).getName();

		// Then:
		assertThat(name).isEqualTo("user_42");
	}

	@Test
	void shouldBuildCorsConfigurationFromPropertiesTest() {
		// Given: one allowed origin and a custom max age
		SecurityProperties props = new SecurityProperties();
		props.getCors().setAllowedOrigins("https://app.example.com");
		props.getCors().setMaxAgeSeconds(600L);
		SecurityConfig config = new SecurityConfig(props, clerkJwtClaimsValidator, publishableKeyDecoder,
				companyEmailDomainAuthorizationManager, corsOriginPatternNormalizer);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reports");

		// When:
		CorsConfiguration cors = config.corsConfigurationSource().getCorsConfiguration(request);

		// Then:
		assertThat(cors).isNotNull();
		assertThat(cors.getAllowedOriginPatterns()).contains("https://app.example.com");
		assertThat(cors.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
		assertThat(cors.getAllowedHeaders())
				.containsExactly("Authorization", "Content-Type", "X-Correlation-Id", "Accept");
		assertThat(cors.getExposedHeaders()).containsExactly("X-Correlation-Id");
		assertThat(cors.getAllowCredentials()).isTrue();
		assertThat(cors.getMaxAge()).isEqualTo(600L);
	}

	/**
	 * Collects the validation errors raised by the audience check.
	 *
	 * @param result the composite validation result
	 * @return the aud-claim error descriptions
	 */
	List<String> audienceErrors(OAuth2TokenValidatorResult result) {
		return result.getErrors().stream()
				.map(OAuth2Error::getDescription)
				.filter(d -> d != null && d.contains(JwtClaimNames.AUD))
				.toList();
	}

	@Test
	void shouldDeriveIssuerAndJwksFromPublishableKeyTest() {
		AuthProperties props = new AuthProperties();
		String host = "clerk.example.com";
		props.setPublishableKey("pk_test_"
				+ java.util.Base64.getUrlEncoder().withoutPadding()
				.encodeToString(host.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		JwtDecoder decoder = securityConfig.buildSsoDecoder(props);
		assertThat(decoder).isInstanceOf(NimbusJwtDecoder.class);
		assertThat(props.getSso().getIssuerUri()).isEqualTo("https://clerk.example.com");
		assertThat(props.getSso().getJwkSetUri())
				.isEqualTo("https://clerk.example.com/.well-known/jwks.json");
	}
}
