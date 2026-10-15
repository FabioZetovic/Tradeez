package com.tradeez.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.DataClass;
import com.tradeez.marketdata.DemoSeriesGenerator;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;

/**
 * DEC-026 — scanning stores every hit, once, with what is needed to reproduce and explain it.
 */
class SetupScannerTest extends AbstractIntegrationTest {

	private static final Instant FROM = Instant.parse("2024-03-12T13:00:00Z");
	private static final Instant TO = Instant.parse("2024-03-12T20:00:00Z");

	@Autowired
	SetupScanner scanner;

	@Autowired
	MarketDataService marketData;

	@Autowired
	JdbcTemplate jdbc;

	private Instrument nq;

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM drill_setup");
		jdbc.update("DELETE FROM drill");
		jdbc.update("DELETE FROM detected_setup");
		nq = marketData.requireInstrument("DEMO-NQ");
	}

	@Test
	@DisplayName("stores exactly the detector's hits, each with rule, parameters and evidence")
	void storesWhatTheDetectorFinds() {
		List<DetectedSetup> stored = scanner.scanFvgs(nq, Timeframe.M5, 42, FROM, TO);

		// The same detector over the same bars, independently of the scanner.
		List<Bar> bars = marketData.internalBars(nq, Timeframe.M5,
			FROM.minusSeconds(15 * 300), TO, 42).bars();
		List<FvgDetector.Hit> expected = FvgDetector.detect(bars, Timeframe.M5,
				FvgDetector.Parameters.DEFAULT).stream()
			.filter(h -> !h.anchorTime().isBefore(FROM) && h.anchorTime().isBefore(TO))
			.toList();

		assertThat(stored).isNotEmpty();
		assertThat(stored).extracting(DetectedSetup::getAnchorTime)
			.containsExactlyElementsOf(expected.stream().map(FvgDetector.Hit::anchorTime).toList());

		DetectedSetup first = stored.getFirst();
		FvgDetector.Hit hit = expected.getFirst();
		assertThat(first.getRuleVersion()).isEqualTo(FvgDetector.RULE_VERSION);
		assertThat(first.getDetector()).isEqualTo("FVG");
		assertThat(first.getDataClass()).isEqualTo(DataClass.SIMULATED);
		assertThat(first.getSeed()).isEqualTo(42L);
		assertThat(first.getGeneratorVersion()).isEqualTo(DemoSeriesGenerator.GENERATOR_VERSION);
		assertThat(first.getParams()).containsEntry("averageRangeLookback", 14);
		assertThat(first.getMethodologySource()).contains("fvg-1.0.0");
		assertThat(first.getDirection()).isEqualTo(hit.direction().name());
		assertThat(first.getShape()).isEqualTo("ZONE");
		assertThat(first.getConfirmedAt()).isEqualTo(hit.candle3().openTime().plusSeconds(300));
		assertThat(first.getZoneLowTicks()).isEqualTo(hit.zoneLowTicks());
		assertThat(first.getZoneHighTicks()).isEqualTo(hit.zoneHighTicks());
		assertThat(((Number) first.getEvidence().get("gapTicks")).longValue()).isEqualTo(hit.gapTicks());
		assertThat(first.isHumanValidated()).isFalse();
	}

	@Test
	@DisplayName("scanning the same stretch again stores nothing new")
	void idempotent() {
		int first = scanner.scanFvgs(nq, Timeframe.M5, 42, FROM, TO).size();
		int stored = rows();
		int second = scanner.scanFvgs(nq, Timeframe.M5, 42, FROM, TO).size();

		assertThat(second).isEqualTo(first);
		assertThat(rows()).isEqualTo(stored);
	}

	@Test
	@DisplayName("a stretch whose rows were deleted is scanned again, not trusted from memory")
	void rescansWhatWasDeleted() {
		int first = scanner.scanFvgs(nq, Timeframe.M5, 42, FROM, TO).size();
		jdbc.update("DELETE FROM detected_setup");

		assertThat(scanner.scanFvgs(nq, Timeframe.M5, 42, FROM, TO)).hasSize(first);
	}

	@Test
	@DisplayName("two scans of overlapping stretches at once do not collide")
	void concurrentScans() {
		// Two drills picking overlapping windows at the same moment. The insert ignores duplicates,
		// so neither fails on the unique constraint.
		var a = CompletableFuture.supplyAsync(() -> scanner.scanFvgs(nq, Timeframe.M5, 42, FROM, TO));
		var b = CompletableFuture.supplyAsync(() -> scanner.scanFvgs(nq, Timeframe.M5, 42, FROM, TO));
		assertThat(a.join()).hasSameSizeAs(b.join());
	}

	@Test
	@DisplayName("keeps different timeframes and markets apart")
	void keyedByMarketAndTimeframe() {
		List<DetectedSetup> m5 = scanner.scanFvgs(nq, Timeframe.M5, 42, FROM, TO);
		int m5Rows = rows();
		scanner.scanFvgs(nq, Timeframe.M15, 42, FROM, TO);
		scanner.scanFvgs(nq, Timeframe.M5, 7, FROM, TO);

		assertThat(rows()).isGreaterThan(m5Rows);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM detected_setup WHERE timeframe = '5m' AND seed = 42", Integer.class))
			.isEqualTo(m5Rows);
		assertThat(scanner.scanFvgs(nq, Timeframe.M5, 42, FROM, TO))
			.extracting(DetectedSetup::getId)
			.containsExactlyElementsOf(m5.stream().map(DetectedSetup::getId).toList());
	}

	private int rows() {
		return jdbc.queryForObject("SELECT count(*) FROM detected_setup", Integer.class);
	}

	@Test
	@DisplayName("a simulated hit without its seed is refused by the database (DEC-012)")
	void reproducibilityIsEnforced() {
		scanner.scanFvgs(nq, Timeframe.M5, 42, FROM, TO);
		org.assertj.core.api.Assertions.assertThatThrownBy(() ->
				jdbc.update("UPDATE detected_setup SET seed = NULL"))
			.hasMessageContaining("ck_detected_setup_reproducible");
	}
}
