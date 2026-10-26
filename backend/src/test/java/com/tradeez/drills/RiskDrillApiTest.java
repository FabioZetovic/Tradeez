package com.tradeez.drills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * T26 (DEC-070), mode F through the API: a risk question asked, its answer withheld until it is answered, answered
 * once, graded within its tolerance, and nobody else's to read.
 */
class RiskDrillApiTest extends AbstractIntegrationTest {

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

	private Cookie alice;
	private Cookie bob;

	@BeforeEach
	void users() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		alice = register("alice@example.com");
		bob = register("bob@example.com");
	}

	private Cookie register(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"T\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
	}

	private JsonNode read(ResultActions actions) throws Exception {
		return json.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private ResultActions ask(Cookie who, String body) throws Exception {
		return mockMvc.perform(post("/api/drills/risk").cookie(who).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions answer(Cookie who, String id, String body) throws Exception {
		return mockMvc.perform(post("/api/drills/risk/" + id + "/answer").cookie(who).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private double stored(String id) {
		return jdbc.queryForObject("SELECT answer FROM risk_drill WHERE id = ?::uuid", Double.class, id);
	}

	@Test
	@DisplayName("asked with no answer in sight, answered once and graded, the working and the record with it")
	void asked() throws Exception {
		String raw = ask(alice, "{}").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		JsonNode drill = json.readTree(raw);
		assertThat(drill.get("kind").asString()).isEqualTo("CONTRACTS");
		assertThat(drill.get("version").asString()).isEqualTo("risk-1.0.0");
		assertThat(drill.get("unit").asString()).isEqualTo("contracts");
		assertThat(drill.get("limitSeconds").asInt()).isEqualTo(30);
		assertThat(drill.get("text").asString()).startsWith("An account of $");
		// Nothing of the answer before it is given: no result, no working, no number to read off.
		assertThat(drill.has("result")).isFalse();
		assertThat(raw).doesNotContain("working").doesNotContain("answer");
		String id = drill.get("id").asString();
		assertThat(read(mockMvc.perform(get("/api/drills/risk/" + id).cookie(alice))).has("result")).isFalse();
		mockMvc.perform(get("/api/drills/risk/" + id).cookie(bob)).andExpect(status().isNotFound());
		answer(bob, id, "{\"value\":1}").andExpect(status().isNotFound());

		double right = stored(id);
		JsonNode result = read(answer(alice, id, "{\"value\":" + right + "}").andExpect(status().isOk())).get("result");
		assertThat(result.get("correct").asBoolean()).isTrue();
		assertThat(result.get("answer").asDouble()).isEqualTo(right);
		assertThat(result.get("seconds").asDouble()).isGreaterThanOrEqualTo(0);
		assertThat(result.get("inTime").asBoolean()).isTrue();
		assertThat(result.get("working").size()).isGreaterThanOrEqualTo(3);
		assertThat(result.get("answered").asInt()).isEqualTo(1);
		assertThat(result.get("right").asInt()).isEqualTo(1);
		// Once.
		answer(alice, id, "{\"value\":" + right + "}").andExpect(status().isConflict());

		// A wrong one, a whole contract off, counts against the record.
		String second = read(ask(alice, "{\"kind\":\"CONTRACTS\"}")).get("id").asString();
		JsonNode wrong = read(answer(alice, second, "{\"value\":" + (stored(second) + 1) + "}").andExpect(status().isOk()))
			.get("result");
		assertThat(wrong.get("correct").asBoolean()).isFalse();
		assertThat(wrong.get("answered").asInt()).isEqualTo(2);
		assertThat(wrong.get("right").asInt()).isEqualTo(1);
	}

	@Test
	@DisplayName("a streak is graded within half a point, each kind keeps its own record, and nonsense is refused")
	void kinds() throws Exception {
		String id = read(ask(alice, "{\"kind\":\"streak\"}").andExpect(status().isCreated())).get("id").asString();
		JsonNode result = read(answer(alice, id, "{\"value\":" + (stored(id) + 0.5) + "}").andExpect(status().isOk()))
			.get("result");
		assertThat(result.get("correct").asBoolean()).isTrue();
		assertThat(result.get("answered").asInt()).isEqualTo(1);
		JsonNode recovery = read(ask(alice, "{\"kind\":\"RECOVERY\"}").andExpect(status().isCreated()));
		assertThat(recovery.get("unit").asString()).isEqualTo("%");

		ask(alice, "{\"kind\":\"GUESS\"}").andExpect(status().isBadRequest());
		answer(alice, recovery.get("id").asString(), "{}").andExpect(status().isBadRequest());
		answer(alice, recovery.get("id").asString(), "{\"value\":\"many\"}").andExpect(status().isBadRequest());
		answer(alice, UUID.randomUUID().toString(), "{\"value\":1}").andExpect(status().isNotFound());
		// The database holds an answer together: never a value without its time and its grade.
		assertThatThrownBy(() -> jdbc.update("UPDATE risk_drill SET given = 1 WHERE id = ?::uuid", recovery.get("id").asString()))
			.isInstanceOf(DataIntegrityViolationException.class);

		// A recovery question's record is its own: the streak answered before it is not in it.
		String another = read(ask(alice, "{\"kind\":\"RECOVERY\"}")).get("id").asString();
		JsonNode own = read(answer(alice, another, "{\"value\":" + stored(another) + "}").andExpect(status().isOk()))
			.get("result");
		assertThat(own.get("answered").asInt()).isEqualTo(1);
		assertThat(own.get("right").asInt()).isEqualTo(1);
	}

	@Test
	@DisplayName("the kinds, in order, each with its clock and the learner's own record — unanswered questions left out")
	void record() throws Exception {
		JsonNode none = read(mockMvc.perform(get("/api/drills/risk/kinds").cookie(alice)).andExpect(status().isOk()));
		assertThat(none.toString()).isEqualTo("[{\"kind\":\"CONTRACTS\",\"limitSeconds\":30,\"answered\":0,\"right\":0},"
			+ "{\"kind\":\"STREAK\",\"limitSeconds\":45,\"answered\":0,\"right\":0},"
			+ "{\"kind\":\"RECOVERY\",\"limitSeconds\":30,\"answered\":0,\"right\":0}]");

		String first = read(ask(alice, "{\"kind\":\"STREAK\"}")).get("id").asString();
		answer(alice, first, "{\"value\":" + stored(first) + "}").andExpect(status().isOk());
		String second = read(ask(alice, "{\"kind\":\"STREAK\"}")).get("id").asString();
		answer(alice, second, "{\"value\":" + (stored(second) + 5) + "}").andExpect(status().isOk());
		ask(alice, "{\"kind\":\"STREAK\"}").andExpect(status().isCreated());
		String bobs = read(ask(bob, "{\"kind\":\"RECOVERY\"}")).get("id").asString();
		answer(bob, bobs, "{\"value\":" + stored(bobs) + "}").andExpect(status().isOk());

		JsonNode kinds = read(mockMvc.perform(get("/api/drills/risk/kinds").cookie(alice)));
		assertThat(kinds.get(1).get("answered").asInt()).isEqualTo(2);
		assertThat(kinds.get(1).get("right").asInt()).isEqualTo(1);
		assertThat(kinds.get(2).get("answered").asInt()).isZero();
		assertThat(kinds.get(0).get("answered").asInt()).isZero();
	}
}
