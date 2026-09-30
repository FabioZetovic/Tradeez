package com.tradeez.analysis;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.DataClass;
import com.tradeez.marketdata.DemoSeriesGenerator;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;

/**
 * Runs the FVG detector over a stretch of market and stores every hit (DEC-026).
 *
 * <p>"Scan history once and store every hit" is done lazily: a stretch is scanned when something
 * first needs it, and scanning it again is harmless — the insert ignores rows that already exist.
 * For a simulated market, which is infinite in both directions, scanning on demand is the only
 * version of "all of history" that finishes.
 *
 * <p>BUG-033's lesson applies: bars are generated before any transaction opens, and the write is a
 * single short batch.
 */
@Service
public class SetupScanner {

	private static final String UPSERT = """
		INSERT INTO detected_setup (instrument_id, data_class, seed, generator_version, timeframe,
		    detector, rule_version, params, methodology_source, direction,
		    start_time, anchor_time, end_time, zone_low_ticks, zone_high_ticks, evidence)
		VALUES (?, ?::data_class, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
		ON CONFLICT ON CONSTRAINT uq_detected_setup DO NOTHING""";

	private final MarketDataService marketData;
	private final DetectedSetupRepository setups;
	private final JdbcTemplate jdbc;
	private final ObjectMapper json;
	private final TransactionTemplate writeTx;

	public SetupScanner(MarketDataService marketData, DetectedSetupRepository setups,
			JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager transactionManager) {
		this.marketData = marketData;
		this.setups = setups;
		this.jdbc = jdbc;
		this.json = json;
		this.writeTx = new TransactionTemplate(transactionManager);
	}

	/**
	 * Scans {@code [from, to)} of a simulated market and returns every stored FVG anchored in it.
	 *
	 * <p>Candles before {@code from} are fetched for the detector's lookback, so a gap whose first
	 * candle sits at {@code from} is still evaluated rather than silently skipped.
	 */
	public List<DetectedSetup> scanFvgs(Instrument instrument, Timeframe timeframe, long seed,
			Instant from, Instant to) {
		if (instrument.getDataClass() != DataClass.SIMULATED) {
			throw new IllegalArgumentException("Scanning real data is not built yet.");
		}
		FvgDetector.Parameters parameters = FvgDetector.Parameters.DEFAULT;
		Instant historyFrom = from.minus(
			timeframe.duration().multipliedBy(parameters.averageRangeLookback() + 1L));

		List<Bar> bars = marketData.internalBars(instrument, timeframe, historyFrom, to, seed).bars();
		List<FvgDetector.Hit> hits = FvgDetector.detect(bars, timeframe, parameters);

		String params = write(parameters.asMap());
		writeTx.executeWithoutResult(tx -> jdbc.batchUpdate(UPSERT, hits, hits.size(), (ps, hit) -> {
			ps.setObject(1, instrument.getId());
			ps.setString(2, DataClass.SIMULATED.name());
			ps.setLong(3, seed);
			ps.setString(4, DemoSeriesGenerator.GENERATOR_VERSION);
			ps.setString(5, timeframe.code());
			ps.setString(6, FvgDetector.DETECTOR);
			ps.setString(7, FvgDetector.RULE_VERSION);
			ps.setString(8, params);
			ps.setString(9, FvgDetector.METHODOLOGY_SOURCE);
			ps.setString(10, hit.direction().name());
			ps.setTimestamp(11, Timestamp.from(hit.candle1().openTime()));
			ps.setTimestamp(12, Timestamp.from(hit.anchorTime()));
			ps.setTimestamp(13, Timestamp.from(hit.candle3().openTime()));
			ps.setLong(14, hit.zoneLowTicks());
			ps.setLong(15, hit.zoneHighTicks());
			ps.setString(16, write(hit.evidence()));
		}));

		return setups.findSimulated(instrument.getId(), seed, DemoSeriesGenerator.GENERATOR_VERSION,
				timeframe.code(), FvgDetector.DETECTOR, FvgDetector.RULE_VERSION, from, to)
			.stream()
			.filter(s -> parameters.asMap().equals(normalise(s.getParams())))
			.toList();
	}

	/** JSONB hands numbers back as whatever type Jackson picks; compare them as the ints they are. */
	private static Map<String, Object> normalise(Map<String, Object> params) {
		Map<String, Object> out = new LinkedHashMap<>();
		params.forEach((k, v) -> out.put(k, v instanceof Number n ? n.intValue() : v));
		return out;
	}

	private String write(Object value) {
		try {
			return json.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not serialise detector output", e);
		}
	}
}
