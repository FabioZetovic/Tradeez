package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tradeez.analysis.DisplacementDetector.Leg;
import com.tradeez.analysis.SwingPoints.Swing;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * The blocks of ICT_CONCEPTS.md Part 5 (T17, DEC-061): where each forms, and what happens to it after.
 * Shared by the order block, breaker, mitigation block, rejection block and unicorn rules.
 *
 * <p><b>A block's life</b> is read candle by candle from the close of the candle that confirmed it, as a
 * gap's is ({@link FvgLifecycle}). A bullish block — support, price above it — is <b>tapped</b> when a
 * candle trades down into it; its <b>mean threshold</b> is lost when a candle closes below it, which ICT
 * reads as weakening the block; and it is <b>invalidated</b> when a candle closes below its low. A bearish
 * block mirrors it, and one candle can do several at once. A block still standing
 * {@value #MAX_AGE_CANDLES} candles after it formed has <b>expired</b>: the rule stops following it,
 * which is what keeps its lookback finite.
 *
 * <p><b>Bounds, measured on two years of real NQ</b> (T17): a block's candle lies within
 * {@value #MAX_BACK} candles of where its rule starts looking for it in 99% of cases, and a run of
 * candles closing the same way is merged into one block up to {@value #MAX_RUN} candles (97% are no
 * longer). Everything a block's forming reads lies within {@value #MAX_SPAN} candles of its confirmation,
 * as for every rule on swings.
 */
final class Blocks {

	enum State {
		FORMED, TAPPED, MEAN_THRESHOLD, INVALIDATED, EXPIRED
	}

	/** As for gaps and liquidity: about two trading days on 5m, a week on 15m. */
	static final int MAX_AGE_CANDLES = 500;
	static final int MAX_BACK = 5;
	static final int MAX_RUN = 5;
	static final int MAX_SPAN = StructureDetector.MAX_SPAN;
	static final int N = SwingDetector.STRENGTH;
	/** The candles a gap's size, and a displacement's, is measured against (fvg-1.0.0, displacement-1.0.0). */
	static final int RANGE_LOOKBACK = 14;

	private Blocks() {
	}

	/**
	 * A block as it forms, by candle index in its segment.
	 *
	 * @param first         the first candle of the block — its run, or for a rejection block the swing's left neighbour
	 * @param anchor        the candle a learner points at
	 * @param confirm       the candle at whose close it is known
	 * @param meanHalfTicks its mean threshold in half-ticks, so the midpoint of an odd range stays exact (ICT-21)
	 * @param evidence      what the rule decided on, for its explanation
	 */
	record Block(boolean bullish, int first, int anchor, int confirm, long low, long high, long meanHalfTicks,
			Map<String, Object> evidence) {
	}

	/** Candles {@code first} to {@code last}, each closing the same way. */
	record Run(int first, int last) {

		int candles() {
			return last - first + 1;
		}
	}

	/** A swing broken by a close beyond it: candle {@code at} is the first to close beyond {@code level}. */
	record Break(Swing level, int at) {
	}

	/**
	 * Each block's forming and every later state as hits, in the order they became known — at the close of
	 * the candle that moved it there.
	 */
	static List<Detection> lifecycle(List<Bar> bars, List<Block> blocks, Timeframe timeframe) {
		List<Detection> found = new ArrayList<>();
		for (Block block : blocks) {
			found.add(detection(bars, block, State.FORMED, block.confirm(), timeframe));
			int reached = State.FORMED.ordinal();
			int last = Math.min(block.confirm() + MAX_AGE_CANDLES, bars.size() - 1);
			for (int k = block.confirm() + 1; k <= last; k++) {
				Bar b = bars.get(k);
				int now = reached;
				if (block.bullish() ? b.lowTicks() <= block.high() : b.highTicks() >= block.low()) {
					now = Math.max(now, State.TAPPED.ordinal());
				}
				if (block.bullish() ? 2 * b.closeTicks() < block.meanHalfTicks() : 2 * b.closeTicks() > block.meanHalfTicks()) {
					now = Math.max(now, State.MEAN_THRESHOLD.ordinal());
				}
				if (block.bullish() ? b.closeTicks() < block.low() : b.closeTicks() > block.high()) {
					now = State.INVALIDATED.ordinal();
				}
				for (int s = reached + 1; s <= now; s++) {
					found.add(detection(bars, block, State.values()[s], k, timeframe));
				}
				reached = now;
				if (reached == State.INVALIDATED.ordinal()) {
					break;
				}
				if (k == block.confirm() + MAX_AGE_CANDLES) {
					found.add(detection(bars, block, State.EXPIRED, k, timeframe));
				}
			}
		}
		// Stable: states one candle reached stay in their order.
		found.sort(Comparator.comparing(Detection::confirmedAt));
		return found;
	}

	/** The block as it stands at candle {@code at}: over its own candles to the one that moved it. */
	private static Detection detection(List<Bar> bars, Block block, State state, int at, Timeframe timeframe) {
		Bar candle = bars.get(at);
		Map<String, Object> evidence = new LinkedHashMap<>(block.evidence());
		evidence.put("state", state.name());
		evidence.put("meanHalfTicks", block.meanHalfTicks());
		evidence.put("candleTime", candle.openTime().getEpochSecond());
		evidence.put("candleHighTicks", candle.highTicks());
		evidence.put("candleLowTicks", candle.lowTicks());
		evidence.put("candleCloseTicks", candle.closeTicks());
		return new Detection(Detection.Shape.ZONE,
			block.bullish() ? Detection.Direction.BULLISH : Detection.Direction.BEARISH, state.name(),
			bars.get(block.first()).openTime(), bars.get(block.anchor()).openTime(), candle.openTime(),
			candle.openTime().plus(timeframe.duration()), block.low(), block.high(), evidence);
	}

	/**
	 * The run of candles closing {@code way} (1 up, −1 down) nearest at or before candle {@code from}: its
	 * last candle at most {@value #MAX_BACK} candles back, the run at most {@value #MAX_RUN} long and
	 * unbroken by a market closure. Null when there is none.
	 */
	static Run run(List<Bar> bars, int from, int way, long stepSeconds) {
		for (int k = from; k >= 0 && from - k < MAX_BACK; k--) {
			if (way(bars.get(k)) == way) {
				int first = k;
				while (first > 0 && k - first + 1 < MAX_RUN && way(bars.get(first - 1)) == way
						&& Segments.consecutive(bars.subList(first - 1, first + 1), stepSeconds)) {
					first--;
				}
				return new Run(first, k);
			}
		}
		return null;
	}

	/** Candle {@code c}'s close is the first beyond the latest swing confirmed before it, on the given side. */
	static Break breakAt(List<Bar> bars, List<Swing> side, int c) {
		Swing level = SwingPoints.latestConfirmedBefore(side, c, N);
		return level != null && BosChochDetector.firstClose(bars, level, c) ? new Break(level, c) : null;
	}

	/** A block over a run's candles: the full range, the mean threshold at the middle of their bodies. */
	static Block block(List<Bar> bars, Run run, boolean bullish, int anchor, int confirm, Map<String, Object> evidence) {
		long low = Long.MAX_VALUE;
		long high = Long.MIN_VALUE;
		long bodyLow = Long.MAX_VALUE;
		long bodyHigh = Long.MIN_VALUE;
		for (int k = run.first(); k <= run.last(); k++) {
			Bar b = bars.get(k);
			low = Math.min(low, b.lowTicks());
			high = Math.max(high, b.highTicks());
			bodyLow = Math.min(bodyLow, Math.min(b.openTicks(), b.closeTicks()));
			bodyHigh = Math.max(bodyHigh, Math.max(b.openTicks(), b.closeTicks()));
		}
		Map<String, Object> e = new LinkedHashMap<>(evidence);
		e.put("runCandles", run.candles());
		e.put("bodyLowTicks", bodyLow);
		e.put("bodyHighTicks", bodyHigh);
		return new Block(bullish, run.first(), anchor, confirm, low, high, bodyLow + bodyHigh, e);
	}

	/** The first displacement leg {@code up}'s way whose first candle lies in {@code [from, to]}, or null. */
	static Leg legStarting(List<Leg> legs, boolean up, int from, int to) {
		int lo = 0;
		int hi = legs.size();
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (legs.get(mid).first() < from) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		for (int i = lo; i < legs.size() && legs.get(i).first() <= to; i++) {
			if (legs.get(i).up() == up) {
				return legs.get(i);
			}
		}
		return null;
	}

	static int way(Bar bar) {
		return Long.signum(bar.closeTicks() - bar.openTicks());
	}

	static long time(List<Bar> bars, int index) {
		return bars.get(index).openTime().getEpochSecond();
	}
}
