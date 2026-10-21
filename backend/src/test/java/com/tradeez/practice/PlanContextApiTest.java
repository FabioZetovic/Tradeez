package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Killzone;
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.MarketBarStore.StoredBar;
import com.tradeez.marketdata.SessionCalendar;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * T22 (DEC-066) — a plan's context through the API, on real-style stored minutes: what the app sees for a
 * bracket being drafted, and the same kept with the plan once placed — the model, bias and expected
 * liquidity the learner chose, the killzone, the daily-bias suggestion and the model's checklist.
 */
class PlanContextApiTest extends AbstractIntegrationTest {

	private static final Instant DATA_FROM = Instant.parse("2025-03-03T00:00:00Z");
	private static final Instant DATA_TO = Instant.parse("2025-03-15T00:00:00Z");

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

	@Autowired
	MarketBarStore barStore;

	@Autowired
	MarketDataService marketData;

	private Cookie alice;
	private Cookie bob;

	@BeforeEach
	void importTwoWeeks() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		jdbc.update("DELETE FROM drill_setup");
		jdbc.update("DELETE FROM drill");
		jdbc.update("DELETE FROM detected_setup");
		jdbc.update("DELETE FROM market_bar");
		jdbc.update("DELETE FROM market_data_import");
		alice = register("alice@example.com");
		bob = register("bob@example.com");
		UUID nq = marketData.requireInstrument("NQ").getId();
		Random random = new Random(20250303L);
		List<StoredBar> bars = new ArrayList<>();
		long price = 80_000;
		for (Instant t = DATA_FROM; t.isBefore(DATA_TO); t = t.plusSeconds(60)) {
			if (!SessionCalendar.isOpen(t)) {
				continue;
			}
			long open = price;
			long close = open + random.nextInt(17) - 8;
			bars.add(new StoredBar(new Bar(t, open, Math.max(open, close) + random.nextInt(4),
				Math.min(open, close) - random.nextInt(4), close, 10), 202503));
			price = close;
		}
		barStore.insert(nq, "1m", bars);
		jdbc.update("""
			INSERT INTO market_data_import (instrument_id, source, dataset, symbol, resolution,
			    range_from, range_to, estimated_cost_usd, bars_stored, status)
			VALUES (?, 'massive', 'CME', 'NQH5', '1m', ?, ?, 0, ?, 'DONE')""",
			nq, Timestamp.from(DATA_FROM), Timestamp.from(DATA_TO), bars.size());
	}

	private Cookie register(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"T\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
	}

	private JsonNode read(ResultActions actions) throws Exception {
		return json.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private JsonNode session() throws Exception {
		return read(mockMvc.perform(post("/api/practice/sessions").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"instrument\":\"NQ\",\"timeframe\":\"5m\"}"))
			.andExpect(status().isCreated()));
	}

	private static long reference(JsonNode session) {
		JsonNode bars = session.get("bars");
		return bars.get(bars.size() - 1).get("close").asLong();
	}

	private ResultActions context(Cookie who, String id, String body) throws Exception {
		return mockMvc.perform(post("/api/practice/sessions/" + id + "/context").cookie(who).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** The killzone the hidden moment lies in, read from the database: the client never sees the real time. */
	private String killzoneAtStart(String id) {
		Instant start = jdbc.queryForObject("SELECT cursor_time FROM practice_session WHERE id = ?::uuid", Timestamp.class, id)
			.toInstant();
		for (Killzone zone : Killzone.values()) {
			if (zone.contains(start)) {
				return zone.name();
			}
		}
		return null;
	}

	@Test
	@DisplayName("a draft's context: the killzone, the daily-bias suggestion with its reasons, and the named model's checklist")
	void context() throws Exception {
		JsonNode session = session();
		String id = session.at("/state/id").asString();
		long ref = reference(session);
		String bracket = "\"direction\":\"LONG\",\"stopTicks\":%d,\"targetTicks\":%d".formatted(ref - 40, ref + 80);

		JsonNode plain = read(context(alice, id, "{" + bracket + "}").andExpect(status().isOk()));
		assertThat(plain.get("biasRule").asString()).isEqualTo("daily-bias-1.0.0");
		assertThat(plain.get("biasReasoning").asString()).startsWith("Yesterday");
		assertThat(plain.has("checklist")).isFalse();
		String killzone = killzoneAtStart(id);
		assertThat(plain.has("killzone") ? plain.get("killzone").asString() : null).isEqualTo(killzone);

		JsonNode checked = read(context(alice, id, "{" + bracket + ",\"model\":\"MODEL_2022\"}").andExpect(status().isOk()));
		JsonNode checklist = checked.get("checklist");
		assertThat(checklist.get("version").asString()).isEqualTo("model-2022-1.0.0");
		assertThat(checklist.get("title").asString()).isEqualTo("The 2022 model");
		assertThat(checklist.get("items").findValuesAsString("id")).containsExactly("killzone", "shift", "gap", "stop", "target");
		assertThat(checklist.get("total").asInt()).isEqualTo(5);
		assertThat(checklist.at("/items/0/met").asBoolean())
			.isEqualTo(killzone != null && !killzone.equals("ASIA"));

		// Reading changes nothing; someone else's session is nobody's.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM trade_plan", Integer.class)).isZero();
		context(bob, id, "{" + bracket + "}").andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("a placed plan keeps what the learner chose and what the app saw, written once with it")
	void kept() throws Exception {
		JsonNode session = session();
		String id = session.at("/state/id").asString();
		long ref = reference(session);
		String bracket = "\"direction\":\"LONG\",\"stopTicks\":%d,\"targetTicks\":%d".formatted(ref - 40, ref + 80);
		JsonNode seen = read(context(alice, id, "{" + bracket + ",\"model\":\"SILVER_BULLET\"}").andExpect(status().isOk()));

		JsonNode state = read(mockMvc.perform(post("/api/practice/sessions/" + id + "/plan").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{" + bracket + ",\"contracts\":1,\"model\":\"SILVER_BULLET\",\"bias\":\"BULLISH\",\"dolTicks\":" + (ref + 100)
				+ "}"))
			.andExpect(status().isOk()));
		JsonNode plan = state.at("/trades/0/plan");
		assertThat(plan.get("model").asString()).isEqualTo("SILVER_BULLET");
		assertThat(plan.get("bias").asString()).isEqualTo("BULLISH");
		assertThat(plan.get("dolTicks").asLong()).isEqualTo(ref + 100);
		assertThat(plan.has("killzone") ? plan.get("killzone").asString() : null)
			.isEqualTo(seen.has("killzone") ? seen.get("killzone").asString() : null);
		assertThat(plan.has("suggestedBias") ? plan.get("suggestedBias").asString() : null)
			.isEqualTo(seen.has("suggestedBias") ? seen.get("suggestedBias").asString() : null);
		// The same moment: the checklist placed is the checklist drafted.
		assertThat(plan.get("checklist")).isEqualTo(seen.get("checklist"));
		assertThat(jdbc.queryForObject("SELECT checklist->>'version' FROM trade_plan", String.class)).isEqualTo("model-sb-1.0.0");
	}

	@Test
	@DisplayName("a simulated market gets no daily-bias reading: nothing is taught from invented data")
	void simulated() throws Exception {
		JsonNode session = read(mockMvc.perform(post("/api/practice/sessions").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"instrument\":\"DEMO-NQ\",\"timeframe\":\"5m\"}"))
			.andExpect(status().isCreated()));
		long ref = reference(session);
		JsonNode context = read(context(alice, session.at("/state/id").asString(),
			"{\"direction\":\"LONG\",\"stopTicks\":%d,\"targetTicks\":%d}".formatted(ref - 40, ref + 80)).andExpect(status().isOk()));
		assertThat(context.get("biasReasoning").asString()).startsWith("A simulated market");
		assertThat(context.has("suggestedBias")).isFalse();
	}

	@Test
	@DisplayName("a bracket alone keeps no model, and expected liquidity far from the price is refused")
	void plain() throws Exception {
		JsonNode session = session();
		String id = session.at("/state/id").asString();
		long ref = reference(session);
		String bracket = "\"direction\":\"LONG\",\"stopTicks\":%d,\"targetTicks\":%d,\"contracts\":1".formatted(ref - 40, ref + 80);
		mockMvc.perform(post("/api/practice/sessions/" + id + "/plan").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{" + bracket + ",\"dolTicks\":" + (ref + 30_000) + "}"))
			.andExpect(status().isBadRequest());
		JsonNode plan = read(mockMvc.perform(post("/api/practice/sessions/" + id + "/plan").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{" + bracket + "}"))
			.andExpect(status().isOk())).at("/trades/0/plan");
		assertThat(plan.has("model")).isFalse();
		assertThat(plan.has("checklist")).isFalse();
		assertThat(plan.has("bias")).isFalse();
	}
}
