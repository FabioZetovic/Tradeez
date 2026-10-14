package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketBarStore.StoredBar;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.SessionCalendar;

import jakarta.servlet.http.Cookie;

/**
 * T4 — practice on real history, against imported one-minute bars in a real PostgreSQL.
 *
 * <p>The bars are synthetic but stored exactly as the Massive importer stores them, with a ledger
 * row naming the source. They run from Monday 3 to Friday 14 March 2025, across the US switch to
 * daylight saving on Sunday the 9th, so the hidden-date clock is tested where it is most likely to
 * go wrong.
 */
class RealPracticeApiTest extends AbstractIntegrationTest {

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
	private static final Instant DATA_FROM = Instant.parse("2025-03-03T00:00:00Z");
	private static final Instant DATA_TO = Instant.parse("2025-03-15T00:00:00Z");
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
	private UUID nqId;

	@BeforeEach
	void importTwoWeeksOfNq() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		jdbc.update("DELETE FROM market_bar");
		jdbc.update("DELETE FROM market_data_import");
		alice = mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"alice@example.com\",\"password\":\"a-perfectly-fine-passphrase\","
					+ "\"displayName\":\"A\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);

		nqId = marketData.requireInstrument("NQ").getId();
		// A deterministic walk on the 0.25 grid, one bar for every minute CME is open.
		Random random = new Random(20250303L);
		List<StoredBar> bars = new ArrayList<>();
		long price = 80_000;
		for (Instant t = DATA_FROM; t.isBefore(DATA_TO); t = t.plusSeconds(60)) {
			if (!SessionCalendar.isOpen(t)) {
				continue;
			}
			long open = price;
			long close = open + random.nextInt(17) - 8;
			long high = Math.max(open, close) + random.nextInt(4);
			long low = Math.min(open, close) - random.nextInt(4);
			bars.add(new StoredBar(new Bar(t, open, high, low, close, 10), 202503));
			price = close;
		}
		barStore.insert(nqId, "1m", bars);
		jdbc.update("""
			INSERT INTO market_data_import (instrument_id, source, dataset, symbol, resolution,
			    range_from, range_to, estimated_cost_usd, bars_stored, status)
			VALUES (?, 'massive', 'CME', 'NQH5', '1m', ?, ?, 0, ?, 'DONE')""",
			nqId, Timestamp.from(DATA_FROM), Timestamp.from(DATA_TO), bars.size());
	}

	private JsonNode read(ResultActions actions) throws Exception {
		return json.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private ResultActions create(String body) throws Exception {
		return mockMvc.perform(post("/api/practice/sessions").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private JsonNode created(String body) throws Exception {
		return read(create(body).andExpect(status().isCreated()));
	}

	/** The real hidden moment, read straight from the database: the client is never told it. */
	private Instant realStart(JsonNode session) {
		return jdbc.queryForObject("SELECT start_time FROM practice_session WHERE id = ?::uuid",
			Timestamp.class, session.at("/state/id").asText()).toInstant();
	}

	private static LocalDateTime rendered(long shown) {
		return LocalDateTime.ofEpochSecond(shown, 0, ZoneOffset.UTC);
	}

	@Test
	@DisplayName("a session on real NQ never sends the real date — only weekday and New York time")
	void hidesTheDate() throws Exception {
		JsonNode session = created("{}");
		String raw = json.writeValueAsString(session);
		JsonNode state = session.get("state");

		assertThat(state.get("instrument").asText()).isEqualTo("NQ");
		assertThat(state.get("timeframe").asText()).isEqualTo("5m");
		assertThat(state.get("dataClass").asText()).isEqualTo("IMPORTED_PRIVATE");
		assertThat(state.get("timeBasis").asText()).isEqualTo(PracticeClock.NEW_YORK_WALL_CLOCK);
		assertThat(state.get("dataSource").asText()).isEqualTo("massive");
		assertThat(state.has("seed")).isFalse();
		assertThat(state.has("revealedStartTime")).isFalse();
		assertThat(state.has("disclaimer")).isFalse();

		// Every time is on the fictional week in January 2000, and nothing past the cursor is sent.
		long cursor = state.get("cursor").asLong();
		JsonNode bars = session.get("bars");
		assertThat(bars.size()).isGreaterThan(100);
		for (JsonNode bar : bars) {
			assertThat(bar.get("time").asLong()).isBetween(YEAR_2000, YEAR_2001).isLessThan(cursor);
		}

		// Weekday and wall-clock time match the real moment; the real epoch appears nowhere.
		Instant real = realStart(session);
		LocalDateTime realNy = real.atZone(NEW_YORK).toLocalDateTime();
		LocalDateTime shown = rendered(state.get("startTime").asLong());
		assertThat(shown.getDayOfWeek()).isEqualTo(realNy.getDayOfWeek());
		assertThat(shown.toLocalTime()).isEqualTo(realNy.toLocalTime());
		assertThat(raw).doesNotContain(Long.toString(real.getEpochSecond()));
	}

	@Test
	@DisplayName("the moment can be asked for by ICT killzone and weekday")
	void filters() throws Exception {
		for (int i = 0; i < 5; i++) {
			Instant real = realStart(created("{\"killzone\":\"LONDON\",\"weekday\":\"TUESDAY\"}"))
				.atZone(NEW_YORK).toInstant();
			LocalTime t = real.atZone(NEW_YORK).toLocalTime();
			assertThat(real.atZone(NEW_YORK).getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
			assertThat(t).isBetween(LocalTime.of(2, 0), LocalTime.of(4, 59));
		}
		LocalTime nyAm = realStart(created("{\"killzone\":\"new-york-am\"}"))
			.atZone(NEW_YORK).toLocalTime();
		assertThat(nyAm).isBetween(LocalTime.of(8, 30), LocalTime.of(10, 59));

		create("{\"killzone\":\"TOKYO\"}")
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("a trade resolves on one-minute bars; the real date is revealed when the session ends")
	void resolvesOnMinutesAndRevealsTheDate() throws Exception {
		JsonNode session = created("{\"timeframe\":\"5m\"}");
		String id = session.at("/state/id").asText();
		JsonNode bars = session.get("bars");
		long reference = bars.get(bars.size() - 1).get("close").asLong();

		mockMvc.perform(post("/api/practice/sessions/" + id + "/plan").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"direction\":\"LONG\",\"stopTicks\":%d,\"targetTicks\":%d,\"contracts\":1}"
					.formatted(reference - 60, reference + 60)))
			.andExpect(status().isOk());
		JsonNode closed = read(mockMvc.perform(post("/api/practice/sessions/" + id + "/skip")
				.cookie(alice).with(csrf()))
			.andExpect(status().isOk())).get("state");

		assertThat(closed.get("status").asText()).isEqualTo("ANALYSING");
		JsonNode result = closed.at("/trades/0/result");
		assertThat(result.get("resolution").asText()).isEqualTo("M1");
		assertThat(result.get("engineVersion").asText()).isEqualTo("fill-1.1.0");
		assertThat(result.get("entryTime").asLong()).isBetween(YEAR_2000, YEAR_2001);
		assertThat(result.get("exitTime").asLong()).isBetween(YEAR_2000, YEAR_2001);
		if ("AMBIGUOUS".equals(result.get("outcome").asText())) {
			assertThat(result.get("assumption").asText()).contains("same minute");
		}
		// The trade is over but the session is not: another trade could still use the date.
		assertThat(closed.has("revealedStartTime")).isFalse();
		assertThat(json.writeValueAsString(closed))
			.doesNotContain(Long.toString(realStart(session).getEpochSecond()));

		JsonNode done = read(mockMvc.perform(post("/api/practice/sessions/" + id + "/end")
				.cookie(alice).with(csrf()))
			.andExpect(status().isOk()));
		// DEC-025: now, and only now, the real date.
		assertThat(done.get("status").asText()).isEqualTo("COMPLETE");
		assertThat(done.get("revealedStartTime").asLong()).isEqualTo(realStart(session).getEpochSecond());
		assertThat(done.has("seed")).isFalse();

		var row = jdbc.queryForMap(
			"SELECT data_source, seed FROM practice_result WHERE session_id = ?::uuid", id);
		assertThat(row).containsEntry("data_source", "massive").containsEntry("seed", null);
	}

	@Test
	@DisplayName("without imported history, says so instead of practising on nothing")
	void noHistory() throws Exception {
		jdbc.update("DELETE FROM market_bar");
		create("{\"instrument\":\"NQ\"}")
			.andExpect(status().isBadRequest())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
				.jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
					"No real NQ history has been imported")));
	}

	@Test
	@DisplayName("simulated practice keeps real epoch times and its seed rules (DEC-038)")
	void simulatedUnchanged() throws Exception {
		JsonNode state = created("{\"instrument\":\"DEMO-NQ\"}").get("state");
		assertThat(state.get("timeBasis").asText()).isEqualTo(PracticeClock.UTC);
		assertThat(state.get("dataClass").asText()).isEqualTo("SIMULATED");
		assertThat(state.has("seed")).isFalse();
		assertThat(state.get("startTime").asLong()).isGreaterThan(YEAR_2001);
	}
}
