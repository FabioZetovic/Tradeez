package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.LiquidityLevels.Level;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code sweep-1.0.0} (ICT-13): what price did when it took a swing level's liquidity.
 * <ul>
 * <li>{@code SWEEP} — the candle that traded through closed back on the level's side: the stops were
 * the target. Known at that close.</li>
 * <li>{@code RUN} — it closed beyond, and neither of the next {@value #HOLD_CANDLES} candles closed
 * back: the level was a waypoint. Known at the last of them.</li>
 * </ul>
 * Anything else is undecided and is not reported. When one candle takes several levels on a side,
 * the most extreme is the one it is judged on. A sweep points away from the level (a swept high,
 * down); a run points through it.
 *
 * <p>Measured on two years of real NQ (T12): of the swing levels taken, about half are swept, 29% run
 * and 21% are undecided, on every timeframe. A description of the past, not an edge.
 */
@Component
public final class SweepDetector extends RuleDetector {

	static final int CLOSE_BACK_WITHIN = 1;
	static final int HOLD_CANDLES = 2;
	private static final int N = SwingDetector.STRENGTH;

	public SweepDetector() {
		super("SWEEP", "sweep-1.0.0", "ICT-13", "ICT liquidity sweep versus run", Map.of("strength", N,
			"maxAgeCandles", LiquidityLevels.MAX_AGE_CANDLES, "closeBackWithin", CLOSE_BACK_WITHIN,
			"holdCandles", HOLD_CANDLES, "levelRule", "liquidity-1.0.0"));
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return 2 * N + 1 + LiquidityLevels.MAX_AGE_CANDLES + HOLD_CANDLES;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Level> taken = LiquidityLevels.levels(segment, SwingPoints.find(segment, N), N).stream()
			.filter(l -> l.taken() >= 0)
			.toList();
		List<Detection> found = new ArrayList<>();
		for (Level level : mostExtremePerCandle(taken)) {
			Detection judged = judge(segment, level, timeframe);
			if (judged != null) {
				found.add(judged);
			}
		}
		found.sort((a, b) -> a.confirmedAt().compareTo(b.confirmedAt()));
		return found;
	}

	/** Of the levels a candle took on one side, the most extreme: the one the move is judged on. */
	static List<Level> mostExtremePerCandle(List<Level> taken) {
		Map<String, Level> best = new LinkedHashMap<>();
		for (Level level : taken) {
			String key = level.taken() + (level.high() ? "H" : "L");
			Level seen = best.get(key);
			boolean further = seen == null
				|| (level.high() ? level.price() > seen.price() : level.price() < seen.price());
			if (further) {
				best.put(key, level);
			}
		}
		return new ArrayList<>(best.values());
	}

	private static Detection judge(List<Bar> segment, Level level, Timeframe timeframe) {
		int t = level.taken();
		boolean high = level.high();
		if (LiquidityLevels.closedBack(segment.get(t), high, level.price())) {
			return detection(segment, level, "SWEEP", t, timeframe);
		}
		if (t + HOLD_CANDLES >= segment.size()) {
			return null;
		}
		for (int k = t + 1; k <= t + HOLD_CANDLES; k++) {
			if (LiquidityLevels.closedBack(segment.get(k), high, level.price())) {
				return null;
			}
		}
		return detection(segment, level, "RUN", t + HOLD_CANDLES, timeframe);
	}

	private static Detection detection(List<Bar> segment, Level level, String kind, int decided, Timeframe timeframe) {
		boolean high = level.high();
		boolean down = kind.equals("SWEEP") == high;
		Bar deciding = segment.get(decided);
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("side", high ? "BSL" : "SSL");
		evidence.put("price", level.price());
		evidence.put("takenTime", segment.get(level.taken()).openTime().getEpochSecond());
		evidence.put("takenCloseTicks", segment.get(level.taken()).closeTicks());
		return new Detection(Detection.Shape.LEVEL, down ? Detection.Direction.BEARISH : Detection.Direction.BULLISH,
			kind, segment.get(level.swing().index()).openTime(), segment.get(level.taken()).openTime(),
			deciding.openTime(), deciding.openTime().plus(timeframe.duration()), level.price(), level.price(), evidence);
	}
}
