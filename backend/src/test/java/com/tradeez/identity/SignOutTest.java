package com.tradeez.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.tradeez.AbstractIntegrationTest;

import jakarta.servlet.http.Cookie;

/** T57: signing out ends the session at once (BUG-126); names are checked at sign-up (BUG-127); old sign-ins go (BUG-130). */
class SignOutTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Autowired
	RevokedAccessTokenRepository revoked;

	@Autowired
	SessionHousekeeping housekeeping;

	@Autowired
	JdbcTemplate jdbc;

	private java.util.List<String> column(String column, UUID userId) {
		return jdbc.queryForList("SELECT " + column + " FROM refresh_token WHERE user_id = ?", String.class, userId);
	}

	@BeforeEach
	void cleanSlate() {
		revoked.deleteAllInBatch();
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
	}

	private MvcResult register(String email, String name) throws Exception {
		return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/142.0 Safari/537.36")
				.content("{\"email\":\"" + email + "\",\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"" + name + "\"}"))
			.andReturn();
	}

	@Test
	@DisplayName("BUG-126 a copy of the session cookie stops working the moment its owner signs out")
	void copiedCookieEndsAtSignOut() throws Exception {
		MvcResult in = register("out@example.com", "Out");
		Cookie access = in.getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
		Cookie refresh = in.getResponse().getCookie(CookieService.REFRESH_TOKEN_COOKIE);
		mockMvc.perform(get("/api/me").cookie(access)).andExpect(status().isOk());

		mockMvc.perform(post("/api/auth/logout").cookie(access, refresh).with(csrf())).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/me").cookie(access)).andExpect(status().isUnauthorized());
		assertThat(revoked.count()).isEqualTo(1);
		// Signing in again gives a new token, which works.
		Cookie again = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"out@example.com\",\"password\":\"a-perfectly-fine-passphrase\"}"))
			.andExpect(status().isOk()).andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
		mockMvc.perform(get("/api/me").cookie(again)).andExpect(status().isOk());
	}

	@Test
	@DisplayName("BUG-127 sign-up refuses a disguised name and a look-alike address in words, and stores a tidy one")
	void namesAtSignUp() throws Exception {
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"rtl@example.com\",\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"Admin\\u202Egnp.exe\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("A name can't contain invisible or control characters."));
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"f\\u0430bio@example.com\",\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"F\"}"))
			.andExpect(status().isBadRequest());
		assertThat(users.count()).isZero();

		assertThat(register("Mixed.Case@Example.COM", "  Ana   Horvat ").getResponse().getStatus()).isEqualTo(201);
		AppUser stored = users.findByEmailIgnoreCase("mixed.case@example.com").orElseThrow();
		assertThat(stored.getEmail()).isEqualTo("mixed.case@example.com");
		assertThat(stored.getDisplayName()).isEqualTo("Ana Horvat");
		// BUG-130: only the coarse browser name was kept.
		assertThat(column("user_agent", stored.getId())).containsOnly("Chrome on Windows");
	}

	@Test
	@DisplayName("BUG-130 sign-ins finished more than 30 days ago and expired signed-out tokens are deleted; the rest stay")
	void housekeeping() throws Exception {
		register("tidy@example.com", "Tidy");
		AppUser user = users.findByEmailIgnoreCase("tidy@example.com").orElseThrow();
		Instant now = Instant.now();
		RefreshToken oldRevoked = new RefreshToken(user, "a".repeat(64), now.plus(Duration.ofDays(1)), null);
		oldRevoked.revoke(now.minus(Duration.ofDays(31)));
		refreshTokens.save(oldRevoked);
		// Expired 40 days ago (issued before that: the table insists a token expires after it is issued).
		jdbc.update("INSERT INTO refresh_token (id, user_id, token_hash, issued_at, expires_at, version) VALUES (?, ?, ?, ?, ?, 0)",
			UUID.randomUUID(), user.getId(), "b".repeat(64), java.sql.Timestamp.from(now.minus(Duration.ofDays(70))),
			java.sql.Timestamp.from(now.minus(Duration.ofDays(40))));
		RefreshToken recentRevoked = new RefreshToken(user, "c".repeat(64), now.plus(Duration.ofDays(1)), null);
		recentRevoked.revoke(now.minus(Duration.ofDays(2)));
		refreshTokens.save(recentRevoked);
		revoked.save(new RevokedAccessToken(UUID.randomUUID(), now.minusSeconds(1)));
		revoked.save(new RevokedAccessToken(UUID.randomUUID(), now.plus(Duration.ofMinutes(10))));

		housekeeping.tidy();

		// The live sign-in from registering and the recently revoked one remain.
		assertThat(column("token_hash", user.getId()))
			.doesNotContain("a".repeat(64), "b".repeat(64)).contains("c".repeat(64)).hasSize(2);
		assertThat(revoked.count()).isEqualTo(1);
	}
}
