package com.tradeez.analysis;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@value #VERSION} (T21, DEC-065): what one rule found over a market's history, counted — how often, of
 * which kind, and for a rule that follows its zones, how their lives ended. The base rates a learner
 * should know before believing any one example; never a prediction (INVARIANT 8).
 *
 * <p>A pure function of the rule and the candles, split at contract rolls exactly as the chart splits
 * them ({@link Segments}), so it is the same rule that draws the chart doing the counting. Four ways:
 * <ul>
 * <li><b>every rule</b> — its hits, and how many per 100 candles;</li>
 * <li><b>lives</b> — gaps, blocks, ranges and liquidity levels each emit one row per state their zone
 * reaches. A life is counted once it is over: it reached a final state, or the rule's
 * {@value #LIFE_CANDLES} candles passed within the data. One still running when the data ends is left
 * out, not guessed at;</li>
 * <li><b>kinds</b> — how the hits divide: BOS against CHoCH, a sweep against a run;</li>
 * <li><b>periods</b> — the time rules (T19), against the days, weeks or opening windows in the data, so
 * "a Tuesday low" is a share of weeks, not of hits.</li>
 * </ul>
 */
public final class EvidenceCalculator {

	public static final String VERSION = "evidence-1.0.0";

	/** How long every lifecycle rule follows a zone (fvg-state, blocks, ranges, liquidity: 500). */
	static final int LIFE_CANDLES = 500;

	private EvidenceCalculator() {
	}

	/** Counts, keyed by a state, a kind or a period. */
	public record Count(String key, long count) {
	}

	/**
	 * @param ended   lives over within the data
	 * @param reached of those, how many reached each state, in the rule's order
	 */
	public record Lives(long ended, List<Count> reached) {
	}

	/**
	 * @param unit    "day", "week" or "window"
	 * @param counts  how many there were: one {@code ALL} entry, or one per kind when each kind has its own
	 *                periods (a Judas swing's London and New York windows)
	 */
	public record PeriodTotals(String unit, List<Count> counts) {
	}

	/**
	 * @param hits    lives for a lifecycle rule, detections otherwise
	 * @param kinds   hits by kind (or by direction, for a rule without kinds), largest first; empty for a
	 *                lifecycle rule, whose kinds are its states
	 * @param lives   null unless the rule follows lives
	 * @param periods null unless the rule reads periods
	 */
	public record RuleEvidence(Instant from, Instant to, long candles, long hits, List<Count> kinds, Lives lives,
			PeriodTotals periods) {
	}

	/** A rule whose detections are the states of lives: where they start, end, and what can happen between. */
	record LifeRule(String start, List<String> outcomes, Set<String> terminal) {
	}

	private static final LifeRule GAPS = new LifeRule("OPEN", List.of("TAPPED", "CE", "FILLED", "INVERTED", "EXPIRED"),
		Set.of("INVERTED", "EXPIRED"));
	private static final LifeRule BLOCKS = new LifeRule("FORMED",
		List.of("TAPPED", "MEAN_THRESHOLD", "INVALIDATED", "EXPIRED"), Set.of("INVALIDATED", "EXPIRED"));
	/**
	 * A leg's range, left past its end — the move went on — or past its start — it turned (DEC-062 measured
	 * six in ten past the end). {@code BROKEN_END} and {@code BROKEN_START} are read from the leg's direction.
	 */
	private static final LifeRule RANGES = new LifeRule("FORMED", List.of("BROKEN_END", "BROKEN_START", "EXPIRED"),
		Set.of("BROKEN_END", "BROKEN_START", "EXPIRED"));
	private static final LifeRule OTE = new LifeRule("FORMED", List.of("TAPPED", "BROKEN_END", "BROKEN_START", "EXPIRED"),
		RANGES.terminal());
	/** Premium and discount are halves, not legs: each is left above its range's high or below its low. */
	private static final LifeRule HALVES = new LifeRule("FORMED", List.of("BROKEN_HIGH", "BROKEN_LOW", "EXPIRED"),
		Set.of("BROKEN_HIGH", "BROKEN_LOW", "EXPIRED"));

	static final Map<String, LifeRule> LIVES = Map.ofEntries(
		Map.entry("FVG_STATE", GAPS),
		Map.entry("OB", BLOCKS), Map.entry("BREAKER", BLOCKS), Map.entry("MITIGATION", BLOCKS),
		Map.entry("REJECTION", BLOCKS), Map.entry("UNICORN", BLOCKS),
		Map.entry("DEALING_RANGE", RANGES), Map.entry("PD", HALVES), Map.entry("OTE", OTE),
		Map.entry("LIQUIDITY", new LifeRule("RESTING", List.of("TAKEN", "EXPIRED"), Set.of("TAKEN", "EXPIRED"))));

	/** The time rules, and what their hits are a share of. */
	static final Map<String, String> PERIODS = Map.of("PO3", "day", "DAILY_PROFILE", "day", "WEEKLY_PROFILE", "week",
		"JUDAS", "window");

	/**
	 * @param segments the market's candles of {@code timeframe}, oldest first, split at contract rolls
	 */
	public static RuleEvidence measure(Detector detector, Timeframe timeframe, List<List<Bar>> segments) {
		List<List<Bar>> nonEmpty = segments.stream().filter(s -> !s.isEmpty()).toList();
		long candles = nonEmpty.stream().mapToLong(List::size).sum();
		Instant from = nonEmpty.isEmpty() ? Instant.EPOCH : nonEmpty.getFirst().getFirst().openTime();
		Instant to = nonEmpty.isEmpty() ? Instant.EPOCH : nonEmpty.getLast().getLast().openTime().plus(timeframe.duration());
		LifeRule life = LIVES.get(detector.id());
		if (life != null) {
			return lives(detector, timeframe, nonEmpty, life, from, to, candles);
		}
		Map<String, Long> kinds = new LinkedHashMap<>();
		long hits = 0;
		for (List<Bar> segment : nonEmpty) {
			for (Detection d : detector.detect(segment, timeframe)) {
				hits++;
				kinds.merge(d.kind() != null ? d.kind() : d.direction().name(), 1L, Long::sum);
			}
		}
		String unit = PERIODS.get(detector.id());
		PeriodTotals periods = unit == null ? null : new PeriodTotals(unit, periods(detector.id(), timeframe, nonEmpty));
		return new RuleEvidence(from, to, candles, hits, largestFirst(kinds), null, periods);
	}

	private static RuleEvidence lives(Detector detector, Timeframe timeframe, List<List<Bar>> segments, LifeRule rule,
			Instant from, Instant to, long candles) {
		long lives = 0;
		long ended = 0;
		Map<String, Long> reached = new LinkedHashMap<>();
		rule.outcomes().forEach(state -> reached.put(state, 0L));
		for (List<Bar> segment : segments) {
			List<Instant> opens = segment.stream().map(Bar::openTime).toList();
			// One life per zone: its anchor and direction (a range's premium and discount are two).
			Map<String, List<Detection>> byLife = new LinkedHashMap<>();
			for (Detection d : detector.detect(segment, timeframe)) {
				byLife.computeIfAbsent(d.anchor() + "|" + d.direction() + "|" + d.lowTicks() + "|" + d.highTicks(),
					k -> new ArrayList<>()).add(d);
			}
			for (List<Detection> states : byLife.values()) {
				Detection start = states.stream().filter(d -> rule.start().equals(d.kind())).findFirst().orElse(null);
				if (start == null) {
					continue; // its start lies before the data: not a whole life
				}
				lives++;
				boolean over = states.stream().anyMatch(d -> rule.terminal().contains(outcome(d, rule)));
				int formed = candleClosingAt(opens, start.confirmedAt());
				if (!over && segment.size() - 1 - formed < LIFE_CANDLES) {
					continue; // still running when the data ends
				}
				ended++;
				states.stream().map(d -> outcome(d, rule)).distinct().filter(reached::containsKey)
					.forEach(state -> reached.merge(state, 1L, Long::sum));
			}
		}
		List<Count> outcomes = reached.entrySet().stream().map(e -> new Count(e.getKey(), e.getValue())).toList();
		return new RuleEvidence(from, to, candles, lives, List.of(), new Lives(ended, outcomes), null);
	}

	/** A state as counted: for a rule that counts legs, a break read as past the leg's end or its start. */
	private static String outcome(Detection state, LifeRule rule) {
		if (!rule.outcomes().contains("BROKEN_END")) {
			return state.kind();
		}
		boolean up = state.direction() == Detection.Direction.BULLISH;
		return switch (state.kind()) {
			case "BROKEN_HIGH" -> up ? "BROKEN_END" : "BROKEN_START";
			case "BROKEN_LOW" -> up ? "BROKEN_START" : "BROKEN_END";
			default -> state.kind();
		};
	}

	/** The index of the candle a confirmation time is the close of. */
	private static int candleClosingAt(List<Instant> opens, Instant confirmedAt) {
		int i = Collections.binarySearch(opens, confirmedAt);
		int insertion = i >= 0 ? i : -i - 1;
		return Math.max(0, insertion - 1);
	}

	/** The days, weeks or windows a time rule's hits are a share of. */
	private static List<Count> periods(String rule, Timeframe timeframe, List<List<Bar>> segments) {
		long days = 0;
		long weeks = 0;
		long london = 0;
		long newYork = 0;
		for (List<Bar> segment : segments) {
			days += Periods.midnightDays(segment, timeframe).size();
			weeks += Periods.weeks(segment, timeframe).size();
			for (Bar bar : segment) {
				LocalTime at = Periods.local(bar).toLocalTime();
				london += at.equals(LocalTime.MIDNIGHT) ? 1 : 0;
				newYork += at.equals(LocalTime.of(9, 30)) ? 1 : 0;
			}
		}
		return switch (rule) {
			case "WEEKLY_PROFILE" -> List.of(new Count("ALL", weeks));
			case "JUDAS" -> List.of(new Count("LONDON", london), new Count("NEW_YORK", newYork));
			default -> List.of(new Count("ALL", days));
		};
	}

	private static List<Count> largestFirst(Map<String, Long> counts) {
		return counts.entrySet().stream()
			.sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
			.map(e -> new Count(e.getKey(), e.getValue())).toList();
	}
}
