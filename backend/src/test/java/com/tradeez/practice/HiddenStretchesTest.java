package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.ThreeWeeksOfNq;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;
import com.tradeez.marketdata.HiddenStretches;
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketDataService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * BUG-085, BUG-086: what a learner is still graded by stays off the Chart tab — an open practice session's replay, the
 * drill being answered — and nothing new is drawn from it: no lesson example, no drill, no other session.
 */
class HiddenStretchesTest extends AbstractIntegrationTest {

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

	@BeforeEach
	void threeWeeks() throws Exception {
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
	}

	private JsonNode read(ResultActions actions) throws Exception {
		return json.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private JsonNode startSession() throws Exception {
		return read(mockMvc.perform(post("/api/practice/sessions").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"instrument\":\"NQ\",\"timeframe\":\"5m\"}"))
			.andExpect(status().isCreated()));
	}

	/** The open session's stretch on the real timeline: its cursor to a day past its replay. */
	private Instant[] sessionStretch(String id) {
		Map<String, Object> row = jdbc.queryForMap("SELECT cursor_time, start_time FROM practice_session WHERE id = ?::uuid",
			id);
		return new Instant[] { ((java.sql.Timestamp) row.get("cursor_time")).toInstant(),
			((java.sql.Timestamp) row.get("start_time")).toInstant().plus(HiddenStretches.PAST_REPLAY_START) };
	}

	private JsonNode chart(String what, Instant from, Instant to) throws Exception {
		return read(mockMvc.perform(get("/api/market/instruments/NQ/" + what).cookie(alice)
				.param("timeframe", "5m").param("from", String.valueOf(from.getEpochSecond()))
				.param("to", String.valueOf(to.getEpochSecond())).param("detectors", "FVG,SWING"))
			.andExpect(status().isOk()));
	}

	private static boolean inside(long second, Instant[] stretch) {
		return second >= stretch[0].getEpochSecond() && second < stretch[1].getEpochSecond();
	}

	@Test
	@DisplayName("the replay's length plus a day: a trade still running at its end stays hidden too")
	void reach() {
		assertThat(HiddenStretches.PAST_REPLAY_START).isEqualTo(PracticeService.MAX_REPLAY.plus(Duration.ofDays(1)));
	}

	@Test
	@DisplayName("DEC-085 a replay runs a week past its start: a whole trading week to follow, not one day")
	void aWeek() {
		assertThat(PracticeService.MAX_REPLAY).isEqualTo(Duration.ofDays(7));
	}

	@Test
	@DisplayName("BUG-086 an open session's replay is off the Chart tab — candles, setups, clock prices — until it ends")
	void practiceHidden() throws Exception {
		String id = startSession().at("/state/id").asString();
		Instant[] hidden = sessionStretch(id);
		Instant from = ThreeWeeksOfNq.FROM;
		Instant to = ThreeWeeksOfNq.TO;

		JsonNode bars = chart("bars", from, to);
		assertThat(bars.get("hidden")).hasSize(1);
		assertThat(bars.at("/hidden/0/from").asLong()).isEqualTo(hidden[0].getEpochSecond());
		assertThat(bars.at("/hidden/0/to").asLong()).isEqualTo(hidden[1].getEpochSecond());
		assertThat(bars.at("/hidden/0/why").asString()).isEqualTo("PRACTICE");
		List<Long> times = new ArrayList<>();
		bars.get("bars").forEach(b -> times.add(b.get("time").asLong()));
		// What the replay has shown stays: the candles up to its cursor.
		assertThat(times).anyMatch(t -> t < hidden[0].getEpochSecond())
			.noneMatch(t -> t + 300 > hidden[0].getEpochSecond() && t < hidden[1].getEpochSecond());

		JsonNode setups = chart("setups", from, to).get("setups");
		assertThat(setups).isNotEmpty();
		// What a setup says runs to its confirmation, a candle already closed, or through its last candle's open.
		setups.forEach(s -> {
			long last = Math.max(s.get("end").asLong() + 1, s.get("confirmedAt").asLong());
			assertThat(s.get("start").asLong() < hidden[1].getEpochSecond() && hidden[0].getEpochSecond() < last)
				.as("a setup touching the replay").isFalse();
		});

		JsonNode lines = read(mockMvc.perform(get("/api/market/instruments/NQ/clock").cookie(alice)
				.param("from", String.valueOf(from.getEpochSecond())).param("to", String.valueOf(to.getEpochSecond())))
			.andExpect(status().isOk())).get("lines");
		assertThat(lines).isNotEmpty();
		lines.forEach(l -> {
			assertThat(l.get("from").asLong() < hidden[1].getEpochSecond() && hidden[0].getEpochSecond() < l.get("to").asLong())
				.as("a price over the replay").isFalse();
			if (l.hasNonNull("takenAt")) {
				assertThat(inside(l.get("takenAt").asLong(), hidden)).as("a price taken inside the replay").isFalse();
			}
		});

		mockMvc.perform(post("/api/practice/sessions/" + id + "/end").cookie(alice).with(csrf()))
			.andExpect(status().isOk());
		JsonNode after = chart("bars", from, to);
		assertThat(after.get("hidden")).isEmpty();
		assertThat(after.get("bars").size()).isGreaterThan(bars.get("bars").size());
	}

	@Test
	@DisplayName("BUG-085 the drill being answered is off the Chart tab; answered, or left for the next, it is back")
	void drillHidden() throws Exception {
		JsonNode first = read(mockMvc.perform(post("/api/drills").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"FVG_IDENTIFICATION\",\"timeframe\":\"5m\"}"))
			.andExpect(status().isCreated()));
		String firstId = first.get("id").asString();
		Map<String, Object> row = jdbc.queryForMap("SELECT window_from, window_to FROM drill WHERE id = ?::uuid", firstId);
		Instant[] window = { ((java.sql.Timestamp) row.get("window_from")).toInstant(),
			((java.sql.Timestamp) row.get("window_to")).toInstant() };
		Instant from = window[0].minus(Duration.ofDays(1));
		Instant to = window[1].plus(Duration.ofDays(1));

		JsonNode bars = chart("bars", from, to);
		assertThat(bars.at("/hidden/0/why").asString()).isEqualTo("DRILL");
		bars.get("bars").forEach(b -> assertThat(inside(b.get("time").asLong(), window)).isFalse());

		mockMvc.perform(post("/api/drills/" + firstId + "/answer").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"marks\":[]}"))
			.andExpect(status().isOk());
		assertThat(chart("bars", from, to).get("hidden")).isEmpty();
	}

	/** An open session over {@code start}'s replay, copied from an ended one, last touched {@code touched}. */
	private void plant(String template, Instant start, Instant touched) {
		jdbc.update("""
			INSERT INTO practice_session (id, user_id, instrument_id, timeframe, seed, generator_version, data_source,
			    clock_day_shift, history_from, start_time, cursor_time, status, version, created_at, updated_at)
			SELECT gen_random_uuid(), user_id, instrument_id, timeframe, seed, generator_version, data_source,
			    clock_day_shift, ?, ?, ?, 'ANALYSING', 0, ?, ?
			FROM practice_session WHERE id = ?::uuid""",
			java.sql.Timestamp.from(start.minus(PracticeService.HISTORY)), java.sql.Timestamp.from(start),
			java.sql.Timestamp.from(start), java.sql.Timestamp.from(touched), java.sql.Timestamp.from(touched), template);
	}

	/** A session started and ended at once: a row to copy. */
	private String ended() throws Exception {
		String id = startSession().at("/state/id").asString();
		mockMvc.perform(post("/api/practice/sessions/" + id + "/end").cookie(alice).with(csrf()))
			.andExpect(status().isOk());
		return id;
	}

	@Test
	@DisplayName("BUG-086 only the session the learner is in is hidden — the open one touched last, not every one left open")
	void onlyTheSessionInHand() throws Exception {
		String template = ended();
		Instant earlier = Instant.parse("2025-05-02T14:00:00Z");
		Instant later = Instant.parse("2025-05-12T14:00:00Z");
		plant(template, later, Instant.now());
		plant(template, earlier, Instant.now().minus(Duration.ofHours(1)));

		JsonNode hidden = chart("bars", ThreeWeeksOfNq.FROM, ThreeWeeksOfNq.TO).get("hidden");
		assertThat(hidden).hasSize(1);
		assertThat(hidden.at("/0/from").asLong()).isEqualTo(later.getEpochSecond());
	}

	@Test
	@DisplayName("BUG-086 a new session's history and replay keep clear of another open session's replay")
	void sessionsKeepOut() throws Exception {
		// One session's replay planted over 3 to 7 May: on three weeks of minutes, any new session must then start on
		// or after the 11th, its four days of history clear of it.
		String template = ended();
		Instant planted = Instant.parse("2025-05-03T00:00:00Z");
		plant(template, planted, Instant.now());
		Instant clear = planted.plus(Duration.ofDays(4)).plus(PracticeService.HISTORY);
		for (int i = 0; i < 3; i++) {
			String id = startSession().at("/state/id").asString();
			Instant start = sessionStretch(id)[1].minus(HiddenStretches.PAST_REPLAY_START);
			assertThat(start).as("session %d's start", i + 1).isAfterOrEqualTo(clear);
			mockMvc.perform(post("/api/practice/sessions/" + id + "/end").cookie(alice).with(csrf()))
				.andExpect(status().isOk());
		}
	}

	@Test
	@DisplayName("BUG-085, BUG-086 lessons and drills draw nothing from an open session's replay")
	void nothingNewFromIt() throws Exception {
		// The replay planted over 5 to 9 May, four of the fortnight lessons draw from: eight lessons' examples at random
		// would all miss it once in a few hundred runs — and keeping clear, they never touch it.
		Instant start = Instant.parse("2025-05-05T14:00:00Z");
		plant(ended(), start, Instant.now());
		Instant[] hidden = { start, start.plus(HiddenStretches.PAST_REPLAY_START) };
		for (int i = 0; i < 8; i++) {
			JsonNode card = read(mockMvc.perform(get("/api/concepts/ICT-20/lesson").cookie(alice))
				.andExpect(status().isOk()));
			card.get("examples").forEach(example -> example.get("bars").forEach(b ->
				assertThat(b.get("time").asLong() + 300 > hidden[0].getEpochSecond()
					&& b.get("time").asLong() < hidden[1].getEpochSecond()).as("a lesson candle inside the replay").isFalse()));
		}
		for (int i = 0; i < 3; i++) {
			JsonNode drill = read(mockMvc.perform(post("/api/drills").cookie(alice).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"FVG_IDENTIFICATION\",\"timeframe\":\"5m\"}"))
				.andExpect(status().isCreated()));
			Map<String, Object> row = jdbc.queryForMap("SELECT window_from, window_to FROM drill WHERE id = ?::uuid",
				drill.get("id").asString());
			Instant windowFrom = ((java.sql.Timestamp) row.get("window_from")).toInstant();
			Instant windowTo = ((java.sql.Timestamp) row.get("window_to")).toInstant();
			assertThat(windowFrom.isBefore(hidden[1]) && hidden[0].isBefore(windowTo)).as("a drill inside the replay")
				.isFalse();
		}
	}
}
