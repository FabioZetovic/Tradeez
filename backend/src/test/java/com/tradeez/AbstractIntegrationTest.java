package com.tradeez;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

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
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	static {
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
	}
}
