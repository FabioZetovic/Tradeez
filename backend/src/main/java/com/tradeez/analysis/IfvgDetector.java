package com.tradeez.analysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.FvgLifecycle.State;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code ifvg-1.0.0} (ICT-23): a {@code fvg-1.0.0} gap that a candle closed straight through — beyond
 * its far edge — within {@value FvgStateDetector#MAX_AGE_CANDLES} candles of forming. Confirmed at
 * that close. ICT's claim is that the failed gap flips sides, so the hit points the other way: a
 * failed bullish gap is a bearish inversion gap. That is the claim being labelled, not endorsed.
 */
@Component
public final class IfvgDetector extends RuleDetector {

	private static final FvgDetector.Parameters FVG = FvgDetector.Parameters.DEFAULT;

	public IfvgDetector() {
		super("IFVG", "ifvg-1.0.0", "ICT-23", "ICT inversion fair value gap", rule());
	}

	private static Map<String, Object> rule() {
		Map<String, Object> p = new LinkedHashMap<>(FVG.asMap());
		p.put("gapRule", FvgDetector.RULE_VERSION);
		p.put("maxAgeCandles", FvgStateDetector.MAX_AGE_CANDLES);
		return p;
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return FVG.averageRangeLookback() + 3 + FvgStateDetector.MAX_AGE_CANDLES;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		return FvgLifecycle.events(segment, timeframe, FVG, FvgStateDetector.MAX_AGE_CANDLES).stream()
			.filter(e -> e.state() == State.INVERTED)
			.map(e -> FvgLifecycle.detection(e, e.gap().direction() == FvgDetector.Direction.BULLISH
				? Detection.Direction.BEARISH
				: Detection.Direction.BULLISH, null, timeframe))
			.toList();
	}
}
