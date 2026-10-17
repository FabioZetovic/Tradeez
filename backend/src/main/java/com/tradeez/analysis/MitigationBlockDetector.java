package com.tradeez.analysis;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.Flips.Flip;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code mitigation-1.0.0} (ICT-32): the flip of a breaker without the sweep ({@link Flips}). Bullish: after
 * a swing low and a swing high, price falls only to a higher low — or the same one — failing to take the
 * sell-side liquidity below the first; then a displacement closes back above the high, and the up-close
 * candles there become a bullish zone. ICT treats it as weaker than a breaker. Followed as every block is
 * ({@link Blocks}).
 */
@Component
public final class MitigationBlockDetector extends RuleDetector {

	public MitigationBlockDetector() {
		super("MITIGATION", "mitigation-1.0.0", "ICT-32", "ICT mitigation block", BreakerDetector.rule(false));
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return Blocks.MAX_SPAN + Blocks.MAX_AGE_CANDLES;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		return Blocks.lifecycle(segment, Flips.find(segment, timeframe).stream().filter(f -> !f.swept())
			.map(Flip::block).toList(), timeframe);
	}
}
