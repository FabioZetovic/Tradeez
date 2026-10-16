package com.tradeez.clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
 * T7 — the clock layer over the Chart tab and over a practice replay, on imported one-minute NQ.
 *
 * <p>The minutes are synthetic and flat, stored exactly as an import stores them, with a roll from
 * the March to the June contract at the start of Thursday 13 March's session — the June contract
 * trading 250 points higher, as it would.
 */
class ClockApiTest extends AbstractIntegrationTest {

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
	private static final Instant DATA_FROM = ny("2025-03-02T18:00");
	private static final Instant DATA_TO = ny("2025-03-14T17:00");
	private static final Instant ROLL = ny("2025-03-12T18:00");
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

	private static Instant ny(String localDateTime) {
		return LocalDateTime.parse(localDateTime).atZone(NEW_YORK).toInstant();
	}

	@BeforeEach
	void importTwoWeeksWithARoll() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		jdbc.update("DELETE FROM market_bar");
		jdbc.update("DELETE FROM market_data_import");
		alice = register("alice@example.com");

		UUID nq = marketData.requireInstrument("NQ").getId();
		List<StoredBar> bars = new ArrayList<>();
		for (Instant t = DATA_FROM; t.isBefore(DATA_TO); t = t.plusSeconds(60)) {
			if (!SessionCalendar.isOpen(t)) {
				continue;
			}
			boolean june = !t.isBefore(ROLL);
			long price = june ? 81_000 : 80_000;
			// Each Monday's 10:00 minute is that day's high, and its 10:01 minute its low.
			LocalDateTime local = LocalDateTime.ofInstant(t, NEW_YORK);
			long high = price + (local.getHour() == 10 && local.getMinute() == 0 ? 40 : 0);
			long low = price - (local.getHour() == 10 && local.getMinute() == 1 ? 40 : 0);
			bars.add(new StoredBar(new Bar(t, price, high, low, price, 10), june ? 202506 : 202503));
		}
		barStore.insert(nq, "1m", bars);
		jdbc.update("""
			INSERT INTO market_data_import (instrument_id, source, dataset, symbol, resolution,
			    range_from, range_to, estimated_cost_usd, bars_stored, status)
			VALUES (?, 'massive', 'CME', 'NQ', '1m', ?, ?, 0, ?, 'DONE')""",
			nq, Timestamp.from(DATA_FROM), Timestamp.from(DATA_TO), bars.size());
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

	private ResultActions chartClock(Cookie who, Instant from, Instant to) throws Exception {
		return mockMvc.perform(get("/api/market/instruments/NQ/clock")
			.param("from", Long.toString(from.getEpochSecond()))
			.param("to", Long.toString(to.getEpochSecond()))
			.cookie(who));
	}

	private static List<JsonNode> lines(JsonNode layer, String label) {
		List<JsonNode> found = new ArrayList<>();
		layer.get("lines").forEach(l -> {
			if (l.get("label").asString().equals(label)) {
				found.add(l);
			}
		});
		return found;
	}

	@Test
	@DisplayName("the Chart tab gets the clock for its window, built under named rule versions")
	void chart() throws Exception {
		JsonNode layer = read(chartClock(alice, ny("2025-03-11T00:00"), ny("2025-03-12T00:00"))
			.andExpect(status().isOk()));

		assertThat(layer.at("/rules/sessions").asString()).isEqualTo("sessions-1.0.0");
		assertThat(layer.at("/rules/opens").asString()).isEqualTo("opens-1.0.0");
		assertThat(layer.at("/rules/levels").asString()).isEqualTo("levels-1.0.0");
		assertThat(layer.has("nextChange")).isFalse();

		// Tuesday's PDH is Monday's 10:00 high, drawn over Tuesday's whole session.
		JsonNode pdh = lines(layer, "PDH").getFirst();
		assertThat(pdh.get("priceTicks").asLong()).isEqualTo(80_040);
		assertThat(pdh.get("from").asLong()).isEqualTo(ny("2025-03-10T18:00").getEpochSecond());
		assertThat(pdh.get("to").asLong()).isEqualTo(ny("2025-03-11T17:00").getEpochSecond());
		assertThat(lines(layer, "PWH")).isNotEmpty();
		assertThat(layer.get("windows").findValuesAsString("label")).contains("Tue", "London", "NY AM");
	}

	@Test
	@DisplayName("no level is carried across the roll to the next contract")
	void roll() throws Exception {
		JsonNode layer = read(chartClock(alice, ny("2025-03-12T12:00"), ny("2025-03-13T17:00"))
			.andExpect(status().isOk()));

		// Wednesday's PDH (Tuesday's high) is there; Thursday's is not — Thursday is the June
		// contract's first day, and Wednesday's high was the March contract's.
		assertThat(lines(layer, "PDH")).extracting(l -> l.get("from").asLong())
			.containsExactly(ny("2025-03-11T18:00").getEpochSecond());
		assertThat(lines(layer, "PDL")).extracting(l -> l.get("from").asLong())
			.doesNotContain(ROLL.getEpochSecond());
		// Each day's opening price is on its own contract: Wednesday March's, Thursday June's.
		assertThat(lines(layer, "09:30 open")).extracting(l -> l.get("priceTicks").asLong())
			.containsExactly(80_000L, 81_000L);
		// The week's lines stop at the roll.
		assertThat(lines(layer, "Weekly open").getFirst().get("to").asLong())
			.isEqualTo(ROLL.getEpochSecond());
		assertThat(lines(layer, "PWH").getFirst().get("to").asLong()).isEqualTo(ROLL.getEpochSecond());
	}

	@Test
	@DisplayName("refuses ranges over 200 days, unknown instruments, and strangers")
	void limits() throws Exception {
		chartClock(alice, ny("2024-03-01T00:00"), ny("2025-03-01T00:00")).andExpect(status().isBadRequest());
		chartClock(alice, ny("2025-03-12T00:00"), ny("2025-03-11T00:00")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/market/instruments/NOPE/clock").param("from", "0").param("to", "60")
			.cookie(alice)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/market/instruments/NQ/clock").param("from", "0").param("to", "60"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("a replay gets the clock up to its cursor, on its hidden-date clock, and nothing after")
	void practice() throws Exception {
		JsonNode session = read(mockMvc.perform(post("/api/practice/sessions").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"killzone\":\"NEW_YORK_AM\"}"))
			.andExpect(status().isCreated()));
		String id = session.at("/state/id").asString();
		long cursor = session.at("/state/cursor").asLong();

		JsonNode layer = read(mockMvc.perform(get("/api/practice/sessions/" + id + "/clock").cookie(alice))
			.andExpect(status().isOk()));
		String raw = json.writeValueAsString(layer);

		// DEC-045: every time on the fictional week, the real date nowhere.
		Instant real = jdbc.queryForObject("SELECT start_time FROM practice_session WHERE id = ?::uuid",
			Timestamp.class, id).toInstant();
		assertThat(raw).doesNotContain(Long.toString(real.getEpochSecond()));
		// DEC-009: nothing begins after the cursor, and the next change is still ahead of it.
		assertThat(layer.get("windows").size()).isPositive();
		for (JsonNode w : layer.get("windows")) {
			assertThat(w.get("from").asLong()).isBetween(YEAR_2000, YEAR_2001).isLessThan(cursor);
		}
		for (JsonNode l : layer.get("lines")) {
			assertThat(l.get("from").asLong()).isBetween(YEAR_2000, YEAR_2001).isLessThanOrEqualTo(cursor);
		}
		// The moment is inside today's NY AM, which has not closed: earlier days' NY AM highs are
		// there, today's does not exist yet.
		assertThat(lines(layer, "NY AM H")).isNotEmpty()
			.allSatisfy(l -> assertThat(shownDate(l.get("from").asLong())).isBefore(shownDate(cursor)));
		assertThat(layer.get("nextChange").asLong()).isGreaterThan(cursor);

		// Someone else's session is not theirs to read.
		mockMvc.perform(get("/api/practice/sessions/" + id + "/clock").cookie(register("bob@example.com")))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("a replay that steps past the killzone's close then gets its high and low")
	void practiceGrows() throws Exception {
		JsonNode session = read(mockMvc.perform(post("/api/practice/sessions").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"killzone\":\"NEW_YORK_AM\",\"timeframe\":\"15m\"}"))
			.andExpect(status().isCreated()));
		String id = session.at("/state/id").asString();

		// NY AM is at most two and a half hours: ten fifteen-minute bars are past its close.
		mockMvc.perform(post("/api/practice/sessions/" + id + "/advance").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"bars\":10}"))
			.andExpect(status().isOk());
		JsonNode layer = read(mockMvc.perform(get("/api/practice/sessions/" + id + "/clock").cookie(alice))
			.andExpect(status().isOk()));
		java.time.LocalDate today = shownDate(session.at("/state/startTime").asLong());
		assertThat(lines(layer, "NY AM H"))
			.anySatisfy(l -> assertThat(shownDate(l.get("from").asLong())).isEqualTo(today));
	}

	/** The New York date of a time shown on a session's clock: wall-clock time written as UTC. */
	private static java.time.LocalDate shownDate(long shown) {
		return LocalDateTime.ofEpochSecond(shown, 0, java.time.ZoneOffset.UTC).toLocalDate();
	}
}
