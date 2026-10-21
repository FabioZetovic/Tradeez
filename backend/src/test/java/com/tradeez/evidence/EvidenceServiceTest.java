package com.tradeez.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.ThreeWeeksOfNq;
import com.tradeez.analysis.Detector;
import com.tradeez.analysis.DetectorRegistry;
import com.tradeez.analysis.EvidenceCalculator;
import com.tradeez.analysis.Segments;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;

/**
 * Evidence over a whole imported history (T21, DEC-065), on {@link ThreeWeeksOfNq}: every rule on every
 * timeframe it reads, counted once and kept until the data or the rule changes — and never a simulated market.
 */
class EvidenceServiceTest extends AbstractIntegrationTest {

	@Autowired
	EvidenceService evidence;

	@Autowired
	MarketDataService marketData;

	@Autowired
	MarketBarStore barStore;

	@Autowired
	DetectorRegistry detectors;

	@Autowired
	JdbcTemplate jdbc;

	private Instrument nq;

	@BeforeEach
	void importThreeWeeks() {
		jdbc.update("DELETE FROM rule_evidence");
		nq = marketData.requireInstrument("NQ");
		if (!ThreeWeeksOfNq.present(marketData)) {
			jdbc.update("DELETE FROM drill_setup");
			jdbc.update("DELETE FROM drill");
			jdbc.update("DELETE FROM detected_setup");
			jdbc.update("DELETE FROM market_bar");
			jdbc.update("DELETE FROM market_data_import");
			ThreeWeeksOfNq.insert(marketData, barStore, jdbc);
		}
	}

	private int rows() {
		Integer n = jdbc.queryForObject("SELECT count(*) FROM rule_evidence", Integer.class);
		return n == null ? 0 : n;
	}

	@Test
	@DisplayName("every rule on every timeframe it reads is counted once; a second refresh counts nothing")
	void countsOnce() {
		long expected = EvidenceService.TIMEFRAMES.stream()
			.mapToLong(tf -> detectors.all().stream().filter(d -> d.runsOn(tf)).count()).sum();
		assertThat(evidence.refresh(nq)).isEqualTo(expected);
		assertThat(rows()).isEqualTo(expected);
		// The daily profile reads 30 minutes or less: nothing is stored for it on the hour.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM rule_evidence WHERE detector = 'DAILY_PROFILE' AND timeframe = '1h'",
			Integer.class)).isZero();
		assertThat(evidence.refresh(nq)).isZero();
	}

	@Test
	@DisplayName("what is stored is what the rule finds over the history split at the roll, read back unchanged")
	void stored() {
		evidence.refresh(nq);
		Detector gaps = detectors.require("FVG_STATE");
		Instant[] coverage = marketData.coverage(nq);
		var segments = Segments.atRolls(marketData.internalBars(nq, Timeframe.M5, coverage[0], coverage[1], 0).bars(),
			marketData.rolls(nq, coverage[0], coverage[1]));
		assertThat(segments).as("the fixture's roll splits the history").hasSize(2);
		EvidenceCalculator.RuleEvidence expected = EvidenceCalculator.measure(gaps, Timeframe.M5, segments);

		List<EvidenceService.Stored> stored = evidence.forRule(nq, gaps);
		assertThat(stored).extracting(EvidenceService.Stored::timeframe)
			.containsExactly(Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.H1);
		assertThat(stored.get(1).evidence()).isEqualTo(expected);
		assertThat(expected.hits()).isPositive();
		assertThat(expected.lives().ended()).isPositive().isLessThanOrEqualTo(expected.hits());
	}

	@Test
	@DisplayName("counts made before the data grew, or by another rule version, are made again")
	void stale() {
		evidence.refresh(nq);
		jdbc.update("UPDATE rule_evidence SET data_to = data_to - interval '1 day' WHERE detector = 'OB' AND timeframe = '5m'");
		jdbc.update("UPDATE rule_evidence SET rule_version = 'swing-0.9.0' WHERE detector = 'SWING' AND timeframe = '15m'");
		assertThat(evidence.refresh(nq)).isEqualTo(2);
		// The old version's row is left as it was, and the current one is counted beside it.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM rule_evidence WHERE detector = 'SWING' AND timeframe = '15m'",
			Integer.class)).isEqualTo(2);
	}

	@Test
	@DisplayName("a simulated market is never counted (INVARIANT 8b)")
	void notSimulated() {
		assertThat(evidence.refresh(marketData.requireInstrument("DEMO-NQ"))).isZero();
		assertThat(rows()).isZero();
	}
}
