package com.tradeez.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * T12 (DEC-053) — the liquidity rules on candles built by hand. {@link DetectorContractTest} checks
 * the same detectors keep the contract.
 */
class LiquidityDetectorsTest {

	private static final Timeframe TF = Timeframe.M5;
	private static final Instant T0 = Instant.parse("2025-05-06T14:00:00Z");

	private static Instant at(int index) {
		return T0.plusSeconds(300L * index);
	}

	private static List<Bar> candles(List<long[]> rows) {
		List<Bar> bars = new ArrayList<>();
		for (long[] c : rows) {
			bars.add(new Bar(at(bars.size()), c[0], c[1], c[2], c[3], 1));
		}
		return bars;
	}

	/** {@code n} identical quiet candles at 100: no swing among them. */
	private static List<long[]> quiet(int n) {
		List<long[]> rows = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			rows.add(new long[] { 100, 101, 99, 100 });
		}
		return rows;
	}

	/** Three quiet candles, then a swing high at 120 (candle 3), confirmed by candle 4. */
	private static List<long[]> swingHigh() {
		List<long[]> rows = quiet(3);
		rows.add(new long[] { 100, 120, 99, 110 });
		rows.add(new long[] { 110, 112, 105, 106 });
		return rows;
	}

	private static List<long[]> with(List<long[]> rows, long[]... more) {
		List<long[]> all = new ArrayList<>(rows);
		all.addAll(List.of(more));
		return all;
	}

	@Nested
	@DisplayName("liquidity-1.0.0")
	class Liquidity {

		@Test
		@DisplayName("a swing high rests from its confirmation; a touch leaves it, a tick beyond takes it")
		void restingThenTaken() {
			List<Bar> bars = candles(with(swingHigh(),
				new long[] { 106, 120, 104, 110 },     // 5: touches 120
				new long[] { 110, 121, 108, 115 }));   // 6: trades 121 — taken
			assertThat(new LiquidityDetector().detect(bars, TF))
				.filteredOn(d -> d.direction() == Detection.Direction.BEARISH)
				.extracting(Detection::kind, Detection::end, Detection::lowTicks)
				.containsExactly(tuple("RESTING", at(4), 120L), tuple("TAKEN", at(6), 120L));
		}

		@Test
		@DisplayName("a level still resting 500 candles after its confirmation expires")
		void expires() {
			List<long[]> rows = swingHigh();
			rows.addAll(quiet(LiquidityLevels.MAX_AGE_CANDLES));
			assertThat(new LiquidityDetector().detect(candles(rows), TF))
				.filteredOn(d -> d.direction() == Detection.Direction.BEARISH)
				.extracting(Detection::kind, Detection::end)
				.containsExactly(tuple("RESTING", at(4)), tuple("EXPIRED", at(4 + LiquidityLevels.MAX_AGE_CANDLES)));
		}
	}

	/**
	 * Fifteen quiet candles, a swing high at 120 (candle 15) and another at {@code second} (candle 18),
	 * with candle 17 reaching {@code between}.
	 */
	private static List<long[]> pair(long second, long between) {
		List<long[]> rows = quiet(15);
		rows.add(new long[] { 100, 120, 99, 110 });          // 15
		rows.add(new long[] { 110, 112, 105, 106 });         // 16
		rows.add(new long[] { 106, between, 104, 108 });     // 17
		rows.add(new long[] { 108, second, 107, 115 });      // 18
		rows.add(new long[] { 115, 116, 110, 111 });         // 19
		return rows;
	}

	@Nested
	@DisplayName("eqhl-1.0.0")
	class EqualHighs {

		@Test
		@DisplayName("two swing highs a tick apart, three candles apart, nothing above them between: equal highs")
		void equalHighs() {
			assertThat(new EqualHighsLowsDetector().detect(candles(pair(121, 110)), TF)).singleElement().satisfies(e -> {
				assertThat(e.kind()).isEqualTo("EQH");
				assertThat(e.lowTicks()).isEqualTo(120);
				assertThat(e.highTicks()).isEqualTo(121);
				assertThat(e.start()).isEqualTo(at(15));
				assertThat(e.anchor()).isEqualTo(at(18));
				assertThat(e.confirmedAt()).isEqualTo(at(20));
			});
		}

		@Test
		@DisplayName("not when they are further apart than the tolerance, nor when a candle between trades above")
		void notEqual() {
			assertThat(new EqualHighsLowsDetector().detect(candles(pair(123, 110)), TF)).isEmpty();
			assertThat(new EqualHighsLowsDetector().detect(candles(pair(121, 125)), TF)).isEmpty();
		}
	}

	@Nested
	@DisplayName("sweep-1.0.0 and turtle soup")
	class Sweeps {

		@Test
		@DisplayName("taken and closed back in the same candle: a sweep, pointing away from the level")
		void sweep() {
			List<Bar> bars = candles(with(swingHigh(), new long[] { 106, 123, 104, 118 }));
			assertThat(new SweepDetector().detect(bars, TF)).singleElement().satisfies(s -> {
				assertThat(s.kind()).isEqualTo("SWEEP");
				assertThat(s.direction()).isEqualTo(Detection.Direction.BEARISH);
				assertThat(s.anchor()).isEqualTo(at(5));
				assertThat(s.confirmedAt()).isEqualTo(at(6));
			});
		}

		@Test
		@DisplayName("closed beyond, and two more closes beyond: a run, known at the second")
		void run() {
			List<Bar> bars = candles(with(swingHigh(),
				new long[] { 106, 125, 104, 124 },
				new long[] { 124, 126, 122, 123 },
				new long[] { 123, 127, 121, 126 }));
			assertThat(new SweepDetector().detect(bars, TF)).filteredOn(d -> d.lowTicks() == 120).singleElement()
				.satisfies(r -> {
					assertThat(r.kind()).isEqualTo("RUN");
					assertThat(r.direction()).isEqualTo(Detection.Direction.BULLISH);
					assertThat(r.confirmedAt()).isEqualTo(at(8));
				});
			// Closed beyond, then back inside: undecided, not reported.
			List<Bar> undecided = candles(with(swingHigh(),
				new long[] { 106, 125, 104, 124 },
				new long[] { 124, 126, 117, 119 },
				new long[] { 119, 121, 116, 118 }));
			assertThat(new SweepDetector().detect(undecided, TF)).noneMatch(d -> d.lowTicks() == 120);
		}

		@Test
		@DisplayName("one candle through two highs is judged on the higher one")
		void mostExtreme() {
			List<Bar> bars = candles(with(swingHigh(),
				new long[] { 106, 115, 104, 110 },     // 5: a lower swing high, 115
				new long[] { 110, 111, 107, 108 },     // 6
				new long[] { 108, 125, 106, 118 }));   // 7: through both, closing at 118
			// Above 115 but back under 120: a sweep of 120 — not a run through 115.
			assertThat(new SweepDetector().detect(bars, TF)).singleElement().satisfies(s -> {
				assertThat(s.kind()).isEqualTo("SWEEP");
				assertThat(s.lowTicks()).isEqualTo(120);
			});
		}

		@Test
		@DisplayName("turtle soup: equal highs swept, closing back under the higher of them")
		void turtleSoup() {
			List<Bar> soup = candles(with(pair(121, 110), new long[] { 111, 123, 110, 119 }));
			assertThat(new TurtleSoupDetector().detect(soup, TF)).singleElement().satisfies(t -> {
				assertThat(t.lowTicks()).isEqualTo(121);
				assertThat(t.direction()).isEqualTo(Detection.Direction.BEARISH);
				assertThat(t.anchor()).isEqualTo(at(20));
				assertThat(t.start()).isEqualTo(at(15));
			});
			List<Bar> breakout = candles(with(pair(121, 110), new long[] { 111, 125, 110, 124 }));
			assertThat(new TurtleSoupDetector().detect(breakout, TF)).isEmpty();
		}
	}
}
