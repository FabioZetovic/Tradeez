package com.tradeez.practice;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.tradeez.practice.FillEngine.Direction;

/**
 * The learner's committed trade. Maps {@code trade_plan} from V5, numbered within its session since
 * V10: a replay holds several trades, one after another (DEC-046).
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

	/** 1, 2, 3… in the order the session's trades were locked. */
	@Column(name = "seq", nullable = false, updatable = false)
	private int seq;

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

	// T22 (DEC-066): what the learner meant, and what the app saw then. All optional.
	@Enumerated(EnumType.STRING)
	@Column(name = "model", updatable = false)
	private TradingModel model;

	@Column(name = "bias", updatable = false)
	private String bias;

	@Column(name = "dol_ticks", updatable = false)
	private Long dolTicks;

	@Column(name = "killzone", updatable = false)
	private String killzone;

	@Column(name = "suggested_bias", updatable = false)
	private String suggestedBias;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "checklist", updatable = false)
	private Map<String, Object> checklist;

	// T23 (DEC-067): how sure the learner was, and the risk limit they had set then. Optional.
	@Column(name = "confidence", updatable = false)
	private Integer confidence;

	@Column(name = "risk_limit_cents", updatable = false)
	private Long riskLimitCents;

	protected TradePlan() {
		// for JPA
	}

	public TradePlan(UUID sessionId, int seq, Direction direction, long stopTicks, long targetTicks,
			int contracts, long referenceTicks, Instant submittedCursor) {
		this.sessionId = sessionId;
		this.seq = seq;
		this.direction = direction;
		this.stopTicks = stopTicks;
		this.targetTicks = targetTicks;
		this.contracts = contracts;
		this.referenceTicks = referenceTicks;
		this.submittedCursor = submittedCursor;
		this.submittedAt = Instant.now();
	}

	/**
	 * A plan with what the learner meant and what the app saw when it was placed (T22), how sure they were and
	 * the risk limit they had set (T23).
	 */
	public TradePlan(UUID sessionId, int seq, Direction direction, long stopTicks, long targetTicks, int contracts,
			long referenceTicks, Instant submittedCursor, TradingModel model, String bias, Long dolTicks, String killzone,
			String suggestedBias, Map<String, Object> checklist, Integer confidence, Long riskLimitCents) {
		this(sessionId, seq, direction, stopTicks, targetTicks, contracts, referenceTicks, submittedCursor);
		this.model = model;
		this.bias = bias;
		this.dolTicks = dolTicks;
		this.killzone = killzone;
		this.suggestedBias = suggestedBias;
		this.checklist = checklist;
		this.confidence = confidence;
		this.riskLimitCents = riskLimitCents;
	}

	public UUID getId() {
		return id;
	}

	public UUID getSessionId() {
		return sessionId;
	}

	public int getSeq() {
		return seq;
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

	public TradingModel getModel() {
		return model;
	}

	public String getBias() {
		return bias;
	}

	public Long getDolTicks() {
		return dolTicks;
	}

	public String getKillzone() {
		return killzone;
	}

	public String getSuggestedBias() {
		return suggestedBias;
	}

	public Map<String, Object> getChecklist() {
		return checklist;
	}

	public Integer getConfidence() {
		return confidence;
	}

	public Long getRiskLimitCents() {
		return riskLimitCents;
	}

}
