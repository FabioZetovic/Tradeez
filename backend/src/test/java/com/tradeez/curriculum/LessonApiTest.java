package com.tradeez.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tradeez.AbstractIntegrationTest;
import com.tradeez.ThreeWeeksOfNq;
import com.tradeez.evidence.EvidenceService;
import com.tradeez.evidence.EvidenceText;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketDataService;

import jakarta.servlet.http.Cookie;

/**
 * T14 (DEC-055) — lesson cards: the concept from the curriculum, its rules in words, the drills that
 * practise it, and real examples its rule found at random, on {@link ThreeWeeksOfNq}.
 */
class LessonApiTest extends AbstractIntegrationTest {

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

	@Autowired
	EvidenceService evidence;

	private Cookie alice;

	@BeforeEach
	void importThreeWeeks() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		alice = mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"alice@example.com\",\"password\":\"a-perfectly-fine-passphrase\","
					+ "\"displayName\":\"A\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
		if (ThreeWeeksOfNq.present(marketData)) {
			return; // imported by an earlier test in this class
		}
		jdbc.update("DELETE FROM drill_setup");
		jdbc.update("DELETE FROM drill");
		jdbc.update("DELETE FROM detected_setup");
		jdbc.update("DELETE FROM market_bar");
		jdbc.update("DELETE FROM market_data_import");
		ThreeWeeksOfNq.insert(marketData, barStore, jdbc);
	}

	private ResultActions request(String id, String timeframe) throws Exception {
		var request = get("/api/concepts/" + id + "/lesson").cookie(alice);
		return mockMvc.perform(timeframe == null ? request : request.param("timeframe", timeframe));
	}

	private JsonNode lesson(String id) throws Exception {
		return json.readTree(request(id, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private static List<Long> times(JsonNode example) {
		List<Long> times = new ArrayList<>();
		example.get("bars").forEach(b -> times.add(b.get("time").asLong()));
		return times;
	}

	@Test
	@DisplayName("a lesson comes with its words (T45): summary, what it is, how to spot it, how it is traded; and its topic")
	void words() throws Exception {
		JsonNode ifvg = lesson("ICT-23");
		assertThat(ifvg.get("kind").asString()).isEqualTo("LESSON");
		assertThat(ifvg.get("topic").asString()).isEqualTo("Gaps & imbalances");
		assertThat(ifvg.has("into")).isFalse();
		assertThat(ifvg.at("/text/summary").asString()).startsWith("A fair value gap that failed");
		assertThat(ifvg.at("/text/whatItIs").asString()).contains("inversion fair value gap");
		assertThat(ifvg.at("/text/howToSpot")).hasSize(3);
		assertThat(ifvg.at("/text/howToTrade").asString()).isNotBlank();
		// A merged concept names the lesson it is a section of, and has no page of its own.
		JsonNode mss = lesson("ICT-05");
		assertThat(mss.get("kind").asString()).isEqualTo("MERGED");
		assertThat(mss.get("into").asString()).isEqualTo("ICT-06");
		assertThat(mss.has("text")).isFalse();
		assertThat(lesson("ICT-80").at("/text/howToTrade").asString()).contains("Stop beyond the sweep");
	}

	@Test
	@DisplayName("a card: the concept from the curriculum, its rule in words, its drill, and three real examples")
	void card() throws Exception {
		mockMvc.perform(get("/api/concepts/ICT-20/lesson")).andExpect(status().isUnauthorized());
		JsonNode card = lesson("ICT-20");
		assertThat(card.get("name").asString()).contains("Fair value gap");
		assertThat(card.get("status").asString()).isEqualTo("IMPLEMENTED");
		assertThat(card.get("summary").asString()).contains("Three consecutive candles");
		assertThat(card.at("/rules/0/version").asString()).isEqualTo("fvg-1.0.0");
		assertThat(card.at("/rules/0/definition").asString()).contains("25%").contains("14 candles");
		assertThat(card.get("shownOn").asString()).isEqualTo("SETUPS");
		assertThat(card.get("drills").findValuesAsString("kind")).containsExactly("FVG_IDENTIFICATION");
		assertThat(card.get("timeframe").asString()).isEqualTo("5m");
		assertThat(card.has("examplesNote")).isFalse();

		JsonNode examples = card.get("examples");
		assertThat(examples).hasSize(LessonService.EXAMPLES);
		List<long[]> spans = new ArrayList<>();
		for (JsonNode example : examples) {
			List<Long> times = times(example);
			JsonNode setup = example.get("setup");
			assertThat(example.get("detector").asString()).isEqualTo("FVG");
			assertThat(example.get("label").asString()).isIn("Bullish gap", "Bearish gap");
			assertThat(example.get("explanation")).isNotEmpty();
			assertThat(example.at("/rule/version").asString()).isEqualTo("fvg-1.0.0");
			assertThat(times).hasSizeLessThanOrEqualTo(LessonService.BEFORE + LessonService.AFTER)
				.as("the candle it is known by is on the chart").contains(setup.get("end").asLong())
				.as("so is its whole pattern").contains(setup.get("start").asLong(), setup.get("anchor").asLong());
			assertThat(setup.get("confirmedAt").asLong()).isLessThanOrEqualTo(times.getLast() + 300);
			spans.add(new long[] { times.getFirst(), times.getLast() });
		}
		// Each example is its own stretch of history.
		for (int i = 0; i < spans.size(); i++) {
			for (int j = i + 1; j < spans.size(); j++) {
				assertThat(spans.get(i)[1] < spans.get(j)[0] || spans.get(j)[1] < spans.get(i)[0]).isTrue();
			}
		}
	}

	@Test
	@DisplayName("examples are found at random, not picked: asking again shows others")
	void notHandPicked() throws Exception {
		Set<Long> first = new HashSet<>(lesson("ICT-20").get("examples").findValuesAsString("anchor").stream()
			.map(Long::valueOf).toList());
		Set<Long> again = new HashSet<>(lesson("ICT-20").get("examples").findValuesAsString("anchor").stream()
			.map(Long::valueOf).toList());
		assertThat(again).isNotEqualTo(first);
	}

	@ParameterizedTest
	@ValueSource(strings = { "ICT-01", "ICT-02", "ICT-03", "ICT-04", "ICT-05", "ICT-06", "ICT-10", "ICT-12", "ICT-13",
		"ICT-14", "ICT-22", "ICT-23", "ICT-24", "ICT-25", "ICT-26", "ICT-27", "ICT-28" })
	@DisplayName("every detected concept shows real examples, each explained from its own stored evidence")
	void everyDetectedConcept(String id) throws Exception {
		JsonNode card = lesson(id);
		assertThat(card.get("rules")).isNotEmpty();
		assertThat(card.get("examples")).as(id).isNotEmpty();
		for (JsonNode example : card.get("examples")) {
			assertThat(example.get("explanation")).isNotEmpty();
			assertThat(example.get("explanation").toString()).doesNotContain("null");
			assertThat(times(example)).contains(example.at("/setup/end").asLong());
		}
	}

	@Test
	@DisplayName("a concept with no rule yet says so; one drawn by the clock says where")
	void undetected() throws Exception {
		JsonNode implied = lesson("ICT-29");
		assertThat(implied.get("rules")).isEmpty();
		assertThat(implied.get("examples")).isEmpty();
		assertThat(implied.has("shownOn")).isFalse();
		assertThat(implied.get("examplesNote").asString()).contains("does not detect");

		JsonNode killzones = lesson("ICT-52");
		assertThat(killzones.get("shownOn").asString()).isEqualTo("CLOCK");
		assertThat(killzones.get("examplesNote").asString()).contains("New York clock layer");
	}

	@Test
	@DisplayName("prerequisites and what a concept unlocks agree with each other")
	void links() throws Exception {
		// Turtle soup is a sweep (ICT-13) of equal highs or lows (ICT-12), and its section says so.
		JsonNode soup = lesson("ICT-14");
		assertThat(soup.get("prerequisites").findValuesAsString("id")).contains("ICT-12", "ICT-13");
		assertThat(lesson("ICT-13").get("unlocks").findValuesAsString("id")).contains("ICT-14");
	}

	@Test
	@DisplayName("refuses an unknown concept and a timeframe it does not show")
	void refusals() throws Exception {
		request("ICT-99", null).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value(Matchers.containsString("ICT-99")));
		request("ICT-20", "1h").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(Matchers.containsString("[1m, 5m, 15m]")));
		JsonNode oneMinute = json.readTree(request("ICT-20", "1m").andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString());
		List<Long> times = times(oneMinute.at("/examples/0"));
		assertThat(times.get(1) - times.get(0)).isEqualTo(60);
	}

	@Test
	@DisplayName("T21: each rule carries what it found over the whole history, per timeframe, once counted")
	void evidence() throws Exception {
		jdbc.update("DELETE FROM rule_evidence");
		assertThat(lesson("ICT-22").at("/rules/0/evidence")).isEmpty();

		evidence.refresh(marketData.requireInstrument("NQ"));
		JsonNode counted = lesson("ICT-22").at("/rules/0/evidence");
		assertThat(counted.findValuesAsString("timeframe")).containsExactly("1m", "5m", "15m", "1h");
		JsonNode fiveMinutes = counted.get(1);
		assertThat(fiveMinutes.get("version").asString()).isEqualTo("evidence-1.0.0");
		assertThat(fiveMinutes.get("hits").asLong()).isPositive();
		assertThat(fiveMinutes.get("to").asLong()).isGreaterThan(fiveMinutes.get("from").asLong());
		JsonNode lines = fiveMinutes.get("lines");
		assertThat(lines.get(0).asString()).startsWith("NQ, 5-minute candles, 27 Apr 2025 to");
		assertThat(lines.get(1).asString()).contains("gaps — about");
		assertThat(lines.get(2).asString()).startsWith("Of the ").contains("whose life ended within the data");
		assertThat(lines.get(lines.size() - 1).asString()).isEqualTo(EvidenceText.NOT_A_FORECAST);
		// A concept read by a clock rule, not a detector, has no rule card to count.
		assertThat(lesson("ICT-50").get("rules")).isEmpty();
		// A model, or the daily bias, is read in Practice when a trade is planned (T22).
		assertThat(lesson("ICT-70").get("examplesNote").asString()).startsWith("Read in Practice");
		assertThat(lesson("ICT-61").get("examplesNote").asString()).startsWith("Read in Practice");
	}

	@Test
	@DisplayName("with no real history imported, the card still reads — without examples, and says why")
	void nothingImported() throws Exception {
		jdbc.update("DELETE FROM market_bar");
		JsonNode card = lesson("ICT-20");
		assertThat(card.get("examples")).isEmpty();
		assertThat(card.get("examplesNote").asString()).contains("No real NQ history");
		assertThat(card.get("rules")).isNotEmpty();
	}

	@Test
	@DisplayName("a lesson changes nothing it is not meant to: no drill, no csrf needed to read it")
	void readOnly() throws Exception {
		lesson("ICT-13");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM drill", Integer.class)).isZero();
		mockMvc.perform(post("/api/concepts/ICT-13/lesson").cookie(alice).with(csrf()))
			.andExpect(status().isMethodNotAllowed());
	}
}
