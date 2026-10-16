package com.tradeez.practice;

import java.time.Instant;
import java.util.UUID;

import com.tradeez.practice.FillEngine.Fill;
import com.tradeez.practice.FillEngine.FillModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A running trade the learner closed at market (DEC-057). Maps {@code practice_close} from V15.
 *
 * <p>The planned outcome ({@link PracticeResult}) is left exactly as it was written at the lock; this
 * is a second fact beside it, written once and never changed — the database refuses an update.
 */
@Entity
@Table(name = "practice_close")
public class PracticeClose {

	@Id
	@GeneratedValue
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "session_id", nullable = false, updatable = false)
	private UUID sessionId;

	@Column(name = "seq", nullable = false, updatable = false)
	private int seq;

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

	@Column(name = "resolution", nullable = false, updatable = false)
	private String resolution;

	@Column(name = "engine_version", nullable = false, updatable = false)
	private String engineVersion;

	@Column(name = "fill_model_version", nullable = false, updatable = false)
	private String fillModelVersion;

	protected PracticeClose() {
		// for JPA
	}

	PracticeClose(TradePlan plan, Fill fill, long tickValueCents, FillModel model) {
		this.sessionId = plan.getSessionId();
		this.seq = plan.getSeq();
		this.exitTime = fill.exitTime();
		this.exitTicks = fill.exitTicks();
		this.pnlTicks = fill.pnlTicks();
		this.netPnlCents = fill.netPnlCents(tickValueCents, plan.getContracts(), model);
		this.commissionCents = model.commissionCentsPerContractRound() * plan.getContracts();
		this.maeTicks = fill.maeTicks();
		this.mfeTicks = fill.mfeTicks();
		this.resolution = fill.resolution().name();
		this.engineVersion = FillEngine.VERSION;
		this.fillModelVersion = model.version();
	}

	public UUID getSessionId() {
		return sessionId;
	}

	public int getSeq() {
		return seq;
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
