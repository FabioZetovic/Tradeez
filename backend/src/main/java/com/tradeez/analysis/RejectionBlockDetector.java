package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.Blocks.Block;
import com.tradeez.analysis.SwingPoints.Swing;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code rejection-1.0.0} (ICT-33): a swing that left a long wick. Bearish: a swing high (strength 1) whose
 * upper wick is at least {@value #WICK_PERCENT}% of its candle's range, and at least
 * {@value #WICK_PERCENT_OF_AVERAGE}% of the average range of the {@value Blocks#RANGE_LOOKBACK} candles
 * before it. The zone runs from the highest body — open or close — among the swing candle and its
 * neighbours up to the swing high; its mean threshold is the middle of the wick (the wick's CE). Known when
 * the swing is. Bullish mirrors it at a swing low. Followed as every block is ({@link Blocks}): invalidated
 * by a close beyond the swing.
 *
 * <p>Measured on two years of real NQ (T17): the draft's wick share alone qualifies a third of all swings,
 * 4.5 per 30 candles, traded back into on the next candle more often than not — every wicky swing, not a
 * long wick. Measured against the average candle too, it is about 0.5 per 30 on every timeframe.
 */
@Component
public final class RejectionBlockDetector extends RuleDetector {

	static final int WICK_PERCENT = 50;
	static final int WICK_PERCENT_OF_AVERAGE = 100;

	public RejectionBlockDetector() {
		super("REJECTION", "rejection-1.0.0", "ICT-33", "ICT rejection block", Map.of("strength", Blocks.N,
			"wickPercentOfRange", WICK_PERCENT, "wickPercentOfAverageRange", WICK_PERCENT_OF_AVERAGE,
			"rangeLookback", Blocks.RANGE_LOOKBACK, "maxAgeCandles", Blocks.MAX_AGE_CANDLES));
	}

	/** The average before the swing, the swing and its neighbours, then the block's life. */
	@Override
	public int lookbackBars(Timeframe timeframe) {
		return Blocks.RANGE_LOOKBACK + 2 + Blocks.MAX_AGE_CANDLES;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Block> blocks = new ArrayList<>();
		for (Swing swing : SwingPoints.find(segment, Blocks.N)) {
			int i = swing.index();
			if (i < Blocks.RANGE_LOOKBACK || !Segments.consecutive(segment.subList(i - 1, i + 2), timeframe.seconds())) {
				continue;
			}
			Bar candle = segment.get(i);
			long range = candle.rangeTicks();
			long wick = swing.high() ? candle.highTicks() - Math.max(candle.openTicks(), candle.closeTicks())
				: Math.min(candle.openTicks(), candle.closeTicks()) - candle.lowTicks();
			long sum = 0;
			for (int k = i - Blocks.RANGE_LOOKBACK; k < i; k++) {
				sum += segment.get(k).rangeTicks();
			}
			// In whole ticks: a share of the range, and of the average range, without floating point.
			if (range == 0 || wick * 100 < (long) WICK_PERCENT * range
					|| wick * 100 * Blocks.RANGE_LOOKBACK < (long) WICK_PERCENT_OF_AVERAGE * sum) {
				continue;
			}
			long body = swing.high() ? Long.MIN_VALUE : Long.MAX_VALUE;
			for (int k = i - 1; k <= i + 1; k++) {
				Bar b = segment.get(k);
				body = swing.high() ? Math.max(body, Math.max(b.openTicks(), b.closeTicks()))
					: Math.min(body, Math.min(b.openTicks(), b.closeTicks()));
			}
			long low = swing.high() ? body : candle.lowTicks();
			long high = swing.high() ? candle.highTicks() : body;
			if (low >= high) {
				continue;
			}
			Map<String, Object> evidence = new LinkedHashMap<>();
			evidence.put("swingPrice", swing.price());
			evidence.put("bodyTicks", body);
			evidence.put("wickTicks", wick);
			evidence.put("rangeTicks", range);
			evidence.put("rangeSumTicks", sum);
			blocks.add(new Block(!swing.high(), i - 1, i, swing.confirmIndex(Blocks.N), low, high, low + high, evidence));
		}
		return Blocks.lifecycle(segment, blocks, timeframe);
	}
}
