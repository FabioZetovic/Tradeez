package com.tradeez.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.tradeez.AbstractIntegrationTest;

/**
 * T31 (DEC-084): one server for the page and the API. The built page here is a stand-in in
 * {@code src/test/resources/static}; the Docker build puts the real one in its place.
 */
class SpaConfigTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	@DisplayName("the page at /, and at any of its routes, signed out — the browser draws the screen")
	void routesAnswerWithThePage() throws Exception {
		for (String path : new String[] { "/learn", "/learn/ICT-20", "/practice/sessions/42", "/sign-in" }) {
			mockMvc.perform(get(path))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Tradeez test page")))
				.andExpect(header().string("Cache-Control", containsString("no-cache")));
		}
		// / is Spring's welcome page: a forward to the same file, which a real server follows (MockMvc does not).
		mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
	}

	@Test
	@DisplayName("a built asset is served as itself and kept a year; a missing one is a 404, not the page")
	void assets() throws Exception {
		mockMvc.perform(get("/assets/app-abc123.js"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("asset")))
			.andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
			.andExpect(header().string("Cache-Control", containsString("immutable")));
		mockMvc.perform(get("/assets/gone-000.js")).andExpect(status().isNotFound());
		// HEAD as GET: what an uptime check or a cache asks.
		mockMvc.perform(head("/assets/app-abc123.js")).andExpect(status().isOk());
		mockMvc.perform(head("/api/learning")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("the API is not the page: signed out it is refused, an unknown path of it is never index.html")
	void apiStaysTheApi() throws Exception {
		mockMvc.perform(get("/api/learning")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/no-such-thing")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/health")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("only reading the page is public: a write to a page path is still refused")
	void writesAreNotPublic() throws Exception {
		mockMvc.perform(post("/learn")).andExpect(status().isForbidden());
	}
}
