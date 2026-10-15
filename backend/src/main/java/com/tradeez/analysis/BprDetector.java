package com.tradeez.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.FvgDetector.Hit;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code bpr-1.0.0} (ICT-24): a balanced price range — price ran one way through an area, leaving a
 * {@code fvg-1.0.0} gap, then straight back, leaving an opposite one, the second formed within
 * {@value #MAX_CANDLES} candles of the first and overlapping it in price. The zone is the overlap.
 * It points the way of the second gap, the move that is still running, and is confirmed with it.
 * When several earlier gaps qualify, the most recent is paired: one balanced range per gap.
 */
@Component
public final class BprDetector extends RuleDetector {

	static final int MAX_CANDLES = 20;

	private static final FvgDetector.Parameters FVG = FvgDetector.Parameters.DEFAULT;

	public BprDetector() {
		super("BPR", "bpr-1.0.0", "ICT-24", "ICT balanced price range", rule());
	}

	private static Map<String, Object> rule() {
		Map<String, Object> p = new LinkedHashMap<>(FVG.asMap());
		p.put("gapRule", FvgDetector.RULE_VERSION);
		p.put("maxCandles", MAX_CANDLES);
		return p;
	}

	/** The second gap, up to {@value #MAX_CANDLES} candles back to the first, and the first's own lookback. */
	@Override
	public int lookbackBars(Timeframe timeframe) {
		return FVG.averageRangeLookback() + 3 + MAX_CANDLES;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Instant> opens = segment.stream().map(Bar::openTime).toList();
		List<Hit> gaps = FvgDetector.detect(segment, timeframe, FVG);
		List<Detection> found = new ArrayList<>();
		for (int second = 0; second < gaps.size(); second++) {
			Hit g2 = gaps.get(second);
			int formed2 = Collections.binarySearch(opens, g2.candle3().openTime());
			for (int first = second - 1; first >= 0; first--) {
				Hit g1 = gaps.get(first);
				int formed1 = Collections.binarySearch(opens, g1.candle3().openTime());
				if (formed2 - formed1 > MAX_CANDLES) {
					break;
				}
				long low = Math.max(g1.zoneLowTicks(), g2.zoneLowTicks());
				long high = Math.min(g1.zoneHighTicks(), g2.zoneHighTicks());
				if (g1.direction() != g2.direction() && low < high) {
					found.add(detection(g1, g2, low, high, timeframe));
					break;
				}
			}
		}
		return found;
	}

	private static Detection detection(Hit g1, Hit g2, long low, long high, Timeframe timeframe) {
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("firstGapAnchorTime", g1.anchorTime().getEpochSecond());
		evidence.put("firstGapLowTicks", g1.zoneLowTicks());
		evidence.put("firstGapHighTicks", g1.zoneHighTicks());
		evidence.put("secondGapAnchorTime", g2.anchorTime().getEpochSecond());
		evidence.put("secondGapLowTicks", g2.zoneLowTicks());
		evidence.put("secondGapHighTicks", g2.zoneHighTicks());
		evidence.put("ceHalfTicks", low + high);
		return new Detection(Detection.Shape.ZONE, Detection.Direction.valueOf(g2.direction().name()), null,
			g1.candle1().openTime(), g2.anchorTime(), g2.candle3().openTime(),
			g2.candle3().openTime().plus(timeframe.duration()), low, high, evidence);
	}
}
