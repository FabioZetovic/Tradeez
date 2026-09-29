package com.tradeez.practice;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A hidden moment in a market, replayed forward. Maps {@code practice_session} from V5.
 *
 * <p>DEC-031 — everything about a replay's progress lives in this row. The cursor is a column, not a
 * field on a long-lived server object, so a restart or a dropped connection cannot lose it.
 */
@Entity
@Table(name = "practice_session")
public class PracticeSession {

	public enum Status {
		/** Looking at history, no trade yet. */
		ANALYSING,
		/** A plan is locked and resolved; the result is hidden until the cursor passes its exit. */
		IN_TRADE,
		/** The cursor has passed the exit. The result, and the seed, may now be shown. */
		COMPLETE
	}

	@Id
	@GeneratedValue
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "instrument_id", nullable = false, updatable = false)
	private UUID instrumentId;

	@Column(name = "timeframe", nullable = false, updatable = false)
	private String timeframe;

	/** DEC-038 — the key to this market's future. Never leaves the server before COMPLETE. */
	@Column(name = "seed", nullable = false, updatable = false)
	private long seed;

	@Column(name = "generator_version", nullable = false, updatable = false)
	private String generatorVersion;

	@Column(name = "history_from", nullable = false, updatable = false)
	private Instant historyFrom;

	@Column(name = "start_time", nullable = false, updatable = false)
	private Instant startTime;

	@Column(name = "cursor_time", nullable = false)
	private Instant cursorTime;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private Status status;

	/**
	 * Optimistic lock. Two "advance" requests racing on the same session must not both move the
	 * cursor from the same starting point; the second commit fails instead.
	 */
	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PracticeSession() {
		// for JPA
	}

	public PracticeSession(UUID userId, UUID instrumentId, String timeframe, long seed,
			String generatorVersion, Instant historyFrom, Instant startTime) {
		this.userId = userId;
		this.instrumentId = instrumentId;
		this.timeframe = timeframe;
		this.seed = seed;
		this.generatorVersion = generatorVersion;
		this.historyFrom = historyFrom;
		this.startTime = startTime;
		this.cursorTime = startTime;
		this.status = Status.ANALYSING;
		// Set here rather than only in a lifecycle callback — BUG-024.
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** Moves the replay forward. Never backward: rewinding past a loss would be a free retry. */
	void moveCursorTo(Instant next) {
		if (next.isBefore(cursorTime)) {
			throw new IllegalStateException("The replay cursor only moves forward.");
		}
		this.cursorTime = next;
	}

	void setStatus(Status status) {
		this.status = status;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getInstrumentId() {
		return instrumentId;
	}

	public String getTimeframe() {
		return timeframe;
	}

	public long getSeed() {
		return seed;
	}

	public String getGeneratorVersion() {
		return generatorVersion;
	}

	public Instant getHistoryFrom() {
		return historyFrom;
	}

	public Instant getStartTime() {
		return startTime;
	}

	public Instant getCursorTime() {
		return cursorTime;
	}

	public Status getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	/** Read to detect a change between a snapshot and the write that follows it (BUG-033). */
	public long getVersion() {
		return version;
	}
}
