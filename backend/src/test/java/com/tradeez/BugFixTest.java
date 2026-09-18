package com.tradeez;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.tradeez.identity.AppUser;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.AuthService;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RateLimiter;
import com.tradeez.identity.RefreshTokenRepository;

import jakarta.servlet.http.Cookie;

/**
 * Regression tests for the bug-fix batch (session 011).
 *
 * <p>Each of these would have failed before its fix. They live together deliberately: a reader
 * tracing an entry in {@code docs/BUGS.md} can find the test that proves it is actually dead.
 */
class BugFixTest extends AbstractIntegrationTest {

	private static final String PASSWORD = "a-perfectly-fine-passphrase";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Autowired
	RateLimiter rateLimiter;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void cleanSlate() {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		// The limiter is a singleton shared across tests in this JVM. Without clearing it, whichever
		// test ran first would leave counters that throttle the next one.
		rateLimiter.clear();
	}

	private MvcResult register(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
					+ "\",\"displayName\":\"Fabio\"}"))
			.andExpect(status().isCreated())
			.andReturn();
	}

	// --- BUG-001 ------------------------------------------------------------------------------

	@Nested
	@DisplayName("BUG-001 concurrent refresh must not log the user out everywhere")
	class ConcurrentRefresh {

		@Test
		@DisplayName("a second refresh with the same token succeeds inside the grace window")
		void duplicateRefreshIsNotTreatedAsTheft() throws Exception {
			Cookie original = register("race@example.com")
				.getResponse().getCookie(CookieService.REFRESH_TOKEN_COOKIE);
			assertThat(original).isNotNull();

			// First refresh wins the race and rotates.
			MvcResult first = mockMvc.perform(post("/api/auth/refresh").cookie(original))
				.andExpect(status().isOk())
				.andReturn();
			Cookie rotated = first.getResponse().getCookie(CookieService.REFRESH_TOKEN_COOKIE);

			// The loser of the race replays the same cookie moments later. Before the fix this was
			// treated as a stolen token and every session for the account was revoked — the user was
			// signed out of every device because their own app made two requests at once.
			mockMvc.perform(post("/api/auth/refresh").cookie(original))
				.andExpect(status().isOk());

			// And the winner's token must still work: nothing was revoked.
			assertThat(rotated).isNotNull();
			mockMvc.perform(post("/api/auth/refresh").cookie(rotated))
				.andExpect(status().isOk());
		}

		@Test
		@DisplayName("genuine reuse after the grace window still revokes every session")
		void lateReuseStillRevokesEverything() throws Exception {
			Cookie original = register("thief@example.com")
				.getResponse().getCookie(CookieService.REFRESH_TOKEN_COOKIE);
			assertThat(original).isNotNull();

			mockMvc.perform(post("/api/auth/refresh").cookie(original))
				.andExpect(status().isOk());

			// Age the rotation past the grace window. Rewriting revoked_at directly is the only way
			// to test this without sleeping for the full window in the suite.
			AppUser user = users.findByEmailIgnoreCase("thief@example.com").orElseThrow();
			jdbc.update("UPDATE refresh_token SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NOT NULL",
				java.sql.Timestamp.from(Instant.now().minus(AuthService.REUSE_GRACE).minusSeconds(60)),
				user.getId());

			mockMvc.perform(post("/api/auth/refresh").cookie(original))
				.andExpect(status().isUnauthorized());

			assertThat(refreshTokens.findAllByUserId(user.getId()))
				.isNotEmpty()
				.allSatisfy(t -> assertThat(t.getRevokedAt()).isNotNull());
		}
	}

	// --- BUG-002 ------------------------------------------------------------------------------

	@Nested
	@DisplayName("BUG-002 authentication endpoints are rate limited")
	class RateLimiting {

		@Test
		@DisplayName("repeated failed sign-ins against one account are throttled")
		void accountIsThrottledAfterRepeatedFailures() throws Exception {
			register("target@example.com");

			int throttled = 0;
			for (int attempt = 0; attempt < AuthService.ACCOUNT_ATTEMPTS + 5; attempt++) {
				int status = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"target@example.com\",\"password\":\"wrong-guess\"}"))
					.andReturn().getResponse().getStatus();
				if (status == 429) {
					throttled++;
				}
			}

			assertThat(throttled)
				.as("guessing must stop being free after a handful of attempts")
				.isPositive();
		}

		@Test
		@DisplayName("a successful sign-in clears the counter")
		void successResetsTheCounter() throws Exception {
			register("forgetful@example.com");

			// A few wrong guesses, then the right one — a normal human morning.
			for (int i = 0; i < 3; i++) {
				mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"forgetful@example.com\",\"password\":\"nope\"}"));
			}

			mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"forgetful@example.com\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk());

			// Without the reset, this user would stay throttled after getting their password right —
			// punishing exactly the person the limit is meant to protect.
			mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"forgetful@example.com\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk());
		}

		@Test
		@DisplayName("expired windows are evicted so the limiter cannot grow without bound")
		void expiredWindowsAreEvicted() throws InterruptedException {
			rateLimiter.clear();
			// The map is keyed on attacker-supplied values (IP, email). Without eviction a stream of
			// varied inputs turns a brute-force defence into a memory-exhaustion vector.
			for (int i = 0; i < 500; i++) {
				rateLimiter.tryAcquire("probe-" + i, 5, java.time.Duration.ofMillis(1));
			}
			assertThat(rateLimiter.trackedKeys()).isGreaterThan(400);

			// Windows' clock granularity is around a millisecond, so all 500 windows can be stamped
			// with the same instant and a 1ms expiry may not yet have passed when eviction runs.
			// This is why the first version of this test reported 499 survivors against correct code.
			Thread.sleep(50);

			rateLimiter.evictExpired();
			assertThat(rateLimiter.trackedKeys()).isZero();
		}
	}

	// --- BUG-004, BUG-005 ---------------------------------------------------------------------

	@Nested
	@DisplayName("schema")
	class Schema {

		@Test
		@DisplayName("BUG-004 refresh_token.replaced_by is indexed")
		void replacedByIsIndexed() {
			var indexes = jdbc.queryForList(
				"SELECT indexname FROM pg_indexes WHERE tablename = 'refresh_token'");
			assertThat(indexes)
				.extracting(row -> row.get("indexname"))
				.contains("ix_refresh_token_replaced_by");
		}

		@Test
		@DisplayName("BUG-005 the JSONB settings column actually round-trips")
		void settingsRoundTrip() throws Exception {
			// The column and its Hibernate mapping existed from the baseline migration but nothing
			// ever read or wrote it, so the JSONB mapping was entirely unexercised — it might simply
			// not have worked the first time someone relied on it.
			register("settings@example.com");
			AppUser user = users.findByEmailIgnoreCase("settings@example.com").orElseThrow();

			assertThat(user.getSettings()).isNotNull().isEmpty();

			user.getSettings().put("defaultTimeframe", "5m");
			user.getSettings().put("chartTheme", Map.of("mode", "dark"));
			users.saveAndFlush(user);

			AppUser reloaded = users.findById(user.getId()).orElseThrow();
			assertThat(reloaded.getSettings()).containsEntry("defaultTimeframe", "5m");
			assertThat(reloaded.getSettings()).containsKey("chartTheme");
		}
	}

	// --- BUG-003 ------------------------------------------------------------------------------

	@Test
	@DisplayName("BUG-003 health still reports UP and does not leak internals")
	void healthReportsWithoutLeaking() throws Exception {
		// The fix logs the failure cause rather than putting it in the body. This asserts the body
		// stays clean — an unauthenticated endpoint must not hand a connection string to the world.
		String body = mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		assertThat(body).contains("\"status\":\"UP\"");
		assertThat(body).doesNotContain("jdbc:").doesNotContain("password");
	}
}
