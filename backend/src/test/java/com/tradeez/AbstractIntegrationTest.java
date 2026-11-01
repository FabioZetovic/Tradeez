package com.tradeez;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.tradeez.identity.RateLimiter;

/**
 * Shared PostgreSQL container for integration tests (DEC-029).
 *
 * <p>The container is a static singleton started once per JVM rather than per class — Testcontainers
 * reuses it and Ryuk cleans it up at exit. Starting a fresh database per test class would roughly
 * triple the suite's runtime for no extra confidence.
 *
 * <p>A real PostgreSQL is used rather than an in-memory substitute on purpose: it means these tests
 * also prove the Flyway migrations apply and that Hibernate's {@code validate} agrees with them.
 * H2 would pass while hiding both.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

	@SuppressWarnings("resource") // lifecycle is managed by Testcontainers
	// Every distinct test configuration keeps a Spring context, each with its own pool of ten connections; past about ten
	// of them PostgreSQL's default of 100 ran out ("too many clients already", T56).
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
		.withCommand("postgres", "-c", "max_connections=400");

	static {
		// Tests start Spring without main(), so they set the JVM zone themselves (BUG-036).
		TradeezApplication.useUtc();
		DockerCheck.require(() -> DockerClientFactory.instance().isDockerAvailable());
		POSTGRES.start();
	}

	@Autowired
	private RateLimiter rateLimiterForIsolation;

	/**
	 * Clears the rate limiter before every test.
	 *
	 * <p>The limiter is an application singleton keyed by client IP, and every MockMvc request in
	 * the suite arrives from the same address. Without this, attempts accumulate across unrelated
	 * test classes until registration starts returning 429 — which is precisely what happened when
	 * the limiter was introduced: eleven market-data tests failed because earlier authentication
	 * tests had spent the quota.
	 */
	@BeforeEach
	void resetRateLimiter() {
		rateLimiterForIsolation.clear();
	}

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		// No test calls Have I Been Pwned: PwnedPasswordsTest checks the client against a local stand-in.
		registry.add("tradeez.password.breach-check.enabled", () -> "false");
		// Evidence is counted by the tests that mean to, not by every context that starts (EvidenceServiceTest).
		registry.add("tradeez.evidence.refresh-on-start", () -> "false");
		// Nor FRED: the economic calendar is filled by the tests that mean to (EconomicCalendarServiceTest).
		registry.add("tradeez.news.refresh-enabled", () -> "false");
		// BCrypt at cost 12 (BUG-132) is a quarter second a hash; hundreds of sign-ups would add minutes. The cost is the
		// setting's business, tested on its own (PasswordRehashTest); 5 keeps it above the cost a rehash is tested from.
		registry.add("tradeez.password.bcrypt-strength", () -> "5");
		// T56: most tests sign up to test something else, and are signed in at once. EmailSignUpTest switches confirmation
		// on; the prod profile cannot run without it (MailGuard). No mail is written to disk, only kept in memory.
		// A placeholder, so a subclass can say otherwise: a second registration of the same name would not win.
		registry.add("tradeez.signup.verify-email", () -> "${tradeez.test.verify-email:false}");
		registry.add("tradeez.mail.outbox-dir", () -> "");
	}
}
