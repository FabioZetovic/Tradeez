package com.tradeez.drills;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.tradeez.drills.DrillDtos.RiskDrillView;
import com.tradeez.drills.DrillDtos.RiskKindView;
import com.tradeez.drills.DrillDtos.RiskResultView;
import com.tradeez.drills.RiskQuestion.Kind;
import com.tradeez.drills.RiskQuestion.Question;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Risk drills (T26, DEC-070), mode F: a question of {@value RiskQuestion#VERSION}, stored with its answer and
 * answered once. The answer and the working leave the server only with the result; the time taken is the
 * database's own clock from the question to the answer.
 */
@Service
public class RiskDrillService {

	private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
	};

	private final JdbcTemplate jdbc;
	private final ObjectMapper json;
	private final SecureRandom random = new SecureRandom();

	public RiskDrillService(JdbcTemplate jdbc, ObjectMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	/** A row as stored. {@code given} and the timings are null until answered. */
	private record Row(UUID id, Kind kind, String version, Map<String, Object> question, double answer, double tolerance,
			Double given, Boolean correct, Double seconds) {
	}

	public RiskDrillView create(UUID userId, String kindCode) {
		Kind kind = kindCode == null || kindCode.isBlank() ? Kind.CONTRACTS : parse(kindCode);
		Integer recent = jdbc.queryForObject(
			"SELECT count(*) FROM risk_drill WHERE user_id = ? AND created_at > now() - interval '1 hour'", Integer.class,
			userId);
		if (recent != null && recent >= DrillService.MAX_DRILLS_PER_HOUR) {
			throw new DrillService.TooManyDrillsException();
		}
		Question q = RiskQuestion.of(kind, random);
		UUID id = UUID.randomUUID();
		jdbc.update("""
			INSERT INTO risk_drill (id, user_id, kind, rule_version, question, answer, tolerance)
			VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)""", id, userId, kind.name(), RiskQuestion.VERSION,
			json.writeValueAsString(Map.of("text", q.text(), "unit", q.unit(), "limitSeconds", q.limitSeconds(),
				"givens", q.givens(), "working", q.working())),
			q.answer(), q.tolerance());
		return view(userId, require(userId, id));
	}

	/** Every kind, in order, with its clock and the learner's record: none answered is a record of 0 of 0. */
	public List<RiskKindView> kinds(UUID userId) {
		Map<String, int[]> tally = new HashMap<>();
		jdbc.query("""
			SELECT kind, count(*) AS answered, count(*) FILTER (WHERE correct) AS right_answers
			FROM risk_drill WHERE user_id = ? AND answered_at IS NOT NULL GROUP BY kind""",
			rs -> {
				tally.put(rs.getString("kind"), new int[] { rs.getInt("answered"), rs.getInt("right_answers") });
			}, userId);
		return Arrays.stream(Kind.values()).map(kind -> {
			int[] record = tally.getOrDefault(kind.name(), new int[2]);
			return new RiskKindView(kind.name(), kind.limitSeconds(), record[0], record[1]);
		}).toList();
	}

	public RiskDrillView get(UUID userId, UUID id) {
		return view(userId, require(userId, id));
	}

	public RiskDrillView answer(UUID userId, UUID id, double value) {
		if (!Double.isFinite(value) || Math.abs(value) > 1e9) {
			throw new IllegalArgumentException("An answer is a number.");
		}
		Row row = require(userId, id);
		boolean correct = RiskQuestion.within(row.answer(), row.tolerance(), value);
		// Once: a second answer, even one racing the first, finds the row answered and changes nothing.
		int updated = jdbc.update("""
			UPDATE risk_drill SET given = ?, answered_at = now(), correct = ?
			WHERE id = ? AND user_id = ? AND answered_at IS NULL""", value, correct, id, userId);
		if (updated == 0) {
			throw new DrillService.DrillAnsweredException();
		}
		return view(userId, require(userId, id));
	}

	private Row require(UUID userId, UUID id) {
		List<Row> rows = jdbc.query("""
			SELECT id, kind, rule_version, question, answer, tolerance, given, correct,
			       EXTRACT(EPOCH FROM answered_at - created_at) AS seconds
			FROM risk_drill WHERE id = ? AND user_id = ?""",
			(rs, n) -> new Row(rs.getObject("id", UUID.class), Kind.valueOf(rs.getString("kind")),
				rs.getString("rule_version"), json.readValue(rs.getString("question"), JSON_OBJECT),
				rs.getDouble("answer"), rs.getDouble("tolerance"), rs.getObject("given") == null ? null : rs.getDouble("given"),
				rs.getObject("correct") == null ? null : rs.getBoolean("correct"),
				rs.getObject("seconds") == null ? null : rs.getDouble("seconds")),
			id, userId);
		if (rows.isEmpty()) {
			throw new DrillService.DrillNotFoundException();
		}
		return rows.getFirst();
	}

	@SuppressWarnings("unchecked")
	private RiskDrillView view(UUID userId, Row row) {
		Map<String, Object> q = row.question();
		int limit = ((Number) q.get("limitSeconds")).intValue();
		RiskResultView result = null;
		if (row.given() != null) {
			// The learner's record at this kind of question, this one included.
			Map<String, Object> tally = jdbc.queryForMap("""
				SELECT count(*) AS answered, count(*) FILTER (WHERE correct) AS right_answers
				FROM risk_drill WHERE user_id = ? AND kind = ? AND answered_at IS NOT NULL""", userId, row.kind().name());
			result = new RiskResultView(row.given(), row.answer(), row.correct(), Math.round(row.seconds() * 10) / 10.0,
				row.seconds() <= limit, (List<String>) q.get("working"), ((Number) tally.get("answered")).intValue(),
				((Number) tally.get("right_answers")).intValue());
		}
		return new RiskDrillView(row.id(), row.kind().name(), row.version(), (String) q.get("text"), (String) q.get("unit"),
			limit, result);
	}

	private static Kind parse(String code) {
		try {
			return Kind.valueOf(code.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("A risk drill is CONTRACTS, STREAK or RECOVERY.");
		}
	}
}
