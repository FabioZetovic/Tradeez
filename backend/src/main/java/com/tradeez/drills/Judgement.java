package com.tradeez.drills;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.function.LongFunction;

import com.tradeez.analysis.RuleText;
import com.tradeez.drills.Prediction.Context;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;
import com.tradeez.practice.FillEngine.Direction;
import com.tradeez.practice.ModelChecklist;
import com.tradeez.practice.TradingModel;

/**
 * Mode J (T45, DEC-082): a question about the chart as it stands, answered by a rule the moment it is asked — premium
 * or discount, inside the OTE zone or not, which power of three, where the stop goes, whether a model's setup is
 * there. Lines are drawn for it (the range, the leg, the trade); the learner picks one of two or three answers; the
 * answer and the reasons, in prices, come with the result. Nothing after the chart is shown or needed.
 *
 * <p>Each question is a pure function of the candles and the rule's stored hits, so it is tested without a market.
 */
public final class Judgement {

	private Judgement() {
	}

	/** One of the answers offered. */
	public record Option(String value, String label) {
	}

	/**
	 * What a judgement asks and how it was decided, stored with the drill.
	 *
	 * @param closeTicks  the last close, which most questions are about
	 * @param context     the lines drawn for it
	 * @param answer      the option's value the rule gives
	 * @param explanation why, in prices: shown only with the result
	 */
	public record Asked(String version, String definition, long closeTicks, List<Option> options, List<Context> context,
			String answer, List<String> explanation) {

		List<String> values() {
			return options.stream().map(Option::value).toList();
		}
	}

	/** A stored hit as much as a judgement reads of it. */
	record Hit(String kind, String direction, Instant start, Instant anchor, Instant confirmedAt, long low, long high,
			Map<String, Object> evidence) {
	}

	// --- premium or discount (ICT-41) ----------------------------------------------------------------

	static final String PD_VERSION = "judge-pd-1.0.0";

	static String pdDefinition() {
		return PD_VERSION + ". A dealing range of dealing-range-1.0.0, still standing when the chart ends, its high and low "
			+ "drawn. Its equilibrium is halfway between them: a close above it is in premium, below it in discount. A "
			+ "close exactly at equilibrium, or outside the range, is never asked.";
	}

	/** States that end a range: after one, it is no longer a range to be in. */
	private static final Set<String> RANGE_ENDS = Set.of("BROKEN_HIGH", "BROKEN_LOW", "EXPIRED");

	/**
	 * Premium or discount: the latest dealing range formed on the chart — its leg wholly on screen — and standing
	 * when the chart ends (never broken or expired by then), and where the last close sits in it. The PD rule stores
	 * each range as its two halves; together they are the range. Null when there is none, or the close is at
	 * equilibrium or outside the range.
	 *
	 * @param halves every state of the PD rule's halves known by the chart's end
	 */
	static Asked premiumDiscount(List<Bar> bars, List<Hit> halves, Instant firstCandle, BigDecimal tickSize) {
		Instant end = bars.getLast().openTime().plusSeconds(1);
		Optional<Hit> formed = halves.stream()
			.filter(h -> "FORMED".equals(h.kind()) && !h.start().isBefore(firstCandle) && h.confirmedAt().isBefore(end))
			.filter(h -> halves.stream().noneMatch(s -> s.anchor().equals(h.anchor()) && RANGE_ENDS.contains(s.kind())
				&& s.confirmedAt().isBefore(end)))
			.max(Comparator.comparing(Hit::confirmedAt));
		if (formed.isEmpty()) {
			return null;
		}
		List<Hit> range = halves.stream()
			.filter(h -> "FORMED".equals(h.kind()) && h.anchor().equals(formed.get().anchor())).toList();
		long low = range.stream().mapToLong(Hit::low).min().orElseThrow();
		long high = range.stream().mapToLong(Hit::high).max().orElseThrow();
		long close = bars.getLast().closeTicks();
		long twiceEq = low + high;
		if (close < low || close > high || 2 * close == twiceEq) {
			return null;
		}
		boolean premium = 2 * close > twiceEq;
		LongFunction<String> price = prices(tickSize);
		long from = formed.get().start().getEpochSecond();
		List<Context> context = List.of(new Context("RANGE_HIGH", true, from, high, high),
			new Context("RANGE_LOW", true, from, low, low));
		List<String> why = List.of(
			"The range runs from " + price.apply(low) + " to " + price.apply(high) + "; halfway, its equilibrium, is "
				+ RuleText.halfPrice(twiceEq, tickSize) + ".",
			"The last close, " + price.apply(close) + ", is " + (premium ? "above" : "below") + " it: "
				+ (premium ? "premium — where ICT looks to sell, not to buy." : "discount — where ICT looks to buy, not to sell."));
		return new Asked(PD_VERSION, pdDefinition(), close,
			List.of(new Option("PREMIUM", "Premium"), new Option("DISCOUNT", "Discount")), context,
			premium ? "PREMIUM" : "DISCOUNT", why);
	}

	// --- inside the OTE zone or not (ICT-42) -----------------------------------------------------------

	static final String OTE_VERSION = "judge-ote-1.0.0";

	static String oteDefinition() {
		return OTE_VERSION + ". A leg of dealing-range-1.0.0, still standing when the chart ends, its start and end drawn. "
			+ "Its optimal trade entry, by ote-1.0.0, runs from 62% to 79% of the way back from the end toward the start, "
			+ "rounded outward to whole ticks. The last close is inside it or not.";
	}

	/**
	 * Is the last close in the OTE zone: the latest OTE whose leg is wholly on the chart and that still stands when it
	 * ends. Null when there is none.
	 *
	 * @param zones every state of the OTE rule's zones known by the chart's end
	 */
	static Asked ote(List<Bar> bars, List<Hit> zones, Instant firstCandle, BigDecimal tickSize) {
		Instant end = bars.getLast().openTime().plusSeconds(1);
		Optional<Hit> zone = zones.stream()
			.filter(h -> "FORMED".equals(h.kind()) && number(h, "startTime") >= firstCandle.getEpochSecond()
				&& h.confirmedAt().isBefore(end))
			.filter(h -> zones.stream().noneMatch(s -> s.anchor().equals(h.anchor()) && RANGE_ENDS.contains(s.kind())
				&& s.confirmedAt().isBefore(end)))
			.max(Comparator.comparing(Hit::confirmedAt));
		if (zone.isEmpty()) {
			return null;
		}
		Hit z = zone.get();
		long start = number(z, "startPrice");
		long finish = number(z, "endPrice");
		long close = bars.getLast().closeTicks();
		boolean inside = close >= z.low() && close <= z.high();
		boolean up = finish > start;
		// After a leg up the zone is below the end: a close above it has not come back far enough yet.
		boolean shallow = up ? close > z.high() : close < z.low();
		String where = inside ? "inside it"
			: (close > z.high() ? "above it" : "below it") + (shallow ? " — not back far enough yet" : " — further back than the zone");
		LongFunction<String> price = prices(tickSize);
		List<Context> context = List.of(
			new Context("LEG_START", true, number(z, "startTime"), start, start),
			new Context("LEG_END", true, number(z, "endTime"), finish, finish));
		List<String> why = List.of(
			"The leg runs " + (up ? "up" : "down") + " from " + price.apply(start) + " to " + price.apply(finish) + ", "
				+ price.apply(Math.abs(finish - start)) + " points.",
			"62% to 79% of the way back from its end is " + price.apply(z.low()) + " to " + price.apply(z.high())
				+ ": the OTE zone, its line at 70.5% at " + RuleText.halfPrice(number(z, "midHalfTicks"), tickSize) + ".",
			"The last close, " + price.apply(close) + ", is " + where + ".");
		return new Asked(OTE_VERSION, oteDefinition(), close,
			List.of(new Option("INSIDE", "Inside the OTE zone"), new Option("OUTSIDE", "Outside it")), context,
			inside ? "INSIDE" : "OUTSIDE", why);
	}

	// --- which power of three (ICT-62) ------------------------------------------------------------------

	static final String PO3_VERSION = "judge-po3-1.0.0";
	/** po3-1.0.0's manipulation: at least this share of the day's range beyond the open. */
	static final int MANIPULATION_PERCENT = 15;

	static String po3Definition() {
		return PO3_VERSION + ". A whole New York day, midnight to the 17:00 halt, read after it closed by po3-1.0.0. "
			+ "Bullish power of three: it closed above its open, its low came before its high, and the low lay at least "
			+ MANIPULATION_PERCENT + "% of the day's range below the open — accumulation, manipulation down, distribution "
			+ "up. Bearish mirrors it. Any other day is neither.";
	}

	/**
	 * Bullish, bearish or neither, as the PO3 rule read the day: its stored hit for the day, or none. The reasons are
	 * worked out again from the candles, in prices; a day where the two disagree is never asked.
	 *
	 * @param bars the day, midnight to 17:00 New York
	 * @param days the PO3 rule's hits known by the day's close
	 */
	static Asked po3(List<Bar> bars, List<Hit> days, BigDecimal tickSize) {
		Instant first = bars.getFirst().openTime();
		Optional<Hit> hit = days.stream().filter(h -> h.start().equals(first)).findFirst();
		long open = bars.getFirst().openTicks();
		long close = bars.getLast().closeTicks();
		int lowAt = 0;
		int highAt = 0;
		for (int i = 1; i < bars.size(); i++) {
			if (bars.get(i).lowTicks() < bars.get(lowAt).lowTicks()) {
				lowAt = i;
			}
			if (bars.get(i).highTicks() > bars.get(highAt).highTicks()) {
				highAt = i;
			}
		}
		long low = bars.get(lowAt).lowTicks();
		long high = bars.get(highAt).highTicks();
		long range = high - low;
		if (range == 0) {
			return null;
		}
		boolean up = close > open;
		boolean down = close < open;
		boolean lowFirst = lowAt < highAt;
		long beyond = up || !down ? open - low : high - open;
		boolean deep = beyond * 100 >= (long) MANIPULATION_PERCENT * range;
		String mine = up && lowFirst && deep ? "BULLISH" : down && !lowFirst && deep ? "BEARISH" : "NEITHER";
		String rules = hit.map(Hit::direction).orElse("NEITHER");
		if (!mine.equals(rules)) {
			return null;
		}
		LongFunction<String> price = prices(tickSize);
		String side = up || !down ? "low" : "high";
		List<String> why = List.of(
			"The day opened at midnight at " + price.apply(open) + " and closed at 17:00 at " + price.apply(close) + ": "
				+ (up ? "above its open." : down ? "below its open." : "where it opened."),
			"Its low, " + price.apply(low) + ", came " + (lowFirst ? "before" : "after") + " its high, " + price.apply(high) + ".",
			"The " + side + " lay " + price.apply(beyond) + " points " + (side.equals("low") ? "below" : "above")
				+ " the open: " + Math.round(100.0 * beyond / range) + "% of the day's " + price.apply(range) + "-point range — "
				+ (deep ? "enough" : "short of the " + MANIPULATION_PERCENT + "%") + " to count as the manipulation.",
			switch (mine) {
				case "BULLISH" -> "Bullish power of three: accumulation at the open, a drop below it, then distribution up.";
				case "BEARISH" -> "Bearish power of three: accumulation at the open, a rise above it, then distribution down.";
				default -> "So neither: " + (!up && !down ? "a day that closes where it opened has no direction."
					: up && !lowFirst ? "a day up needs its low before its high — the drop first, then the rise."
					: down && lowFirst ? "a day down needs its high before its low — the rise first, then the drop."
					: "the move against the day's direction was too shallow to be the manipulation.");
			});
		return new Asked(PO3_VERSION, po3Definition(), close,
			List.of(new Option("BULLISH", "Bullish power of three"), new Option("BEARISH", "Bearish power of three"),
				new Option("NEITHER", "Neither")),
			List.of(new Context("DAY_OPEN", true, first.getEpochSecond(), open, open)), mine, why);
	}

	// --- where the stop goes (ICT-80) -------------------------------------------------------------------

	static final String STOP_VERSION = "judge-stop-1.0.0";
	/** How recent the sweep must be: its candle among the chart's last few. */
	static final int STOP_RECENT_CANDLES = 6;
	/** The least room between two stops offered, so each reads as its own place. */
	static final long STOP_MIN_GAP_TICKS = 4;

	static String stopDefinition() {
		return STOP_VERSION + ". A sweep of sweep-1.0.0 in the chart's last " + STOP_RECENT_CANDLES + " candles, and a trade "
			+ "the other way at the last close. The stop goes a tick beyond the low (or high) that made the sweep: if price "
			+ "trades there, the sweep failed and the idea is wrong. Fabio's standard: \"Stops are placed on recent high and "
			+ "lows after a liquidity sweep.\" Offered against it: a stop just past the entry, and one just past the old "
			+ "swing, inside the sweep's own wick.";
	}

	/**
	 * Where the stop goes: the latest sweep in the chart's last candles, nothing beyond its extreme since, and a trade
	 * the other way at the last close; three stops offered as A, B and C in an order drawn at random. Null when there
	 * is no such sweep, or the three would sit too close to tell apart.
	 *
	 * @param sweeps the sweep rule's hits known by the chart's end
	 */
	static Asked stopPlacement(List<Bar> bars, List<Hit> sweeps, BigDecimal tickSize, java.util.random.RandomGenerator random) {
		Instant end = bars.getLast().openTime().plusSeconds(1);
		Instant recent = bars.get(bars.size() - STOP_RECENT_CANDLES).openTime();
		Optional<Hit> sweep = sweeps.stream()
			.filter(h -> "SWEEP".equals(h.kind()) && !h.anchor().isBefore(recent) && h.confirmedAt().isBefore(end))
			.max(Comparator.comparing(Hit::anchor));
		if (sweep.isEmpty()) {
			return null;
		}
		Hit h = sweep.get();
		boolean lows = "SSL".equals(h.evidence().get("side"));
		long swing = number(h, "price");
		int at = 0;
		while (!bars.get(at).openTime().equals(h.anchor())) {
			at++;
		}
		Bar taken = bars.get(at);
		long extreme = lows ? taken.lowTicks() : taken.highTicks();
		for (int i = at + 1; i < bars.size(); i++) {
			if (lows ? bars.get(i).lowTicks() < extreme : bars.get(i).highTicks() > extreme) {
				return null;
			}
		}
		long entry = bars.getLast().closeTicks();
		int way = lows ? 1 : -1;
		long right = extreme - way;
		long wick = swing - way;
		long tight = entry - way * Math.max(STOP_MIN_GAP_TICKS, Math.abs(entry - swing) / 3);
		// In the trade's direction: right, then the swing's, then the tight one, then the entry, each apart.
		if ((wick - right) * way < STOP_MIN_GAP_TICKS || (tight - wick) * way < STOP_MIN_GAP_TICKS
				|| (entry - tight) * way < STOP_MIN_GAP_TICKS) {
			return null;
		}
		List<String> letters = new java.util.ArrayList<>(List.of("A", "B", "C"));
		java.util.Collections.shuffle(letters, java.util.Random.from(random));
		Map<String, Long> stops = Map.of(letters.get(0), right, letters.get(1), tight, letters.get(2), wick);
		LongFunction<String> price = prices(tickSize);
		List<Option> options = letters.stream().sorted()
			.map(l -> new Option(l, l + " — " + price.apply(stops.get(l)))).toList();
		long from = h.start().getEpochSecond();
		List<Context> context = new java.util.ArrayList<>();
		context.add(new Context("TRADE_ENTRY", true, taken.openTime().getEpochSecond(), entry, entry));
		letters.stream().sorted().forEach(l -> context.add(new Context("STOP_" + l, true, from, stops.get(l), stops.get(l))));
		String side = lows ? "low" : "high";
		String beyond = lows ? "below" : "above";
		List<String> why = List.of(
			"Price swept the swing " + side + " at " + price.apply(swing) + ": it traded to " + price.apply(extreme)
				+ " and closed back " + (lows ? "above" : "below") + " it. The trade is " + (lows ? "long" : "short") + " from "
				+ price.apply(entry) + ".",
			letters.get(0) + ", " + price.apply(right) + ", is a tick " + beyond + " the " + side + " that made the sweep: "
				+ "if price goes there, the sweep failed and the idea is wrong. That is where the stop goes.",
			letters.get(1) + ", " + price.apply(tight) + ", sits just " + beyond + " the entry, inside ordinary noise.",
			letters.get(2) + ", " + price.apply(wick) + ", sits just " + beyond + " the old swing — inside the sweep's own wick, "
				+ "where price has already been once.");
		return new Asked(STOP_VERSION, stopDefinition(), entry, options, context, letters.get(0), why);
	}

	// --- is the model's setup there (ICT-70, ICT-71) ------------------------------------------------------

	static String modelDefinition(TradingModel model) {
		return model.version() + ", the checklist practice uses (DEC-066), at the moment the chart ends, with a trade drawn "
			+ "the way the model draws it: the stop beyond what the model puts it beyond, the target at the nearest resting "
			+ "liquidity on the far side. A valid setup when every step of the checklist is met; otherwise not.";
	}

	/**
	 * Is {@code model}'s setup there when the chart ends? The trade's side is the way the model's own trigger points —
	 * the latest gap for the Silver Bullet, the latest shift for the 2022 model — and its stop and target are placed as
	 * the model places them; the model's checklist decides. Null when there is no trigger to draw a trade from.
	 *
	 * @param hits the model's rules' hits known by the chart's end, by detector
	 */
	static Asked model(TradingModel model, List<Bar> bars, Map<String, List<ModelChecklist.Hit>> hits, Instant cursor,
			Timeframe timeframe, BigDecimal tickSize) {
		String trigger = model == TradingModel.SILVER_BULLET ? "FVG" : "MSS";
		Optional<ModelChecklist.Hit> latest = hits.getOrDefault(trigger, List.of()).stream()
			.filter(h -> "FVG".equals(trigger) ? "OPEN".equals(h.kind()) || h.kind() == null : true)
			.max(Comparator.comparing(ModelChecklist.Hit::confirmedAt));
		if (latest.isEmpty()) {
			return null;
		}
		boolean up = "BULLISH".equals(latest.get().direction());
		long reference = bars.getLast().closeTicks();
		int way = up ? 1 : -1;
		long stop = model == TradingModel.SILVER_BULLET
			? (up ? latest.get().low() : latest.get().high()) - way
			: ((Number) latest.get().evidence().get("sweptPrice")).longValue() - way;
		if ((reference - stop) * way <= 0) {
			return null;
		}
		long risk = Math.abs(reference - stop);
		String side = up ? "BSL" : "SSL";
		long target = hits.getOrDefault("LIQUIDITY", List.of()).stream()
			.filter(l -> "RESTING".equals(l.kind()) && side.equals(l.evidence().get("side")))
			.filter(l -> hits.get("LIQUIDITY").stream().noneMatch(t -> t.start().equals(l.start())
				&& side.equals(t.evidence().get("side")) && !"RESTING".equals(t.kind())))
			.map(l -> ((Number) l.evidence().get("price")).longValue())
			.filter(p -> (p - reference) * way > 0)
			.min(Comparator.comparingLong(p -> Math.abs(p - reference)))
			.map(p -> p + way)
			.orElse(reference + way * 3 * risk);
		ModelChecklist.Checklist checklist = ModelChecklist.check(model, new ModelChecklist.Moment(cursor, timeframe,
			up ? Direction.LONG : Direction.SHORT, reference, stop, target, tickSize, bars, hits));
		boolean valid = checklist.items().stream().allMatch(ModelChecklist.Item::met);
		LongFunction<String> price = prices(tickSize);
		List<String> why = new java.util.ArrayList<>();
		why.add("The trade drawn: " + (up ? "long" : "short") + " from " + price.apply(reference) + ", the stop at "
			+ price.apply(stop) + ", the target at " + price.apply(target) + ".");
		checklist.items().forEach(i -> why.add((i.met() ? "✓ " : "✗ ") + i.text()));
		String name = model.title().replaceFirst("^The ", "");
		why.add(valid ? "Every step met: a valid " + name + " setup."
			: "Not every step is met, so this is not a valid " + name + " setup — whatever price does next.");
		long from = bars.getFirst().openTime().getEpochSecond();
		long entryAt = bars.getLast().openTime().getEpochSecond();
		return new Asked(model.version(), modelDefinition(model), reference,
			List.of(new Option("YES", "Yes — a valid setup"), new Option("NO", "No — a step is missing")),
			List.of(new Context("TRADE_ENTRY", true, entryAt, reference, reference),
				new Context("TRADE_STOP", true, entryAt, stop, stop), new Context("TRADE_TARGET", true, entryAt, target, target)),
			valid ? "YES" : "NO", why);
	}

	private static long number(Hit hit, String key) {
		return ((Number) hit.evidence().get(key)).longValue();
	}

	/** A drawn line's words on the chart: "Range high 21330.00". */
	static String label(Context c, BigDecimal tickSize) {
		String name = switch (c.what()) {
			case "RANGE_HIGH" -> "Range high";
			case "RANGE_LOW" -> "Range low";
			case "LEG_START" -> "Leg start";
			case "LEG_END" -> "Leg end";
			case "DAY_OPEN" -> "Midnight open";
			case "TRADE_ENTRY" -> "Entry";
			case "TRADE_STOP" -> "Stop";
			case "TRADE_TARGET" -> "Target";
			case "STOP_A" -> "A";
			case "STOP_B" -> "B";
			case "STOP_C" -> "C";
			default -> c.what().charAt(0) + c.what().substring(1).toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
		};
		return name + " " + RuleText.price(c.lowTicks(), tickSize);
	}

	/** Prices in the instrument's own form. */
	static LongFunction<String> prices(BigDecimal tickSize) {
		return ticks -> RuleText.price(ticks, tickSize);
	}
}
