package com.tradeez.practice;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.tradeez.practice.FillEngine.Fill;
import com.tradeez.practice.FillEngine.FillModel;
import com.tradeez.practice.FillEngine.Outcome;

/**
 * What happened to a trade. Maps {@code practice_result} from V5.
 *
 * <p>INVARIANT 2 — written once, at the moment the plan is locked, and never again. Immutable in the
 * same three layers as {@link TradePlan}. Computing it up front rather than as the replay reaches the
 * exit means there is no moment at which a learner could influence it.
 *
 * <p>DEC-012 — carries every version and the seed, so it can be re-derived later and explained if it
 * ever cannot be.
 */
@Entity
@Table(name = "practice_result")
public class PracticeResult {

	@Id
	@GeneratedValue
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "session_id", nullable = false, updatable = false)
	private UUID sessionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "outcome", nullable = false, updatable = false)
	private Outcome outcome;

	@Column(name = "entry_time", nullable = false, updatable = false)
	private Instant entryTime;

	@Column(name = "entry_ticks", nullable = false, updatable = false)
	private long entryTicks;

	@Column(name = "exit_time", nullable = false, updatable = false)
	private Instant exitTime;

	@Column(name = "exit_ticks", nullable = false, updatable = false)
	private long exitTicks;

	@Column(name = "pnl_ticks", nullable = false, updatable = false)
	private long pnlTicks;

	@Column(name = "net_pnl_cents", nullable = false, updatable = false)
	private long netPnlCents;

	@Column(name = "commission_cents", nullable = false, updatable = false)
	private long commissionCents;

	@Column(name = "mae_ticks", nullable = false, updatable = false)
	private long maeTicks;

	@Column(name = "mfe_ticks", nullable = false, updatable = false)
	private long mfeTicks;

	@Column(name = "gap_exit", nullable = false, updatable = false)
	private boolean gapExit;

	@Column(name = "resolution", nullable = false, updatable = false)
	private String resolution;

	@Column(name = "engine_version", nullable = false, updatable = false)
	private String engineVersion;

	@Column(name = "fill_model_version", nullable = false, updatable = false)
	private String fillModelVersion;

	/** Simulated data only. */
	@Column(name = "generator_version", updatable = false)
	private String generatorVersion;

	/** Simulated data only. */
	@Column(name = "seed", updatable = false)
	private Long seed;

	/** Real data only: where the bars came from (V9). */
	@Column(name = "data_source", updatable = false)
	private String dataSource;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected PracticeResult() {
		// for JPA
	}

	public PracticeResult(UUID sessionId, Fill fill, int contracts, long tickValueCents,
			FillModel model, String generatorVersion, Long seed, String dataSource) {
		this.sessionId = sessionId;
		this.outcome = fill.outcome();
		this.entryTime = fill.entryTime();
		this.entryTicks = fill.entryTicks();
		this.exitTime = fill.exitTime();
		this.exitTicks = fill.exitTicks();
		this.pnlTicks = fill.pnlTicks();
		this.commissionCents = model.commissionCentsPerContractRound() * contracts;
		this.netPnlCents = fill.netPnlCents(tickValueCents, contracts, model);
		this.maeTicks = fill.maeTicks();
		this.mfeTicks = fill.mfeTicks();
		this.gapExit = fill.gapExit();
		this.resolution = fill.resolution().name();
		this.engineVersion = FillEngine.VERSION;
		this.fillModelVersion = model.version();
		this.generatorVersion = generatorVersion;
		this.seed = seed;
		this.dataSource = dataSource;
		this.createdAt = Instant.now();
	}

	public UUID getSessionId() {
		return sessionId;
	}

	public Outcome getOutcome() {
		return outcome;
	}

	public Instant getEntryTime() {
		return entryTime;
	}

	public long getEntryTicks() {
		return entryTicks;
	}

	public Instant getExitTime() {
		return exitTime;
	}

	public long getExitTicks() {
		return exitTicks;
	}

	public long getPnlTicks() {
		return pnlTicks;
	}

	public long getNetPnlCents() {
		return netPnlCents;
	}

	public long getCommissionCents() {
		return commissionCents;
	}

	public long getMaeTicks() {
		return maeTicks;
	}

	public long getMfeTicks() {
		return mfeTicks;
	}

	public boolean isGapExit() {
		return gapExit;
	}

	public String getResolution() {
		return resolution;
	}

	public String getEngineVersion() {
		return engineVersion;
	}

	public String getFillModelVersion() {
		return fillModelVersion;
	}
}
