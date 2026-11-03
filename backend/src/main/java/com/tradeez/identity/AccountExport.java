package com.tradeez.identity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * BUG-120 (T57): everything the app holds about one person, as they could take it elsewhere (GDPR Art. 15 and 20).
 * Every table that belongs to an account is here, read whole, except what would help someone break in: the password's
 * hash and the sign-in tokens' hashes. {@code AccountDataTest} fails when a new table that hangs off an account is not
 * listed below.
 *
 * <p>BUG-140: nor what the app has not shown yet. A drill not answered keeps its answer, its real dates and its outcome;
 * a practice session not ended keeps its real date and seed, and a trade whose exit the replay has not reached is left
 * out. Handing them over would be a way round the training rules — they come in the export once revealed. A row that is
 * still open is marked by an {@code open_} column in its query, dropped before the export.
 */
@Service
public class AccountExport {

	/** Each section: its name in the export, and the query for one account's rows ({@code ?} is the account's id). */
	static final Map<String, String> SECTIONS = sections();

	private static final String SESSION_OPEN = "ps.status <> 'COMPLETE' AS open_";

	/** BUG-140: per section, the columns left out of a row that is still open. */
	static final Map<String, List<String>> HIDDEN_WHILE_OPEN = Map.of(
		"drills", List.of("seed", "generator_version", "detector", "rule_version", "window_from", "window_to", "graded",
			"question", "outcome", "outcome_at", "reveal_to"),
		"riskDrills", List.of("question", "answer", "tolerance", "correct"),
		"practiceSessions", List.of("seed", "generator_version", "history_from", "start_time", "cursor_time", "clock_day_shift"),
		"tradePlans", List.of("submitted_cursor"),
		"tradeCloses", List.of("exit_time", "partial_time"),
		"tradeResults", List.of("entry_time", "exit_time", "partial_time", "seed", "generator_version"));

	private static Map<String, String> sections() {
		Map<String, String> s = new LinkedHashMap<>();
		s.put("account", "SELECT id, email, display_name, created_at, email_verified_at FROM app_user WHERE id = ?");
		s.put("signIns", "SELECT issued_at, expires_at, revoked_at, user_agent FROM refresh_token WHERE user_id = ?"
			+ " ORDER BY issued_at");
		s.put("emailLinks", "SELECT purpose, created_at, expires_at, used_at FROM email_token WHERE user_id = ? ORDER BY created_at");
		s.put("lessonsRead", "SELECT * FROM concept_read WHERE user_id = ?");
		s.put("drills", "SELECT d.*, d.answered_at IS NULL AS open_ FROM drill d WHERE d.user_id = ? ORDER BY d.created_at");
		// A drill's marked setups are its answer key.
		s.put("drillMarks", "SELECT ds.* FROM drill_setup ds JOIN drill d ON d.id = ds.drill_id WHERE d.user_id = ?"
			+ " AND d.answered_at IS NOT NULL");
		s.put("riskDrills", "SELECT r.*, r.answered_at IS NULL AS open_ FROM risk_drill r WHERE r.user_id = ? ORDER BY r.created_at");
		s.put("drawings", "SELECT * FROM chart_drawing WHERE user_id = ? ORDER BY created_at");
		s.put("practiceSettings", "SELECT * FROM practice_settings WHERE user_id = ?");
		s.put("practiceSessions", "SELECT ps.*, " + SESSION_OPEN + " FROM practice_session ps WHERE ps.user_id = ?"
			+ " ORDER BY ps.created_at");
		s.put("tradePlans", "SELECT tp.*, " + SESSION_OPEN + " FROM trade_plan tp JOIN practice_session ps ON ps.id = tp.session_id"
			+ " WHERE ps.user_id = ?");
		s.put("tradeCloses", "SELECT pc.*, " + SESSION_OPEN + " FROM practice_close pc JOIN practice_session ps ON ps.id = pc.session_id"
			+ " WHERE ps.user_id = ?");
		// A trade counts once the replay has passed its exit — the rule of the score and the journal.
		s.put("tradeResults", "SELECT pr.*, " + SESSION_OPEN + " FROM practice_result pr JOIN practice_session ps ON ps.id = pr.session_id"
			+ " WHERE ps.user_id = ? AND (ps.status = 'COMPLETE' OR pr.exit_time <= ps.cursor_time)");
		return s;
	}

	/** How the PostgreSQL driver hands back a json or jsonb column. */
	private static final String JSON_WRAPPER = "org.postgresql.util.PGobject";

	/** Never handed out, even to the account's owner: they would only help someone else sign in as them. */
	private static final List<String> NEVER = List.of("password_hash", "token_hash", "version");

	private final JdbcTemplate jdbc;
	private final ObjectMapper json;

	public AccountExport(JdbcTemplate jdbc, ObjectMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> of(UUID userId) {
		Map<String, Object> export = new LinkedHashMap<>();
		export.put("exportedAt", Instant.now().toString());
		export.put("about", "Everything Tradeez holds about this account. Times are UTC; prices are whole ticks. "
			+ "Answers, outcomes and real dates not shown yet are left out until they are (an unanswered drill, a running session).");
		SECTIONS.forEach((name, sql) -> {
			List<Map<String, Object>> rows = jdbc.queryForList(sql, userId);
			rows.forEach(row -> {
				NEVER.forEach(row::remove);
				if (Boolean.TRUE.equals(row.remove("open_"))) {
					HIDDEN_WHILE_OPEN.getOrDefault(name, List.of()).forEach(row::remove);
				}
				// JSON columns (a drill's marks, a drawing's anchors) as JSON, not as the driver's wrapper around text. The
				// driver is a runtime dependency only, so its wrapper is known by name; its text is the column's JSON.
				row.replaceAll((column, value) -> value != null && JSON_WRAPPER.equals(value.getClass().getName())
					? json.readTree(value.toString()) : value);
			});
			export.put(name, rows);
		});
		return export;
	}
}
