package com.tradeez.practice;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.tradeez.practice.FillEngine.FillModel;

/**
 * A learner's own practice rules (V20, V23): the most one trade may risk, and what trading costs them — fees per
 * round trip, the ticks a market fill and a stop fill slip, and how a target's limit fills. Set before a session; the
 * limit is copied onto each plan as it is placed, the costs resolve it and are kept with its result.
 */
@Repository
public class PracticeSettingsStore {

	/** What is stored; null fields are unset, and the costs then are {@link FillModel#STANDARD}'s. */
	public record Settings(Long riskLimitCents, Long feesCents, Integer marketSlippageTicks, Integer stopSlippageTicks,
			Integer limitThroughTicks) {

		static final Settings NONE = new Settings(null, null, null, null, null);

		/** The fill model these costs make: the standard one's numbers where nothing is set. */
		FillModel fillModel() {
			FillModel standard = FillModel.STANDARD;
			return new FillModel(marketSlippageTicks == null ? standard.marketSlippageTicks() : marketSlippageTicks,
				stopSlippageTicks == null ? standard.stopSlippageTicks() : stopSlippageTicks,
				limitThroughTicks == null ? standard.limitThroughTicks() : limitThroughTicks,
				feesCents == null ? standard.commissionCentsPerContractRound() : feesCents);
		}
	}

	private final JdbcTemplate jdbc;

	public PracticeSettingsStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Settings settings(UUID userId) {
		List<Settings> rows = jdbc.query("""
			SELECT risk_limit_cents, fees_cents, market_slippage_ticks, stop_slippage_ticks, limit_through_ticks
			FROM practice_settings WHERE user_id = ?""",
			(rs, n) -> new Settings(rs.getObject(1, Long.class), rs.getObject(2, Long.class), rs.getObject(3, Integer.class),
				rs.getObject(4, Integer.class), rs.getObject(5, Integer.class)),
			userId);
		return rows.isEmpty() ? Settings.NONE : rows.getFirst();
	}

	/** @return null when no limit is set */
	public Long riskLimitCents(UUID userId) {
		return settings(userId).riskLimitCents();
	}

	public FillModel fillModel(UUID userId) {
		return settings(userId).fillModel();
	}

	/** Replaces every setting at once; a null one is unset. */
	public void save(UUID userId, Settings settings) {
		// One statement, so two saves at once cannot both insert.
		jdbc.update("""
			INSERT INTO practice_settings (user_id, risk_limit_cents, fees_cents, market_slippage_ticks, stop_slippage_ticks,
			    limit_through_ticks, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, now())
			ON CONFLICT (user_id) DO UPDATE SET risk_limit_cents = EXCLUDED.risk_limit_cents, fees_cents = EXCLUDED.fees_cents,
			    market_slippage_ticks = EXCLUDED.market_slippage_ticks, stop_slippage_ticks = EXCLUDED.stop_slippage_ticks,
			    limit_through_ticks = EXCLUDED.limit_through_ticks, updated_at = now()""",
			userId, settings.riskLimitCents(), settings.feesCents(), settings.marketSlippageTicks(), settings.stopSlippageTicks(),
			settings.limitThroughTicks());
	}
}
