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
 */
@Service
public class AccountExport {

	/** Each section: its name in the export, and the query for one account's rows ({@code ?} is the account's id). */
	static final Map<String, String> SECTIONS = sections();

	private static Map<String, String> sections() {
		Map<String, String> s = new LinkedHashMap<>();
		s.put("account", "SELECT id, email, display_name, created_at, email_verified_at FROM app_user WHERE id = ?");
		s.put("signIns", "SELECT issued_at, expires_at, revoked_at, user_agent FROM refresh_token WHERE user_id = ?"
			+ " ORDER BY issued_at");
		s.put("emailLinks", "SELECT purpose, created_at, expires_at, used_at FROM email_token WHERE user_id = ? ORDER BY created_at");
		s.put("lessonsRead", "SELECT * FROM concept_read WHERE user_id = ?");
		s.put("drills", "SELECT * FROM drill WHERE user_id = ? ORDER BY created_at");
		s.put("drillMarks", "SELECT ds.* FROM drill_setup ds JOIN drill d ON d.id = ds.drill_id WHERE d.user_id = ?");
		s.put("riskDrills", "SELECT * FROM risk_drill WHERE user_id = ? ORDER BY created_at");
		s.put("drawings", "SELECT * FROM chart_drawing WHERE user_id = ? ORDER BY created_at");
		s.put("practiceSettings", "SELECT * FROM practice_settings WHERE user_id = ?");
		s.put("practiceSessions", "SELECT * FROM practice_session WHERE user_id = ? ORDER BY created_at");
		s.put("tradePlans", "SELECT tp.* FROM trade_plan tp JOIN practice_session ps ON ps.id = tp.session_id"
			+ " WHERE ps.user_id = ?");
		s.put("tradeCloses", "SELECT pc.* FROM practice_close pc JOIN practice_session ps ON ps.id = pc.session_id"
			+ " WHERE ps.user_id = ?");
		s.put("tradeResults", "SELECT pr.* FROM practice_result pr JOIN practice_session ps ON ps.id = pr.session_id"
			+ " WHERE ps.user_id = ?");
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
		export.put("about", "Everything Tradeez holds about this account. Times are UTC; prices are whole ticks.");
		SECTIONS.forEach((name, sql) -> {
			List<Map<String, Object>> rows = jdbc.queryForList(sql, userId);
			rows.forEach(row -> {
				NEVER.forEach(row::remove);
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
