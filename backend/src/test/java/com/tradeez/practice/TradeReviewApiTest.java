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
 * T24 (DEC-068) through the API, on real-style stored minutes: a finished trade's review — only once the replay
 * has passed its exit, nothing past the cursor, someone else's trades nobody's — and, after a close by hand,
 * what the bracket would have done, told only once the replay reaches it.
 */
class TradeReviewApiTest extends AbstractIntegrationTest {

	private static final Instant DATA_FROM = Instant.parse("2025-03-03T00:00:00Z");
	private static final Instant DATA_TO = Instant.parse("2025-03-15T00:00:00Z");

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
	void importTwoWeeks() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		jdbc.update("DELETE FROM drill_setup");
		jdbc.update("DELETE FROM drill");
		jdbc.update("DELETE FROM detected_setup");
		jdbc.update("DELETE FROM market_bar");
		jdbc.update("DELETE FROM market_data_import");
		alice = register("alice@example.com");
		bob = register("bob@example.com");
		UUID nq = marketData.requireInstrument("NQ").getId();
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
		barStore.insert(nq, "1m", bars);
		jdbc.update("""
			INSERT INTO market_data_import (instrument_id, source, dataset, symbol, resolution,
			    range_from, range_to, estimated_cost_usd, bars_stored, status)
			VALUES (?, 'massive', 'CME', 'NQH5', '1m', ?, ?, 0, ?, 'DONE')""",
			nq, Timestamp.from(DATA_FROM), Timestamp.from(DATA_TO), bars.size());
	}

	private Cookie register(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"T\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
	}

	private JsonNode read(ResultActions actions) throws Exception {
		return json.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private JsonNode session() throws Exception {
		return read(mockMvc.perform(post("/api/practice/sessions").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"instrument\":\"NQ\",\"timeframe\":\"5m\"}"))
			.andExpect(status().isCreated()));
	}

	private static long reference(JsonNode session) {
		JsonNode bars = session.get("bars");
		return bars.get(bars.size() - 1).get("close").asLong();
	}

	private ResultActions lock(String id, String body) throws Exception {
		return mockMvc.perform(post("/api/practice/sessions/" + id + "/plan").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions review(Cookie who, String id, String number) throws Exception {
		return mockMvc.perform(get("/api/practice/sessions/" + id + "/trades/" + number + "/review").cookie(who));
	}

	@Test
	@DisplayName("a review opens once the exit is shown: in order, nothing past the cursor, its hits drawn, nobody else's")
	void review() throws Exception {
		JsonNode session = session();
		String id = session.at("/state/id").asString();
		long ref = reference(session);
		// A near target, also named as the draw: when the target fills, the draw was reached while the trade was open.
		lock(id, "{\"direction\":\"LONG\",\"stopTicks\":%d,\"targetTicks\":%d,\"contracts\":1,\"model\":\"OTE\",\"dolTicks\":%d}"
			.formatted(ref - 80, ref + 8, ref + 8)).andExpect(status().isOk());
		review(alice, id, "1").andExpect(status().isConflict());

		JsonNode state = read(mockMvc.perform(post("/api/practice/sessions/" + id + "/skip").cookie(alice).with(csrf()))
			.andExpect(status().isOk())).get("state");
		JsonNode view = read(review(alice, id, "1").andExpect(status().isOk()));
		assertThat(view.get("version").asString()).isEqualTo("review-1.0.0");
		// The core rules, and the OTE model's own.
		assertThat(view.get("rules").valueStream().map(JsonNode::asString).toList())
			.containsExactly("LIQUIDITY", "SWEEP", "MSS", "FVG", "OTE");
		JsonNode moments = view.get("moments");
		List<String> kinds = moments.findValuesAsString("kind");
		assertThat(kinds).contains("DECISION", "ENTRY", "EXIT");
		assertThat(kinds.indexOf("DECISION")).isLessThan(kinds.indexOf("ENTRY"));
		assertThat(kinds.indexOf("ENTRY")).isLessThan(kinds.indexOf("EXIT"));
		long previous = Long.MIN_VALUE;
		long cursor = state.get("cursor").asLong();
		for (JsonNode moment : moments) {
			long time = moment.get("time").asLong();
			assertThat(time).isGreaterThanOrEqualTo(previous).isLessThanOrEqualTo(cursor);
			previous = time;
		}
		assertThat(moments.get(kinds.indexOf("DECISION")).get("lines").get(1).asString())
			.startsWith("OTE model: ").endsWith(" checks met when placed.");
		// Every rule's moment is drawn, and nothing else is.
		assertThat(view.at("/layer/setups").size()).isEqualTo((int) kinds.stream().filter("RULE"::equals).count());
		if (state.at("/trades/0/result/outcome").asString().equals("TARGET")) {
			JsonNode draw = moments.get(kinds.indexOf("DRAW"));
			assertThat(draw.get("phase").asString()).isEqualTo("TRADE");
		}

		review(bob, id, "1").andExpect(status().isNotFound());
		review(alice, id, "2").andExpect(status().isNotFound());
		review(alice, UUID.randomUUID().toString(), "1").andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("closed by hand: what the bracket would have done is still ahead, then told once the replay passes it")
	void held() throws Exception {
		JsonNode session = session();
		String id = session.at("/state/id").asString();
		long ref = reference(session);
		// Wide, so the close is the learner's and not the bracket's.
		lock(id, "{\"direction\":\"SHORT\",\"stopTicks\":%d,\"targetTicks\":%d,\"contracts\":1}".formatted(ref + 600, ref - 600))
			.andExpect(status().isOk());
		JsonNode closed = read(mockMvc.perform(post("/api/practice/sessions/" + id + "/close").cookie(alice).with(csrf()))
			.andExpect(status().isOk())).get("state");
		assertThat(closed.at("/trades/0/result/outcome").asString()).isEqualTo("CLOSED");

		JsonNode ahead = read(review(alice, id, "1").andExpect(status().isOk()));
		assertThat(ahead.get("moments").findValuesAsString("kind")).doesNotContain("HELD").endsWith("EXIT");
		assertThat(ahead.get("moments").get(ahead.get("moments").size() - 1).get("title").asString()).startsWith("You closed it at ");
		assertThat(ahead.get("summary").valueStream().map(JsonNode::asString).toList())
			.contains("What the bracket would have done without your close is still ahead of the replay: step on to see it.");

		// Past the session's close, where the bracket was out at the latest.
		mockMvc.perform(post("/api/practice/sessions/" + id + "/advance").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"bars\":500}")).andExpect(status().isOk());
		JsonNode passed = read(review(alice, id, "1").andExpect(status().isOk()));
		JsonNode last = passed.get("moments").get(passed.get("moments").size() - 1);
		assertThat(last.get("kind").asString()).isEqualTo("HELD");
		assertThat(last.get("phase").asString()).isEqualTo("AFTER");
		assertThat(last.get("title").asString()).startsWith("Had you held, ");
	}
}
