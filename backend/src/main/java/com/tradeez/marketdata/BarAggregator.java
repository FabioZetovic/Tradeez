package com.tradeez.marketdata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Rolls finer bars up into coarser ones.
 *
 * <p>Aggregating rather than generating each timeframe independently is what guarantees a 5-minute
 * candle and the 1-second bars inside it tell the same story. If they could disagree, the fill
 * engine (which walks the fine bars) would resolve trades that contradict what the user watched on
 * the chart — and the user would be right to distrust every result after that.
 */
public final class BarAggregator {

	private BarAggregator() {
	}

	/**
	 * Groups {@code source} bars into {@code target} buckets.
	 *
	 * <p>Buckets are fixed by {@link Timeframe#bucketStart}, not by the first bar, so the same wall-clock
	 * minute always produces the same candle regardless of where the requested range happens to start.
	 * Aligning to the first bar would make a 09:31 request and a 09:30 request disagree about where
	 * every subsequent candle begins. Up to an hour that is the epoch; 4-hour and daily candles count
	 * from the trading day's 18:00 New York open (T35).
	 *
	 * <p>Empty buckets produce no bar. A gap in the output is real information — the market was
	 * closed — and inventing a flat candle to fill it would be inventing price action.
	 */
	public static List<Bar> aggregate(List<Bar> source, Timeframe target) {
		if (source.isEmpty()) {
			return List.of();
		}
		List<Bar> out = new ArrayList<>();

		long currentBucket = Long.MIN_VALUE;
		long open = 0;
		long high = Long.MIN_VALUE;
		long low = Long.MAX_VALUE;
		long close = 0;
		long volume = 0;

		for (Bar bar : source) {
			long bucket = target.bucketStart(bar.openTime()).getEpochSecond();

			if (bucket != currentBucket) {
				if (currentBucket != Long.MIN_VALUE) {
					out.add(new Bar(Instant.ofEpochSecond(currentBucket),
						open, high, low, close, volume));
				}
				currentBucket = bucket;
				open = bar.openTicks();
				high = Long.MIN_VALUE;
				low = Long.MAX_VALUE;
				volume = 0;
			}

			high = Math.max(high, bar.highTicks());
			low = Math.min(low, bar.lowTicks());
			close = bar.closeTicks();
			volume += bar.volume();
		}

		out.add(new Bar(Instant.ofEpochSecond(currentBucket),
			open, high, low, close, volume));
		return out;
	}
}
