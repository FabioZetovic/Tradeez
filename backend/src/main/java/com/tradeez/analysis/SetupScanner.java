package com.tradeez.analysis;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.DataClass;
import com.tradeez.marketdata.DemoSeriesGenerator;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;

/**
 * Runs detectors over a stretch of market and stores every hit (DEC-026, DEC-049).
 *
 * <p>"Scan history once and store every hit" is done lazily: a stretch is scanned when something
 * first needs it, and scanning it again is harmless — the insert ignores rows that already exist.
 * For a simulated market, which is infinite in both directions, scanning on demand is the only
 * version of "all of history" that finishes; for real data it means the first look at a week pays
 * for it, and every later look reads it back.
 *
 * <p>The candles are split at contract rolls before any detector sees them ({@link Segments}), so no
 * rule can measure across two contracts. BUG-033's lesson applies: candles are generated or read
 * before any transaction opens, and the write is a single short batch.
 */
@Service
public class SetupScanner {

	private static final String UPSERT = """
		INSERT INTO detected_setup (instrument_id, data_class, seed, generator_version, timeframe,
		    detector, rule_version, params, methodology_source, shape, direction, kind,
		    start_time, anchor_time, end_time, confirmed_at, zone_low_ticks, zone_high_ticks, evidence)
		VALUES (?, ?::data_class, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
		ON CONFLICT ON CONSTRAINT uq_detected_setup DO NOTHING""";

	private static final String COUNT = """
		SELECT count(*) FROM detected_setup
		WHERE instrument_id = ? AND seed IS NOT DISTINCT FROM ? AND generator_version IS NOT DISTINCT FROM ?
		  AND timeframe = ? AND detector = ? AND rule_version = ? AND confirmed_at BETWEEN ? AND ?""";

	/**
	 * How far back past a detector's own lookback the scanner reaches for its context when the
	 * market was shut: further than a weekend plus a holiday, so a Monday-morning pattern measures
	 * itself against Friday the same way whichever stretch happened to be scanned first.
	 */
	private static final Duration CLOSED_REACH = Duration.ofDays(10);

	/**
	 * Ranges already scanned in this process, so a replay that asks on every step does not rescan
	 * its window each time. Each is remembered with how many hits it held afterwards and how many
	 * one-minute bars were stored under it: a range whose rows were deleted since, or whose market
	 * was imported since, is scanned again rather than trusted. Bounded; forgetting one costs only a
	 * rescan that inserts nothing new.
	 */
	private final Map<String, Scan> scanned = Collections.synchronizedMap(
		new LinkedHashMap<>(64, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Scan> eldest) {
				return size() > 1_000;
			}
		});

	private record Scan(int hits, int minutes) {
	}

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
	 * Makes sure every hit of {@code detector} confirmed in {@code [from, to]} is stored.
	 *
	 * <p>DEC-038: {@code seed} is a simulated market's secret; never pass one that came from a
	 * request. Real data ignores it.
	 */
	public void ensureScanned(Detector detector, Instrument instrument, Timeframe timeframe, long seed,
			Instant from, Instant to) {
		boolean simulated = instrument.getDataClass() == DataClass.SIMULATED;
		Long rowSeed = simulated ? seed : null;
		String key = String.join("|", instrument.getId().toString(), String.valueOf(rowSeed),
			timeframe.code(), detector.id(), detector.ruleVersion(), from.toString(), to.toString());
		Instant reach = from.minus(timeframe.duration().multipliedBy(detector.lookbackBars(timeframe)))
			.minus(CLOSED_REACH);
		int minutes = simulated ? 0 : marketData.storedMinutes(instrument, reach, to);
		Scan remembered = scanned.get(key);
		if (remembered != null
				&& remembered.equals(new Scan(count(detector, instrument, timeframe, rowSeed, from, to), minutes))) {
			return;
		}

		List<Bar> bars = new ArrayList<>(history(detector, instrument, timeframe, seed, from));
		bars.addAll(marketData.internalBars(instrument, timeframe, from, to, seed).bars());
		Instant first = bars.isEmpty() ? from : bars.getFirst().openTime();
		List<Detection> found = new ArrayList<>();
		for (List<Bar> segment : Segments.atRolls(bars, marketData.rolls(instrument, first, to))) {
			for (Detection detection : detector.detect(segment, timeframe)) {
				if (!detection.confirmedAt().isAfter(to) && !detection.confirmedAt().isBefore(from)) {
					found.add(detection);
				}
			}
		}

		String params = write(detector.parameters());
		writeTx.executeWithoutResult(tx -> jdbc.batchUpdate(UPSERT, found, found.size(), (ps, hit) -> {
			ps.setObject(1, instrument.getId());
			ps.setString(2, instrument.getDataClass().name());
			ps.setObject(3, rowSeed);
			ps.setString(4, simulated ? DemoSeriesGenerator.GENERATOR_VERSION : null);
			ps.setString(5, timeframe.code());
			ps.setString(6, detector.id());
			ps.setString(7, detector.ruleVersion());
			ps.setString(8, params);
			ps.setString(9, detector.methodologySource());
			ps.setString(10, hit.shape().name());
			ps.setString(11, hit.direction().name());
			ps.setString(12, hit.kind());
			ps.setTimestamp(13, Timestamp.from(hit.start()));
			ps.setTimestamp(14, Timestamp.from(hit.anchor()));
			ps.setTimestamp(15, Timestamp.from(hit.end()));
			ps.setTimestamp(16, Timestamp.from(hit.confirmedAt()));
			ps.setLong(17, hit.lowTicks());
			ps.setLong(18, hit.highTicks());
			ps.setString(19, write(hit.evidence()));
		}));
		scanned.put(key, new Scan(count(detector, instrument, timeframe, rowSeed, from, to), minutes));
	}

	/**
	 * Stored hits touching {@code [from, to)} that were confirmed by {@code confirmedBy}: what may be
	 * shown at that moment. Nothing confirmed after it is returned, whatever was scanned (DEC-009).
	 */
	public List<DetectedSetup> visible(Detector detector, Instrument instrument, Timeframe timeframe,
			long seed, Instant from, Instant to, Instant confirmedBy) {
		List<DetectedSetup> rows = instrument.getDataClass() == DataClass.SIMULATED
			? setups.findVisibleSimulated(instrument.getId(), seed, DemoSeriesGenerator.GENERATOR_VERSION,
				timeframe.code(), detector.id(), detector.ruleVersion(), from, to, confirmedBy)
			: setups.findVisibleReal(instrument.getId(), timeframe.code(), detector.id(),
				detector.ruleVersion(), from, to, confirmedBy);
		Map<String, Object> parameters = normalise(detector.parameters());
		return rows.stream().filter(s -> parameters.equals(normalise(s.getParams()))).toList();
	}

	/**
	 * The {@link Detector#lookbackBars} candles before {@code from}, however long the market was
	 * shut before it. Exactly that many, so what is found from {@code from} on never depends on how
	 * much earlier a scan happened to start (DEC-049).
	 */
	private List<Bar> history(Detector detector, Instrument instrument, Timeframe timeframe, long seed,
			Instant from) {
		int needed = detector.lookbackBars(timeframe);
		Instant near = from.minus(timeframe.duration().multipliedBy(needed));
		List<Bar> bars = marketData.internalBars(instrument, timeframe, near, from, seed).bars();
		if (bars.size() < needed) {
			List<Bar> wider = new ArrayList<>(
				marketData.internalBars(instrument, timeframe, near.minus(CLOSED_REACH), near, seed).bars());
			wider.addAll(bars);
			bars = wider;
		}
		return bars.subList(Math.max(0, bars.size() - needed), bars.size());
	}

	private int count(Detector detector, Instrument instrument, Timeframe timeframe, Long seed,
			Instant from, Instant to) {
		Integer n = jdbc.queryForObject(COUNT, Integer.class, instrument.getId(), seed,
			seed == null ? null : DemoSeriesGenerator.GENERATOR_VERSION, timeframe.code(), detector.id(),
			detector.ruleVersion(), Timestamp.from(from), Timestamp.from(to));
		return n == null ? 0 : n;
	}

	/** JSONB hands numbers back as whatever type Jackson picks; compare them as the whole numbers they are. */
	private static Map<String, Object> normalise(Map<String, Object> params) {
		Map<String, Object> out = new LinkedHashMap<>();
		params.forEach((k, v) -> out.put(k, v instanceof Number n ? n.longValue() : v));
		return out;
	}

	private String write(Object value) {
		try {
			return json.writeValueAsString(value);
		} catch (JacksonException e) {
			throw new IllegalStateException("Could not serialise detector output", e);
		}
	}
}
