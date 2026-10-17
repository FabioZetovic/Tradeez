package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.Ranges.Range;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code pd-1.0.0} (ICT-41): a {@code dealing-range-1.0.0} range split at equilibrium, its middle in
 * half-ticks. Above it is <b>premium</b> — a bearish zone, where ICT looks to sell — and below it
 * <b>discount</b>, a bullish one, where ICT looks to buy. On an odd range equilibrium falls between two
 * ticks and belongs to neither half. Two hits per state of the range, one per half, with the range's life.
 */
@Component
public final class PremiumDiscountDetector extends RuleDetector {

	public PremiumDiscountDetector() {
		super("PD", "pd-1.0.0", "ICT-41", "ICT premium, discount and equilibrium", rule());
	}

	private static Map<String, Object> rule() {
		Map<String, Object> p = new java.util.LinkedHashMap<>();
		p.put("rangeRule", "dealing-range-1.0.0");
		p.put("equilibriumPercent", 50);
		p.put("maxAgeCandles", Ranges.MAX_AGE_CANDLES);
		return p;
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return Ranges.MAX_SPAN + Ranges.MAX_AGE_CANDLES;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Detection> found = new ArrayList<>();
		for (Range range : Ranges.find(segment, timeframe)) {
			long eq = range.equilibriumHalfTicks();
			// Equilibrium on a tick belongs to both halves' edges; between two ticks, to neither.
			long premiumLow = Math.floorDiv(eq + 1, 2);
			long discountHigh = Math.floorDiv(eq, 2);
			if (premiumLow >= range.high() || discountHigh <= range.low()) {
				continue;
			}
			Map<String, Object> extra = Map.of("midHalfTicks", eq);
			found.addAll(Ranges.lifecycle(segment, range, null, null, (state, at) -> List.of(
				Ranges.zone(segment, range, state, at, Detection.Direction.BEARISH, premiumLow, range.high(), extra, timeframe),
				Ranges.zone(segment, range, state, at, Detection.Direction.BULLISH, range.low(), discountHigh, extra, timeframe))));
		}
		found.sort((a, b) -> a.confirmedAt().compareTo(b.confirmedAt()));
		return found;
	}
}
