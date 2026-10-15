package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.EqualHighsLowsDetector.Pool;
import com.tradeez.analysis.LiquidityLevels.Level;
import com.tradeez.analysis.SwingPoints.Swing;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * Turtle soup (ICT-14), read under {@code sweep-1.0.0}: a sweep of relative equal highs or lows — the
 * obvious double top whose stops were the target. The pool's level is the more extreme of its two
 * swings, resting from its confirmation; the candle that takes it closes back inside. Known at that
 * close, pointing away from the pool. When one candle sweeps several pools on a side, the most
 * extreme counts.
 *
 * <p>The curriculum also counts sweeps of the reference highs and lows (PDH, PWH…); those are taken
 * in the New York clock layer, and are not reported as turtle soup here.
 */
@Component
public final class TurtleSoupDetector extends RuleDetector {

	public TurtleSoupDetector() {
		super("TURTLE_SOUP", "sweep-1.0.0", "ICT-14", "Turtle soup: a sweep of relative equal highs or lows",
			Map.of("poolRule", "eqhl-1.0.0", "maxAgeCandles", LiquidityLevels.MAX_AGE_CANDLES,
				"closeBackWithin", SweepDetector.CLOSE_BACK_WITHIN));
	}

	/** The pool's own span, then up to its oldest followed candle. */
	@Override
	public int lookbackBars(Timeframe timeframe) {
		return EqualHighsLowsDetector.MAX_SPAN + LiquidityLevels.MAX_AGE_CANDLES + 1;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		Map<Swing, Pool> poolOf = new LinkedHashMap<>();
		List<Level> taken = new ArrayList<>();
		for (Pool pool : EqualHighsLowsDetector.pools(segment, SwingPoints.find(segment, SwingDetector.STRENGTH))) {
			Swing level = new Swing(pool.second().index(), pool.high(), pool.extreme());
			Level followed = LiquidityLevels.follow(segment, level, pool.confirmIndex());
			if (followed.taken() >= 0) {
				poolOf.put(level, pool);
				taken.add(followed);
			}
		}
		List<Detection> found = new ArrayList<>();
		for (Level level : SweepDetector.mostExtremePerCandle(taken)) {
			Bar bar = segment.get(level.taken());
			if (LiquidityLevels.closedBack(bar, level.high(), level.price())) {
				found.add(detection(segment, poolOf.get(level.swing()), level, timeframe));
			}
		}
		found.sort((a, b) -> a.confirmedAt().compareTo(b.confirmedAt()));
		return found;
	}

	private static Detection detection(List<Bar> segment, Pool pool, Level level, Timeframe timeframe) {
		Bar sweeping = segment.get(level.taken());
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("poolFirstTime", segment.get(pool.first().index()).openTime().getEpochSecond());
		evidence.put("poolSecondTime", segment.get(pool.second().index()).openTime().getEpochSecond());
		evidence.put("price", level.price());
		evidence.put("closeTicks", sweeping.closeTicks());
		return new Detection(Detection.Shape.LEVEL,
			level.high() ? Detection.Direction.BEARISH : Detection.Direction.BULLISH, null,
			segment.get(pool.first().index()).openTime(), sweeping.openTime(), sweeping.openTime(),
			sweeping.openTime().plus(timeframe.duration()), level.price(), level.price(), evidence);
	}
}
