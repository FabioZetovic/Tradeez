package com.tradeez;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;
import com.tradeez.marketdata.MarketDataService;

import jakarta.servlet.http.Cookie;

/**
 * Integration tests for the market data API, including the V2 migration and the seeded instruments.
 */
class MarketDataApiTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	private Cookie auth;

	@BeforeEach
	void signIn() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();

		auth = mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"trader@example.com\","
					+ "\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"Fabio\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);

		assertThat(auth).isNotNull();
	}

	@Test
	@DisplayName("market data requires authentication (DEC-022 deny-by-default)")
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/market/instruments"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("the V2 migration seeds DEMO-NQ and DEMO-MNQ")
	void seededInstrumentsArePresent() throws Exception {
		mockMvc.perform(get("/api/market/instruments").cookie(auth))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].symbol").value("DEMO-MNQ"))
			.andExpect(jsonPath("$[1].symbol").value("DEMO-NQ"));
	}

	@Test
	@DisplayName("DEMO-NQ carries real NQ contract economics: 0.25 tick worth $5.00")
	void demoNqHasCorrectContractEconomics() throws Exception {
		// The generated *prices* are invented, but the contract maths must be right from day one —
		// position sizing and P&L are exercised against these numbers throughout the project.
		mockMvc.perform(get("/api/market/instruments/DEMO-NQ").cookie(auth))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tickSize").value(0.25))
			.andExpect(jsonPath("$.tickValueCents").value(500))
			.andExpect(jsonPath("$.exchange").value("DEMO"))
			.andExpect(jsonPath("$.dataClass").value("SIMULATED"));
	}

	@Test
	@DisplayName("DEMO-MNQ is a tenth of DEMO-NQ, not a decimal of it")
	void microIsItsOwnProduct() throws Exception {
		mockMvc.perform(get("/api/market/instruments/DEMO-MNQ").cookie(auth))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tickSize").value(0.25))
			.andExpect(jsonPath("$.tickValueCents").value(50));
	}

	@Test
	@DisplayName("an unknown symbol is a 404, not an empty series")
	void unknownSymbolIsNotFound() throws Exception {
		mockMvc.perform(get("/api/market/instruments/NQ").cookie(auth))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("bars are returned with their provenance attached (DEC-033)")
	void barsCarryProvenance() throws Exception {
		long from = 1710338400L; // 2024-03-13 14:00 UTC, mid-session
		long to = from + 3600;

		mockMvc.perform(get("/api/market/instruments/DEMO-NQ/bars").cookie(auth)
				.param("timeframe", "5m")
				.param("from", String.valueOf(from))
				.param("to", String.valueOf(to)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.instrument").value("DEMO-NQ"))
			.andExpect(jsonPath("$.timeframe").value("5m"))
			.andExpect(jsonPath("$.dataClass").value("SIMULATED"))
			.andExpect(jsonPath("$.seed").value(MarketDataService.PUBLIC_CHART_SEED))
			.andExpect(jsonPath("$.generatorVersion").exists())
			// The disclaimer ships with the data rather than being the UI's job to remember.
			.andExpect(jsonPath("$.disclaimer").value("SIMULATED DATA — NOT MARKET HISTORY"))
			.andExpect(jsonPath("$.bars.length()").value(12));
	}

	@Test
	@DisplayName("the public market is identical across requests (DEC-012)")
	void publicMarketIsReproducible() throws Exception {
		long from = 1710338400L;
		long to = from + 1800;

		assertThat(fetchBars(from, to)).isEqualTo(fetchBars(from, to));
	}

	@Test
	@DisplayName("a client cannot choose which market it reads (DEC-038)")
	void seedCannotBeChosen() throws Exception {
		// This test used to assert the opposite: that seed 7 and seed 8 returned different markets
		// over HTTP. That was the hole. A simulated market is a pure function of its seed, and this
		// endpoint serves any range, including the future — so a practice session's seed would have
		// let anyone read every bar its replay had not yet revealed, from outside the practice API
		// entirely. Seed-level determinism is still proven, in DemoSeriesGeneratorTest, where it
		// belongs; it is just no longer something a caller can reach.
		mockMvc.perform(get("/api/market/instruments/DEMO-NQ/bars").cookie(auth)
				.param("timeframe", "1m")
				.param("from", "1710338400")
				.param("to", "1710340200")
				.param("seed", "7"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message")
				.value(org.hamcrest.Matchers.containsString("does not accept a seed")));
	}

	@Test
	@DisplayName("an unreasonable range is rejected rather than attempted")
	void oversizedRangeIsRejected() throws Exception {
		// Without the cap this asks the generator for ~31 million one-second bars and serialises
		// them into a single response.
		mockMvc.perform(get("/api/market/instruments/DEMO-NQ/bars").cookie(auth)
				.param("timeframe", "1s")
				.param("from", "1710338400")
				.param("to", String.valueOf(1710338400L + 365L * 86400)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message")
				.value(org.hamcrest.Matchers.containsString(
					String.valueOf(MarketDataService.MAX_BARS_PER_REQUEST))));
	}

	@Test
	@DisplayName("an inverted range is rejected")
	void invertedRangeIsRejected() throws Exception {
		mockMvc.perform(get("/api/market/instruments/DEMO-NQ/bars").cookie(auth)
				.param("from", "1710342000")
				.param("to", "1710338400"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("an unknown timeframe is rejected with the supported list")
	void unknownTimeframeIsRejected() throws Exception {
		mockMvc.perform(get("/api/market/instruments/DEMO-NQ/bars").cookie(auth)
				.param("timeframe", "7m")
				.param("from", "1710338400")
				.param("to", "1710342000"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message")
				.value(org.hamcrest.Matchers.containsString("7m")));
	}

	@Test
	@DisplayName("a closed-market range returns an empty series, not an error")
	void closedMarketReturnsEmptySeries() throws Exception {
		long saturday = 1710590400L; // 2024-03-16 12:00 UTC

		mockMvc.perform(get("/api/market/instruments/DEMO-NQ/bars").cookie(auth)
				.param("timeframe", "5m")
				.param("from", String.valueOf(saturday))
				.param("to", String.valueOf(saturday + 3600)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bars.length()").value(0));
	}

	private String fetchBars(long from, long to) throws Exception {
		return mockMvc.perform(get("/api/market/instruments/DEMO-NQ/bars").cookie(auth)
				.param("timeframe", "1m")
				.param("from", String.valueOf(from))
				.param("to", String.valueOf(to)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
	}
}
