package com.tradeez.marketdata.databento;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Every download and what it cost ({@code market_data_import}, V7).
 *
 * <p>The spending caps are enforced against this table, so the free credit is accounted for in the
 * database rather than remembered. An import that failed part-way still counts as spent: Databento
 * bills for bytes delivered, and a partial download delivered some.
 */
@Repository
public class ImportLedger {

	public record Request(UUID instrumentId, String source, String dataset, String symbol,
			String resolution, Instant from, Instant to) {
	}

	private final JdbcTemplate jdbc;

	public ImportLedger(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Dollars already committed: everything attempted, whether it finished or not. */
	public BigDecimal spentUsd() {
		BigDecimal spent = jdbc.queryForObject("""
			SELECT coalesce(sum(estimated_cost_usd), 0) FROM market_data_import
			WHERE status IN ('RUNNING', 'DONE', 'FAILED')""", BigDecimal.class);
		return spent == null ? BigDecimal.ZERO : spent;
	}

	/** True when this exact stretch has already been imported completely. */
	public boolean covered(UUID instrumentId, String resolution, Instant from, Instant to) {
		Boolean covered = jdbc.queryForObject("""
			SELECT exists (SELECT 1 FROM market_data_import
			    WHERE instrument_id = ? AND resolution = ? AND status = 'DONE'
			      AND range_from <= ? AND range_to >= ?)""",
			Boolean.class, instrumentId, resolution, Timestamp.from(from), Timestamp.from(to));
		return Boolean.TRUE.equals(covered);
	}

	public UUID start(Request request, BigDecimal estimatedCostUsd) {
		return insert(request, estimatedCostUsd, "RUNNING", null);
	}

	public void refused(Request request, BigDecimal estimatedCostUsd, String reason) {
		UUID id = insert(request, estimatedCostUsd, "REFUSED", reason);
		jdbc.update("UPDATE market_data_import SET finished_at = now() WHERE id = ?", id);
	}

	public void done(UUID id, long barsStored) {
		jdbc.update("""
			UPDATE market_data_import SET status = 'DONE', bars_stored = ?, finished_at = now()
			WHERE id = ?""", barsStored, id);
	}

	public void failed(UUID id, long barsStored, String error) {
		jdbc.update("""
			UPDATE market_data_import SET status = 'FAILED', bars_stored = ?, error = ?,
			    finished_at = now()
			WHERE id = ?""", barsStored, error, id);
	}

	private UUID insert(Request r, BigDecimal cost, String status, String error) {
		return jdbc.queryForObject("""
			INSERT INTO market_data_import (instrument_id, source, dataset, symbol, resolution,
			    range_from, range_to, estimated_cost_usd, status, error)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			RETURNING id""", UUID.class,
			r.instrumentId(), r.source(), r.dataset(), r.symbol(), r.resolution(),
			Timestamp.from(r.from()), Timestamp.from(r.to()), cost, status, error);
	}
}
