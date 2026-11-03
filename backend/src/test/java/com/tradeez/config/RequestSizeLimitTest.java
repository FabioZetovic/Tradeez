package com.tradeez.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.tradeez.AbstractIntegrationTest;

/** BUG-123 (T57): a body past the limit is refused before anything reads it. */
class RequestSizeLimitTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	private static String login(int passwordLength) {
		return "{\"email\":\"someone@example.com\",\"password\":\"" + "x".repeat(passwordLength) + "\"}";
	}

	@Test
	@DisplayName("a sign-in of a megabyte is 413, signed out; one just under the limit is read and judged as usual")
	void tooLarge() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(1024 * 1024)))
			.andExpect(status().isPayloadTooLarge());
		// Under the limit: read, and refused by the field's own limit (1000 characters), not by size.
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(login(RequestSizeLimitFilter.MAX_BODY - 100)))
			.andExpect(status().isBadRequest());
		// An ordinary wrong sign-in is untouched.
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(20)))
			.andExpect(status().isUnauthorized());
	}
}
