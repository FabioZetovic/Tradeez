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
 * T11 (DEC-052) — the structure rules on candles built by hand, where the answer can be read off the
 * numbers. {@link DetectorContractTest} checks the same detectors keep the contract.
 */
class StructureDetectorsTest {

	private static final Timeframe TF = Timeframe.M5;
	private static final Instant T0 = Instant.parse("2025-05-06T14:00:00Z");

	private static Instant at(int index) {
		return T0.plusSeconds(300L * index);
	}

	private static List<Bar> candles(long[]... ohlc) {
		List<Bar> bars = new ArrayList<>();
		for (long[] c : ohlc) {
			bars.add(new Bar(at(bars.size()), c[0], c[1], c[2], c[3], 1));
		}
		return bars;
	}

	/**
	 * A clean zigzag through the given turning points, starting with a trough: each point is one
	 * candle, joined by one candle halfway between, so every point is a swing and nothing else is.
	 */
	private static List<Bar> zigzag(long... points) {
		List<long[]> rows = new ArrayList<>();
		for (int i = 0; i < points.length; i++) {
			long p = points[i];
			boolean trough = i % 2 == 0;
			rows.add(trough ? new long[] { p + 2, p + 4, p, p + 2 } : new long[] { p - 2, p, p - 4, p - 2 });
			if (i + 1 < points.length) {
				long mid = (p + points[i + 1]) / 2;
				rows.add(new long[] { mid, mid + 1, mid - 1, mid });
			}
		}
		return candles(rows.toArray(long[][]::new));
	}

	@Nested
	@DisplayName("swing-1.0.0")
	class Swings {

		@Test
		@DisplayName("a high above its neighbours, known when the next candle closes; of two equal highs, the first")
		void swingHigh() {
			List<Bar> bars = candles(new long[] { 10, 11, 9, 10 }, new long[] { 10, 13, 9, 12 }, new long[] { 12, 13, 10, 11 },
				new long[] { 11, 12, 10, 11 });
			assertThat(new SwingDetector().detect(bars, TF)).singleElement().satisfies(s -> {
				assertThat(s.kind()).isEqualTo("HIGH");
				assertThat(s.anchor()).isEqualTo(at(1));
				assertThat(s.lowTicks()).isEqualTo(13);
				assertThat(s.confirmedAt()).isEqualTo(at(3));
				assertThat(s.direction()).isEqualTo(Detection.Direction.BEARISH);
			});
		}
	}

	@Nested
	@DisplayName("swing-tier-1.0.0 and structure-1.0.0")
	class Tiers {

		/**
		 * Points at candles 0, 2, 4 … 20. The first and last cannot be swings — no candle on one side —
		 * so the swing highs are 120, 130, 125, 140, 128 and the swing lows 105, 110, 108, 112.
		 */
		private final List<Bar> bars = zigzag(100, 120, 105, 130, 110, 125, 108, 140, 112, 128, 115);

		@Test
		@DisplayName("an intermediate-term high stands above the highs either side, known when the next one is")
		void intermediate() {
			List<Detection> tiers = new SwingTierDetector().detect(bars, TF);
			// 130 (candle 6) is known when 125 is (candle 10, confirmed at 11's close); 140 when 128 is.
			assertThat(tiers).filteredOn(d -> d.kind().equals("ITH"))
				.extracting(Detection::lowTicks, Detection::confirmedAt)
				.containsExactly(tuple(130L, at(12)), tuple(140L, at(20)));
			// 140 has an ITH on its left only: not yet long-term.
			assertThat(tiers).noneMatch(d -> d.kind().equals("LTH"));
			// 108 is below 110 and 112.
			assertThat(tiers).filteredOn(d -> d.kind().equals("ITL"))
				.extracting(Detection::lowTicks, Detection::confirmedAt).containsExactly(tuple(108L, at(18)));
		}

		@Test
		@DisplayName("each swing labelled against the last of its kind")
		void labels() {
			List<Detection> labels = new StructureDetector().detect(bars, TF);
			assertThat(labels).filteredOn(d -> d.kind().endsWith("H"))
				.extracting(Detection::kind).containsExactly("HH", "LH", "HH", "LH");
			assertThat(labels).filteredOn(d -> d.kind().endsWith("L"))
				.extracting(Detection::kind).containsExactly("HL", "LL", "HL");
			assertThat(labels).filteredOn(d -> d.kind().equals("HH"))
				.allSatisfy(d -> assertThat(d.direction()).isEqualTo(Detection.Direction.BULLISH));
		}
	}

	@Nested
	@DisplayName("structure-1.0.0 breaks — BOS and CHoCH")
	class Breaks {

		@Test
		@DisplayName("with bullish structure, the first close above the last high is a break of structure")
		void bos() {
			List<Bar> bars = new ArrayList<>(zigzag(100, 120, 105, 130, 110));
			// After the higher low at 110: up, and a close at 135, above the higher high at 130.
			bars.add(new Bar(at(bars.size()), 120, 126, 119, 125, 1));
			bars.add(new Bar(at(bars.size()), 125, 136, 124, 135, 1));
			assertThat(new BosChochDetector().detect(bars, TF)).singleElement().satisfies(b -> {
				assertThat(b.kind()).isEqualTo("BOS");
				assertThat(b.direction()).isEqualTo(Detection.Direction.BULLISH);
				assertThat(b.lowTicks()).isEqualTo(130);
				assertThat(b.anchor()).isEqualTo(at(bars.size() - 1));
			});
		}

		@Test
		@DisplayName("with bearish structure, the first close above the last high is a change of character")
		void choch() {
			// Lows 110, 100 (LL) and highs 130, 125, 120 (LH): bearish. Then a close above 120.
			List<Bar> bars = new ArrayList<>(zigzag(115, 130, 110, 125, 100, 120));
			bars.add(new Bar(at(bars.size()), 112, 113, 111, 112, 1));
			bars.add(new Bar(at(bars.size()), 112, 123, 111, 122, 1));
			assertThat(new BosChochDetector().detect(bars, TF)).singleElement().satisfies(b -> {
				assertThat(b.kind()).isEqualTo("CHOCH");
				assertThat(b.direction()).isEqualTo(Detection.Direction.BULLISH);
				assertThat(b.lowTicks()).isEqualTo(120);
			});
		}
	}

	/**
	 * Quiet candles at 110, then a sweep of a resting low at 100 and a displacement through 115 — and
	 * no other gap anywhere: every neighbouring wick overlaps.
	 */
	private static long[][] shift(long sweptLow) {
		List<long[]> rows = new ArrayList<>();
		for (int i = 0; i < 15; i++) {
			rows.add(new long[] { 110, 111, 109, 110 });
		}
		rows.addAll(List.of(
			new long[] { 110, 111, 104, 105 },           // 15
			new long[] { 105, 109, sweptLow, 103 },      // 16: the low that rests
			new long[] { 103, 109, 102, 108 },           // 17
			new long[] { 108, 115, 106, 110 },           // 18: the high that will break
			new long[] { 110, 111, 101, 102 },           // 19: does not touch the resting low
			new long[] { 102, 106, 97, 99 },             // 20: the pivot, 97
			new long[] { 99, 104, 98, 103 },             // 21: candle 1 of the gap
			new long[] { 103, 125, 103, 124 },           // 22: displacement — closes above 115
			new long[] { 124, 130, 112, 129 }));         // 23: candle 3: gap [104, 112]
		return rows.toArray(long[][]::new);
	}

	@Nested
	@DisplayName("displacement-1.0.0 and mss-1.0.0")
	class Shift {

		@Test
		@DisplayName("displacement: the candles that left a gap, when their bodies are twice the recent range")
		void displacement() {
			List<Detection> legs = new DisplacementDetector().detect(candles(shift(100)), TF);
			assertThat(legs).singleElement().satisfies(d -> {
				assertThat(d.direction()).isEqualTo(Detection.Direction.BULLISH);
				assertThat(d.start()).isEqualTo(at(21));
				assertThat(d.anchor()).isEqualTo(at(22));
				assertThat(d.lowTicks()).isEqualTo(99);
				assertThat(d.highTicks()).isEqualTo(129);
				assertThat(d.confirmedAt()).isEqualTo(at(24));
			});
		}

		@Test
		@DisplayName("a gap left by small candles is not displacement")
		void smallBodies() {
			long[][] rows = shift(100);
			rows[21] = new long[] { 103, 104, 98, 103 };
			rows[22] = new long[] { 103, 125, 103, 104 };
			rows[23] = new long[] { 113, 130, 112, 114 };
			assertThat(new DisplacementDetector().detect(candles(rows), TF)).isEmpty();
		}

		@Test
		@DisplayName("MSS: sweep, displacement, break — the break is the displacement candle, known when its gap is")
		void shiftConfirmedWithItsGap() {
			assertThat(new MssDetector().detect(candles(shift(100)), TF)).singleElement().satisfies(m -> {
				assertThat(m.direction()).isEqualTo(Detection.Direction.BULLISH);
				assertThat(m.lowTicks()).isEqualTo(115);
				assertThat(m.anchor()).isEqualTo(at(22));      // the close through 115
				assertThat(m.end()).isEqualTo(at(23));         // the gap's third candle
				assertThat(m.confirmedAt()).isEqualTo(at(24));
				assertThat(m.evidence()).containsEntry("sweptPrice", 100L).containsEntry("pivotPrice", 97L);
			});
		}

		@Test
		@DisplayName("no MSS without the sweep: the pivot stays above the earlier low")
		void noSweep() {
			assertThat(new MssDetector().detect(candles(shift(96)), TF)).isEmpty();
		}
	}
}
