package com.tradeez.analysis;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.SwingPoints.Swing;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code swing-1.0.0} (ICT-01): every swing high ({@code HIGH}, a top, pointing down) and swing low
 * ({@code LOW}, pointing up), one candle each side as ICT draws short-term structure — see
 * {@link SwingPoints}. Dense: about 13 per 30 candles on real NQ, highs and lows together, on every
 * timeframe (T11). Swings across the daily halt count; across a roll they cannot.
 */
@Component
public final class SwingDetector extends RuleDetector {

	static final int STRENGTH = 1;

	public SwingDetector() {
		super("SWING", "swing-1.0.0", "ICT-01", "ICT swing high and swing low", Map.of("strength", STRENGTH));
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return 2 * STRENGTH + 1;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		return SwingPoints.find(segment, STRENGTH).stream()
			.map(s -> point(segment, s, s.high() ? "HIGH" : "LOW", segment.get(s.index() - STRENGTH),
				s.confirmIndex(STRENGTH), timeframe, Map.of("price", s.price())))
			.toList();
	}

	/** A swing as a point at its price, from the first candle read to the one that confirms it. */
	static Detection point(List<Bar> segment, Swing swing, String kind, Bar first, int confirmIndex,
			Timeframe timeframe, Map<String, Object> evidence) {
		Bar confirming = segment.get(confirmIndex);
		return new Detection(Detection.Shape.POINT,
			swing.high() ? Detection.Direction.BEARISH : Detection.Direction.BULLISH, kind, first.openTime(),
			segment.get(swing.index()).openTime(), confirming.openTime(),
			confirming.openTime().plus(timeframe.duration()), swing.price(), swing.price(), evidence);
	}
}
