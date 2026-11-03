package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The journal and the figures over it (T41): one row per finished trade, newest placed first, a session's date kept
 * hidden until it is complete; held to plan, costs paid, best and worst — at the standard costs every trade pays now
 * (DEC-080), since this class sets none.
 */
class PracticeJournalApiTest extends AbstractIntegrationTest {

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
	MarketDataService marketData;

	@Autowired
	MarketBarStore barStore;

	private Cookie alice;
	private Cookie bob;

	@BeforeEach
	void twoLearnersAndTwoWeeksOfNq() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		jdbc.update("DELETE FROM market_bar");
		jdbc.update("DELETE FROM market_data_import");
		alice = register("alice@example.com");
		bob = register("bob@example.com");
		UUID nqId = marketData.requireInstrument("NQ").getId();
		Random random = new Random(20250303L);
		List<StoredBar> bars = new ArrayList<>();
		long price = 80_000;
		for (Instant t = DATA_FROM; t.isBefore(DATA_TO); t = t.plusSeconds(60)) {
			if (!SessionCalendar.isOpen(t)) {
				continue;
			}
			long open = price;
			long close = open + random.nextInt(17) - 8;
			bars.add(new StoredBar(new Bar(t, open, Math.max(open, close) + random.nextInt(4),
				Math.min(open, close) - random.nextInt(4), close, 10), 202503));
			price = close;
		}
		barStore.insert(nqId, "1m", bars);
		jdbc.update("""
			INSERT INTO market_data_import (instrument_id, source, dataset, symbol, resolution,
			    range_from, range_to, estimated_cost_usd, bars_stored, status)
			VALUES (?, 'massive', 'CME', 'NQH5', '1m', ?, ?, 0, ?, 'DONE')""",
			nqId, Timestamp.from(DATA_FROM), Timestamp.from(DATA_TO), bars.size());
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

	/**
	 * A session with one trade, 15 points either side, skipped to its result; its id. Ended when asked: a learner's open
	 * session holds its days back from the next one (DEC-077), and twelve days of history leave little room for two.
	 */
	private String oneTrade(Cookie who, String direction, boolean end) throws Exception {
		JsonNode session = read(mockMvc.perform(post("/api/practice/sessions").cookie(who).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isCreated()));
		String id = session.at("/state/id").asString();
		JsonNode bars = session.get("bars");
		long reference = bars.get(bars.size() - 1).get("close").asLong();
		mockMvc.perform(post("/api/practice/sessions/" + id + "/plan").cookie(who).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"direction\":\"%s\",\"stopTicks\":%d,\"targetTicks\":%d,\"contracts\":1}"
					.formatted(direction, direction.equals("LONG") ? reference - 60 : reference + 60,
						direction.equals("LONG") ? reference + 60 : reference - 60)))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/practice/sessions/" + id + "/skip").cookie(who).with(csrf())).andExpect(status().isOk());
		if (end) {
			mockMvc.perform(post("/api/practice/sessions/" + id + "/end").cookie(who).with(csrf())).andExpect(status().isOk());
		}
		return id;
	}

	private Instant placed(String sessionId) {
		return jdbc.queryForObject("SELECT submitted_cursor FROM trade_plan WHERE session_id = ?::uuid", Timestamp.class,
			sessionId).toInstant();
	}

	private static JsonNode row(JsonNode rows, String sessionId) {
		for (JsonNode row : rows) {
			if (row.get("sessionId").asString().equals(sessionId)) {
				return row;
			}
		}
		throw new AssertionError("no row for session " + sessionId);
	}

	private JsonNode journal(Cookie who, String query) throws Exception {
		return read(mockMvc.perform(get("/api/practice/trades" + query).cookie(who)).andExpect(status().isOk()));
	}

	@Test
	@DisplayName("one row per finished trade, newest placed first; the date hidden until its session is complete")
	void journalRows() throws Exception {
		String first = oneTrade(alice, "LONG", true);
		String second = oneTrade(alice, "SHORT", false);
		oneTrade(bob, "LONG", false);

		JsonNode rows = journal(alice, "");
		assertThat(rows.size()).isEqualTo(2);
		Instant firstPlaced = placed(first);
		Instant secondPlaced = placed(second);
		// BUG-158: newest placed first in real time, whatever days of history the replays were on. The first trade made
		// later than the second, as far as the clock is concerned: it now leads.
		assertThat(rows.get(0).get("sessionId").asString()).isEqualTo(second);
		// Plans are immutable (INVARIANT 2); the test steps round that guard for this one row, and only here.
		jdbc.execute("DO $$ BEGIN SET LOCAL session_replication_role = replica; UPDATE trade_plan SET submitted_at = now()"
			+ " + interval '1 hour' WHERE session_id = '" + UUID.fromString(first) + "'; END $$");
		assertThat(journal(alice, "").get(0).get("sessionId").asString()).isEqualTo(first);
		JsonNode newest = row(rows, second);
		JsonNode ended = row(rows, first);
		// The ended one has revealed its date: the real moment, in UTC.
		assertThat(ended.get("timeBasis").asString()).isEqualTo("UTC");
		assertThat(ended.get("at").asLong()).isEqualTo(firstPlaced.getEpochSecond());
		assertThat(newest.get("sessionId").asString()).isEqualTo(second);
		assertThat(newest.get("number").asInt()).isEqualTo(1);
		assertThat(newest.get("direction").asString()).isEqualTo("SHORT");
		assertThat(newest.get("instrument").asString()).isEqualTo("NQ");
		assertThat(newest.get("tickSize").decimalValue()).isEqualByComparingTo("0.25");
		assertThat(newest.get("outcome").asString()).isIn("TARGET", "STOP", "AMBIGUOUS", "TIME_EXIT");
		// Open: the session's own clock, a week in January 2000.
		assertThat(newest.get("timeBasis").asString()).isEqualTo("NEW_YORK_WALL_CLOCK");
		assertThat(newest.get("at").asLong()).isBetween(YEAR_2000, YEAR_2001);

		// The row agrees with the session's own record of the trade.
		JsonNode result = read(mockMvc.perform(get("/api/practice/sessions/" + second).cookie(alice)))
			.at("/state/trades/0/result");
		assertThat(newest.get("entryTicks").asLong()).isEqualTo(result.get("entryTicks").asLong());
		assertThat(newest.get("netPnlCents").asLong()).isEqualTo(result.get("netPnlCents").asLong());
		assertThat(newest.get("r").asDouble()).isEqualTo(result.get("rMultiple").asDouble());
		// No costs set anywhere: the standard ones (DEC-080).
		assertThat(result.get("fillModelVersion").asString()).isEqualTo("mkt-1t/slip-1t/thru-1t/comm-450c");

		// Complete, the session has revealed its date: the real moment, in UTC.
		mockMvc.perform(post("/api/practice/sessions/" + second + "/end").cookie(alice).with(csrf())).andExpect(status().isOk());
		JsonNode revealed = row(journal(alice, ""), second);
		assertThat(revealed.get("timeBasis").asString()).isEqualTo("UTC");
		assertThat(revealed.get("at").asLong()).isEqualTo(secondPlaced.getEpochSecond());

		// Bob's own, never Alice's; and the limit is a limit.
		assertThat(journal(bob, "").size()).isEqualTo(1);
		assertThat(journal(alice, "?limit=1").size()).isEqualTo(1);
		mockMvc.perform(get("/api/practice/trades?limit=0").cookie(alice)).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/practice/trades?limit=201").cookie(alice)).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("BUG-157 page by page with an offset, and filtered on the server before paging")
	void pagesAndFilters() throws Exception {
		String longOne = oneTrade(alice, "LONG", true);
		String shortOne = oneTrade(alice, "SHORT", false);
		JsonNode all = journal(alice, "");
		assertThat(all.size()).isEqualTo(2);
		assertThat(journal(alice, "?limit=1&offset=1").get(0).get("sessionId").asString())
			.isEqualTo(all.get(1).get("sessionId").asString());
		assertThat(journal(alice, "?offset=2").size()).isZero();

		// A match beyond the first page is found: the long trade is the older one, past limit=1 unfiltered.
		JsonNode longs = journal(alice, "?limit=1&kind=LONG");
		assertThat(longs.size()).isEqualTo(1);
		assertThat(longs.get(0).get("sessionId").asString()).isEqualTo(longOne);
		assertThat(journal(alice, "?kind=SHORT").get(0).get("sessionId").asString()).isEqualTo(shortOne);

		mockMvc.perform(get("/api/practice/trades?offset=-1").cookie(alice)).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/practice/trades?kind=EVERYTHING").cookie(alice)).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("a running trade is not in the journal: only exits the replay has shown")
	void runningNotListed() throws Exception {
		JsonNode session = read(mockMvc.perform(post("/api/practice/sessions").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isCreated()));
		String id = session.at("/state/id").asString();
		JsonNode bars = session.get("bars");
		long reference = bars.get(bars.size() - 1).get("close").asLong();
		mockMvc.perform(post("/api/practice/sessions/" + id + "/plan").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"direction\":\"LONG\",\"stopTicks\":%d,\"targetTicks\":%d,\"contracts\":1}"
					.formatted(reference - 4000, reference + 4000)))
			.andExpect(status().isOk());
		assertThat(journal(alice, "").size()).isZero();
	}

	@Test
	@DisplayName("statistics: held to plan, costs paid as the scorecards count them, the best and worst trade")
	void figures() throws Exception {
		oneTrade(alice, "LONG", true);
		oneTrade(alice, "SHORT", true);
		JsonNode none = read(mockMvc.perform(get("/api/practice/statistics").cookie(bob)).andExpect(status().isOk()));
		assertThat(none.get("heldToPlan").asInt()).isZero();
		assertThat(none.get("costsPaidCents").asLong()).isZero();
		assertThat(none.has("bestR")).isFalse();

		JsonNode stats = read(mockMvc.perform(get("/api/practice/statistics").cookie(alice)).andExpect(status().isOk()));
		assertThat(stats.at("/overall/trades").asInt()).isEqualTo(2);
		assertThat(stats.get("heldToPlan").asInt()).isEqualTo(2);
		List<Double> rs = new ArrayList<>();
		long costs = 0;
		for (JsonNode row : journal(alice, "")) {
			rs.add(row.get("r").asDouble());
			JsonNode result = read(mockMvc.perform(get("/api/practice/sessions/" + row.get("sessionId").asString())
				.cookie(alice))).at("/state/trades/0/result");
			// Fees, a tick in at market, and the exit's own slippage — what the scorecard's costs line says.
			long exitSlip = switch (result.get("outcome").asString()) {
				case "STOP", "AMBIGUOUS" -> 1;
				case "TARGET" -> 0;
				default -> 1;
			};
			costs += result.get("commissionCents").asLong() + (1 + exitSlip) * 500;
		}
		assertThat(stats.get("costsPaidCents").asLong()).isEqualTo(costs);
		assertThat(stats.get("bestR").asDouble()).isEqualTo(rs.stream().mapToDouble(Double::doubleValue).max().getAsDouble());
		assertThat(stats.get("worstR").asDouble()).isEqualTo(rs.stream().mapToDouble(Double::doubleValue).min().getAsDouble());
	}
}
