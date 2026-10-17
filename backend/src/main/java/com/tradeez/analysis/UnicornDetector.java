package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.Blocks.Block;
import com.tradeez.analysis.FvgDetector.Hit;
import com.tradeez.analysis.Flips.Flip;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code unicorn-1.0.0} (ICT-36): a {@code breaker-1.0.0} zone overlapped by the {@code fvg-1.0.0} gap its own
 * displacement left — the leg that broke the high is tied to its gap (displacement-1.0.0), so the gap is
 * of the breaker's direction and formed in its displacement. The zone is the overlap, its mean threshold
 * the overlap's middle. Known with the breaker, which is never before its gap. Followed as every block is
 * ({@link Blocks}).
 *
 * <p>Measured on two years of real NQ (T17): three breakers in four are unicorns at this reading, on every
 * timeframe from 1m to 1h — the displacement that breaks the high usually leaves its gap across the
 * breaker's candles. Common, then, not rare: what makes ICT present it as a high-quality entry is not
 * something a rule can measure.
 */
@Component
public final class UnicornDetector extends RuleDetector {

	public UnicornDetector() {
		super("UNICORN", "unicorn-1.0.0", "ICT-36", "ICT unicorn: a breaker and a fair value gap", rule());
	}

	private static Map<String, Object> rule() {
		// The breaker's own parameters are its version's: breaker-1.0.0 names them.
		Map<String, Object> p = new LinkedHashMap<>();
		p.put("breakerRule", "breaker-1.0.0");
		p.put("gapRule", FvgDetector.RULE_VERSION);
		p.put("gap", "the displacement's own");
		p.put("maxAgeCandles", Blocks.MAX_AGE_CANDLES);
		return p;
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return Blocks.MAX_SPAN + Blocks.MAX_AGE_CANDLES;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Block> unicorns = new ArrayList<>();
		for (Flip flip : Flips.find(segment, timeframe)) {
			Block breaker = flip.block();
			Hit gap = flip.leg().gap();
			long low = Math.max(breaker.low(), gap.zoneLowTicks());
			long high = Math.min(breaker.high(), gap.zoneHighTicks());
			if (!flip.swept() || low >= high) {
				continue;
			}
			Map<String, Object> evidence = new LinkedHashMap<>(breaker.evidence());
			evidence.put("breakerLowTicks", breaker.low());
			evidence.put("breakerHighTicks", breaker.high());
			evidence.put("gapLowTicks", gap.zoneLowTicks());
			evidence.put("gapHighTicks", gap.zoneHighTicks());
			evidence.put("gapAnchorTime", gap.anchorTime().getEpochSecond());
			unicorns.add(new Block(breaker.bullish(), breaker.first(), breaker.anchor(), breaker.confirm(), low, high,
				low + high, evidence));
		}
		return Blocks.lifecycle(segment, unicorns, timeframe);
	}
}
