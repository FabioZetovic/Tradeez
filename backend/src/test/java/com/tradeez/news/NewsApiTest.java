package com.tradeez.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** GET /api/news (T40, DEC-079): signed in only, a bounded window, and FRED's notice with every answer. */
class NewsApiTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper json;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	AppUserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	private Cookie learner;

	@BeforeEach
	void setUp() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		jdbc.sql("DELETE FROM economic_release").update();
		jdbc.sql("DELETE FROM economic_release_check").update();
		learner = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"news@example.com\",\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"N\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
	}

	private void release(String key, String at) {
		Instant instant = Instant.parse(at);
		jdbc.sql("INSERT INTO economic_release (release_key, release_date, at) VALUES (:k, :d, :at)")
			.param("k", key)
			.param("d", LocalDate.ofInstant(instant, ZoneOffset.UTC))
			.param("at", OffsetDateTime.ofInstant(instant, ZoneOffset.UTC))
			.update();
	}

	private JsonNode news(String query) throws Exception {
		return json.readTree(mockMvc.perform(get("/api/news" + query).cookie(learner))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	@Test
	@DisplayName("is for signed-in learners only")
	void signedIn() throws Exception {
		mockMvc.perform(get("/api/news")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("the releases in the window, in order, each with its agency, impact and moment; FRED's notice and terms")
	void window() throws Exception {
		release("NFP", "2026-11-06T13:30:00Z");
		release("CPI", "2026-10-14T12:30:00Z");
		release("JOLTS", "2026-11-03T15:00:00Z");
		long from = Instant.parse("2026-11-01T00:00:00Z").getEpochSecond();
		long to = Instant.parse("2026-11-08T00:00:00Z").getEpochSecond();
		JsonNode body = news("?from=" + from + "&to=" + to);

		assertThat(body.path("events").size()).isEqualTo(2);
		JsonNode jolts = body.path("events").get(0);
		assertThat(jolts.path("key").asString()).isEqualTo("JOLTS");
		assertThat(jolts.path("impact").asString()).isEqualTo("MEDIUM");
		assertThat(jolts.path("agency").asString()).isEqualTo("BLS");
		assertThat(jolts.path("at").asLong()).isEqualTo(Instant.parse("2026-11-03T15:00:00Z").getEpochSecond());
		assertThat(body.path("events").get(1).path("impact").asString()).isEqualTo("HIGH");
		assertThat(body.path("notice").asString())
			.isEqualTo("This product uses the FRED® API but is not endorsed or certified by the Federal Reserve Bank of St. Louis.");
		assertThat(body.path("termsUrl").asString()).isEqualTo("https://fred.stlouisfed.org/docs/api/terms_of_use.html");
		// Never refreshed here: the schedule says it may be out of date.
		assertThat(body.path("stale").asBoolean()).isTrue();
		assertThat(body.has("checkedAt")).isFalse();
		assertThat(body.path("fomcKnownUntil").asString()).isEqualTo("2027-12-08");
	}

	@Test
	@DisplayName("without bounds, two weeks from this week's Monday in New York")
	void defaultWindow() throws Exception {
		release("CPI", Instant.now().plusSeconds(3600).toString());
		assertThat(news("").path("events").size()).isEqualTo(1);
	}

	@Test
	@DisplayName("refuses a window that ends before it starts, or spans more than 62 days")
	void bounded() throws Exception {
		mockMvc.perform(get("/api/news?from=200&to=100").cookie(learner)).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/news?from=0&to=" + 63 * 86_400).cookie(learner)).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/news?from=0&to=" + 62 * 86_400).cookie(learner)).andExpect(status().isOk());
	}
}
