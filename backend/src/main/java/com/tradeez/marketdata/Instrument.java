package com.tradeez.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
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

/**
 * A tradeable product. Maps {@code instrument} from V2__instruments.sql.
 *
 * <p>Everything that varies between contracts lives here rather than in code, so adding ES or CL
 * later is a row rather than a refactor. {@code tickSize} is the only decimal on the object and
 * exists solely to render an integer tick count as a human-readable price (INVARIANT 3).
 */
@Entity
@Table(name = "instrument")
public class Instrument {

	@Id
	@GeneratedValue
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "symbol", nullable = false, unique = true)
	private String symbol;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(name = "exchange", nullable = false)
	private String exchange;

	@Column(name = "currency", nullable = false)
	private String currency;

	@Column(name = "tick_size", nullable = false, precision = 19, scale = 9)
	private BigDecimal tickSize;

	@Column(name = "tick_value_cents", nullable = false)
	private long tickValueCents;

	@Column(name = "point_multiplier_cents", nullable = false)
	private long pointMultiplierCents;

	@Column(name = "exchange_timezone", nullable = false)
	private String exchangeTimezone;

	@Column(name = "session_open_local", nullable = false)
	private LocalTime sessionOpenLocal;

	@Column(name = "session_close_local", nullable = false)
	private LocalTime sessionCloseLocal;

	@Column(name = "maintenance_break_minutes", nullable = false)
	private int maintenanceBreakMinutes;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "data_class", nullable = false)
	private DataClass dataClass;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	/** Whether the app offers this instrument (V7). Withdrawn rows stay, so history stays intact. */
	@Column(name = "listed", nullable = false, insertable = false, updatable = false)
	private boolean listed;

	protected Instrument() {
		// for JPA
	}

	/** Renders a tick count as a price. The only place ticks become a decimal. */
	public BigDecimal ticksToPrice(long ticks) {
		return tickSize.multiply(BigDecimal.valueOf(ticks));
	}

	/**
	 * Exact P&L in cents for a movement of {@code ticks}.
	 *
	 * <p>Integer arithmetic throughout — this is the reason prices are stored as ticks at all
	 * (INVARIANT 3). No rounding, no accumulated floating-point drift, no result a trader cannot
	 * reconcile by hand.
	 */
	public long pnlCents(long ticks, int contracts) {
		return ticks * tickValueCents * contracts;
	}

	public UUID getId() {
		return id;
	}

	public String getSymbol() {
		return symbol;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getExchange() {
		return exchange;
	}

	public String getCurrency() {
		return currency;
	}

	public BigDecimal getTickSize() {
		return tickSize;
	}

	public long getTickValueCents() {
		return tickValueCents;
	}

	public long getPointMultiplierCents() {
		return pointMultiplierCents;
	}

	public String getExchangeTimezone() {
		return exchangeTimezone;
	}

	public LocalTime getSessionOpenLocal() {
		return sessionOpenLocal;
	}

	public LocalTime getSessionCloseLocal() {
		return sessionCloseLocal;
	}

	public int getMaintenanceBreakMinutes() {
		return maintenanceBreakMinutes;
	}

	public DataClass getDataClass() {
		return dataClass;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public boolean isListed() {
		return listed;
	}
}
