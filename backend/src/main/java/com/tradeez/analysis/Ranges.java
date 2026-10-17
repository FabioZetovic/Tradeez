package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import com.tradeez.analysis.Blocks.Break;
import com.tradeez.analysis.DisplacementDetector.Leg;
import com.tradeez.analysis.SwingPoints.Swing;
import com.tradeez.analysis.SwingTierDetector.Ranked;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * The leg every rule of ICT_CONCEPTS.md Part 6 is read from (T18, DEC-062), and what happens to its range
 * after — shared by the dealing range, premium and discount, the OTE and the standard deviations.
 *
 * <p><b>The leg</b>, bullish: a {@code displacement-1.0.0} move in which a candle is the first to close above
 * the latest swing high (as {@code ob-1.0.0} reads a break). It starts at the lowest low between that broken
 * swing and the move — where the move began — and ends at the first intermediate-term swing high
 * ({@code swing-tier-1.0.0}) at or after the breaking candle: where price turned, as that rule ranks a turn.
 * Known when that rank is, at the close of the next swing high's confirming candle. A leg whose range price
 * has already left by then is not reported. When two legs reach the same turn, the earlier — the move's
 * origin — is the one read; a candle that turned both ways (an outside candle ranked high and low) ends
 * only the range known first. Everything read lies within {@value #MAX_SPAN} candles. Bearish mirrors it.
 *
 * <p><b>Its range's life</b>, from then on: broken when a candle closes above its high or below its low,
 * or expired {@value #MAX_AGE_CANDLES} candles on, still whole.
 *
 * <p>Measured on two years of real NQ (T18): about 0.7 legs per 30 candles on every timeframe from 1m to 1h,
 * 5–6 average candles long; a range lasts a median of 10–11 candles, and is left beyond its end about six
 * times in ten, beyond its start four.
 */
final class Ranges {

	enum State {
		FORMED, TAPPED, BROKEN_HIGH, BROKEN_LOW, EXPIRED
	}

	static final int MAX_AGE_CANDLES = 500;
	static final int MAX_SPAN = SwingTierDetector.MAX_SPAN;
	static final int N = SwingDetector.STRENGTH;

	private Ranges() {
	}

	/**
	 * A leg by candle index in its segment: from {@code start} to the turn at {@code end}, known at {@code form}.
	 *
	 * @param up       a move up: the range's low is its start
	 * @param evidence what the leg was read from, for the explanations
	 */
	record Range(boolean up, int start, int end, int form, long startPrice, long endPrice, Map<String, Object> evidence) {

		long low() {
			return Math.min(startPrice, endPrice);
		}

		long high() {
			return Math.max(startPrice, endPrice);
		}

		long length() {
			return high() - low();
		}

		/** Equilibrium, the range's middle, in half-ticks (ICT-41). */
		long equilibriumHalfTicks() {
			return low() + high();
		}

		/**
		 * The price {@code percent} of the way back from the leg's end toward its start, in half-ticks and
		 * rounded half up: 50 is equilibrium, 70.5 the OTE's line.
		 */
		long retracementHalfTicks(long permille) {
			long back = Math.floorDiv(2 * permille * length() + 500, 1000);
			return up ? 2 * endPrice - back : 2 * endPrice + back;
		}
	}

	/** Every leg in {@code segment}, in the order they became known. */
	static List<Range> find(List<Bar> bars, Timeframe timeframe) {
		List<Swing> swings = SwingPoints.find(bars, N);
		List<Swing> highs = SwingPoints.side(swings, true);
		List<Swing> lows = SwingPoints.side(swings, false);
		List<Ranked> turnsUp = SwingTierDetector.intermediate(highs);
		List<Ranked> turnsDown = SwingTierDetector.intermediate(lows);
		List<Range> found = new ArrayList<>();
		for (Leg leg : DisplacementDetector.legs(bars, timeframe)) {
			boolean up = leg.up();
			Break broke = null;
			for (int c = leg.first(); c <= leg.last() && broke == null; c++) {
				broke = Blocks.breakAt(bars, up ? highs : lows, c);
			}
			if (broke == null) {
				continue;
			}
			int start = broke.level().index();
			for (int k = start + 1; k <= leg.first(); k++) {
				if (up ? bars.get(k).lowTicks() < bars.get(start).lowTicks() : bars.get(k).highTicks() > bars.get(start).highTicks()) {
					start = k;
				}
			}
			Ranked turn = firstAtOrAfter(up ? turnsUp : turnsDown, broke.at());
			if (turn == null) {
				continue;
			}
			int form = turn.confirmIndex();
			int earliest = Math.min(Math.min(broke.level().index() - N, leg.middle() - 1 - Blocks.RANGE_LOOKBACK),
				Math.min(start, turn.earliest()));
			long startPrice = up ? bars.get(start).lowTicks() : bars.get(start).highTicks();
			long endPrice = turn.swing().price();
			long close = bars.get(form).closeTicks();
			// Not reported once price has left it: the range is gone before it was known.
			if (earliest < form - MAX_SPAN + 1 || close < Math.min(startPrice, endPrice) || close > Math.max(startPrice, endPrice)) {
				continue;
			}
			Map<String, Object> evidence = new LinkedHashMap<>();
			evidence.put("startTime", Blocks.time(bars, start));
			evidence.put("startPrice", startPrice);
			evidence.put("endTime", Blocks.time(bars, turn.swing().index()));
			evidence.put("endPrice", endPrice);
			evidence.put("brokenPrice", broke.level().price());
			evidence.put("breakTime", Blocks.time(bars, broke.at()));
			evidence.put("displacementTime", Blocks.time(bars, leg.middle()));
			found.add(new Range(up, start, turn.swing().index(), form, startPrice, endPrice, evidence));
		}
		// One range per turn: the one known first, and of two legs known together the earlier. Stable.
		found.sort((a, b) -> Integer.compare(a.form(), b.form()));
		Set<Integer> turned = new HashSet<>();
		return found.stream().filter(r -> turned.add(r.end())).toList();
	}

	/** The first ranked swing on a candle at or after {@code index}. */
	private static Ranked firstAtOrAfter(List<Ranked> ranked, int index) {
		int lo = 0;
		int hi = ranked.size();
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (ranked.get(mid).swing().index() < index) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		return lo < ranked.size() ? ranked.get(lo) : null;
	}

	/**
	 * A range's life as hits: formed, then — with {@code zoneLow..zoneHigh} for a zone that can be traded into
	 * (the OTE) — tapped, then broken high or low, or expired. {@code hit} builds a state's hits at a candle.
	 */
	static List<Detection> lifecycle(List<Bar> bars, Range range, Long zoneLow, Long zoneHigh,
			BiFunction<State, Integer, List<Detection>> hit) {
		List<Detection> found = new ArrayList<>(hit.apply(State.FORMED, range.form()));
		boolean tapped = zoneLow == null;
		int last = Math.min(range.form() + MAX_AGE_CANDLES, bars.size() - 1);
		for (int k = range.form() + 1; k <= last; k++) {
			Bar b = bars.get(k);
			if (!tapped && (range.up() ? b.lowTicks() <= zoneHigh : b.highTicks() >= zoneLow)) {
				tapped = true;
				found.addAll(hit.apply(State.TAPPED, k));
			}
			if (b.closeTicks() > range.high() || b.closeTicks() < range.low()) {
				found.addAll(hit.apply(b.closeTicks() > range.high() ? State.BROKEN_HIGH : State.BROKEN_LOW, k));
				return found;
			}
			if (k == range.form() + MAX_AGE_CANDLES) {
				found.addAll(hit.apply(State.EXPIRED, k));
			}
		}
		return found;
	}

	/** A range's state at candle {@code at} as a zone over the leg to that candle. */
	static Detection zone(List<Bar> bars, Range range, State state, int at, Detection.Direction direction, long low,
			long high, Map<String, Object> extra, Timeframe timeframe) {
		Bar candle = bars.get(at);
		Map<String, Object> evidence = new LinkedHashMap<>(range.evidence());
		evidence.putAll(extra);
		evidence.put("state", state.name());
		evidence.put("candleTime", candle.openTime().getEpochSecond());
		evidence.put("candleHighTicks", candle.highTicks());
		evidence.put("candleLowTicks", candle.lowTicks());
		evidence.put("candleCloseTicks", candle.closeTicks());
		return new Detection(Detection.Shape.ZONE, direction, state.name(), bars.get(range.start()).openTime(),
			bars.get(range.end()).openTime(), candle.openTime(), candle.openTime().plus(timeframe.duration()), low, high,
			evidence);
	}

	static Detection.Direction direction(Range range) {
		return range.up() ? Detection.Direction.BULLISH : Detection.Direction.BEARISH;
	}
}
