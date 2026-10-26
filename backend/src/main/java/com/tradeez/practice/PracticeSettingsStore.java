package com.tradeez.practice;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * A learner's own practice rule (V20): the most one trade may risk, copied onto each plan as it is placed. What
 * trading costs is no longer theirs to set (T41, DEC-080): {@link PracticeCosts}.
 */
@Repository
public class PracticeSettingsStore {

	private final JdbcTemplate jdbc;

	public PracticeSettingsStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** @return null when no limit is set */
	public Long riskLimitCents(UUID userId) {
		List<Long> rows = jdbc.query("SELECT risk_limit_cents FROM practice_settings WHERE user_id = ?",
			(rs, n) -> rs.getObject(1, Long.class), userId);
		return rows.isEmpty() ? null : rows.getFirst();
	}

	/** Sets the limit, or unsets it with null. */
	public void saveRiskLimit(UUID userId, Long riskLimitCents) {
		// One statement, so two saves at once cannot both insert.
		jdbc.update("""
			INSERT INTO practice_settings (user_id, risk_limit_cents, updated_at) VALUES (?, ?, now())
			ON CONFLICT (user_id) DO UPDATE SET risk_limit_cents = EXCLUDED.risk_limit_cents, updated_at = now()""",
			userId, riskLimitCents);
	}
}
