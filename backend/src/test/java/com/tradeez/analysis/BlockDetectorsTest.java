package com.tradeez.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * T17 (DEC-061) — the block rules on candles built by hand, where every zone and state can be read off
 * the numbers. {@link DetectorContractTest} checks the same detectors keep the contract.
 */
class BlockDetectorsTest {

	private static final Timeframe TF = Timeframe.M5;
	private static final Instant T0 = Instant.parse("2025-05-06T14:00:00Z");

	private static Instant at(int index) {
		return T0.plusSeconds(300L * index);
	}

	private static List<Bar> candles(List<long[]> ohlc) {
		return candles(ohlc, Integer.MAX_VALUE);
	}

	/** Candles five minutes apart, with the market shut for an hour before candle {@code reopen}. */
	private static List<Bar> candles(List<long[]> ohlc, int reopen) {
		List<Bar> bars = new ArrayList<>();
		for (long[] c : ohlc) {
			int i = bars.size();
			bars.add(new Bar(at(i).plusSeconds(i >= reopen ? 3600 : 0), c[0], c[1], c[2], c[3], 1));
		}
		return bars;
	}

	/**
	 * Quiet candles at 110; a low at {@code earlierLow} (16) and a swing high at {@code high} (18, after two
	 * up-close candles); a drop through two down-close candles to 97 (20); then a displacement whose
	 * middle candle (22) closes at 124, leaving a gap from 104 to 112. Every other pair of wicks overlaps.
	 */
	private static List<long[]> flip(long earlierLow, long high, long... after) {
		List<long[]> rows = new ArrayList<>();
		for (int i = 0; i < 15; i++) {
			rows.add(new long[] { 110, 111, 109, 110 });
		}
		rows.addAll(List.of(
			new long[] { 110, 111, 104, 105 },           // 15
			new long[] { 105, 109, earlierLow, 103 },    // 16: L1
			new long[] { 103, 109, 102, 108 },           // 17: up
			new long[] { 108, high, 106, 110 },          // 18: up — H
			new long[] { 110, 111, 101, 102 },           // 19: down
			new long[] { 102, 106, 97, 99 },             // 20: down — L2, 97
			new long[] { 99, 104, 98, 103 },             // 21: the leg's first candle, the gap's first
			new long[] { 103, 125, 103, 124 },           // 22: displacement, the first close above H
			new long[] { 124, 130, 112, 129 }));         // 23: the gap's third: [104, 112]
		for (int i = 0; i < after.length; i += 4) {
			rows.add(Arrays.copyOfRange(after, i, i + 4));
		}
		return rows;
	}

	/** After the leg: a candle away from every zone, then back down through them, one level at a time. */
	private static final long[] RETURN = {
		129, 130, 124, 126,    // 24: above everything
		126, 127, 110, 112,    // 25: trades into all three zones
		112, 113, 103, 104,    // 26: closes below each mean threshold
		104, 105, 95, 96 };    // 27: closes below each zone

	private static List<Detection> bullish(Detector detector, List<long[]> rows) {
		return detector.detect(candles(rows), TF).stream()
			.filter(d -> d.direction() == Detection.Direction.BULLISH)
			.toList();
	}

	@Nested
	@DisplayName("ob-1.0.0")
	class OrderBlocks {

		@Test
		@DisplayName("the down-close candles before a displacement that breaks a swing high: full range, mean threshold of the bodies")
		void forms() {
			assertThat(bullish(new OrderBlockDetector(), flip(100, 115))).singleElement().satisfies(ob -> {
				assertThat(ob.kind()).isEqualTo("FORMED");
				assertThat(ob.start()).isEqualTo(at(19));      // the run: 19 and 20 both close down
				assertThat(ob.anchor()).isEqualTo(at(20));     // the last down-close candle before the leg
				assertThat(ob.lowTicks()).isEqualTo(97);
				assertThat(ob.highTicks()).isEqualTo(111);
				assertThat(ob.confirmedAt()).isEqualTo(at(24)); // when the leg is known: its gap's third candle closed
				assertThat(ob.evidence()).containsEntry("meanHalfTicks", 209L)   // bodies 99 to 110: 104.5
					.containsEntry("runCandles", 2).containsEntry("brokenPrice", 115L)
					.containsEntry("breakCloseTicks", 124L);
			});
		}

		@Test
		@DisplayName("tapped, mean threshold lost, invalidated — each at the close of the candle that did it")
		void life() {
			assertThat(bullish(new OrderBlockDetector(), flip(100, 115, RETURN)))
				.extracting(Detection::kind, Detection::confirmedAt, Detection::anchor)
				.containsExactly(
					tuple("FORMED", at(24), at(20)),
					tuple("TAPPED", at(26), at(20)),            // 25 traded down to 110, into the top at 111
					tuple("MEAN_THRESHOLD", at(27), at(20)),    // 26 closed at 104, below 104.5
					tuple("INVALIDATED", at(28), at(20)));      // 27 closed at 96, below 97
		}

		@Test
		@DisplayName("still standing 500 candles later, it has expired: the rule stops following it")
		void expires() {
			List<long[]> rows = flip(100, 115, 129, 130, 124, 126);
			for (int i = 0; i < Blocks.MAX_AGE_CANDLES; i++) {
				rows.add(new long[] { 126, 127, 125, 126 });
			}
			assertThat(bullish(new OrderBlockDetector(), rows)).extracting(Detection::kind, Detection::confirmedAt)
				.containsExactly(tuple("FORMED", at(24)), tuple("EXPIRED", at(24 + Blocks.MAX_AGE_CANDLES)));
		}

		@Test
		@DisplayName("the last down-close candle need not be right before the move: up to five back")
		void notRightBefore() {
			List<long[]> rows = flip(100, 115);
			rows.set(20, new long[] { 102, 106, 97, 103 });   // 20 now closes up: the last down-close candle is 19
			assertThat(bullish(new OrderBlockDetector(), rows)).singleElement().satisfies(ob -> {
				assertThat(ob.start()).isEqualTo(at(19));
				assertThat(ob.anchor()).isEqualTo(at(19));
				assertThat(ob.lowTicks()).isEqualTo(101);
				assertThat(ob.highTicks()).isEqualTo(111);
			});
		}

		@Test
		@DisplayName("no block across a market closure: the block and the move away are one unbroken stretch")
		void notAcrossAClosure() {
			// Shut for an hour between the block (19–20) and the move (21–23); the move and its gap stay whole.
			List<Bar> bars = candles(flip(100, 115), 21);
			assertThat(new OrderBlockDetector().detect(bars, TF)).isEmpty();
			// The breaker's own candles, 17 and 18, lie before the closure: it still forms.
			assertThat(new BreakerDetector().detect(bars, TF)).extracting(Detection::kind).containsExactly("FORMED");
		}

		@Test
		@DisplayName("a run of down-close candles stops at a market closure: the block is the candles after it")
		void runStopsAtAClosure() {
			// Shut for an hour between 19 and 20, both closing down: the block is 20 alone.
			assertThat(new OrderBlockDetector().detect(candles(flip(100, 115), 20), TF).stream()
					.filter(d -> d.direction() == Detection.Direction.BULLISH))
				.singleElement().satisfies(ob -> {
					assertThat(ob.start()).isEqualTo(ob.anchor());
					assertThat(ob.lowTicks()).isEqualTo(97);
					assertThat(ob.highTicks()).isEqualTo(106);
				});
		}

		/**
		 * A swing high at 320, then {@code fall} candles each a tick lower and closing down — no newer swing
		 * high, no close above it — and a displacement whose middle candle closes at 325, through it.
		 */
		private static List<long[]> staircase(int fall) {
			List<long[]> rows = new ArrayList<>();
			for (int i = 0; i < 15; i++) {
				rows.add(new long[] { 300, 301, 299, 300 });
			}
			rows.add(new long[] { 300, 320, 299, 301 });
			long high = 300;
			for (int k = 0; k < fall; k++, high--) {
				rows.add(new long[] { high - 1, high, high - 4, high - 3 });
			}
			rows.add(new long[] { high - 2, high - 1, high - 5, high - 4 });   // the last down-close candle
			rows.add(new long[] { high - 4, 330, high - 4, 325 });             // displacement through 320
			rows.add(new long[] { 325, 335, 300, 330 });                       // the gap's third candle
			return rows;
		}

		@Test
		@DisplayName("a swing more than 200 candles back is out of the rule's reach: no block, though the move breaks it")
		void beyondTheSpan() {
			// 150 candles of falling: the swing is within reach, and the block forms.
			assertThat(bullish(new OrderBlockDetector(), staircase(150))).extracting(Detection::kind).containsExactly("FORMED");
			// 220: everything a block reads must lie within 200 candles of its confirmation (DEC-049).
			assertThat(bullish(new OrderBlockDetector(), staircase(220))).isEmpty();
		}

		@Test
		@DisplayName("an invalidated block is done: it never expires as well")
		void invalidatedIsDone() {
			List<long[]> rows = flip(100, 115, RETURN);
			for (int i = 0; i < Blocks.MAX_AGE_CANDLES; i++) {
				rows.add(new long[] { 96, 97, 95, 96 });
			}
			assertThat(bullish(new OrderBlockDetector(), rows)).extracting(Detection::kind)
				.containsExactly("FORMED", "TAPPED", "MEAN_THRESHOLD", "INVALIDATED");
		}

		@Test
		@DisplayName("no block when the displacement does not itself break the swing — a later close does")
		void breakOutsideTheLeg() {
			// 24 closes above 135, a candle after the leg, and leaves no gap of its own: its low is not above 22's high.
			assertThat(bullish(new OrderBlockDetector(), flip(100, 135, 129, 140, 124, 138))).isEmpty();
		}
	}

	@Nested
	@DisplayName("breaker-1.0.0, mitigation-1.0.0 and unicorn-1.0.0")
	class Flips {

		@Test
		@DisplayName("breaker: the lower low took the earlier one; the up-close candles at the high are the zone")
		void breaker() {
			List<long[]> rows = flip(100, 115);
			assertThat(bullish(new BreakerDetector(), rows)).singleElement().satisfies(b -> {
				assertThat(b.kind()).isEqualTo("FORMED");
				assertThat(b.start()).isEqualTo(at(17));
				assertThat(b.anchor()).isEqualTo(at(18));      // the high that failed
				assertThat(b.lowTicks()).isEqualTo(102);
				assertThat(b.highTicks()).isEqualTo(115);
				assertThat(b.confirmedAt()).isEqualTo(at(24)); // the break at 22, the displacement known at 23's close
				assertThat(b.evidence()).containsEntry("originPrice", 100L).containsEntry("dropPrice", 97L)
					.containsEntry("levelPrice", 115L).containsEntry("meanHalfTicks", 213L);
			});
			assertThat(bullish(new MitigationBlockDetector(), rows)).isEmpty();
		}

		@Test
		@DisplayName("mitigation block: the same flip when the drop stays above the earlier low")
		void mitigation() {
			List<long[]> rows = flip(96, 115);
			assertThat(bullish(new MitigationBlockDetector(), rows)).singleElement().satisfies(m -> {
				assertThat(m.anchor()).isEqualTo(at(18));
				assertThat(m.lowTicks()).isEqualTo(102);
				assertThat(m.highTicks()).isEqualTo(115);
				assertThat(m.evidence()).containsEntry("originPrice", 96L).containsEntry("dropPrice", 97L);
			});
			assertThat(bullish(new BreakerDetector(), rows)).isEmpty();
			assertThat(bullish(new UnicornDetector(), rows)).isEmpty();
		}

		@Test
		@DisplayName("equal lows: the drop to the same price takes nothing — a mitigation block, not a breaker")
		void equalLows() {
			List<long[]> rows = flip(97, 115);
			assertThat(bullish(new MitigationBlockDetector(), rows)).singleElement()
				.satisfies(m -> assertThat(m.evidence()).containsEntry("originPrice", 97L).containsEntry("dropPrice", 97L));
			assertThat(bullish(new BreakerDetector(), rows)).isEmpty();
		}

		@Test
		@DisplayName("unicorn: the breaker and its displacement's gap overlap from 104 to 112")
		void unicorn() {
			assertThat(bullish(new UnicornDetector(), flip(100, 115))).singleElement().satisfies(u -> {
				assertThat(u.anchor()).isEqualTo(at(18));
				assertThat(u.lowTicks()).isEqualTo(104);
				assertThat(u.highTicks()).isEqualTo(112);
				assertThat(u.confirmedAt()).isEqualTo(at(24));
				assertThat(u.evidence()).containsEntry("meanHalfTicks", 216L).containsEntry("gapLowTicks", 104L)
					.containsEntry("breakerHighTicks", 115L);
			});
		}

		@Test
		@DisplayName("the breaker's and the unicorn's lives, read against their own zones")
		void lives() {
			List<long[]> rows = flip(100, 115, RETURN);
			assertThat(bullish(new BreakerDetector(), rows)).extracting(Detection::kind, Detection::confirmedAt)
				.containsExactly(tuple("FORMED", at(24)), tuple("TAPPED", at(26)), tuple("MEAN_THRESHOLD", at(27)),
					tuple("INVALIDATED", at(28)));
			// 26 closes at 104: below the unicorn's mean threshold at 108, but on its low — not through it.
			assertThat(bullish(new UnicornDetector(), rows)).extracting(Detection::kind, Detection::confirmedAt)
				.containsExactly(tuple("FORMED", at(24)), tuple("TAPPED", at(26)), tuple("MEAN_THRESHOLD", at(27)),
					tuple("INVALIDATED", at(28)));
		}
	}

	@Nested
	@DisplayName("rejection-1.0.0")
	class Rejection {

		/** Quiet candles four ticks tall, then a swing high {@code swing} between two quiet neighbours. */
		private static List<long[]> rows(long[] swing, long... after) {
			List<long[]> rows = new ArrayList<>();
			for (int i = 0; i < 15; i++) {
				rows.add(new long[] { 110, 112, 108, 110 });
			}
			rows.add(swing);                                // 15
			rows.add(new long[] { 113, 114, 108, 110 });    // 16: confirms it; its open is the highest body
			for (int i = 0; i < after.length; i += 4) {
				rows.add(Arrays.copyOfRange(after, i, i + 4));
			}
			return rows;
		}

		private static List<Detection> bearish(List<long[]> rows) {
			return new RejectionBlockDetector().detect(candles(rows), TF).stream()
				.filter(d -> d.direction() == Detection.Direction.BEARISH)
				.toList();
		}

		@Test
		@DisplayName("a long upper wick at a swing high: from the highest body nearby up to the high")
		void forms() {
			assertThat(bearish(rows(new long[] { 110, 125, 108, 111 }))).singleElement().satisfies(r -> {
				assertThat(r.kind()).isEqualTo("FORMED");
				assertThat(r.start()).isEqualTo(at(14));
				assertThat(r.anchor()).isEqualTo(at(15));
				assertThat(r.lowTicks()).isEqualTo(113);       // the highest body of the three: 16's open
				assertThat(r.highTicks()).isEqualTo(125);
				assertThat(r.confirmedAt()).isEqualTo(at(17));
				assertThat(r.evidence()).containsEntry("wickTicks", 14L).containsEntry("rangeTicks", 17L)
					.containsEntry("meanHalfTicks", 238L);
			});
		}

		@Test
		@DisplayName("not when the wick is under half the candle, nor when it is no longer than the average candle")
		void notLongEnough() {
			// A 4-tick wick on a 17-tick candle: under half.
			assertThat(bearish(rows(new long[] { 110, 126, 109, 122 }))).isEmpty();
			// 3 ticks of a 5-tick candle, but shorter than the 4-tick average candle before it.
			assertThat(bearish(rows(new long[] { 110, 114, 109, 111 }))).isEmpty();
		}

		@Test
		@DisplayName("its life: into the wick, a close above its middle, a close above the high")
		void life() {
			List<long[]> rows = rows(new long[] { 110, 125, 108, 111 },
				110, 113, 109, 112,    // 17: up to 113, into the zone's bottom
				112, 121, 111, 120,    // 18: closes at 120, above the middle at 119
				120, 127, 119, 126);   // 19: closes at 126, above the high
			assertThat(bearish(rows)).extracting(Detection::kind, Detection::confirmedAt)
				.containsExactly(tuple("FORMED", at(17)), tuple("TAPPED", at(18)), tuple("MEAN_THRESHOLD", at(19)),
					tuple("INVALIDATED", at(20)));
		}
	}
}
