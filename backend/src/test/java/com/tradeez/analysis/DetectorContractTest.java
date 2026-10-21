package com.tradeez.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * The contract in {@link Detector}, checked for every detector the application registers (DEC-049).
 *
 * <p>A replay is served from one scan of its whole window, filtered by the cursor. That is only the
 * truth — exactly what the learner could have known — if each detector is causal, and the scanner's
 * results only stop depending on where a scan began if {@link Detector#lookbackBars} is honest.
 * A new detector that breaks either fails here before it reaches a learner.
 *
 * <p>Run on {@link PatternMarket}, dense in every pattern, across weekends and daily halts: long
 * enough that every detector's lookback is cut into many times. Checks are spread over evenly spaced
 * points rather than every candle, to keep the suite quick.
 */
class DetectorContractTest extends AbstractIntegrationTest {

	/**
	 * Long enough for the longest lookback on each timeframe several times over: on one-minute candles, a
	 * whole week (weekly-profile-1.0.0, T19) twice, with weeks complete inside it.
	 */
	private static final Map<Timeframe, Instant[]> SERIES = Map.of(
		Timeframe.M1, new Instant[] { Instant.parse("2025-04-25T12:00:00Z"), Instant.parse("2025-05-13T00:00:00Z") },
		Timeframe.M5, new Instant[] { Instant.parse("2025-04-24T12:00:00Z"), Instant.parse("2025-05-16T00:00:00Z") },
		Timeframe.M15, new Instant[] { Instant.parse("2025-03-03T12:00:00Z"), Instant.parse("2025-04-04T00:00:00Z") });

	private static final int POINTS = 120;

	@Autowired
	DetectorRegistry detectors;

	private static List<Bar> bars(Timeframe timeframe) {
		Instant[] span = SERIES.get(timeframe);
		return PatternMarket.bars(timeframe.ordinal(), timeframe, span[0], span[1]);
	}

	private static final Map<Timeframe, List<Bar>> COMPANIONS = new ConcurrentHashMap<>();

	/**
	 * A detector run the way the scanner runs it (T20): a rule that reads a second market gets that market's
	 * candles over the same minutes — here another seed's walk, which disagrees with this one at many swings.
	 * Cut this market's candles, and the other's are cut with them.
	 */
	private static List<Detection> find(Detector detector, List<Bar> bars, Timeframe timeframe) {
		String other = detector.companionOf("NQ");
		if (other == null) {
			return detector.detect(bars, Detector.Companion.NONE, timeframe);
		}
		List<Bar> theirs = COMPANIONS.computeIfAbsent(timeframe, t -> {
			Instant[] span = SERIES.get(t);
			return PatternMarket.bars(100 + t.ordinal(), t, span[0], span[1]);
		});
		return detector.detect(bars, Segments.beside(bars, other, theirs), timeframe);
	}

	/** About {@value #POINTS} indexes spread evenly over {@code size}. */
	private static List<Integer> points(int size) {
		int step = Math.max(1, size / POINTS);
		return IntStream.iterate(1, i -> i < size, i -> i + step).boxed().toList();
	}

	@Test
	@DisplayName("every detector is registered once, names a lookback, and finds something on every timeframe here")
	void registered() {
		assertThat(detectors.all()).extracting(Detector::id)
			.containsExactlyInAnyOrder("BPR", "FVG", "FVG_STATE", "IFVG", "OPENING_GAP", "ORG", "VI", "VOID",
				"SWING", "SWING_TIER", "STRUCTURE", "BOS_CHOCH", "DISPLACEMENT", "MSS",
				"LIQUIDITY", "EQHL", "SWEEP", "TURTLE_SOUP", "OB", "BREAKER", "MITIGATION", "REJECTION", "UNICORN",
				"DEALING_RANGE", "PD", "OTE", "STDEV", "PO3", "DAILY_PROFILE", "WEEKLY_PROFILE", "JUDAS", "SMT");
		// Every switch on at once must still be one request the server accepts (found in T12: 18 > 16).
		assertThat(SetupService.MAX_DETECTORS).isGreaterThanOrEqualTo(detectors.all().size());
		for (Timeframe timeframe : SERIES.keySet()) {
			List<Bar> bars = bars(timeframe);
			for (Detector detector : detectors.all()) {
				assertThat(detector.runsOn(timeframe)).as("%s runs on %s", detector.id(), timeframe.code()).isTrue();
				assertThat(detector.lookbackBars(timeframe)).isPositive();
				assertThat(find(detector, bars, timeframe)).as("%s on %s", detector.id(), timeframe.code())
					.isNotEmpty();
			}
		}
	}

	@Test
	@DisplayName("every rule reads in words with its own numbers, and every hit explains itself (DEC-055)")
	void words() {
		for (Detector detector : detectors.all()) {
			String definition = RuleText.definition(detector);
			detector.parameters().forEach((name, value) -> {
				if (value instanceof Number) {
					assertThat(definition).as("%s states its %s", detector.id(), name).contains(value.toString());
				}
			});
		}
		// Every hit on every timeframe: an evidence key the words expect but the rule never wrote fails here.
		BigDecimal tick = new BigDecimal("0.25");
		for (Timeframe timeframe : SERIES.keySet()) {
			List<Bar> bars = bars(timeframe);
			for (Detector detector : detectors.all()) {
				for (Detection detection : find(detector, bars, timeframe)) {
					DetectedSetup hit = stored(detector, detection);
					assertThat(RuleText.label(hit)).as("%s %s", detector.id(), detection.kind()).isNotBlank()
						.doesNotContain("null");
					assertThat(RuleText.explain(hit, tick)).as("%s %s", detector.id(), detection.kind()).isNotEmpty()
						.allSatisfy(line -> assertThat(line).doesNotContain("null").doesNotContain(".."));
				}
			}
		}
	}

	/** A detection as the scanner stores it: only the fields the words read. */
	private static DetectedSetup stored(Detector detector, Detection detection) {
		DetectedSetup hit = BeanUtils.instantiateClass(DetectedSetup.class);
		ReflectionTestUtils.setField(hit, "detector", detector.id());
		ReflectionTestUtils.setField(hit, "params", detector.parameters());
		ReflectionTestUtils.setField(hit, "kind", detection.kind());
		ReflectionTestUtils.setField(hit, "direction", detection.direction().name());
		ReflectionTestUtils.setField(hit, "zoneLowTicks", detection.lowTicks());
		ReflectionTestUtils.setField(hit, "zoneHighTicks", detection.highTicks());
		ReflectionTestUtils.setField(hit, "evidence", detection.evidence());
		return hit;
	}

	@Test
	@DisplayName("deterministic, and at most one detection per anchor, direction and kind")
	void deterministic() {
		for (Timeframe timeframe : SERIES.keySet()) {
			List<Bar> bars = bars(timeframe);
			for (Detector detector : detectors.all()) {
				List<Detection> found = find(detector, bars, timeframe);
				assertThat(find(detector, List.copyOf(bars), timeframe)).isEqualTo(found);
				assertThat(found).extracting(d -> d.anchor() + " " + d.direction() + " " + d.kind())
					.as("%s on %s", detector.id(), timeframe.code())
					.doesNotHaveDuplicates();
			}
		}
	}

	@Test
	@DisplayName("causal: each detection is found from the candles closed by its confirmation, and no sooner")
	void causal() {
		for (Timeframe timeframe : SERIES.keySet()) {
			List<Bar> bars = bars(timeframe);
			for (Detector detector : detectors.all()) {
				List<Detection> all = find(detector, bars, timeframe);
				// Knowable at its confirmation: the candles closed by then are enough to find it.
				int stride = Math.max(1, all.size() / POINTS);
				for (int i = 0; i < all.size(); i += stride) {
					Detection d = all.get(i);
					List<Bar> closed = bars.stream()
						.filter(b -> !b.openTime().plus(timeframe.duration()).isAfter(d.confirmedAt()))
						.toList();
					assertThat(find(detector, closed, timeframe)).as("%s at %s", detector.id(), d.confirmedAt())
						.contains(d);
				}
				// And not before: cut anywhere, and only what was confirmed by the cut is found.
				for (int k : points(bars.size())) {
					Instant cut = bars.get(k).openTime();
					assertThat(find(detector, bars.subList(0, k), timeframe))
						.as("%s on %s cut at %s", detector.id(), timeframe.code(), cut)
						.isEqualTo(all.stream().filter(d -> !d.confirmedAt().isAfter(cut)).toList());
				}
			}
		}
	}

	@Test
	@DisplayName("lookback: given only lookbackBars candles before a point, it finds the same from there on")
	void lookbackIsEnough() {
		for (Timeframe timeframe : SERIES.keySet()) {
			List<Bar> bars = bars(timeframe);
			for (Detector detector : detectors.all()) {
				int lookback = detector.lookbackBars(timeframe);
				assertThat(bars.size()).as("series long enough to test %s", detector.id()).isGreaterThan(2 * lookback);
				List<Detection> all = find(detector, bars, timeframe);
				for (int k : points(bars.size())) {
					Instant from = bars.get(k).openTime();
					List<Bar> cut = bars.subList(Math.max(0, k - lookback), bars.size());
					assertThat(find(detector, cut, timeframe).stream()
							.filter(d -> !d.confirmedAt().isBefore(from)).toList())
						.as("%s on %s from %s", detector.id(), timeframe.code(), from)
						.isEqualTo(all.stream().filter(d -> !d.confirmedAt().isBefore(from)).toList());
				}
			}
		}
	}
}
