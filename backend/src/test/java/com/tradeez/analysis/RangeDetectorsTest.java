package com.tradeez.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * T18 (DEC-062) — the range rules on candles built by hand: one leg, its range, halves, OTE and
 * projections, each number read off the candles. {@link DetectorContractTest} checks the contract.
 */
class RangeDetectorsTest {

	private static final Timeframe TF = Timeframe.M5;
	private static final Instant T0 = Instant.parse("2025-05-06T14:00:00Z");

	private static Instant at(int index) {
		return T0.plusSeconds(300L * index);
	}

	/**
	 * Quiet candles at 110; a swing high at 115 (18); a drop to 97 (20); a displacement through 115 (22,
	 * its gap's third candle 23); a high at 136 (25); a dip and a lower swing high at 131 (28), which ranks
	 * 136 as an intermediate-term high once 29 closes. Then {@code after}, four numbers a candle.
	 */
	private static List<Bar> leg(long close29, long... after) {
		List<long[]> rows = new ArrayList<>();
		for (int i = 0; i < 15; i++) {
			rows.add(new long[] { 110, 111, 109, 110 });
		}
		rows.addAll(List.of(
			new long[] { 110, 111, 104, 105 },    // 15
			new long[] { 105, 109, 100, 103 },    // 16
			new long[] { 103, 109, 102, 108 },    // 17
			new long[] { 108, 115, 106, 110 },    // 18: the swing high that breaks
			new long[] { 110, 111, 101, 102 },    // 19
			new long[] { 102, 106, 97, 99 },      // 20: the leg's start, 97
			new long[] { 99, 104, 98, 103 },      // 21
			new long[] { 103, 125, 103, 124 },    // 22: closes above 115
			new long[] { 124, 130, 112, 129 },    // 23: the gap's third candle
			new long[] { 129, 134, 126, 133 },    // 24
			new long[] { 133, 136, 130, 131 },    // 25: the turn, 136
			new long[] { 131, 132, 124, 125 },    // 26
			new long[] { 125, 128, 122, 127 },    // 27
			new long[] { 127, 131, 126, 130 },    // 28: a lower swing high
			new long[] { 130, 130, Math.min(125, close29), close29 })); // 29: confirms 28, ranking 25
		for (int i = 0; i < after.length; i += 4) {
			rows.add(Arrays.copyOfRange(after, i, i + 4));
		}
		List<Bar> bars = new ArrayList<>();
		for (long[] c : rows) {
			bars.add(new Bar(at(bars.size()), c[0], c[1], c[2], c[3], 1));
		}
		return bars;
	}

	/** Back to the OTE's edge, a close on the range's low, then one below it. */
	private static final long[] BREAK_LOW = {
		126, 127, 112, 113,    // 30: down to 112, the OTE's top: tapped
		113, 114, 96, 97,      // 31: closes at 97, on the range's low — not beyond it
		97, 98, 95, 96 };      // 32: closes at 96, below it

	private static List<Detection> fromTheTurn(Detector detector, List<Bar> bars) {
		return detector.detect(bars, TF).stream().filter(d -> d.anchor().equals(at(25))).toList();
	}

	@Test
	@DisplayName("dealing range: from where the displacement began to the intermediate-term turn, known when the turn is ranked")
	void dealingRange() {
		assertThat(fromTheTurn(new DealingRangeDetector(), leg(126))).singleElement().satisfies(r -> {
			assertThat(r.kind()).isEqualTo("FORMED");
			assertThat(r.direction()).isEqualTo(Detection.Direction.BULLISH);
			assertThat(r.start()).isEqualTo(at(20));
			assertThat(r.lowTicks()).isEqualTo(97);
			assertThat(r.highTicks()).isEqualTo(136);
			assertThat(r.confirmedAt()).isEqualTo(at(30));
			assertThat(r.evidence()).containsEntry("midHalfTicks", 233L).containsEntry("brokenPrice", 115L);
		});
	}

	@Test
	@DisplayName("premium above equilibrium, discount below it — on an odd range the middle belongs to neither")
	void premiumAndDiscount() {
		assertThat(fromTheTurn(new PremiumDiscountDetector(), leg(126)))
			.extracting(Detection::direction, Detection::lowTicks, Detection::highTicks)
			.containsExactly(tuple(Detection.Direction.BEARISH, 117L, 136L), tuple(Detection.Direction.BULLISH, 97L, 116L));
	}

	@Test
	@DisplayName("OTE: 62% to 79% back from the turn, rounded outward, its line at 70.5% — then tapped, then over with the range")
	void ote() {
		List<Detection> ote = fromTheTurn(new OteDetector(), leg(126, BREAK_LOW));
		assertThat(ote.getFirst()).satisfies(o -> {
			// 39 ticks: 62% is 24.18 and 79% is 30.81 back, rounded outward to 24 and 31.
			assertThat(o.lowTicks()).isEqualTo(105);
			assertThat(o.highTicks()).isEqualTo(112);
			assertThat(o.evidence()).containsEntry("midHalfTicks", 217L);    // 136 − 27.5 = 108.5
		});
		assertThat(ote).extracting(Detection::kind, Detection::confirmedAt)
			.containsExactly(tuple("FORMED", at(30)), tuple("TAPPED", at(31)), tuple("BROKEN_LOW", at(33)));
	}

	@Test
	@DisplayName("standard deviations: −1, −2, −2.5 and −4 leg-lengths above the turn, a half tick rounded away")
	void deviations() {
		assertThat(fromTheTurn(new StdevDetector(), leg(126)))
			.extracting(Detection::kind, Detection::lowTicks, Detection::confirmedAt)
			.containsExactly(tuple("ONE", 175L, at(30)), tuple("TWO", 214L, at(30)), tuple("TWO_AND_A_HALF", 234L, at(30)),
				tuple("FOUR", 292L, at(30)));
	}

	@Test
	@DisplayName("a range is followed until price closes beyond it, either way, or for 500 candles")
	void life() {
		assertThat(fromTheTurn(new DealingRangeDetector(), leg(126, BREAK_LOW))).extracting(Detection::kind, Detection::confirmedAt)
			.containsExactly(tuple("FORMED", at(30)), tuple("BROKEN_LOW", at(33)));
		assertThat(fromTheTurn(new DealingRangeDetector(), leg(126, 126, 138, 125, 137))).extracting(Detection::kind)
			.containsExactly("FORMED", "BROKEN_HIGH");
		long[] inside = new long[4 * Ranges.MAX_AGE_CANDLES];
		for (int i = 0; i < inside.length; i += 4) {
			System.arraycopy(new long[] { 120, 121, 119, 120 }, 0, inside, i, 4);
		}
		assertThat(fromTheTurn(new DealingRangeDetector(), leg(126, inside))).extracting(Detection::kind, Detection::confirmedAt)
			.containsExactly(tuple("FORMED", at(30)), tuple("EXPIRED", at(30 + Ranges.MAX_AGE_CANDLES)));
	}

	/**
	 * A swing high at 400, then {@code fall} candles each a tick lower — no swing high among them — a
	 * displacement through 400, a small swing high, the turn at 430 and a lower swing high that ranks it.
	 */
	private static List<Bar> farBreak(int fall) {
		List<long[]> rows = new ArrayList<>();
		for (int i = 0; i < 15; i++) {
			rows.add(new long[] { 380, 381, 379, 380 });
		}
		rows.add(new long[] { 380, 400, 379, 381 });                        // 15: the swing high that breaks
		long high = 380;
		for (int k = 0; k < fall; k++, high--) {
			rows.add(new long[] { high - 1, high, high - 4, high - 3 });
		}
		rows.add(new long[] { high - 2, high - 1, high - 5, high - 4 });
		rows.add(new long[] { high - 4, 410, high - 4, 405 });                // displacement through 400
		rows.addAll(List.of(
			new long[] { 405, 415, 380, 412 },     // the gap's third candle
			new long[] { 412, 418, 408, 410 },     // a small swing high, 418
			new long[] { 410, 412, 404, 411 },
			new long[] { 411, 430, 410, 428 },     // the turn, 430
			new long[] { 428, 428, 415, 416 },
			new long[] { 416, 420, 412, 418 },
			new long[] { 418, 425, 416, 424 },     // a lower swing high, 425
			new long[] { 424, 424, 418, 420 }));   // confirms it, ranking 430
		List<Bar> bars = new ArrayList<>();
		for (long[] c : rows) {
			bars.add(new Bar(at(bars.size()), c[0], c[1], c[2], c[3], 1));
		}
		return bars;
	}

	@Test
	@DisplayName("a broken swing more than 300 candles back is out of reach: no range, though the move breaks it")
	void beyondTheSpan() {
		// 200 candles of falling: within reach, a range from the bottom of the fall to 430.
		assertThat(new DealingRangeDetector().detect(farBreak(200), TF)).extracting(Detection::kind, Detection::highTicks)
			.containsExactly(tuple("FORMED", 430L));
		// 320: everything a range reads must lie within 300 candles of when it is known (DEC-049).
		assertThat(new DealingRangeDetector().detect(farBreak(320), TF)).isEmpty();
	}

	@Test
	@DisplayName("not reported when price has already left the range by the time it is known")
	void alreadyLeft() {
		// 29 confirms the turn but closes at 92, below 97.
		assertThat(fromTheTurn(new DealingRangeDetector(), leg(92))).isEmpty();
		assertThat(fromTheTurn(new StdevDetector(), leg(92))).isEmpty();
	}
}
