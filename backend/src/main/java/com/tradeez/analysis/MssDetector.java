package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.DisplacementDetector.Leg;
import com.tradeez.analysis.SwingPoints.Swing;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code mss-1.0.0} (ICT-05): a market structure shift — in ICT's order, a sweep, then displacement,
 * then a break. Bullish:
 * <ol>
 * <li>a swing low <i>L</i> trades below an earlier swing low that was still resting — no candle had
 * traded below it since it formed (the sweep);</li>
 * <li><i>H</i> is the latest swing high confirmed before <i>L</i>;</li>
 * <li>within {@value #MAX_CANDLES} candles of <i>L</i>, a candle <b>closes</b> above <i>H</i>, the first
 * to since <i>H</i> formed;</li>
 * <li>a {@code displacement-1.0.0} leg starts between <i>L</i> and that close.</li>
 * </ol>
 * Confirmed once both the break and the displacement are known: at the later of the breaking close
 * and the leg's confirmation. Often they are one move — the displacement candle is the one that
 * closes through <i>H</i>, and its gap is known a candle later. Anchored on the breaking candle;
 * bearish mirrors it. When several lows qualify the latest is taken: one shift per breaking candle. Everything it reads lies within {@value #MAX_SPAN} candles.
 * The draft's parameters — sweep required, displacement required, break by close — are kept; each is
 * stored, so a looser reading would be a new version.
 */
@Component
public final class MssDetector extends RuleDetector {

	static final int MAX_CANDLES = 20;
	private static final int MAX_SPAN = StructureDetector.MAX_SPAN;
	private static final int N = SwingDetector.STRENGTH;
	private static final int RANGE_LOOKBACK = 14;

	public MssDetector() {
		super("MSS", "mss-1.0.0", "ICT-05", "ICT market structure shift", Map.of("strength", N,
			"maxCandles", MAX_CANDLES, "maxSpanCandles", MAX_SPAN, "requiresSweep", true,
			"requiresDisplacement", true, "breakBy", "close", "displacementRule", "displacement-1.0.0"));
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return MAX_SPAN;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Swing> swings = SwingPoints.find(segment, N);
		Market market = new Market(segment, SwingPoints.side(swings, true), SwingPoints.side(swings, false),
			DisplacementDetector.legs(segment, timeframe));
		List<Detection> found = new ArrayList<>();
		int seen = 0;
		for (int b = 1; b < segment.size(); b++) {
			while (seen < swings.size() && swings.get(seen).index() < b) {
				seen++;
			}
			for (boolean up : new boolean[] { true, false }) {
				Detection shift = market.shiftBrokenAt(swings, seen, b, up, timeframe);
				if (shift != null) {
					found.add(shift);
				}
			}
		}
		// In the order they became known.
		found.sort((x, y) -> x.confirmedAt().compareTo(y.confirmedAt()));
		return found;
	}

	/** One segment's candles, swings by kind and displacement legs, for the shift at each candle. */
	private record Market(List<Bar> bars, List<Swing> highs, List<Swing> lows, List<Leg> legs) {

		/** The shift whose break is candle {@code b}, if any; {@code seen} swings lie before it. */
		Detection shiftBrokenAt(List<Swing> swings, int seen, int b, boolean up, Timeframe timeframe) {
			// The pivot is the swept swing — a low for a bullish shift; the level is what breaks — a high.
			for (int p = seen - 1; p >= 0 && b - swings.get(p).index() <= MAX_CANDLES; p--) {
				Swing pivot = swings.get(p);
				if (pivot.high() == up) {
					continue;
				}
				Swing level = latestConfirmedBefore(up ? highs : lows, pivot.index());
				if (level == null || !BosChochDetector.firstClose(bars, level, b)) {
					continue;
				}
				Swing swept = swept(up ? lows : highs, pivot, b);
				Leg leg = swept == null ? null : leg(up, pivot.index(), b);
				if (leg == null) {
					continue;
				}
				int e = Math.max(b, leg.third());
				// The leg's gap reads the 14 candles before its first candle.
				int earliest = Math.min(Math.min(swept.index(), level.index()) - N, leg.middle() - 1 - RANGE_LOOKBACK);
				if (earliest >= e - MAX_SPAN + 1) {
					return detection(swept, pivot, level, leg, b, e, up, timeframe);
				}
			}
			return null;
		}

		/** The latest swing of {@code side} confirmed before candle {@code index} opened. */
		private static Swing latestConfirmedBefore(List<Swing> side, int index) {
			int lo = 0;
			int hi = side.size() - 1;
			Swing latest = null;
			while (lo <= hi) {
				int mid = (lo + hi) >>> 1;
				if (side.get(mid).confirmIndex(N) < index) {
					latest = side.get(mid);
					lo = mid + 1;
				} else {
					hi = mid - 1;
				}
			}
			return latest;
		}

		/**
		 * The latest earlier swing of the pivot's kind that the pivot went beyond while it was still
		 * resting — untouched from its own candle to the pivot's — within the span; or null. Walks back
		 * keeping the most extreme price traded in between, and stops once no earlier swing could
		 * both have rested and been swept.
		 */
		private Swing swept(List<Swing> side, Swing pivot, int e) {
			boolean high = pivot.high();
			long extreme = high ? Long.MIN_VALUE : Long.MAX_VALUE;
			int from = pivot.index();
			for (int q = firstAtOrAfter(side.size(), i -> side.get(i).index(), pivot.index()) - 1; q >= 0; q--) {
				Swing earlier = side.get(q);
				if (earlier.index() - N < e - MAX_SPAN + 1) {
					return null;
				}
				for (int k = earlier.index() + 1; k < from; k++) {
					extreme = high ? Math.max(extreme, bars.get(k).highTicks()) : Math.min(extreme, bars.get(k).lowTicks());
				}
				from = earlier.index() + 1;
				boolean rested = high ? extreme <= earlier.price() : extreme >= earlier.price();
				if (earlier.confirmIndex(N) < pivot.index() && rested && pivot.beyond(earlier)) {
					return earlier;
				}
				// Anything earlier would have to sit beyond what has traded since — and so beyond the pivot.
				if (high ? extreme >= pivot.price() : extreme <= pivot.price()) {
					return null;
				}
			}
			return null;
		}

		/** The first displacement leg the shift's way starting between the pivot and the break. */
		private Leg leg(boolean up, int from, int b) {
			for (int i = firstAtOrAfter(legs.size(), j -> legs.get(j).first(), from); i < legs.size(); i++) {
				Leg leg = legs.get(i);
				if (leg.first() > b) {
					return null;
				}
				if (leg.up() == up) {
					return leg;
				}
			}
			return null;
		}

		/** The first position whose key is at least {@code target}, keys rising with position. */
		private static int firstAtOrAfter(int size, java.util.function.IntUnaryOperator key, int target) {
			int lo = 0;
			int hi = size;
			while (lo < hi) {
				int mid = (lo + hi) >>> 1;
				if (key.applyAsInt(mid) < target) {
					lo = mid + 1;
				} else {
					hi = mid;
				}
			}
			return lo;
		}

		private Detection detection(Swing swept, Swing pivot, Swing level, Leg leg, int b, int e, boolean up,
				Timeframe timeframe) {
			Bar breaking = bars.get(b);
			Bar confirming = bars.get(e);
			Map<String, Object> evidence = new LinkedHashMap<>();
			evidence.put("sweptTime", bars.get(swept.index()).openTime().getEpochSecond());
			evidence.put("sweptPrice", swept.price());
			evidence.put("pivotTime", bars.get(pivot.index()).openTime().getEpochSecond());
			evidence.put("pivotPrice", pivot.price());
			evidence.put("brokenTime", bars.get(level.index()).openTime().getEpochSecond());
			evidence.put("brokenPrice", level.price());
			evidence.put("displacementTime", bars.get(leg.middle()).openTime().getEpochSecond());
			evidence.put("closeTicks", breaking.closeTicks());
			return new Detection(Detection.Shape.LEVEL, up ? Detection.Direction.BULLISH : Detection.Direction.BEARISH,
				null, bars.get(Math.min(swept.index(), level.index())).openTime(), breaking.openTime(),
				confirming.openTime(), confirming.openTime().plus(timeframe.duration()), level.price(), level.price(),
				evidence);
		}
	}
}
