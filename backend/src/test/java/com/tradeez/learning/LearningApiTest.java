package com.tradeez.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketDataService;

import jakarta.servlet.http.Cookie;

/**
 * T16 (DEC-059) — the learning state through the API, from real drills answered on imported NQ
 * ({@link ThreeWeeksOfNq}) and cards marked read.
 */
class LearningApiTest extends AbstractIntegrationTest {

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
	void setUp() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch(); // drills and reads cascade from their user
		alice = register("alice@example.com");
		bob = register("bob@example.com");
		if (!ThreeWeeksOfNq.present(marketData)) {
			jdbc.update("DELETE FROM drill_setup");
			jdbc.update("DELETE FROM drill");
			jdbc.update("DELETE FROM detected_setup");
			jdbc.update("DELETE FROM market_bar");
			jdbc.update("DELETE FROM market_data_import");
			ThreeWeeksOfNq.insert(marketData, barStore, jdbc);
		}
	}

	private Cookie register(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"A\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
	}

	private JsonNode read(ResultActions actions) throws Exception {
		return json.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private JsonNode state(Cookie who) throws Exception {
		return read(mockMvc.perform(get("/api/learning").cookie(who)).andExpect(status().isOk()));
	}

	private ResultActions markRead(Cookie who, String id) throws Exception {
		return mockMvc.perform(post("/api/learning/concepts/" + id + "/read").cookie(who).with(csrf()));
	}

	private static JsonNode concept(JsonNode state, String id) {
		for (JsonNode c : state.get("concepts")) {
			if (c.get("id").asString().equals(id)) {
				return c;
			}
		}
		throw new AssertionError("no concept " + id);
	}

	private static JsonNode level(JsonNode state, int level) {
		for (JsonNode l : state.get("levels")) {
			if (l.get("level").asInt() == level) {
				return l;
			}
		}
		throw new AssertionError("no level " + level);
	}

	private String startDrill(Cookie who, String kind, String instrument) throws Exception {
		return read(mockMvc.perform(post("/api/drills").cookie(who).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"" + kind + "\",\"instrument\":\"" + instrument + "\",\"timeframe\":\"5m\"}"))
			.andExpect(status().isCreated())).get("id").asString();
	}

	private List<Long> key(String drillId) {
		return jdbc.queryForList("""
			SELECT extract(epoch FROM s.anchor_time)::bigint FROM drill_setup ds JOIN detected_setup s ON s.id = ds.setup_id
			WHERE ds.drill_id = ?::uuid""", Long.class, drillId);
	}

	private void answer(Cookie who, String drillId, String body) throws Exception {
		mockMvc.perform(post("/api/drills/" + drillId + "/answer").cookie(who).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
	}

	private void markPerfectly(Cookie who, String drillId) throws Exception {
		answer(who, drillId, "{\"marks\":" + json.writeValueAsString(key(drillId)) + "}");
	}

	@Test
	@DisplayName("starts at the foundations: every concept listed, levels 0 and 6 open, F-1 suggested")
	void start() throws Exception {
		mockMvc.perform(get("/api/learning")).andExpect(status().isUnauthorized());
		JsonNode state = state(alice);
		assertThat(state.get("version").asString()).isEqualTo(LearningRule.VERSION);
		assertThat(state.get("rule").asString()).contains("solid");
		assertThat(state.get("concepts")).hasSize(72);
		assertThat(state.get("suggested").asString()).isEqualTo("F-1");
		assertThat(state.get("due")).isEmpty();
		assertThat(level(state, 0).get("open").asBoolean()).isTrue();
		assertThat(level(state, 6).get("open").asBoolean()).isTrue();
		assertThat(level(state, 1).get("open").asBoolean()).isFalse();
		assertThat(level(state, 1).get("unlocksWhen").asString()).contains("Level 0 complete");
		// Rated exactly where a marking or naming drill grades: not a prediction-only concept.
		// Nine since DEC-075: SMT divergence (ICT-66) has its marking drill.
		assertThat(state.at("/summary/rated").asInt()).isEqualTo(9);
		assertThat(concept(state, "ICT-66").get("rated").asBoolean()).isTrue();
		assertThat(concept(state, "ICT-20").get("state").asString()).isEqualTo("NEW");
		assertThat(concept(state, "ICT-16").get("rated").asBoolean()).isFalse();
		assertThat(concept(state, "ICT-16").get("state").asString()).isEqualTo("UNREAD");
	}

	@Test
	@DisplayName("reading the foundations' cards opens level 1 and suggests its first concept; a read is kept once")
	void reads() throws Exception {
		for (String id : List.of("F-1", "F-2", "F-3", "F-4")) {
			markRead(alice, id).andExpect(status().isOk());
		}
		JsonNode before = state(alice);
		assertThat(level(before, 1).get("open").asBoolean()).as("F-5 still unread").isFalse();
		assertThat(before.get("suggested").asString()).isEqualTo("F-5");

		JsonNode after = read(markRead(alice, "F-5").andExpect(status().isOk()));
		assertThat(level(after, 0).get("complete").asBoolean()).isTrue();
		assertThat(level(after, 1).get("open").asBoolean()).isTrue();
		assertThat(after.get("suggested").asString()).isEqualTo("ICT-01");
		assertThat(concept(after, "F-5").get("state").asString()).isEqualTo("DONE");

		long readAt = concept(after, "F-1").get("readAt").asLong();
		markRead(alice, "F-1").andExpect(status().isOk());
		assertThat(concept(state(alice), "F-1").get("readAt").asLong()).as("reading again changes nothing").isEqualTo(readAt);
		assertThatThrownBy(() -> jdbc.update("UPDATE concept_read SET read_at = now() WHERE concept_id = 'F-1'"))
			.hasMessageContaining("immutable");
		markRead(alice, "ICT-99").andExpect(status().isNotFound());
		// Bob's ladder is his own.
		assertThat(level(state(bob), 1).get("open").asBoolean()).isFalse();
	}

	@Test
	@DisplayName("ten perfect marking drills make a concept solid, due again tomorrow; a miss lowers its accuracy")
	void ratings() throws Exception {
		for (int i = 0; i < 9; i++) {
			markPerfectly(alice, startDrill(alice, "FVG_IDENTIFICATION", "NQ"));
		}
		JsonNode nine = concept(state(alice), "ICT-20");
		assertThat(nine.get("state").asString()).isEqualTo("LEARNING");
		assertThat(nine.get("recentAccuracy").asDouble()).isEqualTo(1.0);

		markPerfectly(alice, startDrill(alice, "FVG_IDENTIFICATION", "NQ"));
		JsonNode state = state(alice);
		JsonNode ten = concept(state, "ICT-20");
		assertThat(ten.get("state").asString()).isEqualTo("SOLID");
		assertThat(ten.get("attempts").asInt()).isEqualTo(10);
		assertThat(ten.get("nextReview").asString()).isEqualTo(LocalDate.now(ZoneOffset.UTC).plusDays(1).toString());
		assertThat(ten.get("due").asBoolean()).isFalse();
		assertThat(state.at("/summary/solid").asInt()).isEqualTo(1);
		assertThat(state.at("/summary/ratedAttempts").asInt()).isEqualTo(10);

		// A miss: nothing marked scores 0, and the last eleven average 10/11.
		answer(alice, startDrill(alice, "FVG_IDENTIFICATION", "NQ"), "{\"marks\":[]}");
		JsonNode eleven = concept(state(alice), "ICT-20");
		assertThat(eleven.get("recentAccuracy").asDouble()).isEqualTo(Math.round(10_000.0 / 11) / 1000.0);
		assertThat(eleven.get("state").asString()).isEqualTo("SOLID");
	}

	@Test
	@DisplayName("a marking drill half right scores what the result says: found over found, missed and extra")
	void partialMarks() throws Exception {
		String id = startDrill(alice, "SWING_HIGH_IDENTIFICATION", "NQ");
		List<Long> key = key(id);
		long notAnAnswer = json.readTree(mockMvc.perform(get("/api/drills/" + id).cookie(alice))
			.andReturn().getResponse().getContentAsString()).get("bars").valueStream()
			.map(b -> b.get("time").asLong()).filter(t -> !key.contains(t)
				&& !jdbc.queryForObject("SELECT excused::text FROM drill WHERE id = ?::uuid", String.class, id).contains(String.valueOf(t)))
			.findFirst().orElseThrow();
		List<Long> marks = new ArrayList<>(List.of(key.getFirst(), notAnAnswer));
		answer(alice, id, "{\"marks\":" + json.writeValueAsString(marks) + "}");
		double expected = 1.0 / (key.size() + 1);
		assertThat(concept(state(alice), "ICT-01").get("recentAccuracy").asDouble())
			.isEqualTo(Math.round(expected * 1000) / 1000.0);
	}

	@Test
	@DisplayName("a mark on a candle the chart cannot judge counts neither way, as the drill's result says")
	void excusedMarks() throws Exception {
		String id = null;
		List<Long> excused = List.of();
		for (int i = 0; i < 30 && excused.isEmpty(); i++) {
			id = startDrill(alice, "SWING_HIGH_IDENTIFICATION", "NQ");
			excused = new ArrayList<>();
			for (JsonNode t : json.readTree(jdbc.queryForObject("SELECT excused::text FROM drill WHERE id = ?::uuid",
					String.class, id))) {
				excused.add(t.asLong());
			}
		}
		assertThat(excused).as("a drill with an excused candle").isNotEmpty();
		List<Long> marks = new ArrayList<>(key(id));
		marks.add(excused.getFirst());
		answer(alice, id, "{\"marks\":" + json.writeValueAsString(marks) + "}");
		assertThat(concept(state(alice), "ICT-01").get("recentAccuracy").asDouble()).isEqualTo(1.0);
	}

	@Test
	@DisplayName("a naming drill counts right or wrong")
	void naming() throws Exception {
		String id = startDrill(alice, "SWEEP_RUN_CLASSIFICATION", "NQ");
		String rule = jdbc.queryForObject("""
			SELECT s.kind FROM drill_setup ds JOIN detected_setup s ON s.id = ds.setup_id WHERE ds.drill_id = ?::uuid""",
			String.class, id);
		answer(alice, id, "{\"choice\":\"" + ("SWEEP".equals(rule) ? "RUN" : "SWEEP") + "\"}");
		JsonNode wrong = concept(state(alice), "ICT-13");
		assertThat(wrong.get("recentAccuracy").asDouble()).isZero();
		assertThat(wrong.get("due").asBoolean()).as("a lapse is due tomorrow, not today").isFalse();
		assertThat(wrong.get("nextReview").asString()).isEqualTo(LocalDate.now(ZoneOffset.UTC).plusDays(1).toString());
	}

	@Test
	@DisplayName("a simulated fixture never counts (INVARIANT 8b), nor an unanswered drill")
	void onlyRealGradedAnswers() throws Exception {
		markPerfectly(alice, startDrill(alice, "FVG_IDENTIFICATION", "DEMO-NQ"));
		startDrill(alice, "FVG_IDENTIFICATION", "NQ"); // never answered
		JsonNode state = state(alice);
		assertThat(concept(state, "ICT-20").get("attempts").asInt()).isZero();
		assertThat(state.at("/summary/ratedAttempts").asInt()).isZero();
	}

	@Test
	@DisplayName("a prediction drill is practice: it completes its concept by use, and rates nothing")
	void predictions() throws Exception {
		String id = startDrill(alice, "LIQUIDITY_FIRST_PREDICTION", "NQ");
		answer(alice, id, "{\"choice\":\"BSL\"}");
		JsonNode state = state(alice);
		JsonNode dol = concept(state, "ICT-16");
		assertThat(dol.get("practised").asInt()).isEqualTo(1);
		assertThat(dol.get("state").asString()).isEqualTo("DONE");
		assertThat(dol.has("recentAccuracy")).isFalse();
		assertThat(state.at("/summary/predictions").asInt()).isEqualTo(1);
		assertThat(state.at("/summary/ratedAttempts").asInt()).isZero();
		// Bob has done nothing.
		assertThat(concept(state(bob), "ICT-16").get("practised").asInt()).isZero();
	}
}
