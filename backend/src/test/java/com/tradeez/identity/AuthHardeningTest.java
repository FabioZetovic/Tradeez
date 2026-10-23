package com.tradeez.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.tradeez.AbstractIntegrationTest;

import jakarta.servlet.http.Cookie;

/**
 * The security pass Fabio asked for (session 017, DEC-064): long passwords (BUG-066), the password policy
 * at sign-up, changing a password, signing out everywhere, an expired session and a forged
 * {@code X-Forwarded-For} (BUG-070); and from the bug hunt of 2026-10-21, an escaped path (BUG-075), the owner locked
 * out by a guesser (BUG-076) and access tokens that outlived "sign out everywhere" (BUG-077).
 */
class AuthHardeningTest extends AbstractIntegrationTest {

	private static final String PASSWORD = "a-perfectly-fine-passphrase";
	private static final String NEW_PASSWORD = "blue lanterns over the harbour";

	/** Exactly 72 bytes: all BCrypt reads, and the longest password-1.0.0 allows. */
	private static final String LONGEST = "the harbour lanterns burned all night, and the boats came home at dawn!!";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Autowired
	JdbcTemplate jdbc;

	@Value("${tradeez.jwt.secret}")
	String jwtSecret;

	@BeforeEach
	void cleanSlate() {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
	}

	private ResultActions register(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"displayName\":\"Marta\"}"));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
	}

	private ResultActions changePassword(Cookie access, String current, String next) throws Exception {
		return mockMvc.perform(post("/api/auth/password").cookie(access).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
	}

	private static Cookie cookie(MvcResult result, String name) {
		Cookie cookie = result.getResponse().getCookie(name);
		assertThat(cookie).as(name).isNotNull();
		return cookie;
	}

	private ResultActions refresh(Cookie refreshCookie) throws Exception {
		return mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie));
	}

	private ResultActions me(Cookie access) throws Exception {
		return mockMvc.perform(get("/api/me").cookie(access));
	}

	@Nested
	@DisplayName("BUG-066 a password longer than BCrypt reads")
	class LongPasswords {

		@Test
		@DisplayName("at sign-in is wrong the same way for an account that exists and one that does not")
		void noEnumeration() throws Exception {
			register("marta@example.com", PASSWORD).andExpect(status().isCreated());
			String tooLong = "x".repeat(80);
			MvcResult known = login("marta@example.com", tooLong).andExpect(status().isUnauthorized()).andReturn();
			MvcResult unknown = login("nobody@example.com", tooLong).andExpect(status().isUnauthorized()).andReturn();
			assertThat(known.getResponse().getContentAsString().replaceAll("\"timestamp\":\"[^\"]+\"", ""))
				.isEqualTo(unknown.getResponse().getContentAsString().replaceAll("\"timestamp\":\"[^\"]+\"", ""));
		}

		@Test
		@DisplayName("is not the stored password with something typed after it")
		void noTruncatedMatch() throws Exception {
			assertThat(LONGEST.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(72);
			register("marta@example.com", LONGEST).andExpect(status().isCreated());
			login("marta@example.com", LONGEST).andExpect(status().isOk());
			// BCrypt compares the first 72 bytes only: this signed in before.
			login("marta@example.com", LONGEST + " and more").andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("at sign-up is refused in words, not with BCrypt's own message")
		void refusedAtSignUp() throws Exception {
			register("marta@example.com", LONGEST + "!")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(Matchers.containsString("It is too long to be stored safely")))
				.andExpect(jsonPath("$.message").value(Matchers.not(Matchers.containsString("cannot be more than"))));
		}
	}

	@Nested
	@DisplayName("password-1.0.0 at sign-up")
	class PolicyAtSignUp {

		@Test
		@DisplayName("a weak password is refused with every problem named, and no account is made")
		void refused() throws Exception {
			register("marta@example.com", "Password1234")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.problems[0]").value("It is one of the most common passwords."));
			register("marta@example.com", "marta1")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.problems.length()").value(3))
				.andExpect(jsonPath("$.message").value(Matchers.startsWith("Choose a different password. It is too short")));
			assertThat(users.count()).isZero();
		}

		@Test
		@DisplayName("a weak password does not reveal whether the address is taken")
		void noEnumeration() throws Exception {
			register("marta@example.com", PASSWORD).andExpect(status().isCreated());
			register("marta@example.com", "Password1234").andExpect(status().isBadRequest());
		}
	}

	@Nested
	@DisplayName("changing a password")
	class ChangePassword {

		@Test
		@DisplayName("needs the current one, signs out every other session, and renews this one")
		void changes() throws Exception {
			MvcResult first = register("marta@example.com", PASSWORD).andExpect(status().isCreated()).andReturn();
			MvcResult other = login("marta@example.com", PASSWORD).andExpect(status().isOk()).andReturn();

			MvcResult changed = changePassword(cookie(first, CookieService.ACCESS_TOKEN_COOKIE), PASSWORD, NEW_PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("marta@example.com"))
				.andReturn();
			assertThat(changed.getResponse().getContentAsString()).doesNotContain("eyJ");

			// The other device, and this one's old refresh token, are signed out; the renewed one works.
			refresh(cookie(other, CookieService.REFRESH_TOKEN_COOKIE)).andExpect(status().isUnauthorized());
			refresh(cookie(changed, CookieService.REFRESH_TOKEN_COOKIE)).andExpect(status().isOk());
			login("marta@example.com", PASSWORD).andExpect(status().isUnauthorized());
			login("marta@example.com", NEW_PASSWORD).andExpect(status().isOk());
		}

		@Test
		@DisplayName("BUG-077 ends the access tokens already handed out too; the one it hands back works")
		void accessTokensEnd() throws Exception {
			MvcResult first = register("marta@example.com", PASSWORD).andExpect(status().isCreated()).andReturn();
			Cookie elsewhere = cookie(login("marta@example.com", PASSWORD).andReturn(), CookieService.ACCESS_TOKEN_COOKIE);
			me(elsewhere).andExpect(status().isOk());

			Cookie here = cookie(first, CookieService.ACCESS_TOKEN_COOKIE);
			MvcResult changed = changePassword(here, PASSWORD, NEW_PASSWORD).andExpect(status().isOk()).andReturn();
			me(elsewhere).andExpect(status().isUnauthorized());
			me(here).andExpect(status().isUnauthorized());
			me(cookie(changed, CookieService.ACCESS_TOKEN_COOKIE)).andExpect(status().isOk());
		}

		@Test
		@DisplayName("a wrong current password is a 403, a weak or unchanged new one a 400; nothing changes")
		void refused() throws Exception {
			Cookie access = cookie(register("marta@example.com", PASSWORD).andReturn(), CookieService.ACCESS_TOKEN_COOKIE);
			changePassword(access, "not-the-password", NEW_PASSWORD)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Your current password is not right."));
			changePassword(access, "x".repeat(80), NEW_PASSWORD).andExpect(status().isForbidden());
			changePassword(access, PASSWORD, "Password1234")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.problems[0]").value("It is one of the most common passwords."));
			changePassword(access, PASSWORD, PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.problems[0]").value("It is the password you have now."));
			login("marta@example.com", PASSWORD).andExpect(status().isOk());
		}

		@Test
		@DisplayName("is throttled per account: the sixth try in fifteen minutes is refused, even with a new address")
		void throttled() throws Exception {
			Cookie access = cookie(register("marta@example.com", PASSWORD).andReturn(), CookieService.ACCESS_TOKEN_COOKIE);
			for (int i = 0; i < AuthService.PASSWORD_CHANGES; i++) {
				changePassword(access, "wrong-guess-" + i, NEW_PASSWORD).andExpect(status().isForbidden());
			}
			mockMvc.perform(post("/api/auth/password").cookie(access).with(csrf()).with(r -> {
					r.setRemoteAddr("198.51.100.9");
					return r;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
				.andExpect(status().isTooManyRequests());
		}

		@Test
		@DisplayName("needs a session and the CSRF token")
		void guarded() throws Exception {
			Cookie access = cookie(register("marta@example.com", PASSWORD).andReturn(), CookieService.ACCESS_TOKEN_COOKIE);
			mockMvc.perform(post("/api/auth/password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
				.andExpect(status().isUnauthorized());
			mockMvc.perform(post("/api/auth/password").cookie(access).contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
				.andExpect(status().isForbidden());
			login("marta@example.com", PASSWORD).andExpect(status().isOk());
		}
	}

	@Nested
	@DisplayName("sessions")
	class Sessions {

		@Test
		@DisplayName("signing out everywhere ends every refresh token and clears this browser's cookies")
		void logoutEverywhere() throws Exception {
			MvcResult first = register("marta@example.com", PASSWORD).andReturn();
			MvcResult other = login("marta@example.com", PASSWORD).andReturn();
			MvcResult out = mockMvc.perform(post("/api/auth/logout-all")
					.cookie(cookie(first, CookieService.ACCESS_TOKEN_COOKIE)).with(csrf()))
				.andExpect(status().isNoContent()).andReturn();
			assertThat(out.getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE).getMaxAge()).isZero();
			assertThat(out.getResponse().getCookie(CookieService.REFRESH_TOKEN_COOKIE).getMaxAge()).isZero();
			refresh(cookie(first, CookieService.REFRESH_TOKEN_COOKIE)).andExpect(status().isUnauthorized());
			refresh(cookie(other, CookieService.REFRESH_TOKEN_COOKIE)).andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("BUG-077 signing out everywhere ends the access tokens already handed out, here and elsewhere")
		void accessTokensEnd() throws Exception {
			Cookie here = cookie(register("marta@example.com", PASSWORD).andReturn(), CookieService.ACCESS_TOKEN_COOKIE);
			Cookie elsewhere = cookie(login("marta@example.com", PASSWORD).andReturn(), CookieService.ACCESS_TOKEN_COOKIE);
			me(elsewhere).andExpect(status().isOk());

			mockMvc.perform(post("/api/auth/logout-all").cookie(here).with(csrf())).andExpect(status().isNoContent());
			// Still inside their fifteen minutes, and over all the same.
			me(here).andExpect(status().isUnauthorized());
			me(elsewhere).andExpect(status().isUnauthorized());
			me(cookie(login("marta@example.com", PASSWORD).andReturn(), CookieService.ACCESS_TOKEN_COOKIE))
				.andExpect(status().isOk());
		}

		@Test
		@DisplayName("BUG-077 a token without a generation is refused, though signed with the right key")
		void generationRequired() throws Exception {
			register("marta@example.com", PASSWORD).andExpect(status().isCreated());
			String userId = users.findByEmailIgnoreCase("marta@example.com").orElseThrow().getId().toString();
			javax.crypto.SecretKey key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
				java.util.Base64.getDecoder().decode(jwtSecret));
			java.util.Date now = new java.util.Date();
			java.util.Date later = new java.util.Date(now.getTime() + 300_000);
			String without = io.jsonwebtoken.Jwts.builder().issuer("tradeez").subject(userId)
				.issuedAt(now).expiration(later).signWith(key).compact();
			String with = io.jsonwebtoken.Jwts.builder().issuer("tradeez").subject(userId).claim("gen", 0)
				.issuedAt(now).expiration(later).signWith(key).compact();
			me(new Cookie(CookieService.ACCESS_TOKEN_COOKIE, with)).andExpect(status().isOk());
			me(new Cookie(CookieService.ACCESS_TOKEN_COOKIE, without)).andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("a refresh token past its thirty days is refused like any other")
		void expired() throws Exception {
			Cookie refreshCookie = cookie(register("marta@example.com", PASSWORD).andReturn(),
				CookieService.REFRESH_TOKEN_COOKIE);
			// Issued thirty-one days ago, so its expiry is behind it (ck_refresh_token_expiry wants it after issue).
			jdbc.update("UPDATE refresh_token SET issued_at = ?, expires_at = ?",
				java.sql.Timestamp.from(Instant.now().minus(java.time.Duration.ofDays(31))),
				java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
			refresh(refreshCookie)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Your session has expired. Please sign in again."));
		}
	}

	@Nested
	@DisplayName("BUG-070 the address the per-IP limit counts")
	class ClientAddress {

		private MockHttpServletRequest request(String forwarded) {
			MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
			request.setRemoteAddr("10.0.0.5");
			if (forwarded != null) {
				request.addHeader("X-Forwarded-For", forwarded);
			}
			return request;
		}

		@Test
		@DisplayName("a client writing a new X-Forwarded-For each time does not get a new quota")
		void forgedHeaderIgnored() throws Exception {
			for (int i = 0; i < 20; i++) {
				int answered = mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", "203.0.113." + i)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"nobody" + i + "@example.com\",\"password\":\"wrong-guess\"}"))
					.andReturn().getResponse().getStatus();
				assertThat(answered).isEqualTo(401);
			}
			mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", "203.0.113.99")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"nobody99@example.com\",\"password\":\"wrong-guess\"}"))
				.andExpect(status().isTooManyRequests());
		}

		@Test
		@DisplayName("counts sign-in, sign-up and the password change; not refresh, sign-out or anything else")
		void limitedPaths() {
			AuthRateLimitFilter filter = new AuthRateLimitFilter(new RateLimiter(), 20, 0);
			for (String path : java.util.List.of("/api/auth/login", "/api/auth/register", "/api/auth/password")) {
				assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", path))).as(path).isFalse();
				assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", path))).as("GET " + path).isTrue();
			}
			for (String path : java.util.List.of("/api/auth/refresh", "/api/auth/logout", "/api/me", "/api/health")) {
				assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", path))).as(path).isTrue();
			}
		}

		@Test
		@DisplayName("behind trusted proxies, the entry the nearest of them appended — never one the client wrote")
		void trustedProxies() {
			RateLimiter limiter = new RateLimiter();
			assertThat(new AuthRateLimitFilter(limiter, 20, 0).clientIp(request("1.1.1.1"))).isEqualTo("10.0.0.5");
			assertThat(new AuthRateLimitFilter(limiter, 20, 1).clientIp(request("6.6.6.6, 198.51.100.7")))
				.isEqualTo("198.51.100.7");
			assertThat(new AuthRateLimitFilter(limiter, 20, 2).clientIp(request("6.6.6.6, 198.51.100.7, 10.1.1.1")))
				.isEqualTo("198.51.100.7");
			// Fewer entries than proxies, or none: the connection's own address.
			assertThat(new AuthRateLimitFilter(limiter, 20, 2).clientIp(request("198.51.100.7"))).isEqualTo("10.0.0.5");
			assertThat(new AuthRateLimitFilter(limiter, 20, 1).clientIp(request(null))).isEqualTo("10.0.0.5");
		}
	}

	@Nested
	@DisplayName("BUG-075 the per-address limit reads the path as the router does")
	class EncodedPaths {

		@Test
		@DisplayName("sign-in spelled with an escaped letter is counted as sign-in: the 21st try, however spelled, is refused")
		void escapedPathCounted() throws Exception {
			for (int i = 0; i < 20; i++) {
				int answered = mockMvc.perform(post(java.net.URI.create("/api/auth/log%69n"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"nobody" + i + "@example.com\",\"password\":\"wrong-guess\"}"))
					.andReturn().getResponse().getStatus();
				assertThat(answered).as("attempt %d", i + 1).isEqualTo(401);
			}
			login("nobody20@example.com", "wrong-guess").andExpect(status().isTooManyRequests());
		}

		@Test
		@DisplayName("escaped letters and ;parameters are read away before the path is compared")
		void pathsDecoded() {
			AuthRateLimitFilter filter = new AuthRateLimitFilter(new RateLimiter(), 20, 0);
			for (String path : java.util.List.of("/api/auth/log%69n", "/api/auth/%6Cogin", "/api/auth/login;x=1",
					"/api/auth/regist%65r", "/api/auth/p%61ssword")) {
				assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", path))).as(path).isFalse();
			}
			assertThat(AuthRateLimitFilter.path(new MockHttpServletRequest("POST", "/api/auth/log%69n")))
				.isEqualTo("/api/auth/login");
		}
	}

	@Nested
	@DisplayName("BUG-076 the per-account sign-in limit")
	class AccountLimit {

		private ResultActions loginFrom(String address, String password) throws Exception {
			return mockMvc.perform(post("/api/auth/login").with(r -> {
					r.setRemoteAddr(address);
					return r;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"marta@example.com\",\"password\":\"" + password + "\"}"));
		}

		@Test
		@DisplayName("stops the guesser's address, and not the owner signing in from another")
		void guesserStoppedOwnerNot() throws Exception {
			register("marta@example.com", PASSWORD).andExpect(status().isCreated());
			for (int i = 0; i < AuthService.ACCOUNT_ATTEMPTS; i++) {
				loginFrom("203.0.113.66", "wrong-guess-" + i).andExpect(status().isUnauthorized());
			}
			// The guesser is stopped, the right password included; the owner, elsewhere, is not.
			loginFrom("203.0.113.66", PASSWORD).andExpect(status().isTooManyRequests());
			loginFrom("198.51.100.20", PASSWORD).andExpect(status().isOk());
		}
	}
}
