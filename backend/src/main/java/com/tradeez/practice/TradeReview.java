package com.tradeez.practice;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.tradeez.analysis.RuleText;
import com.tradeez.marketdata.Bar;
import com.tradeez.practice.FillEngine.Direction;
import com.tradeez.practice.FillEngine.Outcome;

/**
 * {@value #VERSION} (T24, DEC-068): a finished trade told as it happened — what the rules had seen before the
 * learner placed it, what they saw while it ran, how it ended — each hit explained in its rule's own numbers
 * (DEC-026). Then what only hindsight shows: whether price reached the learner's draw, and, for a trade closed
 * by hand, what the bracket would have done.
 *
 * <p>Nothing past the replay's cursor is used (DEC-009): the hits are those confirmed by it, the candles end
 * at it, and the bracket's own exit is told only once the replay has passed it.
 */
public final class TradeReview {

	public static final String VERSION = "review-1.0.0";

	/** How far before the decision the review looks, in the session's candles: the checklist's own reach. */
	static final int BEFORE_CANDLES = ModelChecklist.RECENT_CANDLES;

	/** The latest hits of each rule before the decision; older ones are on the chart. */
	static final int PER_RULE_BEFORE = 3;

	/** Hits while the trade ran, at most; the rest are counted. */
	static final int MAX_DURING = 24;

	public static final String BEFORE = "BEFORE";
	public static final String TRADE = "TRADE";
	public static final String AFTER = "AFTER";

	/** One stored hit, already in words: its heading and why it qualified. */
	public record Hit(String detector, Instant confirmedAt, String title, List<String> lines) {
	}

	/**
	 * @param checklist   the model's checklist as placed, in one line, or null
	 * @param exit        the trade's exit: the learner's close, or the bracket's own
	 * @param plannedExit when closed by hand, where the bracket itself would have gone out; otherwise null
	 * @param bars        one-minute candles from the entry to the cursor
	 */
	public record Facts(Direction direction, int contracts, long reference, long stop, long target, BigDecimal tickSize,
			Duration candle, String checklist, String bias, Long dol, Integer confidence, Instant decision, Instant entry,
			long entryTicks, Instant exit, long exitTicks, Outcome outcome, double r, boolean gapExit, Instant plannedExit,
			Outcome plannedOutcome, long plannedExitTicks, double plannedR, Instant cursor, List<Hit> hits, List<Bar> bars) {
	}

	public record Moment(Instant at, String phase, String kind, String title, List<String> lines) {
	}

	/** @param hitsUsed indices into {@link Facts#hits()} of the hits told, for drawing them */
	public record Review(List<Moment> moments, List<String> summary, List<Integer> hitsUsed) {
	}

	/** Where a moment sorts among others at the same instant. */
	private record Placed(Moment moment, int order) {
	}

	private TradeReview() {
	}

	public static Review build(Facts f) {
		List<Placed> placed = new ArrayList<>();
		List<String> summary = new ArrayList<>();
		List<Integer> used = new ArrayList<>();
		String side = f.direction() == Direction.LONG ? "long" : "short";

		List<String> plan = new ArrayList<>();
		plan.add(Scorecard.capitalised(side) + " " + f.contracts() + (f.contracts() == 1 ? " contract" : " contracts") + " at market from "
			+ price(f.reference(), f) + ": stop " + price(f.stop(), f) + ", target " + price(f.target(), f) + String.format(
				Locale.ROOT, ", %.2f : 1.", (double) Math.abs(f.target() - f.reference()) / Math.abs(f.reference() - f.stop())));
		if (f.checklist() != null) {
			plan.add(f.checklist());
		}
		if (f.bias() != null) {
			plan.add("Your bias: " + f.bias().toLowerCase(Locale.ROOT) + ".");
		}
		if (f.dol() != null) {
			plan.add("Your draw on liquidity: " + price(f.dol(), f) + ".");
		}
		if (f.confidence() != null) {
			plan.add(f.confidence() + "% sure the target would fill before the stop.");
		}
		placed.add(new Placed(new Moment(f.decision(), TRADE, "DECISION", "You placed a " + side, plan), 1));
		placed.add(new Placed(new Moment(f.entry(), TRADE, "ENTRY", "Filled at " + price(f.entryTicks(), f),
			List.of("The first price after you placed it: a market order fills at the next price there is.")), 2));

		// Before the decision: the latest few hits of each rule within the checklist's reach.
		Instant since = f.decision().minus(f.candle().multipliedBy(BEFORE_CANDLES));
		Map<String, List<Integer>> before = new LinkedHashMap<>();
		List<Integer> during = new ArrayList<>();
		for (int i = 0; i < f.hits().size(); i++) {
			Instant at = f.hits().get(i).confirmedAt();
			if (at.isAfter(since) && !at.isAfter(f.decision())) {
				before.computeIfAbsent(f.hits().get(i).detector(), k -> new ArrayList<>()).add(i);
			} else if (at.isAfter(f.decision()) && !at.isAfter(f.exit())) {
				during.add(i);
			}
		}
		Comparator<Integer> byTime = Comparator.comparing(i -> f.hits().get(i).confirmedAt());
		for (List<Integer> rule : before.values()) {
			rule.sort(byTime);
			for (int i : rule.subList(Math.max(0, rule.size() - PER_RULE_BEFORE), rule.size())) {
				placed.add(rule(f, i, BEFORE, 0));
				used.add(i);
			}
		}
		during.sort(byTime);
		for (int i : during.subList(0, Math.min(MAX_DURING, during.size()))) {
			placed.add(rule(f, i, TRADE, 3));
			used.add(i);
		}
		if (during.size() > MAX_DURING) {
			summary.add((during.size() - MAX_DURING) + " more rule hits while it ran are not listed: switch the rules on "
				+ "above the chart to see every one.");
		}

		placed.add(new Placed(new Moment(f.exit(), TRADE, "EXIT", ended(f.outcome(), f.gapExit()) + " at "
			+ price(f.exitTicks(), f) + ": " + Scorecard.r(f.r()) + ".", List.of()), 5));

		if (f.dol() != null) {
			// Reached by trading at or beyond it from the entry's side: a market that gaps through it has reached it.
			boolean above = f.dol() >= f.entryTicks();
			Bar touched = f.bars().stream()
				.filter(b -> !b.openTime().isBefore(minute(f.entry())) && (above ? b.highTicks() >= f.dol() : b.lowTicks() <= f.dol()))
				.findFirst().orElse(null);
			if (touched == null) {
				summary.add("Your draw at " + price(f.dol(), f) + " was not reached in what the replay has shown.");
			} else if (!touched.openTime().isAfter(f.exit())) {
				placed.add(new Placed(new Moment(touched.openTime(), TRADE, "DRAW", "Price reached your draw at "
					+ price(f.dol(), f), List.of("While the trade was open.")), 4));
			} else {
				placed.add(new Placed(new Moment(touched.openTime(), AFTER, "DRAW", "Price reached your draw at "
					+ price(f.dol(), f), List.of("After the trade had ended.")), 6));
			}
		}

		if (f.plannedExit() != null) {
			if (f.cursor().isAfter(f.plannedExit())) {
				placed.add(new Placed(new Moment(f.plannedExit(), AFTER, "HELD", "Had you held, " + held(f.plannedOutcome())
					+ " at " + price(f.plannedExitTicks(), f) + ": " + Scorecard.r(f.plannedR()) + ".",
					List.of("What the bracket you placed would have done without your close.")), 6));
			} else {
				summary.add("What the bracket would have done without your close is still ahead of the replay: step on to "
					+ "see it.");
			}
		}

		placed.sort(Comparator.comparing((Placed p) -> p.moment().at()).thenComparingInt(Placed::order));
		return new Review(placed.stream().map(Placed::moment).toList(), summary, used);
	}

	private static Placed rule(Facts f, int i, String phase, int order) {
		Hit hit = f.hits().get(i);
		return new Placed(new Moment(hit.confirmedAt(), phase, "RULE", hit.title(), hit.lines()), order);
	}

	/** How a trade ended, as a sentence's start. */
	private static String ended(Outcome outcome, boolean gap) {
		return switch (outcome) {
			case TARGET -> gap ? "The target filled, the market opening beyond it," : "The target filled";
			case STOP -> gap ? "The stop filled, the market opening beyond it," : "The stop filled";
			case AMBIGUOUS -> "Stop and target inside one candle, scored as the stop";
			case TIME_EXIT -> "The session closed with neither level reached, out";
			case CLOSED -> "You closed it";
		};
	}

	/** How the bracket would have ended, had the learner not closed it. */
	private static String held(Outcome outcome) {
		return switch (outcome) {
			case TARGET -> "the target would have filled";
			case STOP -> "the stop would have filled";
			case AMBIGUOUS -> "stop and target would have been touched inside one candle, scored as the stop,";
			case TIME_EXIT, CLOSED -> "the session's close would have taken it out";
		};
	}

	/** One-minute candles open on the minute: the one the entry falls in. */
	private static Instant minute(Instant at) {
		return Instant.ofEpochSecond(at.getEpochSecond() - Math.floorMod(at.getEpochSecond(), 60));
	}

	private static String price(long ticks, Facts f) {
		return RuleText.price(ticks, f.tickSize());
	}

}
