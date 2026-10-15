package com.tradeez.analysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code fvg-state-1.0.0} (ICT-22): each {@code fvg-1.0.0} gap's lifecycle as a series of hits, one
 * per state it reaches — {@code OPEN} when it forms, then {@code TAPPED}, {@code CE}, {@code FILLED},
 * {@code INVERTED} — each confirmed at the close of the candle that moved it there (see
 * {@link FvgLifecycle}). A gap still unfilled {@value #MAX_AGE_CANDLES} candles after it formed is
 * {@code EXPIRED}: the rule stops watching it.
 *
 * <p>At any moment the latest state confirmed so far is the gap's state then — which is how a replay
 * shows a gap open, half-filled or filled without knowing what comes next.
 */
@Component
public final class FvgStateDetector extends RuleDetector {

	/** About two trading days on 5m, a week on 15m. A gap older than this is no longer followed. */
	static final int MAX_AGE_CANDLES = 500;

	private static final FvgDetector.Parameters FVG = FvgDetector.Parameters.DEFAULT;

	public FvgStateDetector() {
		super("FVG_STATE", "fvg-state-1.0.0", "ICT-22", "ICT fair value gap lifecycle", rule());
	}

	private static Map<String, Object> rule() {
		Map<String, Object> p = new LinkedHashMap<>(FVG.asMap());
		p.put("gapRule", FvgDetector.RULE_VERSION);
		p.put("maxAgeCandles", MAX_AGE_CANDLES);
		return p;
	}

	/** The gap's own candles and lookback, then everything up to its oldest tracked candle. */
	@Override
	public int lookbackBars(Timeframe timeframe) {
		return FVG.averageRangeLookback() + 3 + MAX_AGE_CANDLES;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		return FvgLifecycle.events(segment, timeframe, FVG, MAX_AGE_CANDLES).stream()
			.map(e -> FvgLifecycle.detection(e, Detection.Direction.valueOf(e.gap().direction().name()),
				e.state().name(), timeframe))
			.toList();
	}
}
