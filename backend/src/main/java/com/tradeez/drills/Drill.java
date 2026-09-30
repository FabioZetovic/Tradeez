package com.tradeez.drills;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tradeez.marketdata.DataClass;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * One drill: a window of market, the answer key, and — once given — the learner's answer.
 * Maps {@code drill} and {@code drill_setup} from V6.
 */
@Entity
@Table(name = "drill")
public class Drill {

	public static final String FVG_IDENTIFICATION = "FVG_IDENTIFICATION";

	@Id
	@GeneratedValue
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "kind", nullable = false, updatable = false)
	private String kind;

	@Column(name = "instrument_id", nullable = false, updatable = false)
	private UUID instrumentId;

	@Column(name = "timeframe", nullable = false, updatable = false)
	private String timeframe;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "data_class", nullable = false, updatable = false)
	private DataClass dataClass;

	@Column(name = "seed", updatable = false)
	private Long seed;

	@Column(name = "generator_version", updatable = false)
	private String generatorVersion;

	@Column(name = "detector", nullable = false, updatable = false)
	private String detector;

	@Column(name = "rule_version", nullable = false, updatable = false)
	private String ruleVersion;

	@Column(name = "window_from", nullable = false, updatable = false)
	private Instant windowFrom;

	@Column(name = "window_to", nullable = false, updatable = false)
	private Instant windowTo;

	/** DEC-034: always false for simulated data, and the database refuses anything else. */
	@Column(name = "graded", nullable = false, updatable = false)
	private boolean graded;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "marks")
	private List<Long> marks;

	@Column(name = "answered_at")
	private Instant answeredAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** The answer key. Never sent to the client before the drill is answered. */
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "drill_setup", joinColumns = @JoinColumn(name = "drill_id"))
	@Column(name = "setup_id", nullable = false)
	private Set<UUID> setupIds = new HashSet<>();

	protected Drill() {
		// for JPA
	}

	public Drill(UUID userId, String kind, UUID instrumentId, String timeframe, DataClass dataClass,
			Long seed, String generatorVersion, String detector, String ruleVersion,
			Instant windowFrom, Instant windowTo, Set<UUID> setupIds) {
		this.userId = userId;
		this.kind = kind;
		this.instrumentId = instrumentId;
		this.timeframe = timeframe;
		this.dataClass = dataClass;
		this.seed = seed;
		this.generatorVersion = generatorVersion;
		this.detector = detector;
		this.ruleVersion = ruleVersion;
		this.windowFrom = windowFrom;
		this.windowTo = windowTo;
		this.graded = false;
		this.setupIds = new HashSet<>(setupIds);
		this.createdAt = Instant.now();
	}

	/** Records the answer. Once only: the service checks, and a database trigger enforces it. */
	void answer(List<Long> marks) {
		if (answeredAt != null) {
			throw new IllegalStateException("Drill " + id + " is already answered");
		}
		this.marks = List.copyOf(marks);
		this.answeredAt = Instant.now();
	}

	public boolean isAnswered() {
		return answeredAt != null;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getKind() {
		return kind;
	}

	public UUID getInstrumentId() {
		return instrumentId;
	}

	public String getTimeframe() {
		return timeframe;
	}

	public DataClass getDataClass() {
		return dataClass;
	}

	public Long getSeed() {
		return seed;
	}

	public String getGeneratorVersion() {
		return generatorVersion;
	}

	public String getDetector() {
		return detector;
	}

	public String getRuleVersion() {
		return ruleVersion;
	}

	public Instant getWindowFrom() {
		return windowFrom;
	}

	public Instant getWindowTo() {
		return windowTo;
	}

	public boolean isGraded() {
		return graded;
	}

	public List<Long> getMarks() {
		return marks;
	}

	public Instant getAnsweredAt() {
		return answeredAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Set<UUID> getSetupIds() {
		return setupIds;
	}
}
