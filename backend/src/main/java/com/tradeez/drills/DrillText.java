package com.tradeez.drills;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.tradeez.analysis.DetectedSetup;
import com.tradeez.analysis.Detector;
import com.tradeez.drills.DrillDtos.RuleView;

/**
 * The words a drill shows: each rule's definition, and why each hit qualified (DEC-026).
 *
 * <p>Generated, never hand-written per hit. A definition is assembled from the rule's own parameters
 * and an explanation from the evidence stored with the hit, so the sentence names the exact prices
 * the rule decided on and cannot drift from it. Each says what the rule saw — not whether the
 * structure matters, and not whether the method it comes from is right (INVARIANT 8).
 *
 * <p>No times: a candle's time is shown beside each explanation, in the exchange's zone, by the
 * client that knows how to format it.
 */
final class DrillText {

	private DrillText() {
	}

	/** The rule in words, from the parameters it runs with. */
	static RuleView rule(Detector detector) {
		Map<String, Object> p = detector.parameters();
		String definition = switch (detector.id()) {
			case "FVG" -> "Bullish: candle 1's high is below candle 3's low. Bearish: candle 1's low is above "
				+ "candle 3's high. The three candles are consecutive, with no market closure between them, "
				+ "and the gap is at least " + p.get("minGapPercentOfAverageRange") + "% of the average "
				+ "high–low range of the " + p.get("averageRangeLookback") + " candles before candle 1.";
			case "SWING" -> "A swing high is a candle whose high is above the high of " + before(p) + " and "
				+ "not below the high of " + after(p) + "; a swing low mirrors it with lows. It is known when "
				+ after(p) + " closes. Candles either side of a contract roll are never compared.";
			case "VI" -> "Two consecutive candles whose bodies — open to close — do not touch, while their "
				+ "wicks overlap. Bullish when the second body is wholly above the first, bearish when "
				+ "below. The space between the bodies is at least " + p.get("minTicks") + " ticks.";
			case "DISPLACEMENT" -> "A fair value gap under " + p.get("gapRule") + " whose middle candle "
				+ "closes the gap's way, taken together with its first and third candles when they close "
				+ "that way too. It is displacement when those candles' bodies add up to at least "
				+ p.get("multiplePercent") + "% of the average high–low range of the " + p.get("rangeLookback")
				+ " candles before the first of them. Known at the gap's third candle's close.";
			case "EQHL" -> "A swing high pairs with the latest earlier swing high at least "
				+ p.get("minApartCandles") + " candles before it whose price is within the tolerance — the "
				+ "larger of one tick and " + p.get("tolerancePercentOfAverageRange") + "% of the average "
				+ "range of the " + p.get("rangeLookback") + " candles before the second swing — with no "
				+ "candle between them trading above the higher of the two. Lows mirror it. Swings are "
				+ "those of swing-1.0.0, at most " + p.get("maxSpanCandles") + " candles apart. Known when "
				+ "the second swing is.";
			case "SWEEP" -> "A swing high is buy-side liquidity, a swing low sell-side, resting from the "
				+ "swing's confirmation until a candle trades a tick beyond it (followed for at most "
				+ p.get("maxAgeCandles") + " candles). The candle that takes it decides: closing back at or "
				+ "inside the level, it is a sweep; closing beyond, with the next " + p.get("holdCandles")
				+ " candles closing beyond too, a run. Anything else is undecided and not reported. When "
				+ "one candle takes several levels on a side, it is judged on the most extreme.";
			case "STRUCTURE" -> "Each swing (swing-1.0.0) is labelled against the previous swing of its "
				+ "kind: a higher high (HH), lower high (LH) or equal high (EH); a higher low (HL), lower "
				+ "low (LL) or equal low (EL). Equal means the same price, to the tick. The previous swing "
				+ "lies within " + p.get("maxSpanCandles") + " candles.";
			case "BOS_CHOCH" -> "Structure is read from the latest swing high and swing low, each labelled "
				+ "against the one before it: bullish after a higher high and a higher low, bearish after a "
				+ "lower high and a lower low, and not read otherwise. The first candle to close beyond the "
				+ "latest swing high or low breaks it: with the trend it is a break of structure (BOS), "
				+ "against it a change of character (CHoCH). Everything read lies within "
				+ p.get("maxSpanCandles") + " candles.";
			default -> throw new IllegalArgumentException("No drill text for rule " + detector.id());
		};
		return new RuleView(detector.id(), detector.ruleVersion(), p, definition, detector.methodologySource());
	}

	private static String before(Map<String, Object> p) {
		return candles(p) + " before it";
	}

	private static String after(Map<String, Object> p) {
		return candles(p) + " after it";
	}

	private static String candles(Map<String, Object> p) {
		int n = ((Number) p.get("strength")).intValue();
		return n == 1 ? "the candle" : "each of the " + n + " candles";
	}

	/** A short name for one hit, as a heading: "Bullish gap", "Higher high (HH)". */
	static String label(DetectedSetup s) {
		boolean bullish = "BULLISH".equals(s.getDirection());
		String way = bullish ? "Bullish" : "Bearish";
		return switch (s.getDetector()) {
			case "FVG" -> way + " gap";
			case "SWING" -> "HIGH".equals(s.getKind()) ? "Swing high" : "Swing low";
			case "VI" -> way + " volume imbalance";
			case "DISPLACEMENT" -> way + " displacement";
			case "EQHL" -> "EQH".equals(s.getKind()) ? "Equal highs" : "Equal lows";
			case "SWEEP" -> ("SWEEP".equals(s.getKind()) ? "Sweep of " : "Run through ")
				+ ("BSL".equals(s.getEvidence().get("side")) ? "buy-side liquidity" : "sell-side liquidity");
			case "STRUCTURE", "BOS_CHOCH" -> DrillKind.NAMES.get(s.getKind());
			default -> s.getDetector();
		};
	}

	/** Why the hit qualified, in the rule's own numbers. */
	static List<String> explain(DetectedSetup s, BigDecimal tickSize) {
		Map<String, Object> e = s.getEvidence();
		Prices price = new Prices(tickSize);
		boolean bullish = "BULLISH".equals(s.getDirection());
		return switch (s.getDetector()) {
			case "FVG" -> fvg(s, e, price, bullish);
			case "SWING" -> {
				boolean high = "HIGH".equals(s.getKind());
				yield List.of((high ? "Its high, " : "Its low, ") + price.of(n(e, "price")) + ", "
					+ (high ? "stands above the high of the candle before it and is not topped by the one after it"
						: "stands below the low of the candle before it and is not undercut by the one after it")
					+ ": a swing " + (high ? "high" : "low") + ", known when that next candle closed.");
			}
			case "VI" -> vi(s, e, price, bullish);
			case "DISPLACEMENT" -> displacement(s, e, price, bullish);
			case "EQHL" -> {
				boolean high = "EQH".equals(s.getKind());
				long a = n(e, "firstPrice");
				long b = n(e, "secondPrice");
				String side = high ? "high" : "low";
				yield List.of(
					"Two swing " + side + "s, at " + price.of(a) + " and " + price.of(b) + ": "
						+ (a == b ? "the same price" : ticks(Math.abs(a - b)) + " apart") + ", within the rule's tolerance, "
						+ "and no candle between them traded " + (high ? "above the higher" : "below the lower") + ".",
					"Relative equal " + side + "s: the stops " + (high ? "above" : "below") + " them are the "
						+ (high ? "buy" : "sell") + "-side liquidity the pattern advertises.");
			}
			case "SWEEP" -> sweep(s, e, price);
			case "STRUCTURE" -> structure(s, e, price);
			case "BOS_CHOCH" -> bosChoch(s, e, price, bullish);
			default -> List.of();
		};
	}

	private static List<String> fvg(DetectedSetup s, Map<String, Object> e, Prices price, boolean bullish) {
		long gap = n(e, "gapTicks");
		// Worded to say exactly what the rule checks and no more: on real data candle 2 need not span
		// the whole gap, so the sentence does not say it was "crossed only by candle 2" (DEC-026).
		String geometry = bullish
			? "Candle 1's high, " + price.of(n(e, "candle1HighTicks")) + ", is below candle 3's low, "
				+ price.of(n(e, "candle3LowTicks")) + ". Neither traded in the " + gap + " ticks ("
				+ price.of(gap) + " points) between them: a bullish gap."
			: "Candle 1's low, " + price.of(n(e, "candle1LowTicks")) + ", is above candle 3's high, "
				+ price.of(n(e, "candle3HighTicks")) + ". Neither traded in the " + gap + " ticks ("
				+ price.of(gap) + " points) between them: a bearish gap.";
		long lookback = n(e, "lookback");
		String average = String.format(Locale.ROOT, "%.1f", (double) n(e, "sumRangeTicks") / lookback);
		String size = "Big enough to count: the rule needs at least " + n(e, "thresholdTicks") + " ticks here — "
			+ s.getParams().get("minGapPercentOfAverageRange") + "% of the " + average + "-tick average range "
			+ "of the " + lookback + " candles before candle 1.";
		return List.of(geometry, size);
	}

	private static List<String> vi(DetectedSetup s, Map<String, Object> e, Prices price, boolean bullish) {
		long size = n(e, "sizeTicks");
		String bodies = bullish
			? "Candle 1's body tops out at " + price.of(s.getZoneLowTicks()) + "; candle 2's body starts at "
				+ price.of(s.getZoneHighTicks()) + ", " + ticks(size) + " higher."
			: "Candle 1's body bottoms out at " + price.of(s.getZoneHighTicks()) + "; candle 2's body tops out "
				+ "at " + price.of(s.getZoneLowTicks()) + ", " + ticks(size) + " lower.";
		String wicks = bullish
			? "The bodies do not touch, but the wicks overlap: candle 2's low, " + price.of(n(e, "candle2LowTicks"))
				+ ", is not above candle 1's high, " + price.of(n(e, "candle1HighTicks")) + "."
			: "The bodies do not touch, but the wicks overlap: candle 2's high, "
				+ price.of(n(e, "candle2HighTicks")) + ", is not below candle 1's low, "
				+ price.of(n(e, "candle1LowTicks")) + ".";
		String minimum = "At least the rule's " + s.getParams().get("minTicks") + " ticks between the bodies: a "
			+ (bullish ? "bullish" : "bearish") + " volume imbalance.";
		return List.of(bodies, wicks, minimum);
	}

	private static List<String> displacement(DetectedSetup s, Map<String, Object> e, Prices price, boolean bullish) {
		long candles = n(e, "candles");
		long body = n(e, "bodyTicks");
		long lookback = ((Number) s.getParams().get("rangeLookback")).longValue();
		long percent = ((Number) s.getParams().get("multiplePercent")).longValue();
		String average = String.format(Locale.ROOT, "%.1f", (double) n(e, "rangeSumTicks") / lookback);
		return List.of(
			(candles == 1 ? "One candle" : candles + " candles in a row") + " closed " + (bullish ? "up" : "down")
				+ ", from " + price.of(bullish ? s.getZoneLowTicks() : s.getZoneHighTicks()) + " to "
				+ price.of(bullish ? s.getZoneHighTicks() : s.getZoneLowTicks()) + ", with bodies adding up to "
				+ ticks(body) + ".",
			"The rule asks for " + percent + "% of the " + average + "-tick average range of the " + lookback
				+ " candles before: " + String.format(Locale.ROOT, "%.1f", n(e, "rangeSumTicks") * percent / 100.0 / lookback)
				+ " ticks. And the move left a " + (bullish ? "bullish" : "bearish") + " fair value gap behind it.");
	}

	private static List<String> sweep(DetectedSetup s, Map<String, Object> e, Prices price) {
		boolean high = "BSL".equals(e.get("side"));
		String level = (high ? "the swing high at " : "the swing low at ") + price.of(n(e, "price"));
		String liquidity = high ? "the buy-side liquidity above it" : "the sell-side liquidity below it";
		String close = price.of(n(e, "takenCloseTicks"));
		if ("SWEEP".equals(s.getKind())) {
			return List.of("This candle traded " + (high ? "above " : "below ") + level + ", taking " + liquidity
				+ ", and closed back at " + close + ", " + (high ? "at or below" : "at or above")
				+ " the level: a sweep.");
		}
		return List.of("This candle traded " + (high ? "above " : "below ") + level + ", taking " + liquidity
			+ ", and closed beyond it, at " + close + ". The next " + s.getParams().get("holdCandles")
			+ " candles closed beyond it too: a run — the level was a waypoint, not the target.");
	}

	private static List<String> structure(DetectedSetup s, Map<String, Object> e, Prices price) {
		String kind = s.getKind();
		boolean high = kind.endsWith("H");
		String side = high ? "high" : "low";
		long now = n(e, "price");
		long before = n(e, "previousPrice");
		String comparison = now == before ? "the same price"
			: ticks(Math.abs(now - before)) + (now > before ? " higher" : " lower");
		return List.of("This swing " + side + " at " + price.of(now) + " came after a swing " + side + " at "
			+ price.of(before) + ": " + comparison + " — " + article(DrillKind.NAMES.get(kind)) + ".");
	}

	private static List<String> bosChoch(DetectedSetup s, Map<String, Object> e, Prices price, boolean bullish) {
		String highLabel = String.valueOf(e.get("highLabel"));
		String lowLabel = String.valueOf(e.get("lowLabel"));
		boolean upTrend = "HH".equals(highLabel) && "HL".equals(lowLabel);
		String structure = "Before the break, the latest swing high was " + article(DrillKind.NAMES.get(highLabel))
			+ " and the latest swing low " + article(DrillKind.NAMES.get(lowLabel)) + ": structure was "
			+ (upTrend ? "bullish" : "bearish") + ".";
		String broke = "This candle closed at " + price.of(n(e, "closeTicks")) + ", " + (bullish ? "above" : "below")
			+ " the swing " + (bullish ? "high" : "low") + " at " + price.of(n(e, "brokenPrice"))
			+ " — the first close beyond it since it formed.";
		String verdict = "BOS".equals(s.getKind())
			? "With the trend: a break of structure (BOS)."
			: "Against the trend: a change of character (CHoCH).";
		return List.of(structure, broke, verdict);
	}

	private static String article(String name) {
		return (name.startsWith("E") ? "an " : "a ") + name.substring(0, 1).toLowerCase(Locale.ROOT) + name.substring(1);
	}

	private static String ticks(long n) {
		return n == 1 ? "1 tick" : n + " ticks";
	}

	private static long n(Map<String, Object> evidence, String key) {
		return ((Number) evidence.get(key)).longValue();
	}

	/** Ticks as a price at the instrument's tick size, the way the chart shows it (INVARIANT 3). */
	private record Prices(BigDecimal tickSize) {

		String of(long ticks) {
			int decimals = Math.max(0, tickSize.stripTrailingZeros().scale());
			return BigDecimal.valueOf(ticks).multiply(tickSize).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
		}
	}
}
