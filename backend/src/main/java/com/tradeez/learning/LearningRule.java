package com.tradeez.learning;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * {@code learning-1.0.0} (T16, DEC-059): what a learner's record says about each concept — written as
 * plainly as possible, because the learner is shown the rule beside the result.
 *
 * <ul>
 *   <li><b>Rated</b> concepts are those a drill grades against a rule (modes A and B, ICT_CONCEPTS.md:
 *       "solid is measured on D and R concepts only"). Each attempt scores 0 to 1 — a name right or
 *       wrong; for marking, the share of the answer found once misses and extra marks count against it.
 *       A concept is <b>solid</b> once it has at least {@value #SOLID_MIN_ATTEMPTS} attempts and the last
 *       {@value #RECENT} average at least {@value #SOLID_PERCENT}%.</li>
 *   <li>Prediction drills (mode C) are practice, never a rating: a judgement concept "unlocks by use,
 *       never by a score".</li>
 *   <li>A concept no drill rates is <b>done</b> once its card is read — or, for one a prediction drill
 *       practises, once that is done.</li>
 *   <li><b>Review</b> follows SM-2, Anki's scheduler, one review per concept per day (UTC): the day's
 *       mean score is the review's quality. A poor day ({@value #PASS_PERCENT}% or less) brings the
 *       concept back the next day; good ones push it out — 1 day, 6 days, then by its ease.</li>
 * </ul>
 *
 * <p>Only graded drills count, and a drill is graded only on real data (INVARIANT 8b, DEC-034).
 */
public final class LearningRule {

	public static final String VERSION = "learning-1.0.0";
	static final int SOLID_MIN_ATTEMPTS = 10;
	static final int RECENT = 20;
	static final int SOLID_PERCENT = 80;
	/** At or below this a day's review is a lapse (SM-2 quality under 3). */
	static final int PASS_PERCENT = 50;
	private static final double START_EASE = 2.5;
	private static final double MIN_EASE = 1.3;

	private LearningRule() {
	}

	/** The rule in words, from the same constants the rule runs on. */
	public static String definition() {
		return VERSION + ". A concept a drill grades against a rule — by marking or by naming, on real data — is solid "
			+ "once it has at least " + SOLID_MIN_ATTEMPTS + " attempts and the last " + RECENT + " average at least "
			+ SOLID_PERCENT + "%. Each attempt scores 0 to 1: a name right or wrong; marks by the share of the answer "
			+ "found, misses and extra marks counting against it. Prediction drills are practice, never a rating. A "
			+ "concept no drill grades is done once its card is read, or practised. Reviews are spaced as Anki spaces "
			+ "them (SM-2), one a day per concept: a day at " + PASS_PERCENT + "% or less brings it back the next day; "
			+ "good days push it out — a day, six days, then further each time. A level opens when the levels it "
			+ "builds on are complete; the ladder suggests, it never locks.";
	}

	/** One graded drill, as the record keeps it. */
	public record Attempt(String conceptId, Instant at, double score, boolean rated) {
	}

	public enum State {
		/** Rated, never attempted. */
		NEW,
		/** Rated, attempted, not solid yet. */
		LEARNING,
		SOLID,
		/** No drill rates it, and it has not been read or practised. */
		UNREAD,
		/** No drill rates it, and it has been read or practised. */
		DONE
	}

	/**
	 * @param recentAccuracy the mean score of the last {@value #RECENT} rated attempts, 0 to 1; null without any
	 * @param nextReview     the day a rated concept is due again; null before its first attempt
	 */
	public record Progress(String conceptId, boolean rated, State state, int attempts, int recentCount,
			Double recentAccuracy, int practised, Instant readAt, Instant lastPractised, LocalDate nextReview,
			boolean due) {

		/** Solid, or done: what a level's unlock counts. */
		public boolean complete() {
			return state == State.SOLID || state == State.DONE;
		}
	}

	/**
	 * A concept's progress from its attempts and whether its card was read.
	 *
	 * @param rated whether any drill grades this concept against a rule
	 */
	static Progress progress(String conceptId, boolean rated, List<Attempt> attempts, Instant readAt, LocalDate today) {
		List<Attempt> mine = attempts.stream().filter(a -> a.conceptId().equals(conceptId))
			.sorted(Comparator.comparing(Attempt::at)).toList();
		List<Attempt> graded = mine.stream().filter(Attempt::rated).toList();
		int practised = (int) mine.stream().filter(a -> !a.rated()).count();
		Instant last = mine.isEmpty() ? null : mine.getLast().at();
		if (!rated) {
			State state = readAt != null || practised > 0 ? State.DONE : State.UNREAD;
			return new Progress(conceptId, false, state, 0, 0, null, practised, readAt, last, null, false);
		}
		if (graded.isEmpty()) {
			return new Progress(conceptId, true, State.NEW, 0, 0, null, practised, readAt, last, null, false);
		}
		List<Attempt> recent = graded.subList(Math.max(0, graded.size() - RECENT), graded.size());
		double accuracy = recent.stream().mapToDouble(Attempt::score).average().orElseThrow();
		boolean solid = graded.size() >= SOLID_MIN_ATTEMPTS && 100 * accuracy >= SOLID_PERCENT - 1e-9;
		LocalDate next = nextReview(graded);
		return new Progress(conceptId, true, solid ? State.SOLID : State.LEARNING, graded.size(), recent.size(),
			accuracy, practised, readAt, last, next, !today.isBefore(next));
	}

	/** SM-2 over days: each day the concept was practised is one review, its quality the day's mean score. */
	static LocalDate nextReview(List<Attempt> graded) {
		Map<LocalDate, List<Double>> byDay = new TreeMap<>();
		for (Attempt a : graded) {
			byDay.computeIfAbsent(a.at().atZone(ZoneOffset.UTC).toLocalDate(), d -> new ArrayList<>()).add(a.score());
		}
		double ease = START_EASE;
		int repetitions = 0;
		long interval = 0;
		LocalDate last = null;
		for (Map.Entry<LocalDate, List<Double>> day : byDay.entrySet()) {
			double mean = day.getValue().stream().mapToDouble(Double::doubleValue).average().orElseThrow();
			int quality = quality(mean);
			if (quality < 3) {
				repetitions = 0;
				interval = 1;
			} else {
				repetitions++;
				interval = repetitions == 1 ? 1 : repetitions == 2 ? 6 : Math.round(interval * ease);
			}
			ease = Math.max(MIN_EASE, ease + 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
			last = day.getKey();
		}
		return last.plusDays(interval);
	}

	/** A day's mean score as SM-2's 0–5 grade: 1.0 is 5, 0.8 is 4, 0.6 is 3, a half or less fails. */
	static int quality(double mean) {
		if (100 * mean <= PASS_PERCENT + 1e-9) {
			return (int) Math.floor(5 * mean);
		}
		return Math.max(3, (int) Math.round(5 * mean));
	}

	/**
	 * When a level of the ladder opens (ICT_CONCEPTS.md, Part 12). The ladder suggests; it never locks —
	 * every card stays open to read.
	 */
	sealed interface Unlock {
		/** Reference knowledge, or the start. */
		record Always() implements Unlock {
		}

		/** Every concept of these levels complete. */
		record LevelsComplete(Set<Integer> levels) implements Unlock {
		}

		/** Each concept of the level opens on its own, once the concepts it builds on are complete. */
		record EachOnItsPrerequisites() implements Unlock {
		}

		/** Opens with another level. */
		record With(int level) implements Unlock {
		}
	}

	/** Part 12's "Unlocks when", as {@code learning-1.0.0} settles it. */
	static final Map<Integer, Unlock> LADDER = Map.of(
		0, new Unlock.Always(),
		1, new Unlock.LevelsComplete(Set.of(0)),
		2, new Unlock.LevelsComplete(Set.of(1)),
		3, new Unlock.LevelsComplete(Set.of(1)),
		4, new Unlock.LevelsComplete(Set.of(2, 3)),
		5, new Unlock.LevelsComplete(Set.of(1)),
		6, new Unlock.Always(),
		7, new Unlock.LevelsComplete(Set.of(2, 5, 6)),
		8, new Unlock.EachOnItsPrerequisites(),
		9, new Unlock.With(8));

	/** What the learner needs to know about a concept to place it on the ladder. */
	record Placed(String conceptId, int level, List<String> prerequisites) {
	}

	/** Whether each level is open, given every concept's progress. */
	static Map<Integer, Boolean> openLevels(Collection<Placed> concepts, Map<String, Progress> progress) {
		Map<Integer, Boolean> open = new TreeMap<>();
		for (int level : new TreeMap<>(LADDER).keySet()) {
			open.put(level, opens(level, concepts, progress));
		}
		return open;
	}

	private static boolean opens(int level, Collection<Placed> concepts, Map<String, Progress> progress) {
		return switch (LADDER.get(level)) {
			case Unlock.Always a -> true;
			case Unlock.LevelsComplete c -> c.levels().stream().allMatch(l -> complete(l, concepts, progress));
			case Unlock.EachOnItsPrerequisites e -> concepts.stream().filter(p -> p.level() == level)
				.anyMatch(p -> ready(p, progress));
			case Unlock.With w -> opens(w.level(), concepts, progress);
		};
	}

	/** Every concept of the level solid or done. A level with no concepts in it is not complete. */
	static boolean complete(int level, Collection<Placed> concepts, Map<String, Progress> progress) {
		List<Placed> in = concepts.stream().filter(p -> p.level() == level).toList();
		return !in.isEmpty() && in.stream().allMatch(p -> progress.get(p.conceptId()).complete());
	}

	/** The concepts a concept builds on are all complete. */
	static boolean ready(Placed concept, Map<String, Progress> progress) {
		return concept.prerequisites().stream().allMatch(id -> progress.containsKey(id) && progress.get(id).complete());
	}

	/** Whether a concept is open to be suggested: its level is, and in level 8 the concept itself is. */
	static boolean open(Placed concept, Map<Integer, Boolean> levels, Map<String, Progress> progress) {
		if (!levels.getOrDefault(concept.level(), false)) {
			return false;
		}
		return !(LADDER.get(concept.level()) instanceof Unlock.EachOnItsPrerequisites) || ready(concept, progress);
	}
}
