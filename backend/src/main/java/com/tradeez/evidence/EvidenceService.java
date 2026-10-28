package com.tradeez.evidence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.tradeez.analysis.Detector;
import com.tradeez.analysis.DetectorRegistry;
import com.tradeez.analysis.EvidenceCalculator;
import com.tradeez.analysis.EvidenceCalculator.RuleEvidence;
import com.tradeez.analysis.Segments;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.DataClass;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Evidence (T21, DEC-065): every rule counted over the whole imported history of a real market, on the
 * timeframes learners use, and kept in {@code rule_evidence} until the data or the rule changes.
 *
 * <p>A minute-by-minute history of two years is read once per timeframe and every rule is run over it,
 * split at contract rolls. Seconds of work per timeframe, so it is done in the background after start-up
 * ({@link EvidenceRefresher}) rather than while a learner waits. Simulated markets are never counted
 * (INVARIANT 8b).
 */
@Service
public class EvidenceService {

	private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);

	/** The timeframes drills and practice use (1m to 15m), and the hour for a wider view. */
	public static final List<Timeframe> TIMEFRAMES = List.of(Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.H1);

	private static final String UPSERT = """
		INSERT INTO rule_evidence (instrument_id, timeframe, detector, rule_version, evidence_version, data_from,
		                           data_to, candles, body, computed_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
		ON CONFLICT (instrument_id, timeframe, detector, rule_version, evidence_version) DO UPDATE
		SET data_from = EXCLUDED.data_from, data_to = EXCLUDED.data_to, candles = EXCLUDED.candles,
		    body = EXCLUDED.body, computed_at = EXCLUDED.computed_at""";

	private final MarketDataService marketData;
	private final DetectorRegistry detectors;
	private final JdbcTemplate jdbc;
	private final ObjectMapper json;

	public EvidenceService(MarketDataService marketData, DetectorRegistry detectors, JdbcTemplate jdbc,
			ObjectMapper json) {
		this.marketData = marketData;
		this.detectors = detectors;
		this.jdbc = jdbc;
		this.json = json;
	}

	/** A rule's evidence on one timeframe, as stored. */
	public record Stored(Timeframe timeframe, Instant computedAt, RuleEvidence evidence) {
	}

	/**
	 * Counts every rule on every evidence timeframe whose stored counts are missing or older than the data,
	 * or were made by another version of the rule or of the counting.
	 *
	 * @return how many rule-and-timeframe counts were made
	 */
	public int refresh(Instrument instrument) {
		if (instrument.getDataClass() == DataClass.SIMULATED) {
			return 0;
		}
		Instant[] coverage = marketData.coverage(instrument);
		if (coverage == null) {
			return 0;
		}
		int made = 0;
		for (Timeframe timeframe : TIMEFRAMES) {
			List<Detector> due = detectors.all().stream()
				.filter(d -> d.runsOn(timeframe) && !fresh(instrument, timeframe, d, coverage[1]))
				.toList();
			if (due.isEmpty()) {
				continue;
			}
			long started = System.nanoTime();
			List<Bar> bars = marketData.internalBars(instrument, timeframe, coverage[0], coverage[1], 0).bars();
			List<Instant> rolls = marketData.rolls(instrument, coverage[0], coverage[1]);
			List<List<Bar>> segments = Segments.atRolls(bars, rolls);
			for (Detector detector : due) {
				String other = detector.companionOf(instrument.getSymbol());
				store(instrument, timeframe, detector, other == null
					? EvidenceCalculator.measure(detector, instrument.getSymbol(), timeframe, segments,
						segment -> Detector.Companion.NONE)
					: beside(instrument, timeframe, detector, marketData.requireInstrument(other), bars, rolls, coverage),
					coverage[1]);
				made++;
			}
			log.info("Evidence for {} on {}: {} rules over {} candles in {} ms.", instrument.getSymbol(), timeframe.code(),
				due.size(), bars.size(), (System.nanoTime() - started) / 1_000_000);
		}
		return made;
	}

	/**
	 * A rule that reads a second market (T20), counted over the same minutes of it: the candles split at both
	 * markets' rolls. Counted again when this market's data changes; the other's alone does not trigger it.
	 */
	private RuleEvidence beside(Instrument instrument, Timeframe timeframe, Detector detector, Instrument companion,
			List<Bar> bars, List<Instant> rolls, Instant[] coverage) {
		List<Bar> theirs = marketData.internalBars(companion, timeframe, coverage[0], coverage[1], 0).bars();
		List<Instant> splits = Segments.merged(rolls, marketData.rolls(companion, coverage[0], coverage[1]));
		return EvidenceCalculator.measure(detector, instrument.getSymbol(), timeframe, Segments.atRolls(bars, splits),
			segment -> Segments.beside(segment, companion.getSymbol(), theirs));
	}

	/** Every stored count of one rule on a market, finest timeframe first. */
	public List<Stored> forRule(Instrument instrument, Detector detector) {
		List<Stored> out = new ArrayList<>();
		for (Timeframe timeframe : TIMEFRAMES) {
			read(instrument, timeframe, detector).ifPresent(out::add);
		}
		return out;
	}

	private Optional<Stored> read(Instrument instrument, Timeframe timeframe, Detector detector) {
		return jdbc.query("""
				SELECT body, computed_at FROM rule_evidence
				WHERE instrument_id = ? AND timeframe = ? AND detector = ? AND rule_version = ? AND evidence_version = ?""",
			(rs, i) -> new Stored(timeframe, rs.getTimestamp(2).toInstant(), parse(rs.getString(1))),
			instrument.getId(), timeframe.code(), detector.id(), detector.ruleVersion(), EvidenceCalculator.VERSION)
			.stream().findFirst();
	}

	/** Counted by this version of the rule and of the counting, up to the end of the data. */
	private boolean fresh(Instrument instrument, Timeframe timeframe, Detector detector, Instant dataEnd) {
		Integer n = jdbc.queryForObject("""
				SELECT count(*) FROM rule_evidence
				WHERE instrument_id = ? AND timeframe = ? AND detector = ? AND rule_version = ? AND evidence_version = ?
				  AND data_to >= ?""",
			Integer.class, instrument.getId(), timeframe.code(), detector.id(), detector.ruleVersion(),
			EvidenceCalculator.VERSION, Timestamp.from(dataEnd.minus(timeframe.duration())));
		return n != null && n > 0;
	}

	private void store(Instrument instrument, Timeframe timeframe, Detector detector, RuleEvidence evidence,
			Instant dataEnd) {
		jdbc.update(UPSERT, instrument.getId(), timeframe.code(), detector.id(), detector.ruleVersion(),
			EvidenceCalculator.VERSION, Timestamp.from(evidence.candles() == 0 ? dataEnd : evidence.from()),
			Timestamp.from(evidence.candles() == 0 ? dataEnd : evidence.to()), evidence.candles(), write(evidence));
	}

	private String write(RuleEvidence evidence) {
		try {
			return json.writeValueAsString(evidence);
		} catch (JacksonException e) {
			throw new IllegalStateException("Could not serialise evidence", e);
		}
	}

	private RuleEvidence parse(String body) {
		try {
			return json.readValue(body, RuleEvidence.class);
		} catch (JacksonException e) {
			throw new IllegalStateException("Stored evidence could not be read", e);
		}
	}
}
