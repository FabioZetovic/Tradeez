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

	private static JsonNode topic(JsonNode state, String title) {
		for (JsonNode t : state.get("topics")) {
			if (t.get("title").asString().equals(title)) {
				return t;
			}
		}
		throw new AssertionError("no topic " + title);
	}

	private static boolean open(JsonNode state, String id) {
		return concept(state, id).get("open").asBoolean();
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
	@DisplayName("starts with nine topics of lessons and cards: each topic's first lesson open, ICT-01 suggested")
	void start() throws Exception {
		mockMvc.perform(get("/api/learning")).andExpect(status().isUnauthorized());
		JsonNode state = state(alice);
		assertThat(state.get("version").asString()).isEqualTo(LearningRule.VERSION);
		assertThat(state.get("rule").asString()).contains("passed").contains("5 times").contains("70%");
		assertThat(state.get("concepts")).hasSize(33);
		assertThat(state.get("topics")).hasSize(9);
		assertThat(topic(state, "Structure").get("concepts").toString()).isEqualTo("[\"ICT-01\",\"ICT-03\",\"ICT-04\",\"ICT-06\"]");
		assertThat(topic(state, "Structure").get("complete").asInt()).isZero();
		assertThat(state.get("suggested").asString()).isEqualTo("ICT-01");
		assertThat(state.get("due")).isEmpty();
		assertThat(open(state, "ICT-01")).isTrue();
		assertThat(open(state, "ICT-03")).isFalse();
		assertThat(open(state, "ICT-10")).as("Liquidity's first lesson").isTrue();
		assertThat(open(state, "ICT-13")).isFalse();
		assertThat(concept(state, "ICT-44").get("kind").asString()).isEqualTo("REFERENCE");
		assertThat(open(state, "ICT-44")).as("a reference card never locks").isTrue();
		assertThat(concept(state, "ICT-01").get("topic").asString()).isEqualTo("Structure");
		// Drilled where a drill exists — predictions, managed trades and the risk questions included.
		assertThat(state.at("/summary/drilled").asInt()).isEqualTo(17);
		assertThat(concept(state, "ICT-16").get("drilled").asBoolean()).isTrue();
		assertThat(concept(state, "ICT-16").get("state").asString()).isEqualTo("NEW");
		assertThat(concept(state, "ICT-83").get("drilled").asBoolean()).isTrue();
		assertThat(concept(state, "ICT-23").get("drilled").asBoolean()).isTrue();
		assertThat(concept(state, "ICT-41").get("drilled").asBoolean()).as("its drill is not built yet").isFalse();
		// Merged and removed concepts are not pages of Learn.
		assertThat(state.get("concepts").valueStream().map(c -> c.get("id").asString()))
			.doesNotContain("F-1", "ICT-05", "ICT-29");
	}

	@Test
	@DisplayName("a card or a lesson without its drill yet is done once read, and opens the next; a read is kept once")
	void reads() throws Exception {
		JsonNode card = read(markRead(alice, "ICT-44").andExpect(status().isOk()));
		assertThat(concept(card, "ICT-44").get("state").asString()).isEqualTo("DONE");
		assertThat(topic(card, "Premium & discount").get("complete").asInt()).isEqualTo(1);

		JsonNode after = read(markRead(alice, "ICT-10").andExpect(status().isOk()));
		assertThat(concept(after, "ICT-10").get("state").asString()).isEqualTo("DONE");
		assertThat(open(after, "ICT-13")).isTrue();
		assertThat(after.get("suggested").asString()).as("the topic started").isEqualTo("ICT-13");

		long readAt = concept(after, "ICT-10").get("readAt").asLong();
		markRead(alice, "ICT-10").andExpect(status().isOk());
		assertThat(concept(state(alice), "ICT-10").get("readAt").asLong()).as("reading again changes nothing").isEqualTo(readAt);
		assertThatThrownBy(() -> jdbc.update("UPDATE concept_read SET read_at = now() WHERE concept_id = 'ICT-10'"))
			.hasMessageContaining("immutable");
		markRead(alice, "ICT-99").andExpect(status().isNotFound());
		markRead(alice, "F-1").andExpect(status().isNotFound()); // removed
		markRead(alice, "ICT-05").andExpect(status().isNotFound()); // merged into ICT-06
		// Bob's locks are his own.
		assertThat(open(state(bob), "ICT-13")).isFalse();
	}

	@Test
	@DisplayName("five perfect marking drills pass a lesson and open the next, due again tomorrow; a miss lowers its accuracy")
	void ratings() throws Exception {
		for (int i = 0; i < 4; i++) {
			markPerfectly(alice, startDrill(alice, "SWING_HIGH_IDENTIFICATION", "NQ"));
		}
		JsonNode four = state(alice);
		assertThat(concept(four, "ICT-01").get("state").asString()).isEqualTo("LEARNING");
		assertThat(concept(four, "ICT-01").get("recentAccuracy").asDouble()).isEqualTo(1.0);
		assertThat(open(four, "ICT-03")).isFalse();

		markPerfectly(alice, startDrill(alice, "SWING_LOW_IDENTIFICATION", "NQ"));
		JsonNode state = state(alice);
		JsonNode five = concept(state, "ICT-01");
		assertThat(five.get("state").asString()).isEqualTo("SOLID");
		assertThat(five.get("attempts").asInt()).as("both swing drills count toward the lesson").isEqualTo(5);
		assertThat(five.get("nextReview").asString()).isEqualTo(LocalDate.now(ZoneOffset.UTC).plusDays(1).toString());
		assertThat(five.get("due").asBoolean()).isFalse();
		assertThat(open(state, "ICT-03")).isTrue();
		assertThat(state.get("suggested").asString()).isEqualTo("ICT-03");
		assertThat(state.at("/summary/solid").asInt()).isEqualTo(1);
		assertThat(state.at("/summary/ratedAttempts").asInt()).isEqualTo(5);

		// A miss: nothing marked scores 0, and the last six average 5/6.
		answer(alice, startDrill(alice, "SWING_HIGH_IDENTIFICATION", "NQ"), "{\"marks\":[]}");
		JsonNode six = concept(state(alice), "ICT-01");
		assertThat(six.get("recentAccuracy").asDouble()).isEqualTo(Math.round(5_000.0 / 6) / 1000.0);
		assertThat(six.get("state").asString()).isEqualTo("SOLID");
	}

	@Test
	@DisplayName("five risk questions right pass Risk rules and open Stop placement; Bob's are not Alice's")
	void risk() throws Exception {
		for (int i = 0; i < 5; i++) {
			String id = read(mockMvc.perform(post("/api/drills/risk").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"RECOVERY\"}"))).get("id").asString();
			double right = jdbc.queryForObject("SELECT answer FROM risk_drill WHERE id = ?::uuid", Double.class, id);
			mockMvc.perform(post("/api/drills/risk/" + id + "/answer").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"value\":" + right + "}")).andExpect(status().isOk());
		}
		JsonNode state = state(alice);
		assertThat(concept(state, "ICT-83").get("state").asString()).isEqualTo("SOLID");
		assertThat(concept(state, "ICT-83").get("practised").asInt()).isEqualTo(5);
		assertThat(open(state, "ICT-80")).isTrue();
		assertThat(state.at("/summary/predictions").asInt()).isEqualTo(5);
		assertThat(concept(state(bob), "ICT-83").get("attempts").asInt()).isZero();
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
	@DisplayName("a prediction counts toward its lesson, scored by what price did")
	void predictions() throws Exception {
		String id = startDrill(alice, "LIQUIDITY_FIRST_PREDICTION", "NQ");
		answer(alice, id, "{\"choice\":\"BSL\"}");
		boolean right = "BSL".equals(jdbc.queryForObject("SELECT outcome FROM drill WHERE id = ?::uuid", String.class, id));
		JsonNode state = state(alice);
		JsonNode dol = concept(state, "ICT-16");
		assertThat(dol.get("practised").asInt()).isEqualTo(1);
		assertThat(dol.get("attempts").asInt()).isEqualTo(1);
		assertThat(dol.get("state").asString()).isEqualTo("LEARNING");
		assertThat(dol.get("recentAccuracy").asDouble()).isEqualTo(right ? 1.0 : 0.0);
		assertThat(state.at("/summary/predictions").asInt()).isEqualTo(1);
		assertThat(state.at("/summary/ratedAttempts").asInt()).isZero();
		// Bob has done nothing.
		assertThat(concept(state(bob), "ICT-16").get("practised").asInt()).isZero();
	}
}
