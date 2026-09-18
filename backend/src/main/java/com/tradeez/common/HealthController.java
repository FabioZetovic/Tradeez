package com.tradeez.common;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	public HealthController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
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

		return Map.of(
			"status", dbUp ? "UP" : "DEGRADED",
			"database", dbUp ? "UP" : "DOWN",
			// INVARIANT 4: UTC everywhere.
			"timestamp", Instant.now().toString(),
			"application", "tradeez-backend");
	}
}
