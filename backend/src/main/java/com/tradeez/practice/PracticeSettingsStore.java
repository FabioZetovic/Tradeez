package com.tradeez.practice;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * A learner's own practice rules (V20): for now the most one trade may risk. Set before a session, copied
 * onto each plan as it is placed, so the scorecard checks a trade against the limit it was placed under.
 */
@Repository
public class PracticeSettingsStore {

	private final JdbcTemplate jdbc;

	public PracticeSettingsStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** @return null when no limit is set */
	public Long riskLimitCents(UUID userId) {
		List<Long> rows = jdbc.queryForList("SELECT risk_limit_cents FROM practice_settings WHERE user_id = ?", Long.class,
			userId);
		return rows.isEmpty() ? null : rows.getFirst();
	}

	/** @param cents null clears the limit */
	public void setRiskLimitCents(UUID userId, Long cents) {
		// One statement, so two saves at once cannot both insert.
		jdbc.update("""
			INSERT INTO practice_settings (user_id, risk_limit_cents, updated_at) VALUES (?, ?, now())
			ON CONFLICT (user_id) DO UPDATE SET risk_limit_cents = EXCLUDED.risk_limit_cents, updated_at = now()""",
			userId, cents);
	}
}
