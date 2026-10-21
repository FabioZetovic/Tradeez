package com.tradeez.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every rule in words, and why each of its hits qualified (DEC-026, DEC-054, DEC-055).
 *
 * <p>Generated, never hand-written per hit. A definition is assembled from the rule's own parameters
 * and an explanation from the evidence stored with the hit, so a sentence names the exact prices the
 * rule decided on and cannot drift from it. Each says what the rule saw — not whether the structure
 * matters, and not whether the method it comes from is right (INVARIANT 8). Where a sentence reports
 * ICT's claim about a structure, it says it is ICT's.
 *
 * <p>No times: a candle's time is shown beside each explanation, in the exchange's zone, by the
 * client that knows how to format it.
 */
public final class RuleText {

	/** What each named kind of hit is called, as a learner reads it. */
	public static final Map<String, String> NAMES = Map.ofEntries(
		Map.entry("HH", "Higher high (HH)"), Map.entry("LH", "Lower high (LH)"), Map.entry("EH", "Equal high (EH)"),
		Map.entry("HL", "Higher low (HL)"), Map.entry("LL", "Lower low (LL)"), Map.entry("EL", "Equal low (EL)"),
		Map.entry("BOS", "Break of structure (BOS)"), Map.entry("CHOCH", "Change of character (CHoCH)"),
		Map.entry("SWEEP", "Sweep"), Map.entry("RUN", "Run"),
		Map.entry("ITH", "Intermediate-term high (ITH)"), Map.entry("LTH", "Long-term high (LTH)"),
		Map.entry("ITL", "Intermediate-term low (ITL)"), Map.entry("LTL", "Long-term low (LTL)"),
		Map.entry("NWOG", "New week opening gap (NWOG)"), Map.entry("NDOG", "New day opening gap (NDOG)"));

	private RuleText() {
	}

	/** The rule in words, from the parameters it runs with. */
	public static String definition(Detector detector) {
		Map<String, Object> p = detector.parameters();
		String gaps = "gaps of " + p.get("gapRule") + " (at least " + p.get("minGapPercentOfAverageRange")
			+ "% of the average range of the " + p.get("averageRangeLookback") + " candles before)";
		return switch (detector.id()) {
			case "FVG" -> "Bullish: candle 1's high is below candle 3's low. Bearish: candle 1's low is above "
				+ "candle 3's high. The three candles are consecutive, with no market closure between them, "
				+ "and the gap is at least " + p.get("minGapPercentOfAverageRange") + "% of the average "
				+ "high–low range of the " + p.get("averageRangeLookback") + " candles before candle 1.";
			case "FVG_STATE" -> "Each of the " + gaps + " is followed from its third candle's close. A bullish "
				+ "gap is tapped when a candle trades into its top, reaches its CE when a candle trades to its "
				+ "midpoint, is filled when a candle trades to its bottom, and is inverted when a candle "
				+ "closes below it; a bearish gap mirrors it, and one candle can do several at once. A gap "
				+ "not filled " + p.get("maxAgeCandles") + " candles after it formed has expired: the rule "
				+ "stops following it. Each state is known at the close of the candle that reached it.";
			case "IFVG" -> "One of the " + gaps + " that a candle closes straight through — beyond its far "
				+ "edge — within " + p.get("maxAgeCandles") + " candles of forming. Known at that close. "
				+ "ICT's claim is that the failed gap flips sides, so it points the other way: a failed "
				+ "bullish gap is a bearish inversion gap.";
			case "BPR" -> "Price runs one way through an area, leaving one of the " + gaps + ", then straight "
				+ "back, leaving an opposite one within " + p.get("maxCandles") + " candles that overlaps "
				+ "it in price. The balanced range is the overlap; it points the way of the second gap and "
				+ "is known with it. When several earlier gaps qualify, the most recent is paired.";
			case "VI" -> "Two consecutive candles whose bodies — open to close — do not touch, while their "
				+ "wicks overlap. Bullish when the second body is wholly above the first, bearish when "
				+ "below. The space between the bodies is at least " + p.get("minTicks") + " ticks.";
			case "VOID" -> "A gap: two consecutive candles whose ranges do not overlap at all, known at the "
				+ "second's close. A liquidity void: at least " + p.get("minCandles") + " consecutive "
				+ "candles closing the same way, each closing beyond the one before and overlapping it by "
				+ "at most " + p.get("maxOverlapPercent") + "% of its own range, measured over at most the "
				+ "last " + p.get("maxCandles") + " of them; known when the run ends, so the whole run is "
				+ "the void.";
			case "OPENING_GAP" -> "The price difference across a market closure — from the last trade before "
				+ "it to the first after — read from the data: two candles at least " + p.get("closureMinutes")
				+ " minutes apart. A new week opening gap (NWOG) when the closure spans a Saturday in New "
				+ "York, a new day opening gap (NDOG) otherwise. No minimum size. Known at the close of the "
				+ "first candle after the closure.";
			case "ORG" -> "The opening range gap: from the close of the regular session, at " + p.get("closeAt")
				+ " New York, to the next regular open at " + p.get("openAt") + ". No minimum size. Known "
				+ "at the close of the " + p.get("openAt") + " candle.";
			case "SWING" -> "A swing high is a candle whose high is above the high of the candle before it and "
				+ "not below the high of the candle after it — one candle either side, strength "
				+ p.get("strength") + "; a swing low mirrors it with lows. It is known when the candle after "
				+ "it closes. Candles either side of a contract roll are never compared.";
			case "SWING_TIER" -> "Swings (swing-1.0.0, strength " + p.get("strength") + ") ranked. An "
				+ "intermediate-term high is a swing high above the swing highs either side of it; a "
				+ "long-term high is an intermediate-term high above the intermediate-term highs either "
				+ "side of it. Lows mirror them. A rank is known only once the next swing of its rank either "
				+ "side is, and is not given when its candles span more than " + p.get("maxSpanCandles") + ".";
			case "STRUCTURE" -> "Each swing (swing-1.0.0, strength " + p.get("strength") + ") is labelled "
				+ "against the previous swing of its kind: a higher high (HH), lower high (LH) or equal high "
				+ "(EH); a higher low (HL), lower low (LL) or equal low (EL). Equal means the same price, to "
				+ "the tick. The previous swing lies within " + p.get("maxSpanCandles") + " candles.";
			case "BOS_CHOCH" -> "Structure is read from the latest swing high and swing low (strength "
				+ p.get("strength") + "), each labelled against the one before it: bullish after a higher "
				+ "high and a higher low, bearish after a lower high and a lower low, and not read otherwise. "
				+ "The first candle to close beyond the latest swing high or low breaks it: with the trend it "
				+ "is a break of structure (BOS), against it a change of character (CHoCH). Everything read "
				+ "lies within " + p.get("maxSpanCandles") + " candles.";
			case "DISPLACEMENT" -> "One of the " + gaps + " whose middle candle closes the gap's way, taken "
				+ "together with its first and third candles when they close that way too — one to "
				+ p.get("maxCandles") + " candles. It is displacement when those candles' bodies add up to at "
				+ "least " + p.get("multiplePercent") + "% of the average high–low range of the "
				+ p.get("rangeLookback") + " candles before the first of them. Known at the gap's third "
				+ "candle's close.";
			case "MSS" -> "In ICT's order: a sweep, displacement, then a break. Bullish: a swing low (strength "
				+ p.get("strength") + ") trades below an earlier swing low that was still resting; within "
				+ p.get("maxCandles") + " candles of it a candle closes above the latest swing high before "
				+ "it, the first to; and a " + p.get("displacementRule") + " move starts between the low and "
				+ "that close. Known once both the break and the displacement are. Bearish mirrors it. "
				+ "Everything read lies within " + p.get("maxSpanCandles") + " candles.";
			case "LIQUIDITY" -> "Above every swing high (strength " + p.get("strength") + ") rests buy-side "
				+ "liquidity, below every swing low sell-side: resting from the swing's confirmation, taken "
				+ "at the close of the first candle trading a tick beyond it, expired if it still rests "
				+ p.get("maxAgeCandles") + " candles on.";
			case "EQHL" -> "A swing high pairs with the latest earlier swing high at least "
				+ p.get("minApartCandles") + " candles before it whose price is within the tolerance — the "
				+ "larger of one tick and " + p.get("tolerancePercentOfAverageRange") + "% of the average "
				+ "range of the " + p.get("rangeLookback") + " candles before the second swing — with no "
				+ "candle between them trading above the higher of the two. Lows mirror it. Swings are "
				+ "those of swing-1.0.0 (strength " + p.get("strength") + "), at most " + p.get("maxSpanCandles")
				+ " candles apart. Known when the second swing is.";
			case "SWEEP" -> "A swing high (strength " + p.get("strength") + ") is buy-side liquidity, a swing "
				+ "low sell-side, resting from the swing's confirmation until a candle trades a tick beyond "
				+ "it (followed for at most " + p.get("maxAgeCandles") + " candles). The candle that takes it "
				+ "decides: closing back at or inside the level within " + p.get("closeBackWithin") + " candle "
				+ "— itself — it is a sweep; closing beyond, with the next " + p.get("holdCandles") + " candles "
				+ "closing beyond too, a run. Anything else is undecided and not reported. When one candle "
				+ "takes several levels on a side, it is judged on the most extreme.";
			case "TURTLE_SOUP" -> "A sweep of relative equal highs or lows (" + p.get("poolRule") + "): the "
				+ "more extreme of the pair, resting from the pair's confirmation (followed for at most "
				+ p.get("maxAgeCandles") + " candles), is traded through by a candle that closes back at or "
				+ "inside it within " + p.get("closeBackWithin") + " candle — itself. It points away from the "
				+ "pool.";
			case "OB" -> "Bullish: the last down-close candle before a " + p.get("displacementRule") + " move up "
				+ "that breaks structure — a candle of the move is the first to close above the latest swing high "
				+ "(strength " + p.get("strength") + "). Down-close candles in a row are one block, up to "
				+ p.get("maxRunCandles") + "; its last candle is at most " + p.get("maxCandlesBeforeLeg")
				+ " candles before the move, with no market closure between. The zone is the candles' full range, "
				+ "the mean threshold the middle of their bodies. Known when the move is; everything read lies within "
				+ p.get("maxSpanCandles") + " candles. Bearish mirrors it. "
				+ life(p);
			case "BREAKER", "MITIGATION" -> "Bullish: a swing low, then a swing high (strength " + p.get("strength")
				+ "); then price falls "
				+ (Boolean.TRUE.equals(p.get("requiresSweep"))
					? "to a lower low — below the first, taking the sell-side liquidity there"
					: "only to a higher low, or the same one — failing to take the sell-side liquidity below the first")
				+ " — and a " + p.get("displacementRule") + " move up starting from that low brings the first "
				+ "close above the high. The up-close candles at the high, a bearish block that failed, are the "
				+ "zone: the last at most " + p.get("maxCandlesBeforeHigh") + " candles before the high, with the "
				+ "up-close candles before it, up to " + p.get("maxRunCandles") + ". Known at the later of that "
				+ "close and the displacement; everything read lies within " + p.get("maxSpanCandles")
				+ " candles. Bearish mirrors it. " + life(p);
			case "REJECTION" -> "Bearish: a swing high (strength " + p.get("strength") + ") whose upper wick is "
				+ "at least " + p.get("wickPercentOfRange") + "% of its candle's range, and at least "
				+ p.get("wickPercentOfAverageRange") + "% of the average range of the " + p.get("rangeLookback")
				+ " candles before it. The zone runs from the highest body — open or close — of the swing candle "
				+ "and its neighbours up to the high; its mean threshold is the middle of the wick. Known when the "
				+ "swing is. Bullish mirrors it at a swing low. " + life(p);
			case "UNICORN" -> "A breaker (" + p.get("breakerRule") + ") overlapped in price by the "
				+ p.get("gapRule") + " gap its own displacement left: the zone is the overlap, the mean "
				+ "threshold its middle. Known with the breaker. " + life(p);
			case "DEALING_RANGE" -> "Bullish: a " + p.get("displacementRule") + " move up in which a candle is the "
				+ "first to close above the latest swing high (strength " + p.get("strength") + "). The leg starts at "
				+ "the lowest low between that swing and the move, and ends at the first intermediate-term swing high "
				+ "(swing-tier-1.0.0) at or after the breaking candle, where price turned. Known when that turn is, "
				+ "and only while price is still inside; when two moves reach one turn, the earlier is read. The range "
				+ "runs from the leg's start to its end, its middle the equilibrium; everything read lies within "
				+ p.get("maxSpanCandles") + " candles. Bearish mirrors it. " + rangeLife(p);
			case "PD" -> "The range of " + p.get("rangeRule") + " split at its equilibrium, " + p.get("equilibriumPercent")
				+ "% of the way from its low to its high, in half-ticks: above it premium, below it discount. On a "
				+ "range an odd number of ticks tall equilibrium falls between two ticks and belongs to neither half. "
				+ rangeLife(p);
			case "OTE" -> "Of the leg of " + p.get("rangeRule") + ": the zone from " + p.get("nearPercent") + "% to "
				+ p.get("farPercent") + "% of the way back from its end toward its start, rounded outward to whole "
				+ "ticks, its line at " + p.get("linePercent") + "%. After a leg up, a zone below price. Tapped when a "
				+ "candle trades into it. " + rangeLife(p);
			case "STDEV" -> "Of the leg of " + p.get("rangeRule") + ": levels whole leg-lengths beyond its end, in its "
				+ "direction — " + p.get("multiples") + " (the leg's length once, twice, two and a half and four times "
				+ "past the end). A level between two ticks is rounded away from the leg. Known with the leg; "
				+ "everything read lies within " + p.get("maxSpanCandles") + " candles.";
			case "PO3" -> "A day from the midnight open to the 17:00 halt, New York, read after it closes. Bullish "
				+ "power of three: it closed above its open, its low came before its high, and the low lay at least "
				+ p.get("manipulationPercentOfRange") + "% of the day's range below the open — accumulation at the "
				+ "open, manipulation down, distribution up. Bearish mirrors it. On charts of an hour or less.";
			case "DAILY_PROFILE" -> "A day from midnight to the 17:00 halt, New York, read after it closes, by the "
				+ "session its high and its low formed in — London from midnight to 08:30, New York from 08:30 on. "
				+ "London low: London made the low and New York the high, and the day closed above its open. London "
				+ "high: the mirror. New York reversal: New York made both, pointing from the first to the second. "
				+ "Any other day is not named. On charts of 30 minutes or less.";
			case "WEEKLY_PROFILE" -> "A CME week, Sunday 18:00 to Friday 17:00 New York, read after it closes: the "
				+ "trading day that made its extreme against the close — the low of a week that closed up, the high of "
				+ "one that closed down — when that extreme came before the other. A week the other way round is not "
				+ "named. ICT calls a Tuesday low in a bullish week the classic profile.";
			case "JUDAS" -> "In a window — midnight to 05:00 or 09:30 to 10:00, New York — price goes a tick beyond "
				+ "a reference level on one side of the window's opening price, then a candle closes back through "
				+ "that opening price, still within the window. The levels are those still resting at the window's "
				+ "open: the previous trading day's high and low, the Asia killzone's, and for the 09:30 window the "
				+ "London killzone's. Known at that close; the real move points the other way. On charts of 30 "
				+ "minutes or less.";
			default -> throw new IllegalArgumentException("No words for rule " + detector.id());
		};
	}

	/** What happens to a range after it forms, the same for every rule read from its leg (T18). */
	private static String rangeLife(Map<String, Object> p) {
		return "It lasts as long as its range: until a candle closes above the range's high or below its low; "
			+ "one still whole " + p.get("maxAgeCandles") + " candles after it formed has expired.";
	}

	/** What happens to a block after it forms, the same for every block rule (T17). */
	private static String life(Map<String, Object> p) {
		return "From then on it is followed: tapped when a candle trades back into it, its mean threshold lost "
			+ "when a candle closes beyond that — which ICT reads as weakening the block — and invalidated when a "
			+ "candle closes beyond its far edge. One still standing " + p.get("maxAgeCandles") + " candles after "
			+ "it formed has expired. Each state is known at the close of the candle that reached it.";
	}

	/** A short name for one hit, as a heading: "Bullish gap", "Higher high (HH)". */
	public static String label(DetectedSetup s) {
		String way = "BULLISH".equals(s.getDirection()) ? "Bullish" : "Bearish";
		String kind = s.getKind();
		return switch (s.getDetector()) {
			case "FVG" -> way + " gap";
			case "FVG_STATE" -> way + " gap, " + switch (kind) {
				case "OPEN" -> "formed";
				case "CE" -> "at its CE";
				default -> kind.toLowerCase(Locale.ROOT);
			};
			case "IFVG" -> way + " inversion gap";
			case "BPR" -> way + " balanced price range";
			case "VI" -> way + " volume imbalance";
			case "VOID" -> way + ("GAP".equals(kind) ? " price gap" : " liquidity void");
			case "OPENING_GAP", "STRUCTURE", "BOS_CHOCH", "SWING_TIER" -> NAMES.get(kind);
			case "ORG" -> "Opening range gap";
			case "SWING" -> "HIGH".equals(kind) ? "Swing high" : "Swing low";
			case "DISPLACEMENT" -> way + " displacement";
			case "MSS" -> way + " market structure shift";
			case "LIQUIDITY" -> ("BSL".equals(s.getEvidence().get("side")) ? "Buy-side" : "Sell-side")
				+ " liquidity, " + kind.toLowerCase(Locale.ROOT);
			case "EQHL" -> "EQH".equals(kind) ? "Equal highs" : "Equal lows";
			case "SWEEP" -> ("SWEEP".equals(kind) ? "Sweep of " : "Run through ")
				+ ("BSL".equals(s.getEvidence().get("side")) ? "buy-side liquidity" : "sell-side liquidity");
			case "TURTLE_SOUP" -> "Turtle soup at equal " + ("BEARISH".equals(s.getDirection()) ? "highs" : "lows");
			case "OB", "BREAKER", "MITIGATION", "REJECTION", "UNICORN" -> way + " " + BLOCK_NAMES.get(s.getDetector())
				+ switch (kind) {
					case "FORMED" -> "";
					case "MEAN_THRESHOLD" -> ", mean threshold lost";
					default -> ", " + kind.toLowerCase(Locale.ROOT);
				};
			case "DEALING_RANGE", "OTE" -> way + ("OTE".equals(s.getDetector()) ? " OTE" : " dealing range") + rangeState(kind);
			case "PD" -> ("BEARISH".equals(s.getDirection()) ? "Premium" : "Discount") + rangeState(kind);
			case "STDEV" -> "Standard deviation " + DEVIATIONS.get(kind);
			case "PO3" -> way + " power of three";
			case "DAILY_PROFILE" -> switch (kind) {
				case "LONDON_LOW" -> "London low, New York higher";
				case "LONDON_HIGH" -> "London high, New York lower";
				default -> "New York reversal " + ("BULLISH".equals(s.getDirection()) ? "up" : "down");
			};
			case "WEEKLY_PROFILE" -> weekday(kind) + ("BULLISH".equals(s.getDirection()) ? " low" : " high") + " of the week";
			case "JUDAS" -> way + " Judas swing, " + ("LONDON".equals(kind) ? "London" : "New York");
			default -> s.getDetector();
		};
	}

	/** A range's state as a label adds it: nothing when it forms. */
	private static String rangeState(String kind) {
		return switch (kind) {
			case "FORMED" -> "";
			case "BROKEN_HIGH" -> ", high broken";
			case "BROKEN_LOW" -> ", low broken";
			default -> ", " + kind.toLowerCase(Locale.ROOT);
		};
	}

	/** stdev-1.0.0's kinds, which can hold no digits, as the multiples they are. */
	private static final Map<String, String> DEVIATIONS = Map.of("ONE", "−1", "TWO", "−2", "TWO_AND_A_HALF", "−2.5",
		"FOUR", "−4");

	/** Why the hit qualified, in the rule's own numbers. */
	public static List<String> explain(DetectedSetup s, BigDecimal tickSize) {
		Map<String, Object> e = s.getEvidence();
		Prices price = new Prices(tickSize);
		boolean bullish = "BULLISH".equals(s.getDirection());
		return switch (s.getDetector()) {
			case "FVG" -> fvg(s, e, price, bullish);
			case "FVG_STATE" -> fvgState(s, e, price);
			case "IFVG" -> ifvg(s, e, price);
			case "BPR" -> bpr(s, e, price, bullish);
			case "VI" -> vi(s, e, price, bullish);
			case "VOID" -> voids(s, e, price, bullish);
			case "OPENING_GAP" -> openingGap(s, e, price);
			case "ORG" -> org(e, price);
			case "SWING" -> {
				boolean high = "HIGH".equals(s.getKind());
				yield List.of((high ? "Its high, " : "Its low, ") + price.of(n(e, "price")) + ", "
					+ (high ? "stands above the high of the candle before it and is not topped by the one after it"
						: "stands below the low of the candle before it and is not undercut by the one after it")
					+ ": a swing " + (high ? "high" : "low") + ", known when that next candle closed.");
			}
			case "SWING_TIER" -> swingTier(s, e, price);
			case "STRUCTURE" -> structure(s, e, price);
			case "BOS_CHOCH" -> bosChoch(s, e, price, bullish);
			case "DISPLACEMENT" -> displacement(s, e, price, bullish);
			case "MSS" -> mss(e, price, bullish);
			case "LIQUIDITY" -> liquidity(s, e, price);
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
			case "OB", "BREAKER", "MITIGATION", "REJECTION", "UNICORN" -> block(s, e, price, bullish);
			case "DEALING_RANGE", "PD", "OTE" -> range(s, e, price);
			case "PO3" -> List.of(
				"The day opened at " + price.of(n(e, "openTicks")) + " at midnight and closed at " + price.of(n(e, "closeTicks"))
					+ " at 17:00 — " + (bullish ? "up" : "down") + ".",
				"Its " + (bullish ? "low, " + price.of(s.getZoneLowTicks()) + ", came before its high, " + price.of(s.getZoneHighTicks())
					: "high, " + price.of(s.getZoneHighTicks()) + ", came before its low, " + price.of(s.getZoneLowTicks()))
					+ ", and lay " + ticks(n(e, "manipulationTicks")) + " " + (bullish ? "below" : "above") + " the open: "
					+ (100 * n(e, "manipulationTicks") / n(e, "rangeTicks")) + "% of the day's " + n(e, "rangeTicks")
					+ "-tick range. Accumulation, manipulation " + (bullish ? "down" : "up") + ", distribution "
					+ (bullish ? "up" : "down") + ": power of three, in ICT's words.");
			case "DAILY_PROFILE" -> {
				String high = price.of(s.getZoneHighTicks());
				String low = price.of(s.getZoneLowTicks());
				yield List.of(switch (s.getKind()) {
					case "LONDON_LOW" -> "London made the day's low at " + low + "; New York made its high at " + high
						+ ", and the day closed at " + price.of(n(e, "closeTicks")) + ", above the midnight open at "
						+ price.of(n(e, "openTicks")) + ".";
					case "LONDON_HIGH" -> "London made the day's high at " + high + "; New York made its low at " + low
						+ ", and the day closed at " + price.of(n(e, "closeTicks")) + ", below the midnight open at "
						+ price.of(n(e, "openTicks")) + ".";
					default -> "New York made both the day's " + (bullish ? "low, at " + low + ", and then its high, at " + high
						: "high, at " + high + ", and then its low, at " + low) + ": a New York reversal.";
				});
			}
			case "WEEKLY_PROFILE" -> List.of("The week opened at " + price.of(n(e, "openTicks")) + " and closed at "
				+ price.of(n(e, "closeTicks")) + " — " + (bullish ? "up" : "down") + ". Its " + (bullish ? "low, "
					+ price.of(s.getZoneLowTicks()) : "high, " + price.of(s.getZoneHighTicks())) + ", formed on " + weekday(s.getKind())
				+ ", before its " + (bullish ? "high, " + price.of(s.getZoneHighTicks()) : "low, " + price.of(s.getZoneLowTicks()))
				+ ": a " + weekday(s.getKind()) + (bullish ? " low" : " high") + " of the week"
				+ ("TUESDAY".equals(s.getKind()) && bullish ? " — ICT's classic profile." : "."));
			case "JUDAS" -> List.of(
				"The " + ("LONDON".equals(s.getKind()) ? "midnight" : "09:30") + " window opened at " + price.of(n(e, "openTicks"))
					+ ". Price went " + (bullish ? "down" : "up") + " to " + price.of(n(e, "extremeTicks")) + ", beyond "
					+ LEVEL_NAMES.get(String.valueOf(e.get("level"))) + " at " + price.of(n(e, "levelPrice")) + ", which was still resting.",
				"Then a candle closed at " + price.of(n(e, "closeTicks")) + ", back " + (bullish ? "above" : "below")
					+ " the opening price, within the window: the move " + (bullish ? "down" : "up")
					+ " was a Judas swing, in ICT's word, and the real move points " + (bullish ? "up." : "down."));
			case "STDEV" -> {
				boolean up = n(e, "endPrice") > n(e, "startPrice");
				yield List.of(leg(e, price, up) + " " + DEVIATIONS.get(s.getKind()).substring(1) + " leg-lengths "
					+ (up ? "above" : "below") + " its end is " + price.of(s.getZoneLowTicks()) + ": the "
					+ DEVIATIONS.get(s.getKind()) + " standard deviation projection, "
					+ ticks(Math.abs(s.getZoneLowTicks() - n(e, "endPrice"))) + " beyond the end.");
			}
			case "TURTLE_SOUP" -> {
				boolean highs = "BEARISH".equals(s.getDirection());
				yield List.of("This candle traded " + (highs ? "above" : "below") + " the equal " + (highs ? "highs" : "lows")
					+ " at " + price.of(n(e, "price")) + " — the " + (highs ? "higher" : "lower") + " of the pair — and "
					+ "closed back at " + price.of(n(e, "closeTicks")) + ", " + (highs ? "at or below" : "at or above")
					+ " them: the pool was swept. Turtle soup.");
			}
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

	/** One state of a gap's life, read from the later candle that moved it there. */
	private static List<String> fvgState(DetectedSetup s, Map<String, Object> e, Prices price) {
		boolean bullish = "BULLISH".equals(e.get("gapDirection"));
		String zone = "The " + (bullish ? "bullish" : "bearish") + " gap from " + price.of(s.getZoneLowTicks()) + " to "
			+ price.of(s.getZoneHighTicks());
		String toward = bullish ? "down to " + price.of(n(e, "candleLowTicks")) : "up to " + price.of(n(e, "candleHighTicks"));
		String near = price.of(bullish ? s.getZoneHighTicks() : s.getZoneLowTicks());
		String far = price.of(bullish ? s.getZoneLowTicks() : s.getZoneHighTicks());
		return List.of(switch (s.getKind()) {
			case "OPEN" -> zone + " formed at its third candle's close: open, nothing has traded in it yet.";
			case "TAPPED" -> zone + ": a later candle traded " + toward + ", into its near edge at " + near + " — tapped.";
			case "CE" -> zone + ": a later candle traded " + toward + ", reaching its midpoint at "
				+ price.ofHalf(n(e, "ceHalfTicks")) + " — consequent encroachment.";
			case "FILLED" -> zone + ": a later candle traded " + toward + ", to its far edge at " + far + " — filled.";
			case "INVERTED" -> zone + ": a later candle closed at " + price.of(n(e, "candleCloseTicks")) + ", beyond its far "
				+ "edge at " + far + " — inverted.";
			case "EXPIRED" -> zone + " was still not filled " + s.getParams().get("maxAgeCandles") + " candles after it "
				+ "formed: the rule stops following it.";
			default -> zone + ".";
		});
	}

	private static List<String> ifvg(DetectedSetup s, Map<String, Object> e, Prices price) {
		boolean gapBullish = "BULLISH".equals(e.get("gapDirection"));
		return List.of(
			"A " + (gapBullish ? "bullish" : "bearish") + " gap from " + price.of(s.getZoneLowTicks()) + " to "
				+ price.of(s.getZoneHighTicks()) + " formed, and a later candle closed straight through it, at "
				+ price.of(n(e, "candleCloseTicks")) + ", " + (gapBullish ? "below its bottom" : "above its top") + ".",
			"ICT's claim is that a failed gap flips sides: this one is read as a " + (gapBullish ? "bearish" : "bullish")
				+ " inversion gap.");
	}

	private static List<String> bpr(DetectedSetup s, Map<String, Object> e, Prices price, boolean bullish) {
		return List.of(
			"First a " + (bullish ? "bearish" : "bullish") + " gap from " + price.of(n(e, "firstGapLowTicks")) + " to "
				+ price.of(n(e, "firstGapHighTicks")) + ", then, within " + s.getParams().get("maxCandles")
				+ " candles, a " + (bullish ? "bullish" : "bearish") + " one from " + price.of(n(e, "secondGapLowTicks"))
				+ " to " + price.of(n(e, "secondGapHighTicks")) + ": price ran through the area and straight back.",
			"They overlap from " + price.of(s.getZoneLowTicks()) + " to " + price.of(s.getZoneHighTicks())
				+ ": a balanced price range, pointing the way of the second move.");
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

	private static List<String> voids(DetectedSetup s, Map<String, Object> e, Prices price, boolean bullish) {
		if ("GAP".equals(s.getKind())) {
			return List.of(bullish
				? "Candle 2's low, " + price.of(n(e, "candle2LowTicks")) + ", is above candle 1's high, "
					+ price.of(n(e, "candle1HighTicks")) + ": the two ranges do not overlap — a price gap of "
					+ ticks(s.getZoneHighTicks() - s.getZoneLowTicks()) + "."
				: "Candle 2's high, " + price.of(n(e, "candle2HighTicks")) + ", is below candle 1's low, "
					+ price.of(n(e, "candle1LowTicks")) + ": the two ranges do not overlap — a price gap of "
					+ ticks(s.getZoneHighTicks() - s.getZoneLowTicks()) + ".");
		}
		return List.of(n(e, "candles") + " candles in a row closed " + (bullish ? "up" : "down") + ", from "
			+ price.of(n(e, "openTicks")) + " to " + price.of(n(e, "closeTicks")) + ", each closing beyond the one before "
			+ "and overlapping it by at most " + s.getParams().get("maxOverlapPercent") + "% of its range: a "
			+ "one-sided run, a liquidity void.");
	}

	private static List<String> openingGap(DetectedSetup s, Map<String, Object> e, Prices price) {
		long close = n(e, "lastCloseTicks");
		long open = n(e, "firstOpenTicks");
		long minutes = n(e, "closedMinutes");
		String shut = minutes < 120 ? minutes + " minutes" : Math.round(minutes / 60.0) + " hours";
		return List.of("The market closed at " + price.of(close) + " and reopened " + shut + " later at "
			+ price.of(open) + ": " + moved(open - close) + ". "
			+ ("NWOG".equals(s.getKind()) ? "Across a weekend: a new week opening gap (NWOG)."
				: "Across the daily halt or a holiday: a new day opening gap (NDOG)."));
	}

	private static List<String> org(Map<String, Object> e, Prices price) {
		long close = n(e, "closeTicks");
		long open = n(e, "openTicks");
		return List.of("The regular session closed at " + price.of(close) + " (16:15 New York) and the next opened "
			+ "at " + price.of(open) + " (09:30): " + moved(open - close) + ", its midpoint at "
			+ price.ofHalf(n(e, "ceHalfTicks")) + ".");
	}

	private static List<String> swingTier(DetectedSetup s, Map<String, Object> e, Prices price) {
		String kind = s.getKind();
		boolean high = kind.endsWith("H");
		boolean longTerm = kind.startsWith("L");
		String side = high ? "high" : "low";
		String lower = longTerm ? "intermediate-term " + side : "swing " + side;
		return List.of("This " + lower + " at " + price.of(n(e, "price")) + " stands " + (high ? "above" : "below")
			+ " the " + lower + "s either side of it: " + article(NAMES.get(kind)) + ".");
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
			+ price.of(before) + ": " + comparison + " — " + article(NAMES.get(kind)) + ".");
	}

	private static List<String> bosChoch(DetectedSetup s, Map<String, Object> e, Prices price, boolean bullish) {
		String highLabel = String.valueOf(e.get("highLabel"));
		String lowLabel = String.valueOf(e.get("lowLabel"));
		boolean upTrend = "HH".equals(highLabel) && "HL".equals(lowLabel);
		String structure = "Before the break, the latest swing high was " + article(NAMES.get(highLabel))
			+ " and the latest swing low " + article(NAMES.get(lowLabel)) + ": structure was "
			+ (upTrend ? "bullish" : "bearish") + ".";
		String broke = "This candle closed at " + price.of(n(e, "closeTicks")) + ", " + (bullish ? "above" : "below")
			+ " the swing " + (bullish ? "high" : "low") + " at " + price.of(n(e, "brokenPrice"))
			+ " — the first close beyond it since it formed.";
		String verdict = "BOS".equals(s.getKind())
			? "With the trend: a break of structure (BOS)."
			: "Against the trend: a change of character (CHoCH).";
		return List.of(structure, broke, verdict);
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

	private static List<String> mss(Map<String, Object> e, Prices price, boolean bullish) {
		String low = bullish ? "low" : "high";
		String high = bullish ? "high" : "low";
		return List.of(
			"A swing " + low + " at " + price.of(n(e, "pivotPrice")) + " traded " + (bullish ? "below" : "above")
				+ " the earlier swing " + low + " at " + price.of(n(e, "sweptPrice")) + ", which was still resting: "
				+ "a sweep.",
			"Then a displacement, and this candle closed at " + price.of(n(e, "closeTicks")) + ", "
				+ (bullish ? "above" : "below") + " the swing " + high + " at " + price.of(n(e, "brokenPrice"))
				+ " — the first close beyond it: a " + (bullish ? "bullish" : "bearish") + " market structure shift.");
	}

	private static List<String> liquidity(DetectedSetup s, Map<String, Object> e, Prices price) {
		boolean high = "BSL".equals(e.get("side"));
		String level = "the swing " + (high ? "high" : "low") + " at " + price.of(n(e, "price"));
		String liquidity = high ? "buy-side liquidity — the stops above it" : "sell-side liquidity — the stops below it";
		return List.of(switch (s.getKind()) {
			case "RESTING" -> capital(level) + ": " + liquidity + ", resting from the candle that confirmed the swing.";
			case "TAKEN" -> "A later candle traded " + (high ? "above " : "below ") + level + ", a tick or more beyond it: "
				+ "the " + (high ? "buy" : "sell") + "-side liquidity there is taken.";
			case "EXPIRED" -> capital(level) + " still rested " + s.getParams().get("maxAgeCandles") + " candles after "
				+ "it formed: the rule stops following it.";
			default -> capital(level) + ".";
		});
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

	/** A block's forming in its rule's own numbers; a later state, by the candle that moved it there. */
	private static List<String> block(DetectedSetup s, Map<String, Object> e, Prices price, boolean bullish) {
		String name = BLOCK_NAMES.get(s.getDetector());
		String zone = "The " + (bullish ? "bullish " : "bearish ") + name + " from " + price.of(s.getZoneLowTicks())
			+ " to " + price.of(s.getZoneHighTicks());
		String mean = price.ofHalf(n(e, "meanHalfTicks"));
		String toward = bullish ? "down to " + price.of(n(e, "candleLowTicks")) : "up to " + price.of(n(e, "candleHighTicks"));
		String close = price.of(n(e, "candleCloseTicks"));
		return switch (s.getKind()) {
			case "FORMED" -> formed(s, e, price, bullish, mean);
			case "TAPPED" -> List.of(zone + ": a later candle traded " + toward + ", back into it — tapped.");
			case "MEAN_THRESHOLD" -> List.of(zone + ": a later candle closed at " + close + ", " + (bullish ? "below" : "above")
				+ " its mean threshold at " + mean + ". ICT reads that as weakening the block.");
			case "INVALIDATED" -> List.of(zone + ": a later candle closed at " + close + ", beyond its far edge at "
				+ price.of(bullish ? s.getZoneLowTicks() : s.getZoneHighTicks()) + " — invalidated.");
			case "EXPIRED" -> List.of(zone + " was still standing " + s.getParams().get("maxAgeCandles") + " candles after "
				+ "it formed: the rule stops following it.");
			default -> List.of(zone + ".");
		};
	}

	private static List<String> formed(DetectedSetup s, Map<String, Object> e, Prices price, boolean bullish, String mean) {
		String low = price.of(s.getZoneLowTicks());
		String high = price.of(s.getZoneHighTicks());
		String up = bullish ? "up" : "down";
		return switch (s.getDetector()) {
			case "OB" -> {
				long candles = n(e, "runCandles");
				yield List.of(
					(candles == 1 ? "The last " + (bullish ? "down" : "up") + "-close candle"
						: "The last " + candles + " candles closing " + (bullish ? "down" : "up") + " in a row")
						+ " before the move, from " + low + " to " + high + ": the block. Its mean threshold, the middle "
						+ "of its bodies, is at " + mean + ".",
					"Then a displacement " + up + ", and in it a candle closed at " + price.of(n(e, "breakCloseTicks")) + ", "
						+ (bullish ? "above" : "below") + " the swing " + (bullish ? "high" : "low") + " at "
						+ price.of(n(e, "brokenPrice")) + " — the first close beyond it: structure broke. ICT's claim is that "
						+ "the block is where the move's orders were placed.");
			}
			case "BREAKER", "MITIGATION" -> {
				boolean breaker = "BREAKER".equals(s.getDetector());
				String first = bullish ? "low" : "high";
				String second = bullish ? "high" : "low";
				yield List.of(
					"A swing " + first + " at " + price.of(n(e, "originPrice")) + ", then a swing " + second + " at "
						+ price.of(n(e, "levelPrice")) + "; then price went to " + price.of(n(e, "dropPrice")) + " — "
						+ (breaker ? (bullish ? "below" : "above") + " the first " + first + ", taking the "
							+ (bullish ? "sell" : "buy") + "-side liquidity there."
							: "not beyond the first " + first + ": its liquidity was not taken."),
					"A displacement " + up + " followed, and a candle closed at " + price.of(n(e, "breakCloseTicks")) + ", "
						+ (bullish ? "above" : "below") + " the " + second + " — the first close beyond it.",
					"The " + (bullish ? "up" : "down") + "-close candles at the " + second + ", from " + low + " to " + high
						+ ", failed as a " + (bullish ? "bearish" : "bullish") + " block: ICT reads them as a "
						+ (bullish ? "bullish " : "bearish ") + (breaker ? "breaker" : "mitigation block, weaker than a breaker")
						+ " now. Mean threshold at " + mean + ".");
			}
			case "UNICORN" -> List.of(
				"A " + (bullish ? "bullish" : "bearish") + " breaker from " + price.of(n(e, "breakerLowTicks")) + " to "
					+ price.of(n(e, "breakerHighTicks")) + ": the " + (bullish ? "high" : "low") + " at "
					+ price.of(n(e, "levelPrice")) + " failed after price took the liquidity at "
					+ price.of(n(e, "originPrice")) + ".",
				"The displacement that broke it left a " + (bullish ? "bullish" : "bearish") + " gap from "
					+ price.of(n(e, "gapLowTicks")) + " to " + price.of(n(e, "gapHighTicks")) + ". They overlap from " + low
					+ " to " + high + ": a unicorn, in ICT's word. Mean threshold at " + mean + ".");
			case "REJECTION" -> {
				long wick = n(e, "wickTicks");
				long range = n(e, "rangeTicks");
				long lookback = ((Number) s.getParams().get("rangeLookback")).longValue();
				String average = String.format(Locale.ROOT, "%.1f", (double) n(e, "rangeSumTicks") / lookback);
				yield List.of(
					"This swing " + (bullish ? "low" : "high") + " at " + price.of(n(e, "swingPrice")) + " left "
						+ (bullish ? "a lower" : "an upper") + " wick of " + ticks(wick) + ": " + (100 * wick / range)
						+ "% of the candle's " + range + "-tick range, and longer than the " + average + "-tick average "
						+ "candle of the " + lookback + " before it.",
					"The zone runs from the " + (bullish ? "lowest" : "highest") + " body nearby, " + price.of(n(e, "bodyTicks"))
						+ ", to the " + (bullish ? "low" : "high") + ": a " + (bullish ? "bullish" : "bearish")
						+ " rejection block. ICT's claim is that price returning here reacts at the body. The wick's "
						+ "middle is at " + mean + ".");
			}
			default -> List.of();
		};
	}

	/** The leg a range rule reads, in a sentence: where it began, what it broke, where it turned (T18). */
	private static String leg(Map<String, Object> e, Prices price, boolean up) {
		return "A displacement " + (up ? "up" : "down") + " closed " + (up ? "above" : "below") + " the swing "
			+ (up ? "high" : "low") + " at " + price.of(n(e, "brokenPrice")) + "; the leg ran from " + price.of(n(e, "startPrice"))
			+ " to " + price.of(n(e, "endPrice")) + ", where it turned — " + ticks(Math.abs(n(e, "endPrice") - n(e, "startPrice")))
			+ ".";
	}

	/** A dealing range, a half of one, or an OTE: forming in its numbers, a later state by the candle that did it. */
	private static List<String> range(DetectedSetup s, Map<String, Object> e, Prices price) {
		boolean up = n(e, "endPrice") > n(e, "startPrice");
		long low = Math.min(n(e, "startPrice"), n(e, "endPrice"));
		long high = Math.max(n(e, "startPrice"), n(e, "endPrice"));
		String eq = price.ofHalf(low + high);
		String what = switch (s.getDetector()) {
			case "PD" -> "BEARISH".equals(s.getDirection()) ? "Premium" : "Discount";
			case "OTE" -> "The OTE";
			default -> "The dealing range";
		} + " from " + price.of(s.getZoneLowTicks()) + " to " + price.of(s.getZoneHighTicks());
		String close = price.of(n(e, "candleCloseTicks"));
		return switch (s.getKind()) {
			case "FORMED" -> switch (s.getDetector()) {
				case "PD" -> List.of(leg(e, price, up), "BEARISH".equals(s.getDirection())
					? "Above its equilibrium at " + eq + ", from " + price.of(s.getZoneLowTicks()) + " to " + price.of(high)
						+ ", is premium — where ICT looks to sell."
					: "Below its equilibrium at " + eq + ", from " + price.of(low) + " to " + price.of(s.getZoneHighTicks())
						+ ", is discount — where ICT looks to buy.");
				case "OTE" -> List.of(leg(e, price, up), "62% to 79% of the way back, rounded outward, is "
					+ price.of(s.getZoneLowTicks()) + " to " + price.of(s.getZoneHighTicks()) + ": the optimal trade entry, its "
					+ "line at 70.5% — " + price.ofHalf(n(e, "midHalfTicks")) + ".");
				default -> List.of(leg(e, price, up), "The dealing range runs from " + price.of(low) + " to " + price.of(high)
					+ ", its equilibrium at " + eq + ".");
			};
			case "TAPPED" -> List.of(what + ": a later candle traded " + (up ? "down to " + price.of(n(e, "candleLowTicks"))
				: "up to " + price.of(n(e, "candleHighTicks"))) + ", into it — tapped.");
			case "BROKEN_HIGH" -> List.of(what + ": a later candle closed at " + close + ", above the range's high at "
				+ price.of(high) + " — the range is left.");
			case "BROKEN_LOW" -> List.of(what + ": a later candle closed at " + close + ", below the range's low at "
				+ price.of(low) + " — the range is left.");
			case "EXPIRED" -> List.of(what + ": the range was still whole " + s.getParams().get("maxAgeCandles")
				+ " candles after it formed — the rule stops following it.");
			default -> List.of(what + ".");
		};
	}

	/** A weekday as a word: "Tuesday". */
	private static String weekday(String kind) {
		return kind.charAt(0) + kind.substring(1).toLowerCase(Locale.ROOT);
	}

	/** judas-1.0.0's reference levels, as a sentence names them. */
	private static final Map<String, String> LEVEL_NAMES = Map.of("PREVIOUS_DAY_HIGH", "the previous day's high",
		"PREVIOUS_DAY_LOW", "the previous day's low", "ASIA_HIGH", "the Asia high", "ASIA_LOW", "the Asia low",
		"LONDON_HIGH", "the London high", "LONDON_LOW", "the London low");

	/** What each block rule calls its block. */
	private static final Map<String, String> BLOCK_NAMES = Map.of("OB", "order block", "BREAKER", "breaker",
		"MITIGATION", "mitigation block", "REJECTION", "rejection block", "UNICORN", "unicorn");

	private static String moved(long ticks) {
		return ticks == 0 ? "no gap — it opened where it closed"
			: "a gap of " + ticks(Math.abs(ticks)) + (ticks > 0 ? " up" : " down");
	}

	private static String article(String name) {
		return (name.startsWith("E") || name.startsWith("I") ? "an " : "a ") + name.substring(0, 1).toLowerCase(Locale.ROOT)
			+ name.substring(1);
	}

	private static String capital(String text) {
		return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
	}

	private static String ticks(long n) {
		return n == 1 ? "1 tick" : n + " ticks";
	}

	private static long n(Map<String, Object> evidence, String key) {
		return ((Number) evidence.get(key)).longValue();
	}

	/** Ticks as a price at the instrument's tick size, the way the chart shows it (INVARIANT 3). */
	/** A price in ticks, in the instrument's own decimals: what every sentence here prints. */
	public static String price(long ticks, BigDecimal tickSize) {
		return new Prices(tickSize).of(ticks);
	}

	/** A midpoint in half-ticks as a price: the tick's decimals, and one more only when it lies between two ticks. */
	public static String halfPrice(long halfTicks, BigDecimal tickSize) {
		return new Prices(tickSize).ofHalf(halfTicks);
	}

	private record Prices(BigDecimal tickSize) {

		String of(long ticks) {
			return format(BigDecimal.valueOf(ticks).multiply(tickSize));
		}

		/** A midpoint, stored in half-ticks so it stays an integer (ICT-21). */
		String ofHalf(long halfTicks) {
			return format(BigDecimal.valueOf(halfTicks).multiply(tickSize).divide(BigDecimal.TWO));
		}

		private String format(BigDecimal price) {
			int decimals = Math.max(0, tickSize.stripTrailingZeros().scale());
			return price.setScale(Math.max(decimals, price.stripTrailingZeros().scale()), RoundingMode.HALF_UP)
				.toPlainString();
		}
	}
}
