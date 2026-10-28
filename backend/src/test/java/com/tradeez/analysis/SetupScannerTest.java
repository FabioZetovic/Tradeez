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
	FvgDetector fvg;

	@Autowired
	SwingDetector swing;

	@Autowired
	LiquidityDetector liquidity;

	@Autowired
	MarketDataService marketData;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	com.tradeez.marketdata.MarketBarStore barStore;

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
		List<DetectedSetup> stored = scanFvgs(nq, Timeframe.M5, 42, FROM, TO);

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
		int first = scanFvgs(nq, Timeframe.M5, 42, FROM, TO).size();
		int stored = rows();
		int second = scanFvgs(nq, Timeframe.M5, 42, FROM, TO).size();

		assertThat(second).isEqualTo(first);
		assertThat(rows()).isEqualTo(stored);
	}

	@Test
	@DisplayName("a stretch whose rows were deleted is scanned again, not trusted from memory")
	void rescansWhatWasDeleted() {
		int first = scanFvgs(nq, Timeframe.M5, 42, FROM, TO).size();
		jdbc.update("DELETE FROM detected_setup");

		assertThat(scanFvgs(nq, Timeframe.M5, 42, FROM, TO)).hasSize(first);
	}

	@Test
	@DisplayName("BUG-099: a rule is told which market it reads")
	void toldTheMarket() {
		List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
		Detector recording = new Detector() {
			public String id() { return "RECORDING"; }
			public String ruleVersion() { return "recording-1.0.0"; }
			public String methodologySource() { return "a test's own"; }
			public String conceptId() { return "ICT-01"; }
			public java.util.Map<String, Object> parameters() { return java.util.Map.of(); }
			public int lookbackBars(Timeframe timeframe) { return 0; }
			public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
				return List.of();
			}
			public List<Detection> detect(List<Bar> segment, String symbol, Companion companion, Timeframe timeframe) {
				seen.add(String.valueOf(symbol));
				return List.of();
			}
		};
		scanner.ensureScanned(recording, nq, Timeframe.M5, 42, FROM, TO);
		assertThat(seen).isNotEmpty().containsOnly("DEMO-NQ");
	}

	@Test
	@DisplayName("a stretch whose market was imported since is scanned again, though its rows are all there")
	void rescansAfterAnImport() {
		java.util.concurrent.atomic.AtomicInteger scans = new java.util.concurrent.atomic.AtomicInteger();
		Detector counting = new Detector() {
			public String id() { return "COUNTING"; }
			public String ruleVersion() { return "counting-1.0.0"; }
			public String methodologySource() { return "a test's own"; }
			public String conceptId() { return "ICT-01"; }
			public java.util.Map<String, Object> parameters() { return java.util.Map.of(); }
			public int lookbackBars(Timeframe timeframe) { return 0; }
			public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
				scans.incrementAndGet();
				return List.of();
			}
		};
		Instrument es = marketData.requireInstrument("ES");
		com.tradeez.ThreeWeeksOfEs.insert(marketData, barStore);
		try {
			// Monday 12 May, on the June contract.
			Instant from = Instant.parse("2025-05-12T14:00:00Z");
			Instant to = Instant.parse("2025-05-12T18:00:00Z");
			scanner.ensureScanned(counting, es, Timeframe.M5, 0, from, to);
			scanner.ensureScanned(counting, es, Timeframe.M5, 0, from, to);
			assertThat(scans).as("nothing changed: read back, not scanned").hasValue(1);

			// A minute the import adds inside the rules' reach: the context may have changed.
			Instant added = Instant.parse("2025-05-10T12:00:00Z");
			barStore.insert(es.getId(), "1m", List.of(new com.tradeez.marketdata.MarketBarStore.StoredBar(
				new Bar(added, 20_000, 20_001, 19_999, 20_000, 1), 202506)));
			scanner.ensureScanned(counting, es, Timeframe.M5, 0, from, to);
			assertThat(scans).hasValue(2);
		} finally {
			com.tradeez.ThreeWeeksOfEs.remove(marketData, jdbc);
		}
	}

	@Test
	@DisplayName("T35: rules scanned together read their candles once, and store exactly what each stores alone")
	void togetherAsAlone() {
		List<Detector> rules = List.of(fvg, swing, liquidity);
		scanner.ensureScanned(rules, nq, Timeframe.M5, 42, FROM, TO);
		List<String> together = stored();
		jdbc.update("DELETE FROM detected_setup");

		for (Detector rule : rules) {
			scanner.ensureScanned(rule, nq, Timeframe.M5, 42, FROM, TO);
		}
		assertThat(stored()).isEqualTo(together);
		assertThat(together).anyMatch(r -> r.startsWith("FVG ")).anyMatch(r -> r.startsWith("SWING "))
			.anyMatch(r -> r.startsWith("LIQUIDITY "));
	}

	@Test
	@DisplayName("BUG-080 at most two scans run at once, server-wide; the rest wait their turn and still run")
	void scansBounded() {
		java.util.concurrent.atomic.AtomicInteger running = new java.util.concurrent.atomic.AtomicInteger();
		java.util.concurrent.atomic.AtomicInteger most = new java.util.concurrent.atomic.AtomicInteger();
		java.util.concurrent.atomic.AtomicInteger scans = new java.util.concurrent.atomic.AtomicInteger();
		Detector slow = new Detector() {
			public String id() { return "SLOW"; }
			public String ruleVersion() { return "slow-1.0.0"; }
			public String methodologySource() { return "a test's own"; }
			public String conceptId() { return "ICT-01"; }
			public java.util.Map<String, Object> parameters() { return java.util.Map.of(); }
			public int lookbackBars(Timeframe timeframe) { return 0; }
			public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
				most.accumulateAndGet(running.incrementAndGet(), Math::max);
				scans.incrementAndGet();
				try {
					Thread.sleep(300);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				running.decrementAndGet();
				return List.of();
			}
		};
		// Five different weekdays, so none is another's scan already done, and each has candles to read.
		var pool = java.util.concurrent.Executors.newFixedThreadPool(5);
		try {
			List<CompletableFuture<Void>> five = java.util.stream.IntStream.of(0, 1, 2, 3, 6)
				.mapToObj(day -> CompletableFuture.runAsync(() -> scanner.ensureScanned(slow, nq, Timeframe.M5, 42,
					FROM.plus(java.time.Duration.ofDays(day)), TO.plus(java.time.Duration.ofDays(day))), pool))
				.toList();
			five.forEach(CompletableFuture::join);
		} finally {
			pool.shutdown();
		}
		assertThat(scans).hasValue(5);
		assertThat(most).hasValue(SetupScanner.CONCURRENT_SCANS);
	}

	@Test
	@DisplayName("two scans of overlapping stretches at once do not collide")
	void concurrentScans() {
		// Two drills picking overlapping windows at the same moment. The insert ignores duplicates,
		// so neither fails on the unique constraint.
		var a = CompletableFuture.supplyAsync(() -> scanFvgs(nq, Timeframe.M5, 42, FROM, TO));
		var b = CompletableFuture.supplyAsync(() -> scanFvgs(nq, Timeframe.M5, 42, FROM, TO));
		assertThat(a.join()).hasSameSizeAs(b.join());
	}

	@Test
	@DisplayName("keeps different timeframes and markets apart")
	void keyedByMarketAndTimeframe() {
		List<DetectedSetup> m5 = scanFvgs(nq, Timeframe.M5, 42, FROM, TO);
		int m5Rows = rows();
		scanFvgs(nq, Timeframe.M15, 42, FROM, TO);
		scanFvgs(nq, Timeframe.M5, 7, FROM, TO);

		assertThat(rows()).isGreaterThan(m5Rows);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM detected_setup WHERE timeframe = '5m' AND seed = 42", Integer.class))
			.isEqualTo(m5Rows);
		assertThat(scanFvgs(nq, Timeframe.M5, 42, FROM, TO))
			.extracting(DetectedSetup::getId)
			.containsExactlyElementsOf(m5.stream().map(DetectedSetup::getId).toList());
	}

	/** Scans a stretch, then reads back every stored gap anchored in it. */
	private List<DetectedSetup> scanFvgs(Instrument instrument, Timeframe timeframe, long seed, Instant from,
			Instant to) {
		scanner.ensureScanned(fvg, instrument, timeframe, seed, from, to);
		return scanner.visible(fvg, instrument, timeframe, seed, from, to, to).stream()
			.filter(s -> !s.getAnchorTime().isBefore(from) && s.getAnchorTime().isBefore(to))
			.toList();
	}

	private List<String> stored() {
		return jdbc.queryForList("""
			SELECT format('%s %s %s %s %s %s %s %s', detector, kind, direction, start_time, end_time,
			    confirmed_at, zone_low_ticks, zone_high_ticks)
			FROM detected_setup ORDER BY 1""", String.class);
	}

	private int rows() {
		return jdbc.queryForObject("SELECT count(*) FROM detected_setup", Integer.class);
	}

	@Test
	@DisplayName("a simulated hit without its seed is refused by the database (DEC-012)")
	void reproducibilityIsEnforced() {
		scanFvgs(nq, Timeframe.M5, 42, FROM, TO);
		org.assertj.core.api.Assertions.assertThatThrownBy(() ->
				jdbc.update("UPDATE detected_setup SET seed = NULL"))
			.hasMessageContaining("ck_detected_setup_reproducible");
	}
}
