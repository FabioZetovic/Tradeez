package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketBarStore.StoredBar;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.SessionCalendar;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * T23 (DEC-067) through the API, on real-style stored minutes: the risk limit set before a session and copied
 * onto each plan, the confidence kept with it, the scorecard of a finished trade, and calibration — none of it
 * before the replay has shown the exit. And T25 (DEC-069): the journal's statistics, by the same rule.
 */
class ScorecardApiTest extends AbstractIntegrationTest {

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

	private ResultActions saveLimit(Cookie who, String body) throws Exception {
		return mockMvc.perform(put("/api/practice/settings").cookie(who).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private JsonNode calibration(Cookie who) throws Exception {
		return read(mockMvc.perform(get("/api/practice/calibration").cookie(who)).andExpect(status().isOk()));
	}

	/** A session and a long with a 40-tick stop — $200 on one contract — and an 80-tick target. */
	private ResultActions lock(String id, long ref, String extra) throws Exception {
		return mockMvc.perform(post("/api/practice/sessions/" + id + "/plan").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"direction\":\"LONG\",\"stopTicks\":%d,\"targetTicks\":%d,\"contracts\":1%s}"
				.formatted(ref - 40, ref + 80, extra)));
	}

	private JsonNode session() throws Exception {
		return session("");
	}

	/** @param extra more of the request, e.g. a killzone */
	private JsonNode session(String extra) throws Exception {
		return read(mockMvc.perform(post("/api/practice/sessions").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"instrument\":\"NQ\",\"timeframe\":\"5m\"" + extra + "}"))
			.andExpect(status().isCreated()));
	}

	private static long reference(JsonNode session) {
		JsonNode bars = session.get("bars");
		return bars.get(bars.size() - 1).get("close").asLong();
	}

	@Test
	@DisplayName("the risk limit: unset, set, refused out of range, each learner's own, and cleared")
	void settings() throws Exception {
		assertThat(read(mockMvc.perform(get("/api/practice/settings").cookie(alice)).andExpect(status().isOk()))
			.has("riskLimitCents")).isFalse();
		assertThat(read(saveLimit(alice, "{\"riskLimitCents\":50000}").andExpect(status().isOk()))
			.get("riskLimitCents").asLong()).isEqualTo(50_000);
		assertThat(read(mockMvc.perform(get("/api/practice/settings").cookie(alice))).get("riskLimitCents").asLong())
			.isEqualTo(50_000);
		saveLimit(alice, "{\"riskLimitCents\":99}").andExpect(status().isBadRequest());
		saveLimit(alice, "{\"riskLimitCents\":100000001}").andExpect(status().isBadRequest());
		assertThat(read(mockMvc.perform(get("/api/practice/settings").cookie(bob))).has("riskLimitCents")).isFalse();
		saveLimit(alice, "{\"riskLimitCents\":100}").andExpect(status().isOk());
		saveLimit(alice, "{}").andExpect(status().isOk());
		assertThat(read(mockMvc.perform(get("/api/practice/settings").cookie(alice))).has("riskLimitCents")).isFalse();
		// T41 (DEC-080): every trade's costs are the standard ones, named as results name them — to read, not to set.
		JsonNode standard = read(mockMvc.perform(get("/api/practice/settings").cookie(alice)));
		assertThat(standard.get("feesCents").asLong()).isEqualTo(450);
		assertThat(standard.get("marketSlippageTicks").asLong()).isEqualTo(1);
		assertThat(standard.get("stopSlippageTicks").asLong()).isEqualTo(1);
		assertThat(standard.get("limitThroughTicks").asLong()).isEqualTo(1);
		assertThat(standard.get("fillModel").asString()).isEqualTo("mkt-1t/slip-1t/thru-1t/comm-450c");
		for (String costs : new String[] { "{\"feesCents\":0}", "{\"riskLimitCents\":20000,\"marketSlippageTicks\":0}",
				"{\"stopSlippageTicks\":2}", "{\"limitThroughTicks\":0}" }) {
			assertThat(read(saveLimit(alice, costs).andExpect(status().isBadRequest())).get("message").asString())
				.isEqualTo("Costs are fixed: every trade pays the standard fees and slippage.");
		}
		// Refused whole: the limit sent with them was not saved either.
		assertThat(read(mockMvc.perform(get("/api/practice/settings").cookie(alice))).has("riskLimitCents")).isFalse();
	}

	@Test
	@DisplayName("a trade keeps its confidence and the limit it was placed under; scored and counted only once its exit is shown")
	void scored() throws Exception {
		saveLimit(alice, "{\"riskLimitCents\":10000}").andExpect(status().isOk());
		JsonNode session = session();
		String id = session.at("/state/id").asString();
		long ref = reference(session);

		JsonNode running = read(lock(id, ref, ",\"confidence\":70").andExpect(status().isOk())).at("/trades/0");
		assertThat(running.at("/plan/confidence").asInt()).isEqualTo(70);
		assertThat(running.at("/plan/riskLimitCents").asLong()).isEqualTo(10_000);
		// The outcome is the future: no scorecard, and nothing for calibration to count.
		assertThat(running.has("scorecard")).isFalse();
		assertThat(calibration(alice).get("trades").asInt()).isZero();

		// A later change to the limit does not reach a trade already placed.
		saveLimit(alice, "{\"riskLimitCents\":50000}").andExpect(status().isOk());
		JsonNode trade = read(mockMvc.perform(post("/api/practice/sessions/" + id + "/skip").cookie(alice).with(csrf()))
			.andExpect(status().isOk())).at("/state/trades/0");
		JsonNode card = trade.get("scorecard");
		assertThat(card.get("version").asString()).isEqualTo("scorecard-1.1.0");
		JsonNode risk = card.at("/process/0");
		assertThat(risk.get("id").asString()).isEqualTo("risk");
		assertThat(risk.get("verdict").asString()).isEqualTo("BROKEN");
		// The standard costs (DEC-072): a tick in, a tick out at $5, $4.50 a round trip (BUG-088).
		assertThat(risk.get("text").asString())
			.isEqualTo("Risked $200.00; $214.50 at the stop with slippage and fees, $114.50 over your $100.00 limit.");
		assertThat(card.get("outcome").findValuesAsString("id")).containsExactly("adverse", "favourable", "costs");
		assertThat(card.get("process").findValuesAsString("id")).contains("plan", "execution", "confidence");

		String outcome = trade.at("/result/outcome").asString();
		JsonNode calibration = calibration(alice);
		assertThat(calibration.get("version").asString()).isEqualTo("calibration-1.0.0");
		assertThat(calibration.get("trades").asInt()).isEqualTo(1);
		boolean decided = outcome.equals("TARGET") || outcome.equals("STOP");
		assertThat(calibration.get("decided").asInt()).isEqualTo(decided ? 1 : 0);
		assertThat(calibration.get("hits").asInt()).isEqualTo(outcome.equals("TARGET") ? 1 : 0);
		assertThat(calibration.get("buckets").size()).isEqualTo(decided ? 1 : 0);
		// Someone else's trades are not theirs to read.
		assertThat(calibration(bob).get("trades").asInt()).isZero();
	}

	@Test
	@DisplayName("T25: statistics count a trade once its exit is shown — its model, side and own rules — and only the learner's")
	void statistics() throws Exception {
		saveLimit(alice, "{\"riskLimitCents\":10000}").andExpect(status().isOk());
		// Placed at the hidden moment, inside the New York AM killzone it was asked for.
		JsonNode session = session(",\"killzone\":\"NEW_YORK_AM\"");
		String id = session.at("/state/id").asString();
		JsonNode placed = read(lock(id, reference(session), ",\"model\":\"OTE\"").andExpect(status().isOk()));
		assertThat(placed.at("/trades/0/plan/killzone").asString()).isEqualTo("NEW_YORK_AM");
		JsonNode running = read(mockMvc.perform(get("/api/practice/statistics").cookie(alice)).andExpect(status().isOk()));
		assertThat(running.get("version").asString()).isEqualTo("journal-stats-1.0.0");
		assertThat(running.at("/overall/trades").asInt()).isZero();

		JsonNode trade = read(mockMvc.perform(post("/api/practice/sessions/" + id + "/skip").cookie(alice).with(csrf()))
			.andExpect(status().isOk())).at("/state/trades/0");
		JsonNode stats = read(mockMvc.perform(get("/api/practice/statistics").cookie(alice)).andExpect(status().isOk()));
		assertThat(stats.at("/overall/trades").asInt()).isEqualTo(1);
		assertThat(stats.at("/overall/totalR").asDouble()).isEqualTo(trade.at("/result/rMultiple").asDouble());
		JsonNode dimensions = stats.get("dimensions");
		assertThat(dimensions.findValuesAsString("id")).containsExactly("model", "killzone", "weekday", "side", "checks", "rules");
		assertThat(dimensions.get(0).at("/groups/0/label").asString()).isEqualTo("OTE model");
		assertThat(dimensions.get(1).at("/groups/0/label").asString()).isEqualTo("New York AM");
		assertThat(trade.get("scorecard").get("process").findValuesAsString("text")).contains("Placed in the New York AM killzone.");
		assertThat(dimensions.get(3).at("/groups/0/label").asString()).isEqualTo("Long");
		// $200 risked against a $100 limit: a rule broken, as the scorecard says.
		assertThat(dimensions.get(5).at("/groups/0/label").asString()).isEqualTo("Some broken");
		assertThat(read(mockMvc.perform(get("/api/practice/statistics").cookie(bob))).at("/overall/trades").asInt()).isZero();
	}

	@Test
	@DisplayName("a confidence is a whole ten from 10 to 90; a trade without one is not counted")
	void confidence() throws Exception {
		JsonNode session = session();
		String id = session.at("/state/id").asString();
		long ref = reference(session);
		lock(id, ref, ",\"confidence\":75").andExpect(status().isBadRequest());
		lock(id, ref, ",\"confidence\":100").andExpect(status().isBadRequest());
		lock(id, ref, ",\"confidence\":0").andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM trade_plan", Integer.class)).isZero();

		JsonNode plan = read(lock(id, ref, "").andExpect(status().isOk())).at("/trades/0/plan");
		assertThat(plan.has("confidence")).isFalse();
		assertThat(plan.has("riskLimitCents")).isFalse();
		JsonNode trade = read(mockMvc.perform(post("/api/practice/sessions/" + id + "/skip").cookie(alice).with(csrf()))
			.andExpect(status().isOk())).at("/state/trades/0");
		assertThat(trade.at("/scorecard/process/0/text").asString()).endsWith("No limit was set: set one before a session to have it checked.");
		assertThat(trade.at("/scorecard/process").findValuesAsString("id")).doesNotContain("confidence");
		assertThat(calibration(alice).get("trades").asInt()).isZero();
	}
}
