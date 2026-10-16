package com.tradeez.drills;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.tradeez.marketdata.Bar;

/**
 * {@code predict-1.0.0} (T15, DEC-058): what a prediction drill asks, and how what price did answers it.
 *
 * <p>Two questions, both about two targets drawn on the chart, both answered by whichever real price
 * reached first:
 * <ul>
 *   <li><b>Which liquidity is taken first</b> (ICT-16, the draw on liquidity): the nearest resting
 *       buy-side above the last close and sell-side below it ({@code liquidity-1.0.0}), from swings on
 *       the chart. Together they bracket price — the range's external liquidity (ICT-15); the open gaps
 *       inside it are its internal liquidity. Each path is given its obstacles (ICT-17): the swing points
 *       between price and the level, and the open fair value gaps facing the move.</li>
 *   <li><b>External or internal next</b> (ICT-15): the last candle took a resting level on one side — external
 *       liquidity. Does price next reach the nearest untapped gap behind it (internal), or the next
 *       resting level beyond it (external)?</li>
 * </ul>
 *
 * <p>A level is taken when a candle trades a tick beyond it, as {@code liquidity-1.0.0} says; a gap is
 * reached when a candle trades into it — tapped, in {@code fvg-state-1.0.0}'s words. Within
 * {@value #HORIZON_CANDLES} candles of the chart's end, the first candle to reach either decides. When one
 * candle reaches both, its one-minute candles decide; when one minute does, nothing can, and the question
 * is never asked — nor one that neither target answers in time.
 *
 * <p>Nothing here reads past the moment except {@link #resolve}, which is given the future explicitly.
 */
final class Prediction {

	static final String VERSION = "predict-1.0.0";

	/** Candles after the chart that may answer a question. Measured: 99% are answered within 44–74. */
	static final int HORIZON_CANDLES = 100;

	/** Candles shown after the deciding one, once answered: what happened next, briefly. */
	static final int REVEAL_AFTER = 10;

	private Prediction() {
	}

	/** Where a target sits in the range (ICT-15): its edge, or inside it. */
	enum Role {
		ERL, IRL
	}

	/**
	 * A resting swing level, or a fair value gap, as the chart shows it at the moment.
	 *
	 * @param state the rule's latest state confirmed by the moment
	 */
	record Level(UUID id, boolean high, long price, Instant swingTime, String state, Instant changedAt) {

		boolean resting() {
			return "RESTING".equals(state);
		}
	}

	record Gap(UUID id, boolean bullish, long lowTicks, long highTicks, Instant start, String state) {

		/** Not filled, inverted or given up on: still a gap in the chart. */
		boolean open() {
			return "OPEN".equals(state) || "TAPPED".equals(state) || "CE".equals(state);
		}

		boolean untapped() {
			return "OPEN".equals(state);
		}
	}

	/**
	 * A stored rule hit ({@code liquidity-1.0.0} or {@code fvg-state-1.0.0}), as much as a question reads.
	 *
	 * @param side BSL or SSL, for a liquidity level
	 */
	record Hit(UUID id, String kind, boolean bullish, String side, Instant start, Instant anchor, Instant end,
			Instant confirmedAt, long lowTicks, long highTicks) {
	}

	/** States of {@code fvg-state-1.0.0} in the order a gap moves through them. */
	private static final List<String> GAP_STATES = List.of("OPEN", "TAPPED", "CE", "FILLED", "INVERTED", "EXPIRED");

	/**
	 * Each swing level whose swing is on the chart, in the state the moment knows: the latest of its
	 * {@code liquidity-1.0.0} hits confirmed by then — nothing after it. Its identity is the hit that set
	 * it resting.
	 *
	 * @param earliestSwing the first candle a swing may be on: the one after the chart's first, whose left
	 *                      neighbour is what makes it a swing
	 */
	static List<Level> levelsAt(List<Hit> hits, Instant earliestSwing, Instant moment) {
		Map<String, List<Hit>> byLevel = hits.stream()
			.filter(h -> !h.start().isBefore(earliestSwing) && h.start().isBefore(moment) && !h.confirmedAt().isAfter(moment))
			.collect(Collectors.groupingBy(h -> h.side() + "@" + h.start()));
		List<Level> levels = new ArrayList<>();
		for (List<Hit> states : byLevel.values()) {
			Hit resting = states.stream().filter(h -> "RESTING".equals(h.kind())).findFirst().orElse(null);
			if (resting == null) {
				continue;
			}
			Hit now = states.stream().max(Comparator.comparing(Hit::confirmedAt)).orElseThrow();
			levels.add(new Level(resting.id(), "BSL".equals(resting.side()), resting.lowTicks(), resting.start(),
				now.kind(), now.end()));
		}
		return levels;
	}

	/**
	 * Each gap whose first candle is on the chart, in the state the moment knows — nothing after it. One
	 * candle can move a gap through several states at once; the furthest is where it is.
	 */
	static List<Gap> gapsAt(List<Hit> hits, Instant windowFrom, Instant moment) {
		Map<String, List<Hit>> byGap = hits.stream()
			.filter(h -> !h.start().isBefore(windowFrom) && !h.confirmedAt().isAfter(moment))
			.collect(Collectors.groupingBy(h -> h.bullish() + "@" + h.anchor()));
		List<Gap> gaps = new ArrayList<>();
		for (List<Hit> states : byGap.values()) {
			Hit open = states.stream().filter(h -> "OPEN".equals(h.kind())).findFirst().orElse(null);
			if (open == null) {
				continue;
			}
			Hit now = states.stream()
				.max(Comparator.comparing(Hit::confirmedAt).thenComparing(h -> GAP_STATES.indexOf(h.kind())))
				.orElseThrow();
			gaps.add(new Gap(open.id(), open.bullish(), open.lowTicks(), open.highTicks(), open.start(), now.kind()));
		}
		return gaps;
	}

	/**
	 * One of the two answers.
	 *
	 * @param value     the answer's name: BSL, SSL, IRL or ERL
	 * @param level     a level (taken a tick beyond) rather than a gap (reached when traded into)
	 * @param above     above the last close
	 * @param distance  ticks from the last close to where it is taken or reached
	 * @param obstacles for "which is taken first", what lies on the way (ICT-17); otherwise null
	 */
	record Target(String value, Role role, boolean level, boolean above, long fromTime, long lowTicks,
			long highTicks, UUID setupId, long distance, Obstacles obstacles) {

		boolean reachedBy(Bar bar) {
			if (level) {
				return above ? bar.highTicks() > lowTicks : bar.lowTicks() < lowTicks;
			}
			return above ? bar.highTicks() >= lowTicks : bar.lowTicks() <= highTicks;
		}
	}

	/** On the way to a target: swing points between price and it, and open gaps facing the move. */
	record Obstacles(int swings, int gaps) {

		int total() {
			return swings + gaps;
		}
	}

	/**
	 * Drawn for context, not to be chosen: a gap inside the range (IRL), or the level the last candle
	 * just took.
	 */
	record Context(String what, boolean level, long fromTime, long lowTicks, long highTicks) {
	}

	record Question(long closeTicks, List<Target> targets, List<Context> context) {
	}

	/**
	 * @param candles  how many candles after the chart the deciding one is, from 1
	 * @param minutes  decided on one-minute candles, because one candle reached both
	 */
	record Outcome(String value, Instant at, int candles, boolean minutes) {
	}

	/**
	 * Which is taken first: the nearest resting buy-side above the close and sell-side below it. Null
	 * when the chart lacks either.
	 */
	static Question liquidityFirst(Bar last, List<Level> levels, List<Gap> gaps) {
		long close = last.closeTicks();
		Level bsl = levels.stream().filter(l -> l.high() && l.resting() && l.price() >= close)
			.min(Comparator.comparingLong(Level::price)).orElse(null);
		Level ssl = levels.stream().filter(l -> !l.high() && l.resting() && l.price() <= close)
			.max(Comparator.comparingLong(Level::price)).orElse(null);
		if (bsl == null || ssl == null) {
			return null;
		}
		List<Target> targets = List.of(
			level("BSL", Role.ERL, bsl, close, obstacles(close, bsl.price(), levels, gaps)),
			level("SSL", Role.ERL, ssl, close, obstacles(close, ssl.price(), levels, gaps)));
		List<Context> context = gaps.stream()
			.filter(g -> g.open() && g.lowTicks() >= ssl.price() && g.highTicks() <= bsl.price())
			.map(g -> new Context(g.bullish() ? "IRL_BULLISH_GAP" : "IRL_BEARISH_GAP", false,
				g.start().getEpochSecond(), g.lowTicks(), g.highTicks()))
			.toList();
		return new Question(close, targets, context);
	}

	/**
	 * External or internal next: the last candle took resting liquidity on one side (and only one). Then
	 * the nearest untapped gap behind it, against the next resting level beyond. Null when the moment is
	 * not one, or the chart lacks either target.
	 */
	static Question externalOrInternal(Bar last, List<Level> levels, List<Gap> gaps) {
		long close = last.closeTicks();
		Instant at = last.openTime();
		List<Level> taken = levels.stream().filter(l -> "TAKEN".equals(l.state()) && at.equals(l.changedAt())).toList();
		boolean up = taken.stream().anyMatch(Level::high);
		if (taken.isEmpty() || taken.stream().anyMatch(l -> l.high() != up)) {
			return null;
		}
		// The most extreme level it took, as sweep-1.0.0 judges a candle that takes several.
		Level swept = up ? taken.stream().max(Comparator.comparingLong(Level::price)).orElseThrow()
			: taken.stream().min(Comparator.comparingLong(Level::price)).orElseThrow();
		Level next = up
			? levels.stream().filter(l -> l.high() && l.resting() && l.price() >= close)
				.min(Comparator.comparingLong(Level::price)).orElse(null)
			: levels.stream().filter(l -> !l.high() && l.resting() && l.price() <= close)
				.max(Comparator.comparingLong(Level::price)).orElse(null);
		Gap behind = up
			? gaps.stream().filter(g -> g.bullish() && g.untapped() && g.highTicks() < close)
				.max(Comparator.comparingLong(Gap::highTicks)).orElse(null)
			: gaps.stream().filter(g -> !g.bullish() && g.untapped() && g.lowTicks() > close)
				.min(Comparator.comparingLong(Gap::lowTicks)).orElse(null);
		if (next == null || behind == null) {
			return null;
		}
		Target internal = new Target("IRL", Role.IRL, false, !up, behind.start().getEpochSecond(), behind.lowTicks(),
			behind.highTicks(), behind.id(), up ? close - behind.highTicks() : behind.lowTicks() - close, null);
		Target external = level("ERL", Role.ERL, next, close, null);
		Context took = new Context(up ? "TAKEN_BSL" : "TAKEN_SSL", true, swept.swingTime().getEpochSecond(),
			swept.price(), swept.price());
		return new Question(close, List.of(internal, external), List.of(took));
	}

	/**
	 * The first of the targets the future reaches, or null if no candle within the horizon decides it.
	 *
	 * @param minutesOf a candle's one-minute candles, for when it reaches both
	 */
	static Outcome resolve(List<Target> targets, List<Bar> future, Function<Bar, List<Bar>> minutesOf) {
		for (int k = 0; k < Math.min(future.size(), HORIZON_CANDLES); k++) {
			Bar bar = future.get(k);
			List<Target> reached = targets.stream().filter(t -> t.reachedBy(bar)).toList();
			if (reached.size() == 1) {
				return new Outcome(reached.getFirst().value(), bar.openTime(), k + 1, false);
			}
			if (reached.size() > 1) {
				for (Bar minute : minutesOf.apply(bar)) {
					List<Target> first = targets.stream().filter(t -> t.reachedBy(minute)).toList();
					if (first.size() == 1) {
						return new Outcome(first.getFirst().value(), bar.openTime(), k + 1, true);
					}
					if (first.size() > 1) {
						return null; // one minute reached both: nothing stored can say which came first
					}
				}
				return null; // the minutes do not show it: undecidable from what is stored
			}
		}
		return null;
	}

	private static Target level(String value, Role role, Level level, long close, Obstacles obstacles) {
		long distance = level.high() ? level.price() - close : close - level.price();
		return new Target(value, role, true, level.high(), level.swingTime().getEpochSecond(), level.price(),
			level.price(), level.id(), distance, obstacles);
	}

	/**
	 * What stands between price and a level: every swing point strictly between them, whatever became of
	 * it, and every open gap facing the move that reaches into the path.
	 */
	static Obstacles obstacles(long close, long target, List<Level> levels, List<Gap> gaps) {
		boolean up = target >= close;
		long low = Math.min(close, target);
		long high = Math.max(close, target);
		int swings = (int) levels.stream().filter(l -> l.price() > low && l.price() < high).count();
		int facing = (int) gaps.stream()
			.filter(g -> g.open() && g.bullish() != up && g.lowTicks() < high && g.highTicks() > low)
			.count();
		return new Obstacles(swings, facing);
	}

	/** The future the drill may reveal: up to the deciding candle and a few after it. */
	static List<Bar> revealed(List<Bar> future, Outcome outcome) {
		List<Bar> shown = new ArrayList<>();
		for (int k = 0; k < Math.min(future.size(), outcome.candles() + REVEAL_AFTER); k++) {
			shown.add(future.get(k));
		}
		return shown;
	}
}
