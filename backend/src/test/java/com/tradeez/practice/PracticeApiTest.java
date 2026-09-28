package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

	@BeforeEach
	void signInTwoUsers() throws Exception {
		// Sessions, plans and results cascade from the user.
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		alice = register("alice@example.com");
		bob = register("bob@example.com");
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
		return plan(who, session.at("/state/id").asText(), "LONG", reference - 40, reference + 80);
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
			assertThat(session.at("/state/status").asText()).isEqualTo("ANALYSING");
		}

		@Test
		@DisplayName("withholds its seed, which is never the public chart's")
		void seedWithheld() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asText();

			// DEC-038: the seed would regenerate the whole future of this market.
			assertThat(absent(session, "/state/seed")).isTrue();
			// Not under any key, in any form: the digits of the seed appear nowhere in the response.
			assertThat(session.toString()).doesNotContain(Long.toString(seedOf(id)));

			assertThat(seedOf(id)).isNotEqualTo(MarketDataService.PUBLIC_CHART_SEED);
			assertThat(seedOf(create(alice).at("/state/id").asText()))
				.as("every session is a different market")
				.isNotEqualTo(seedOf(id));
		}

		@Test
		@DisplayName("is labelled as simulated data wherever it appears (DEC-033)")
		void labelled() throws Exception {
			JsonNode session = create(alice);
			assertThat(session.at("/state/dataClass").asText()).isEqualTo("SIMULATED");
			assertThat(session.at("/state/disclaimer").asText()).contains("SIMULATED");
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
		@DisplayName("defaults to DEMO-NQ on five minutes when no body is sent")
		void defaults() throws Exception {
			JsonNode session = read(mockMvc.perform(post("/api/practice/sessions").cookie(alice)
					.with(csrf()))
				.andExpect(status().isCreated()));
			assertThat(session.at("/state/instrument").asText()).isEqualTo("DEMO-NQ");
			assertThat(session.at("/state/timeframe").asText()).isEqualTo("5m");
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

			mockMvc.perform(post("/api/practice/sessions").cookie(alice).with(csrf()))
				.andExpect(status().isTooManyRequests());
			// Someone else's quota is their own.
			mockMvc.perform(post("/api/practice/sessions").cookie(bob).with(csrf()))
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
			String id = session.at("/state/id").asText();
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

			assertThat(state.get("status").asText()).isEqualTo("IN_TRADE");
			assertThat(state.at("/plan/direction").asText()).isEqualTo("LONG");
			assertThat(state.at("/plan/plannedRewardRisk").asDouble()).isEqualTo(2.0);
			assertThat(state.at("/plan/lockedAt").asLong())
				.isEqualTo(session.at("/state/cursor").asLong());
			// The result exists in the database already — and must not be in the response.
			assertThat(absent(state, "/result")).isTrue();
			assertThat(absent(state, "/seed")).isTrue();
			// Even the entry price is one second of the future until the replay shows it.
			assertThat(absent(state, "/entry")).isTrue();
		}

		@Test
		@DisplayName("cannot be submitted twice (DEC-013)")
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
			UUID id = UUID.fromString(session.at("/state/id").asText());

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
			String id = session.at("/state/id").asText();
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
			String id = session.at("/state/id").asText();
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
			String id = session.at("/state/id").asText();
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
			String id = session.at("/state/id").asText();
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
			String id = session.at("/state/id").asText();
			Instant start = Instant.ofEpochSecond(session.at("/state/startTime").asLong());
			jdbc.update("UPDATE practice_session SET cursor_time = ? WHERE id = ?::uuid",
				java.sql.Timestamp.from(start.plus(PracticeService.MAX_REPLAY)), id);

			advance(alice, id, 1).andExpect(status().isConflict());
		}

		@Test
		@DisplayName("rejects step sizes outside 1..500")
		void stepBounds() throws Exception {
			String id = create(alice).at("/state/id").asText();
			advance(alice, id, 0).andExpect(status().isBadRequest());
			advance(alice, id, PracticeService.MAX_ADVANCE_BARS + 1).andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("reveals the entry with the first bar after the plan, at that bar's open")
		void entryRevealed() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asText();
			long cursor = session.at("/state/cursor").asLong();
			planLong(alice, session).andExpect(status().isOk());

			JsonNode step = read(advance(alice, id, 1).andExpect(status().isOk()));
			if (step.at("/state/status").asText().equals("COMPLETE")) {
				return; // resolved inside the very first bar; the entry is covered by the result
			}
			assertThat(step.at("/state/entry/time").asLong()).isEqualTo(cursor);
			assertThat(step.at("/state/entry/ticks").asLong())
				.isEqualTo(step.at("/bars/0/open").asLong());
			assertThat(absent(step, "/state/result")).isTrue();
		}

		@Test
		@DisplayName("skip is only for a running trade")
		void skipNeedsATrade() throws Exception {
			String id = create(alice).at("/state/id").asText();
			skip(alice, id).andExpect(status().isConflict());
		}
	}

	@Nested
	@DisplayName("the result")
	class Results {

		@Test
		@DisplayName("appears exactly when the replay passes the exit, with the seed")
		void revealedAtTheEnd() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asText();
			planLong(alice, session).andExpect(status().isOk());

			JsonNode done = read(skip(alice, id).andExpect(status().isOk()));
			JsonNode state = done.get("state");
			JsonNode result = state.get("result");

			assertThat(state.get("status").asText()).isEqualTo("COMPLETE");
			assertThat(result.get("outcome").asText()).isIn("TARGET", "STOP", "AMBIGUOUS", "TIME_EXIT");
			assertThat(result.get("exitTime").asLong()).isLessThan(state.get("cursor").asLong());
			// BUG-028: a string, exactly. As a JSON number a 64-bit seed is rounded by any JavaScript
			// client, and the rounded value reproduces a different market.
			assertThat(state.get("seed").isTextual()).isTrue();
			assertThat(state.get("seed").asText()).isEqualTo(Long.toString(seedOf(id)));
			assertThat(result.get("engineVersion").asText()).isEqualTo(FillEngine.VERSION);
			assertThat(result.get("fillModelVersion").asText())
				.isEqualTo(FillEngine.FillModel.DEFAULT.version());

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
			String id = session.at("/state/id").asText();
			JsonNode planned = read(planLong(alice, session).andExpect(status().isOk()));
			Instant lockedAt = Instant.ofEpochSecond(planned.get("cursor").asLong());

			JsonNode state = read(skip(alice, id).andExpect(status().isOk())).get("state");
			JsonNode result = state.get("result");

			// Nothing but what the API has now published: the seed, the plan, the moment.
			Instrument nq = marketData.requireInstrument("DEMO-NQ");
			List<Bar> seconds = marketData.internalBars(nq, Timeframe.S1, lockedAt,
				PracticeService.sessionEnd(lockedAt), Long.parseLong(state.get("seed").asText())).bars();
			FillEngine.Fill again = FillEngine.resolve(new FillEngine.Plan(
					FillEngine.Direction.LONG,
					state.at("/plan/stopTicks").asLong(),
					state.at("/plan/targetTicks").asLong(), 1),
				seconds, FillEngine.FillModel.DEFAULT);

			assertThat(again.outcome().name()).isEqualTo(result.get("outcome").asText());
			assertThat(again.exitTime().getEpochSecond()).isEqualTo(result.get("exitTime").asLong());
			assertThat(again.exitTicks()).isEqualTo(result.get("exitTicks").asLong());
			assertThat(again.pnlTicks()).isEqualTo(result.get("pnlTicks").asLong());
		}

		@Test
		@DisplayName("is the same whether the replay is stepped or skipped: it was fixed at the plan")
		void steppingChangesNothing() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asText();
			planLong(alice, session).andExpect(status().isOk());
			String before = jdbc.queryForObject(
				"SELECT outcome || ':' || exit_ticks || ':' || created_at FROM practice_result "
					+ "WHERE session_id = ?::uuid", String.class, id);

			JsonNode state = read(fetch(alice, id)).get("state");
			while (!state.get("status").asText().equals("COMPLETE")) {
				state = read(advance(alice, id, 50).andExpect(status().isOk())).get("state");
			}

			String after = jdbc.queryForObject(
				"SELECT outcome || ':' || exit_ticks || ':' || created_at FROM practice_result "
					+ "WHERE session_id = ?::uuid", String.class, id);
			assertThat(after).isEqualTo(before);
			assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM practice_result WHERE session_id = ?::uuid", Long.class, id))
				.isEqualTo(1L);
			assertThat(state.at("/result/outcome").asText()).isEqualTo(before.split(":")[0]);
		}

		@Test
		@DisplayName("the journal keeps the outcome hidden until the session is complete")
		void journalHidesOutcome() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asText();
			planLong(alice, session).andExpect(status().isOk());

			JsonNode running = read(mockMvc.perform(get("/api/practice/sessions").cookie(alice)));
			assertThat(running.get(0).get("status").asText()).isEqualTo("IN_TRADE");
			assertThat(running.get(0).get("direction").asText()).isEqualTo("LONG");
			assertThat(absent(running, "/0/outcome")).isTrue();
			assertThat(absent(running, "/0/netPnlCents")).isTrue();

			skip(alice, id).andExpect(status().isOk());
			JsonNode finished = read(mockMvc.perform(get("/api/practice/sessions").cookie(alice)));
			assertThat(absent(finished, "/0/outcome")).isFalse();
			assertThat(finished.get(0).get("rMultiple").isNumber()).isTrue();
		}
	}

	@Nested
	@DisplayName("isolation (DEC-022)")
	class Isolation {

		@Test
		@DisplayName("another user's session is indistinguishable from one that does not exist")
		void notYours() throws Exception {
			JsonNode session = create(alice);
			String id = session.at("/state/id").asText();

			fetch(bob, id).andExpect(status().isNotFound());
			planLong(bob, session).andExpect(status().isNotFound());
			advance(bob, id, 1).andExpect(status().isNotFound());
			skip(bob, id).andExpect(status().isNotFound());
			fetch(bob, UUID.randomUUID().toString()).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("No such practice session."));

			mockMvc.perform(get("/api/practice/sessions").cookie(bob))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

			// And Alice's session is untouched by any of it.
			read(fetch(alice, id).andExpect(status().isOk()));
			assertThat(read(fetch(alice, id)).at("/state/status").asText()).isEqualTo("ANALYSING");
		}
	}

	private long seedOf(String sessionId) {
		return jdbc.queryForObject("SELECT seed FROM practice_session WHERE id = ?::uuid",
			Long.class, sessionId);
	}
}
