package com.tradeez.identity.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.identity.AppUser;
import com.tradeez.identity.AppUserRepository;

/**
 * BUG-143 (Codex's audit, 2026-11-01): forgot password for an address with an account waited for the mail provider, and
 * for one without did not — the time told them apart. Now the mail goes after the answer, on a thread of its own.
 */
class MailTimingTest extends AbstractIntegrationTest {

	/** A provider that takes two seconds to answer, as Resend may. */
	static final Duration PROVIDER = Duration.ofSeconds(2);

	@DynamicPropertySource
	static void slowAndAsync(DynamicPropertyRegistry registry) {
		registry.add("tradeez.mail.provider", () -> "slow-test");
		registry.add("tradeez.test.mail-async", () -> "true");
	}

	@TestConfiguration
	static class Slow {
		@Bean
		SlowMailer slowMailer() {
			return new SlowMailer();
		}
	}

	static class SlowMailer implements Mailer {
		final List<Mail> sent = new CopyOnWriteArrayList<>();

		@Override
		public void send(Mail mail) {
			try {
				Thread.sleep(PROVIDER);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			sent.add(mail);
		}
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	SlowMailer mailer;

	private long forgot(String email) throws Exception {
		long start = System.nanoTime();
		mockMvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\"}"));
		return Duration.ofNanos(System.nanoTime() - start).toMillis();
	}

	@Test
	@DisplayName("forgot password answers before the mail provider does, for an address with an account as without")
	void answerDoesNotWaitForMail() throws Exception {
		AppUser owner = new AppUser("timing@example.com", "x", "T");
		owner.verifyEmail(java.time.Instant.now());
		users.saveAndFlush(owner);
		forgot("warm-up@example.com");

		long known = forgot("timing@example.com");
		long unknown = forgot("nobody-here@example.com");
		assertThat(known).as("an address with an account").isLessThan(PROVIDER.toMillis() / 2);
		assertThat(unknown).isLessThan(PROVIDER.toMillis() / 2);

		// The mail still goes, after.
		long until = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (mailer.sent.isEmpty() && System.nanoTime() < until) {
			Thread.sleep(50);
		}
		assertThat(mailer.sent).singleElement().satisfies(m -> assertThat(m.to()).isEqualTo("timing@example.com"));
	}
}
