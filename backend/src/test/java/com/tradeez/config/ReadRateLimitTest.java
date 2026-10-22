package com.tradeez.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;

import jakarta.servlet.http.Cookie;

/** BUG-080: the reads that cost the server work are limited per account. */
class ReadRateLimitTest extends AbstractIntegrationTest {

	/** An hour of the simulated market's five-minute candles: cheap, so the test counts requests, not seconds. */
	private static final String BARS = "/api/market/instruments/DEMO-NQ/bars?timeframe=5m&from=1710248400&to=1710252000";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Value("${tradeez.rate-limit.reads-per-minute}")
	int limit;

	@BeforeEach
	void cleanSlate() {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
	}

	private Cookie register(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"R\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
	}

	@Test
	@DisplayName("the 241st read in a minute is refused for that account only, and only for the costly reads")
	void perAccount() throws Exception {
		assertThat(limit).isEqualTo(240);
		Cookie busy = register("busy@example.com");
		Cookie other = register("other@example.com");
		for (int i = 0; i < limit; i++) {
			int answered = mockMvc.perform(get(BARS).cookie(busy)).andReturn().getResponse().getStatus();
			assertThat(answered).as("read %d", i + 1).isEqualTo(200);
		}
		mockMvc.perform(get(BARS).cookie(busy))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", "60"))
			.andExpect(jsonPath("$.message").value("Too many chart requests in a minute. Wait a moment and try again."));
		// Every costly read shares the count; the rest of the API does not.
		mockMvc.perform(get("/api/market/instruments/DEMO-NQ/clock?from=1710248400&to=1710252000").cookie(busy))
			.andExpect(status().isTooManyRequests());
		mockMvc.perform(get("/api/me").cookie(busy)).andExpect(status().isOk());
		mockMvc.perform(get("/api/market/instruments").cookie(busy)).andExpect(status().isOk());
		mockMvc.perform(get(BARS).cookie(other)).andExpect(status().isOk());
	}
}
