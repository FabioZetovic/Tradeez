package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;

import jakarta.servlet.http.Cookie;

/**
 * The practice API (task 6).
 *
 * <p>Most of these tests are about what the API must <b>not</b> say. A practice session is only
 * worth anything if the learner cannot see the future, so the assertions that matter are: no bar at
 * or past the cursor, no seed before the end, no result before the replay reaches it, and no way to
 * change a plan or a result once written.
 */
class PracticeApiTest extends AbstractIntegrationTest {

	private static final String PASSWORD = "a-perfectly-fine-passphrase";
	private static final long FIVE_MINUTES = 300;

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
	PracticeSessionRepository sessions;

	@Autowired
	MarketDataService marketData;

	private Cookie alice;
	private Cookie bob;


	/** The costs the worked examples are worked in: FillModel.BASIC's, set as the learner's own (T27). */
	static final String BASIC_COSTS =
		"{\"feesCents\":250,\"marketSlippageTicks\":0,\"stopSlippageTicks\":1,\"limitThroughTicks\":0}";

	@BeforeEach
	void signInTwoUsers() throws Exception {
		// Sessions, plans and results cascade from the user.
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		alice = register("alice@example.com");
		bob = register("bob@example.com");
		// The worked examples below are in fill-1.2.0's numbers; the standard costs have tests of their own.
		mockMvc.perform(put("/api/practice/settings").cookie(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(BASIC_COSTS)).andExpect(status().isOk());
	}

	// --- helpers ---------------------------------------------------------------------------------

	private Cookie register(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
					+ "\",\"displayName\":\"T\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
	}

	private JsonNode read(ResultActions actions) throws Exception {
		return json.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private JsonNode create(Cookie who) throws Exception {
		return read(mockMvc.perform(post("/api/practice/sessions").cookie(who).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"instrument\":\"DEMO-NQ\",\"timeframe\":\"5m\"}"))
			.andExpect(status().isCreated()));
	}

	private ResultActions plan(Cookie who, String id, String direction, long stop, long target)
			throws Exception {
		return mockMvc.perform(post("/api/practice/sessions/" + id + "/plan").cookie(who).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"direction\":\"%s\",\"stopTicks\":%d,\"targetTicks\":%d,\"contracts\":1}"
				.formatted(direction, stop, target)));
	}

	/** A sensible long: 10 points of risk for 20 points of reward. */
	private ResultActions planLong(Cookie who, JsonNode session) throws Exception {
		long reference = lastClose(session.get("bars"));
		return plan(who, session.at("/state/id").asString(), "LONG", reference - 40, reference + 80);
	}

	private ResultActions advance(Cookie who, String id, int bars) throws Exception {
		return mockMvc.perform(post("/api/practice/sessions/" + id + "/advance").cookie(who)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"bars\":" + bars + "}"));
	}

	private ResultActions skip(Cookie who, String id) throws Exception {
		return mockMvc.perform(post("/api/practice/sessions/" + id + "/skip").cookie(who)
			.with(csrf()));
	}

	private ResultActions close(Cookie who, String id) throws Exception {
		return mockMvc.perform(post("/api/practice/sessions/" + id + "/close").cookie(who)
			.with(csrf()));
	}

	private ResultActions end(Cookie who, String id) throws Exception {
		return mockMvc.perform(post("/api/practice/sessions/" + id + "/end").cookie(who)
			.with(csrf()));
	}

	/**
	 * A long with one point of risk and two of reward: settled within seconds, so the replay is still
	 * in open market afterwards and the next trade can follow straight away.
	 */
	private ResultActions tightLong(Cookie who, String id, long reference) throws Exception {
		return plan(who, id, "LONG", reference - 4, reference + 8);
	}

	private ResultActions fetch(Cookie who, String id) throws Exception {
		return mockMvc.perform(get("/api/practice/sessions/" + id).cookie(who));
	}

	/**
	 * Null fields are left out of responses entirely ({@code non_null} inclusion), so "withheld"
	 * reads as absent. Either is acceptable; a value is not.
	 */
	private static boolean absent(JsonNode node, String pointer) {
		JsonNode value = node.at(pointer);
		return value.isMissingNode() || value.isNull();
	}

	private static long lastClose(JsonNode bars) {
		return bars.get(bars.size() - 1).get("close").asLong();
	}

	private static void assertNothingAtOrAfter(JsonNode bars, long cursor) {
		for (JsonNode bar : bars) {
			assertThat(bar.get("time").asLong() + FIVE_MINUTES)
				.as("bar at %d must have closed before the cursor %d", bar.get("time").asLong(), cursor)
				.isLessThanOrEqualTo(cursor);
		}
	}

	// --- tests -----------------------------------------------------------------------------------

	@Test
	@DisplayName("practice requires authentication")
	void requiresAuth() throws Exception {
		mockMvc.perform(get("/api/practice/sessions")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/practice/sessions").with(csrf()))
			.andExpect(status().isUnauthorized());
	}

	@Nested
	@DisplayName("a new session")
	class NewSession {

		@Test
		@DisplayName("shows history that ends exactly at the hidden moment, and nothing after it")
		void noLookahead() throws Exception {
			JsonNode session = create(alice);
			JsonNode bars = session.get("bars");
			long cursor = session.at("/state/cursor").asLong();

			assertThat(bars.size()).as("four days of five-minute history").isGreaterThan(200);
			assertNothingAtOrAfter(bars, cursor);
			// And right up to it: the last visible bar is the one that closed at the cursor.
			assertThat(bars.get(bars.size() - 1).get("time").asLong()).isEqualTo(cursor - FIVE_MINUTES);
			assertThat(session.at("/state/status").asString()).isEqualTo("ANALYSING");
		}

		@Test
		@DisplayName("withholds its seed, which is never the public chart's")
		void seedWithheld() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();

			// DEC-038: the seed would regenerate the whole future of this market.
			assertThat(absent(session, "/state/seed")).isTrue();
			// Not under any key, in any form: the digits of the seed appear nowhere in the response.
			assertThat(session.toString()).doesNotContain(Long.toString(seedOf(id)));

			assertThat(seedOf(id)).isNotEqualTo(MarketDataService.PUBLIC_CHART_SEED);
			assertThat(seedOf(create(alice).at("/state/id").asString()))
				.as("every session is a different market")
				.isNotEqualTo(seedOf(id));
		}

		@Test
		@DisplayName("is labelled as simulated data wherever it appears (DEC-033)")
		void labelled() throws Exception {
			JsonNode session = create(alice);
			assertThat(session.at("/state/dataClass").asString()).isEqualTo("SIMULATED");
			assertThat(session.at("/state/disclaimer").asString()).contains("SIMULATED");
		}

		@Test
		@DisplayName("rejects timeframes practice does not run on, and unknown instruments")
		void validatesInput() throws Exception {
			mockMvc.perform(post("/api/practice/sessions").cookie(alice).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"timeframe\":\"1h\"}"))
				.andExpect(status().isBadRequest());
			mockMvc.perform(post("/api/practice/sessions").cookie(alice).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"instrument\":\"NOPE\"}"))
				.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("defaults to real NQ, and says so plainly when no history has been imported")
		void defaults() throws Exception {
			// No imported bars — made so here, not assumed: the test classes share one database, and the
			// real-data ones leave theirs behind, so this passed only when it ran before them (BUG-060).
			// RealPracticeApiTest covers the default with data.
			jdbc.update("DELETE FROM market_bar");
			jdbc.update("DELETE FROM market_data_import");
			mockMvc.perform(post("/api/practice/sessions").cookie(alice).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
					"No real NQ history has been imported")));
		}

		@Test
		@DisplayName("is refused after too many in an hour, because each one generates days of data")
		void creationIsCapped() throws Exception {
			UUID aliceId = users.findByEmailIgnoreCase("alice@example.com").orElseThrow().getId();
			Instrument nq = marketData.requireInstrument("DEMO-NQ");
			Instant start = Instant.parse("2024-03-12T15:00:00Z");
			List<PracticeSession> many = new ArrayList<>();
			for (int i = 0; i < PracticeService.MAX_SESSIONS_PER_HOUR; i++) {
				many.add(new PracticeSession(aliceId, nq.getId(), "5m", 1000 + i, "test",
					start.minusSeconds(3600), start));
			}
			sessions.saveAll(many);

			mockMvc.perform(post("/api/practice/sessions").cookie(alice).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content("{\"instrument\":\"DEMO-NQ\"}"))
				.andExpect(status().isTooManyRequests());
			// Someone else's quota is their own.
			mockMvc.perform(post("/api/practice/sessions").cookie(bob).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content("{\"instrument\":\"DEMO-NQ\"}"))
				.andExpect(status().isCreated());
		}
	}

	@Nested
	@DisplayName("a plan")
	class Plans {

		@Test
		@DisplayName("with its stop or target on the wrong side of the price is rejected")
		void wrongSides() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			long reference = lastClose(session.get("bars"));

			plan(alice, id, "LONG", reference + 40, reference + 80).andExpect(status().isBadRequest());
			plan(alice, id, "SHORT", reference - 40, reference - 80).andExpect(status().isBadRequest());
			plan(alice, id, "LONG", reference - 40, reference).andExpect(status().isBadRequest());
			plan(alice, id, "LONG", reference - 40, reference + PracticeService.MAX_BRACKET_TICKS + 1)
				.andExpect(status().isBadRequest());

			// None of those locked anything.
			planLong(alice, session).andExpect(status().isOk());
		}

		@Test
		@DisplayName("is locked, and the outcome is not revealed with it")
		void lockedAndHidden() throws Exception {
			JsonNode session = create(alice);
			JsonNode state = read(planLong(alice, session).andExpect(status().isOk()));

			assertThat(state.get("status").asString()).isEqualTo("IN_TRADE");
			assertThat(state.get("trades").size()).isEqualTo(1);
			assertThat(state.at("/trades/0/number").asInt()).isEqualTo(1);
			assertThat(state.at("/trades/0/plan/direction").asString()).isEqualTo("LONG");
			assertThat(state.at("/trades/0/plan/plannedRewardRisk").asDouble()).isEqualTo(2.0);
			assertThat(state.at("/trades/0/plan/lockedAt").asLong())
				.isEqualTo(session.at("/state/cursor").asLong());
			// The result exists in the database already — and must not be in the response.
			assertThat(absent(state, "/trades/0/result")).isTrue();
			assertThat(state.at("/score/closedTrades").asInt()).isZero();
			assertThat(state.at("/score/netPnlCents").asLong()).isZero();
			assertThat(absent(state, "/seed")).isTrue();
			// Even the entry price is one second of the future until the replay shows it.
			assertThat(absent(state, "/trades/0/entry")).isTrue();
		}

		@Test
		@DisplayName("cannot be submitted while another trade is running (DEC-013)")
		void onlyOnce() throws Exception {
			JsonNode session = create(alice);
			planLong(alice, session).andExpect(status().isOk());
			planLong(alice, session).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cannot be changed")));
		}

		@Test
		@DisplayName("cannot be altered even by going around the API: the database refuses")
		void immutableInTheDatabase() throws Exception {
			JsonNode session = create(alice);
			planLong(alice, session).andExpect(status().isOk());
			UUID id = UUID.fromString(session.at("/state/id").asString());

			assertThatThrownBy(() -> jdbc.update(
				"UPDATE trade_plan SET target_ticks = target_ticks + 1 WHERE session_id = ?", id))
				.hasMessageContaining("immutable");
			assertThatThrownBy(() -> jdbc.update(
				"UPDATE practice_result SET outcome = 'TARGET' WHERE session_id = ?", id))
				.hasMessageContaining("immutable");
		}

		@Test
		@DisplayName("is refused while the market is closed")
		void marketClosed() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			Instant start = Instant.ofEpochSecond(session.at("/state/startTime").asLong());
			Instant closed = PracticeService.sessionEnd(start);
			jdbc.update("UPDATE practice_session SET cursor_time = ? WHERE id = ?::uuid",
				java.sql.Timestamp.from(closed), id);

			planLong(alice, session).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("closed")));
		}
	}

	@Nested
	@DisplayName("the replay")
	class Replay {

		@Test
		@DisplayName("reveals bars in order, each after the previous cursor and before the new one")
		void reveals() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			long cursor = session.at("/state/cursor").asLong();

			JsonNode step = read(advance(alice, id, 3).andExpect(status().isOk()));
			JsonNode bars = step.get("bars");
			long next = step.at("/state/cursor").asLong();

			assertThat(bars.size()).isEqualTo(3);
			assertThat(bars.get(0).get("time").asLong()).isGreaterThanOrEqualTo(cursor);
			assertNothingAtOrAfter(bars, next);
			assertThat(next).isEqualTo(bars.get(2).get("time").asLong() + FIVE_MINUTES);
		}

		@Test
		@DisplayName("never rewrites what it has already shown")
		void revealedBarsAreStable() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			JsonNode step = read(advance(alice, id, 5).andExpect(status().isOk()));

			JsonNode resumed = read(fetch(alice, id).andExpect(status().isOk()));
			JsonNode all = resumed.get("bars");
			int history = session.get("bars").size();

			assertThat(all.size()).isEqualTo(history + 5);
			for (int i = 0; i < history; i++) {
				assertThat(all.get(i)).isEqualTo(session.get("bars").get(i));
			}
			for (int i = 0; i < 5; i++) {
				assertThat(all.get(history + i)).isEqualTo(step.get("bars").get(i));
			}
			assertNothingAtOrAfter(all, resumed.at("/state/cursor").asLong());
		}

		@Test
		@DisplayName("steps over a closed market to the next session's first bar")
		void crossesTheBreak() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			Instant closed = PracticeService.sessionEnd(
				Instant.ofEpochSecond(session.at("/state/startTime").asLong()));
			jdbc.update("UPDATE practice_session SET cursor_time = ? WHERE id = ?::uuid",
				java.sql.Timestamp.from(closed), id);

			JsonNode step = read(advance(alice, id, 1).andExpect(status().isOk()));
			long revealed = step.at("/bars/0/time").asLong();
			assertThat(revealed).as("the break is at least an hour").isGreaterThanOrEqualTo(
				closed.getEpochSecond() + 3600);
			assertThat(com.tradeez.marketdata.SessionCalendar.isOpen(revealed)).isTrue();
		}

		@Test
		@DisplayName("stops at its limit")
		void hasAnEnd() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			Instant start = Instant.ofEpochSecond(session.at("/state/startTime").asLong());
			jdbc.update("UPDATE practice_session SET cursor_time = ? WHERE id = ?::uuid",
				java.sql.Timestamp.from(start.plus(PracticeService.MAX_REPLAY)), id);

			advance(alice, id, 1).andExpect(status().isConflict());
		}

		@Test
		@DisplayName("rejects step sizes outside 1..500")
		void stepBounds() throws Exception {
			String id = create(alice).at("/state/id").asString();
			advance(alice, id, 0).andExpect(status().isBadRequest());
			advance(alice, id, PracticeService.MAX_ADVANCE_BARS + 1).andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("reveals the entry with the first bar after the plan, at that bar's open")
		void entryRevealed() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			long cursor = session.at("/state/cursor").asLong();
			planLong(alice, session).andExpect(status().isOk());

			JsonNode step = read(advance(alice, id, 1).andExpect(status().isOk()));
			assertThat(step.at("/state/trades/0/entry/time").asLong()).isEqualTo(cursor);
			assertThat(step.at("/state/trades/0/entry/ticks").asLong())
				.isEqualTo(step.at("/bars/0/open").asLong());
			if (step.at("/state/status").asString().equals("IN_TRADE")) {
				assertThat(absent(step, "/state/trades/0/result")).isTrue();
			}
		}

		@Test
		@DisplayName("ends a flat session when it reaches its limit, even across a closed market")
		void endsAtTheLimit() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			Instant start = Instant.ofEpochSecond(session.at("/state/startTime").asLong());
			jdbc.update("UPDATE practice_session SET cursor_time = ? WHERE id = ?::uuid",
				java.sql.Timestamp.from(start.plus(PracticeService.MAX_REPLAY).minusSeconds(FIVE_MINUTES)),
				id);

			JsonNode last = read(advance(alice, id, 1).andExpect(status().isOk())).get("state");
			assertThat(last.get("status").asString()).isEqualTo("COMPLETE");
			assertThat(last.get("cursor").asLong())
				.isEqualTo(start.plus(PracticeService.MAX_REPLAY).getEpochSecond());
			assertThat(last.get("seed").asString()).isEqualTo(Long.toString(seedOf(id)));
			advance(alice, id, 1).andExpect(status().isConflict());
		}

		@Test
		@DisplayName("skip is only for a running trade")
		void skipNeedsATrade() throws Exception {
			String id = create(alice).at("/state/id").asString();
			skip(alice, id).andExpect(status().isConflict());
		}
	}

	@Nested
	@DisplayName("the result")
	class Results {

		@Test
		@DisplayName("appears exactly when the replay passes the exit; the seed only when the session ends")
		void revealedAtTheEnd() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			planLong(alice, session).andExpect(status().isOk());

			JsonNode done = read(skip(alice, id).andExpect(status().isOk()));
			JsonNode state = done.get("state");
			JsonNode result = state.at("/trades/0/result");

			// Flat again, free to place the next trade — so the seed, the whole future, stays hidden.
			assertThat(state.get("status").asString()).isEqualTo("ANALYSING");
			assertThat(absent(state, "/seed")).isTrue();
			assertThat(result.get("outcome").asString()).isIn("TARGET", "STOP", "AMBIGUOUS", "TIME_EXIT");
			assertThat(result.get("exitTime").asLong()).isLessThan(state.get("cursor").asLong());
			assertThat(state.at("/score/closedTrades").asInt()).isEqualTo(1);
			assertThat(state.at("/score/totalR").asDouble()).isEqualTo(result.get("rMultiple").asDouble());
			assertThat(state.at("/score/netPnlCents").asLong()).isEqualTo(result.get("netPnlCents").asLong());

			JsonNode ended = read(end(alice, id).andExpect(status().isOk()));
			assertThat(ended.get("status").asString()).isEqualTo("COMPLETE");
			// BUG-028: a string, exactly. As a JSON number a 64-bit seed is rounded by any JavaScript
			// client, and the rounded value reproduces a different market.
			assertThat(ended.get("seed").isTextual()).isTrue();
			assertThat(ended.get("seed").asString()).isEqualTo(Long.toString(seedOf(id)));
			assertThat(result.get("engineVersion").asString()).isEqualTo(FillEngine.VERSION);
			assertThat(result.get("fillModelVersion").asString())
				.isEqualTo(FillEngine.FillModel.BASIC.version());

			// The bar the trade ended in is among those just revealed.
			long exit = result.get("exitTime").asLong();
			boolean found = false;
			for (JsonNode bar : done.get("bars")) {
				long t = bar.get("time").asLong();
				found |= t <= exit && exit < t + FIVE_MINUTES;
			}
			assertThat(found).isTrue();
		}

		@Test
		@DisplayName("can be re-derived from the revealed seed alone (DEC-012)")
		void reproducible() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			JsonNode planned = read(planLong(alice, session).andExpect(status().isOk()));
			Instant lockedAt = Instant.ofEpochSecond(planned.get("cursor").asLong());

			skip(alice, id).andExpect(status().isOk());
			JsonNode state = read(end(alice, id).andExpect(status().isOk()));
			JsonNode result = state.at("/trades/0/result");

			// Nothing but what the API has now published: the seed, the plan, the moment.
			Instrument nq = marketData.requireInstrument("DEMO-NQ");
			List<Bar> seconds = marketData.internalBars(nq, Timeframe.S1, lockedAt,
				PracticeService.sessionEnd(lockedAt), Long.parseLong(state.get("seed").asString())).bars();
			FillEngine.Fill again = FillEngine.resolve(new FillEngine.Plan(
					FillEngine.Direction.LONG,
					state.at("/trades/0/plan/stopTicks").asLong(),
					state.at("/trades/0/plan/targetTicks").asLong(), 1),
				seconds, FillEngine.FillModel.BASIC);

			assertThat(again.outcome().name()).isEqualTo(result.get("outcome").asString());
			assertThat(again.exitTime().getEpochSecond()).isEqualTo(result.get("exitTime").asLong());
			assertThat(again.exitTicks()).isEqualTo(result.get("exitTicks").asLong());
			assertThat(again.pnlTicks()).isEqualTo(result.get("pnlTicks").asLong());
		}

		@Test
		@DisplayName("is the same whether the replay is stepped or skipped: it was fixed at the plan")
		void steppingChangesNothing() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			planLong(alice, session).andExpect(status().isOk());
			String before = jdbc.queryForObject(
				"SELECT outcome || ':' || exit_ticks || ':' || created_at FROM practice_result "
					+ "WHERE session_id = ?::uuid", String.class, id);

			JsonNode state = read(fetch(alice, id)).get("state");
			while (state.get("status").asString().equals("IN_TRADE")) {
				state = read(advance(alice, id, 50).andExpect(status().isOk())).get("state");
			}

			String after = jdbc.queryForObject(
				"SELECT outcome || ':' || exit_ticks || ':' || created_at FROM practice_result "
					+ "WHERE session_id = ?::uuid", String.class, id);
			assertThat(after).isEqualTo(before);
			assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM practice_result WHERE session_id = ?::uuid", Long.class, id))
				.isEqualTo(1L);
			assertThat(state.at("/trades/0/result/outcome").asString()).isEqualTo(before.split(":")[0]);
		}

		@Test
		@DisplayName("the journal counts a trade only once the replay has passed its exit")
		void journalHidesOutcome() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			planLong(alice, session).andExpect(status().isOk());

			JsonNode running = read(mockMvc.perform(get("/api/practice/sessions").cookie(alice)));
			assertThat(running.get(0).get("status").asString()).isEqualTo("IN_TRADE");
			assertThat(running.get(0).get("trades").asInt()).isEqualTo(1);
			assertThat(running.at("/0/score/closedTrades").asInt()).isZero();
			assertThat(running.at("/0/score/netPnlCents").asLong()).isZero();

			JsonNode result = read(skip(alice, id).andExpect(status().isOk()))
				.at("/state/trades/0/result");
			JsonNode closed = read(mockMvc.perform(get("/api/practice/sessions").cookie(alice)));
			assertThat(closed.at("/0/score/closedTrades").asInt()).isEqualTo(1);
			assertThat(closed.at("/0/score/totalR").asDouble()).isEqualTo(result.get("rMultiple").asDouble());
		}
	}

	@Nested
	@DisplayName("a replay with several trades (DEC-046)")
	class SeveralTrades {

		@Test
		@DisplayName("takes the next trade once the last one is over, numbered in order")
		void tradesInSequence() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			tightLong(alice, id, lastClose(session.get("bars"))).andExpect(status().isOk());
			JsonNode afterFirst = read(skip(alice, id).andExpect(status().isOk()));
			assertThat(afterFirst.at("/state/status").asString()).isEqualTo("ANALYSING");

			// The second trade is priced against what the replay shows now, not the first one's price.
			long reference = lastClose(afterFirst.get("bars"));
			JsonNode second = read(tightLong(alice, id, reference).andExpect(status().isOk()));
			assertThat(second.get("status").asString()).isEqualTo("IN_TRADE");
			assertThat(second.get("trades").size()).isEqualTo(2);
			assertThat(second.at("/trades/1/number").asInt()).isEqualTo(2);
			assertThat(second.at("/trades/1/plan/referenceTicks").asLong()).isEqualTo(reference);
			assertThat(absent(second, "/trades/0/result")).isFalse();
			assertThat(absent(second, "/trades/1/result")).isTrue();
			assertThat(second.at("/score/closedTrades").asInt()).isEqualTo(1);

			JsonNode afterSecond = read(skip(alice, id).andExpect(status().isOk())).get("state");
			JsonNode score = afterSecond.get("score");
			assertThat(score.get("closedTrades").asInt()).isEqualTo(2);
			assertThat(score.get("wins").asInt() + score.get("losses").asInt()).isLessThanOrEqualTo(2);
			long net = afterSecond.at("/trades/0/result/netPnlCents").asLong()
				+ afterSecond.at("/trades/1/result/netPnlCents").asLong();
			assertThat(score.get("netPnlCents").asLong()).isEqualTo(net);
			// BUG-039: exactly the sum of the R column as shown, not of the unrounded values.
			double r = afterSecond.at("/trades/0/result/rMultiple").asDouble()
				+ afterSecond.at("/trades/1/result/rMultiple").asDouble();
			assertThat(score.get("totalR").asDouble()).isEqualTo(Math.round(r * 100) / 100.0);
			assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM practice_result WHERE session_id = ?::uuid", Long.class, id))
				.isEqualTo(2L);
		}

		@Test
		@DisplayName("cannot end while a trade runs; once ended, takes no more trades")
		void ending() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			planLong(alice, session).andExpect(status().isOk());

			end(alice, id).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("still running")));

			JsonNode flat = read(skip(alice, id).andExpect(status().isOk()));
			end(alice, id).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETE"));
			// Ending twice is the same as ending once: a double-click is not an error.
			end(alice, id).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETE"));

			tightLong(alice, id, lastClose(flat.get("bars"))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ended")));
		}

		@Test
		@DisplayName("an empty session can be ended too, and reveals its seed")
		void endWithoutTrades() throws Exception {
			String id = create(alice).at("/state/id").asString();
			JsonNode ended = read(end(alice, id).andExpect(status().isOk()));
			assertThat(ended.get("trades").size()).isZero();
			assertThat(ended.at("/score/closedTrades").asInt()).isZero();
			assertThat(ended.get("seed").asString()).isEqualTo(Long.toString(seedOf(id)));
		}

		@Test
		@DisplayName("holds at most " + PracticeService.MAX_TRADES + " trades")
		void capped() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			// Fifty settled trades, written straight into the tables: the cap is what is under test.
			jdbc.update("""
				INSERT INTO trade_plan (session_id, seq, direction, stop_ticks, target_ticks, contracts,
				    reference_ticks, submitted_cursor)
				SELECT ?::uuid, n, 'LONG', 99, 101, 1, 100, '2000-01-01' FROM generate_series(1, ?) n""",
				id, PracticeService.MAX_TRADES);
			jdbc.update("""
				INSERT INTO practice_result (session_id, seq, outcome, entry_time, entry_ticks, exit_time,
				    exit_ticks, pnl_ticks, net_pnl_cents, commission_cents, mae_ticks, mfe_ticks, gap_exit,
				    resolution, engine_version, fill_model_version, generator_version, seed)
				SELECT ?::uuid, n, 'STOP', '2000-01-01', 100, '2000-01-01', 99, -1, -500, 0, 1, 0, false,
				    'S1', 'test', 'test', 'test', 1 FROM generate_series(1, ?) n""",
				id, PracticeService.MAX_TRADES);

			planLong(alice, session).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("at most")));
			JsonNode state = read(fetch(alice, id)).get("state");
			assertThat(state.at("/score/closedTrades").asInt()).isEqualTo(PracticeService.MAX_TRADES);
			assertThat(state.at("/score/losses").asInt()).isEqualTo(PracticeService.MAX_TRADES);
			assertThat(state.at("/score/totalR").asDouble()).isEqualTo(-PracticeService.MAX_TRADES);
		}

		@Test
		@DisplayName("the total R is the sum of the R column as shown (BUG-039)")
		void totalMatchesTheColumn() throws Exception {
			String id = create(alice).at("/state/id").asString();
			// The live case: −43 and +79 ticks on 40 of risk, each less a $2.50 round trip (DEC-076): −1.0875R and
			// +1.9625R. Unrounded they sum to +0.875R, shown +0.88R; the learner sees −1.09R and +1.96R, and adds them
			// up to +0.87R.
			jdbc.update("""
				INSERT INTO trade_plan (session_id, seq, direction, stop_ticks, target_ticks, contracts,
				    reference_ticks, submitted_cursor)
				VALUES (?::uuid, 1, 'LONG', 60, 200, 1, 100, '2000-01-01'),
				       (?::uuid, 2, 'SHORT', 140, 20, 1, 100, '2000-01-01')""", id, id);
			jdbc.update("""
				INSERT INTO practice_result (session_id, seq, outcome, entry_time, entry_ticks, exit_time,
				    exit_ticks, pnl_ticks, net_pnl_cents, commission_cents, mae_ticks, mfe_ticks, gap_exit,
				    resolution, engine_version, fill_model_version, generator_version, seed)
				VALUES (?::uuid, 1, 'STOP', '2000-01-01', 100, '2000-01-01', 57, -43, -21750, 250, 43, 13,
				        false, 'M1', 'test', 'test', 'test', 1),
				       (?::uuid, 2, 'TARGET', '2000-01-01', 100, '2000-01-01', 21, 79, 39250, 250, 0, 79,
				        false, 'M1', 'test', 'test', 'test', 1)""", id, id);

			JsonNode state = read(fetch(alice, id)).get("state");
			assertThat(state.at("/trades/0/result/rMultiple").asDouble()).isEqualTo(-1.09);
			assertThat(state.at("/trades/1/result/rMultiple").asDouble()).isEqualTo(1.96);
			assertThat(state.at("/score/totalR").asDouble()).isEqualTo(0.87);
			assertThat(state.at("/score/netPnlCents").asLong()).isEqualTo(17_500);
		}

		@Test
		@DisplayName("a result belongs to the plan with its number, and the database insists")
		void resultNeedsItsPlan() throws Exception {
			String id = create(alice).at("/state/id").asString();
			assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO practice_result (session_id, seq, outcome, entry_time, entry_ticks, exit_time,
				    exit_ticks, pnl_ticks, net_pnl_cents, commission_cents, mae_ticks, mfe_ticks, gap_exit,
				    resolution, engine_version, fill_model_version, generator_version, seed)
				VALUES (?::uuid, 1, 'STOP', now(), 100, now(), 99, -1, -100, 0, 1, 0, false,
				    'S1', 'test', 'test', 'test', 1)""", id))
				.hasMessageContaining("fk_practice_result_plan");
		}
	}

	@Nested
	@DisplayName("closing at market (DEC-057)")
	class CloseAtMarket {

		/** A long with 100 points of room either side: it will not settle on its own in a few bars. */
		private ResultActions wideLong(Cookie who, String id, long reference) throws Exception {
			return plan(who, id, "LONG", reference - 400, reference + 800);
		}

		@Test
		@DisplayName("closes the running trade at the next price, and the replay moves to the fill")
		void closes() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			wideLong(alice, id, lastClose(session.get("bars"))).andExpect(status().isOk());
			advance(alice, id, 2).andExpect(status().isOk());

			JsonNode closed = read(close(alice, id).andExpect(status().isOk()));
			JsonNode state = closed.get("state");
			JsonNode result = state.at("/trades/0/result");
			assertThat(state.get("status").asString()).isEqualTo("ANALYSING");
			assertThat(result.get("outcome").asString()).isEqualTo("CLOSED");
			assertThat(result.has("assumption")).isFalse();
			assertThat(result.get("pnlTicks").asLong())
				.isEqualTo(result.get("exitTicks").asLong() - result.get("entryTicks").asLong());
			// The fill is in the past now, by one bar of the engine's resolution and no more.
			long cursor = state.get("cursor").asLong();
			assertThat(cursor).isGreaterThan(result.get("exitTime").asLong())
				.isLessThanOrEqualTo(result.get("exitTime").asLong() + 60);
			assertThat(closed.get("bars")).allSatisfy(bar -> assertThat(bar.get("time").asLong()).isLessThan(cursor));
			assertThat(state.at("/score/closedTrades").asInt()).isEqualTo(1);
			assertThat(state.at("/score/netPnlCents").asLong()).isEqualTo(result.get("netPnlCents").asLong());

			// The planned outcome is still the record of what the plan would have done, unchanged.
			assertThat(jdbc.queryForObject("SELECT outcome FROM practice_result WHERE session_id = ?::uuid",
				String.class, id)).isIn("TARGET", "STOP", "AMBIGUOUS", "TIME_EXIT");
			assertThat(jdbc.queryForObject("SELECT count(*) FROM practice_close WHERE session_id = ?::uuid",
				Long.class, id)).isEqualTo(1L);

			// Flat again: the next trade can follow, and the closed one stays closed.
			JsonNode next = read(tightLong(alice, id, lastClose(closed.get("bars"))).andExpect(status().isOk()));
			assertThat(next.get("status").asString()).isEqualTo("IN_TRADE");
			assertThat(next.at("/trades/0/result/outcome").asString()).isEqualTo("CLOSED");
		}

		@Test
		@DisplayName("closed the moment it is placed: in and out at one price, the commission is the cost")
		void atOnce() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			wideLong(alice, id, lastClose(session.get("bars"))).andExpect(status().isOk());
			JsonNode result = read(close(alice, id).andExpect(status().isOk())).at("/state/trades/0/result");
			assertThat(result.get("pnlTicks").asLong()).isZero();
			assertThat(result.get("netPnlCents").asLong()).isEqualTo(-result.get("commissionCents").asLong());
		}

		@Test
		@DisplayName("BUG-087 in and out at one price under $40 of fees: a loss of 0.02R, not a scratch at 0R")
		void netOfCosts() throws Exception {
			mockMvc.perform(put("/api/practice/settings").cookie(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"feesCents\":4000,\"marketSlippageTicks\":0,\"stopSlippageTicks\":1,\"limitThroughTicks\":0}"))
				.andExpect(status().isOk());
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			wideLong(alice, id, lastClose(session.get("bars"))).andExpect(status().isOk());
			close(alice, id).andExpect(status().isOk());
			JsonNode state = read(advance(alice, id, 1).andExpect(status().isOk())).get("state");

			JsonNode result = state.at("/trades/0/result");
			assertThat(result.get("pnlTicks").asLong()).isZero();
			assertThat(result.get("netPnlCents").asLong()).isEqualTo(-4_000);
			// 400 ticks to the stop at $5 is $2,000 risked; $40 of it is 0.02R.
			assertThat(result.get("rMultiple").asDouble()).isEqualTo(-0.02);
			assertThat(state.at("/score/wins").asInt()).isZero();
			assertThat(state.at("/score/losses").asInt()).isEqualTo(1);
			assertThat(state.at("/score/totalR").asDouble()).isEqualTo(-0.02);
		}

		@Test
		@DisplayName("when the plan's own exit comes first, that exit is revealed and nothing is closed")
		void planExitsFirst() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			tightLong(alice, id, lastClose(session.get("bars"))).andExpect(status().isOk());
			// Planted: the replay already past the second the plan's stop or target filled in. In play
			// this is a gap at the open the close would take, which the simulator cannot be told to make.
			jdbc.update("UPDATE practice_session SET cursor_time = (SELECT exit_time FROM practice_result "
				+ "WHERE session_id = ?::uuid) + interval '1 second' WHERE id = ?::uuid", id, id);

			JsonNode result = read(close(alice, id).andExpect(status().isOk())).at("/state/trades/0/result");
			assertThat(result.get("outcome").asString()).isIn("TARGET", "STOP", "AMBIGUOUS");
			assertThat(jdbc.queryForObject("SELECT count(*) FROM practice_close WHERE session_id = ?::uuid",
				Long.class, id)).isZero();
		}

		@Test
		@DisplayName("only a running trade can be closed, and a close is never changed afterwards")
		void rules() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();
			// BUG-057: the refusal names the command that was refused, not Skip to result.
			close(alice, id).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("There is no running trade to close."));
			skip(alice, id).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("There is no running trade to skip to the end of."));
			wideLong(alice, id, lastClose(session.get("bars"))).andExpect(status().isOk());
			close(bob, id).andExpect(status().isNotFound());
			close(alice, id).andExpect(status().isOk());
			close(alice, id).andExpect(status().isConflict());
			assertThatThrownBy(() -> jdbc.update("UPDATE practice_close SET exit_ticks = 1 WHERE session_id = ?::uuid", id))
				.hasMessageContaining("immutable");
		}
	}

	@Nested
	@DisplayName("isolation (DEC-022)")
	class Isolation {

		@Test
		@DisplayName("another user's session is indistinguishable from one that does not exist")
		void notYours() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asString();

			fetch(bob, id).andExpect(status().isNotFound());
			planLong(bob, session).andExpect(status().isNotFound());
			advance(bob, id, 1).andExpect(status().isNotFound());
			skip(bob, id).andExpect(status().isNotFound());
			end(bob, id).andExpect(status().isNotFound());
			fetch(bob, UUID.randomUUID().toString()).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("No such practice session."));

			mockMvc.perform(get("/api/practice/sessions").cookie(bob))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

			// And Alice's session is untouched by any of it.
			read(fetch(alice, id).andExpect(status().isOk()));
			assertThat(read(fetch(alice, id)).at("/state/status").asString()).isEqualTo("ANALYSING");
		}
	}

	private long seedOf(String sessionId) {
		return jdbc.queryForObject("SELECT seed FROM practice_session WHERE id = ?::uuid",
			Long.class, sessionId);
	}
}
