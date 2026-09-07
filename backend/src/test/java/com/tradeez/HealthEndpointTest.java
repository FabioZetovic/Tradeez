package com.tradeez;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 0 vertical smoke test (DEC-029).
 *
 * <p>Runs against a real PostgreSQL container rather than an in-memory substitute, so it also
 * proves the Flyway migration applies cleanly and that Hibernate's {@code ddl-auto: validate}
 * agrees with the migrated schema. An H2 stand-in would pass while hiding both.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class HealthEndpointTest {

	@Container
	@SuppressWarnings("resource") // Testcontainers manages the lifecycle.
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired
	MockMvc mockMvc;

	@Test
	@DisplayName("health endpoint is reachable without authentication and reports the database up")
	void healthEndpointReportsDatabaseUp() throws Exception {
		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.database").value("UP"))
			.andExpect(jsonPath("$.application").value("tradeez-backend"));
	}

	@Test
	@DisplayName("unknown endpoints require authentication — deny by default (DEC-022)")
	void unknownEndpointsAreSecuredByDefault() throws Exception {
		mockMvc.perform(get("/api/practice/sessions"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("OpenAPI document is published — it is the frontend contract (DEC-007)")
	void openApiDocumentIsPublished() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.info.title").value("Tradeez API"));
	}
}
