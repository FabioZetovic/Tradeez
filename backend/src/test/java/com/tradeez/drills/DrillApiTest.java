package com.tradeez.drills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeez.AbstractIntegrationTest;
import com.tradeez.analysis.FvgDetector;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;

import jakarta.servlet.http.Cookie;

/**
 * The FVG identification drill (task 7).
 *
 * <p>Three kinds of assertion. The drill must be <b>right</b>: its answer key is exactly the rule's
 * hits in the window. It must be <b>fair</b>: nothing gives the answer away before it is given, and
 * an answer cannot be changed afterwards. And it must be <b>honest</b>: on simulated data it says it
 * is a fixture, and the database refuses to let it be graded (DEC-034).
 */
class DrillApiTest extends AbstractIntegrationTest {

	private static final String PASSWORD = "a-perfectly-fine-passphrase";

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
	MarketDataService marketData;

	private Cookie alice;
	private Cookie bob;

	@BeforeEach
	void signInTwoUsers() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch(); // drills cascade from their user
		alice = register("alice@example.com");
		bob = register("bob@example.com");
	}

	private Cookie register(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
					+ "\",\"displayName\":\"T\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
	}

	private JsonNode read(ResultActions actions) throws Exception {
		return json.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private ResultActions start(Cookie who, String body) throws Exception {
		return mockMvc.perform(post("/api/drills").cookie(who).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private JsonNode start(Cookie who) throws Exception {
		return read(start(who, "{\"kind\":\"FVG_IDENTIFICATION\",\"timeframe\":\"5m\"}")
			.andExpect(status().isCreated()));
	}

	private ResultActions answer(Cookie who, String id, List<Long> marks) throws Exception {
		return mockMvc.perform(post("/api/drills/" + id + "/answer").cookie(who).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"marks\":" + json.writeValueAsString(marks) + "}"));
	}

	/** The answer key's middle candles, read from the database — what a perfect answer marks. */
	private List<Long> anchors(String drillId) {
		return jdbc.queryForList("""
			SELECT extract(epoch FROM s.anchor_time)::bigint FROM drill_setup ds
			JOIN detected_setup s ON s.id = ds.setup_id
			WHERE ds.drill_id = ?::uuid ORDER BY s.anchor_time""", Long.class, drillId);
	}

	private static List<Long> barTimes(JsonNode drill) {
		List<Long> times = new ArrayList<>();
		drill.get("bars").forEach(b -> times.add(b.get("time").asLong()));
		return times;
	}

	private static boolean absent(JsonNode node, String pointer) {
		JsonNode value = node.at(pointer);
		return value.isMissingNode() || value.isNull();
	}

	@Test
	@DisplayName("drills require authentication")
	void requiresAuth() throws Exception {
		mockMvc.perform(post("/api/drills").with(csrf())).andExpect(status().isUnauthorized());
	}

	@Nested
	@DisplayName("a new drill")
	class NewDrill {

		@Test
		@DisplayName("shows thirty consecutive candles and withholds the answer")
		void withholdsTheAnswer() throws Exception {
			JsonNode drill = start(alice);
			List<Long> times = barTimes(drill);

			assertThat(times).hasSize(DrillService.WINDOW_BARS);
			for (int i = 1; i < times.size(); i++) {
				assertThat(times.get(i) - times.get(i - 1)).as("one unbroken stretch").isEqualTo(300);
			}
			assertThat(absent(drill, "/result")).isTrue();
			assertThat(absent(drill, "/marks")).isTrue();
			// No answer-key data under any name.
			assertThat(drill.toString()).doesNotContain("anchorTime").doesNotContain("zoneLow");
		}

		@Test
		@DisplayName("says it is a developer fixture, never graded (DEC-034)")
		void labelledAsFixture() throws Exception {
			JsonNode drill = start(alice);
			assertThat(drill.get("fixture").asBoolean()).isTrue();
			assertThat(drill.get("graded").asBoolean()).isFalse();
			assertThat(drill.get("dataClass").asText()).isEqualTo("SIMULATED");
			assertThat(drill.get("fixtureNotice").asText()).contains("not a graded exercise");
		}

		@Test
		@DisplayName("carries the versioned rule it was built from (DEC-026)")
		void carriesTheRule() throws Exception {
			JsonNode rule = start(alice).get("rule");
			assertThat(rule.get("version").asText()).isEqualTo(FvgDetector.RULE_VERSION);
			assertThat(rule.at("/parameters/minGapPercentOfAverageRange").asInt()).isEqualTo(25);
			assertThat(rule.get("definition").asText()).contains("25%").contains("14 candles");
		}

		@Test
		@DisplayName("has an answer key that is exactly the rule's gaps with all three candles on screen")
		void answerKeyIsTheRule() throws Exception {
			JsonNode drill = start(alice);
			String id = drill.get("id").asText();
			List<Long> times = barTimes(drill);
			Instant from = Instant.ofEpochSecond(times.getFirst());
			Instant to = Instant.ofEpochSecond(times.getLast() + 300);

			// Recomputed independently: the detector over the same market, with its lookback.
			Instrument nq = marketData.requireInstrument("DEMO-NQ");
			List<Bar> bars = marketData.internalBars(nq, Timeframe.M5, from.minusSeconds(15 * 300),
				to, MarketDataService.PUBLIC_CHART_SEED).bars();
			List<Long> expected = FvgDetector.detect(bars, Timeframe.M5,
					FvgDetector.Parameters.DEFAULT).stream()
				.filter(h -> !h.candle1().openTime().isBefore(from) && h.candle3().openTime().isBefore(to))
				.map(h -> h.anchorTime().getEpochSecond())
				.toList();

			assertThat(anchors(id)).isEqualTo(expected);
			assertThat(expected).hasSizeBetween(DrillService.MIN_GAPS, DrillService.MAX_GAPS);
		}

		@Test
		@DisplayName("refuses one-minute drills with the reason, and timeframes it does not run on")
		void timeframes() throws Exception {
			start(alice, "{\"timeframe\":\"1m\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(Matchers.containsString("not tuned")));
			start(alice, "{\"timeframe\":\"1h\"}").andExpect(status().isBadRequest());
			start(alice, "{\"kind\":\"BOS_CLASSIFICATION\"}").andExpect(status().isBadRequest());
			start(alice, "{\"instrument\":\"NOPE\"}").andExpect(status().isNotFound());
			start(alice, "{\"timeframe\":\"15m\"}").andExpect(status().isCreated())
				.andExpect(jsonPath("$.timeframe").value("15m"));
		}
	}

	@Nested
	@DisplayName("an answer")
	class Answers {

		@Test
		@DisplayName("that marks every gap finds them all, with an explanation for each")
		void perfect() throws Exception {
			String id = start(alice).get("id").asText();
			List<Long> key = anchors(id);

			JsonNode result = read(answer(alice, id, key).andExpect(status().isOk())).get("result");

			assertThat(result.get("found").asInt()).isEqualTo(key.size());
			assertThat(result.get("missed").asInt()).isZero();
			assertThat(result.get("extra").asInt()).isZero();
			for (JsonNode setup : result.get("setups")) {
				assertThat(setup.get("found").asBoolean()).isTrue();
				// The explanation's raw material: the numbers the rule decided on.
				assertThat(setup.at("/evidence/gapTicks").asLong())
					.isGreaterThanOrEqualTo(setup.at("/evidence/thresholdTicks").asLong());
				assertThat(setup.get("direction").asText()).isIn("BULLISH", "BEARISH");
			}
		}

		@Test
		@DisplayName("is scored as found, missed and extra — feedback, not a grade")
		void partial() throws Exception {
			JsonNode drill = start(alice);
			String id = drill.get("id").asText();
			List<Long> key = anchors(id);
			Long wrong = barTimes(drill).stream().filter(t -> !key.contains(t)).findFirst().orElseThrow();

			JsonNode result = read(answer(alice, id, List.of(key.getFirst(), wrong))
				.andExpect(status().isOk())).get("result");

			assertThat(result.get("found").asInt()).isEqualTo(1);
			assertThat(result.get("missed").asInt()).isEqualTo(key.size() - 1);
			assertThat(result.get("extra").asInt()).isEqualTo(1);
			assertThat(result.at("/extraMarks/0").asLong()).isEqualTo(wrong);
		}

		@Test
		@DisplayName("with no marks at all is allowed, and misses everything")
		void empty() throws Exception {
			String id = start(alice).get("id").asText();
			JsonNode result = read(answer(alice, id, List.of()).andExpect(status().isOk())).get("result");
			assertThat(result.get("found").asInt()).isZero();
			assertThat(result.get("missed").asInt()).isEqualTo(anchors(id).size());
		}

		@Test
		@DisplayName("must mark candles that are in the window")
		void marksMustBeCandles() throws Exception {
			JsonNode drill = start(alice);
			String id = drill.get("id").asText();
			long before = barTimes(drill).getFirst() - 300;
			answer(alice, id, List.of(before)).andExpect(status().isBadRequest());
			answer(alice, id, List.of(barTimes(drill).getFirst() + 1)).andExpect(status().isBadRequest());
			// A refused answer did not use up the drill.
			answer(alice, id, List.of()).andExpect(status().isOk());
		}

		@Test
		@DisplayName("is given once; the result stays on the drill afterwards")
		void once() throws Exception {
			String id = start(alice).get("id").asText();
			List<Long> key = anchors(id);
			answer(alice, id, key).andExpect(status().isOk());
			answer(alice, id, List.of()).andExpect(status().isConflict());

			JsonNode resumed = read(mockMvc.perform(get("/api/drills/" + id).cookie(alice))
				.andExpect(status().isOk()));
			assertThat(resumed.at("/result/found").asInt()).isEqualTo(key.size());
			assertThat(resumed.get("marks").size()).isEqualTo(key.size());
		}
	}

	@Nested
	@DisplayName("the database")
	class Database {

		@Test
		@DisplayName("refuses to grade a drill on simulated data (DEC-034)")
		void neverGraded() throws Exception {
			String id = start(alice).get("id").asText();
			assertThatThrownBy(() -> jdbc.update("UPDATE drill SET graded = true WHERE id = ?::uuid", id))
				.hasMessageContaining("ck_drill_simulated_never_graded");
		}

		@Test
		@DisplayName("refuses to change an answer once given")
		void answerIsImmutable() throws Exception {
			String id = start(alice).get("id").asText();
			answer(alice, id, anchors(id)).andExpect(status().isOk());
			assertThatThrownBy(() -> jdbc.update(
				"UPDATE drill SET marks = '[]'::jsonb WHERE id = ?::uuid", id))
				.hasMessageContaining("immutable");
		}
	}

	@Test
	@DisplayName("another user's drill is indistinguishable from one that does not exist (DEC-022)")
	void isolation() throws Exception {
		String id = start(alice).get("id").asText();
		mockMvc.perform(get("/api/drills/" + id).cookie(bob)).andExpect(status().isNotFound());
		answer(bob, id, List.of()).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/drills/" + UUID.randomUUID()).cookie(bob))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("No such drill."));
		// Alice's drill is untouched by any of it.
		assertThat(absent(read(mockMvc.perform(get("/api/drills/" + id).cookie(alice))), "/result"))
			.isTrue();
	}

	@Test
	@DisplayName("creation is capped per hour, per user")
	void capped() throws Exception {
		UUID aliceId = users.findByEmailIgnoreCase("alice@example.com").orElseThrow().getId();
		UUID nq = marketData.requireInstrument("DEMO-NQ").getId();
		List<Object[]> rows = new ArrayList<>();
		for (int i = 0; i < DrillService.MAX_DRILLS_PER_HOUR; i++) {
			rows.add(new Object[] { aliceId, nq });
		}
		jdbc.batchUpdate("""
			INSERT INTO drill (user_id, kind, instrument_id, timeframe, data_class, seed,
			    generator_version, detector, rule_version, window_from, window_to)
			VALUES (?, 'FVG_IDENTIFICATION', ?, '5m', 'SIMULATED', 42, 'x', 'FVG', 'fvg-1.0.0',
			    now(), now() + interval '1 hour')""", rows);

		start(alice, "{}").andExpect(status().isTooManyRequests());
		start(bob, "{}").andExpect(status().isCreated());
	}
}
