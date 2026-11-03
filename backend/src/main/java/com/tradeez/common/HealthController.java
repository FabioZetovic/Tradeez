package com.tradeez.common;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Phase 0 smoke-test endpoint. Proves the whole vertical is wired: HTTP in, database round-trip,
 * JSON out. Deliberately unauthenticated so the deployment check needs no credentials.
 */
@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Service and database liveness")
public class HealthController {

	private static final Logger log = LoggerFactory.getLogger(HealthController.class);

	private final JdbcTemplate jdbcTemplate;
	private final BuildProperties build;
	private final boolean deployed;
	private final Instant startedAt =
		Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());

	/**
	 * @param build absent when the app runs without a Maven build (straight from an IDE), which is
	 *              why it is optional rather than required
	 */
	public HealthController(JdbcTemplate jdbcTemplate, ObjectProvider<BuildProperties> build,
			@Value("${tradeez.cookies.secure}") boolean deployed) {
		this.jdbcTemplate = jdbcTemplate;
		this.build = build.getIfAvailable();
		this.deployed = deployed;
	}

	@GetMapping
	@Operation(summary = "Service health, including a real database round-trip")
	public Map<String, Object> health() {
		boolean dbUp;
		try {
			jdbcTemplate.queryForObject("SELECT 1", Integer.class);
			dbUp = true;
		} catch (Exception e) {
			// BUG-003 — log the cause. Reporting DEGRADED without saying why is least useful at
			// exactly the moment it matters: a deployed instance that cannot reach its database.
			// The message stays out of the response body deliberately, because this endpoint is
			// unauthenticated and a connection string is not something to hand to the internet.
			log.warn("Health check could not reach the database", e);
			dbUp = false;
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", dbUp ? "UP" : "DEGRADED");
		body.put("database", dbUp ? "UP" : "DOWN");
		// INVARIANT 4: UTC everywhere.
		body.put("timestamp", Instant.now().toString());
		if (deployed) {
			// BUG-132: anyone may ask; deployed, they learn only whether it is up — not what it is or when it was restarted.
			return body;
		}
		body.put("application", "tradeez-backend");
		// ENV-003. A restart that fails because the port is taken leaves the OLD process answering,
		// and "the server responds" then proves nothing about which code is running. These two say:
		// when this process started, and when the code it runs was built. Neither is sensitive.
		body.put("startedAt", startedAt.toString());
		if (build != null && build.getTime() != null) {
			body.put("builtAt", build.getTime().toString());
		}
		return body;
	}
}
