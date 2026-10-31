package com.tradeez.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.tradeez.AbstractIntegrationTest;

/** BUG-132 (T57): a password hashed at a lower cost than today's is hashed again when its owner signs in. */
class PasswordRehashTest extends AbstractIntegrationTest {

	private static final String PASSWORD = "a-perfectly-fine-passphrase";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@BeforeEach
	void cleanSlate() {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
	}

	private void signIn(String password, int expected) throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"old@example.com\",\"password\":\"" + password + "\"}"))
			.andExpect(status().is(expected));
	}

	@Test
	@DisplayName("an older, cheaper hash is redone at the configured cost on a right sign-in, and only then")
	void rehashedOnSignIn() throws Exception {
		users.saveAndFlush(new AppUser("old@example.com", new BCryptPasswordEncoder(4).encode(PASSWORD), "Old"));

		signIn("not-the-password-at-all", 401);
		assertThat(users.findByEmailIgnoreCase("old@example.com").orElseThrow().getPasswordHash()).startsWith("$2a$04$");

		signIn(PASSWORD, 200);
		String hash = users.findByEmailIgnoreCase("old@example.com").orElseThrow().getPasswordHash();
		assertThat(hash).startsWith("$2a$05$");
		assertThat(new BCryptPasswordEncoder().matches(PASSWORD, hash)).isTrue();

		// Signed in again at today's cost: left as it is.
		signIn(PASSWORD, 200);
		assertThat(users.findByEmailIgnoreCase("old@example.com").orElseThrow().getPasswordHash()).isEqualTo(hash);
	}
}
