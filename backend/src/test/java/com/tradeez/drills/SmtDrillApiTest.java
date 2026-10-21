package com.tradeez.drills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.ThreeWeeksOfEs;
import com.tradeez.ThreeWeeksOfNq;
import com.tradeez.analysis.DetectorRegistry;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DEC-075 — "find the SMT" on stored data: NQ from {@link ThreeWeeksOfNq} with {@link ThreeWeeksOfEs} beside it. The
 * drill and the ICT-66 lesson both carry ES's candles over the same minutes, and a window never holds either
 * market's roll.
 */
class SmtDrillApiTest extends AbstractIntegrationTest {

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

	@Autowired
	DrillService drillService;

	@Autowired
	DetectorRegistry detectors;

	private Cookie alice;

	@BeforeEach
	void importBoth() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		alice = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"alice@example.com\",\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"A\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
		if (!ThreeWeeksOfNq.present(marketData)) {
			jdbc.update("DELETE FROM drill_setup");
			jdbc.update("DELETE FROM drill");
			jdbc.update("DELETE FROM detected_setup");
			jdbc.update("DELETE FROM market_bar");
			jdbc.update("DELETE FROM market_data_import");
			ThreeWeeksOfNq.insert(marketData, barStore, jdbc);
		}
		ThreeWeeksOfEs.remove(marketData, jdbc);
		ThreeWeeksOfEs.insert(marketData, barStore);
	}

	@AfterEach
	void removeEs() {
		users.deleteAllInBatch(); // drills cascade from their user
		ThreeWeeksOfEs.remove(marketData, jdbc);
	}

	private JsonNode read(ResultActions actions) throws Exception {
		return json.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private static List<Long> times(JsonNode bars) {
		List<Long> times = new ArrayList<>();
		bars.forEach(b -> times.add(b.get("time").asLong()));
		return times;
	}

	private List<Long> key(String drillId) {
		return jdbc.queryForList("""
			SELECT extract(epoch FROM s.anchor_time)::bigint FROM drill_setup ds JOIN detected_setup s ON s.id = ds.setup_id
			WHERE ds.drill_id = ?::uuid AND s.detector = 'SMT' ORDER BY s.anchor_time""", Long.class, drillId);
	}

	@Test
	@DisplayName("the drill shows ES under NQ, candle for candle, and is graded on NQ's divergences")
	void drill() throws Exception {
		JsonNode drill = read(mockMvc.perform(post("/api/drills").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"SMT_IDENTIFICATION\",\"timeframe\":\"5m\"}"))
			.andExpect(status().isCreated()));
		assertThat(drill.get("mode").asString()).isEqualTo("IDENTIFY");
		assertThat(drill.at("/rule/version").asString()).isEqualTo("smt-1.0.0");
		assertThat(drill.get("instrument").asString()).isEqualTo("NQ");
		assertThat(drill.at("/companion/instrument").asString()).isEqualTo("ES");
		assertThat(drill.at("/companion/tickSize").asDouble()).isEqualTo(0.25);
		List<Long> nq = times(drill.get("bars"));
		assertThat(nq).hasSize(DrillService.WINDOW_BARS);
		assertThat(times(drill.at("/companion/bars"))).isEqualTo(nq);
		long from = nq.getFirst();
		long to = nq.getLast() + 300;
		long esRoll = ThreeWeeksOfEs.ROLL.getEpochSecond();
		long nqRoll = ThreeWeeksOfNq.ROLL.getEpochSecond();
		assertThat(esRoll > from && esRoll < to).as("ES's roll is not in the window").isFalse();
		assertThat(nqRoll > from && nqRoll < to).as("nor NQ's").isFalse();

		String id = drill.get("id").asString();
		List<Long> key = key(id);
		assertThat(key).hasSizeBetween(1, 4).allSatisfy(t -> assertThat(nq).contains(t));
		JsonNode result = read(mockMvc.perform(post("/api/drills/" + id + "/answer").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"marks\":" + json.writeValueAsString(key) + "}"))
			.andExpect(status().isOk())).get("result");
		assertThat(result.get("found").asInt()).isEqualTo(key.size());
		assertThat(result.get("missed").asInt()).isZero();
		for (JsonNode setup : result.get("setups")) {
			assertThat(setup.get("label").asString()).endsWith("SMT divergence");
			assertThat(setup.get("explanation").toString()).contains("On ES");
		}
		// The answered drill reads back the same, ES and all.
		JsonNode again = read(mockMvc.perform(get("/api/drills/" + id).cookie(alice)).andExpect(status().isOk()));
		assertThat(times(again.at("/companion/bars"))).isEqualTo(nq);
	}

	@Test
	@DisplayName("a window holds no roll of ES either, and every one of its candles; a one-market rule asks neither")
	void besideWhole() {
		var nq = marketData.requireInstrument("NQ");
		var smt = detectors.require("SMT");
		Instant clear = ThreeWeeksOfEs.ROLL.plus(Duration.ofHours(1));
		assertThat(drillService.besideWhole(smt, nq, Timeframe.M5, clear, clear.plus(Duration.ofMinutes(150)), 30)).isTrue();
		Instant across = ThreeWeeksOfEs.ROLL.minus(Duration.ofHours(1));
		assertThat(drillService.besideWhole(smt, nq, Timeframe.M5, across, across.plus(Duration.ofMinutes(150)), 30))
			.as("ES rolls inside it").isFalse();
		jdbc.update("DELETE FROM market_bar WHERE instrument_id = ? AND open_time >= ? AND open_time < ?",
			marketData.requireInstrument("ES").getId(), java.sql.Timestamp.from(clear.plus(Duration.ofMinutes(30))),
			java.sql.Timestamp.from(clear.plus(Duration.ofMinutes(40))));
		assertThat(drillService.besideWhole(smt, nq, Timeframe.M5, clear, clear.plus(Duration.ofMinutes(150)), 30))
			.as("two of ES's candles missing").isFalse();
		assertThat(drillService.besideWhole(detectors.require("FVG"), nq, Timeframe.M5, across,
			across.plus(Duration.ofMinutes(150)), 30)).isTrue();
	}

	@Test
	@DisplayName("no drill is asked where ES is missing candles: without its first five minutes of every hour, none is")
	void neverWithEsMissing() throws Exception {
		jdbc.update("DELETE FROM market_bar WHERE instrument_id = ? AND extract(minute FROM open_time) < 5",
			marketData.requireInstrument("ES").getId());
		mockMvc.perform(post("/api/drills").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"SMT_IDENTIFICATION\",\"timeframe\":\"5m\"}"))
			.andExpect(status().isServiceUnavailable());
	}

	@Test
	@DisplayName("ICT-66's lesson shows each example with ES's candles over the same minutes")
	void lesson() throws Exception {
		JsonNode lesson = read(mockMvc.perform(get("/api/concepts/ICT-66/lesson?timeframe=5m").cookie(alice))
			.andExpect(status().isOk()));
		assertThat(lesson.get("examples")).isNotEmpty();
		assertThat(lesson.get("drills").findValuesAsString("kind")).containsExactly("SMT_IDENTIFICATION");
		for (JsonNode example : lesson.get("examples")) {
			assertThat(example.at("/companion/instrument").asString()).isEqualTo("ES");
			List<Long> nq = times(example.get("bars"));
			List<Long> es = times(example.at("/companion/bars"));
			assertThat(es).isNotEmpty();
			assertThat(es.getFirst()).isGreaterThanOrEqualTo(nq.getFirst());
			assertThat(es.getLast()).isLessThanOrEqualTo(nq.getLast());
		}
		// A rule that reads one market carries none.
		JsonNode gaps = read(mockMvc.perform(get("/api/concepts/ICT-20/lesson?timeframe=5m").cookie(alice))
			.andExpect(status().isOk()));
		assertThat(gaps.get("examples")).isNotEmpty().allSatisfy(e -> assertThat(e.has("companion")).isFalse());
	}
}
