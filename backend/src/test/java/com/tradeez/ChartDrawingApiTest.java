package com.tradeez;

import static org.assertj.core.api.Assertions.assertThat;
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

	@Autowired
	com.tradeez.marketdata.MarketDataService marketData;

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
	@DisplayName("BUG-152 a drawing on one market cannot be changed or deleted through another market's address")
	void boundToItsMarket() throws Exception {
		UUID id = UUID.randomUUID();
		mockMvc.perform(put("/api/charts/DEMO-NQ/drawings/" + id).cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body(id, "horizontal-line", 1710338400L, 80_000L)))
			.andExpect(status().isCreated());
		mockMvc.perform(put("/api/charts/NQ/drawings/" + id).cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body(id, "horizontal-line", 1710338400L, 90_000L)))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/charts/NQ/drawings/" + id).cookie(alice).with(csrf()))
			.andExpect(status().isNoContent());
		assertThat(drawings.findById(id)).hasValueSatisfying(d -> {
			assertThat(d.getAnchors().getFirst().get("priceTicks")).isEqualTo(80_000);
		});
		mockMvc.perform(delete("/api/charts/DEMO-NQ/drawings/" + id).cookie(alice).with(csrf()))
			.andExpect(status().isNoContent());
		assertThat(drawings.findById(id)).isEmpty();
	}

	@Test
	@DisplayName("BUG-165 no anchors at all, a null anchor or one missing a coordinate is a 400, not a crash")
	void malformedAnchorsAreRejected() throws Exception {
		UUID id = UUID.randomUUID();
		for (String anchors : java.util.List.of("null", "[null]", "[{\"time\":1710338400,\"priceTicks\":80000},null]",
				"[{\"time\":1710338400}]", "[{\"priceTicks\":80000}]")) {
			mockMvc.perform(put("/api/charts/DEMO-NQ/drawings/" + id).cookie(alice).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"id\":\"" + id + "\",\"kind\":\"horizontal-line\",\"anchors\":" + anchors + "}"))
				.andExpect(status().isBadRequest());
		}
		mockMvc.perform(put("/api/charts/DEMO-NQ/drawings/" + id).cookie(alice).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(id, "horizontal-line", 1710338400L, 80_000L)))
			.andExpect(status().is2xxSuccessful());
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

	@Test
	@DisplayName("a chart holds at most 500 drawings: the next is refused with 409, while an edit of one still saves")
	void capped() throws Exception {
		UUID owner = users.findByEmailIgnoreCase("alice@example.com").orElseThrow().getId();
		UUID instrument = marketData.requireInstrument("DEMO-NQ").getId();
		java.util.List<com.tradeez.charting.ChartDrawing> full = new java.util.ArrayList<>();
		for (int i = 0; i < 500; i++) {
			full.add(new com.tradeez.charting.ChartDrawing(UUID.randomUUID(), owner, instrument, "horizontal-line",
				java.util.List.of(java.util.Map.of("time", 1710338400L, "priceTicks", 80_000L + i)),
				java.util.Map.of("color", "#58a6ff"), null));
		}
		drawings.saveAll(full);

		UUID next = UUID.randomUUID();
		save(body(next, "horizontal-line", 1710338400L, 81_000L), next)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("This chart already has the maximum of 500 drawings."));
		UUID existing = full.getFirst().getId();
		save(body(existing, "horizontal-line", 1710338400L, 82_000L), existing).andExpect(status().isOk());
	}

	@Test
	@DisplayName("BUG-072 a style is a colour, a width and a fill, not any JSON of any size")
	void styleBounded() throws Exception {
		UUID id = UUID.randomUUID();
		String huge = body(id, "horizontal-line", 1710338400L, 80_000L)
			.replace("\"style\":{\"color\":\"#58a6ff\"}", "\"style\":{\"color\":\"" + "f".repeat(100_000) + "\"}");
		// Past the request size limit it is not even read (BUG-123); under it, the style rule refuses it.
		save(huge, id).andExpect(status().isPayloadTooLarge());
		save(huge.replace("f".repeat(100_000), "f".repeat(10_000)), id).andExpect(status().isBadRequest());
		StringBuilder many = new StringBuilder("\"style\":{");
		for (int i = 0; i < 9; i++) {
			many.append(i == 0 ? "" : ",").append("\"k").append(i).append("\":1");
		}
		save(body(id, "horizontal-line", 1710338400L, 80_000L)
			.replace("\"style\":{\"color\":\"#58a6ff\"", many.toString()), id).andExpect(status().isBadRequest());
		assertThat(drawings.count()).isZero();
		save(body(id, "rectangle", 1710338400L, 80_000L)
			.replace("{\"color\":\"#58a6ff\"}", "{\"color\":\"#d29922\",\"lineWidth\":1,\"fill\":\"rgba(210, 153, 34, 0.12)\"}"), id)
			.andExpect(status().isCreated());
	}

	private String styled(UUID id, String style) {
		return body(id, "horizontal-line", 1710338400L, 80_000L).replace("{\"color\":\"#58a6ff\"}", style);
	}

	@Test
	@DisplayName("BUG-084 a style holds only a colour, a fill and a line width, each in the form the chart draws")
	void styleIsTheChartsOwn() throws Exception {
		for (String style : java.util.List.of(
				"{\"onload\":\"#58a6ff\"}",
				"{\"color\":\"<img src=x onerror=alert(1)>\"}",
				"{\"color\":\"red\"}",
				"{\"color\":\"#58a6ff; background:url(x)\"}",
				"{\"fill\":\"rgba(1, 2, 3, 0.5) url(x)\"}",
				"{\"color\":12}",
				"{\"lineWidth\":0}",
				"{\"lineWidth\":8.5}",
				"{\"lineWidth\":\"2\"}")) {
			UUID id = UUID.randomUUID();
			save(styled(id, style), id).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("A drawing's style is")));
		}
		assertThat(drawings.count()).isZero();
		for (String style : java.util.List.of("{}", "{\"color\":\"#abc\",\"lineWidth\":8}",
				"{\"color\":\"#58A6FFCC\",\"fill\":\"rgb(1,2,3)\",\"lineWidth\":0.5}",
				"{\"fill\":\"rgba(57, 197, 207, 1)\"}")) {
			UUID id = UUID.randomUUID();
			save(styled(id, style), id).andExpect(status().isCreated());
		}
	}

	@Test
	@DisplayName("BUG-084 a drawing's points lie between 2000 and 2100 and within a billion ticks of zero")
	void anchorsBounded() throws Exception {
		long[][] refused = { { 946_684_799L, 80_000L }, { 4_102_444_801L, 80_000L }, { Long.MIN_VALUE, 80_000L },
			{ 1710338400L, 1_000_000_001L }, { 1710338400L, -1_000_000_001L }, { 1710338400L, Long.MAX_VALUE },
			{ 1710338400L, Long.MIN_VALUE } };
		for (long[] point : refused) {
			UUID id = UUID.randomUUID();
			save(body(id, "horizontal-line", point[0], point[1]), id).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
					"A drawing's points lie between the years 2000 and 2100, within 1000000000 ticks of zero."));
		}
		assertThat(drawings.count()).isZero();
		long[][] accepted = { { 946_684_800L, 1_000_000_000L }, { 4_102_444_800L, -1_000_000_000L } };
		for (long[] point : accepted) {
			UUID id = UUID.randomUUID();
			save(body(id, "trend-line", point[0], point[1]), id).andExpect(status().isCreated());
		}
	}

	@Test
	@DisplayName("BUG-135 overlapping first saves of a new drawing all succeed, and leave one drawing, its owner's")
	void overlappingFirstSaves() throws Exception {
		for (int round = 0; round < 5; round++) {
			UUID id = UUID.randomUUID();
			String json = body(id, "horizontal-line", 1710338400, 80000);
			java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
			java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
			java.util.List<java.util.concurrent.Future<Integer>> saves = new java.util.ArrayList<>();
			for (int i = 0; i < 4; i++) {
				saves.add(pool.submit(() -> {
					start.await();
					return mockMvc.perform(put("/api/charts/DEMO-NQ/drawings/" + id).cookie(alice).with(csrf())
							.contentType(MediaType.APPLICATION_JSON).content(json))
						.andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			for (var save : saves) {
				org.assertj.core.api.Assertions.assertThat(save.get(30, java.util.concurrent.TimeUnit.SECONDS)).isIn(200, 201);
			}
			pool.shutdown();
			org.assertj.core.api.Assertions.assertThat(drawings.findByIdAndUserId(id,
				users.findByEmailIgnoreCase("alice@example.com").orElseThrow().getId())).isPresent();
		}
		org.assertj.core.api.Assertions.assertThat(drawings.count()).isEqualTo(5);
	}
}
