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
 * {@code liquidity-1.0.0} (ICT-10): buy-side liquidity above every swing high, sell-side below every
 * swing low, as a lifecycle — {@code RESTING} from the swing's confirmation, then {@code TAKEN} at the
 * close of the first candle trading a tick beyond it, or {@code EXPIRED} if it was still resting
 * {@value LiquidityLevels#MAX_AGE_CANDLES} candles on. A level's state at any moment is the latest
 * hit confirmed by then. Highs point down and lows up, as the swings they come from.
 *
 * <p>The reference highs and lows of ICT-11 — PDH, PWH, killzone highs — are taken under the same
 * rule, in the New York clock layer.
 */
@Component
public final class LiquidityDetector extends RuleDetector {

	private static final int N = SwingDetector.STRENGTH;

	public LiquidityDetector() {
		super("LIQUIDITY", "liquidity-1.0.0", "ICT-10", "ICT buy-side and sell-side liquidity",
			Map.of("strength", N, "maxAgeCandles", LiquidityLevels.MAX_AGE_CANDLES, "takenBy", "one tick beyond"));
	}

	/** The swing's own candles, then up to its oldest followed candle. */
	@Override
	public int lookbackBars(Timeframe timeframe) {
		return 2 * N + 1 + LiquidityLevels.MAX_AGE_CANDLES;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Detection> found = new ArrayList<>();
		for (Level level : LiquidityLevels.levels(segment, SwingPoints.find(segment, N), N)) {
			found.add(detection(segment, level, "RESTING", level.from(), timeframe));
			if (level.taken() >= 0) {
				found.add(detection(segment, level, "TAKEN", level.taken(), timeframe));
			} else if (level.expired() >= 0) {
				found.add(detection(segment, level, "EXPIRED", level.expired(), timeframe));
			}
		}
		found.sort((a, b) -> a.confirmedAt().compareTo(b.confirmedAt()));
		return found;
	}

	private static Detection detection(List<Bar> segment, Level level, String kind, int at, Timeframe timeframe) {
		Bar bar = segment.get(at);
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("side", level.high() ? "BSL" : "SSL");
		evidence.put("price", level.price());
		evidence.put("candleTime", bar.openTime().getEpochSecond());
		return new Detection(Detection.Shape.LEVEL,
			level.high() ? Detection.Direction.BEARISH : Detection.Direction.BULLISH, kind,
			segment.get(level.swing().index()).openTime(), segment.get(level.swing().index()).openTime(),
			bar.openTime(), bar.openTime().plus(timeframe.duration()), level.price(), level.price(), evidence);
	}
}
