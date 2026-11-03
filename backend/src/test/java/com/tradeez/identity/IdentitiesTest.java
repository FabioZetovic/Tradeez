package com.tradeez.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** BUG-127 (T57): names and addresses that pass for something else are refused. */
class IdentitiesTest {

	@ParameterizedTest
	@ValueSource(strings = {
		"Admin‮gnp.exe", // right-to-left override: shows as "Adminexe.png"
		"Tra​deez fan", // zero-width space
		"Line1\nLine2",
		"Tab\there",
		"<img src=x onerror=alert(1)>",
		"Tradeez Support",
		"the admin",
		"STAFF",
		"   ",
		"Elon 💸 Musk",
	})
	@DisplayName("a name with invisible characters, markup, symbols or the app's own words is refused")
	void refusedNames(String name) {
		assertThatThrownBy(() -> Identities.displayName(name)).isInstanceOf(Identities.InvalidIdentityException.class);
	}

	@Test
	@DisplayName("a real name in any script is kept, its spaces tidied; 40 characters at most")
	void keptNames() {
		assertThat(Identities.displayName("  Fabio   Žetović ")).isEqualTo("Fabio Žetović");
		assertThat(Identities.displayName("Ana-Marija O'Neil")).isEqualTo("Ana-Marija O'Neil");
		assertThat(Identities.displayName("Ζωή")).isEqualTo("Ζωή");
		assertThat(Identities.displayName("trader_77")).isEqualTo("trader_77");
		// Decomposed "é" (e + combining accent) is stored composed.
		assertThat(Identities.displayName("René")).isEqualTo("René");
		assertThat(Identities.displayName("x".repeat(40))).hasSize(40);
		assertThatThrownBy(() -> Identities.displayName("x".repeat(41))).isInstanceOf(Identities.InvalidIdentityException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"fаbio@example.com", // Cyrillic "а"
		"x@localhost",
		"x@example",
		"no-at-sign.example.com",
		".dot@example.com",
		"a..b@example.com",
		"a@-example.com",
		"name@exa mple.com",
	})
	@DisplayName("an address in look-alike letters, without a domain ending or malformed is refused")
	void refusedEmails(String email) {
		assertThatThrownBy(() -> Identities.email(email)).isInstanceOf(Identities.InvalidIdentityException.class);
	}

	@Test
	@DisplayName("an ordinary address is kept, trimmed and in lower case")
	void keptEmails() {
		assertThat(Identities.email("  Fabio.Zetovic+tradeez@Example.COM ")).isEqualTo("fabio.zetovic+tradeez@example.com");
		assertThat(Identities.email("probe@example.test")).isEqualTo("probe@example.test");
		assertThat(Identities.email("a@sub.domain.co.uk")).isEqualTo("a@sub.domain.co.uk");
	}

	@Test
	@DisplayName("BUG-130 a sign-in keeps only the browser and the system, not the whole user-agent string")
	void devices() {
		assertThat(Devices.describe("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
			+ "Chrome/142.0.0.0 Safari/537.36")).isEqualTo("Chrome on Windows");
		assertThat(Devices.describe("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 "
			+ "(KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1")).isEqualTo("Safari on iOS");
		assertThat(Devices.describe("Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0"))
			.isEqualTo("Firefox on Linux");
		assertThat(Devices.describe("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/142.0 "
			+ "Safari/537.36 Edg/142.0")).isEqualTo("Edge on Windows");
		assertThat(Devices.describe("curl/8.0")).isEqualTo("A browser");
		assertThat(Devices.describe(null)).isNull();
	}
}
