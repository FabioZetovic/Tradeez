package com.tradeez.common;

import java.time.Instant;
import java.util.Map;

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
