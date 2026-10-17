package com.tradeez.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;

import jakarta.servlet.http.Cookie;

/**
 * T9 — detected setups over the Chart tab and over a practice replay, on imported one-minute NQ.
 *
 * <p>The minutes are {@link ThreeWeeksOfNq}: the simulated market's, stored as an import stores them,
 * with volume imbalances added and a roll to the June contract, 1,000 points higher, in the middle of
 * Thursday 8 May's morning — a jump that would read as an enormous gap to a detector handed both.
 */
class SetupApiTest extends AbstractIntegrationTest {

	private static final Instant ROLL = ThreeWeeksOfNq.ROLL;
	/** Every detector the app has after T18 (DEC-050, DEC-052, DEC-053, DEC-061, DEC-062). */
	private static final String ALL = "FVG,FVG_STATE,IFVG,BPR,VI,VOID,OPENING_GAP,ORG,"
		+ "SWING,SWING_TIER,STRUCTURE,BOS_CHOCH,DISPLACEMENT,MSS,LIQUIDITY,EQHL,SWEEP,TURTLE_SOUP,"
		+ "OB,BREAKER,MITIGATION,REJECTION,UNICORN,DEALING_RANGE,PD,OTE,STDEV";
	private static final long YEAR_2000 = Instant.parse("2000-01-01T00:00:00Z").getEpochSecond();
	private static final long YEAR_2001 = Instant.parse("2001-01-01T00:00:00Z").getEpochSecond();

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
	void importThreeWeeksWithARoll() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		jdbc.update("DELETE FROM market_bar");
		jdbc.update("DELETE FROM market_data_import");
		jdbc.update("DELETE FROM drill_setup");
		jdbc.update("DELETE FROM drill");
		jdbc.update("DELETE FROM detected_setup");
		alice = register("alice@example.com");

		nq = marketData.requireInstrument("NQ");
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

	private ResultActions chartRequest(String symbol, String timeframe, Instant from, Instant to, String detectors)
			throws Exception {
		var request = get("/api/market/instruments/" + symbol + "/setups")
			.param("timeframe", timeframe)
			.param("from", Long.toString(from.getEpochSecond()))
			.param("to", Long.toString(to.getEpochSecond()))
			.cookie(alice);
		return mockMvc.perform(detectors == null ? request : request.param("detectors", detectors));
	}

	private JsonNode chartSetups(Instant from, Instant to) throws Exception {
		return chartSetups(from, to, "FVG");
	}

	private JsonNode chartSetups(Instant from, Instant to, String detectors) throws Exception {
		return read(chartRequest("NQ", "5m", from, to, detectors).andExpect(status().isOk())).get("setups");
	}

	/** A setup as a line of text, its times shifted: enough to compare two lists exactly. */
	private static List<String> described(JsonNode setups, long shift) {
		List<String> out = new ArrayList<>();
		setups.forEach(s -> out.add(s.get("detector").asString() + "/" + (s.hasNonNull("kind") ? s.get("kind").asString() : "-") + " "
			+ s.get("direction").asString() + " " + (s.get("start").asLong() + shift) + " "
			+ (s.get("end").asLong() + shift) + " " + (s.get("confirmedAt").asLong() + shift) + " "
			+ s.get("lowTicks").asLong() + "-" + s.get("highTicks").asLong()));
		return out;
	}

	@Test
	@DisplayName("the chart gets exactly the rule's gaps over its window, with the rule they were found under")
	void chart() throws Exception {
		Instant from = Instant.parse("2025-05-05T13:00:00Z");
		Instant to = Instant.parse("2025-05-07T20:00:00Z");
		JsonNode layer = read(chartRequest("NQ", "5m", from, to, "FVG").andExpect(status().isOk()));

		assertThat(layer.at("/rules/0/detector").asString()).isEqualTo("FVG");
		assertThat(layer.at("/rules/0/version").asString()).isEqualTo(FvgDetector.RULE_VERSION);
		assertThat(layer.at("/rules/0/conceptId").asString()).isEqualTo("ICT-20");
		assertThat(layer.at("/rules/0/parameters/averageRangeLookback").asInt()).isEqualTo(14);

		// The same rule over the same candles, run by hand.
		List<Bar> bars = marketData.internalBars(nq, Timeframe.M5, from.minusSeconds(86_400), to, 0).bars();
		List<String> expected = new ArrayList<>();
		for (Detection d : new FvgDetector().detect(bars, Timeframe.M5)) {
			if (!d.end().isBefore(from) && d.start().isBefore(to) && !d.confirmedAt().isAfter(to)) {
				expected.add("FVG/- " + d.direction() + " " + d.start().getEpochSecond() + " " + d.end().getEpochSecond()
					+ " " + d.confirmedAt().getEpochSecond() + " " + d.lowTicks() + "-" + d.highTicks());
			}
		}
		assertThat(expected).hasSizeGreaterThan(3);
		assertThat(described(layer.get("setups"), 0)).containsExactlyElementsOf(expected);
		assertThat(layer.toString()).doesNotContain("evidence").doesNotContain("gapTicks");
		layer.get("setups").forEach(s -> assertThat(s.get("shape").asString()).isEqualTo("ZONE"));
	}

	@Test
	@DisplayName("a narrower window is the wider one cut at its right edge: nothing confirmed after it")
	void rightEdge() throws Exception {
		Instant from = Instant.parse("2025-05-05T13:00:00Z");
		Instant edge = Instant.parse("2025-05-06T15:37:00Z");
		JsonNode wide = chartSetups(from, Instant.parse("2025-05-07T20:00:00Z"));
		JsonNode narrow = chartSetups(from, edge);

		List<JsonNode> cut = new ArrayList<>();
		wide.forEach(s -> {
			if (s.get("confirmedAt").asLong() <= edge.getEpochSecond() && s.get("start").asLong() < edge.getEpochSecond()) {
				cut.add(s);
			}
		});
		assertThat(narrow.size()).isPositive().isLessThan(wide.size());
		assertThat(described(narrow, 0)).containsExactlyElementsOf(described(json.valueToTree(cut), 0));
	}

	@Test
	@DisplayName("nothing spans the roll, though the two contracts side by side would make a gap, and the roll is sent")
	void roll() throws Exception {
		Instant from = Instant.parse("2025-05-08T12:00:00Z");
		Instant to = Instant.parse("2025-05-08T18:00:00Z");
		// Handed both contracts at once, the rule would see the jump as a gap.
		List<Bar> unsplit = marketData.internalBars(nq, Timeframe.M5, from.minusSeconds(86_400), to, 0).bars();
		assertThat(new FvgDetector().detect(unsplit, Timeframe.M5))
			.anySatisfy(d -> {
				assertThat(d.start()).isBefore(ROLL);
				assertThat(d.end()).isAfterOrEqualTo(ROLL);
			});

		JsonNode layer = read(chartRequest("NQ", "5m", from, to, ALL).andExpect(status().isOk()));
		assertThat(layer.get("rolls")).extracting(JsonNode::asLong).containsExactly(ROLL.getEpochSecond());
		JsonNode setups = layer.get("setups");
		assertThat(setups.size()).isPositive();
		setups.forEach(s -> assertThat(s.get("start").asLong() < ROLL.getEpochSecond()
			&& s.get("end").asLong() >= ROLL.getEpochSecond()).as("spans the roll: %s", s).isFalse());
	}

	@Test
	@DisplayName("refuses what the bars would refuse, unknown rules, one-second charts and strangers")
	void limits() throws Exception {
		Instant from = Instant.parse("2025-05-05T13:00:00Z");
		Instant to = Instant.parse("2025-05-06T13:00:00Z");
		chartRequest("NQ", "5m", from, to, null).andExpect(status().isBadRequest());
		chartRequest("NQ", "5m", from, to, "NOPE").andExpect(status().isBadRequest());
		chartRequest("NQ", "1s", from, to, "FVG").andExpect(status().isBadRequest());
		chartRequest("NQ", "5m", to, from, "FVG").andExpect(status().isBadRequest());
		chartRequest("NQ", "1m", from, from.plusSeconds(30L * 86_400), "FVG").andExpect(status().isBadRequest());
		chartRequest("DEMO-NQ", "5m", from, from.plusSeconds(15L * 86_400), "FVG").andExpect(status().isBadRequest());
		chartRequest("NOPE", "5m", from, to, "FVG").andExpect(status().isNotFound());
		mockMvc.perform(get("/api/market/instruments/NQ/setups").param("from", "1").param("to", "2")
				.param("detectors", "FVG"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("a replay gets what a scan up to its cursor finds, on its hidden-date clock, and nothing later")
	void practice() throws Exception {
		JsonNode session = read(mockMvc.perform(post("/api/practice/sessions").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isCreated()));
		String id = session.at("/state/id").asString();
		JsonNode first = practiceSetups(id, alice);
		assertThat(first.size()).isPositive();
		assertMatchesTheChart(id, first);

		// Thirty bars on: everything shown before is still there, and more has been confirmed.
		mockMvc.perform(post("/api/practice/sessions/" + id + "/advance").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"bars\":30}"))
			.andExpect(status().isOk());
		JsonNode later = practiceSetups(id, alice);
		assertThat(described(later, 0)).containsAll(described(first, 0));
		assertMatchesTheChart(id, later);

		// Someone else's session is not theirs to read.
		mockMvc.perform(get("/api/practice/sessions/" + id + "/setups").param("detectors", "FVG")
				.cookie(register("bob@example.com")))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/practice/sessions/" + UUID.randomUUID() + "/setups").param("detectors", "FVG")
				.cookie(alice))
			.andExpect(status().isNotFound());
	}

	private JsonNode practiceSetups(String id, Cookie who) throws Exception {
		JsonNode layer = read(mockMvc.perform(get("/api/practice/sessions/" + id + "/setups")
				.param("detectors", ALL).cookie(who))
			.andExpect(status().isOk()));
		return layer.get("setups");
	}

	/**
	 * The replay's setups are the Chart tab's over the same real stretch, up to the cursor, moved onto
	 * the session's fictional week — and the real date is nowhere in them.
	 */
	private void assertMatchesTheChart(String id, JsonNode practice) throws Exception {
		var row = jdbc.queryForMap(
			"SELECT start_time, history_from, cursor_time FROM practice_session WHERE id = ?::uuid", id);
		Instant realStart = ((Timestamp) row.get("start_time")).toInstant();
		Instant historyFrom = ((Timestamp) row.get("history_from")).toInstant();
		Instant cursor = ((Timestamp) row.get("cursor_time")).toInstant();
		JsonNode state = read(mockMvc.perform(get("/api/practice/sessions/" + id).cookie(alice))).get("state");
		long shift = state.get("startTime").asLong() - realStart.getEpochSecond();
		long shownCursor = state.get("cursor").asLong();

		for (JsonNode s : practice) {
			assertThat(s.get("confirmedAt").asLong()).isBetween(YEAR_2000, YEAR_2001).isLessThanOrEqualTo(shownCursor);
			assertThat(s.get("start").asLong()).isLessThan(shownCursor);
		}
		assertThat(practice.toString()).doesNotContain(Long.toString(realStart.getEpochSecond()));
		assertThat(described(practice, 0)).containsExactlyElementsOf(described(chartSetups(historyFrom, cursor, ALL), shift));
		// A comparison over most of the rules, not a handful. Not all of them: the replay starts at a random
		// moment, and a rare rule (an MSS) can have nothing in its four days (BUG-051). Every rule is shown
		// to come through the endpoint over a fixed stretch in everyRule().
		assertThat(described(practice, 0).stream().map(d -> d.substring(0, d.indexOf('/'))).distinct().count())
			.isGreaterThanOrEqualTo(12);
	}

	@Test
	@DisplayName("T17, T18: each zone carries the line drawn through it — a gap's middle, a block's mean threshold, an OTE's 70.5%")
	void midlines() throws Exception {
		JsonNode setups = chartSetups(Instant.parse("2025-04-28T00:00:00Z"), Instant.parse("2025-05-08T00:00:00Z"), "FVG,OB,OTE");
		java.util.Set<String> offCentre = new java.util.HashSet<>();
		for (JsonNode s : setups) {
			long low = s.get("lowTicks").asLong();
			long high = s.get("highTicks").asLong();
			long mid = s.get("midHalfTicks").asLong();
			if (s.get("detector").asString().equals("FVG")) {
				assertThat(mid).isEqualTo(low + high);
			} else {
				assertThat(mid).isBetween(2 * low, 2 * high);
				if (mid != low + high) {
					offCentre.add(s.get("detector").asString());
				}
			}
		}
		// The middle of the bodies is rarely the middle of the whole range; 70.5% is never the middle of 62–79%.
		assertThat(offCentre).as("lines off their zone's middle").containsExactlyInAnyOrder("OB", "OTE");
	}

	@Test
	@DisplayName("over a fixed fortnight the chart has something from every rule, so the comparisons cover them all")
	void everyRule() throws Exception {
		JsonNode setups = chartSetups(Instant.parse("2025-04-28T00:00:00Z"), Instant.parse("2025-05-08T00:00:00Z"), ALL);
		assertThat(described(setups, 0)).extracting(d -> d.substring(0, d.indexOf('/')))
			.containsAll(List.of(ALL.split(",")));
	}
}
