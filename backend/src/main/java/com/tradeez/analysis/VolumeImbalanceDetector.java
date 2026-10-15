package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code vi-1.0.0} (ICT-25): two consecutive candles whose bodies do not touch while their wicks
 * overlap — bullish when the second body is wholly above the first. The zone is the space between
 * the bodies, at least {@value #MIN_TICKS} ticks.
 *
 * <p>Why a fixed minimum, unlike the FVG's relative one: measured on two years of real NQ (session
 * 017), body gaps of any size occur about seven times per 30 candles <i>on every timeframe from one
 * minute to one hour</i> — the space between one trade and the next does not grow with the candle.
 * A threshold relative to the candles' range therefore removed them all above five minutes. One
 * point (four ticks) leaves about 1.6 per 30 candles on every timeframe.
 */
@Component
public final class VolumeImbalanceDetector extends RuleDetector {

	static final int MIN_TICKS = 4;

	public VolumeImbalanceDetector() {
		super("VI", "vi-1.0.0", "ICT-25", "ICT volume imbalance", Map.of("minTicks", MIN_TICKS));
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return 2;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Detection> found = new ArrayList<>();
		for (int i = 1; i < segment.size(); i++) {
			Bar a = segment.get(i - 1);
			Bar b = segment.get(i);
			if (!Segments.consecutive(List.of(a, b), timeframe.seconds())) {
				continue;
			}
			long aTop = Math.max(a.openTicks(), a.closeTicks());
			long aBottom = Math.min(a.openTicks(), a.closeTicks());
			long bTop = Math.max(b.openTicks(), b.closeTicks());
			long bBottom = Math.min(b.openTicks(), b.closeTicks());
			if (bBottom - aTop >= MIN_TICKS && b.lowTicks() <= a.highTicks()) {
				found.add(detection(a, b, aTop, bBottom, Detection.Direction.BULLISH, timeframe));
			} else if (aBottom - bTop >= MIN_TICKS && b.highTicks() >= a.lowTicks()) {
				found.add(detection(a, b, bTop, aBottom, Detection.Direction.BEARISH, timeframe));
			}
		}
		return found;
	}

	private static Detection detection(Bar a, Bar b, long low, long high, Detection.Direction direction,
			Timeframe timeframe) {
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("candle1Time", a.openTime().getEpochSecond());
		evidence.put("candle1OpenTicks", a.openTicks());
		evidence.put("candle1CloseTicks", a.closeTicks());
		evidence.put("candle1HighTicks", a.highTicks());
		evidence.put("candle1LowTicks", a.lowTicks());
		evidence.put("candle2Time", b.openTime().getEpochSecond());
		evidence.put("candle2OpenTicks", b.openTicks());
		evidence.put("candle2CloseTicks", b.closeTicks());
		evidence.put("candle2HighTicks", b.highTicks());
		evidence.put("candle2LowTicks", b.lowTicks());
		evidence.put("sizeTicks", high - low);
		return new Detection(Detection.Shape.ZONE, direction, null, a.openTime(), b.openTime(), b.openTime(),
			b.openTime().plus(timeframe.duration()), low, high, evidence);
	}
}
