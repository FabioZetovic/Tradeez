package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.SwingPoints.Swing;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code eqhl-1.0.0} (ICT-12): relative equal highs ({@code EQH}) and lows ({@code EQL}) — "engineered
 * liquidity". A swing high pairs with the latest earlier swing high at least {@value #MIN_APART}
 * candles before it whose price is within the tolerance — the larger of one tick and
 * {@value #TOLERANCE_PERCENT}% of the average range of the 14 candles before the second swing — with no
 * candle between them trading above the higher of the two. Lows mirror. Known when the second swing
 * is. The zone spans the two prices (a level when they are equal), from the first swing on.
 *
 * <p>The draft's numbers, kept after measuring them on two years of real NQ (T12): 1.3 to 2.2 pairs
 * per 30 candles across timeframes. A pair reads at most {@value #MAX_SPAN} candles.
 */
@Component
public final class EqualHighsLowsDetector extends RuleDetector {

	static final int MIN_APART = 3;
	static final int TOLERANCE_PERCENT = 10;
	static final int MAX_SPAN = StructureDetector.MAX_SPAN;
	private static final int N = SwingDetector.STRENGTH;
	private static final int RANGE_LOOKBACK = 14;

	public EqualHighsLowsDetector() {
		super("EQHL", "eqhl-1.0.0", "ICT-12", "ICT relative equal highs and lows", Map.of("strength", N,
			"minApartCandles", MIN_APART, "tolerancePercentOfAverageRange", TOLERANCE_PERCENT,
			"rangeLookback", RANGE_LOOKBACK, "maxSpanCandles", MAX_SPAN));
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return MAX_SPAN;
	}

	/** A pair of equal swings: {@code first} the earlier, {@code second} the one that completes it. */
	record Pool(Swing first, Swing second, int confirmIndex) {

		boolean high() {
			return second.high();
		}

		/** The price a candle must trade beyond to take the pool: the more extreme of the two. */
		long extreme() {
			return high() ? Math.max(first.price(), second.price()) : Math.min(first.price(), second.price());
		}
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Detection> found = new ArrayList<>();
		for (Pool pool : pools(segment, SwingPoints.find(segment, N))) {
			found.add(detection(segment, pool, timeframe));
		}
		return found;
	}

	/** Every pool in {@code bars}, in the order they are confirmed. */
	static List<Pool> pools(List<Bar> bars, List<Swing> swings) {
		List<Pool> pools = new ArrayList<>();
		for (int j = 0; j < swings.size(); j++) {
			Swing second = swings.get(j);
			int confirm = second.confirmIndex(N);
			if (second.index() < RANGE_LOOKBACK) {
				continue;
			}
			long range = 0;
			for (int k = second.index() - RANGE_LOOKBACK; k < second.index(); k++) {
				range += bars.get(k).rangeTicks();
			}
			// The larger of one tick and 10% of the average range, rounded up: integers throughout.
			long tolerance = Math.max(1, Math.floorDiv(range * TOLERANCE_PERCENT + 100L * RANGE_LOOKBACK - 1,
				100L * RANGE_LOOKBACK));
			for (int q = j - 1; q >= 0; q--) {
				Swing first = swings.get(q);
				if (first.index() - N < confirm - MAX_SPAN + 1) {
					break;
				}
				if (first.high() != second.high() || second.index() - first.index() < MIN_APART
						|| Math.abs(first.price() - second.price()) > tolerance) {
					continue;
				}
				Pool pool = new Pool(first, second, confirm);
				if (clean(bars, pool)) {
					pools.add(pool);
					break;
				}
			}
		}
		return pools;
	}

	/** No candle between the two traded beyond the more extreme of them. */
	private static boolean clean(List<Bar> bars, Pool pool) {
		for (int k = pool.first().index() + 1; k < pool.second().index(); k++) {
			if (LiquidityLevels.beyond(bars.get(k), pool.high(), pool.extreme())) {
				return false;
			}
		}
		return true;
	}

	private static Detection detection(List<Bar> segment, Pool pool, Timeframe timeframe) {
		long a = pool.first().price();
		long b = pool.second().price();
		Bar confirming = segment.get(pool.confirmIndex());
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("firstTime", segment.get(pool.first().index()).openTime().getEpochSecond());
		evidence.put("firstPrice", a);
		evidence.put("secondTime", segment.get(pool.second().index()).openTime().getEpochSecond());
		evidence.put("secondPrice", b);
		return new Detection(Detection.bandOrLevel(a, b),
			pool.high() ? Detection.Direction.BEARISH : Detection.Direction.BULLISH, pool.high() ? "EQH" : "EQL",
			segment.get(pool.first().index()).openTime(), segment.get(pool.second().index()).openTime(),
			confirming.openTime(), confirming.openTime().plus(timeframe.duration()), Math.min(a, b), Math.max(a, b),
			evidence);
	}
}
