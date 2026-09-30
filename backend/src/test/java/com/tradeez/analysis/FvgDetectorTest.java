package com.tradeez.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tradeez.analysis.FvgDetector.Direction;
import com.tradeez.analysis.FvgDetector.Hit;
import com.tradeez.analysis.FvgDetector.Parameters;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.BarAggregator;
import com.tradeez.marketdata.DemoSeriesGenerator;
import com.tradeez.marketdata.Timeframe;

/**
 * The FVG detector (task 7).
 *
 * <p>The golden sequences below are HAND_AUTHORED_FIXTURE data (DATA_STRATEGY.md): built to contain
 * exactly the shapes under test, and therefore evidence that the <i>code</i> follows the rule — never
 * evidence that the rule means anything in a market.
 */
class FvgDetectorTest {

	private static final Timeframe TF = Timeframe.M5;
	private static final long T0 = 1_710_338_400L; // 2024-03-13 14:00 UTC, mid-session
	private static final Parameters P = Parameters.DEFAULT;

	/** Fourteen quiet candles, each with an 8-tick range: average 8, so the threshold is 2 ticks. */
	private static List<Bar> quietHistory() {
		List<Bar> bars = new ArrayList<>();
		for (int i = 0; i < 14; i++) {
			bars.add(new Bar(Instant.ofEpochSecond(T0 + i * 300L), 1000, 1004, 996, 1000, 10));
		}
		return bars;
	}

	private static Bar bar(int index, long open, long high, long low, long close) {
		return new Bar(Instant.ofEpochSecond(T0 + index * 300L), open, high, low, close, 10);
	}

	/** History, then three candles: indexes 14, 15, 16. */
	private static List<Bar> withPattern(Bar c1, Bar c2, Bar c3) {
		List<Bar> bars = quietHistory();
		bars.add(c1);
		bars.add(c2);
		bars.add(c3);
		return bars;
	}

	@Nested
	@DisplayName("the rule")
	class Rule {

		@Test
		@DisplayName("finds a bullish gap: candle 1's high below candle 3's low")
		void bullish() {
			List<Hit> hits = FvgDetector.detect(withPattern(
				bar(14, 1000, 1004, 996, 1003),
				bar(15, 1003, 1030, 1002, 1028),
				bar(16, 1028, 1035, 1014, 1033)), TF, P);

			assertThat(hits).hasSize(1);
			Hit hit = hits.getFirst();
			assertThat(hit.direction()).isEqualTo(Direction.BULLISH);
			assertThat(hit.zoneLowTicks()).isEqualTo(1004);
			assertThat(hit.zoneHighTicks()).isEqualTo(1014);
			assertThat(hit.gapTicks()).isEqualTo(10);
			assertThat(hit.anchorTime()).isEqualTo(Instant.ofEpochSecond(T0 + 15 * 300L));
			assertThat(hit.thresholdTicks()).isEqualTo(2);
		}

		@Test
		@DisplayName("finds a bearish gap: candle 1's low above candle 3's high")
		void bearish() {
			List<Hit> hits = FvgDetector.detect(withPattern(
				bar(14, 1000, 1004, 996, 997),
				bar(15, 997, 998, 970, 972),
				bar(16, 972, 986, 965, 970)), TF, P);

			assertThat(hits).hasSize(1);
			Hit hit = hits.getFirst();
			assertThat(hit.direction()).isEqualTo(Direction.BEARISH);
			assertThat(hit.zoneLowTicks()).isEqualTo(986);
			assertThat(hit.zoneHighTicks()).isEqualTo(996);
			assertThat(hit.gapTicks()).isEqualTo(10);
		}

		@Test
		@DisplayName("candles that touch are not a gap")
		void touching() {
			assertThat(FvgDetector.detect(withPattern(
				bar(14, 1000, 1004, 996, 1003),
				bar(15, 1003, 1030, 1002, 1028),
				bar(16, 1028, 1035, 1004, 1033)), TF, P)).isEmpty();
		}

		@Test
		@DisplayName("the size threshold is inclusive: exactly at it qualifies, one tick under does not")
		void thresholdBoundary() {
			// Threshold is 2 ticks. Gap of 2 qualifies; gap of 1 does not.
			assertThat(FvgDetector.detect(withPattern(
				bar(14, 1000, 1004, 996, 1003),
				bar(15, 1003, 1010, 1002, 1009),
				bar(16, 1009, 1012, 1006, 1010)), TF, P)).hasSize(1);
			assertThat(FvgDetector.detect(withPattern(
				bar(14, 1000, 1004, 996, 1003),
				bar(15, 1003, 1010, 1002, 1009),
				bar(16, 1009, 1012, 1005, 1010)), TF, P)).isEmpty();
		}

		@Test
		@DisplayName("a jump across a market closure is not a fair value gap")
		void notAcrossAClosure() {
			// Candle 3 opens an hour after candle 2: the daily break. That is an opening gap, a
			// different concept, however large.
			List<Bar> bars = quietHistory();
			bars.add(bar(14, 1000, 1004, 996, 1003));
			bars.add(bar(15, 1003, 1030, 1002, 1028));
			bars.add(new Bar(Instant.ofEpochSecond(T0 + 15 * 300L + 3600), 1060, 1070, 1050, 1065, 10));
			assertThat(FvgDetector.detect(bars, TF, P)).isEmpty();
		}

		@Test
		@DisplayName("is not evaluated without a full lookback before candle 1")
		void needsHistory() {
			List<Bar> bars = new ArrayList<>(quietHistory().subList(0, 13));
			bars.add(bar(13, 1000, 1004, 996, 1003));
			bars.add(bar(14, 1003, 1030, 1002, 1028));
			bars.add(bar(15, 1028, 1035, 1014, 1033));
			assertThat(FvgDetector.detect(bars, TF, P)).isEmpty();
		}

		@Test
		@DisplayName("the threshold scales with recent volatility, not a fixed size")
		void relativeThreshold() {
			// The same 10-tick gap after a violent history (80-tick candles, threshold 20) is too
			// small to count. A fixed tick size could not tell these two situations apart.
			List<Bar> bars = new ArrayList<>();
			for (int i = 0; i < 14; i++) {
				bars.add(new Bar(Instant.ofEpochSecond(T0 + i * 300L), 1000, 1040, 960, 1000, 10));
			}
			bars.add(bar(14, 1000, 1004, 996, 1003));
			bars.add(bar(15, 1003, 1030, 1002, 1028));
			bars.add(bar(16, 1028, 1035, 1014, 1033));
			assertThat(FvgDetector.detect(bars, TF, P)).isEmpty();
		}
	}

	@Nested
	@DisplayName("the arithmetic")
	class Arithmetic {

		@Test
		@DisplayName("rounds the threshold up to whole ticks, never below one")
		void threshold() {
			assertThat(FvgDetector.threshold(112, 14, 25)).isEqualTo(2);  // exactly 2.00
			assertThat(FvgDetector.threshold(100, 14, 25)).isEqualTo(2);  // 1.79 → 2
			assertThat(FvgDetector.threshold(1344, 14, 25)).isEqualTo(24); // 96-tick average
			assertThat(FvgDetector.threshold(0, 14, 25)).isEqualTo(1);    // flat market: still > 0
		}

		@Test
		@DisplayName("records the evidence an explanation is built from")
		void evidence() {
			Hit hit = FvgDetector.detect(withPattern(
				bar(14, 1000, 1004, 996, 1003),
				bar(15, 1003, 1030, 1002, 1028),
				bar(16, 1028, 1035, 1014, 1033)), TF, P).getFirst();

			assertThat(hit.evidence())
				.containsEntry("candle1HighTicks", 1004L)
				.containsEntry("candle3LowTicks", 1014L)
				.containsEntry("gapTicks", 10L)
				.containsEntry("sumRangeTicks", 112L)
				.containsEntry("lookback", 14)
				.containsEntry("thresholdTicks", 2L)
				.containsEntry("candle2Time", T0 + 15 * 300L);
		}

		@Test
		@DisplayName("stores its parameters and rule version for DEC-026")
		void parameters() {
			assertThat(Parameters.DEFAULT.asMap())
				.containsEntry("minGapPercentOfAverageRange", 25)
				.containsEntry("averageRangeLookback", 14);
			assertThat(FvgDetector.RULE_VERSION).isEqualTo("fvg-1.0.0");
		}
	}

	@Nested
	@DisplayName("against an independent check, on generated data")
	class Oracle {

		@Test
		@DisplayName("reports every qualifying gap and nothing else, on 5m and 15m")
		void soundAndComplete() {
			// Written separately and as plainly as possible: floating point is exact here because
			// every value is a small integer, and "gap >= ceil(x)" equals "gap >= x" for whole ticks.
			DemoSeriesGenerator generator = new DemoSeriesGenerator();
			List<Bar> seconds = generator.generateSeconds(42,
				Instant.parse("2024-03-11T00:00:00Z"), Instant.parse("2024-03-16T00:00:00Z"));

			for (Timeframe tf : new Timeframe[] { Timeframe.M5, Timeframe.M15 }) {
				List<Bar> bars = BarAggregator.aggregate(seconds, tf);
				Set<String> expected = new HashSet<>();
				for (int i = 15; i + 1 < bars.size(); i++) {
					Bar a = bars.get(i - 1);
					Bar b = bars.get(i);
					Bar c = bars.get(i + 1);
					long s = tf.seconds();
					if (b.openTime().getEpochSecond() - a.openTime().getEpochSecond() != s
							|| c.openTime().getEpochSecond() - b.openTime().getEpochSecond() != s) {
						continue;
					}
					double average = 0;
					for (int k = i - 15; k <= i - 2; k++) {
						average += bars.get(k).rangeTicks();
					}
					average /= 14;
					long up = c.lowTicks() - a.highTicks();
					long down = a.lowTicks() - c.highTicks();
					if (up > 0 && up >= 0.25 * average) {
						expected.add(b.openTime() + " BULLISH");
					}
					if (down > 0 && down >= 0.25 * average) {
						expected.add(b.openTime() + " BEARISH");
					}
				}

				Set<String> actual = new HashSet<>();
				for (Hit hit : FvgDetector.detect(bars, tf, Parameters.DEFAULT)) {
					actual.add(hit.anchorTime() + " " + hit.direction());
				}

				assertThat(expected).as("the check itself found something to compare").isNotEmpty();
				assertThat(actual).as(tf.code()).isEqualTo(expected);
			}
		}

		@Test
		@DisplayName("is deterministic: the same bars give the same hits")
		void deterministic() {
			DemoSeriesGenerator generator = new DemoSeriesGenerator();
			Instant from = Instant.parse("2024-03-12T13:00:00Z");
			Instant to = Instant.parse("2024-03-12T20:00:00Z");
			List<Bar> a = BarAggregator.aggregate(generator.generateSeconds(42, from, to), TF);
			List<Bar> b = BarAggregator.aggregate(generator.generateSeconds(42, from, to), TF);
			assertThat(FvgDetector.detect(a, TF, P)).isEqualTo(FvgDetector.detect(b, TF, P));
		}
	}
}
