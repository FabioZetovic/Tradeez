package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code void-1.0.0} (ICT-26), two kinds of one-sided price:
 * <ul>
 * <li>{@code GAP} — two consecutive candles whose ranges do not overlap at all. The zone is the space
 * between them. Confirmed at the second candle's close. Rare on a market that trades around the clock:
 * about one per 75 one-minute candles on real NQ, almost none on five minutes and above.</li>
 * <li>{@code VOID} — a liquidity void: at least {@value #MIN_CANDLES} consecutive candles closing the
 * same way, each closing beyond the one before and overlapping it by at most
 * {@value #MAX_OVERLAP_PERCENT}% of its own range. (The draft did not say the run must progress; without
 * it, up-closing candles each gapping lower would count.)
 * The zone runs from the first candle's open to the last one's close. Confirmed when the run is over:
 * at the close of the first candle that does not continue it, so the void reported is the whole run.</li>
 * </ul>
 *
 * <p>The void's numbers are the draft's, kept after measuring them on two years of real NQ (session
 * 017): about one void per 270 five-minute candles — roughly one a day. The longest run found was six
 * candles; a run is measured over at most its last {@value #MAX_CANDLES}, which bounds what a void can
 * depend on without ever having cut one.
 */
@Component
public final class VoidDetector extends RuleDetector {

	static final int MIN_CANDLES = 3;
	static final int MAX_OVERLAP_PERCENT = 20;
	static final int MAX_CANDLES = 10;

	public VoidDetector() {
		super("VOID", "void-1.0.0", "ICT-26", "ICT gap and liquidity void", Map.of(
			"minCandles", MIN_CANDLES, "maxOverlapPercent", MAX_OVERLAP_PERCENT, "maxCandles", MAX_CANDLES));
	}

	/** The candle before the longest run measured, the run, and the candle that ends it. */
	@Override
	public int lookbackBars(Timeframe timeframe) {
		return MAX_CANDLES + 2;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		long step = timeframe.seconds();
		List<Detection> found = new ArrayList<>();
		for (int i = 1; i < segment.size(); i++) {
			Bar a = segment.get(i - 1);
			Bar b = segment.get(i);
			if (Segments.consecutive(List.of(a, b), step)) {
				if (b.lowTicks() > a.highTicks()) {
					found.add(gap(a, b, a.highTicks(), b.lowTicks(), Detection.Direction.BULLISH, timeframe));
				} else if (b.highTicks() < a.lowTicks()) {
					found.add(gap(a, b, b.highTicks(), a.lowTicks(), Detection.Direction.BEARISH, timeframe));
				}
			}
			// Candle i ends whatever run came before it, if it does not continue it.
			if (continues(segment, i, step)) {
				continue;
			}
			int first = i - 1;
			while (first > i - MAX_CANDLES && continues(segment, first, step)) {
				first--;
			}
			int length = i - first;
			if (length >= MIN_CANDLES && direction(segment.get(first)) != 0) {
				found.add(runVoid(segment, first, i - 1, b, timeframe));
			}
		}
		return found;
	}

	/** Whether candle {@code k} carries on the one-sided run of candle {@code k - 1}. */
	private static boolean continues(List<Bar> bars, int k, long step) {
		if (k < 1) {
			return false;
		}
		Bar prev = bars.get(k - 1);
		Bar bar = bars.get(k);
		long range = bar.highTicks() - bar.lowTicks();
		long overlap = Math.max(0, Math.min(prev.highTicks(), bar.highTicks()) - Math.max(prev.lowTicks(), bar.lowTicks()));
		return Segments.consecutive(List.of(prev, bar), step)
			&& direction(bar) != 0 && direction(bar) == direction(prev)
			&& Long.signum(bar.closeTicks() - prev.closeTicks()) == direction(bar)
			&& range > 0 && overlap * 100 <= (long) MAX_OVERLAP_PERCENT * range;
	}

	private static int direction(Bar bar) {
		return Long.signum(bar.closeTicks() - bar.openTicks());
	}

	private static Detection gap(Bar a, Bar b, long low, long high, Detection.Direction direction,
			Timeframe timeframe) {
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("candle1Time", a.openTime().getEpochSecond());
		evidence.put("candle1HighTicks", a.highTicks());
		evidence.put("candle1LowTicks", a.lowTicks());
		evidence.put("candle2Time", b.openTime().getEpochSecond());
		evidence.put("candle2HighTicks", b.highTicks());
		evidence.put("candle2LowTicks", b.lowTicks());
		return new Detection(Detection.Shape.ZONE, direction, "GAP", a.openTime(), b.openTime(), b.openTime(),
			b.openTime().plus(timeframe.duration()), low, high, evidence);
	}

	private static Detection runVoid(List<Bar> bars, int first, int last, Bar ender, Timeframe timeframe) {
		Bar start = bars.get(first);
		Bar end = bars.get(last);
		boolean up = direction(start) > 0;
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("firstCandleTime", start.openTime().getEpochSecond());
		evidence.put("lastCandleTime", end.openTime().getEpochSecond());
		evidence.put("candles", last - first + 1);
		evidence.put("openTicks", start.openTicks());
		evidence.put("closeTicks", end.closeTicks());
		evidence.put("endedByTime", ender.openTime().getEpochSecond());
		return new Detection(Detection.Shape.ZONE, up ? Detection.Direction.BULLISH : Detection.Direction.BEARISH,
			"VOID", start.openTime(), start.openTime(), end.openTime(), ender.openTime().plus(timeframe.duration()),
			up ? start.openTicks() : end.closeTicks(), up ? end.closeTicks() : start.openTicks(), evidence);
	}
}
