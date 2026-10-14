package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeez.AbstractIntegrationTest;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;

import jakarta.servlet.http.Cookie;

/**
 * BUG-033 — market data is generated with no database transaction open, and a session that changes
 * while it is being generated for is refused rather than overwritten.
 */
class PracticeTransactionTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper json;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	AppUserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@MockitoSpyBean
	MarketDataService marketData;

	/** One entry per generation call: was a transaction open at the time? */
	private final List<Boolean> transactionOpenDuringGeneration = new CopyOnWriteArrayList<>();

	/** Run during the next generation call, to simulate another request landing mid-way. */
	private volatile Runnable duringNextGeneration;

	private Cookie alice;

	@BeforeEach
	void setUp() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		alice = mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"tx@example.com\",\"password\":\"a-perfectly-fine-passphrase\","
					+ "\"displayName\":\"T\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);

		transactionOpenDuringGeneration.clear();
		duringNextGeneration = null;
		doAnswer(call -> {
			transactionOpenDuringGeneration.add(
				TransactionSynchronizationManager.isActualTransactionActive());
			Runnable interloper = duringNextGeneration;
			if (interloper != null) {
				duringNextGeneration = null;
				interloper.run();
			}
			return call.callRealMethod();
		}).when(marketData).internalBars(any(Instrument.class), any(Timeframe.class), any(), any(),
			anyLong());
	}

	private JsonNode read(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
		return json.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	@Test
	@DisplayName("no practice command generates data while holding a transaction")
	void generationIsOutsideTransactions() throws Exception {
		JsonNode session = read(mockMvc.perform(post("/api/practice/sessions").cookie(alice)
			.with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"instrument\":\"DEMO-NQ\"}"))
			.andExpect(status().isCreated()));
		String id = session.at("/state/id").asText();
		JsonNode bars = session.get("bars");
		long reference = bars.get(bars.size() - 1).get("close").asLong();

		mockMvc.perform(get("/api/practice/sessions/" + id).cookie(alice))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/practice/sessions/" + id + "/advance").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"bars\":2}"))
			.andExpect(status().isOk());

		JsonNode afterStep = read(mockMvc.perform(get("/api/practice/sessions/" + id).cookie(alice)));
		JsonNode latest = afterStep.get("bars");
		reference = latest.get(latest.size() - 1).get("close").asLong();
		// A step could land on the daily break; only plan if the market is open there.
		var planResult = mockMvc.perform(post("/api/practice/sessions/" + id + "/plan").cookie(alice)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"direction\":\"LONG\",\"stopTicks\":%d,\"targetTicks\":%d,\"contracts\":1}"
					.formatted(reference - 40, reference + 80)))
			.andReturn().getResponse().getStatus();
		if (planResult == 200) {
			mockMvc.perform(post("/api/practice/sessions/" + id + "/skip").cookie(alice).with(csrf()))
				.andExpect(status().isOk());
		}

		assertThat(transactionOpenDuringGeneration)
			.as("every generation call, across create, get, advance, plan and skip")
			.hasSizeGreaterThanOrEqualTo(4)
			.containsOnly(false);
	}

	@Test
	@DisplayName("a session that moves while bars are generated for it is refused, not overwritten")
	void raceIsRefused() throws Exception {
		JsonNode session = read(mockMvc.perform(post("/api/practice/sessions").cookie(alice)
			.with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"instrument\":\"DEMO-NQ\"}"))
			.andExpect(status().isCreated()));
		String id = session.at("/state/id").asText();
		long cursorBefore = session.at("/state/cursor").asLong();

		// Another request steps the same session while this one is generating its bars.
		duringNextGeneration = () -> jdbc.update(
			"UPDATE practice_session SET version = version + 1 WHERE id = ?::uuid", id);

		mockMvc.perform(post("/api/practice/sessions/" + id + "/advance").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"bars\":1}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(
				org.hamcrest.Matchers.containsString("changed by another request")));

		// The losing request changed nothing.
		assertThat(read(mockMvc.perform(get("/api/practice/sessions/" + id).cookie(alice)))
			.at("/state/cursor").asLong()).isEqualTo(cursorBefore);
	}

	@Test
	@DisplayName("a plan resolved for a cursor that has since moved is not stored")
	void planRaceIsRefused() throws Exception {
		JsonNode session = read(mockMvc.perform(post("/api/practice/sessions").cookie(alice)
			.with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"instrument\":\"DEMO-NQ\"}"))
			.andExpect(status().isCreated()));
		String id = session.at("/state/id").asText();
		JsonNode bars = session.get("bars");
		long reference = bars.get(bars.size() - 1).get("close").asLong();

		duringNextGeneration = () -> jdbc.update(
			"UPDATE practice_session SET version = version + 1 WHERE id = ?::uuid", id);

		mockMvc.perform(post("/api/practice/sessions/" + id + "/plan").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"direction\":\"LONG\",\"stopTicks\":%d,\"targetTicks\":%d,\"contracts\":1}"
					.formatted(reference - 40, reference + 80)))
			.andExpect(status().isConflict());

		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM trade_plan WHERE session_id = ?::uuid", Long.class, id)).isZero();
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM practice_result WHERE session_id = ?::uuid", Long.class, id)).isZero();
	}
}
