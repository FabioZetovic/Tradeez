package com.tradeez.practice;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.tradeez.practice.FillEngine.Direction;
import com.tradeez.practice.PracticeDtos.StatisticsDimensionView;
import com.tradeez.practice.PracticeDtos.StatisticsGroupView;
import com.tradeez.practice.PracticeDtos.StatisticsView;

/**
 * {@value #VERSION} (T25, DEC-069): a learner's finished trades added up — overall, and by model, killzone,
 * weekday, side, how much of the model's checklist was met, and whether their own rules were kept — with the
 * number of trades behind every figure (ANALYSIS_CLAUDE.md §1.4).
 *
 * <p>Expectancy, the mean result in R, comes with a 95% range: a handful of trades says little, and a range that
 * spans zero cannot yet be told from no edge at all. Nothing here forecasts a market (INVARIANT 8).
 */
public final class JournalStatistics {

	public static final String VERSION = "journal-stats-1.0.0";

	/** 95%, the normal approximation: fine for a reading, never for a claim. */
	private static final double Z = 1.96;

	private static final Map<String, String> KILLZONES = Map.of("ASIA", "Asia", "LONDON", "London", "NEW_YORK_AM",
		"New York AM", "NEW_YORK_PM", "New York PM");

	/**
	 * One finished trade.
	 *
	 * @param model       the model's title, or null when none was named
	 * @param killzone    ASIA … NEW_YORK_PM, or null outside them
	 * @param met         the checklist's checks met when placed, or null without a model
	 * @param rulesKept   the learner's own rules kept on the scorecard (DEC-067)
	 */
	public record Sample(double r, long netPnlCents, String model, String killzone, DayOfWeek weekday,
			Direction direction, Integer met, Integer total, int rulesKept, int rulesBroken) {
	}

	private JournalStatistics() {
	}

	public static StatisticsView of(List<Sample> samples) {
		List<StatisticsDimensionView> dimensions = List.of(
			dimension("model", "Model", samples, s -> s.model() == null ? "No model" : s.model(), null),
			dimension("killzone", "Killzone", samples,
				s -> s.killzone() == null ? "Outside the killzones" : KILLZONES.get(s.killzone()),
				List.of("Asia", "London", "New York AM", "New York PM", "Outside the killzones")),
			dimension("weekday", "Weekday", samples,
				s -> s.weekday().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
				// The futures week opens on Sunday evening, New York time.
				List.of("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday")),
			dimension("side", "Side", samples, s -> s.direction() == Direction.LONG ? "Long" : "Short",
				List.of("Long", "Short")),
			dimension("checks", "Model checks", samples, JournalStatistics::checks,
				List.of("Every check met", "Some checks met", "No check met", "No model named")),
			dimension("rules", "Your rules", samples, JournalStatistics::rules,
				List.of("All kept", "Some broken", "None to check")));
		List<String> notes = new ArrayList<>();
		if (samples.isEmpty()) {
			notes.add("No finished trade yet: the figures appear as the replay shows each trade's exit.");
		}
		return new StatisticsView(VERSION, group("All trades", samples), dimensions, notes);
	}

	private static String checks(Sample s) {
		if (s.met() == null) {
			return "No model named";
		}
		return s.met().equals(s.total()) ? "Every check met" : s.met() == 0 ? "No check met" : "Some checks met";
	}

	private static String rules(Sample s) {
		return s.rulesBroken() > 0 ? "Some broken" : s.rulesKept() > 0 ? "All kept" : "None to check";
	}

	/** @param order the groups' order, or null for first seen */
	private static StatisticsDimensionView dimension(String id, String title, List<Sample> samples,
			Function<Sample, String> by, List<String> order) {
		Map<String, List<Sample>> groups = new LinkedHashMap<>();
		if (order != null) {
			order.forEach(label -> groups.put(label, new ArrayList<>()));
		}
		samples.forEach(s -> groups.computeIfAbsent(by.apply(s), k -> new ArrayList<>()).add(s));
		return new StatisticsDimensionView(id, title, groups.entrySet().stream()
			.filter(e -> !e.getValue().isEmpty())
			.map(e -> group(e.getKey(), e.getValue()))
			.toList());
	}

	static StatisticsGroupView group(String label, List<Sample> samples) {
		int n = samples.size();
		int wins = 0;
		int losses = 0;
		double won = 0;
		double lost = 0;
		double sum = 0;
		long net = 0;
		for (Sample s : samples) {
			sum += s.r();
			net += s.netPnlCents();
			if (s.r() > 0) {
				wins++;
				won += s.r();
			} else if (s.r() < 0) {
				losses++;
				lost -= s.r();
			}
		}
		Double mean = n == 0 ? null : round2(sum / n);
		Double low = null;
		Double high = null;
		if (n >= 2) {
			double m = sum / n;
			double squares = 0;
			for (Sample s : samples) {
				squares += (s.r() - m) * (s.r() - m);
			}
			double half = Z * Math.sqrt(squares / (n - 1) / n);
			low = round2(m - half);
			high = round2(m + half);
		}
		return new StatisticsGroupView(label, n, wins, losses, n == 0 ? null : round2(100.0 * wins / n),
			wins == 0 ? null : round2(won / wins), losses == 0 ? null : round2(-lost / losses), mean, low, high,
			losses == 0 ? null : round2(won / lost), round2(sum), net, reading(n, low, high));
	}

	/** The expectancy's range in words. */
	private static String reading(int n, Double low, Double high) {
		if (n == 0) {
			return "No trades.";
		}
		if (low == null) {
			return "One trade: no range yet.";
		}
		if (low > 0) {
			return "Positive so far: the whole range is above zero.";
		}
		if (high < 0) {
			return "Negative so far: the whole range is below zero.";
		}
		return "Cannot be told from zero yet: the range spans it.";
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
