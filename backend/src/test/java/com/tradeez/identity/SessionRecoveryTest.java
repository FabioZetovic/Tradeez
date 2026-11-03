package com.tradeez.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.identity.dto.AuthDtos.ChangePasswordRequest;
import com.tradeez.identity.dto.AuthDtos.RegisterRequest;
import com.tradeez.identity.mail.OutboxMailer;

import jakarta.servlet.http.Cookie;

/**
 * Codex's audit of 2026-11-01 (docs/audits/2026-11-01): what a stolen token or an old emailed link can still do after
 * the owner signs out, changes the password or resets it. Each test is one of its probes, turned round.
 */
class SessionRecoveryTest extends AbstractIntegrationTest {

	private static final String PASSWORD = "a-perfectly-fine-passphrase";

	@Autowired
	AuthService auth;

	@Autowired
	AccountEmails emails;

	@Autowired
	OutboxMailer mail;

	@Autowired
	RateLimiter limits;

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	DataSource datasource;

	@Autowired
	JwtService jwt;

	private AuthService.IssuedTokens account() {
		return auth.register(new RegisterRequest("recovery-" + UUID.randomUUID() + "@example.test", PASSWORD, "R"), "test")
			.signedIn();
	}

	private int me(AuthService.IssuedTokens tokens) throws Exception {
		return mvc.perform(get("/api/me").cookie(new Cookie(CookieService.ACCESS_TOKEN_COOKIE, tokens.accessToken())))
			.andReturn().getResponse().getStatus();
	}

	private String resetLink(AppUser user) {
		emails.sendReset(user);
		String text = mail.recent().getLast().text();
		return text.substring(text.indexOf("/reset-password#") + "/reset-password#".length()).split("\\s")[0];
	}

	@Test
	@DisplayName("BUG-138 a token rotated seconds ago starts no session after a sign-out everywhere")
	void graceEndsWithLogoutEverywhere() {
		var original = account();
		auth.refresh(original.refreshToken(), "test");
		auth.logoutEverywhere(original.user().getId());
		assertThatThrownBy(() -> auth.refresh(original.refreshToken(), "test"))
			.isInstanceOf(AuthService.InvalidRefreshTokenException.class);
	}

	@Test
	@DisplayName("BUG-138 … after a password change")
	void graceEndsWithPasswordChange() {
		var original = account();
		auth.refresh(original.refreshToken(), "test");
		auth.changePassword(original.user().getId(), new ChangePasswordRequest(PASSWORD, "a-different-safe-passphrase"), "test");
		assertThatThrownBy(() -> auth.refresh(original.refreshToken(), "test"))
			.isInstanceOf(AuthService.InvalidRefreshTokenException.class);
	}

	@Test
	@DisplayName("BUG-138 … after the session it became signs out")
	void graceEndsWithLogout() {
		var original = account();
		var successor = auth.refresh(original.refreshToken(), "test");
		auth.logout(successor.refreshToken());
		assertThatThrownBy(() -> auth.refresh(original.refreshToken(), "test"))
			.isInstanceOf(AuthService.InvalidRefreshTokenException.class);
	}

	@Test
	@DisplayName("BUG-138 two tabs refreshing at once still both stay signed in (BUG-001)")
	void graceStillServesDuplicates() throws Exception {
		var original = account();
		var first = auth.refresh(original.refreshToken(), "test");
		var duplicate = auth.refresh(original.refreshToken(), "test");
		assertThat(me(first)).isEqualTo(200);
		assertThat(me(duplicate)).isEqualTo(200);
	}

	@Test
	@DisplayName("BUG-139 a stolen token replayed late ends the access tokens already handed out, not only the refresh ones")
	void theftEndsAccessTokens() throws Exception {
		var original = account();
		var successor = auth.refresh(original.refreshToken(), "test");
		jdbc.update("UPDATE refresh_token SET revoked_at = now() - interval '30 seconds' WHERE token_hash = ?",
			jwt.hash(original.refreshToken()));
		assertThatThrownBy(() -> auth.refresh(original.refreshToken(), "test"))
			.isInstanceOf(AuthService.TokenReuseDetectedException.class);
		assertThat(me(successor)).isEqualTo(401);
		assertThatThrownBy(() -> auth.refresh(successor.refreshToken(), "test")).isInstanceOf(RuntimeException.class);
	}

	@Test
	@DisplayName("BUG-142 a reset link sent before a password change no longer works; one sent after does")
	void passwordChangeSpendsResetLinks() throws Exception {
		var a = account();
		limits.clear();
		String before = resetLink(a.user());
		auth.changePassword(a.user().getId(), new ChangePasswordRequest(PASSWORD, "a-different-safe-passphrase"), "test");
		assertThatThrownBy(() -> auth.resetPassword(before, "a-third-safe-passphrase", "test"))
			.isInstanceOf(AccountEmails.InvalidLinkException.class);
		String after = resetLink(a.user());
		assertThat(me(auth.resetPassword(after, "a-third-safe-passphrase", "test"))).isEqualTo(200);
	}

	@Test
	@DisplayName("BUG-141 one reset link opened twice at once sets one password, not two")
	void resetLinkRace() throws Exception {
		var a = account();
		limits.clear();
		String raw = resetLink(a.user());
		CompletableFuture<AuthService.IssuedTokens> first;
		CompletableFuture<AuthService.IssuedTokens> second;
		// Hold the link's row so both requests read it unused before either can spend it — the race, made certain.
		try (var lock = datasource.getConnection()) {
			lock.setAutoCommit(false);
			try (var select = lock.prepareStatement("SELECT id FROM email_token WHERE token_hash = ? FOR UPDATE")) {
				select.setString(1, jwt.hash(raw));
				try (var row = select.executeQuery()) {
					assertThat(row.next()).isTrue();
				}
			}
			first = CompletableFuture.supplyAsync(() -> auth.resetPassword(raw, "a-second-safe-passphrase", "first"));
			second = CompletableFuture.supplyAsync(() -> auth.resetPassword(raw, "a-third-safe-passphrase", "second"));
			long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			int waiting = 0;
			while (System.nanoTime() < until && waiting < 2) {
				waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() "
					+ "AND wait_event_type = 'Lock' AND lower(query) LIKE '%update email_token%'", Integer.class);
				if (waiting < 2) {
					Thread.sleep(25);
				}
			}
			lock.commit();
			assertThat(waiting).as("both requests reached the spend").isEqualTo(2);
		}
		List<String> outcomes = new ArrayList<>();
		for (var call : List.of(first, second)) {
			try {
				call.get(10, TimeUnit.SECONDS);
				outcomes.add("reset");
			} catch (java.util.concurrent.ExecutionException e) {
				assertThat(e.getCause()).isInstanceOf(AccountEmails.InvalidLinkException.class);
				outcomes.add("refused");
			}
		}
		assertThat(outcomes).containsExactlyInAnyOrder("reset", "refused");
	}

	@Test
	@DisplayName("BUG-144 three sign-up attempts with the owner's address cannot use up their reset email")
	void noticesLeaveResetBudget() {
		var a = account();
		limits.clear();
		for (int n = 0; n < 3; n++) {
			emails.sendAlreadyRegistered(a.user());
		}
		int before = mail.recent().size();
		auth.forgotPassword(a.user().getEmail());
		assertThat(mail.recent()).hasSize(before + 1);
		assertThat(mail.recent().getLast().text()).contains("/reset-password#");
	}

	@Test
	@DisplayName("BUG-144 the notes nobody asked for stay rare: one per address per window")
	void noticesAreRare() {
		var a = account();
		limits.clear();
		int before = mail.recent().size();
		for (int n = 0; n < 3; n++) {
			emails.sendAlreadyRegistered(a.user());
		}
		assertThat(mail.recent()).hasSize(before + 1);
	}
}
