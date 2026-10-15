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
 * {@code displacement-1.0.0} (ICT-04): an energetic move that leaves a {@code fvg-1.0.0} gap behind.
 *
 * <p>The leg is the gap's middle candle — which must close the gap's way — together with its first
 * and third candles when they close the same way too: one to three candles, as the draft asks, and
 * always the ones that made the gap. It is displacement when the leg's bodies add up to at least
 * {@value #MULTIPLE_PERCENT}% of the average high–low range of the 14 candles before it. Known when the
 * gap is: at the third candle's close. The zone runs from the leg's first open to its last close.
 *
 * <p>Tying the leg to its gap settles what the draft left open — which 1–3 candles, and when a gap
 * left later still counts — and gives one displacement per gap at most. Measured on two years of
 * real NQ (T11), two times the average range marks about half of all gaps as displacement on every
 * timeframe (1.5 per 30 five-minute candles); the draft's number is kept.
 */
@Component
public final class DisplacementDetector extends RuleDetector {

	static final int MULTIPLE_PERCENT = 200;
	private static final FvgDetector.Parameters FVG = FvgDetector.Parameters.DEFAULT;
	private static final int LOOKBACK = 14;

	public DisplacementDetector() {
		super("DISPLACEMENT", "displacement-1.0.0", "ICT-04", "ICT displacement", rule());
	}

	private static Map<String, Object> rule() {
		Map<String, Object> p = new LinkedHashMap<>(FVG.asMap());
		p.put("gapRule", FvgDetector.RULE_VERSION);
		p.put("multiplePercent", MULTIPLE_PERCENT);
		p.put("rangeLookback", LOOKBACK);
		p.put("maxCandles", 3);
		return p;
	}

	/** The gap's own candles and lookback: the leg's average is taken within it. */
	@Override
	public int lookbackBars(Timeframe timeframe) {
		return FVG.averageRangeLookback() + 3;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		return legs(segment, timeframe).stream().map(l -> detection(segment, l, timeframe)).toList();
	}

	/**
	 * A displacement leg, by candle index in its segment.
	 *
	 * @param third the gap's third candle, at whose close the leg is known
	 */
	record Leg(Hit gap, int first, int middle, int last, int third, long body, long range) {

		boolean up() {
			return gap.direction() == FvgDetector.Direction.BULLISH;
		}
	}

	/** Every displacement leg in {@code segment}, in time order. */
	static List<Leg> legs(List<Bar> segment, Timeframe timeframe) {
		List<Instant> opens = segment.stream().map(Bar::openTime).toList();
		List<Leg> found = new ArrayList<>();
		for (Hit gap : FvgDetector.detect(segment, timeframe, FVG)) {
			int middle = Collections.binarySearch(opens, gap.candle2().openTime());
			int way = gap.direction() == FvgDetector.Direction.BULLISH ? 1 : -1;
			if (way(segment.get(middle)) != way) {
				continue;
			}
			int first = way(segment.get(middle - 1)) == way ? middle - 1 : middle;
			int last = way(segment.get(middle + 1)) == way ? middle + 1 : middle;
			long body = 0;
			for (int k = first; k <= last; k++) {
				body += Math.abs(segment.get(k).closeTicks() - segment.get(k).openTicks());
			}
			long range = 0;
			for (int k = first - LOOKBACK; k < first; k++) {
				range += segment.get(k).rangeTicks();
			}
			// body >= multiple × average range, in whole ticks: no floating point decides it.
			boolean moved = segment.get(first).openTicks() != segment.get(last).closeTicks();
			if (moved && body * 100 * LOOKBACK >= (long) MULTIPLE_PERCENT * range) {
				found.add(new Leg(gap, first, middle, last, middle + 1, body, range));
			}
		}
		return found;
	}

	private static int way(Bar bar) {
		return Long.signum(bar.closeTicks() - bar.openTicks());
	}

	private static Detection detection(List<Bar> segment, Leg leg, Timeframe timeframe) {
		Hit gap = leg.gap();
		int first = leg.first();
		int last = leg.last();
		long body = leg.body();
		long range = leg.range();
		boolean up = leg.up();
		long open = segment.get(first).openTicks();
		long close = segment.get(last).closeTicks();
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("firstCandleTime", segment.get(first).openTime().getEpochSecond());
		evidence.put("candles", last - first + 1);
		evidence.put("bodyTicks", body);
		evidence.put("rangeSumTicks", range);
		evidence.put("gapAnchorTime", gap.anchorTime().getEpochSecond());
		return new Detection(Detection.Shape.ZONE, up ? Detection.Direction.BULLISH : Detection.Direction.BEARISH, null,
			segment.get(first).openTime(), gap.anchorTime(), segment.get(last).openTime(),
			gap.candle3().openTime().plus(timeframe.duration()), Math.min(open, close), Math.max(open, close), evidence);
	}
}
