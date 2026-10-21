package com.tradeez.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.ThreeWeeksOfEs;
import com.tradeez.ThreeWeeksOfNq;
import com.tradeez.evidence.EvidenceService;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;

/**
 * T20 (DEC-073) — smt-1.0.0 as the scanner runs it on stored data: NQ from {@link ThreeWeeksOfNq}, and beside
 * it an ES of another seed's minutes that rolls a day before NQ does, as the real ES did in December 2024 and
 * September 2025. For that day each market is on a different contract, so the candles split at both rolls.
 */
class SmtScanTest extends AbstractIntegrationTest {

	private static final Instant ES_ROLL = ThreeWeeksOfEs.ROLL;
	private static final Instant FROM = ThreeWeeksOfNq.FROM.plus(Duration.ofDays(3));
	private static final Instant TO = ThreeWeeksOfNq.TO.minus(Duration.ofDays(1));

	@Autowired
	SetupScanner scanner;

	@Autowired
	MarketDataService marketData;

	@Autowired
	MarketBarStore barStore;

	@Autowired
	DetectorRegistry detectors;

	@Autowired
	EvidenceService evidence;

	@Autowired
	JdbcTemplate jdbc;

	private Instrument nq;
	private Instrument es;

	@BeforeEach
	void importBoth() {
		nq = marketData.requireInstrument("NQ");
		es = marketData.requireInstrument("ES");
		if (!ThreeWeeksOfNq.present(marketData)) {
			jdbc.update("DELETE FROM drill_setup");
			jdbc.update("DELETE FROM drill");
			jdbc.update("DELETE FROM detected_setup");
			jdbc.update("DELETE FROM market_bar");
			jdbc.update("DELETE FROM market_data_import");
			ThreeWeeksOfNq.insert(marketData, barStore, jdbc);
		}
		removeEs();
		insertEs();
	}

	private void insertEs() {
		ThreeWeeksOfEs.insert(marketData, barStore);
	}

	/** Other classes share the database and know nothing of ES. */
	@AfterEach
	void removeEs() {
		ThreeWeeksOfEs.remove(marketData, jdbc);
	}

	/** The rule over the whole of both histories, split at both markets' rolls, as a scan reads it. */
	private List<Detection> expected(Instrument market, Instrument other, Detector smt, List<Instant> splits) {
		List<Bar> bars = marketData.internalBars(market, Timeframe.M5, ThreeWeeksOfNq.FROM, ThreeWeeksOfNq.TO, 0).bars();
		List<Bar> theirs = marketData.internalBars(other, Timeframe.M5, ThreeWeeksOfNq.FROM, ThreeWeeksOfNq.TO, 0).bars();
		List<Detection> found = new ArrayList<>();
		for (List<Bar> segment : Segments.atRolls(bars, splits)) {
			found.addAll(smt.detect(segment, Segments.beside(segment, other.getSymbol(), theirs), Timeframe.M5));
		}
		return found;
	}

	/** What a stored hit is compared on. */
	private record Row(Instant anchor, String direction, Instant confirmedAt, String companion) {
	}

	private List<Row> stored(Instrument market) {
		return jdbc.query("""
				SELECT anchor_time, direction, confirmed_at, evidence ->> 'companion' FROM detected_setup
				WHERE detector = 'SMT' AND instrument_id = ? ORDER BY confirmed_at, anchor_time""",
			(rs, i) -> new Row(rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getTimestamp(3).toInstant(),
				rs.getString(4)), market.getId());
	}

	/** Those confirmed within the scan, in the order {@link #stored} reads them. */
	private static List<Row> rows(List<Detection> detections) {
		return detections.stream().filter(d -> !d.confirmedAt().isBefore(FROM) && !d.confirmedAt().isAfter(TO))
			.map(d -> new Row(d.anchor(), d.direction().name(), d.confirmedAt(), (String) d.evidence().get("companion")))
			.sorted(Comparator.comparing(Row::confirmedAt).thenComparing(Row::anchor))
			.toList();
	}

	private static boolean spans(Detection d, Instant roll) {
		return d.start().isBefore(roll) && !d.end().isBefore(roll);
	}

	@Test
	@DisplayName("NQ is scanned beside ES over the same minutes, split at both markets' rolls, and nothing spans either")
	void nqBesideEs() {
		Detector smt = detectors.require("SMT");
		scanner.ensureScanned(smt, nq, Timeframe.M5, 0, FROM, TO);
		List<Detection> both = expected(nq, es, smt, List.of(ES_ROLL, ThreeWeeksOfNq.ROLL));
		assertThat(stored(nq)).isNotEmpty().isEqualTo(rows(both));
		assertThat(both).noneMatch(d -> spans(d, ES_ROLL) || spans(d, ThreeWeeksOfNq.ROLL));
		// Why both: split at NQ's roll alone, ES's jump to its next contract reads as a divergence.
		assertThat(expected(nq, es, smt, List.of(ThreeWeeksOfNq.ROLL))).anyMatch(d -> spans(d, ES_ROLL));
	}

	@Test
	@DisplayName("ES is scanned beside NQ the same way; a simulated market has no companion and no divergence")
	void esBesideNq() {
		Detector smt = detectors.require("SMT");
		scanner.ensureScanned(smt, es, Timeframe.M5, 0, FROM, TO);
		assertThat(stored(es)).isNotEmpty().isEqualTo(rows(expected(es, nq, smt, List.of(ES_ROLL, ThreeWeeksOfNq.ROLL))))
			.allSatisfy(row -> assertThat(row.companion()).isEqualTo("NQ"));
		Instrument demo = marketData.requireInstrument("DEMO-NQ");
		scanner.ensureScanned(smt, demo, Timeframe.M5, 9, FROM, FROM.plus(Duration.ofDays(1)));
		assertThat(stored(demo)).isEmpty();
	}

	@Test
	@DisplayName("a stretch scanned before ES was imported is scanned again once it is")
	void rescansWhenEsArrives() {
		Detector smt = detectors.require("SMT");
		removeEs();
		scanner.ensureScanned(smt, nq, Timeframe.M5, 0, FROM, TO);
		assertThat(stored(nq)).isEmpty();
		insertEs();
		scanner.ensureScanned(smt, nq, Timeframe.M5, 0, FROM, TO);
		assertThat(stored(nq)).isNotEmpty();
	}

	@Test
	@DisplayName("its evidence is counted beside ES the same way, over the whole of both histories")
	void evidence() {
		jdbc.update("DELETE FROM rule_evidence");
		evidence.refresh(nq);
		Detector smt = detectors.require("SMT");
		Instant[] coverage = marketData.coverage(nq);
		List<Bar> bars = marketData.internalBars(nq, Timeframe.M5, coverage[0], coverage[1], 0).bars();
		List<Bar> theirs = marketData.internalBars(es, Timeframe.M5, coverage[0], coverage[1], 0).bars();
		EvidenceCalculator.RuleEvidence expected = EvidenceCalculator.measure(smt, Timeframe.M5,
			Segments.atRolls(bars, List.of(ES_ROLL, ThreeWeeksOfNq.ROLL)), s -> Segments.beside(s, "ES", theirs));
		assertThat(expected.hits()).isPositive();
		assertThat(evidence.forRule(nq, smt)).filteredOn(s -> s.timeframe() == Timeframe.M5).singleElement()
			.extracting(EvidenceService.Stored::evidence).isEqualTo(expected);
		jdbc.update("DELETE FROM rule_evidence");
	}

	@Test
	@DisplayName("the two markets of a pair are quoted in the same tick, so the words print both at the chart's")
	void sameTick() {
		assertThat(es.getTickSize()).isEqualByComparingTo(nq.getTickSize());
	}
}
