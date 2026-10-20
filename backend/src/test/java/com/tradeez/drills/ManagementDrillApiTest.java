package com.tradeez.drills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.ThreeWeeksOfNq;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * T26 (DEC-071), mode E through the API on three weeks of real-style NQ: an open trade drawn and the chart stopped,
 * nothing after the moment until the answer, and the stored story checked against the candles independently.
 */
class ManagementDrillApiTest extends AbstractIntegrationTest {

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

	@BeforeEach
	void importThreeWeeks() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		alice = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"alice@example.com\",\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"A\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
		if (ThreeWeeksOfNq.present(marketData)) {
			return;
		}
		jdbc.update("DELETE FROM drill_setup");
		jdbc.update("DELETE FROM drill");
		jdbc.update("DELETE FROM detected_setup");
		jdbc.update("DELETE FROM market_bar");
		jdbc.update("DELETE FROM market_data_import");
		ThreeWeeksOfNq.insert(marketData, barStore, jdbc);
	}

	private JsonNode read(ResultActions actions) throws Exception {
		return json.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private JsonNode start() throws Exception {
		return read(mockMvc.perform(post("/api/drills").cookie(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\":\"OPEN_TRADE_MANAGEMENT\",\"instrument\":\"NQ\",\"timeframe\":\"5m\"}"))
			.andExpect(status().isCreated()));
	}

	private ResultActions choose(String id, String choice) throws Exception {
		return mockMvc.perform(post("/api/drills/" + id + "/answer").cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"choice\":\"" + choice + "\"}"));
	}

	private static JsonNode line(JsonNode drill, String what) {
		for (JsonNode c : drill.at("/question/context")) {
			if (c.get("what").asString().equals(what)) {
				return c;
			}
		}
		throw new AssertionError("no " + what);
	}

	@Test
	@DisplayName("an open trade drawn, the chart stopped at the moment, three choices — and nothing after until the answer")
	void asked() throws Exception {
		JsonNode drill = start();
		assertThat(drill.get("mode").asString()).isEqualTo("MANAGE");
		assertThat(drill.at("/rule/version").asString()).isEqualTo("manage-1.0.0");
		assertThat(drill.at("/question/version").asString()).isEqualTo("manage-1.0.0");
		assertThat(drill.get("options").findValuesAsString("value")).containsExactly("HOLD", "BREAKEVEN", "HALF");
		assertThat(drill.has("result")).isFalse();
		assertThat(drill.has("revealed")).isFalse();
		// The trade: its target three times its stop's distance, on the other side of the entry.
		long entry = line(drill, "TRADE_ENTRY").get("lowTicks").asLong();
		long stop = line(drill, "TRADE_STOP").get("lowTicks").asLong();
		long target = line(drill, "TRADE_TARGET").get("lowTicks").asLong();
		assertThat(target - entry).isEqualTo(3 * (entry - stop));
		assertThat(Math.abs(entry - stop)).isGreaterThanOrEqualTo(8);
		// The chart ends at the moment: the stored window's end is the last candle's close.
		String id = drill.get("id").asString();
		Instant to = jdbc.queryForObject("SELECT window_to FROM drill WHERE id = ?::uuid", Timestamp.class, id).toInstant();
		JsonNode bars = drill.get("bars");
		assertThat(bars.get(bars.size() - 1).get("time").asLong() + 300).isEqualTo(to.getEpochSecond());
		// The last close is the trade a whole R or more in its favour.
		long last = bars.get(bars.size() - 1).get("close").asLong();
		assertThat((double) (entry < target ? last - entry : entry - last) / Math.abs(entry - stop)).isGreaterThanOrEqualTo(1.0);
		// A reload shows no more.
		JsonNode again = read(mockMvc.perform(get("/api/drills/" + id).cookie(alice)).andExpect(status().isOk()));
		assertThat(again.has("revealed")).isFalse();
		assertThat(again.toString()).doesNotContain("exits").doesNotContain("decidedAt");
	}

	@Test
	@DisplayName("answered once: the choices that paid most, each choice's story, and holding checked against the candles")
	void answered() throws Exception {
		JsonNode drill = start();
		String id = drill.get("id").asString();
		choose(id, "SELL").andExpect(status().isBadRequest());
		JsonNode done = read(choose(id, "HOLD").andExpect(status().isOk()));
		choose(id, "HALF").andExpect(status().isConflict());

		JsonNode result = done.get("result");
		List<String> best = List.of(result.get("answer").asString().split(","));
		assertThat(best).isNotEmpty().allMatch(List.of("HOLD", "BREAKEVEN", "HALF")::contains);
		assertThat(result.get("correct").asBoolean()).isEqualTo(best.contains("HOLD"));
		assertThat(result.get("explanation").size()).isEqualTo(5);
		assertThat(result.get("explanation").get(1).asString()).startsWith("Hold: ");
		assertThat(done.get("revealed").size()).isGreaterThan(0);

		// Holding, replayed here from the stored candles: the first to reach the stop or the target, or none.
		long entry = line(drill, "TRADE_ENTRY").get("lowTicks").asLong();
		long stop = line(drill, "TRADE_STOP").get("lowTicks").asLong();
		long target = line(drill, "TRADE_TARGET").get("lowTicks").asLong();
		boolean up = target > entry;
		Instant moment = jdbc.queryForObject("SELECT window_to FROM drill WHERE id = ?::uuid", Timestamp.class, id).toInstant();
		List<Bar> after = marketData.internalBars(marketData.requireInstrument("NQ"), Timeframe.M5, moment,
			moment.plusSeconds(300L * 100), 0).bars();
		String told = result.get("explanation").get(1).asString();
		for (Bar bar : after.subList(0, Math.min(100, after.size()))) {
			boolean hitStop = up ? bar.lowTicks() <= stop : bar.highTicks() >= stop;
			boolean hitTarget = up ? bar.highTicks() >= target : bar.lowTicks() <= target;
			if (hitStop && !hitTarget) {
				assertThat(told).contains("the stop").contains("−");
				return;
			}
			if (hitTarget && !hitStop) {
				assertThat(told).contains("target").contains("+");
				return;
			}
			if (hitStop) {
				return; // one candle reached both: its minutes decided, which this test does not replay
			}
		}
		assertThat(told).contains("neither level within 100 candles");
	}
}
