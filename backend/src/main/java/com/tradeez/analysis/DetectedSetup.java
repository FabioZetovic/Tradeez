package com.tradeez.analysis;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tradeez.marketdata.DataClass;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One hit from one detector under one versioned rule. Maps {@code detected_setup} from V6.
 *
 * <p>Read-only from Java: rows are written by {@link SetupScanner} with an insert that ignores
 * duplicates, because two drills scanning overlapping stretches of market at the same moment must
 * not race each other into a unique-constraint failure.
 */
@Entity
@Table(name = "detected_setup")
public class DetectedSetup {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "instrument_id", nullable = false, updatable = false)
	private UUID instrumentId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "data_class", nullable = false, updatable = false)
	private DataClass dataClass;

	@Column(name = "seed", updatable = false)
	private Long seed;

	@Column(name = "generator_version", updatable = false)
	private String generatorVersion;

	@Column(name = "timeframe", nullable = false, updatable = false)
	private String timeframe;

	@Column(name = "detector", nullable = false, updatable = false)
	private String detector;

	@Column(name = "rule_version", nullable = false, updatable = false)
	private String ruleVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "params", nullable = false, updatable = false)
	private Map<String, Object> params;

	@Column(name = "methodology_source", nullable = false, updatable = false)
	private String methodologySource;

	@Column(name = "direction", nullable = false, updatable = false)
	private String direction;

	/** ZONE, LEVEL or POINT (V12). */
	@Column(name = "shape", nullable = false, updatable = false)
	private String shape;

	@Column(name = "start_time", nullable = false, updatable = false)
	private Instant startTime;

	@Column(name = "anchor_time", nullable = false, updatable = false)
	private Instant anchorTime;

	@Column(name = "end_time", nullable = false, updatable = false)
	private Instant endTime;

	/** When the hit could first be known (V12). A replay shows it only from here on (DEC-009). */
	@Column(name = "confirmed_at", nullable = false, updatable = false)
	private Instant confirmedAt;

	@Column(name = "zone_low_ticks", nullable = false, updatable = false)
	private long zoneLowTicks;

	@Column(name = "zone_high_ticks", nullable = false, updatable = false)
	private long zoneHighTicks;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "evidence", nullable = false, updatable = false)
	private Map<String, Object> evidence;

	@Column(name = "human_validated", nullable = false, updatable = false)
	private boolean humanValidated;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected DetectedSetup() {
		// for JPA
	}

	public UUID getId() {
		return id;
	}

	public String getShape() {
		return shape;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}

	public UUID getInstrumentId() {
		return instrumentId;
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

	public String getTimeframe() {
		return timeframe;
	}

	public String getDetector() {
		return detector;
	}

	public String getRuleVersion() {
		return ruleVersion;
	}

	public Map<String, Object> getParams() {
		return params;
	}

	public String getMethodologySource() {
		return methodologySource;
	}

	public String getDirection() {
		return direction;
	}

	public Instant getStartTime() {
		return startTime;
	}

	public Instant getAnchorTime() {
		return anchorTime;
	}

	public Instant getEndTime() {
		return endTime;
	}

	public long getZoneLowTicks() {
		return zoneLowTicks;
	}

	public long getZoneHighTicks() {
		return zoneHighTicks;
	}

	public Map<String, Object> getEvidence() {
		return evidence;
	}

	public boolean isHumanValidated() {
		return humanValidated;
	}
}
