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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeez.AbstractIntegrationTest;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketBarStore.StoredBar;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.SessionCalendar;
import com.tradeez.marketdata.Timeframe;

import jakarta.servlet.http.Cookie;

/**
 * T9 — detected setups over the Chart tab and over a practice replay, on imported one-minute NQ.
 *
 * <p>The minutes are the simulated market's, stored as an import stores them, so they have gaps to
 * find. Three weeks from Sunday 27 April 2025, all in New York summer time, with a roll to the June
 * contract in the middle of Thursday 8 May's morning — the June contract 1,000 points higher, so the
 * jump between the two would read as an enormous gap to a detector that was handed both.
 */
class SetupApiTest extends AbstractIntegrationTest {

	private static final Instant DATA_FROM = Instant.parse("2025-04-27T22:00:00Z");
	private static final Instant DATA_TO = Instant.parse("2025-05-17T00:00:00Z");
	private static final Instant ROLL = Instant.parse("2025-05-08T14:00:00Z");
	private static final long JUNE_PREMIUM_TICKS = 4_000;
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
		Instrument demo = marketData.requireInstrument("DEMO-NQ");
		List<StoredBar> bars = new ArrayList<>();
		for (Bar b : marketData.internalBars(demo, Timeframe.M1, DATA_FROM, DATA_TO, 7).bars()) {
			if (!SessionCalendar.isOpen(b.openTime())) {
				continue;
			}
			boolean june = !b.openTime().isBefore(ROLL);
			long shift = june ? JUNE_PREMIUM_TICKS : 0;
			bars.add(new StoredBar(new Bar(b.openTime(), b.openTicks() + shift, b.highTicks() + shift,
				b.lowTicks() + shift, b.closeTicks() + shift, b.volume()), june ? 202506 : 202503));
		}
		barStore.insert(nq.getId(), "1m", bars);
		jdbc.update("""
			INSERT INTO market_data_import (instrument_id, source, dataset, symbol, resolution,
			    range_from, range_to, estimated_cost_usd, bars_stored, status)
			VALUES (?, 'massive', 'CME', 'NQ', '1m', ?, ?, 0, ?, 'DONE')""",
			nq.getId(), Timestamp.from(DATA_FROM), Timestamp.from(DATA_TO), bars.size());
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
		return read(chartRequest("NQ", "5m", from, to, "FVG").andExpect(status().isOk())).get("setups");
	}

	/** A setup as a line of text, its times shifted: enough to compare two lists exactly. */
	private static List<String> described(JsonNode setups, long shift) {
		List<String> out = new ArrayList<>();
		setups.forEach(s -> out.add(s.get("direction").asText() + " " + (s.get("start").asLong() + shift) + " "
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

		assertThat(layer.at("/rules/0/detector").asText()).isEqualTo("FVG");
		assertThat(layer.at("/rules/0/version").asText()).isEqualTo(FvgDetector.RULE_VERSION);
		assertThat(layer.at("/rules/0/conceptId").asText()).isEqualTo("ICT-20");
		assertThat(layer.at("/rules/0/parameters/averageRangeLookback").asInt()).isEqualTo(14);

		// The same rule over the same candles, run by hand.
		List<Bar> bars = marketData.internalBars(nq, Timeframe.M5, from.minusSeconds(86_400), to, 0).bars();
		List<String> expected = new ArrayList<>();
		for (Detection d : new FvgDetector().detect(bars, Timeframe.M5)) {
			if (!d.end().isBefore(from) && d.start().isBefore(to) && !d.confirmedAt().isAfter(to)) {
				expected.add(d.direction() + " " + d.start().getEpochSecond() + " " + d.end().getEpochSecond()
					+ " " + d.confirmedAt().getEpochSecond() + " " + d.lowTicks() + "-" + d.highTicks());
			}
		}
		assertThat(expected).hasSizeGreaterThan(3);
		assertThat(described(layer.get("setups"), 0)).containsExactlyElementsOf(expected);
		assertThat(layer.toString()).doesNotContain("evidence").doesNotContain("gapTicks");
		layer.get("setups").forEach(s -> assertThat(s.get("shape").asText()).isEqualTo("ZONE"));
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
	@DisplayName("no gap spans the roll, though the two contracts side by side would make one")
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

		JsonNode setups = chartSetups(from, to);
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
		String id = session.at("/state/id").asText();
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
				.param("detectors", "FVG").cookie(who))
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
		assertThat(described(practice, 0)).containsExactlyElementsOf(described(chartSetups(historyFrom, cursor), shift));
	}
}
