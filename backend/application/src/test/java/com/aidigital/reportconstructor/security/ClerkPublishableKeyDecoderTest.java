package com.aidigital.reportconstructor.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClerkPublishableKeyDecoderTest {

	private final ClerkPublishableKeyDecoder decoder = new ClerkPublishableKeyDecoder();

	private String makeKey(String host) {
		return "pk_test_"
				+ Base64.getUrlEncoder().withoutPadding()
				.encodeToString(host.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void shouldDecodeIssuerAndJwksFromPublishableKeyTest() {
		String pk = makeKey("clerk.example.com");

		assertThat(decoder.decodeFrontendApiHost(pk)).isEqualTo("clerk.example.com");
		assertThat(decoder.issuerFromPublishableKey(pk)).isEqualTo("https://clerk.example.com");
		assertThat(decoder.jwksUriFromPublishableKey(pk))
				.isEqualTo("https://clerk.example.com/.well-known/jwks.json");
	}

	@Test
	void shouldStripTrailingDollarDelimiterTest() {
		String pk = makeKey("clerk.example.com$");
		assertThat(decoder.decodeFrontendApiHost(pk)).isEqualTo("clerk.example.com");
	}

	@Test
	void shouldReturnNullForBlankKeyTest() {
		assertThat(decoder.decodeFrontendApiHost(null)).isNull();
		assertThat(decoder.issuerFromPublishableKey("")).isNull();
		assertThat(decoder.jwksUriFromPublishableKey("   ")).isNull();
	}

	@Test
	void shouldRejectKeyWithWrongPrefixTest() {
		assertThatThrownBy(() -> decoder.decodeFrontendApiHost("sk_test_abc"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("pk_test_");
	}

	@Test
	void shouldRejectMalformedBase64PayloadTest() {
		assertThatThrownBy(() -> decoder.decodeFrontendApiHost("pk_test_!!!not-base64!!!"))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void shouldDecodeLiveKeyWithSurroundingWhitespaceTest() {
		// Given: a production key pasted with padding
		String pk = "  pk_live_" + Base64.getUrlEncoder().withoutPadding()
				.encodeToString("clerk.aidigital.tech".getBytes(StandardCharsets.UTF_8)) + "  ";

		// When:
		String issuer = decoder.issuerFromPublishableKey(pk);

		// Then:
		assertThat(issuer).isEqualTo("https://clerk.aidigital.tech");
	}

	@Test
	void shouldRejectKeyWhosePayloadHasAnImpossibleBase64LengthTest() {
		// Given: the payload uses only base64url characters but a single character cannot encode a byte
		String pk = "pk_test_A";

		// When-Then:
		assertThatThrownBy(() -> decoder.decodeFrontendApiHost(pk))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("payload is not valid base64")
				.hasCauseInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldRejectKeyWhosePayloadIsOnlyTheDelimiterTest() {
		// Given: the payload decodes to "$", leaving an empty host once the delimiter is stripped
		String pk = makeKey("$");

		// When-Then:
		assertThatThrownBy(() -> decoder.decodeFrontendApiHost(pk))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("decoded host is blank");
	}

	@Test
	void shouldRejectBlankHostDirectlyTest() {
		// Given / When-Then: validateHost fails closed on a missing or blank host
		assertThatThrownBy(() -> decoder.validateHost(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("decoded host is blank");
		assertThatThrownBy(() -> decoder.validateHost("  "))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("decoded host is blank");
	}

	@Test
	void shouldAcceptSingleLabelAndMaximumLengthLabelHostTest() {
		// Given: a one-label host and a host with a 63-character label
		String longLabel = "a" + "b".repeat(61) + "c";

		// When-Then: both are valid bare DNS hostnames
		assertThat(decoder.decodeFrontendApiHost(makeKey("localhost"))).isEqualTo("localhost");
		assertThat(decoder.decodeFrontendApiHost(makeKey(longLabel + ".example.com")))
				.isEqualTo(longLabel + ".example.com");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"clerk.example.com#frag",     // fragment present
			"clerk\t.example.com",        // tab
			"clerk-.example.com",         // trailing hyphen
			"clerk.example.com.",         // trailing empty label
			"clerk_x.example.com"         // underscore
	})
	void shouldRejectHostWithForbiddenCharactersOrLabelsTest(String malformedHost) {
		// Given:
		String pk = makeKey(malformedHost);

		// When-Then:
		assertThatThrownBy(() -> decoder.decodeFrontendApiHost(pk))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Malformed CLERK_PUBLISHABLE_KEY");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"https://clerk.example.com",  // scheme present
			"clerk.example.com/path",     // path present
			"clerk.example.com?q=1",      // query present
			"user@clerk.example.com",     // userinfo present
			"clerk.example.com:443",      // port present
			"clerk .example.com",         // whitespace
			"-badlabel.example.com",      // leading hyphen
			"bad..label.example.com"      // empty label
	})
	void shouldRejectMalformedHostTest(String malformedHost) {
		String pk = makeKey(malformedHost);
		assertThatThrownBy(() -> decoder.decodeFrontendApiHost(pk))
				.isInstanceOf(IllegalStateException.class);
	}
}
