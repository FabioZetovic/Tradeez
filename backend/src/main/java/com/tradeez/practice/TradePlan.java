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

import com.tradeez.practice.FillEngine.Direction;

/**
 * The learner's committed trade. Maps {@code trade_plan} from V5.
 *
 * <p>DEC-013 — immutable. There are no setters, every column is {@code updatable = false}, and the
 * database refuses UPDATE with a trigger. Three layers, because the point of the decision is that a
 * plan cannot be quietly improved after the learner has seen what happened.
 */
@Entity
@Table(name = "trade_plan")
public class TradePlan {

	@Id
	@GeneratedValue
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "session_id", nullable = false, updatable = false)
	private UUID sessionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "direction", nullable = false, updatable = false)
	private Direction direction;

	@Column(name = "stop_ticks", nullable = false, updatable = false)
	private long stopTicks;

	@Column(name = "target_ticks", nullable = false, updatable = false)
	private long targetTicks;

	@Column(name = "contracts", nullable = false, updatable = false)
	private int contracts;

	@Column(name = "reference_ticks", nullable = false, updatable = false)
	private long referenceTicks;

	@Column(name = "submitted_at", nullable = false, updatable = false)
	private Instant submittedAt;

	@Column(name = "submitted_cursor", nullable = false, updatable = false)
	private Instant submittedCursor;

	protected TradePlan() {
		// for JPA
	}

	public TradePlan(UUID sessionId, Direction direction, long stopTicks, long targetTicks,
			int contracts, long referenceTicks, Instant submittedCursor) {
		this.sessionId = sessionId;
		this.direction = direction;
		this.stopTicks = stopTicks;
		this.targetTicks = targetTicks;
		this.contracts = contracts;
		this.referenceTicks = referenceTicks;
		this.submittedCursor = submittedCursor;
		this.submittedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getSessionId() {
		return sessionId;
	}

	public Direction getDirection() {
		return direction;
	}

	public long getStopTicks() {
		return stopTicks;
	}

	public long getTargetTicks() {
		return targetTicks;
	}

	public int getContracts() {
		return contracts;
	}

	public long getReferenceTicks() {
		return referenceTicks;
	}

	public Instant getSubmittedCursor() {
		return submittedCursor;
	}

}
