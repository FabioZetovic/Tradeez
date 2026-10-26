package com.tradeez;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Every endpoint, handed what a careless client or a curious user might send: the wrong type, a value
 * out of range, a name that does not exist, an id that is not one. The answer must be a 4xx with a
 * message — never a 500, which says the server did not expect it, and never a 2xx for nonsense.
 *
 * <p>A sweep rather than a test per endpoint, so a new endpoint is one line here (session 017's bug hunt).
 */
class RobustnessTest extends AbstractIntegrationTest {

	private static final String PASSWORD = "a-perfectly-fine-passphrase";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper json;

	@Autowired
	AppUserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	private Cookie alice;

	@BeforeEach
	void signIn() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		alice = mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"alice@example.com\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"A\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
	}

	private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
		return request.cookie(alice).with(csrf());
	}

	private static MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, String content) {
		return request.contentType(MediaType.APPLICATION_JSON).content(content);
	}

	private String practiceSession() throws Exception {
		MvcResult created = mockMvc.perform(body(as(post("/api/practice/sessions")),
				"{\"instrument\":\"DEMO-NQ\",\"timeframe\":\"5m\"}"))
			.andExpect(status().isCreated()).andReturn();
		return json.readTree(created.getResponse().getContentAsString()).at("/state/id").asString();
	}

	/** Each request, its expected 4xx, and what was actually answered when it was not. */
	private List<String> misanswered(Stream<Case> cases) throws Exception {
		List<String> wrong = new ArrayList<>();
		for (Case c : cases.toList()) {
			MvcResult result;
			try {
				result = mockMvc.perform(c.request()).andReturn();
			} catch (jakarta.servlet.ServletException unhandled) {
				// MockMvc rethrows what no handler caught: a real server answers that with a 500.
				wrong.add(c.name() + ": 500 " + unhandled.getRootCause());
				continue;
			}
			int answered = result.getResponse().getStatus();
			if (answered != c.expected()) {
				String content = result.getResponse().getContentAsString();
				wrong.add(c.name() + ": " + answered + " " + content.substring(0, Math.min(200, content.length())));
			}
		}
		return wrong;
	}

	private record Case(String name, RequestBuilder request, int expected) {
	}

	private static Case c(String name, RequestBuilder request, int expected) {
		return new Case(name, request, expected);
	}

	@Test
	@DisplayName("auth: malformed, missing and oversized input is a 400, never a 500")
	void auth() throws Exception {
		String longPassword = "x".repeat(80);
		assertThat(misanswered(Stream.of(
			c("register, no body", body(post("/api/auth/register"), ""), 400),
			c("register, not JSON", body(post("/api/auth/register"), "{nope"), 400),
			c("register, password too long for BCrypt", body(post("/api/auth/register"),
				"{\"email\":\"long@example.com\",\"password\":\"" + longPassword + "\",\"displayName\":\"L\"}"), 400),
			c("register, password of 25 emoji (100 bytes)", body(post("/api/auth/register"),
				"{\"email\":\"emoji@example.com\",\"password\":\"" + "😀".repeat(25) + "\",\"displayName\":\"E\"}"), 400),
			c("login, no body", body(post("/api/auth/login"), ""), 400),
			c("login, wrong type", body(post("/api/auth/login"), "{\"email\":1,\"password\":[]}"), 400))))
			.isEmpty();
	}

	@Test
	@DisplayName("practice: unknown names, bad ids and out-of-range numbers are 4xx, never a 500")
	void practice() throws Exception {
		String id = practiceSession();
		String base = "/api/practice/sessions/" + id;
		String missing = "/api/practice/sessions/" + UUID.randomUUID();
		assertThat(misanswered(Stream.of(
			c("create, unknown timeframe", body(as(post("/api/practice/sessions")),
				"{\"instrument\":\"DEMO-NQ\",\"timeframe\":\"7m\"}"), 400),
			c("create, unknown instrument", body(as(post("/api/practice/sessions")), "{\"instrument\":\"NOPE\"}"), 404),
			c("create, unknown killzone", body(as(post("/api/practice/sessions")),
				"{\"instrument\":\"DEMO-NQ\",\"killzone\":\"MARS\"}"), 400),
			c("create, a weekend weekday", body(as(post("/api/practice/sessions")),
				"{\"instrument\":\"DEMO-NQ\",\"weekday\":\"SUNDAY\"}"), 400),
			c("create, Saturday, when the market is shut", body(as(post("/api/practice/sessions")),
				"{\"instrument\":\"DEMO-NQ\",\"weekday\":\"SATURDAY\"}"), 400),
			c("create, Asia on a Friday evening, when it is shut", body(as(post("/api/practice/sessions")),
				"{\"instrument\":\"DEMO-NQ\",\"killzone\":\"ASIA\",\"weekday\":\"FRIDAY\"}"), 400),
			c("create, not a weekday", body(as(post("/api/practice/sessions")),
				"{\"instrument\":\"DEMO-NQ\",\"weekday\":\"FUNDAY\"}"), 400),
			c("get, not a uuid", as(get("/api/practice/sessions/not-a-uuid")), 400),
			c("get, someone else's or nobody's", as(get(missing)), 404),
			c("get, unknown timeframe", as(get(base).param("timeframe", "zz")), 400),
			c("setups, unknown detector", as(get(base + "/setups").param("detectors", "NOPE")), 400),
			c("plan, unknown direction", body(as(post(base + "/plan")),
				"{\"direction\":\"UP\",\"stopTicks\":1,\"targetTicks\":2,\"contracts\":1}"), 400),
			c("plan, an unknown model", body(as(post(base + "/plan")),
				"{\"direction\":\"LONG\",\"stopTicks\":1,\"targetTicks\":2,\"contracts\":1,\"model\":\"GUESS\"}"), 400),
			c("context, no bracket", body(as(post(base + "/context")), "{\"model\":\"OTE\"}"), 400),
			c("plan, a confidence that is not a number", body(as(post(base + "/plan")),
				"{\"direction\":\"LONG\",\"stopTicks\":1,\"targetTicks\":2,\"contracts\":1,\"confidence\":\"sure\"}"), 400),
			c("settings, a limit that is not a number", body(as(put("/api/practice/settings")), "{\"riskLimitCents\":\"lots\"}"), 400),
			c("settings, a negative limit", body(as(put("/api/practice/settings")), "{\"riskLimitCents\":-1}"), 400),
			c("settings, no body", as(put("/api/practice/settings")), 400),
			c("plan, no contracts", body(as(post(base + "/plan")),
				"{\"direction\":\"LONG\",\"stopTicks\":1,\"targetTicks\":2,\"contracts\":0}"), 400),
			c("plan, a stop past the long range", body(as(post(base + "/plan")),
				"{\"direction\":\"LONG\",\"stopTicks\":" + Long.MIN_VALUE + ",\"targetTicks\":2,\"contracts\":1}"), 400),
			c("advance, zero bars", body(as(post(base + "/advance")), "{\"bars\":0}"), 400),
			c("advance, unknown timeframe", body(as(post(base + "/advance")), "{\"bars\":1,\"timeframe\":\"zz\"}"), 400),
			c("skip, not in a trade", as(post(base + "/skip")), 409),
			c("review, a trade number that is not a number", as(get(base + "/trades/first/review")), 400),
			c("review, no such trade", as(get(base + "/trades/1/review")), 404),
			c("review, nobody's session", as(get(missing + "/trades/1/review")), 404),
			c("close, not in a trade", as(post(base + "/close")), 409),
			c("end, nobody's session", as(post(missing + "/end")), 404))))
			.isEmpty();
	}

	@Test
	@DisplayName("BUG-067 an impossible filter is refused with its reason, before any search")
	void impossibleFilters() throws Exception {
		mockMvc.perform(body(as(post("/api/practice/sessions")),
				"{\"instrument\":\"DEMO-NQ\",\"killzone\":\"ASIA\",\"weekday\":\"FRIDAY\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("The market shuts at 17:00 on Fridays, so no Asia killzone falls on a Friday."));
		mockMvc.perform(body(as(post("/api/practice/sessions")), "{\"instrument\":\"DEMO-NQ\",\"weekday\":\"SATURDAY\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Practice weekdays are Monday to Friday."));
	}

	@Test
	@DisplayName("BUG-071 a stop so far below the price that the distance overflows is refused like any other")
	void bracketOverflow() throws Exception {
		MvcResult created = mockMvc.perform(body(as(post("/api/practice/sessions")),
				"{\"instrument\":\"DEMO-NQ\",\"timeframe\":\"5m\"}"))
			.andExpect(status().isCreated()).andReturn();
		JsonNode session = json.readTree(created.getResponse().getContentAsString());
		JsonNode bars = session.get("bars");
		long reference = bars.get(bars.size() - 1).get("close").asLong();
		// stop - reference is exactly Long.MIN_VALUE, whose Math.abs is negative: it passed the 20,000-tick check.
		mockMvc.perform(body(as(post("/api/practice/sessions/" + session.at("/state/id").asString() + "/plan")),
				"{\"direction\":\"LONG\",\"stopTicks\":%d,\"targetTicks\":%d,\"contracts\":1}"
					.formatted(Long.MIN_VALUE + reference, reference + 40)))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("market data, clock and setups: bad ranges and unknown names are 4xx, never a 500")
	void market() throws Exception {
		String nq = "/api/market/instruments/NQ";
		assertThat(misanswered(Stream.of(
			c("instrument, unknown", as(get("/api/market/instruments/NOPE")), 404),
			c("bars, unknown timeframe", as(get(nq + "/bars").param("timeframe", "zz").param("from", "0").param("to", "60")), 400),
			c("bars, from after to", as(get(nq + "/bars").param("from", "1000").param("to", "0")), 400),
			c("bars, from not a number", as(get(nq + "/bars").param("from", "x").param("to", "0")), 400),
			c("bars, no from", as(get(nq + "/bars").param("to", "0")), 400),
			c("bars, a seed", as(get(nq + "/bars").param("from", "0").param("to", "60").param("seed", "1")), 400),
			c("bars, beyond what an Instant holds", as(get(nq + "/bars").param("from", "0")
				.param("to", Long.toString(Long.MAX_VALUE))), 400),
			c("clock, from after to", as(get(nq + "/clock").param("from", "1000").param("to", "0")), 400),
			c("clock, beyond what an Instant holds", as(get(nq + "/clock").param("from", "0")
				.param("to", Long.toString(Long.MAX_VALUE))), 400),
			c("setups, unknown detector", as(get(nq + "/setups").param("timeframe", "5m").param("from", "0")
				.param("to", "60").param("detectors", "NOPE")), 400),
			c("setups, beyond what an Instant holds", as(get(nq + "/setups").param("timeframe", "5m").param("from", "0")
				.param("to", Long.toString(Long.MAX_VALUE)).param("detectors", "FVG")), 400),
			c("bars, more than 400 days", as(get(nq + "/bars").param("timeframe", "1h").param("from", "0")
				.param("to", Long.toString(401L * 86_400))), 400),
			c("bars, before 1970", as(get(nq + "/bars").param("from", "-60").param("to", "60")), 400),
			c("setups, from after to", as(get(nq + "/setups").param("timeframe", "5m").param("from", "1000")
				.param("to", "0")), 400))))
			.isEmpty();
	}

	@Test
	@DisplayName("drawings, drills, lessons and learning: bad input is a 4xx, never a 500")
	void others() throws Exception {
		UUID id = UUID.randomUUID();
		String drawing = "/api/charts/NQ/drawings/" + id;
		String line = "{\"id\":\"" + id + "\",\"kind\":\"horizontal-line\",\"anchors\":[{\"time\":1710338400,\"priceTicks\":1}]}";
		assertThat(misanswered(Stream.of(
			c("drawing, ids differ", body(as(put(drawing)), line.replace(id.toString(), UUID.randomUUID().toString())), 400),
			c("drawing, unknown kind", body(as(put(drawing)), line.replace("horizontal-line", "circle")), 400),
			c("drawing, a null anchor time", body(as(put(drawing)), line.replace("\"time\":1710338400", "\"time\":null")), 400),
			c("drawing, unknown instrument", body(as(put("/api/charts/NOPE/drawings/" + id)), line), 404),
			c("drawing, delete on an unknown instrument", as(delete("/api/charts/NOPE/drawings/" + id)), 404),
			c("drill, unknown kind", body(as(post("/api/drills")), "{\"kind\":\"NOPE\"}"), 400),
			c("drill, not a uuid", as(get("/api/drills/not-a-uuid")), 400),
			c("drill, nobody's", as(get("/api/drills/" + UUID.randomUUID())), 404),
			c("risk drill, unknown kind", body(as(post("/api/drills/risk")), "{\"kind\":\"NOPE\"}"), 400),
			c("risk drill, nobody's", as(get("/api/drills/risk/" + UUID.randomUUID())), 404),
			c("risk drill, an answer that is not a number", body(as(post("/api/drills/risk/" + UUID.randomUUID() + "/answer")),
				"{\"value\":\"two\"}"), 400),
			c("risk drill, no answer", body(as(post("/api/drills/risk/" + UUID.randomUUID() + "/answer")), "{}"), 400),
			c("drill answer, a null mark", body(as(post("/api/drills/" + UUID.randomUUID() + "/answer")),
				"{\"marks\":[null]}"), 404),
			c("lesson, unknown concept", as(get("/api/concepts/NOPE/lesson")), 404),
			c("learning, unknown concept read", as(post("/api/learning/concepts/NOPE/read")), 404))))
			.isEmpty();
	}

	@Test
	@DisplayName("every endpoint but the public ones refuses a caller without a session")
	void anonymous() throws Exception {
		String uuid = UUID.randomUUID().toString();
		List<String> open = new ArrayList<>();
		for (MockHttpServletRequestBuilder request : List.of(
				get("/api/me"),
				get("/api/market/instruments"),
				get("/api/market/instruments/NQ"),
				get("/api/market/instruments/NQ/bars").param("from", "0").param("to", "60"),
				get("/api/market/instruments/NQ/clock").param("from", "0").param("to", "60"),
				get("/api/market/instruments/NQ/setups").param("from", "0").param("to", "60"),
				get("/api/charts/NQ/drawings"),
				put("/api/charts/NQ/drawings/" + uuid),
				delete("/api/charts/NQ/drawings/" + uuid),
				get("/api/concepts"),
				get("/api/concepts/ICT-20/lesson"),
				get("/api/drills/kinds"),
				post("/api/drills"),
				get("/api/drills/" + uuid),
				post("/api/drills/" + uuid + "/answer"),
				post("/api/drills/risk"),
				get("/api/drills/risk/" + uuid),
				post("/api/drills/risk/" + uuid + "/answer"),
				get("/api/learning"),
				get("/api/news"),
				post("/api/learning/concepts/ICT-20/read"),
				get("/api/practice/sessions"),
				post("/api/practice/sessions"),
				get("/api/practice/sessions/" + uuid),
				get("/api/practice/sessions/" + uuid + "/clock"),
				get("/api/practice/sessions/" + uuid + "/setups").param("detectors", "FVG"),
				post("/api/practice/sessions/" + uuid + "/plan"),
				post("/api/practice/sessions/" + uuid + "/context"),
				post("/api/practice/sessions/" + uuid + "/advance"),
				post("/api/practice/sessions/" + uuid + "/skip"),
				post("/api/practice/sessions/" + uuid + "/close"),
				post("/api/practice/sessions/" + uuid + "/end"),
				get("/api/practice/sessions/" + uuid + "/trades/1/review"),
				get("/api/practice/calibration"),
				get("/api/practice/statistics"),
				get("/api/practice/trades"),
				get("/api/practice/settings"),
				put("/api/practice/settings"),
				post("/api/auth/logout-all"),
				post("/api/auth/password"))) {
			int answered = mockMvc.perform(request.with(csrf())).andReturn().getResponse().getStatus();
			if (answered != 401) {
				open.add(request.buildRequest(new org.springframework.mock.web.MockServletContext()).getMethod() + " "
					+ request.buildRequest(new org.springframework.mock.web.MockServletContext()).getRequestURI() + " → "
					+ answered);
			}
		}
		assertThat(open).isEmpty();
		// And the public ones answer without one.
		mockMvc.perform(get("/api/health")).andExpect(status().is2xxSuccessful());
		JsonNode docs = json.readTree(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString());
		// The sweep above covers every path the API publishes, so a new endpoint cannot be forgotten.
		List<String> paths = new ArrayList<>();
		docs.get("paths").propertyNames().forEach(paths::add);
		assertThat(paths).containsExactlyInAnyOrder("/api/me", "/api/market/instruments", "/api/market/instruments/{symbol}",
			"/api/market/instruments/{symbol}/bars", "/api/market/instruments/{symbol}/clock",
			"/api/market/instruments/{symbol}/setups", "/api/charts/{symbol}/drawings", "/api/charts/{symbol}/drawings/{id}",
			"/api/concepts", "/api/concepts/{id}/lesson", "/api/drills/kinds", "/api/drills", "/api/drills/{id}",
			"/api/drills/{id}/answer", "/api/drills/risk", "/api/drills/risk/{id}", "/api/drills/risk/{id}/answer", "/api/learning", "/api/learning/concepts/{id}/read", "/api/news", "/api/practice/sessions",
			"/api/practice/sessions/{id}", "/api/practice/sessions/{id}/clock", "/api/practice/sessions/{id}/setups",
			"/api/practice/sessions/{id}/plan", "/api/practice/sessions/{id}/context", "/api/practice/sessions/{id}/advance", "/api/practice/sessions/{id}/skip",
			"/api/practice/sessions/{id}/close", "/api/practice/sessions/{id}/end", "/api/practice/sessions/{id}/trades/{number}/review", "/api/practice/calibration", "/api/practice/statistics", "/api/practice/trades",
			"/api/practice/settings", "/api/auth/register", "/api/auth/login",
			"/api/auth/refresh", "/api/auth/logout", "/api/auth/logout-all", "/api/auth/password", "/api/health");
	}
}
