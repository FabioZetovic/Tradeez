package com.tradeez.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.drills.DrillService;
import com.tradeez.drills.RiskDrillService;
import com.tradeez.identity.dto.AuthDtos.RegisterRequest;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketBarStore.StoredBar;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.SessionCalendar;
import com.tradeez.practice.FillEngine.Direction;
import com.tradeez.practice.PracticeDtos.PlanRequest;
import com.tradeez.practice.PracticeService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * BUG-140 (Codex's audit, 2026-11-01): "Your data" handed over the answer to a drill not yet answered, and a running
 * session's real date and the outcome of a trade the replay had not reached. Now they come once revealed.
 */
class ExportRevealTest extends AbstractIntegrationTest {

	@Autowired
	AuthService auth;

	@Autowired
	AccountExport exports;

	@Autowired
	DrillService drills;

	@Autowired
	RiskDrillService risk;

	@Autowired
	PracticeService practice;

	@Autowired
	MarketDataService markets;

	@Autowired
	MarketBarStore barStore;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ObjectMapper json;

	private UUID account() {
		return auth.register(new RegisterRequest("export-" + UUID.randomUUID() + "@example.test",
			"a-perfectly-fine-passphrase", "E"), "test").signedIn().user().getId();
	}

	private JsonNode export(UUID userId) {
		return json.valueToTree(exports.of(userId));
	}

	@Test
	@DisplayName("a drill not yet answered comes without its marked setups, window or seed; answered, with them")
	void drillKeyAfterAnswering() {
		UUID user = account();
		var drill = drills.create(user, "FVG_IDENTIFICATION", "DEMO-NQ", "5m");
		JsonNode open = export(user);
		assertThat(open.get("drillMarks")).as("the answer key").isEmpty();
		JsonNode row = open.get("drills").get(0);
		assertThat(row.has("window_from")).isFalse();
		assertThat(row.has("seed")).isFalse();
		assertThat(row.has("open_")).isFalse();

		drills.answer(user, drill.id(), java.util.List.of(), null);
		JsonNode answered = export(user);
		assertThat(answered.get("drillMarks")).isNotEmpty();
		assertThat(answered.get("drills").get(0).has("window_from")).isTrue();
	}

	@Test
	@DisplayName("a risk question not yet answered comes without its answer, tolerance or working; answered, with them")
	void riskAnswerAfterAnswering() {
		UUID user = account();
		var question = risk.create(user, "STREAK");
		JsonNode open = export(user).get("riskDrills").get(0);
		assertThat(open.has("answer")).isFalse();
		assertThat(open.has("tolerance")).isFalse();
		assertThat(open.has("question")).isFalse();
		assertThat(open.has("open_")).isFalse();
		assertThat(open.get("id").asString()).isEqualTo(question.id().toString());

		risk.answer(user, question.id(), 1);
		JsonNode answered = export(user).get("riskDrills").get(0);
		assertThat(answered.has("answer")).isTrue();
		assertThat(answered.get("question").has("working")).isTrue();
	}

	@Test
	@DisplayName("a running real session keeps its date and its unreached trade's outcome; ended, both are there")
	void practiceAfterTheEnd() {
		UUID user = account();
		UUID nq = markets.requireInstrument("NQ").getId();
		Instant from = Instant.parse("2025-03-03T00:00:00Z");
		Instant to = Instant.parse("2025-03-22T00:00:00Z");
		Random random = new Random(20250303L);
		List<StoredBar> stored = new ArrayList<>();
		long price = 80000;
		for (Instant t = from; t.isBefore(to); t = t.plusSeconds(60)) {
			if (!SessionCalendar.isOpen(t)) {
				continue;
			}
			long close = price + random.nextInt(17) - 8;
			stored.add(new StoredBar(new Bar(t, price, Math.max(price, close) + 3, Math.min(price, close) - 3, close, 10), 202503));
			price = close;
		}
		jdbc.update("DELETE FROM market_bar");
		jdbc.update("DELETE FROM market_data_import");
		barStore.insert(nq, "1m", stored);
		jdbc.update("""
			INSERT INTO market_data_import (instrument_id, source, dataset, symbol, resolution,
			    range_from, range_to, estimated_cost_usd, bars_stored, status)
			VALUES (?, 'massive', 'CME', 'NQH5', '1m', ?, ?, 0, ?, 'DONE')
			""", nq, Timestamp.from(from), Timestamp.from(to), stored.size());

		var created = practice.create(user, "NQ", "5m", null, null);
		long reference = created.bars().getLast().close();
		var state = practice.submitPlan(user, created.state().id(),
			new PlanRequest(Direction.LONG, reference - 100, reference + 100, 1));
		assertThat(state.trades().getLast().result()).as("the page is not told yet").isNull();

		JsonNode running = export(user);
		assertThat(running.get("tradeResults")).as("the unreached exit").isEmpty();
		JsonNode session = running.get("practiceSessions").get(0);
		assertThat(session.has("start_time")).isFalse();
		assertThat(session.has("cursor_time")).isFalse();
		assertThat(session.has("seed")).isFalse();
		assertThat(running.get("tradePlans").get(0).has("submitted_cursor")).isFalse();
		assertThat(running.toString()).doesNotContain("2025-03");

		// The exit reached, the session still running: the result is there, its real times are not.
		practice.skipToResult(user, created.state().id(), null);
		JsonNode reached = export(user).get("tradeResults");
		assertThat(reached).hasSize(1);
		assertThat(reached.get(0).has("outcome")).isTrue();
		assertThat(reached.get(0).has("exit_time")).isFalse();

		practice.end(user, created.state().id());
		JsonNode ended = export(user);
		assertThat(ended.get("tradeResults")).hasSize(1);
		assertThat(ended.get("tradeResults").get(0).has("outcome")).isTrue();
		assertThat(ended.get("practiceSessions").get(0).get("start_time").asString()).contains("2025-03");
	}
}
