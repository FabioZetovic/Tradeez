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
 * {@code dealing-range-1.0.0} (ICT-40): the range a leg spans ({@link Ranges}) — from where a displacement
 * that broke structure began to where price turned, an intermediate-term swing. The zone is the range, its
 * line equilibrium. Followed until a candle closes beyond its high or its low, or for 500 candles.
 *
 * <p>The draft asked for the latest bracketing swings, the more recent ending a displacement leg,
 * intermediate-term by default. Measured on two years of real NQ (T18): a one-candle swing as the turn
 * gives a range left within a median of 7 candles, the displacement and little more; the intermediate-term
 * turn — the draft's default — gives 10–11, 5–6 average candles tall, and is the one kept.
 */
@Component
public final class DealingRangeDetector extends RuleDetector {

	public DealingRangeDetector() {
		super("DEALING_RANGE", "dealing-range-1.0.0", "ICT-40", "ICT dealing range", rule());
	}

	/** The leg's parameters, which every rule read from it shares. */
	static Map<String, Object> rule() {
		Map<String, Object> p = new LinkedHashMap<>();
		p.put("strength", Ranges.N);
		p.put("turn", "intermediate-term swing (swing-tier-1.0.0)");
		p.put("displacementRule", "displacement-1.0.0");
		p.put("breakBy", "close");
		p.put("breakWithin", "leg");
		p.put("maxSpanCandles", Ranges.MAX_SPAN);
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
			found.addAll(Ranges.lifecycle(segment, range, null, null, (state, at) -> List.of(Ranges.zone(segment, range,
				state, at, Ranges.direction(range), range.low(), range.high(),
				Map.of("midHalfTicks", range.equilibriumHalfTicks()), timeframe))));
		}
		found.sort((a, b) -> a.confirmedAt().compareTo(b.confirmedAt()));
		return found;
	}
}
