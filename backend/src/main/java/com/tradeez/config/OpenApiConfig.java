package com.tradeez.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * DEC-007 — the OpenAPI document is the contract between backend and frontend, and between the two
 * AI collaborators working on this repository. The frontend generates its TypeScript types from
 * {@code /v3/api-docs}; nobody hand-writes an API shape on either side.
 */
@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI tradeezOpenApi() {
		return new OpenAPI().info(new Info()
			.title("Tradeez API")
			.version("0.0.1")
			.description("""
				Backend for Tradeez, a futures day-trading practice platform.

				Educational software. Not financial advice, and not a trading venue. Simulated \
				results do not guarantee live results (DEC-015).

				Market data is real CME history imported for private, local use (IMPORTED_PRIVATE, \
				DEC-040) or simulated and labelled as such (DEC-033); every series names its \
				dataClass and source. This service is not a market-data redistributor (INVARIANT 7).""")
			.license(new License().name("Academic project — Tradeez")));
	}
}
