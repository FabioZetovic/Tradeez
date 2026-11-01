package com.tradeez.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.identity.mail.Mailer.Mail;
import com.tradeez.identity.mail.OutboxMailer;

import jakarta.servlet.http.Cookie;

/** T56 (BUG-119, BUG-128): an account is usable only once its owner has opened a link sent to the address. */
class EmailSignUpTest extends AbstractIntegrationTest {

	@DynamicPropertySource
	static void confirmEmails(DynamicPropertyRegistry registry) {
		registry.add("tradeez.test.verify-email", () -> "true");
		registry.add("tradeez.app-url", () -> "https://tradeez.example");
	}

	private static final String PASSWORD = "a-perfectly-fine-passphrase";
	private static final Pattern LINK = Pattern.compile("https://tradeez\\.example/(verify-email|reset-password)#([A-Za-z0-9_-]{43})");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Autowired
	OutboxMailer outbox;

	@Autowired
	RateLimiter rateLimiter;

	@Autowired
	SessionHousekeeping housekeeping;

	@Autowired
	JdbcTemplate jdbc;

	private int seen;

	@BeforeEach
	void cleanSlate() {
		refreshTokens.deleteAllInBatch();
		jdbc.update("DELETE FROM email_token");
		users.deleteAllInBatch();
		rateLimiter.clear();
		seen = outbox.recent().size();
	}

	/** The mails sent since the last call. */
	private List<Mail> mails() {
		List<Mail> all = outbox.recent();
		List<Mail> fresh = all.subList(Math.min(seen, all.size()), all.size());
		seen = all.size();
		return List.copyOf(fresh);
	}

	private static String token(Mail mail) {
		Matcher m = LINK.matcher(mail.text());
		assertThat(m.find()).as("a link in: " + mail.text()).isTrue();
		return m.group(2);
	}

	private MvcResult register(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"displayName\":\"Ana\"}")).andReturn();
	}

	private int login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn().getResponse().getStatus();
	}

	private MvcResult verify(String token) throws Exception {
		return mockMvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"" + token + "\"}")).andReturn();
	}

	private void post_(String path, String json) throws Exception {
		mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isAccepted());
	}

	@Test
	@DisplayName("sign-up signs nobody in; the link in the email confirms the address and signs in, once")
	void confirmByLink() throws Exception {
		MvcResult signedUp = register("ana@example.com", PASSWORD);
		assertThat(signedUp.getResponse().getStatus()).isEqualTo(202);
		assertThat(signedUp.getResponse().getContentAsString()).contains("\"verifyEmail\":true", "ana@example.com");
		assertThat(signedUp.getResponse().getHeaders("Set-Cookie")).noneMatch(c -> c.startsWith("tradeez_"));

		// The right password is not enough until the address is proven — and says so, with a code for the page.
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"ana@example.com\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

		List<Mail> sent = mails();
		assertThat(sent).singleElement().satisfies(m -> {
			assertThat(m.to()).isEqualTo("ana@example.com");
			assertThat(m.subject()).isEqualTo("Confirm your email for Tradeez");
			// The token after a #, which a browser never sends to a server.
			assertThat(m.text()).contains("https://tradeez.example/verify-email#");
		});
		MvcResult verified = verify(token(sent.getFirst()));
		assertThat(verified.getResponse().getStatus()).isEqualTo(200);
		Cookie access = verified.getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
		mockMvc.perform(get("/api/me").cookie(access)).andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("ana@example.com"));

		assertThat(verify(token(sent.getFirst())).getResponse().getStatus()).as("a used link").isEqualTo(400);
		assertThat(verify("x".repeat(43)).getResponse().getStatus()).as("a made-up link").isEqualTo(400);
		assertThat(login("ana@example.com", PASSWORD)).isEqualTo(200);
	}

	@Test
	@DisplayName("signing up with a confirmed address answers the same, emails its owner, and changes nothing")
	void takenAddress() throws Exception {
		register("ana@example.com", PASSWORD);
		verify(token(mails().getFirst()));

		MvcResult again = register("ana@example.com", "another-perfectly-fine-one");
		assertThat(again.getResponse().getStatus()).isEqualTo(202);
		assertThat(again.getResponse().getContentAsString()).contains("\"verifyEmail\":true");
		assertThat(mails()).singleElement().satisfies(m -> {
			assertThat(m.subject()).isEqualTo("Someone tried to sign up to Tradeez with your address");
			assertThat(LINK.matcher(m.text()).find()).as("no link in it").isFalse();
		});
		assertThat(login("ana@example.com", PASSWORD)).isEqualTo(200);
		assertThat(login("ana@example.com", "another-perfectly-fine-one")).isEqualTo(401);
	}

	@Test
	@DisplayName("pre-hijacking: whoever signed up first with the address cannot keep their password on the owner's account")
	void preHijacking() throws Exception {
		register("victim@example.com", "attackers-own-passphrase");
		Mail attackersLink = mails().getFirst();
		register("victim@example.com", PASSWORD);
		Mail ownersLink = mails().getFirst();

		assertThat(verify(token(ownersLink)).getResponse().getStatus()).isEqualTo(200);
		assertThat(login("victim@example.com", PASSWORD)).isEqualTo(200);
		assertThat(login("victim@example.com", "attackers-own-passphrase")).isEqualTo(401);
		// The other sign-up's link died with the first one opened.
		assertThat(verify(token(attackersLink)).getResponse().getStatus()).isEqualTo(400);
	}

	@Test
	@DisplayName("the link again only for someone who knows the password; answered 202 either way")
	void resend() throws Exception {
		register("ana@example.com", PASSWORD);
		mails();
		post_("/api/auth/verify/resend", "{\"email\":\"ana@example.com\",\"password\":\"not-the-right-passphrase\"}");
		post_("/api/auth/verify/resend", "{\"email\":\"nobody@example.com\",\"password\":\"" + PASSWORD + "\"}");
		assertThat(mails()).isEmpty();
		post_("/api/auth/verify/resend", "{\"email\":\"ana@example.com\",\"password\":\"" + PASSWORD + "\"}");
		assertThat(mails()).singleElement().satisfies(m -> assertThat(verify(token(m)).getResponse().getStatus()).isEqualTo(200));
	}

	@Test
	@DisplayName("forgot password: a link for a confirmed account, a note for an unconfirmed one, silence for none")
	void forgotAndReset() throws Exception {
		register("ana@example.com", PASSWORD);
		Cookie oldSession = verify(token(mails().getFirst())).getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);

		post_("/api/auth/password/forgot", "{\"email\":\"nobody@example.com\"}");
		post_("/api/auth/password/forgot", "{\"email\":\"not an address\"}");
		assertThat(mails()).isEmpty();

		post_("/api/auth/password/forgot", "{\"email\":\"ANA@example.com\"}");
		Mail reset = mails().getFirst();
		assertThat(reset.text()).contains("https://tradeez.example/reset-password#");
		String token = token(reset);
		// A reset link is not a sign-up link.
		assertThat(verify(token).getResponse().getStatus()).isEqualTo(400);

		// A weak password is refused and the link is still good after.
		mockMvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"" + token + "\",\"newPassword\":\"short\"}")).andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + token + "\",\"newPassword\":\"a-brand-new-passphrase\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/me").cookie(oldSession)).andExpect(status().isUnauthorized());
		assertThat(login("ana@example.com", PASSWORD)).isEqualTo(401);
		assertThat(login("ana@example.com", "a-brand-new-passphrase")).isEqualTo(200);
		mockMvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"" + token + "\",\"newPassword\":\"yet-another-passphrase\"}")).andExpect(status().isBadRequest());

		register("new@example.com", PASSWORD);
		mails();
		post_("/api/auth/password/forgot", "{\"email\":\"new@example.com\"}");
		assertThat(mails()).singleElement().satisfies(m -> {
			assertThat(m.subject()).isEqualTo("Your Tradeez account was never confirmed");
			assertThat(LINK.matcher(m.text()).find()).isFalse();
		});
	}

	@Test
	@DisplayName("an address gets three emails in a quarter of an hour at most, whatever asks for them")
	void noFlooding() throws Exception {
		register("ana@example.com", PASSWORD);
		verify(token(mails().getFirst()));
		for (int i = 0; i < 5; i++) {
			post_("/api/auth/password/forgot", "{\"email\":\"ana@example.com\"}");
		}
		// One went at sign-up already.
		assertThat(mails()).hasSize(AccountEmails.MAILS_PER_ADDRESS - 1);
	}

	@Test
	@DisplayName("BUG-128 an account never confirmed is deleted after seven days; a confirmed one stays")
	void unconfirmedDeleted() throws Exception {
		register("late@example.com", PASSWORD);
		register("kept@example.com", PASSWORD);
		verify(token(mails().get(1)));
		jdbc.update("UPDATE app_user SET created_at = ?", java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(8))));

		housekeeping.tidy();

		assertThat(users.findByEmailIgnoreCase("late@example.com")).isEmpty();
		assertThat(users.findByEmailIgnoreCase("kept@example.com")).isPresent();
	}
}
