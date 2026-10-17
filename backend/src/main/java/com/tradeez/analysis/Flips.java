package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tradeez.analysis.Blocks.Block;
import com.tradeez.analysis.Blocks.Break;
import com.tradeez.analysis.Blocks.Run;
import com.tradeez.analysis.DisplacementDetector.Leg;
import com.tradeez.analysis.SwingPoints.Swing;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * A block that failed and flipped (ICT-31, ICT-32): shared by {@code breaker-1.0.0},
 * {@code mitigation-1.0.0} and {@code unicorn-1.0.0}. Bullish:
 * <ol>
 * <li><i>H</i> is the latest swing high confirmed before candle <i>b</i>, and <i>b</i> is the first candle
 * to close above it;</li>
 * <li><i>L2</i>, the drop, is the lowest low between <i>H</i> and <i>b</i>; <i>L1</i> is the latest swing low
 * before <i>H</i>;</li>
 * <li>a {@code displacement-1.0.0} leg up starts between <i>L2</i> and <i>b</i>;</li>
 * <li>the zone is the last up-close candle at or before <i>H</i> — the bearish block that failed — with the
 * up-close candles before it, up to {@value Blocks#MAX_RUN}, at most {@value Blocks#MAX_BACK} candles back.</li>
 * </ol>
 * A <b>breaker</b> when <i>L2</i> traded below <i>L1</i>, taking the sell-side liquidity there; a <b>mitigation
 * block</b> when it did not. Known once both the break and the displacement are: at the later of <i>b</i>'s
 * close and the leg's. Anchored on <i>H</i> — one first close beyond a swing, so one flip per swing.
 * Everything read lies within {@value Blocks#MAX_SPAN} candles. Bearish mirrors it.
 */
final class Flips {

	/**
	 * @param swept the drop took the liquidity beyond {@code L1}: a breaker rather than a mitigation block
	 * @param leg   the displacement that broke {@code H}, for the unicorn's gap
	 */
	record Flip(Block block, boolean swept, Leg leg) {
	}

	private Flips() {
	}

	/** Every flip in {@code segment}, in the order of the candle that broke its swing. */
	static List<Flip> find(List<Bar> bars, Timeframe timeframe) {
		List<Swing> swings = SwingPoints.find(bars, Blocks.N);
		List<Swing> highs = SwingPoints.side(swings, true);
		List<Swing> lows = SwingPoints.side(swings, false);
		List<Leg> legs = DisplacementDetector.legs(bars, timeframe);
		List<Flip> found = new ArrayList<>();
		for (int b = 2; b < bars.size(); b++) {
			for (boolean up : new boolean[] { true, false }) {
				Break broke = Blocks.breakAt(bars, up ? highs : lows, b);
				if (broke == null || b - broke.level().index() < 2) {
					continue;
				}
				Flip flip = flip(bars, timeframe, broke, up, up ? lows : highs, legs);
				if (flip != null) {
					found.add(flip);
				}
			}
		}
		return found;
	}

	private static Flip flip(List<Bar> bars, Timeframe timeframe, Break broke, boolean up, List<Swing> otherSide,
			List<Leg> legs) {
		Swing level = broke.level();
		int b = broke.at();
		int drop = level.index() + 1;
		for (int k = drop + 1; k < b; k++) {
			if (up ? bars.get(k).lowTicks() < bars.get(drop).lowTicks() : bars.get(k).highTicks() > bars.get(drop).highTicks()) {
				drop = k;
			}
		}
		Swing origin = latestBefore(otherSide, level.index());
		Leg leg = Blocks.legStarting(legs, up, drop, b);
		// The bearish block that failed: up-close candles at the high, for a bullish flip.
		Run run = Blocks.run(bars, level.index(), up ? 1 : -1, timeframe.seconds());
		if (origin == null || leg == null || run == null) {
			return null;
		}
		int confirm = Math.max(b, leg.third());
		int earliest = Math.min(Math.min(origin.index() - Blocks.N, run.first()), leg.middle() - 1 - Blocks.RANGE_LOOKBACK);
		if (earliest < confirm - Blocks.MAX_SPAN + 1) {
			return null;
		}
		long dropPrice = up ? bars.get(drop).lowTicks() : bars.get(drop).highTicks();
		boolean swept = up ? dropPrice < origin.price() : dropPrice > origin.price();
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("originTime", Blocks.time(bars, origin.index()));
		evidence.put("originPrice", origin.price());
		evidence.put("levelTime", Blocks.time(bars, level.index()));
		evidence.put("levelPrice", level.price());
		evidence.put("dropTime", Blocks.time(bars, drop));
		evidence.put("dropPrice", dropPrice);
		evidence.put("breakTime", Blocks.time(bars, b));
		evidence.put("breakCloseTicks", bars.get(b).closeTicks());
		evidence.put("displacementTime", Blocks.time(bars, leg.middle()));
		return new Flip(Blocks.block(bars, run, up, level.index(), confirm, evidence), swept, leg);
	}

	/** The latest swing of {@code side}, in candle order, on a candle before {@code index}. */
	private static Swing latestBefore(List<Swing> side, int index) {
		int lo = 0;
		int hi = side.size() - 1;
		Swing latest = null;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (side.get(mid).index() < index) {
				latest = side.get(mid);
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return latest;
	}
}
