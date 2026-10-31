package com.tradeez.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.tradeez.AbstractIntegrationTest;

import jakarta.servlet.http.Cookie;

/** BUG-120 (T57): a person can take everything the app holds about them, and delete all of it. */
class AccountDataTest extends AbstractIntegrationTest {

	private static final String PASSWORD = "a-perfectly-fine-passphrase";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Autowired
	JdbcTemplate jdbc;

	private Cookie alice;
	private Cookie bob;

	@BeforeEach
	void twoAccountsWithData() throws Exception {
		jdbc.update("DELETE FROM chart_drawing");
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		alice = register("alice@example.com");
		bob = register("bob@example.com");
		for (Cookie who : List.of(alice, bob)) {
			UUID drawing = UUID.randomUUID();
			mockMvc.perform(put("/api/charts/DEMO-NQ/drawings/" + drawing).cookie(who).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"id\":\"" + drawing + "\",\"kind\":\"horizontal-line\","
						+ "\"anchors\":[{\"time\":1710338400,\"priceTicks\":80000}],\"style\":{\"color\":\"#58a6ff\"}}"))
				.andExpect(status().isCreated());
			mockMvc.perform(post("/api/learning/concepts/ICT-01/read").cookie(who).with(csrf())).andExpect(status().is2xxSuccessful());
			mockMvc.perform(put("/api/practice/settings").cookie(who).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"riskLimitCents\":50000}")).andExpect(status().isOk());
			mockMvc.perform(post("/api/drills/risk").cookie(who).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"STREAK\"}")).andExpect(status().isCreated());
		}
	}

	private Cookie register(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"P\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
	}

	private UUID id(String email) {
		return users.findByEmailIgnoreCase(email).orElseThrow().getId();
	}

	/** Every table whose rows belong to an account, through any chain of foreign keys down from app_user. */
	private List<String> accountTables() {
		return jdbc.queryForList("""
			WITH RECURSIVE owned(name) AS (
			    SELECT 'app_user'::name
			  UNION
			    SELECT child.relname
			    FROM pg_constraint c
			    JOIN pg_class child ON child.oid = c.conrelid
			    JOIN pg_class parent ON parent.oid = c.confrelid
			    JOIN owned ON owned.name = parent.relname
			    WHERE c.contype = 'f' AND child.relname <> parent.relname
			)
			SELECT name::text FROM owned ORDER BY 1""", String.class);
	}

	@Test
	@DisplayName("the export holds the account's own data, in every section, never a hash, never another account's")
	void export() throws Exception {
		var response = mockMvc.perform(get("/api/me/export").cookie(alice))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment; filename=\"tradeez-data-")))
			.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
			.andReturn().getResponse();
		String body = response.getContentAsString();
		assertThat(body).contains("alice@example.com", "\"drawings\":[{", "\"riskDrills\":[{", "\"lessonsRead\":[{",
			"\"practiceSettings\":[{", "\"signIns\":[{").doesNotContain("bob@example.com", id("bob@example.com").toString())
			.doesNotContain("password_hash", "token_hash", "$2a$");
		// JSON columns come out as JSON, not as the driver's wrapper.
		assertThat(body).contains("\"anchors\":[{\"time\":1710338400").doesNotContain("\"type\":\"jsonb\"");
		mockMvc.perform(get("/api/me/export")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("every table that belongs to an account is in the export — a new one fails this until it is added")
	void exportCoversEveryTable() {
		String queries = String.join(" ", AccountExport.SECTIONS.values());
		for (String table : accountTables()) {
			assertThat(queries).as("the export reads " + table).containsPattern("\\b" + table + "\\b");
		}
	}

	@Test
	@DisplayName("deleting needs the password and the CSRF token; then nothing of the account is left, and the other is untouched")
	void delete_() throws Exception {
		UUID aliceId = id("alice@example.com");
		mockMvc.perform(delete("/api/me").cookie(alice).contentType(MediaType.APPLICATION_JSON)
			.content("{\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/me").cookie(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"password\":\"not-the-password-at-all\"}")).andExpect(status().isForbidden());
		assertThat(users.existsById(aliceId)).isTrue();

		long bobsRowsBefore = rowsOf(id("bob@example.com"));
		mockMvc.perform(delete("/api/me").cookie(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isNoContent())
			.andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("Max-Age=0"))));

		assertThat(rowsOf(aliceId)).isZero();
		assertThat(rowsOf(id("bob@example.com"))).isEqualTo(bobsRowsBefore).isPositive();
		mockMvc.perform(get("/api/me").cookie(alice)).andExpect(status().isUnauthorized());
		// The address is free again.
		register("alice@example.com");
	}

	/** Every row in every section of the export for this account: what it would hand over, so what deletion must clear. */
	private long rowsOf(UUID userId) {
		return AccountExport.SECTIONS.values().stream()
			.mapToLong(sql -> jdbc.queryForList(sql, userId).size())
			.sum();
	}
}
