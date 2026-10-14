package com.tradeez.marketdata;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Real bars, stored as they were printed ({@code market_bar}, V7).
 *
 * <p>Plain JDBC rather than JPA: bars are written in batches of thousands and read in ranges of
 * thousands, never edited one at a time, and an entity per bar would cost memory and time for no
 * benefit.
 */
@Repository
public class MarketBarStore {

	/** A bar together with the contract it actually traded in (DEC-024: raw, never adjusted). */
	public record StoredBar(Bar bar, long contractId) {
	}

	private static final String INSERT = """
		INSERT INTO market_bar (instrument_id, resolution, open_time, open_ticks, high_ticks,
		    low_ticks, close_ticks, volume, contract_id)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
		ON CONFLICT (instrument_id, resolution, open_time) DO NOTHING""";

	private final JdbcTemplate jdbc;

	public MarketBarStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Stores bars, skipping any already present, so an interrupted import can simply be run again.
	 *
	 * @return how many were new
	 */
	public int insert(UUID instrumentId, String resolution, List<StoredBar> bars) {
		if (bars.isEmpty()) {
			return 0;
		}
		int[][] counts = jdbc.batchUpdate(INSERT, bars, bars.size(), (ps, stored) -> {
			Bar b = stored.bar();
			ps.setObject(1, instrumentId);
			ps.setString(2, resolution);
			ps.setTimestamp(3, Timestamp.from(b.openTime()));
			ps.setLong(4, b.openTicks());
			ps.setLong(5, b.highTicks());
			ps.setLong(6, b.lowTicks());
			ps.setLong(7, b.closeTicks());
			ps.setLong(8, b.volume());
			ps.setLong(9, stored.contractId());
		});
		return Arrays.stream(counts).flatMapToInt(Arrays::stream).map(c -> Math.max(c, 0)).sum();
	}

	/** How many bars open in {@code [from, to)}. */
	public int count(UUID instrumentId, String resolution, Instant from, Instant to) {
		Integer n = jdbc.queryForObject("""
				SELECT count(*) FROM market_bar
				WHERE instrument_id = ? AND resolution = ? AND open_time >= ? AND open_time < ?""",
			Integer.class, instrumentId, resolution, Timestamp.from(from), Timestamp.from(to));
		return n == null ? 0 : n;
	}

	/** Bars opening in {@code [from, to)}, in time order. */
	public List<Bar> read(UUID instrumentId, String resolution, Instant from, Instant to) {
		return jdbc.query("""
				SELECT open_time, open_ticks, high_ticks, low_ticks, close_ticks, volume
				FROM market_bar
				WHERE instrument_id = ? AND resolution = ? AND open_time >= ? AND open_time < ?
				ORDER BY open_time""",
			(rs, i) -> new Bar(rs.getTimestamp(1).toInstant(), rs.getLong(2), rs.getLong(3),
				rs.getLong(4), rs.getLong(5), rs.getLong(6)),
			instrumentId, resolution, Timestamp.from(from), Timestamp.from(to));
	}

	/** The first and last stored bar times, or null when nothing is stored. */
	public Instant[] extent(UUID instrumentId, String resolution) {
		return jdbc.query("""
				SELECT min(open_time), max(open_time) FROM market_bar
				WHERE instrument_id = ? AND resolution = ?""",
			rs -> {
				rs.next();
				Timestamp first = rs.getTimestamp(1);
				return first == null ? null
					: new Instant[] { first.toInstant(), rs.getTimestamp(2).toInstant() };
			},
			instrumentId, resolution);
	}
}
