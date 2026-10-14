package com.tradeez;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.tradeez.charting.ChartDrawingRepository;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;

import jakarta.servlet.http.Cookie;

/**
 * The drawings API (task 5).
 *
 * <p>The isolation tests are the ones that matter. DEC-022 makes every user-scoped endpoint
 * responsible for proving a client cannot reach another account's data, and drawings are the first
 * feature where that is more than theoretical.
 */
class ChartDrawingApiTest extends AbstractIntegrationTest {

	private static final String PASSWORD = "a-perfectly-fine-passphrase";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Autowired
	ChartDrawingRepository drawings;

	private Cookie alice;
	private Cookie bob;

	@BeforeEach
	void signInTwoUsers() throws Exception {
		drawings.deleteAllInBatch();
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();

		alice = registerAndGetCookie("alice@example.com");
		bob = registerAndGetCookie("bob@example.com");
	}

	private Cookie registerAndGetCookie(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
					+ "\",\"displayName\":\"T\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
	}

	/** Anchors per kind, as the client draws them (DEC-048 checks the count). */
	private static final java.util.Map<String, Integer> ANCHORS = java.util.Map.of(
		"horizontal-line", 1, "label", 1, "position", 3);

	/** A drawing of {@code kind} with every anchor at one point — enough for the API. */
	private String body(UUID id, String kind, long time, long ticks) {
		return body(id, kind, ANCHORS.getOrDefault(kind, 2), time, ticks, null);
	}

	private String body(UUID id, String kind, int anchors, long time, long ticks, String text) {
		String anchor = "{\"time\":%d,\"priceTicks\":%d}".formatted(time, ticks);
		return """
			{"id":"%s","kind":"%s","anchors":[%s],"style":{"color":"#58a6ff"}%s}"""
			.formatted(id, kind, String.join(",", java.util.Collections.nCopies(anchors, anchor)),
				text == null ? "" : ",\"text\":" + json(text));
	}

	private static String json(String text) {
		return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private org.springframework.test.web.servlet.ResultActions save(String content, UUID id)
			throws Exception {
		return mockMvc.perform(put("/api/charts/DEMO-NQ/drawings/" + id).cookie(alice).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(content));
	}

	@Test
	@DisplayName("DEC-048: the ICT tools save — a Fibonacci, a named zone, a label with its words")
	void ictTools() throws Exception {
		UUID fib = UUID.randomUUID();
		UUID zone = UUID.randomUUID();
		UUID label = UUID.randomUUID();
		save(body(fib, "fibonacci", 2, 1710338400L, 80_000L, null), fib).andExpect(status().isCreated());
		save(body(zone, "zone", 2, 1710338400L, 80_000L, "  BISI 5m  "), zone).andExpect(status().isCreated());
		save(body(label, "label", 1, 1710338400L, 80_000L, "Sweep of Asia high"), label)
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/charts/DEMO-NQ/drawings").cookie(alice))
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[0].kind").value("fibonacci"))
			.andExpect(jsonPath("$[0].text").doesNotExist())
			// Trimmed: what is stored is what is shown.
			.andExpect(jsonPath("$[1].text").value("BISI 5m"))
			.andExpect(jsonPath("$[2].text").value("Sweep of Asia high"));
	}

	@Test
	@DisplayName("DEC-048: a shape with the wrong number of anchors is refused, before it can break a chart")
	void anchorCountIsChecked() throws Exception {
		UUID id = UUID.randomUUID();
		save(body(id, "position", 2, 1710338400L, 80_000L, null), id)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("3 anchors")));
		save(body(id, "fibonacci", 1, 1710338400L, 80_000L, null), id).andExpect(status().isBadRequest());
		save(body(id, "horizontal-line", 2, 1710338400L, 80_000L, null), id).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("DEC-048: text belongs to labels and zones only, a label must have some, and it is short")
	void textRules() throws Exception {
		UUID id = UUID.randomUUID();
		save(body(id, "label", 1, 1710338400L, 80_000L, null), id).andExpect(status().isBadRequest());
		save(body(id, "label", 1, 1710338400L, 80_000L, "   "), id).andExpect(status().isBadRequest());
		save(body(id, "rectangle", 2, 1710338400L, 80_000L, "note"), id).andExpect(status().isBadRequest());
		save(body(id, "label", 1, 1710338400L, 80_000L, "x".repeat(81)), id).andExpect(status().isBadRequest());
		save(body(id, "label", 1, 1710338400L, 80_000L, "x".repeat(80)), id).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("drawings require authentication")
	void requiresAuth() throws Exception {
		mockMvc.perform(get("/api/charts/DEMO-NQ/drawings"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("a saved drawing round-trips with its anchors intact")
	void savesAndReadsBack() throws Exception {
		UUID id = UUID.randomUUID();

		mockMvc.perform(put("/api/charts/DEMO-NQ/drawings/" + id).cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(id, "horizontal-line", 1710338400L, 80_000L)))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/charts/DEMO-NQ/drawings").cookie(alice))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].kind").value("horizontal-line"))
			// INVARIANT 3 / DEC-024: the anchor holds an integer tick count, not a decimal price.
			.andExpect(jsonPath("$[0].anchors[0].priceTicks").value(80_000))
			.andExpect(jsonPath("$[0].anchors[0].time").value(1710338400L));
	}

	@Test
	@DisplayName("saving the same id twice updates rather than duplicating")
	void saveIsIdempotent() throws Exception {
		// A dropped response makes the client retry. Without idempotency the user would end up with
		// two identical shapes stacked on the same level, and only notice when dragging one.
		UUID id = UUID.randomUUID();

		mockMvc.perform(put("/api/charts/DEMO-NQ/drawings/" + id).cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(id, "horizontal-line", 1710338400L, 80_000L)))
			.andExpect(status().isCreated());

		mockMvc.perform(put("/api/charts/DEMO-NQ/drawings/" + id).cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(id, "horizontal-line", 1710338400L, 80_500L)))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/charts/DEMO-NQ/drawings").cookie(alice))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].anchors[0].priceTicks").value(80_500));
	}

	@Test
	@DisplayName("one user cannot see another's drawings")
	void drawingsAreScopedToTheirOwner() throws Exception {
		UUID id = UUID.randomUUID();
		mockMvc.perform(put("/api/charts/DEMO-NQ/drawings/" + id).cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(id, "rectangle", 1710338400L, 80_000L)))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/charts/DEMO-NQ/drawings").cookie(bob))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	@DisplayName("writing to another user's drawing id is refused, not silently honoured")
	void cannotOverwriteAnotherUsersDrawing() throws Exception {
		// The dangerous case, and the one that found a real hole: ids are UUIDs in the URL, so a
		// client can name one it does not own. An owner-scoped lookup is not enough on its own —
		// save() on a client-assigned id merges by primary key and rewrote the row, user_id and all.
		UUID id = UUID.randomUUID();

		mockMvc.perform(put("/api/charts/DEMO-NQ/drawings/" + id).cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(id, "horizontal-line", 1710338400L, 80_000L)))
			.andExpect(status().isCreated());

		// 404, not 403: an id the caller cannot write to is indistinguishable from one that does not
		// exist, so this cannot be used to discover which ids are in use.
		mockMvc.perform(put("/api/charts/DEMO-NQ/drawings/" + id).cookie(bob).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(id, "horizontal-line", 1710338400L, 99_999L)))
			.andExpect(status().isNotFound());

		// Alice's is untouched, and still hers.
		mockMvc.perform(get("/api/charts/DEMO-NQ/drawings").cookie(alice))
			.andExpect(jsonPath("$[0].anchors[0].priceTicks").value(80_000));
	}

	@Test
	@DisplayName("deleting another user's drawing does nothing to it")
	void cannotDeleteAnotherUsersDrawing() throws Exception {
		UUID id = UUID.randomUUID();
		mockMvc.perform(put("/api/charts/DEMO-NQ/drawings/" + id).cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(id, "trend-line", 1710338400L, 80_000L)))
			.andExpect(status().isCreated());

		mockMvc.perform(delete("/api/charts/DEMO-NQ/drawings/" + id).cookie(bob).with(csrf()))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/charts/DEMO-NQ/drawings").cookie(alice))
			.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	@DisplayName("deleting something already gone succeeds")
	void deleteIsIdempotent() throws Exception {
		// The client asked for it to be absent, and it is. A retry after a dropped response must not
		// surface as an error the user has to think about.
		mockMvc.perform(delete("/api/charts/DEMO-NQ/drawings/" + UUID.randomUUID())
				.cookie(alice).with(csrf()))
			.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("an unknown kind is a client error, not a server error")
	void unknownKindIsRejected() throws Exception {
		UUID id = UUID.randomUUID();
		mockMvc.perform(put("/api/charts/DEMO-NQ/drawings/" + id).cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(id, "not-a-real-tool", 1710338400L, 80_000L)))
			// The database CHECK constraint would catch this too, but letting it be the only guard
			// turns a client typo into a 500 and a stack trace. A bad tool name should read as the
			// client's mistake.
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("a drawing with no anchors is rejected")
	void emptyAnchorsAreRejected() throws Exception {
		UUID id = UUID.randomUUID();
		mockMvc.perform(put("/api/charts/DEMO-NQ/drawings/" + id).cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"id\":\"" + id + "\",\"kind\":\"rectangle\",\"anchors\":[]}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("a mismatched id between path and body is rejected")
	void mismatchedIdIsRejected() throws Exception {
		mockMvc.perform(put("/api/charts/DEMO-NQ/drawings/" + UUID.randomUUID())
				.cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(UUID.randomUUID(), "rectangle", 1710338400L, 80_000L)))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("drawings on an unknown instrument are a 404")
	void unknownInstrumentIsNotFound() throws Exception {
		mockMvc.perform(get("/api/charts/NOPE/drawings").cookie(alice))
			.andExpect(status().isNotFound());
	}
}
