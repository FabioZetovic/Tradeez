package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.List;

import com.tradeez.marketdata.Bar;

/**
 * {@code swing-1.0.0} points and what the structure rules read off them (T11, ICT-01 to ICT-06).
 *
 * <p>Candle <i>i</i> is a swing high when its high is strictly above the highs of the {@code n}
 * candles to its left and at least the highs of the {@code n} candles to its right — so of two equal
 * highs side by side, the earlier is the swing. Lows mirror it. It is known at the close of candle
 * <i>i + n</i>.
 *
 * <p>Every rule built on swings looks back only over a bounded span of candles — {@code maxSpan},
 * from the first candle it reads to the candle that confirms it. That bound is what makes the rule's
 * lookback finite (DEC-049): a rule needing more than that is simply not reported.
 */
final class SwingPoints {

	private SwingPoints() {
	}

	/**
	 * @param index candle of the swing, in the segment
	 * @param price its high, or its low
	 */
	record Swing(int index, boolean high, long price) {

		/** The candle at whose close the swing is known. */
		int confirmIndex(int n) {
			return index + n;
		}

		/** Strictly beyond, in the swing's own direction: higher for a high, lower for a low. */
		boolean beyond(Swing other) {
			return high ? price > other.price : price < other.price;
		}
	}

	/**
	 * A swing ranked against the one of the same kind before it: {@code HH}, {@code LH} or {@code EH}
	 * for a high, {@code HL}, {@code LL} or {@code EL} for a low.
	 *
	 * @param earliest the first candle the label reads: {@code n} before the previous swing
	 */
	record Label(Swing swing, Swing previous, String kind, int earliest) {
	}

	/** Every swing high and low in {@code bars}, in candle order (a candle may be both). */
	static List<Swing> find(List<Bar> bars, int n) {
		List<Swing> swings = new ArrayList<>();
		for (int i = n; i + n < bars.size(); i++) {
			Bar bar = bars.get(i);
			boolean high = true;
			boolean low = true;
			for (int k = 1; k <= n; k++) {
				high &= bar.highTicks() > bars.get(i - k).highTicks() && bar.highTicks() >= bars.get(i + k).highTicks();
				low &= bar.lowTicks() < bars.get(i - k).lowTicks() && bar.lowTicks() <= bars.get(i + k).lowTicks();
			}
			if (high) {
				swings.add(new Swing(i, true, bar.highTicks()));
			}
			if (low) {
				swings.add(new Swing(i, false, bar.lowTicks()));
			}
		}
		return swings;
	}

	/** Only the highs, or only the lows. */
	static List<Swing> side(List<Swing> swings, boolean high) {
		return swings.stream().filter(s -> s.high() == high).toList();
	}

	/**
	 * Each swing labelled against the previous one of its kind, when both lie within {@code maxSpan}
	 * candles of the label's confirmation.
	 */
	static List<Label> labels(List<Swing> swings, int n, int maxSpan) {
		List<Label> labels = new ArrayList<>();
		for (boolean high : new boolean[] { true, false }) {
			List<Swing> same = side(swings, high);
			for (int j = 1; j < same.size(); j++) {
				Swing previous = same.get(j - 1);
				Swing swing = same.get(j);
				int earliest = previous.index() - n;
				if (swing.confirmIndex(n) - earliest + 1 > maxSpan) {
					continue;
				}
				String kind = swing.price() == previous.price() ? (high ? "EH" : "EL")
					: swing.beyond(previous) ? (high ? "HH" : "LL")
					: (high ? "LH" : "HL");
				labels.add(new Label(swing, previous, kind, earliest));
			}
		}
		labels.sort((a, b) -> Integer.compare(a.swing().confirmIndex(n), b.swing().confirmIndex(n)));
		return labels;
	}
}
