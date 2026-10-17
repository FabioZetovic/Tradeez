package com.tradeez;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.AuthService;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;

import jakarta.servlet.http.Cookie;

/**
 * End-to-end coverage of DEC-032.
 *
 * <p>Every requirement that decision names has a test here: login, rotation on refresh, revocation
 * on logout, rejection of forged and malformed tokens, CSRF enforcement, and cross-user isolation.
 * The reuse-detection case is the one worth reading — it is the reason {@code replaced_by} exists
 * in the schema rather than a simple revoked flag.
 */
@ExtendWith(OutputCaptureExtension.class)
class AuthFlowTest extends AbstractIntegrationTest {

	private static final String PASSWORD = "a-perfectly-fine-passphrase";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Autowired
	org.springframework.jdbc.core.JdbcTemplate jdbc;

	@BeforeEach
	void cleanSlate() {
		// deleteAllInBatch, not deleteAll: refresh_token.replaced_by references refresh_token, and
		// deleteAll issues one DELETE per row, which trips the self-referencing foreign key as soon
		// as a rotated token is still pointed at by its successor. A single bulk DELETE defers the
		// constraint check to the end of the statement, by which point every row is gone.
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
	}

	// --- helpers -------------------------------------------------------------------------------

	private String registerBody(String email) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
			+ "\",\"displayName\":\"Fabio\"}";
	}

	private String loginBody(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	private MvcResult register(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(email)))
			.andExpect(status().isCreated())
			.andReturn();
	}

	private static Cookie cookieNamed(MvcResult result, String name) {
		Cookie c = result.getResponse().getCookie(name);
		assertThat(c).as("cookie %s", name).isNotNull();
		return c;
	}

	// --- registration and login ----------------------------------------------------------------

	@Nested
	@DisplayName("registration and login")
	class RegistrationAndLogin {

		@Test
		@DisplayName("registering sets both auth cookies and never returns a token in the body")
		void registerSetsCookiesAndLeaksNoToken() throws Exception {
			MvcResult result = register("fabio@example.com");

			assertThat(cookieNamed(result, CookieService.ACCESS_TOKEN_COOKIE).isHttpOnly()).isTrue();
			assertThat(cookieNamed(result, CookieService.REFRESH_TOKEN_COOKIE).isHttpOnly()).isTrue();

			// The whole point of the cookie approach: no token is reachable from JavaScript.
			String body = result.getResponse().getContentAsString();
			assertThat(body).doesNotContain("token").doesNotContain("eyJ");
			assertThat(body).contains("fabio@example.com");
		}

		@Test
		@DisplayName("the refresh cookie is scoped to /api/auth, not the whole site")
		void refreshCookieIsPathScoped() throws Exception {
			MvcResult result = register("scope@example.com");
			assertThat(cookieNamed(result, CookieService.REFRESH_TOKEN_COOKIE).getPath())
				.isEqualTo("/api/auth");
		}

		@Test
		@DisplayName("duplicate email is rejected regardless of capitalisation")
		void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
			register("Someone@example.com");

			mockMvc.perform(post("/api/auth/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content(registerBody("someone@example.com")))
				.andExpect(status().isConflict());
		}

		@Test
		@DisplayName("login succeeds with different capitalisation of the same address")
		void loginIsCaseInsensitive() throws Exception {
			register("Mixed@Example.com");

			mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content(loginBody("mixed@example.com", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(cookie().exists(CookieService.ACCESS_TOKEN_COOKIE));
		}

		@Test
		@DisplayName("a wrong password and an unknown account give the same answer")
		void credentialErrorsAreIndistinguishable() throws Exception {
			register("real@example.com");

			String wrongPassword = mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content(loginBody("real@example.com", "not-the-right-one")))
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();

			String unknownAccount = mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content(loginBody("ghost@example.com", PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();

			// Identical messages: this endpoint must not become an account-enumeration oracle.
			assertThat(wrongPassword).contains("Invalid email or password");
			assertThat(unknownAccount).contains("Invalid email or password");
		}

		@Test
		@DisplayName("short passwords are rejected by password-1.0.0 — without the password in the log or the reply (BUG-054)")
		void shortPasswordIsRejected(CapturedOutput output) throws Exception {
			// Eleven characters: one short of the minimum, and quite possibly a password used elsewhere.
			String typed = "Kx7-qq-Wz9p";
			String reply = mockMvc.perform(post("/api/auth/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"short@example.com\",\"password\":\"" + typed + "\","
						+ "\"displayName\":\"X\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Choose a different password. It is too short: at least 12 characters."))
				.andReturn().getResponse().getContentAsString();
			assertThat(reply).doesNotContain(typed);
			assertThat(output.getAll()).doesNotContain(typed);
		}

		@Test
		@DisplayName("the password is never stored in plaintext")
		void passwordIsHashed() throws Exception {
			register("hash@example.com");
			var user = users.findByEmailIgnoreCase("hash@example.com").orElseThrow();
			assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
		}
	}

	// --- access control ------------------------------------------------------------------------

	@Nested
	@DisplayName("access control")
	class AccessControl {

		@Test
		@DisplayName("/api/me requires authentication")
		void meRequiresAuth() throws Exception {
			mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("/api/me returns the cookie holder, and each user sees only themselves")
		void meIsScopedToTheCookieHolder() throws Exception {
			Cookie alice = cookieNamed(register("alice@example.com"),
				CookieService.ACCESS_TOKEN_COOKIE);
			Cookie bob = cookieNamed(register("bob@example.com"),
				CookieService.ACCESS_TOKEN_COOKIE);

			mockMvc.perform(get("/api/me").cookie(alice))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("alice@example.com"));

			mockMvc.perform(get("/api/me").cookie(bob))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("bob@example.com"));
		}

		@Test
		@DisplayName("a tampered access token is rejected, not merely ignored")
		void forgedTokenIsRejected() throws Exception {
			Cookie real = cookieNamed(register("victim@example.com"),
				CookieService.ACCESS_TOKEN_COOKIE);

			// Tamper with the PAYLOAD segment, not the signature's last character.
			//
			// The original version flipped the final character and was quietly flaky. A 32-byte
			// HMAC-SHA256 signature is 43 base64url characters, and the last one carries only four
			// significant bits plus two of padding — so characters differing in those padding bits,
			// 'A' and 'B' among them, decode to identical bytes. Flipping 'A' to 'B' changed the
			// string without changing the signature, the token verified, and the test failed for a
			// reason that had nothing to do with the code under test.
			//
			// Altering the payload always changes what was signed, so verification must always fail.
			String[] segments = real.getValue().split("\\.");
			assertThat(segments).hasSize(3);
			String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
				("{\"iss\":\"tradeez\",\"sub\":\"00000000-0000-0000-0000-000000000000\","
					+ "\"email\":\"attacker@example.com\",\"exp\":9999999999}")
					.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			String tampered = segments[0] + "." + forgedPayload + "." + segments[2];

			mockMvc.perform(get("/api/me")
					.cookie(new Cookie(CookieService.ACCESS_TOKEN_COOKIE, tampered)))
				.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("a syntactically invalid token is rejected")
		void garbageTokenIsRejected() throws Exception {
			mockMvc.perform(get("/api/me")
					.cookie(new Cookie(CookieService.ACCESS_TOKEN_COOKIE, "not-a-jwt")))
				.andExpect(status().isUnauthorized());
		}
	}

	// --- rotation, reuse detection, logout -----------------------------------------------------

	@Nested
	@DisplayName("refresh rotation and revocation")
	class RotationAndRevocation {

		@Test
		@DisplayName("refresh issues a new refresh token — the old one is replaced")
		void refreshRotates() throws Exception {
			Cookie original = cookieNamed(register("rotate@example.com"),
				CookieService.REFRESH_TOKEN_COOKIE);

			MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(original))
				.andExpect(status().isOk())
				.andReturn();

			Cookie rotated = cookieNamed(refreshed, CookieService.REFRESH_TOKEN_COOKIE);
			assertThat(rotated.getValue()).isNotEqualTo(original.getValue());
		}

		@Test
		@DisplayName("replaying a rotated token, after the grace window, revokes every session")
		void reuseOfRotatedTokenRevokesTheWholeFamily() throws Exception {
			// Updated in session 011. This test used to replay the token immediately and expect a
			// rejection — which encoded the behaviour that WAS BUG-001: a client whose own two
			// requests raced was treated as a thief and signed out everywhere. Immediate replays are
			// now forgiven inside AuthService.REUSE_GRACE, so the reuse defence is exercised by
			// ageing the rotation past that window, which is what a real theft looks like.
			Cookie original = cookieNamed(register("stolen@example.com"),
				CookieService.REFRESH_TOKEN_COOKIE);

			// Legitimate rotation.
			MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(original))
				.andExpect(status().isOk())
				.andReturn();
			Cookie current = cookieNamed(refreshed, CookieService.REFRESH_TOKEN_COOKIE);

			// Age the rotation beyond the grace window so this reads as genuine reuse, not a race.
			var stolenUser = users.findByEmailIgnoreCase("stolen@example.com").orElseThrow();
			jdbc.update(
				"UPDATE refresh_token SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NOT NULL",
				java.sql.Timestamp.from(
					java.time.Instant.now().minus(AuthService.REUSE_GRACE).minusSeconds(60)),
				stolenUser.getId());

			// An attacker replays the token they captured earlier.
			mockMvc.perform(post("/api/auth/refresh").cookie(original))
				.andExpect(status().isUnauthorized());

			// The legitimate user's *current* token is now dead too. We cannot tell who holds which,
			// so invalidating everything and forcing a fresh login is the only safe response.
			mockMvc.perform(post("/api/auth/refresh").cookie(current))
				.andExpect(status().isUnauthorized());

			var user = users.findByEmailIgnoreCase("stolen@example.com").orElseThrow();
			assertThat(refreshTokens.findAllByUserId(user.getId()))
				.isNotEmpty()
				.allSatisfy(t -> assertThat(t.getRevokedAt()).isNotNull());
		}

		@Test
		@DisplayName("logout revokes the refresh token and clears both cookies")
		void logoutRevokesAndClears() throws Exception {
			Cookie refresh = cookieNamed(register("bye@example.com"),
				CookieService.REFRESH_TOKEN_COOKIE);

			// Logout is deliberately NOT CSRF-exempt: a forced logout is a nuisance attack, and
			// exempting it would be a gratuitous hole. The SPA always holds a CSRF token while
			// signed in, so requiring one here costs nothing.
			MvcResult loggedOut = mockMvc.perform(post("/api/auth/logout").cookie(refresh).with(csrf()))
				.andExpect(status().isNoContent())
				.andReturn();

			assertThat(cookieNamed(loggedOut, CookieService.ACCESS_TOKEN_COOKIE).getMaxAge())
				.isZero();
			assertThat(cookieNamed(loggedOut, CookieService.REFRESH_TOKEN_COOKIE).getMaxAge())
				.isZero();

			mockMvc.perform(post("/api/auth/refresh").cookie(refresh))
				.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("refresh without a cookie is rejected")
		void refreshWithoutCookieIsRejected() throws Exception {
			mockMvc.perform(post("/api/auth/refresh"))
				.andExpect(status().isUnauthorized());
		}
	}

	// --- CSRF ----------------------------------------------------------------------------------

	@Nested
	@DisplayName("CSRF protection")
	class Csrf {

		@Test
		@DisplayName("a cookie-authenticated mutation without a CSRF token is rejected")
		void mutationWithoutCsrfTokenIsRejected() throws Exception {
			Cookie access = cookieNamed(register("csrf@example.com"),
				CookieService.ACCESS_TOKEN_COOKIE);

			// This is exactly the attack CSRF protection exists to stop: a cross-site form post
			// that the browser would happily attach the auth cookie to. Without the echoed token
			// it must fail, even though the cookie is perfectly valid.
			mockMvc.perform(post("/api/auth/logout-all").cookie(access))
				.andExpect(status().isForbidden());
		}
	}
}
