package com.tradeez.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.charting.ChartDrawingRepository;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;

import jakarta.servlet.http.Cookie;

/**
 * Requests the framework refuses before a controller runs are answered in words about the request (BUG-078, BUG-083),
 * and text carrying U+0000 is refused while the body is read, not by the database (BUG-079).
 *
 * <p>The statuses alone were right here before: MockMvc never forwards to {@code /error}, which is where the real
 * server turned every one of them into an empty 401. {@link com.tradeez.DeployedServerTest} checks that part.
 */
class RequestErrorsTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Autowired
	ChartDrawingRepository drawings;

	private Cookie session;

	@BeforeEach
	void signIn() throws Exception {
		drawings.deleteAllInBatch();
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		session = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"errors@example.com\",\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"E\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
	}

	private String bars(String query) throws Exception {
		return mockMvc.perform(get("/api/market/instruments/DEMO-NQ/bars?" + query).cookie(session))
			.andExpect(status().isBadRequest())
			.andReturn().getResponse().getContentAsString();
	}

	@Test
	@DisplayName("BUG-078 a body that is not JSON, the wrong content type and the wrong method each say what was wrong")
	void framework() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("not json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(
				"The request body could not be read: it is not JSON, or a value does not fit its field."));
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("hello"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.message").value("Send the body as JSON (Content-Type: application/json)."));
		mockMvc.perform(delete("/api/auth/login"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.message").value("This address does not take DELETE."));
	}

	@Test
	@DisplayName("BUG-083 a parameter of the wrong type or missing is named in words, not in the server's Java types")
	void parameters() throws Exception {
		String mismatch = bars("from=yesterday&to=1710338400");
		assertThat(mismatch).contains("'from' must be a whole number.").doesNotContain("java.");
		assertThat(bars("to=1710338400")).contains("'from' is required.");
		String badId = mockMvc.perform(get("/api/practice/sessions/not-an-id").cookie(session))
			.andExpect(status().isBadRequest())
			.andReturn().getResponse().getContentAsString();
		assertThat(badId).contains("'id' must be an id.").doesNotContain("java.");
	}

	@Test
	@DisplayName("what a parameter should have been, for each type the API takes")
	void expected() {
		assertThat(RequestErrorHandler.expected(long.class)).isEqualTo("a whole number");
		assertThat(RequestErrorHandler.expected(Integer.class)).isEqualTo("a whole number");
		assertThat(RequestErrorHandler.expected(double.class)).isEqualTo("a number");
		assertThat(RequestErrorHandler.expected(UUID.class)).isEqualTo("an id");
		assertThat(RequestErrorHandler.expected(java.time.DayOfWeek.class)).startsWith("one of [MONDAY, TUESDAY");
		assertThat(RequestErrorHandler.expected(String.class)).isEqualTo("something else");
		assertThat(RequestErrorHandler.expected(null)).isEqualTo("something else");
	}

	@Test
	@DisplayName("BUG-079 U+0000 in any string or map key is a 400 that says why, and nothing is stored")
	void nul() throws Exception {
		UUID id = UUID.randomUUID();
		// "\\u0000" is the six characters of the JSON escape, which Jackson reads as U+0000.
		for (String body : java.util.List.of(
				"{\"id\":\"%s\",\"kind\":\"label\",\"anchors\":[{\"time\":1710338400,\"priceTicks\":80000}],\"text\":\"a\\u0000b\"}",
				"{\"id\":\"%s\",\"kind\":\"horizontal-line\",\"anchors\":[{\"time\":1710338400,\"priceTicks\":80000}],"
					+ "\"style\":{\"col\\u0000or\":\"#58a6ff\"}}")) {
			mockMvc.perform(put("/api/charts/DEMO-NQ/drawings/" + id).cookie(session).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(body.formatted(id)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Text cannot contain the NUL character (U+0000)."));
		}
		assertThat(drawings.count()).isZero();
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nul@example.com\",\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"N\\u0000\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Text cannot contain the NUL character (U+0000)."));
		assertThat(users.findByEmailIgnoreCase("nul@example.com")).isEmpty();
	}
}
