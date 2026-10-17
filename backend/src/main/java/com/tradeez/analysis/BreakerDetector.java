package com.tradeez.analysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.Flips.Flip;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code breaker-1.0.0} (ICT-31): a block that failed and flipped after a sweep ({@link Flips}). Bullish:
 * after a swing low and a swing high, price falls to a lower low — taking the sell-side liquidity below the
 * first — then a displacement closes back above the high. The up-close candles at that high, a bearish
 * block that failed, become a bullish zone. Followed as every block is ({@link Blocks}).
 *
 * <p>Measured on two years of real NQ (T17): about 0.4 breakers per 30 candles, and 0.5 mitigation blocks
 * — the same flip without the sweep — on every timeframe from 1m to 1h.
 */
@Component
public final class BreakerDetector extends RuleDetector {

	public BreakerDetector() {
		super("BREAKER", "breaker-1.0.0", "ICT-31", "ICT breaker block", rule(true));
	}

	/** The parameters of a flip; {@code swept} is what tells a breaker from a mitigation block. */
	static Map<String, Object> rule(boolean swept) {
		Map<String, Object> p = new LinkedHashMap<>();
		p.put("strength", Blocks.N);
		p.put("requiresSweep", swept);
		p.put("displacementRule", "displacement-1.0.0");
		p.put("breakBy", "close");
		p.put("mergeRun", true);
		p.put("maxRunCandles", Blocks.MAX_RUN);
		p.put("maxCandlesBeforeHigh", Blocks.MAX_BACK);
		p.put("zone", "full");
		p.put("maxSpanCandles", Blocks.MAX_SPAN);
		p.put("maxAgeCandles", Blocks.MAX_AGE_CANDLES);
		return p;
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return Blocks.MAX_SPAN + Blocks.MAX_AGE_CANDLES;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		return Blocks.lifecycle(segment, Flips.find(segment, timeframe).stream().filter(Flip::swept).map(Flip::block)
			.toList(), timeframe);
	}
}
