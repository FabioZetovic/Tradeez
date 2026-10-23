package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * Stop loss and take profit through the whole app — the API, the service, the fill engine, the
 * database — on imported one-minute NQ, as a learner's trade really runs (fill-1.4.0).
 *
 * <p>Two kinds of test. <b>Worked examples</b>: the replay is put at a chosen minute and the minutes
 * after it are written by hand, so each case reads like the lesson it is — a long reaching its target,
 * a short gapping through its stop — with every number of the result checked: outcome, entry, exit,
 * ticks, dollars, commission, R, worst and best point. <b>An independent replay</b>: many random
 * brackets on a market that gaps, pauses and swings wide, each result recomputed here from the stored
 * minutes by a second, separate implementation of the rules, field by field.
 *
 * <p>NQ: a tick is 0.25 points and $5.00 a contract; stops slip one tick; commission is $2.50 a
 * contract a round trip; market fills do not slip and a target fills on the touch — FillModel.BASIC, fill-1.2.0's
 * numbers, set as the learner's costs before each test (T27). The standard costs are PracticeApiTest's. R is the net
 * result, commission paid, over the risk planned (DEC-076): $2.50 on a 10-tick stop at $5 is 0.05R.
 */
class BracketOutcomeApiTest extends AbstractIntegrationTest {

	private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
	private static final Instant DATA_FROM = Instant.parse("2025-06-02T00:00:00Z");
	private static final Instant DATA_TO = Instant.parse("2025-06-14T00:00:00Z");
	private static final long TICK_CENTS = 500;
	private static final long COMMISSION_CENTS = 250;

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

	/**
	 * The background market: a walk on the tick grid for every open minute, which every so often opens
	 * away from the last close (a gap), swings wide, or has a quiet minute with no bar at all.
	 */
	private TreeMap<Instant, Bar> market() {
		Random random = new Random(20250602L);
		TreeMap<Instant, Bar> bars = new TreeMap<>();
		long price = 80_000;
		long n = 0;
		for (Instant t = DATA_FROM; t.isBefore(DATA_TO); t = t.plusSeconds(60)) {
			if (!SessionCalendar.isOpen(t)) {
				continue;
			}
			n++;
			if (n % 89 == 0) {
				continue; // a quiet minute: no trade, no bar
			}
			long open = n % 37 == 0 ? price + (random.nextBoolean() ? 1 : -1) * (6 + random.nextInt(9)) : price;
			long close = open + random.nextInt(17) - 8;
			int reach = n % 23 == 0 ? 12 : 4;
			long high = Math.max(open, close) + random.nextInt(reach);
			long low = Math.min(open, close) - random.nextInt(reach);
			bars.put(t, new Bar(t, open, high, low, close, 10));
			price = close;
		}
		return bars;
	}

	@BeforeEach
	void importTwoWeeks() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch(); // sessions cascade from their user
		jdbc.update("DELETE FROM market_bar");
		jdbc.update("DELETE FROM market_data_import");
		alice = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"alice@example.com\",\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"A\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
		mockMvc.perform(put("/api/practice/settings").cookie(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(PracticeApiTest.BASIC_COSTS)).andExpect(status().isOk());
		nqId = marketData.requireInstrument("NQ").getId();
	}

	private void store(TreeMap<Instant, Bar> bars) {
		barStore.insert(nqId, "1m", bars.values().stream().map(b -> new StoredBar(b, 202506)).toList());
		jdbc.update("""
			INSERT INTO market_data_import (instrument_id, source, dataset, symbol, resolution,
			    range_from, range_to, estimated_cost_usd, bars_stored, status)
			VALUES (?, 'massive', 'CME', 'NQM5', '1m', ?, ?, 0, ?, 'DONE')""",
			nqId, Timestamp.from(DATA_FROM), Timestamp.from(DATA_TO), bars.size());
	}

	private JsonNode read(ResultActions actions) throws Exception {
		return json.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private String newSession() throws Exception {
		return read(mockMvc.perform(post("/api/practice/sessions").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"timeframe\":\"5m\"}"))
			.andExpect(status().isCreated())).at("/state/id").asString();
	}

	private ResultActions plan(String id, String direction, long stop, long target, int contracts) throws Exception {
		return mockMvc.perform(post("/api/practice/sessions/" + id + "/plan").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"direction\":\"" + direction + "\",\"stopTicks\":" + stop + ",\"targetTicks\":" + target
				+ ",\"contracts\":" + contracts + "}"));
	}

	private JsonNode skip(String id) throws Exception {
		return read(mockMvc.perform(post("/api/practice/sessions/" + id + "/skip").cookie(alice).with(csrf()))
			.andExpect(status().isOk())).get("state");
	}

	/** What the database recorded for a session's first trade, in real time (the client sees a hidden date). */
	private record Stored(String outcome, Instant entryTime, long entry, Instant exitTime, long exit, long pnl, long net,
			long commission, long mae, long mfe, boolean gap, String resolution, long reference, Instant cursor) {
	}

	private Stored stored(String id, int seq) {
		return jdbc.queryForObject("""
			SELECT r.outcome, r.entry_time, r.entry_ticks, r.exit_time, r.exit_ticks, r.pnl_ticks, r.net_pnl_cents,
			       r.commission_cents, r.mae_ticks, r.mfe_ticks, r.gap_exit, r.resolution, p.reference_ticks,
			       p.submitted_cursor
			FROM practice_result r JOIN trade_plan p ON p.session_id = r.session_id AND p.seq = r.seq
			WHERE r.session_id = ?::uuid AND r.seq = ?""",
			(rs, i) -> new Stored(rs.getString(1), rs.getTimestamp(2).toInstant(), rs.getLong(3),
				rs.getTimestamp(4).toInstant(), rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8),
				rs.getLong(9), rs.getLong(10), rs.getBoolean(11), rs.getString(12), rs.getLong(13),
				rs.getTimestamp(14).toInstant()),
			id, seq);
	}

	// ---------------------------------------------------------------------------------------------
	// Worked examples
	// ---------------------------------------------------------------------------------------------

	/** A minute: open, high, low, close. */
	private static long[] m(long open, long high, long low, long close) {
		return new long[] { open, high, low, close };
	}

	/**
	 * A session standing at {@code moment}, the market at it written by hand: {@code before} is the
	 * minute just before (its close is the price the plan is made at), {@code after} the minutes from
	 * the moment on. Everything else is the background market.
	 */
	private String standingAt(Instant moment, long[] before, long[]... after) throws Exception {
		TreeMap<Instant, Bar> bars = market();
		bars.put(moment.minusSeconds(60), bar(moment.minusSeconds(60), before));
		for (int k = 0; k < after.length; k++) {
			Instant t = moment.plusSeconds(60L * k);
			bars.put(t, bar(t, after[k]));
		}
		store(bars);
		return sessionAt(moment);
	}

	/** A new session whose replay starts at {@code moment}, as if the random pick had landed there. */
	private String sessionAt(Instant moment) throws Exception {
		String id = newSession();
		jdbc.update("UPDATE practice_session SET history_from = ?, start_time = ?, cursor_time = ? WHERE id = ?::uuid",
			Timestamp.from(moment.minusSeconds(4 * 86_400)), Timestamp.from(moment), Timestamp.from(moment), id);
		return id;
	}

	/**
	 * A seeded moment of open market in the second week, with half an hour left before the close. Seeded
	 * so the sample is the same every run (BUG-062): which rules a random sample exercises must not be
	 * left to chance when the test checks that it exercised them.
	 */
	private static Instant moment(Random random) {
		while (true) {
			Instant t = DATA_FROM.plusSeconds(5 * 86_400L + 60L * random.nextInt(6 * 24 * 60));
			if (SessionCalendar.isOpen(t) && SessionCalendar.isOpen(t.minusSeconds(60))
					&& nextClose(t).getEpochSecond() - t.getEpochSecond() >= 1_800) {
				return t;
			}
		}
	}

	private static Bar bar(Instant t, long[] ohlc) {
		return new Bar(t, ohlc[0], ohlc[1], ohlc[2], ohlc[3], 10);
	}

	/** Tuesday 10 June 2025, 10:00 in Chicago: the market is open, mid-session. */
	private static final Instant TUESDAY_10AM = Instant.parse("2025-06-10T15:00:00Z");

	private static JsonNode result(JsonNode state) {
		return state.at("/trades/0/result");
	}

	@Nested
	@DisplayName("a long")
	class Longs {

		@Test
		@DisplayName("reaching its target is closed at the target: +8 ticks, $37.50 net, 0.75R")
		void target() throws Exception {
			String id = standingAt(TUESDAY_10AM, m(80_000, 80_001, 79_999, 80_000),
				m(80_000, 80_004, 79_998, 80_002),
				m(80_002, 80_010, 80_001, 80_008));
			plan(id, "LONG", 79_990, 80_008, 1).andExpect(status().isOk());
			JsonNode result = result(skip(id));

			assertThat(result.get("outcome").asString()).isEqualTo("TARGET");
			assertThat(result.get("entryTicks").asLong()).isEqualTo(80_000);
			assertThat(result.get("exitTicks").asLong()).as("a limit fills at its own price").isEqualTo(80_008);
			assertThat(result.get("pnlTicks").asLong()).isEqualTo(8);
			assertThat(result.get("netPnlCents").asLong()).isEqualTo(8 * TICK_CENTS - COMMISSION_CENTS);
			assertThat(result.get("commissionCents").asLong()).isEqualTo(COMMISSION_CENTS);
			assertThat(result.get("rMultiple").asDouble()).isEqualTo(0.75);
			assertThat(result.get("maeTicks").asLong()).isEqualTo(2);
			assertThat(result.get("mfeTicks").asLong()).isEqualTo(8);
			assertThat(result.get("gapExit").asBoolean()).isFalse();
			assertThat(result.has("assumption")).isFalse();
			Stored db = stored(id, 1);
			assertThat(db.exitTime()).isEqualTo(TUESDAY_10AM.plusSeconds(60));
			assertThat(db.resolution()).isEqualTo("M1");
		}

		@Test
		@DisplayName("reaching its stop is closed a tick beyond it: −11 ticks, −$57.50, −1.15R")
		void stop() throws Exception {
			String id = standingAt(TUESDAY_10AM, m(80_000, 80_001, 79_999, 80_000),
				m(80_000, 80_003, 79_995, 79_996),
				m(79_996, 79_997, 79_990, 79_991)); // the low touches the stop exactly
			plan(id, "LONG", 79_990, 80_020, 1).andExpect(status().isOk());
			JsonNode result = result(skip(id));

			assertThat(result.get("outcome").asString()).isEqualTo("STOP");
			assertThat(result.get("exitTicks").asLong()).as("a stop slips a tick").isEqualTo(79_989);
			assertThat(result.get("pnlTicks").asLong()).isEqualTo(-11);
			assertThat(result.get("netPnlCents").asLong()).isEqualTo(-11 * TICK_CENTS - COMMISSION_CENTS);
			assertThat(result.get("rMultiple").asDouble()).isEqualTo(-1.15);
			assertThat(result.get("maeTicks").asLong()).isEqualTo(11);
			assertThat(result.get("mfeTicks").asLong()).isEqualTo(3);
		}

		@Test
		@DisplayName("is not filled a tick short of a level: price must reach it")
		void aTickShort() throws Exception {
			String id = standingAt(TUESDAY_10AM, m(80_000, 80_001, 79_999, 80_000),
				m(80_000, 80_009, 79_991, 80_005), // a tick short of both
				m(80_005, 80_006, 79_990, 79_992)); // then the stop
			plan(id, "LONG", 79_990, 80_010, 1).andExpect(status().isOk());
			JsonNode result = result(skip(id));
			assertThat(result.get("outcome").asString()).isEqualTo("STOP");
			assertThat(stored(id, 1).exitTime()).isEqualTo(TUESDAY_10AM.plusSeconds(60));
			assertThat(result.get("mfeTicks").asLong()).as("the near miss still counts as excursion").isEqualTo(9);
		}

		@Test
		@DisplayName("whose target the market opens beyond fills at that open — better than the target")
		void gapThroughTarget() throws Exception {
			String id = standingAt(TUESDAY_10AM, m(80_000, 80_001, 79_999, 80_000),
				m(80_000, 80_006, 79_999, 80_005),
				m(80_014, 80_016, 80_012, 80_015));
			plan(id, "LONG", 79_990, 80_010, 1).andExpect(status().isOk());
			JsonNode result = result(skip(id));
			assertThat(result.get("outcome").asString()).isEqualTo("TARGET");
			assertThat(result.get("exitTicks").asLong()).isEqualTo(80_014);
			assertThat(result.get("pnlTicks").asLong()).isEqualTo(14);
			assertThat(result.get("gapExit").asBoolean()).isTrue();
		}

		@Test
		@DisplayName("is entered at the next price, and its R measured from the price it was planned at")
		void entryIsTheNextPrice() throws Exception {
			// The last close is 80,000; the next minute opens three ticks higher.
			String id = standingAt(TUESDAY_10AM, m(80_000, 80_001, 79_999, 80_000),
				m(80_003, 80_005, 80_002, 80_004),
				m(80_004, 80_011, 80_003, 80_010));
			plan(id, "LONG", 79_990, 80_010, 3).andExpect(status().isOk());
			JsonNode state = skip(id);
			JsonNode result = result(state);
			assertThat(result.get("entryTicks").asLong()).isEqualTo(80_003);
			assertThat(stored(id, 1).reference()).isEqualTo(80_000);
			assertThat(result.get("pnlTicks").asLong()).isEqualTo(7);
			// Three contracts: ticks × $5 × 3, less $2.50 × 3.
			assertThat(result.get("netPnlCents").asLong()).isEqualTo(7 * TICK_CENTS * 3 - COMMISSION_CENTS * 3);
			assertThat(result.get("commissionCents").asLong()).isEqualTo(COMMISSION_CENTS * 3);
			// R is about the decision: 7 ticks against the 10 the plan risked from 80,000, less the commission.
			assertThat(result.get("rMultiple").asDouble()).isEqualTo(0.65);
			assertThat(state.at("/score/totalR").asDouble()).isEqualTo(0.65);
			assertThat(state.at("/score/wins").asInt()).isEqualTo(1);
		}
	}

	@Nested
	@DisplayName("a short")
	class Shorts {

		@Test
		@DisplayName("reaching its target below is closed at the target: +10 ticks, 0.95R")
		void target() throws Exception {
			String id = standingAt(TUESDAY_10AM, m(80_000, 80_001, 79_999, 80_000),
				m(80_000, 80_002, 79_995, 79_996),
				m(79_996, 79_997, 79_989, 79_990));
			plan(id, "SHORT", 80_010, 79_990, 1).andExpect(status().isOk());
			JsonNode result = result(skip(id));
			assertThat(result.get("outcome").asString()).isEqualTo("TARGET");
			assertThat(result.get("exitTicks").asLong()).isEqualTo(79_990);
			assertThat(result.get("pnlTicks").asLong()).as("a short gains as price falls").isEqualTo(10);
			assertThat(result.get("rMultiple").asDouble()).isEqualTo(0.95);
			assertThat(result.get("maeTicks").asLong()).isEqualTo(2);
			assertThat(result.get("mfeTicks").asLong()).isEqualTo(10);
		}

		@Test
		@DisplayName("reaching its stop above is closed a tick beyond it")
		void stop() throws Exception {
			String id = standingAt(TUESDAY_10AM, m(80_000, 80_001, 79_999, 80_000),
				m(80_000, 80_010, 79_997, 80_008));
			plan(id, "SHORT", 80_010, 79_980, 2).andExpect(status().isOk());
			JsonNode result = result(skip(id));
			assertThat(result.get("outcome").asString()).isEqualTo("STOP");
			assertThat(result.get("exitTicks").asLong()).isEqualTo(80_011);
			assertThat(result.get("pnlTicks").asLong()).isEqualTo(-11);
			assertThat(result.get("netPnlCents").asLong()).isEqualTo(-11 * TICK_CENTS * 2 - COMMISSION_CENTS * 2);
			assertThat(result.get("rMultiple").asDouble()).isEqualTo(-1.15);
		}

		@Test
		@DisplayName("whose stop the market opens beyond fills at that open, plus the slippage — worse than the stop")
		void gapThroughStop() throws Exception {
			String id = standingAt(TUESDAY_10AM, m(80_000, 80_001, 79_999, 80_000),
				m(80_000, 80_005, 79_999, 80_004),
				m(80_015, 80_018, 80_012, 80_016));
			plan(id, "SHORT", 80_010, 79_980, 1).andExpect(status().isOk());
			JsonNode result = result(skip(id));
			assertThat(result.get("outcome").asString()).isEqualTo("STOP");
			assertThat(result.get("exitTicks").asLong()).isEqualTo(80_016);
			assertThat(result.get("pnlTicks").asLong()).isEqualTo(-16);
			assertThat(result.get("gapExit").asBoolean()).isTrue();
			assertThat(result.get("rMultiple").asDouble()).as("more than the 1R planned").isEqualTo(-1.65);
		}
	}

	@Test
	@DisplayName("stop and target inside one minute: ambiguous, the stop assumed, and said so")
	void ambiguous() throws Exception {
		String id = standingAt(TUESDAY_10AM, m(80_000, 80_001, 79_999, 80_000),
			m(80_000, 80_006, 79_994, 80_001));
		plan(id, "LONG", 79_995, 80_005, 1).andExpect(status().isOk());
		JsonNode result = result(skip(id));
		assertThat(result.get("outcome").asString()).isEqualTo("AMBIGUOUS");
		assertThat(result.get("exitTicks").asLong()).as("the worst case: the stop, slipped").isEqualTo(79_994);
		assertThat(result.get("pnlTicks").asLong()).isEqualTo(-6);
		assertThat(result.get("assumption").asString()).contains("same minute").contains("assumes the worst case");
	}

	@Test
	@DisplayName("neither level by the session close: flat at the last close before it")
	void sessionClose() throws Exception {
		// 15:30 in Chicago: half an hour before the 16:00 close. A wide bracket survives it.
		Instant moment = Instant.parse("2025-06-10T20:30:00Z");
		long[][] quiet = new long[30][];
		for (int k = 0; k < 30; k++) {
			quiet[k] = m(80_000 + k, 80_001 + k, 79_999 + k, 80_001 + k);
		}
		String id = standingAt(moment, m(80_000, 80_001, 79_999, 80_000), quiet);
		plan(id, "LONG", 79_900, 80_100, 1).andExpect(status().isOk());
		JsonNode result = result(skip(id));
		assertThat(result.get("outcome").asString()).isEqualTo("TIME_EXIT");
		assertThat(result.get("exitTicks").asLong()).as("the 15:59 minute's close").isEqualTo(80_030);
		assertThat(stored(id, 1).exitTime()).isEqualTo(Instant.parse("2025-06-10T20:59:00Z"));
		assertThat(result.get("pnlTicks").asLong()).isEqualTo(30);
	}

	@Test
	@DisplayName("T27, the standard costs: in a tick dear, a target touched but not traded through left alone, $4.50 a round trip")
	void standardCosts() throws Exception {
		mockMvc.perform(put("/api/practice/settings").cookie(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{}")).andExpect(status().isOk());
		String id = standingAt(TUESDAY_10AM, m(80_000, 80_001, 79_999, 80_000),
			m(80_000, 80_008, 79_999, 80_007), // touches the target, no more
			m(80_007, 80_009, 80_005, 80_008)); // trades a tick through it
		plan(id, "LONG", 79_990, 80_008, 1).andExpect(status().isOk());
		JsonNode result = result(skip(id));

		assertThat(result.get("fillModelVersion").asString()).isEqualTo("mkt-1t/slip-1t/thru-1t/comm-450c");
		assertThat(result.get("engineVersion").asString()).isEqualTo("fill-1.4.0");
		assertThat(result.get("outcome").asString()).isEqualTo("TARGET");
		assertThat(result.get("entryTicks").asLong()).as("a market order a tick dear").isEqualTo(80_001);
		assertThat(result.get("exitTicks").asLong()).as("the limit's own price, once traded through").isEqualTo(80_008);
		assertThat(stored(id, 1).exitTime()).as("the second minute, not the touch").isEqualTo(TUESDAY_10AM.plusSeconds(60));
		assertThat(result.get("pnlTicks").asLong()).isEqualTo(7);
		assertThat(result.get("commissionCents").asLong()).isEqualTo(450);
		assertThat(result.get("netPnlCents").asLong()).isEqualTo(7 * TICK_CENTS - 450);
		assertThat(result.get("rMultiple").asDouble()).isEqualTo(0.61);
		assertThat(result.get("maeTicks").asLong()).isEqualTo(2);
		assertThat(result.get("mfeTicks").asLong()).isEqualTo(7);
	}

	@Test
	@DisplayName("T27: a close is filled with the plan's own costs, though the learner changed theirs since")
	void costsKeptWithThePlan() throws Exception {
		mockMvc.perform(put("/api/practice/settings").cookie(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"feesCents\":300,\"marketSlippageTicks\":2,\"stopSlippageTicks\":3,\"limitThroughTicks\":0}"))
			.andExpect(status().isOk());
		String id = standingAt(TUESDAY_10AM, m(80_000, 80_001, 79_999, 80_000),
			m(80_000, 80_003, 79_998, 80_002),
			m(80_002, 80_004, 80_000, 80_001));
		plan(id, "LONG", 79_950, 80_050, 1).andExpect(status().isOk());
		mockMvc.perform(put("/api/practice/settings").cookie(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{}")).andExpect(status().isOk());
		mockMvc.perform(post("/api/practice/sessions/" + id + "/advance").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"bars\":1,\"timeframe\":\"1m\"}")).andExpect(status().isOk());
		JsonNode result = result(read(mockMvc.perform(post("/api/practice/sessions/" + id + "/close?timeframe=1m")
			.cookie(alice).with(csrf())).andExpect(status().isOk())).get("state"));

		assertThat(result.get("outcome").asString()).isEqualTo("CLOSED");
		assertThat(result.get("fillModelVersion").asString()).isEqualTo("mkt-2t/slip-3t/comm-300c");
		assertThat(result.get("entryTicks").asLong()).as("in two ticks dear").isEqualTo(80_002);
		assertThat(result.get("exitTicks").asLong()).as("out two ticks cheap, at the next open").isEqualTo(80_000);
		assertThat(result.get("pnlTicks").asLong()).isEqualTo(-2);
		assertThat(result.get("commissionCents").asLong()).isEqualTo(300);
		assertThat(result.get("netPnlCents").asLong()).isEqualTo(-2 * TICK_CENTS - 300);
		assertThat(result.get("rMultiple").asDouble()).as("−$13.00 on a $250 risk").isEqualTo(-0.05);
	}

	@Test
	@DisplayName("closed at market a minute in: out at that minute's open, the planned exit never reached")
	void closedAtMarket() throws Exception {
		String id = standingAt(TUESDAY_10AM, m(80_000, 80_001, 79_999, 80_000),
			m(80_000, 80_003, 79_998, 80_002),
			m(80_002, 80_004, 80_000, 80_001));
		plan(id, "LONG", 79_950, 80_050, 1).andExpect(status().isOk());
		mockMvc.perform(post("/api/practice/sessions/" + id + "/advance").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"bars\":1,\"timeframe\":\"1m\"}")).andExpect(status().isOk());
		JsonNode result = result(read(mockMvc.perform(post("/api/practice/sessions/" + id + "/close?timeframe=1m")
			.cookie(alice).with(csrf())).andExpect(status().isOk())).get("state"));
		assertThat(result.get("outcome").asString()).isEqualTo("CLOSED");
		assertThat(result.get("exitTicks").asLong()).isEqualTo(80_002);
		assertThat(result.get("pnlTicks").asLong()).isEqualTo(2);
		assertThat(result.get("netPnlCents").asLong()).isEqualTo(2 * TICK_CENTS - COMMISSION_CENTS);
		assertThat(result.get("rMultiple").asDouble()).as("$7.50 on a $250 risk").isEqualTo(0.03);
	}

	@Nested
	@DisplayName("a partial (T28, DEC-074)")
	class Partials {

		/**
		 * Long two from 80,000: stop 79,990, one off at 80,010, the other to 80,020. The second minute reaches the
		 * partial; the fourth comes back a tick under the entry, and the fifth falls through the stop.
		 */
		private String upThenDown() throws Exception {
			return standingAt(TUESDAY_10AM, m(80_000, 80_001, 79_999, 80_000),
				m(80_000, 80_004, 79_998, 80_003),
				m(80_003, 80_011, 80_002, 80_008),
				m(80_008, 80_009, 80_004, 80_005),
				m(80_005, 80_006, 79_999, 80_000),
				m(80_000, 80_001, 79_985, 79_986));
		}

		private ResultActions planWithPartial(String id, String partial) throws Exception {
			return mockMvc.perform(post("/api/practice/sessions/" + id + "/plan").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"direction\":\"LONG\",\"stopTicks\":79990,\"targetTicks\":80020,\"contracts\":2" + partial + "}"));
		}

		private static String line(JsonNode lines, String id) {
			for (JsonNode line : lines) {
				if (line.get("id").asString().equals(id)) {
					return line.get("text").asString();
				}
			}
			return null;
		}

		@Test
		@DisplayName("one off at +1R, the stop on the other then at the entry: +10 and −1 ticks, $40.00 net, 0.40R")
		void breakeven() throws Exception {
			String id = upThenDown();
			planWithPartial(id, ",\"partialTicks\":80010,\"partialContracts\":1,\"breakevenAfterPartial\":true")
				.andExpect(status().isOk());
			JsonNode state = skip(id);
			JsonNode result = result(state);

			assertThat(result.get("outcome").asString()).isEqualTo("STOP");
			assertThat(result.get("exitTicks").asLong()).as("the entry, slipped a tick").isEqualTo(79_999);
			assertThat(result.get("pnlTicks").asLong()).isEqualTo(-1);
			assertThat(result.at("/partial/ticks").asLong()).isEqualTo(80_010);
			assertThat(result.at("/partial/contracts").asInt()).isEqualTo(1);
			assertThat(result.at("/partial/pnlTicks").asLong()).isEqualTo(10);
			assertThat(result.at("/partial/gap").asBoolean()).isFalse();
			assertThat(result.get("netPnlCents").asLong()).isEqualTo((10 - 1) * TICK_CENTS - 2 * COMMISSION_CENTS);
			assertThat(result.get("rMultiple").asDouble()).isEqualTo(0.4);
			assertThat(state.at("/score/totalR").asDouble()).isEqualTo(0.4);
			assertThat(state.at("/score/wins").asInt()).isEqualTo(1);
			assertThat(stored(id, 1).exitTime()).isEqualTo(TUESDAY_10AM.plusSeconds(180));

			JsonNode plan = state.at("/trades/0/plan");
			assertThat(plan.get("partialTicks").asLong()).isEqualTo(80_010);
			assertThat(plan.get("partialContracts").asInt()).isEqualTo(1);
			assertThat(plan.get("breakevenAfterPartial").asBoolean()).isTrue();
			assertThat(plan.get("plannedRewardRisk").asDouble()).as("one to +1R, one to +2R").isEqualTo(1.5);

			JsonNode card = state.at("/trades/0/scorecard");
			assertThat(line(card.get("outcome"), "partial"))
				.isEqualTo("1 of 2 contracts taken off at 20002.50: +1.00R on each; the stop on the rest then moved to the entry.");
			assertThat(line(card.get("outcome"), "costs"))
				.isEqualTo("Fees $5.00 and slippage of 1 tick across the contracts ($5.00): $10.00, 0.10R of what was risked.");
			assertThat(line(card.get("process"), "execution"))
				.isEqualTo("Held to the plan: the stop, moved to the entry after the partial, closed the rest.");
			assertThat(line(card.get("process"), "reward"))
				.isEqualTo("Planned 1.50 : 1, the partial's contracts to their level and the rest to the target.");

			JsonNode review = read(mockMvc.perform(get("/api/practice/sessions/" + id + "/trades/1/review").cookie(alice))
				.andExpect(status().isOk()));
			JsonNode partial = null;
			for (JsonNode moment : review.get("moments")) {
				if (moment.get("kind").asString().equals("PARTIAL")) {
					partial = moment;
				}
			}
			assertThat(partial).isNotNull();
			assertThat(partial.get("title").asString()).isEqualTo("1 of 2 taken off at 20002.50: +1.00R on each.");
			assertThat(partial.get("lines").get(0).asString())
				.isEqualTo("The stop on the last contract moved to the entry, 20000.00, once that candle had closed.");
			JsonNode decision = null;
			for (JsonNode moment : review.get("moments")) {
				if (moment.get("kind").asString().equals("DECISION")) {
					decision = moment;
				}
			}
			assertThat(decision.get("lines").get(1).asString())
				.isEqualTo("Take 1 off at 20002.50, then move the stop on the rest to the entry.");
		}

		@Test
		@DisplayName("without the move the other runs on, and a partial never reached is said so")
		void heldAndUnreached() throws Exception {
			String id = upThenDown();
			planWithPartial(id, ",\"partialTicks\":80010,\"partialContracts\":1").andExpect(status().isOk());
			JsonNode held = result(skip(id));
			assertThat(held.get("outcome").asString()).isEqualTo("STOP");
			assertThat(stored(id, 1).exitTime()).as("on past the entry, to the stop").isEqualTo(TUESDAY_10AM.plusSeconds(240));
			assertThat(held.get("rMultiple").asDouble()).as("+10 and −11 ticks over two contracts, $5.00 of fees").isEqualTo(-0.1);

			String far = upThenDown();
			planWithPartial(far, ",\"partialTicks\":80015,\"partialContracts\":1").andExpect(status().isOk());
			JsonNode state = skip(far);
			assertThat(result(state).has("partial")).isFalse();
			assertThat(line(state.at("/trades/0/scorecard/outcome"), "partial"))
				.isEqualTo("Your partial at 20003.75 was not reached before the exit.");
		}

		@Test
		@DisplayName("a minute reaching it and the stop is ambiguous: the stop for both, and the note names the level")
		void ambiguous() throws Exception {
			String id = standingAt(TUESDAY_10AM, m(80_000, 80_001, 79_999, 80_000),
				m(80_000, 80_004, 79_998, 80_003),
				m(80_003, 80_011, 79_989, 79_995));
			planWithPartial(id, ",\"partialTicks\":80010,\"partialContracts\":1,\"breakevenAfterPartial\":true")
				.andExpect(status().isOk());
			JsonNode result = result(skip(id));
			assertThat(result.get("outcome").asString()).isEqualTo("AMBIGUOUS");
			assertThat(result.has("partial")).isFalse();
			assertThat(result.get("assumption").asString()).contains("the price to take part off at")
				.endsWith("the stop, for every contract.");
			assertThat(result.get("rMultiple").asDouble()).as("−11 ticks on each of two, and the fees").isEqualTo(-1.15);
		}

		@Test
		@DisplayName("is shown once the replay passes it, before the exit; a close then takes the rest")
		void revealedThenClosed() throws Exception {
			String id = upThenDown();
			planWithPartial(id, ",\"partialTicks\":80010,\"partialContracts\":1,\"breakevenAfterPartial\":true")
				.andExpect(status().isOk());
			JsonNode first = read(mockMvc.perform(post("/api/practice/sessions/" + id + "/advance").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"bars\":1,\"timeframe\":\"1m\"}"))
				.andExpect(status().isOk())).get("state");
			assertThat(first.at("/trades/0").has("partial")).as("its minute not yet closed").isFalse();
			JsonNode second = read(mockMvc.perform(post("/api/practice/sessions/" + id + "/advance").cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"bars\":1,\"timeframe\":\"1m\"}"))
				.andExpect(status().isOk())).get("state");
			assertThat(second.at("/trades/0/partial/ticks").asLong()).isEqualTo(80_010);
			assertThat(second.at("/trades/0").has("result")).isFalse();

			JsonNode result = result(read(mockMvc.perform(post("/api/practice/sessions/" + id + "/close?timeframe=1m")
				.cookie(alice).with(csrf())).andExpect(status().isOk())).get("state"));
			assertThat(result.get("outcome").asString()).isEqualTo("CLOSED");
			assertThat(result.get("exitTicks").asLong()).isEqualTo(80_008);
			assertThat(result.at("/partial/ticks").asLong()).isEqualTo(80_010);
			assertThat(result.get("netPnlCents").asLong()).isEqualTo((10 + 8) * TICK_CENTS - 2 * COMMISSION_CENTS);
			assertThat(result.get("rMultiple").asDouble()).isEqualTo(0.85);
		}

		@Test
		@DisplayName("some of the contracts, at a price between the price and the target, or it is refused")
		void refused() throws Exception {
			String id = upThenDown();
			planWithPartial(id, ",\"partialTicks\":80010").andExpect(status().isBadRequest());
			assertThat(read(planWithPartial(id, ",\"partialTicks\":80010,\"partialContracts\":2")
				.andExpect(status().isBadRequest())).get("message").asString()).contains("fewer than all 2");
			planWithPartial(id, ",\"partialTicks\":80020,\"partialContracts\":1").andExpect(status().isBadRequest());
			assertThat(read(planWithPartial(id, ",\"partialTicks\":80000,\"partialContracts\":1")
				.andExpect(status().isBadRequest())).get("message").asString()).contains("between the current price");
			planWithPartial(id, ",\"partialContracts\":1").andExpect(status().isBadRequest());
			planWithPartial(id, ",\"breakevenAfterPartial\":true").andExpect(status().isBadRequest());
			planWithPartial(id, ",\"partialTicks\":80019,\"partialContracts\":1").andExpect(status().isOk());
		}
	}

	// ---------------------------------------------------------------------------------------------
	// An independent replay
	// ---------------------------------------------------------------------------------------------

	/** fill-1.2.0, written a second time and as plainly as possible, to check the first against. */
	private record Expected(String outcome, Instant entryTime, long entry, Instant exitTime, long exit, long mae,
			long mfe, boolean gap) {
	}

	private static Expected replay(boolean longTrade, long stop, long target, List<Bar> bars) {
		int sign = longTrade ? 1 : -1;
		long entry = bars.getFirst().openTicks();
		Instant entryTime = bars.getFirst().openTime();
		long mae = 0;
		long mfe = 0;
		for (Bar b : bars) {
			long o = b.openTicks();
			boolean stopAtOpen = longTrade ? o <= stop : o >= stop;
			boolean targetAtOpen = longTrade ? o >= target : o <= target;
			if (stopAtOpen) {
				long exit = o - sign;
				return new Expected("STOP", entryTime, entry, b.openTime(), exit, Math.max(mae, sign * (entry - exit)), mfe, o != stop);
			}
			if (targetAtOpen) {
				return new Expected("TARGET", entryTime, entry, b.openTime(), o, mae, Math.max(mfe, sign * (o - entry)), o != target);
			}
			boolean stopHit = longTrade ? b.lowTicks() <= stop : b.highTicks() >= stop;
			boolean targetHit = longTrade ? b.highTicks() >= target : b.lowTicks() <= target;
			if (stopHit) {
				long exit = stop - sign;
				return new Expected(targetHit ? "AMBIGUOUS" : "STOP", entryTime, entry, b.openTime(), exit,
					Math.max(mae, sign * (entry - exit)), targetHit ? Math.max(mfe, sign * (target - entry)) : mfe, false);
			}
			if (targetHit) {
				return new Expected("TARGET", entryTime, entry, b.openTime(), target, mae, Math.max(mfe, sign * (target - entry)), false);
			}
			mae = Math.max(mae, Math.max(0, sign * (entry - (longTrade ? b.lowTicks() : b.highTicks()))));
			mfe = Math.max(mfe, Math.max(0, sign * ((longTrade ? b.highTicks() : b.lowTicks()) - entry)));
		}
		Bar last = bars.getLast();
		return new Expected("TIME_EXIT", entryTime, entry, last.openTime(), last.closeTicks(), mae, mfe, false);
	}

	/** Where a trade entered at {@code from} must end: CME's next 16:00 in Chicago. */
	private static Instant nextClose(Instant from) {
		ZonedDateTime t = from.atZone(CHICAGO).withHour(16).withMinute(0).withSecond(0).withNano(0);
		if (!t.toInstant().isAfter(from)) {
			t = t.plusDays(1);
		}
		while (t.getDayOfWeek() == DayOfWeek.SATURDAY || t.getDayOfWeek() == DayOfWeek.SUNDAY) {
			t = t.plusDays(1);
		}
		return t.toInstant();
	}

	@Test
	@DisplayName("every one of 40 random brackets agrees with an independent replay of the stored minutes")
	void randomBrackets() throws Exception {
		TreeMap<Instant, Bar> market = market();
		store(market);
		Random random = new Random(42);
		Map<String, Integer> seen = new TreeMap<>();
		int gaps = 0;
		int shorts = 0;
		for (int i = 0; i < 40; i++) {
			String id = sessionAt(moment(random));
			boolean longTrade = random.nextBoolean();
			long reference = reference(id);
			long risk = 1 + random.nextInt(i % 2 == 0 ? 6 : 60);
			long reward = 1 + random.nextInt(i % 2 == 0 ? 6 : 120);
			long stop = longTrade ? reference - risk : reference + risk;
			long target = longTrade ? reference + reward : reference - reward;
			int contracts = 1 + random.nextInt(3);
			plan(id, longTrade ? "LONG" : "SHORT", stop, target, contracts).andExpect(status().isOk());
			JsonNode result = result(skip(id));
			Stored db = stored(id, 1);

			List<Bar> after = new ArrayList<>(market.subMap(db.cursor(), true, nextClose(db.cursor()), false).values());
			Expected expected = replay(longTrade, stop, target, after);
			long pnl = (longTrade ? 1 : -1) * (expected.exit() - expected.entry());
			String what = "bracket " + i + (longTrade ? " long " : " short ") + stop + "/" + target + " at " + db.cursor();
			assertThat(db.outcome()).as(what).isEqualTo(expected.outcome());
			assertThat(db.reference()).as(what).isEqualTo(reference);
			assertThat(db.entryTime()).as(what).isEqualTo(expected.entryTime());
			assertThat(db.entry()).as(what).isEqualTo(expected.entry());
			assertThat(db.exitTime()).as(what).isEqualTo(expected.exitTime());
			assertThat(db.exit()).as(what).isEqualTo(expected.exit());
			assertThat(db.pnl()).as(what).isEqualTo(pnl);
			assertThat(db.net()).as(what).isEqualTo(pnl * TICK_CENTS * contracts - COMMISSION_CENTS * contracts);
			assertThat(db.mae()).as(what).isEqualTo(expected.mae());
			assertThat(db.mfe()).as(what).isEqualTo(expected.mfe());
			assertThat(db.gap()).as(what).isEqualTo(expected.gap());
			// What the learner is shown agrees with what was stored.
			assertThat(result.get("outcome").asString()).isEqualTo(db.outcome());
			assertThat(result.get("netPnlCents").asLong()).isEqualTo(db.net());
			assertThat(result.get("rMultiple").asDouble())
				.isEqualTo(Math.round(100.0 * db.net() / (risk * contracts * TICK_CENTS)) / 100.0);
			assertThat(result.has("assumption")).isEqualTo("AMBIGUOUS".equals(db.outcome()));
			// Ended, or the next session would have to start clear of this one's replay (DEC-077) — on two weeks of
			// minutes there is not always such a moment.
			mockMvc.perform(post("/api/practice/sessions/" + id + "/end").cookie(alice).with(csrf()))
				.andExpect(status().isOk());
			seen.merge(db.outcome(), 1, Integer::sum);
			gaps += db.gap() ? 1 : 0;
			shorts += longTrade ? 0 : 1;
		}
		// The sample has to have exercised the rules, or agreeing proves little.
		assertThat(seen).containsKeys("TARGET", "STOP", "AMBIGUOUS");
		assertThat(gaps).as("exits at a gapped open").isPositive();
		assertThat(shorts).isBetween(5, 35);
	}

	/** The price a plan in this session is made at: the last minute's close before the cursor. */
	private long reference(String id) {
		Instant cursor = jdbc.queryForObject("SELECT cursor_time FROM practice_session WHERE id = ?::uuid",
			Timestamp.class, id).toInstant();
		return jdbc.queryForObject("""
			SELECT close_ticks FROM market_bar WHERE instrument_id = ? AND resolution = '1m'
			  AND open_time < ? AND open_time >= ? ORDER BY open_time DESC LIMIT 1""",
			Long.class, nqId, Timestamp.from(cursor), Timestamp.from(cursor.minusSeconds(3_600)));
	}
}
