package com.tradeez;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Phase 0 vertical smoke test: HTTP in, database round-trip, JSON out. */
class HealthEndpointTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	@DisplayName("health endpoint is reachable without authentication and reports the database up")
	void healthEndpointReportsDatabaseUp() throws Exception {
		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.database").value("UP"))
			.andExpect(jsonPath("$.application").value("tradeez-backend"))
			// ENV-003: enough to tell a stale process from the one just started.
			.andExpect(jsonPath("$.startedAt").isString())
			.andExpect(jsonPath("$.builtAt").isString());
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
