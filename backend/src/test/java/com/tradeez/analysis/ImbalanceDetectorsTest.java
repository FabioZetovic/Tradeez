package com.tradeez.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * T10 (DEC-050) — each imbalance and gap rule on candles built by hand, where the right answer can
 * be read off the numbers. {@link DetectorContractTest} checks the same detectors keep the contract.
 */
class ImbalanceDetectorsTest {

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
	private static final Timeframe TF = Timeframe.M5;
	private static final Instant T0 = ny("2025-05-06T10:00");

	private static Instant ny(String local) {
		return LocalDateTime.parse(local).atZone(NEW_YORK).toInstant();
	}

	private static Bar bar(Instant time, long open, long high, long low, long close) {
		return new Bar(time, open, high, low, close, 1);
	}

	/** Consecutive five-minute candles from {@link #T0}: each row is open, high, low, close. */
	private static List<Bar> candles(long[]... ohlc) {
		List<Bar> bars = new ArrayList<>();
		for (long[] c : ohlc) {
			bars.add(bar(T0.plusSeconds(300L * bars.size()), c[0], c[1], c[2], c[3]));
		}
		return bars;
	}

	/** Fifteen quiet candles, so a gap after them has its full average-range lookback. */
	private static long[][] quiet(int n, long price) {
		long[][] rows = new long[n][];
		for (int i = 0; i < n; i++) {
			rows[i] = new long[] { price, price + 1, price - 1, price };
		}
		return rows;
	}

	private static long[][] concat(long[][]... parts) {
		List<long[]> all = new ArrayList<>();
		for (long[][] part : parts) {
			all.addAll(List.of(part));
		}
		return all.toArray(long[][]::new);
	}

	/** A bullish fvg-1.0.0 gap [101, 110] after a quiet stretch: candles 15 to 17, the middle one at 16. */
	private static final long[][] BULL_GAP = concat(quiet(15, 100), new long[][] {
		{ 100, 101, 99, 100 }, { 100, 120, 100, 120 }, { 120, 125, 110, 124 } });

	/**
	 * A candle after the gap that dips below 120: anything wholly above the gap's middle candle would
	 * leave a second gap with it.
	 */
	private static final long[] BRIDGE = { 124, 125, 115, 124 };

	private static Instant at(int index) {
		return T0.plusSeconds(300L * index);
	}

	@Nested
	@DisplayName("fvg-state-1.0.0 and ifvg-1.0.0 — a gap's lifecycle")
	class Lifecycle {

		@Test
		@DisplayName("open, then tapped, CE, filled and inverted, each at the close of the candle that did it")
		void stepByStep() {
			List<Bar> bars = candles(concat(BULL_GAP, new long[][] {
				{ 124, 126, 108, 125 },   // 18: low 108 ≤ 110 — tapped
				{ 125, 125, 105, 120 },   // 19: 2 × 105 ≤ 101 + 110 — the CE, in half-ticks
				{ 120, 121, 101, 104 },   // 20: low 101 — filled
				{ 104, 104, 95, 98 } }));  // 21: closed 98, below 101 — inverted
			List<Detection> states = new FvgStateDetector().detect(bars, TF);

			assertThat(states).extracting(Detection::kind).containsExactly("OPEN", "TAPPED", "CE", "FILLED", "INVERTED");
			assertThat(states).extracting(Detection::end).containsExactly(at(17), at(18), at(19), at(20), at(21));
			assertThat(states).allSatisfy(s -> {
				assertThat(s.direction()).isEqualTo(Detection.Direction.BULLISH);
				assertThat(s.lowTicks()).isEqualTo(101);
				assertThat(s.highTicks()).isEqualTo(110);
				assertThat(s.start()).isEqualTo(at(15));
				assertThat(s.anchor()).isEqualTo(at(16));
				assertThat(s.confirmedAt()).isEqualTo(s.end().plusSeconds(300));
			});

			List<Detection> inversions = new IfvgDetector().detect(bars, TF);
			assertThat(inversions).singleElement().satisfies(i -> {
				assertThat(i.direction()).isEqualTo(Detection.Direction.BEARISH); // the failed bullish gap flips
				assertThat(i.end()).isEqualTo(at(21));
				assertThat(i.kind()).isNull();
			});
		}

		@Test
		@DisplayName("a bearish gap's CE at 194.5 is reached at 195, not at 194: half-ticks, never rounded")
		void bearishHalfTick() {
			List<Bar> bars = candles(concat(quiet(15, 200), new long[][] {
				{ 200, 201, 199, 200 }, { 200, 200, 180, 180 }, { 180, 190, 178, 179 },  // gap [190, 199]
				{ 179, 185, 175, 179 },
				{ 179, 194, 178, 180 },   // into the gap, a tick short of its middle
				{ 180, 195, 179, 181 } }));
			List<Detection> states = new FvgStateDetector().detect(bars, TF);
			assertThat(states).extracting(Detection::kind).containsExactly("OPEN", "TAPPED", "CE");
			assertThat(states.get(1).end()).isEqualTo(at(19));
			assertThat(states.get(2).end()).isEqualTo(at(20));
			assertThat(states.get(0).direction()).isEqualTo(Detection.Direction.BEARISH);
		}

		@Test
		@DisplayName("one candle can take a gap through every state at once")
		void allAtOnce() {
			List<Bar> bars = candles(concat(BULL_GAP, new long[][] { { 124, 124, 95, 96 } }));
			assertThat(new FvgStateDetector().detect(bars, TF))
				.extracting(Detection::kind, Detection::end)
				.containsExactly(
					org.assertj.core.groups.Tuple.tuple("OPEN", at(17)),
					org.assertj.core.groups.Tuple.tuple("TAPPED", at(18)),
					org.assertj.core.groups.Tuple.tuple("CE", at(18)),
					org.assertj.core.groups.Tuple.tuple("FILLED", at(18)),
					org.assertj.core.groups.Tuple.tuple("INVERTED", at(18)));
		}

		@Test
		@DisplayName("a gap left alone is followed for 500 candles, then expires; a filled one is not inverted by a wick")
		void expiresAndWicks() {
			List<Bar> untouched = candles(concat(BULL_GAP, new long[][] { BRIDGE },
				quiet(FvgStateDetector.MAX_AGE_CANDLES - 1, 124)));
			assertThat(new FvgStateDetector().detect(untouched, TF))
				.extracting(Detection::kind, Detection::end)
				.containsExactly(
					org.assertj.core.groups.Tuple.tuple("OPEN", at(17)),
					org.assertj.core.groups.Tuple.tuple("EXPIRED", at(17 + FvgStateDetector.MAX_AGE_CANDLES)));
			assertThat(new FvgStateDetector().detect(untouched.subList(0, untouched.size() - 1), TF))
				.extracting(Detection::kind).containsExactly("OPEN");

			// Traded through to 95, but closed back at 105: filled, not inverted.
			List<Bar> wick = candles(concat(BULL_GAP, new long[][] { { 124, 124, 95, 105 } }));
			assertThat(new FvgStateDetector().detect(wick, TF)).extracting(Detection::kind)
				.containsExactly("OPEN", "TAPPED", "CE", "FILLED");
			assertThat(new IfvgDetector().detect(wick, TF)).isEmpty();
		}
	}

	@Nested
	@DisplayName("bpr-1.0.0 — balanced price range")
	class BalancedPriceRange {

		/** The bullish gap [101, 110], {@code between} candles, then down through it leaving a bearish gap [104, 112]. */
		private static long[][] upThenDown(int between) {
			return concat(BULL_GAP, new long[][] { BRIDGE }, quiet(between - 1, 124), new long[][] {
				{ 118, 119, 112, 113 }, { 113, 113, 95, 95 }, { 95, 104, 90, 92 } });
		}

		@Test
		@DisplayName("the overlap of the two gaps, pointing the way of the second, confirmed with it")
		void overlap() {
			List<Bar> bars = candles(upThenDown(2));
			assertThat(new BprDetector().detect(bars, TF)).singleElement().satisfies(b -> {
				assertThat(b.lowTicks()).isEqualTo(104);
				assertThat(b.highTicks()).isEqualTo(110);
				assertThat(b.direction()).isEqualTo(Detection.Direction.BEARISH);
				assertThat(b.start()).isEqualTo(at(15));
				assertThat(b.anchor()).isEqualTo(at(21));
				assertThat(b.confirmedAt()).isEqualTo(at(23));
			});
		}

		@Test
		@DisplayName("not when the second gap forms more than 20 candles after the first")
		void tooLate() {
			assertThat(new BprDetector().detect(candles(upThenDown(19)), TF)).isEmpty();
		}
	}

	@Nested
	@DisplayName("vi-1.0.0 — volume imbalance")
	class VolumeImbalance {

		@Test
		@DisplayName("bodies at least a point apart, wicks overlapping: the zone between the bodies")
		void bodiesApart() {
			List<Bar> bars = candles(new long[] { 100, 103, 99, 102 }, new long[] { 106, 108, 101, 107 });
			assertThat(new VolumeImbalanceDetector().detect(bars, TF)).singleElement().satisfies(v -> {
				assertThat(v.lowTicks()).isEqualTo(102);
				assertThat(v.highTicks()).isEqualTo(106);
				assertThat(v.direction()).isEqualTo(Detection.Direction.BULLISH);
				assertThat(v.confirmedAt()).isEqualTo(at(2));
			});
			// Bearish mirrors it.
			List<Bar> down = candles(new long[] { 107, 108, 105, 106 }, new long[] { 102, 106, 100, 101 });
			assertThat(new VolumeImbalanceDetector().detect(down, TF)).singleElement()
				.satisfies(v -> assertThat(v.direction()).isEqualTo(Detection.Direction.BEARISH));
		}

		@Test
		@DisplayName("not three ticks apart, and not when the wicks do not meet — that is a gap")
		void notAnImbalance() {
			assertThat(new VolumeImbalanceDetector().detect(
				candles(new long[] { 100, 103, 99, 102 }, new long[] { 105, 108, 101, 107 }), TF)).isEmpty();
			assertThat(new VolumeImbalanceDetector().detect(
				candles(new long[] { 100, 103, 99, 102 }, new long[] { 106, 108, 104, 107 }), TF)).isEmpty();
		}
	}

	@Nested
	@DisplayName("void-1.0.0 — gaps and liquidity voids")
	class Voids {

		@Test
		@DisplayName("a gap: two candles whose ranges do not overlap")
		void gap() {
			List<Bar> bars = candles(new long[] { 100, 103, 99, 102 }, new long[] { 106, 108, 104, 107 });
			assertThat(new VoidDetector().detect(bars, TF)).singleElement().satisfies(g -> {
				assertThat(g.kind()).isEqualTo("GAP");
				assertThat(g.lowTicks()).isEqualTo(103);
				assertThat(g.highTicks()).isEqualTo(104);
			});
		}

		private static final long[][] RUN = {
			{ 100, 101, 99, 100 },
			{ 100, 110, 100, 110 },
			{ 110, 120, 109, 120 },   // overlaps the last by 1 tick of its 11
			{ 120, 130, 119, 130 } };

		@Test
		@DisplayName("a void: the whole one-sided run, open to close, reported when a candle ends it")
		void wholeRun() {
			List<Bar> bars = candles(concat(RUN, new long[][] { { 130, 131, 125, 126 } }));
			assertThat(new VoidDetector().detect(bars, TF)).singleElement().satisfies(v -> {
				assertThat(v.kind()).isEqualTo("VOID");
				assertThat(v.direction()).isEqualTo(Detection.Direction.BULLISH);
				assertThat(v.lowTicks()).isEqualTo(100);
				assertThat(v.highTicks()).isEqualTo(130);
				assertThat(v.anchor()).isEqualTo(at(1));
				assertThat(v.end()).isEqualTo(at(3));
				assertThat(v.confirmedAt()).isEqualTo(at(5)); // the close of the candle that ended it
			});
			// While the run may still be going, nothing is claimed.
			assertThat(new VoidDetector().detect(candles(RUN), TF)).isEmpty();
		}

		@Test
		@DisplayName("not when a candle overlaps the last by more than 20% of its range, or steps back")
		void notOneSided() {
			long[][] overlapping = { RUN[0], RUN[1], { 110, 120, 104, 120 }, RUN[3], { 130, 131, 125, 126 } };
			assertThat(new VoidDetector().detect(candles(overlapping), TF)).isEmpty();
			long[][] steppingBack = { RUN[0], RUN[1], { 90, 99, 90, 98 }, { 70, 80, 70, 79 }, { 79, 80, 60, 61 } };
			assertThat(new VoidDetector().detect(candles(steppingBack), TF))
				.noneMatch(d -> "VOID".equals(d.kind()));
		}

		@Test
		@DisplayName("a run is measured over at most its last ten candles")
		void longRun() {
			List<long[]> rows = new ArrayList<>(List.of(RUN[0]));
			for (int i = 0; i < 12; i++) {
				rows.add(new long[] { 100 + 10L * i, 110 + 10L * i, 100 + 10L * i - (i == 0 ? 0 : 1), 110 + 10L * i });
			}
			rows.add(new long[] { 220, 221, 215, 216 });
			assertThat(new VoidDetector().detect(candles(rows.toArray(long[][]::new)), TF))
				.filteredOn(d -> "VOID".equals(d.kind()))
				.singleElement()
				.satisfies(v -> assertThat(v.anchor()).isEqualTo(at(3)));
		}
	}

	@Nested
	@DisplayName("opening-gap-1.0.0 — NDOG and NWOG")
	class OpeningGaps {

		@Test
		@DisplayName("the daily halt: from the 16:59 close to the 18:00 open, a new day opening gap")
		void ndog() {
			List<Bar> bars = List.of(
				bar(ny("2025-05-06T16:50"), 118, 119, 117, 118),
				bar(ny("2025-05-06T16:55"), 118, 121, 118, 120),
				bar(ny("2025-05-06T18:00"), 130, 131, 129, 130));
			assertThat(new OpeningGapDetector().detect(bars, TF)).singleElement().satisfies(g -> {
				assertThat(g.kind()).isEqualTo("NDOG");
				assertThat(g.shape()).isEqualTo(Detection.Shape.ZONE);
				assertThat(g.direction()).isEqualTo(Detection.Direction.BULLISH);
				assertThat(g.lowTicks()).isEqualTo(120);
				assertThat(g.highTicks()).isEqualTo(130);
				assertThat(g.anchor()).isEqualTo(ny("2025-05-06T18:00"));
				assertThat(g.confirmedAt()).isEqualTo(ny("2025-05-06T18:05"));
			});
		}

		@Test
		@DisplayName("over the weekend a new week opening gap; a gap of zero is kept, as a level")
		void nwogAndZero() {
			List<Bar> weekend = List.of(
				bar(ny("2025-05-09T16:55"), 118, 121, 118, 120),   // Friday
				bar(ny("2025-05-11T18:00"), 110, 112, 109, 111));  // Sunday
			assertThat(new OpeningGapDetector().detect(weekend, TF)).singleElement().satisfies(g -> {
				assertThat(g.kind()).isEqualTo("NWOG");
				assertThat(g.direction()).isEqualTo(Detection.Direction.BEARISH);
			});
			List<Bar> flat = List.of(
				bar(ny("2025-05-12T16:55"), 111, 112, 110, 111),
				bar(ny("2025-05-12T18:00"), 111, 113, 110, 112));
			assertThat(new OpeningGapDetector().detect(flat, TF)).singleElement().satisfies(g -> {
				assertThat(g.kind()).isEqualTo("NDOG");
				assertThat(g.shape()).isEqualTo(Detection.Shape.LEVEL);
				assertThat(g.direction()).isEqualTo(Detection.Direction.NEUTRAL);
				assertThat(g.lowTicks()).isEqualTo(111);
			});
		}

		@Test
		@DisplayName("not a quiet half hour inside a session, and not on a four-hour chart")
		void notAClosure() {
			List<Bar> quietHalfHour = List.of(bar(T0, 100, 101, 99, 100), bar(T0.plusSeconds(1800), 104, 105, 103, 104));
			assertThat(new OpeningGapDetector().detect(quietHalfHour, TF)).isEmpty();
			List<Bar> fourHour = List.of(
				bar(ny("2025-05-06T12:00"), 118, 121, 118, 120),
				bar(ny("2025-05-06T20:00"), 130, 131, 129, 130));
			assertThat(new OpeningGapDetector().detect(fourHour, Timeframe.H4)).isEmpty();
		}
	}

	@Nested
	@DisplayName("org-1.0.0 — opening range gap")
	class OpeningRangeGap {

		@Test
		@DisplayName("from Friday's 16:14 close to Monday's 09:30 open")
		void overTheWeekend() {
			List<Bar> bars = List.of(
				bar(ny("2025-05-09T16:10"), 199, 201, 198, 200),
				bar(ny("2025-05-11T18:00"), 195, 196, 194, 195),
				bar(ny("2025-05-12T09:25"), 192, 193, 191, 192),
				bar(ny("2025-05-12T09:30"), 190, 194, 189, 193));
			assertThat(new OrgDetector().detect(bars, TF)).singleElement().satisfies(o -> {
				assertThat(o.lowTicks()).isEqualTo(190);
				assertThat(o.highTicks()).isEqualTo(200);
				assertThat(o.direction()).isEqualTo(Detection.Direction.BEARISH);
				assertThat(o.start()).isEqualTo(ny("2025-05-09T16:10"));
				assertThat(o.anchor()).isEqualTo(ny("2025-05-12T09:30"));
				assertThat(o.confirmedAt()).isEqualTo(ny("2025-05-12T09:35"));
			});
		}

		@Test
		@DisplayName("a morning after a day with no 16:14 close has none, and a 30-minute chart has none")
		void noPreviousClose() {
			List<Bar> bars = List.of(
				bar(ny("2025-05-08T16:10"), 199, 201, 198, 200),
				bar(ny("2025-05-09T09:30"), 205, 206, 204, 205),   // a half day: no 16:10 candle after it
				bar(ny("2025-05-12T09:30"), 190, 194, 189, 193));
			assertThat(new OrgDetector().detect(bars, TF)).singleElement()
				.satisfies(o -> assertThat(o.anchor()).isEqualTo(ny("2025-05-09T09:30")));
			assertThat(new OrgDetector().detect(bars, Timeframe.M30)).isEmpty();
		}
	}
}
