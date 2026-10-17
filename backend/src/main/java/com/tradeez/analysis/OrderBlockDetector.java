package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.Blocks.Block;
import com.tradeez.analysis.Blocks.Break;
import com.tradeez.analysis.Blocks.Run;
import com.tradeez.analysis.DisplacementDetector.Leg;
import com.tradeez.analysis.SwingPoints.Swing;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code ob-1.0.0} (ICT-30): an order block. Bullish: the last down-close candle before a
 * {@code displacement-1.0.0} leg up that breaks structure — a candle of the leg is the first to close
 * above the latest confirmed swing high. Several down-close candles in a row are one block (mergeRun), up
 * to {@value Blocks#MAX_RUN}; the block's last candle lies at most {@value Blocks#MAX_BACK} candles before
 * the leg, with no market closure in between. The zone is the candles' full range, high to low; the mean
 * threshold is the middle of their bodies. Known when the leg is, at its gap's third candle's close.
 * Bearish mirrors it. Then followed as every block is ({@link Blocks}): tapped, mean threshold, invalidated
 * by a close beyond its far edge, or expired.
 *
 * <p>Measured on two years of real NQ (T17): about one block per 30 candles on every timeframe from 1m
 * to 1h; in three of four displacement legs a candle of the leg itself breaks a swing, and waiting three
 * candles more adds only one in twenty. The block's candle is right before the leg in 70–78% of cases.
 * When two legs claim the same candle, the block is the earlier one's.
 */
@Component
public final class OrderBlockDetector extends RuleDetector {

	public OrderBlockDetector() {
		super("OB", "ob-1.0.0", "ICT-30", "ICT order block", rule());
	}

	static Map<String, Object> rule() {
		Map<String, Object> p = new LinkedHashMap<>();
		p.put("strength", Blocks.N);
		p.put("displacementRule", "displacement-1.0.0");
		p.put("breakBy", "close");
		p.put("breakWithin", "leg");
		p.put("mergeRun", true);
		p.put("maxRunCandles", Blocks.MAX_RUN);
		p.put("maxCandlesBeforeLeg", Blocks.MAX_BACK);
		p.put("zone", "full");
		p.put("maxSpanCandles", Blocks.MAX_SPAN);
		p.put("maxAgeCandles", Blocks.MAX_AGE_CANDLES);
		return p;
	}

	/**
	 * The forming span and the block's life; and since two legs may claim one block, how much later than
	 * the first a second can be known.
	 */
	@Override
	public int lookbackBars(Timeframe timeframe) {
		return Blocks.MAX_SPAN + Blocks.MAX_AGE_CANDLES + Blocks.MAX_BACK + Blocks.MAX_RUN;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		return Blocks.lifecycle(segment, blocks(segment, timeframe), timeframe);
	}

	/** Every order block in {@code segment} as it forms, earliest known first. */
	static List<Block> blocks(List<Bar> bars, Timeframe timeframe) {
		List<Swing> swings = SwingPoints.find(bars, Blocks.N);
		List<Swing> highs = SwingPoints.side(swings, true);
		List<Swing> lows = SwingPoints.side(swings, false);
		List<Block> found = new ArrayList<>();
		for (Leg leg : DisplacementDetector.legs(bars, timeframe)) {
			boolean up = leg.up();
			Break broke = null;
			for (int c = leg.first(); c <= leg.last() && broke == null; c++) {
				broke = Blocks.breakAt(bars, up ? highs : lows, c);
			}
			if (broke == null) {
				continue;
			}
			Run run = Blocks.run(bars, leg.first() - 1, up ? -1 : 1, timeframe.seconds());
			if (run == null || !Segments.consecutive(bars.subList(run.last(), leg.first() + 1), timeframe.seconds())) {
				continue;
			}
			int confirm = leg.third();
			// The broken swing reads its neighbours; the leg's gap, the 14 candles before its first candle.
			int earliest = Math.min(Math.min(broke.level().index() - Blocks.N, leg.middle() - 1 - Blocks.RANGE_LOOKBACK),
				run.first());
			if (earliest < confirm - Blocks.MAX_SPAN + 1) {
				continue;
			}
			Map<String, Object> evidence = new LinkedHashMap<>();
			evidence.put("brokenTime", Blocks.time(bars, broke.level().index()));
			evidence.put("brokenPrice", broke.level().price());
			evidence.put("breakTime", Blocks.time(bars, broke.at()));
			evidence.put("breakCloseTicks", bars.get(broke.at()).closeTicks());
			evidence.put("displacementTime", Blocks.time(bars, leg.middle()));
			evidence.put("legCandles", leg.last() - leg.first() + 1);
			found.add(Blocks.block(bars, run, up, run.last(), confirm, evidence));
		}
		// One block per candle: when two legs claim it, the one known first.
		found.sort(Comparator.comparingInt(Block::confirm));
		Set<String> claimed = new HashSet<>();
		return found.stream().filter(b -> claimed.add(b.anchor() + "|" + b.bullish())).toList();
	}
}
