package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.Ranges.Range;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code ote-1.0.0} (ICT-42): the optimal trade entry of a leg ({@link Ranges}) — the retracement from
 * {@value #NEAR_PERCENT}% to {@value #FAR_PERCENT}% of the way back from its end, rounded outward to the
 * tick grid, its line at 70.5% in half-ticks. Bullish after a leg up: a zone below price. Known with the
 * leg; tapped when a candle trades into it; then over with the range — broken high or low, or expired.
 *
 * <p>The draft read the leg of the last {@code mss-1.0.0}; this reads the leg of every displacement that
 * broke structure, of which a shift is one kind. Measured on two years of real NQ (T18): price retraces
 * into it about 64% of the time before its range is left, 59% before a new extreme beyond the leg.
 */
@Component
public final class OteDetector extends RuleDetector {

	static final int NEAR_PERCENT = 62;
	static final int FAR_PERCENT = 79;
	static final int LINE_PERMILLE = 705;

	public OteDetector() {
		super("OTE", "ote-1.0.0", "ICT-42", "ICT optimal trade entry", rule());
	}

	private static Map<String, Object> rule() {
		Map<String, Object> p = new LinkedHashMap<>();
		p.put("rangeRule", "dealing-range-1.0.0");
		p.put("nearPercent", NEAR_PERCENT);
		p.put("farPercent", FAR_PERCENT);
		p.put("linePercent", "70.5");
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
			long near = Math.floorDiv((long) NEAR_PERCENT * range.length(), 100);
			long far = -Math.floorDiv(-(long) FAR_PERCENT * range.length(), 100);
			long low = range.up() ? range.endPrice() - far : range.endPrice() + near;
			long high = range.up() ? range.endPrice() - near : range.endPrice() + far;
			if (low >= high) {
				continue;
			}
			Map<String, Object> extra = Map.of("midHalfTicks", range.retracementHalfTicks(LINE_PERMILLE));
			found.addAll(Ranges.lifecycle(segment, range, low, high, (state, at) -> List.of(
				Ranges.zone(segment, range, state, at, Ranges.direction(range), low, high, extra, timeframe))));
		}
		found.sort((a, b) -> a.confirmedAt().compareTo(b.confirmedAt()));
		return found;
	}
}
