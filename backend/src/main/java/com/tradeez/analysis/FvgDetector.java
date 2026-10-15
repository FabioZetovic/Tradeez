package com.tradeez.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * Finds fair value gaps under one fixed, versioned rule.
 *
 * <h2>What this claims, and what it does not (DEC-026, INVARIANT 8)</h2>
 * A hit means "these three candles satisfy rule {@link #RULE_VERSION} with {@link Parameters}".
 * Nothing more. It is not evidence that the gap matters, that price will return to it, or that the
 * methodology it comes from is right. The claim is reproducibility: the same bars and the same
 * parameters always give the same hits, and each hit carries the numbers that produced it.
 *
 * <h2>The rule</h2>
 * <ul>
 *   <li><b>Bullish:</b> candle 1's high is below candle 3's low. The prices between them were
 *       crossed only by candle 2.</li>
 *   <li><b>Bearish:</b> candle 1's low is above candle 3's high.</li>
 *   <li>The three candles are <b>consecutive</b> — no market closure between them. A jump across a
 *       session break is an opening gap, a different concept, and is never reported here.</li>
 *   <li>The gap must be at least {@link Parameters#minGapPercentOfAverageRange()} percent of the
 *       average high–low range of the {@link Parameters#averageRangeLookback()} candles before
 *       candle 1. Relative rather than a fixed number of ticks because a fixed size means different
 *       things on 5m and 15m, and on different instruments: measured in session 016, a 4-tick
 *       minimum flagged every fourth 5-minute candle and almost no 1-minute ones.</li>
 * </ul>
 *
 * <p>The size threshold is a judgement call — how big a gap has to be before it counts is exactly
 * the kind of parameter DEC-026 says shifts results. That is why the parameters are stored with
 * every hit, and why changing them means a new rule version.
 *
 * <p>Integer ticks throughout (INVARIANT 3): the threshold is compared by cross-multiplying, so no
 * floating point decides whether a gap qualifies.
 */
@Component
public final class FvgDetector implements Detector {

	public static final String DETECTOR = "FVG";

	/** Bump on ANY change to what qualifies. Stored on every hit. */
	public static final String RULE_VERSION = "fvg-1.0.0";

	public static final String METHODOLOGY_SOURCE =
		"ICT three-candle fair value gap, as defined by rule " + RULE_VERSION;

	private final Parameters parameters = Parameters.DEFAULT;

	@Override
	public String id() {
		return DETECTOR;
	}

	@Override
	public String ruleVersion() {
		return RULE_VERSION;
	}

	@Override
	public String methodologySource() {
		return METHODOLOGY_SOURCE;
	}

	@Override
	public String conceptId() {
		return "ICT-20";
	}

	@Override
	public Map<String, Object> parameters() {
		return parameters.asMap();
	}

	/** The three candles of the gap and the average-range lookback before candle 1. */
	@Override
	public int lookbackBars(Timeframe timeframe) {
		return parameters.averageRangeLookback() + 3;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		return detect(segment, timeframe, parameters).stream()
			.map(hit -> hit.toDetection(timeframe))
			.toList();
	}

	public enum Direction {
		BULLISH, BEARISH
	}

	/**
	 * @param minGapPercentOfAverageRange a gap must be at least this percent of the average range
	 * @param averageRangeLookback        how many candles before candle 1 the average is taken over
	 */
	public record Parameters(int minGapPercentOfAverageRange, int averageRangeLookback) {

		/** Chosen in session 016 from measured frequencies; see the class comment. */
		public static final Parameters DEFAULT = new Parameters(25, 14);

		public Parameters {
			if (minGapPercentOfAverageRange < 0 || averageRangeLookback < 1) {
				throw new IllegalArgumentException("Invalid FVG parameters");
			}
		}

		public Map<String, Object> asMap() {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("minGapPercentOfAverageRange", minGapPercentOfAverageRange);
			map.put("averageRangeLookback", averageRangeLookback);
			return map;
		}
	}

	/**
	 * One qualifying gap and the evidence for it.
	 *
	 * @param zoneLowTicks  bottom of the gap: candle 1's high (bullish) or candle 3's high (bearish)
	 * @param zoneHighTicks top of the gap: candle 3's low (bullish) or candle 1's low (bearish)
	 * @param sumRangeTicks total high–low range of the lookback candles; with the lookback it gives
	 *                      the average the threshold is measured against, exactly
	 */
	public record Hit(
		Direction direction,
		Bar candle1,
		Bar candle2,
		Bar candle3,
		long zoneLowTicks,
		long zoneHighTicks,
		long gapTicks,
		long sumRangeTicks,
		int lookback,
		long thresholdTicks) {

		/** The candle the learner points at: the one whose move left the gap. */
		public Instant anchorTime() {
			return candle2.openTime();
		}

		/**
		 * As a generic detection: a zone over the three candles, knowable once candle 3 has closed —
		 * its low is what makes or breaks a bullish gap, and its high a bearish one.
		 */
		Detection toDetection(Timeframe timeframe) {
			return new Detection(Detection.Shape.ZONE, Detection.Direction.valueOf(direction.name()), null,
				candle1.openTime(), anchorTime(), candle3.openTime(),
				candle3.openTime().plus(timeframe.duration()), zoneLowTicks, zoneHighTicks, evidence());
		}

		/** Everything an explanation needs, in the form stored alongside the hit. */
		public Map<String, Object> evidence() {
			Map<String, Object> e = new LinkedHashMap<>();
			e.put("candle1Time", candle1.openTime().getEpochSecond());
			e.put("candle1HighTicks", candle1.highTicks());
			e.put("candle1LowTicks", candle1.lowTicks());
			e.put("candle2Time", candle2.openTime().getEpochSecond());
			e.put("candle2OpenTicks", candle2.openTicks());
			e.put("candle2CloseTicks", candle2.closeTicks());
			e.put("candle3Time", candle3.openTime().getEpochSecond());
			e.put("candle3HighTicks", candle3.highTicks());
			e.put("candle3LowTicks", candle3.lowTicks());
			e.put("gapTicks", gapTicks);
			e.put("sumRangeTicks", sumRangeTicks);
			e.put("lookback", lookback);
			e.put("thresholdTicks", thresholdTicks);
			return e;
		}
	}

	/**
	 * Every qualifying gap in {@code bars}.
	 *
	 * <p>A candidate is only evaluated when all its lookback candles are present, so the first
	 * {@code lookback + 1} candles of the input can never be candle 2. Callers that need hits from a
	 * given point should pass that much history before it.
	 *
	 * @param bars      candles in time order, all at {@code timeframe}
	 */
	public static List<Hit> detect(List<Bar> bars, Timeframe timeframe, Parameters parameters) {
		List<Hit> hits = new ArrayList<>();
		int lookback = parameters.averageRangeLookback();
		long step = timeframe.seconds();

		// i is candle 2. Candle 1 is i-1, whose lookback is i-1-lookback .. i-2.
		for (int i = lookback + 1; i + 1 < bars.size(); i++) {
			Bar c1 = bars.get(i - 1);
			Bar c2 = bars.get(i);
			Bar c3 = bars.get(i + 1);

			if (!Segments.consecutive(List.of(c1, c2, c3), step)) {
				continue;
			}

			long bullGap = c3.lowTicks() - c1.highTicks();
			long bearGap = c1.lowTicks() - c3.highTicks();
			if (bullGap <= 0 && bearGap <= 0) {
				continue;
			}

			long sumRange = 0;
			for (int k = i - 1 - lookback; k <= i - 2; k++) {
				sumRange += bars.get(k).rangeTicks();
			}
			long threshold = threshold(sumRange, lookback, parameters.minGapPercentOfAverageRange());

			if (bullGap >= threshold && bullGap > 0) {
				hits.add(new Hit(Direction.BULLISH, c1, c2, c3, c1.highTicks(), c3.lowTicks(),
					bullGap, sumRange, lookback, threshold));
			} else if (bearGap >= threshold && bearGap > 0) {
				hits.add(new Hit(Direction.BEARISH, c1, c2, c3, c3.highTicks(), c1.lowTicks(),
					bearGap, sumRange, lookback, threshold));
			}
		}
		return hits;
	}

	/**
	 * The smallest gap that qualifies, in whole ticks: {@code percent} of the average range, rounded
	 * up, and never less than one tick — a gap of zero is candles touching, not a gap.
	 */
	static long threshold(long sumRangeTicks, int lookback, int percent) {
		long numerator = sumRangeTicks * percent;
		long denominator = 100L * lookback;
		long rounded = Math.floorDiv(numerator + denominator - 1, denominator);
		return Math.max(1, rounded);
	}
}
