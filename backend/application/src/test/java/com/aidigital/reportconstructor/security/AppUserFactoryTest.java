package com.aidigital.reportconstructor.security;

import com.aidigital.reportconstructor.service.common.security.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the fail-closed {@link AppUserFactory}: only a Clerk JWT carrying {@code user_id} and
 * {@code email} becomes a caller.
 */
class AppUserFactoryTest {

	@Test
	void shouldBuildCallerFromClerkJwtClaimsTest() {
		// Given: a Clerk JWT with a mixed-case, padded email and a full name
		AppUserFactory factory = new AppUserFactory();
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
				.claim("user_id", "user_1")
				.claim("email", "  Alice@AIDigital.com ")
				.claim("full_name", "Alice Example")
				.build();

		// When:
		AppUser user = factory.from(new JwtAuthenticationToken(jwt));

		// Then: the email is trimmed and lower-cased; the name is kept
		assertThat(user).isEqualTo(new AppUser("user_1", "alice@aidigital.com", "Alice Example"));
	}

	@Test
	void shouldFallBackToEmailWhenFullNameIsMissingTest() {
		// Given: a JWT without a full_name claim
		AppUserFactory factory = new AppUserFactory();
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
				.claim("user_id", "user_2")
				.claim("email", "bob@aidigital.com")
				.build();

		// When:
		AppUser user = factory.from(new JwtAuthenticationToken(jwt));

		// Then:
		assertThat(user.fullName()).isEqualTo("bob@aidigital.com");
	}

	@Test
	void shouldFallBackToEmailWhenFullNameIsBlankTest() {
		// Given: a JWT whose full_name is whitespace only
		AppUserFactory factory = new AppUserFactory();
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
				.claim("user_id", "user_3")
				.claim("email", "carol@aidigital.com")
				.claim("full_name", "   ")
				.build();

		// When:
		AppUser user = factory.from(new JwtAuthenticationToken(jwt));

		// Then:
		assertThat(user.fullName()).isEqualTo("carol@aidigital.com");
	}

	@Test
	void shouldRejectMissingAuthenticationTest() {
		// Given:
		AppUserFactory factory = new AppUserFactory();

		// When-Then:
		assertThatThrownBy(() -> factory.from(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageEndingWith(": null");
	}

	@Test
	void shouldRejectNonJwtAuthenticationTest() {
		// Given: a username/password authentication instead of a Clerk JWT
		AppUserFactory factory = new AppUserFactory();
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("alice", "secret");

		// When-Then:
		assertThatThrownBy(() -> factory.from(auth))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(UsernamePasswordAuthenticationToken.class.getName());
	}

	@Test
	void shouldRejectJwtWithoutUserIdTest() {
		// Given: no user_id claim — the factory never falls back to sub
		AppUserFactory factory = new AppUserFactory();
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
				.subject("user_4")
				.claim("email", "dave@aidigital.com")
				.build();

		// When-Then:
		assertThatThrownBy(() -> factory.from(new JwtAuthenticationToken(jwt)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("user_id");
	}

	@Test
	void shouldRejectJwtWithBlankUserIdTest() {
		// Given:
		AppUserFactory factory = new AppUserFactory();
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
				.claim("user_id", " ")
				.claim("email", "dave@aidigital.com")
				.build();

		// When-Then:
		assertThatThrownBy(() -> factory.from(new JwtAuthenticationToken(jwt)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("user_id");
	}

	@Test
	void shouldRejectJwtWithoutEmailTest() {
		// Given:
		AppUserFactory factory = new AppUserFactory();
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
				.claim("user_id", "user_5")
				.build();

		// When-Then:
		assertThatThrownBy(() -> factory.from(new JwtAuthenticationToken(jwt)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("email");
	}

	@Test
	void shouldRejectJwtWithBlankEmailTest() {
		// Given:
		AppUserFactory factory = new AppUserFactory();
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
				.claim("user_id", "user_6")
				.claim("email", "  ")
				.build();

		// When-Then:
		assertThatThrownBy(() -> factory.from(new JwtAuthenticationToken(jwt)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("email");
	}
}
