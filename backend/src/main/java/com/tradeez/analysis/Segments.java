package com.tradeez.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.tradeez.marketdata.Bar;

/**
 * The rule every detector shares: a pattern never spans a session break or a contract roll
 * (ICT_CONCEPTS.md S-1, DEC-049).
 *
 * <p>Rolls are enforced by the framework, before a detector sees anything: candles are split into
 * segments at every roll, because the next NQ contract trades hundreds of points away and its prices
 * are not a continuation of the last one's. Session breaks are each detector's to respect, with
 * {@link #consecutive}: what a detector may look back on across a break is part of its rule; the
 * candles of the pattern itself must follow one another without one.
 */
public final class Segments {

	private Segments() {
	}

	/**
	 * Splits candles at every roll: each segment holds one contract's candles.
	 *
	 * @param rolls the first candle time of each new contract, in time order
	 */
	public static List<List<Bar>> atRolls(List<Bar> bars, List<Instant> rolls) {
		List<List<Bar>> segments = new ArrayList<>();
		List<Bar> current = new ArrayList<>();
		int next = 0;
		for (Bar bar : bars) {
			boolean rolled = false;
			while (next < rolls.size() && !bar.openTime().isBefore(rolls.get(next))) {
				rolled = true;
				next++;
			}
			if (rolled && !current.isEmpty()) {
				segments.add(current);
				current = new ArrayList<>();
			}
			current.add(bar);
		}
		if (!current.isEmpty()) {
			segments.add(current);
		}
		return segments;
	}

	/**
	 * Two markets' rolls in one list, in time order (T20): where a rule that reads both splits its candles, so
	 * that each segment is one contract of each.
	 */
	public static List<Instant> merged(List<Instant> rolls, List<Instant> companionRolls) {
		return Stream.concat(rolls.stream(), companionRolls.stream()).distinct().sorted().toList();
	}

	/**
	 * The companion market's candles over a segment's minutes (T20): those opening from the segment's first
	 * candle to its last.
	 *
	 * @param companion that market's candles, in time order
	 */
	public static Detector.Companion beside(List<Bar> segment, String symbol, List<Bar> companion) {
		if (segment.isEmpty()) {
			return new Detector.Companion(symbol, List.of());
		}
		return new Detector.Companion(symbol, companion.subList(firstAtOrAfter(companion, segment.getFirst().openTime()),
			firstAtOrAfter(companion, segment.getLast().openTime().plusNanos(1))));
	}

	/** The index of the first candle opening at or after {@code time}. */
	private static int firstAtOrAfter(List<Bar> bars, Instant time) {
		int lo = 0;
		int hi = bars.size();
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (bars.get(mid).openTime().isBefore(time)) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		return lo;
	}

	/** True when each candle opens exactly one step after the one before it: no break between. */
	public static boolean consecutive(List<Bar> candles, long stepSeconds) {
		for (int i = 1; i < candles.size(); i++) {
			long gap = candles.get(i).openTime().getEpochSecond()
				- candles.get(i - 1).openTime().getEpochSecond();
			if (gap != stepSeconds) {
				return false;
			}
		}
		return true;
	}
}
