package com.tradeez.drills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeez.AbstractIntegrationTest;
import com.tradeez.ThreeWeeksOfNq;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketDataService;

import jakarta.servlet.http.Cookie;

/**
 * T13 (DEC-054) — every drill, on imported one-minute NQ, graded against its rule.
 *
 * <p>The minutes are {@link ThreeWeeksOfNq}: the simulated market's, with every 35th minute opening two
 * points from the last close (so there are volume imbalances), and a roll to the June contract, 1,000
 * points higher, on Thursday 8 May.
 *
 * <p>The same three promises as the fixture drill. <b>Right</b>: the answer key is every hit of the
 * rule that the chart shows whole, and a hit the chart cuts is excused rather than counted. <b>Fair</b>:
 * nothing gives the answer away before it is given — for a classification drill not even the subject's
 * name, direction or end — and an answer is given once. <b>Honest</b>: graded exactly because the
 * market is real.
 */
class RealDrillApiTest extends AbstractIntegrationTest {

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
	private Instrument nq;

	@BeforeEach
	void importThreeWeeks() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch(); // drills cascade from their user
		alice = register("alice@example.com");
		nq = marketData.requireInstrument("NQ");
		if (ThreeWeeksOfNq.present(marketData)) {
			return; // already imported by an earlier test in this class: the import is the slow part
		}
		jdbc.update("DELETE FROM drill_setup");
		jdbc.update("DELETE FROM drill");
		jdbc.update("DELETE FROM detected_setup");
		jdbc.update("DELETE FROM market_bar");
		jdbc.update("DELETE FROM market_data_import");
		ThreeWeeksOfNq.insert(marketData, barStore, jdbc);
	}

	private Cookie register(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"a-perfectly-fine-passphrase\","
					+ "\"displayName\":\"A\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
	}

	private JsonNode read(ResultActions actions) throws Exception {
		return json.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private ResultActions start(String body) throws Exception {
		return mockMvc.perform(post("/api/drills").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private JsonNode start(DrillKind kind, String timeframe) throws Exception {
		return read(start("{\"kind\":\"" + kind + "\",\"timeframe\":\"" + timeframe + "\"}")
			.andExpect(status().isCreated()));
	}

	private ResultActions answer(String id, String body) throws Exception {
		return mockMvc.perform(post("/api/drills/" + id + "/answer").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions mark(String id, List<Long> marks) throws Exception {
		return answer(id, "{\"marks\":" + json.writeValueAsString(marks) + "}");
	}

	private ResultActions choose(String id, String choice) throws Exception {
		return answer(id, "{\"choice\":\"" + choice + "\"}");
	}

	private static List<Long> barTimes(JsonNode drill) {
		List<Long> times = new ArrayList<>();
		drill.get("bars").forEach(b -> times.add(b.get("time").asLong()));
		return times;
	}

	/** A stored hit, as the database has it. */
	private record Hit(long start, long anchor, long confirmed, String kind, String detector, String shape,
			long low) {
	}

	private static final String HIT_COLUMNS = """
		extract(epoch FROM s.start_time)::bigint, extract(epoch FROM s.anchor_time)::bigint,
		extract(epoch FROM s.confirmed_at)::bigint, s.kind, s.detector, s.shape, s.zone_low_ticks""";

	private Hit hit(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new Hit(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5), rs.getString(6),
			rs.getLong(7));
	}

	/** The drill's answer key, read from the database. */
	private List<Hit> key(String drillId) {
		return jdbc.query("SELECT " + HIT_COLUMNS + """
			 FROM drill_setup ds JOIN detected_setup s ON s.id = ds.setup_id
			WHERE ds.drill_id = ?::uuid ORDER BY s.anchor_time""", (rs, i) -> hit(rs), drillId);
	}

	/** Every stored hit of a rule anchored in {@code [from, to)} on real NQ at five minutes. */
	private List<Hit> stored(String detector, long from, long to) {
		return jdbc.query("SELECT " + HIT_COLUMNS + """
			 FROM detected_setup s
			WHERE s.instrument_id = ? AND s.seed IS NULL AND s.timeframe = '5m' AND s.detector = ?
			  AND s.anchor_time >= to_timestamp(?) AND s.anchor_time < to_timestamp(?)""",
			(rs, i) -> hit(rs), nq.getId(), detector, from, to);
	}

	private List<Long> excused(String drillId) throws Exception {
		List<Long> times = new ArrayList<>();
		json.readTree(jdbc.queryForObject("SELECT excused::text FROM drill WHERE id = ?::uuid", String.class, drillId))
			.forEach(t -> times.add(t.asLong()));
		return times;
	}

	/** What each identification drill asks about, said independently of the catalogue. */
	private static final Map<DrillKind, String> ASKED_KIND = Map.of(
		DrillKind.SWING_HIGH_IDENTIFICATION, "HIGH",
		DrillKind.SWING_LOW_IDENTIFICATION, "LOW",
		DrillKind.SWEEP_IDENTIFICATION, "SWEEP");

	private static boolean asked(DrillKind kind, Hit hit) {
		String only = ASKED_KIND.get(kind);
		return only == null || only.equals(hit.kind());
	}

	@Test
	@DisplayName("lists every drill there is, with its mode and the rule it is graded by")
	void catalogue() throws Exception {
		mockMvc.perform(get("/api/drills/kinds")).andExpect(status().isUnauthorized());
		JsonNode kinds = read(mockMvc.perform(get("/api/drills/kinds").cookie(alice)).andExpect(status().isOk()));
		assertThat(kinds).hasSize(DrillKind.values().length);
		assertThat(kinds.findValuesAsText("kind")).contains("FVG_IDENTIFICATION", "BOS_CHOCH_CLASSIFICATION");
		assertThat(kinds.findValuesAsText("mode")).containsOnly("IDENTIFY", "CLASSIFY");
		assertThat(kinds.get(0).get("ruleVersion").asText()).isEqualTo("fvg-1.0.0");
	}

	@ParameterizedTest
	@EnumSource(value = DrillKind.class, mode = EnumSource.Mode.MATCH_ALL, names = ".*_IDENTIFICATION")
	@DisplayName("identification: graded, and the key is every hit of the rule the chart shows whole")
	void identification(DrillKind kind) throws Exception {
		JsonNode drill = start(kind, "5m");
		assertThat(drill.get("graded").asBoolean()).isTrue();
		assertThat(drill.get("fixture").asBoolean()).isFalse();
		assertThat(drill.get("dataClass").asText()).isEqualTo("IMPORTED_PRIVATE");
		assertThat(drill.has("disclaimer")).isFalse();
		assertThat(drill.get("mode").asText()).isEqualTo("IDENTIFY");
		assertThat(drill.at("/rule/detector").asText()).isEqualTo(kind.detector());
		assertThat(drill.at("/rule/definition").asText()).isNotBlank();
		assertThat(drill.has("result") || drill.has("subject") || drill.has("options")).isFalse();

		String id = drill.get("id").asText();
		List<Long> times = barTimes(drill);
		assertThat(times).hasSize(DrillService.WINDOW_BARS);
		long from = times.getFirst();
		long to = times.getLast() + 300;
		for (int i = 1; i < times.size(); i++) {
			assertThat(times.get(i) - times.get(i - 1)).as("one unbroken stretch").isEqualTo(300);
		}

		List<Hit> key = key(id);
		assertThat(key).hasSizeBetween(kind.minHits(), kind.maxHits());
		for (Hit hit : key) {
			assertThat(hit.detector()).isEqualTo(kind.detector());
			assertThat(asked(kind, hit)).isTrue();
			assertThat(hit.anchor()).isBetween(from, to - 300);
			assertThat(hit.start()).as("everything the rule read is on screen").isGreaterThanOrEqualTo(from + 300L * kind.lead());
			assertThat(hit.confirmed()).as("known by the last candle's close").isLessThanOrEqualTo(to);
		}
		// Every other hit anchored in the window is excused: none is silently left out.
		Set<Long> keyed = new HashSet<>(key.stream().map(Hit::anchor).toList());
		Set<Long> accounted = new HashSet<>(keyed);
		accounted.addAll(excused(id));
		assertThat(stored(kind.detector(), from, to).stream().filter(h -> asked(kind, h)).map(Hit::anchor))
			.allMatch(accounted::contains);
		assertThat(excused(id)).doesNotContainAnyElementsOf(keyed);

		JsonNode result = read(mark(id, new ArrayList<>(keyed)).andExpect(status().isOk())).get("result");
		assertThat(result.get("found").asInt()).isEqualTo(key.size());
		assertThat(result.get("missed").asInt()).isZero();
		assertThat(result.get("extra").asInt()).isZero();
		for (JsonNode setup : result.get("setups")) {
			assertThat(setup.get("found").asBoolean()).isTrue();
			assertThat(setup.get("label").asText()).isNotBlank();
			assertThat(setup.get("explanation")).isNotEmpty();
			assertThat(setup.get("explanation").toString()).doesNotContain("null");
		}
	}

	@Test
	@DisplayName("a mark on a hit the chart cuts counts neither way; a mark on nothing is extra")
	void excusedMarks() throws Exception {
		JsonNode drill = null;
		List<Long> excused = List.of();
		// Swings at the window's edges are common: the first candle's left neighbour is off screen.
		for (int i = 0; i < 30 && excused.isEmpty(); i++) {
			drill = start(DrillKind.SWING_HIGH_IDENTIFICATION, "5m");
			excused = excused(drill.get("id").asText());
		}
		assertThat(excused).as("a drill with an excused candle").isNotEmpty();
		String id = drill.get("id").asText();
		Set<Long> taken = new HashSet<>(excused);
		key(id).forEach(h -> taken.add(h.anchor()));
		Long nothing = barTimes(drill).stream().filter(t -> !taken.contains(t)).findFirst().orElseThrow();

		JsonNode result = read(mark(id, List.of(excused.getFirst(), nothing)).andExpect(status().isOk())).get("result");
		assertThat(result.get("excusedMarks")).extracting(JsonNode::asLong).containsExactly(excused.getFirst());
		assertThat(result.get("extraMarks")).extracting(JsonNode::asLong).containsExactly(nothing);
		assertThat(result.get("extra").asInt()).isEqualTo(1);
		assertThat(result.get("found").asInt()).isZero();
	}

	@ParameterizedTest
	@EnumSource(value = DrillKind.class, mode = EnumSource.Mode.MATCH_ALL, names = ".*_CLASSIFICATION")
	@DisplayName("classification: the subject is drawn without its name, and the rule's name is the answer")
	void classification(DrillKind kind) throws Exception {
		JsonNode drill = start(kind, "5m");
		assertThat(drill.get("mode").asText()).isEqualTo("CLASSIFY");
		assertThat(drill.get("graded").asBoolean()).isTrue();
		List<Long> times = barTimes(drill);
		assertThat(times).hasSize(DrillService.CLASSIFY_WINDOW_BARS);
		// Nothing that names it: no result, no direction, no end, no evidence.
		assertThat(drill.has("result")).isFalse();
		assertThat(drill.get("subject").has("direction") || drill.get("subject").has("kind")).isFalse();
		assertThat(drill.toString()).doesNotContain("\"answer\"").doesNotContain("evidence")
			.doesNotContain("endTime").doesNotContain("explanation");

		String id = drill.get("id").asText();
		List<Hit> key = key(id);
		assertThat(key).hasSize(1);
		Hit subject = key.getFirst();
		assertThat(subject.detector()).isEqualTo(kind.detector());
		JsonNode shown = drill.get("subject");
		assertThat(shown.get("time").asLong()).isEqualTo(subject.anchor());
		assertThat(shown.get("priceTicks").asLong()).isEqualTo(subject.low());
		assertThat(times).contains(shown.get("time").asLong());
		assertThat(subject.start()).isGreaterThanOrEqualTo(times.getFirst() + 300L * kind.lead());
		assertThat(subject.confirmed()).isLessThanOrEqualTo(times.getLast() + 300);

		if (kind == DrillKind.BOS_CHOCH_CLASSIFICATION) {
			// The name depends on the structure before the break: the latest swing high and low and the
			// swings they were labelled against must all be on the chart.
			for (String side : List.of("H", "L")) {
				long labelStart = jdbc.queryForObject("""
					SELECT extract(epoch FROM start_time)::bigint FROM detected_setup
					WHERE instrument_id = ? AND seed IS NULL AND timeframe = '5m' AND detector = 'STRUCTURE'
					  AND right(kind, 1) = ? AND confirmed_at <= to_timestamp(?)
					ORDER BY confirmed_at DESC LIMIT 1""", Long.class, nq.getId(), side, subject.anchor());
				assertThat(labelStart).as("the latest swing %s and the one before it are on screen", side)
					.isGreaterThanOrEqualTo(times.getFirst());
			}
		}

		List<String> options = drill.get("options").findValuesAsText("value");
		assertThat(options).contains(subject.kind());
		if (kind == DrillKind.STRUCTURE_CLASSIFICATION) {
			assertThat(options).containsExactlyElementsOf("HIGH".equals(shown.get("side").asText())
				? List.of("HH", "LH", "EH") : List.of("HL", "LL", "EL"));
		}

		String wrong = options.stream().filter(o -> !o.equals(subject.kind())).findFirst().orElseThrow();
		JsonNode result = read(choose(id, wrong).andExpect(status().isOk())).get("result");
		assertThat(result.get("correct").asBoolean()).isFalse();
		assertThat(result.get("answer").asText()).isEqualTo(subject.kind());
		assertThat(result.at("/setups/0/explanation")).isNotEmpty();
		assertThat(result.at("/setups/0/explanation").toString()).doesNotContain("null");

		JsonNode again = start(kind, "5m");
		String right = key(again.get("id").asText()).getFirst().kind();
		JsonNode resumed = read(choose(again.get("id").asText(), right).andExpect(status().isOk()));
		assertThat(resumed.at("/result/correct").asBoolean()).isTrue();
		assertThat(resumed.get("choice").asText()).isEqualTo(right);
	}

	@Nested
	@DisplayName("an answer")
	class Answers {

		@Test
		@DisplayName("must fit the drill: marks for identification, one of the options for classification")
		void fitsTheMode() throws Exception {
			String identify = start(DrillKind.FVG_IDENTIFICATION, "5m").get("id").asText();
			choose(identify, "BOS").andExpect(status().isBadRequest());
			answer(identify, "{}").andExpect(status().isBadRequest());

			JsonNode classify = start(DrillKind.SWEEP_RUN_CLASSIFICATION, "5m");
			String id = classify.get("id").asText();
			mark(id, List.of()).andExpect(status().isBadRequest());
			choose(id, "BOS").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(Matchers.containsString("[SWEEP, RUN]")));
			// None of the refusals used it up.
			choose(id, "RUN").andExpect(status().isOk());
			choose(id, "SWEEP").andExpect(status().isConflict());
		}

		@Test
		@DisplayName("is kept: the database refuses to change a choice once given")
		void choiceIsImmutable() throws Exception {
			String id = start(DrillKind.BOS_CHOCH_CLASSIFICATION, "5m").get("id").asText();
			choose(id, "BOS").andExpect(status().isOk());
			assertThatThrownBy(() -> jdbc.update("UPDATE drill SET choice = 'CHOCH' WHERE id = ?::uuid", id))
				.hasMessageContaining("immutable");
			assertThat(jdbc.queryForObject("SELECT graded FROM drill WHERE id = ?::uuid", Boolean.class, id)).isTrue();
			assertThat(jdbc.queryForObject("SELECT seed FROM drill WHERE id = ?::uuid", Long.class, id)).isNull();
		}
	}

	@Test
	@DisplayName("runs on one-, five- and fifteen-minute charts, and says so when asked for another")
	void timeframes() throws Exception {
		List<Long> times = barTimes(start(DrillKind.SWING_LOW_IDENTIFICATION, "1m"));
		assertThat(times.get(1) - times.get(0)).isEqualTo(60);
		start("{\"timeframe\":\"1h\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(Matchers.containsString("[1m, 5m, 15m]")));
	}

	@Test
	@DisplayName("with no real history imported, says there is nothing to drill on")
	void nothingImported() throws Exception {
		jdbc.update("DELETE FROM market_bar");
		start("{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(Matchers.containsString("No real NQ history")));
	}
}
