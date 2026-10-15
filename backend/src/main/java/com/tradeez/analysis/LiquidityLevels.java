package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.List;

import com.tradeez.analysis.SwingPoints.Swing;
import com.tradeez.marketdata.Bar;

/**
 * Resting liquidity at swing levels (ICT-10, T12), shared by the liquidity, sweep and turtle soup
 * rules. A swing high is buy-side liquidity, a swing low sell-side, from the swing's confirmation
 * until a candle trades a tick beyond it — then it is taken. A level still resting
 * {@value #MAX_AGE_CANDLES} candles after its confirmation is no longer followed: measured on two
 * years of real NQ, 95% of swing levels are taken within that (half within five candles).
 */
final class LiquidityLevels {

	static final int MAX_AGE_CANDLES = 500;

	private LiquidityLevels() {
	}

	/**
	 * A level and what became of it, by candle index in its segment.
	 *
	 * @param from    the candle at whose close it starts to rest
	 * @param taken   the candle that traded beyond it, or -1 while it rests (or after it expired)
	 * @param expired the candle at which it stopped being followed still resting, or -1
	 */
	record Level(Swing swing, int from, int taken, int expired) {

		boolean high() {
			return swing.high();
		}

		long price() {
			return swing.price();
		}
	}

	/** Every swing level in {@code bars}, followed for at most {@value #MAX_AGE_CANDLES} candles. */
	static List<Level> levels(List<Bar> bars, List<Swing> swings, int n) {
		List<Level> levels = new ArrayList<>();
		for (Swing swing : swings) {
			int from = swing.confirmIndex(n);
			levels.add(follow(bars, swing, from));
		}
		return levels;
	}

	/** The level at {@code swing}'s price, resting from the close of candle {@code from}. */
	static Level follow(List<Bar> bars, Swing swing, int from) {
		int last = Math.min(from + MAX_AGE_CANDLES, bars.size() - 1);
		for (int k = from + 1; k <= last; k++) {
			if (beyond(bars.get(k), swing.high(), swing.price())) {
				return new Level(swing, from, k, -1);
			}
		}
		return new Level(swing, from, -1, last == from + MAX_AGE_CANDLES ? last : -1);
	}

	/** The bar traded at least a tick beyond the price, on the level's side. */
	static boolean beyond(Bar bar, boolean high, long price) {
		return high ? bar.highTicks() > price : bar.lowTicks() < price;
	}

	/** The bar closed back on the level's original side: at or below a high, at or above a low. */
	static boolean closedBack(Bar bar, boolean high, long price) {
		return high ? bar.closeTicks() <= price : bar.closeTicks() >= price;
	}
}
