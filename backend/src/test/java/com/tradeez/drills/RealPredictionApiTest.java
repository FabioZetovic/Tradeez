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

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tradeez.AbstractIntegrationTest;
import com.tradeez.ThreeWeeksOfNq;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;

import jakarta.servlet.http.Cookie;

/**
 * T15 (DEC-058) — prediction drills on imported one-minute NQ ({@link ThreeWeeksOfNq}).
 *
 * <p>The promises. <b>Fair</b>: before the answer, nothing after the moment leaves the server — no
 * candle, no outcome. <b>Honest targets</b>: what is drawn is what the moment saw — resting levels and
 * open gaps, on the chart. <b>Right</b>: the answer is what the candles after the moment did, checked
 * here against the stored candles independently of the engine.
 */
class RealPredictionApiTest extends AbstractIntegrationTest {

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
	DrillService drills;

	private Cookie alice;
	private Instrument nq;

	@BeforeEach
	void importThreeWeeks() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		alice = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"alice@example.com\",\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"A\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
		nq = marketData.requireInstrument("NQ");
		if (ThreeWeeksOfNq.present(marketData)) {
			return;
		}
		jdbc.update("DELETE FROM drill_setup");
		jdbc.update("DELETE FROM drill");
		jdbc.update("DELETE FROM detected_setup");
		jdbc.update("DELETE FROM market_bar");
		jdbc.update("DELETE FROM market_data_import");
		ThreeWeeksOfNq.insert(marketData, barStore, jdbc);
	}

	private JsonNode read(ResultActions actions) throws Exception {
		return json.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private ResultActions start(String body) throws Exception {
		return mockMvc.perform(post("/api/drills").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private JsonNode start(DrillKind kind, String timeframe) throws Exception {
		return read(start("{\"kind\":\"" + kind + "\",\"instrument\":\"NQ\",\"timeframe\":\"" + timeframe + "\"}")
			.andExpect(status().isCreated()));
	}

	private ResultActions choose(String id, String choice) throws Exception {
		return mockMvc.perform(post("/api/drills/" + id + "/answer").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"choice\":\"" + choice + "\"}"));
	}

	private static List<Long> times(JsonNode bars) {
		List<Long> times = new ArrayList<>();
		bars.forEach(b -> times.add(b.get("time").asLong()));
		return times;
	}

	private static JsonNode target(JsonNode drill, String value) {
		for (JsonNode t : drill.at("/question/targets")) {
			if (t.get("value").asString().equals(value)) {
				return t;
			}
		}
		throw new AssertionError("no target " + value);
	}

	/** Whether a candle reaches a target, said independently of the engine: a level a tick beyond, a gap traded into. */
	private static boolean reaches(Bar bar, JsonNode target) {
		boolean above = "ABOVE".equals(target.get("side").asString());
		long low = target.get("lowTicks").asLong();
		long high = target.get("highTicks").asLong();
		if ("LEVEL".equals(target.get("shape").asString())) {
			return above ? bar.highTicks() > low : bar.lowTicks() < low;
		}
		return above ? bar.highTicks() >= low : bar.lowTicks() <= high;
	}

	private List<Bar> stored(Timeframe timeframe, long from, long to) {
		return marketData.internalBars(nq, timeframe, Instant.ofEpochSecond(from), Instant.ofEpochSecond(to), 0).bars();
	}

	@ParameterizedTest
	@EnumSource(value = DrillKind.class, mode = EnumSource.Mode.MATCH_ALL, names = ".*_PREDICTION")
	@DisplayName("shows the chart up to the moment and two targets — and nothing after it, here or on a reload")
	void withholdsTheFuture(DrillKind kind) throws Exception {
		JsonNode drill = start(kind, "5m");
		assertThat(drill.get("mode").asString()).isEqualTo("PREDICT");
		assertThat(drill.get("graded").asBoolean()).isTrue();
		List<Long> times = times(drill.get("bars"));
		assertThat(times).hasSize(DrillService.CLASSIFY_WINDOW_BARS);
		long moment = times.getLast() + 300;
		String id = drill.get("id").asString();
		Instant outcomeAt = jdbc.queryForObject("SELECT outcome_at FROM drill WHERE id = ?::uuid", Instant.class, id);
		assertThat(outcomeAt.getEpochSecond()).as("decided after the chart").isGreaterThanOrEqualTo(moment);

		for (JsonNode shown : List.of(drill, read(mockMvc.perform(get("/api/drills/" + id).cookie(alice))))) {
			assertThat(times(shown.get("bars"))).allMatch(t -> t < moment);
			assertThat(shown.has("result") || shown.has("revealed") || shown.has("choice")).isFalse();
			assertThat(shown.toString()).doesNotContain("outcome").doesNotContain("candlesAfter")
				.doesNotContain("explanation");
			assertThat(shown.at("/question/version").asString()).isEqualTo(Prediction.VERSION);
			assertThat(shown.at("/question/definition").asString()).contains(String.valueOf(Prediction.HORIZON_CANDLES));
			assertThat(shown.at("/question/targets")).hasSize(2);
			assertThat(shown.get("options").findValuesAsString("value")).containsExactlyElementsOf(kind.predictions());
			assertThat(shown.get("options").findValuesAsString("label")).allMatch(l -> !l.isBlank());
		}
	}

	@Test
	@DisplayName("which is taken first: the nearest resting levels either side of the close, swings on the chart")
	void liquidityFirstTargets() throws Exception {
		for (int i = 0; i < 5; i++) {
			JsonNode drill = start(DrillKind.LIQUIDITY_FIRST_PREDICTION, "5m");
			List<Long> times = times(drill.get("bars"));
			long moment = times.getLast() + 300;
			long close = drill.at("/question/closeTicks").asLong();
			assertThat(drill.at("/bars").get(times.size() - 1).get("close").asLong()).isEqualTo(close);
			JsonNode bsl = target(drill, "BSL");
			JsonNode ssl = target(drill, "SSL");
			assertThat(bsl.get("lowTicks").asLong()).isGreaterThanOrEqualTo(close);
			assertThat(ssl.get("lowTicks").asLong()).isLessThanOrEqualTo(close);
			assertThat(bsl.get("distanceTicks").asLong()).isEqualTo(bsl.get("lowTicks").asLong() - close);
			assertThat(bsl.get("role").asString()).isEqualTo("ERL");
			assertThat(bsl.has("obstacleSwings") && ssl.has("obstacleGaps")).isTrue();
			for (JsonNode level : List.of(bsl, ssl)) {
				long from = level.get("fromTime").asLong();
				assertThat(from).as("its swing and the candle before it on the chart").isGreaterThan(times.getFirst());
				// Resting at the moment: no candle after its swing's confirmation traded a tick beyond it.
				List<Bar> after = stored(Timeframe.M5, from + 600, moment);
				assertThat(after).noneMatch(b -> reaches(b, level));
			}
		}
	}

	@Test
	@DisplayName("external or internal: the last candle took a level, the gap behind is untouched, the next level waits")
	void externalOrInternalTargets() throws Exception {
		JsonNode drill = start(DrillKind.ERL_IRL_PREDICTION, "5m");
		List<Long> times = times(drill.get("bars"));
		JsonNode last = drill.get("bars").get(times.size() - 1);
		JsonNode took = drill.at("/question/context/0");
		boolean up = "TAKEN_BSL".equals(took.get("what").asString());
		long level = took.get("lowTicks").asLong();
		assertThat(up ? last.get("high").asLong() > level : last.get("low").asLong() < level)
			.as("the last candle took it").isTrue();
		JsonNode irl = target(drill, "IRL");
		JsonNode erl = target(drill, "ERL");
		assertThat(irl.get("shape").asString()).isEqualTo("ZONE");
		assertThat(irl.get("side").asString()).isEqualTo(up ? "BELOW" : "ABOVE");
		assertThat(erl.get("side").asString()).isEqualTo(up ? "ABOVE" : "BELOW");
		// Untouched since it formed: no candle after its third traded into it.
		long gapStart = irl.get("fromTime").asLong();
		assertThat(stored(Timeframe.M5, gapStart + 900, times.getLast() + 300)).noneMatch(b -> reaches(b, irl));
	}

	@ParameterizedTest
	@EnumSource(value = DrillKind.class, mode = EnumSource.Mode.MATCH_ALL, names = ".*_PREDICTION")
	@DisplayName("the answer is what the candles after the moment did, and they are shown with it")
	void answeredByTheFuture(DrillKind kind) throws Exception {
		JsonNode drill = start(kind, "5m");
		String id = drill.get("id").asString();
		long moment = times(drill.get("bars")).getLast() + 300;
		String outcome = jdbc.queryForObject("SELECT outcome FROM drill WHERE id = ?::uuid", String.class, id);
		String other = kind.predictions().stream().filter(v -> !v.equals(outcome)).findFirst().orElseThrow();

		JsonNode answered = read(choose(id, other).andExpect(status().isOk()));
		JsonNode result = answered.get("result");
		assertThat(result.get("answer").asString()).isEqualTo(outcome);
		assertThat(result.get("correct").asBoolean()).isFalse();
		assertThat(answered.get("choice").asString()).isEqualTo(other);
		assertThat(result.get("explanation")).isNotEmpty();
		assertThat(result.get("explanation").toString()).doesNotContain("null");

		// Checked against the stored candles, not the engine: the deciding candle is the first to reach
		// either target, and it reached the answer.
		List<Long> revealed = times(answered.get("revealed"));
		assertThat(revealed.getFirst()).isGreaterThanOrEqualTo(moment);
		long decided = result.get("outcomeTime").asLong();
		assertThat(revealed).contains(decided);
		JsonNode first = target(answered, outcome);
		JsonNode second = target(answered, other);
		List<Bar> future = stored(Timeframe.M5, moment, decided + 300);
		assertThat(future.getLast().openTime().getEpochSecond()).isEqualTo(decided);
		assertThat(future.subList(0, future.size() - 1)).noneMatch(b -> reaches(b, first) || reaches(b, second));
		Bar deciding = future.getLast();
		assertThat(reaches(deciding, first)).isTrue();
		if (reaches(deciding, second)) {
			// Both in one candle: its minutes decided, and the first minute to reach either reached only the answer.
			Bar minute = stored(Timeframe.M1, decided, decided + 300).stream()
				.filter(b -> reaches(b, first) || reaches(b, second)).findFirst().orElseThrow();
			assertThat(reaches(minute, first) && !reaches(minute, second)).isTrue();
		}
		assertThat(result.get("candlesAfter").asInt()).isEqualTo(future.size());
		assertThat(revealed.size()).isLessThanOrEqualTo(future.size() + Prediction.REVEAL_AFTER);

		// A reload says the same.
		assertThat(read(mockMvc.perform(get("/api/drills/" + id).cookie(alice))).get("result")).isEqualTo(result);
	}

	@Test
	@DisplayName("a future never runs past a contract roll: the next contract's prices are another market")
	void futureStopsAtTheRoll() {
		Instant moment = ThreeWeeksOfNq.ROLL.minusSeconds(3 * 300);
		List<Bar> future = drills.future(nq, Timeframe.M5, moment);
		assertThat(future).hasSize(3);
		assertThat(future).allMatch(b -> b.openTime().isBefore(ThreeWeeksOfNq.ROLL));
		assertThat(drills.future(nq, Timeframe.M5, ThreeWeeksOfNq.ROLL.minusSeconds(86_400 * 3)))
			.as("far from it, the whole horizon").hasSize(Prediction.HORIZON_CANDLES);
	}

	@Test
	@DisplayName("runs on one- and fifteen-minute charts too")
	void otherTimeframes() throws Exception {
		for (String timeframe : List.of("1m", "15m")) {
			JsonNode drill = start(DrillKind.LIQUIDITY_FIRST_PREDICTION, timeframe);
			List<Long> times = times(drill.get("bars"));
			assertThat(times.get(1) - times.get(0)).isEqualTo(Timeframe.fromCode(timeframe).seconds());
			String id = drill.get("id").asString();
			JsonNode result = read(choose(id, "BSL").andExpect(status().isOk())).get("result");
			assertThat(result.get("answer").asString()).isIn("BSL", "SSL");
		}
	}

	@Nested
	@DisplayName("an answer")
	class Answers {

		@Test
		@DisplayName("must be one of the two targets, and is given once")
		void oneOfTheTargets() throws Exception {
			String id = start(DrillKind.LIQUIDITY_FIRST_PREDICTION, "5m").get("id").asString();
			choose(id, "ERL").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(Matchers.containsString("[BSL, SSL]")));
			mockMvc.perform(post("/api/drills/" + id + "/answer").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"marks\":[]}")).andExpect(status().isBadRequest());
			choose(id, "SSL").andExpect(status().isOk());
			choose(id, "BSL").andExpect(status().isConflict());
		}
	}

	@Nested
	@DisplayName("the database")
	class Database {

		@Test
		@DisplayName("keeps the question and outcome as written, and the answer once given")
		void immutable() throws Exception {
			String id = start(DrillKind.LIQUIDITY_FIRST_PREDICTION, "5m").get("id").asString();
			choose(id, "BSL").andExpect(status().isOk());
			assertThatThrownBy(() -> jdbc.update("UPDATE drill SET outcome = 'SSL' WHERE id = ?::uuid", id))
				.hasMessageContaining("immutable");
		}

		@Test
		@DisplayName("refuses a prediction without its outcome, and one on simulated data (DEC-034)")
		void constraints() throws Exception {
			String id = start(DrillKind.LIQUIDITY_FIRST_PREDICTION, "5m").get("id").asString();
			assertThatThrownBy(() -> jdbc.update("UPDATE drill SET outcome = NULL WHERE id = ?::uuid", id))
				.hasMessageContaining("ck_drill_prediction");
			assertThatThrownBy(() -> jdbc.update("UPDATE drill SET outcome_at = window_to - interval '5 minutes' "
				+ "WHERE id = ?::uuid", id)).hasMessageContaining("ck_drill_prediction");
			assertThatThrownBy(() -> jdbc.update("UPDATE drill SET kind = 'FVG_IDENTIFICATION' WHERE id = ?::uuid", id))
				.hasMessageContaining("ck_drill_prediction");
			assertThatThrownBy(() -> jdbc.update("UPDATE drill SET data_class = 'SIMULATED', graded = false "
				+ "WHERE id = ?::uuid", id)).hasMessageContaining("ck_drill_prediction_real");
		}

		@Test
		@DisplayName("is never asked for on simulated data")
		void notOnSimulatedData() throws Exception {
			start("{\"kind\":\"LIQUIDITY_FIRST_PREDICTION\",\"instrument\":\"DEMO-NQ\",\"timeframe\":\"5m\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(Matchers.containsString("DEC-034")));
		}
	}
}
