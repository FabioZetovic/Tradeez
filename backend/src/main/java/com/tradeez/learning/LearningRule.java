package com.tradeez.learning;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code learning-1.1.0} (T45, DEC-082; was {@code learning-1.0.0}, T16, DEC-059): what a learner's record says about
 * each concept Learn teaches — written as plainly as possible, because the learner is shown the rule beside the result.
 *
 * <ul>
 *   <li>A <b>lesson</b> with a drill is <b>passed</b> (the state {@code SOLID}) once its drills have been answered at
 *       least {@value #SOLID_MIN_ATTEMPTS} times and the last {@value #RECENT} average at least {@value #SOLID_PERCENT}%
 *       — Fabio's mark. Every drill of the concept counts: marking and naming by their score, a prediction or a
 *       managed trade 1 when right. Each attempt scores 0 to 1.</li>
 *   <li>A lesson whose drill is not built yet, and a <b>reference card</b>, are <b>done</b> once read.</li>
 *   <li><b>Locks</b>: inside a topic the first lesson is open, and each lesson after it opens once the lesson
 *       before it is passed. Reference cards never lock.</li>
 *   <li><b>Review</b> follows SM-2, Anki's scheduler, one review per concept per day (UTC): the day's
 *       mean score is the review's quality. A poor day ({@value #PASS_PERCENT}% or less) brings the
 *       concept back the next day; good ones push it out — 1 day, 6 days, then by its ease.</li>
 * </ul>
 *
 * <p>Only graded drills count, and a drill is graded only on real data (INVARIANT 8b, DEC-034).
 */
public final class LearningRule {

	public static final String VERSION = "learning-1.1.0";
	static final int SOLID_MIN_ATTEMPTS = 5;
	static final int RECENT = 20;
	static final int SOLID_PERCENT = 70;
	/** At or below this a day's review is a lapse (SM-2 quality under 3). */
	static final int PASS_PERCENT = 50;
	private static final double START_EASE = 2.5;
	private static final double MIN_EASE = 1.3;

	private LearningRule() {
	}

	/** The rule in words, from the same constants the rule runs on. */
	public static String definition() {
		return VERSION + ". A lesson is passed once its drills have been answered at least " + SOLID_MIN_ATTEMPTS
			+ " times and the last " + RECENT + " average at least " + SOLID_PERCENT + "%. Each answer scores 0 to 1: "
			+ "a name or a prediction right or wrong; marks by the share of the answer found, misses and extra marks "
			+ "counting against it. Inside a topic the first lesson is open and each next one opens when the one before "
			+ "it is passed; reference cards never lock, and are done once read. Reviews are spaced as Anki spaces them "
			+ "(SM-2), one a day per lesson: a day at " + PASS_PERCENT + "% or less brings it back the next day; good "
			+ "days push it out — a day, six days, then further each time.";
	}

	/**
	 * One graded drill, as the record keeps it.
	 *
	 * @param rated marking or naming, graded against a rule; a prediction, a managed trade or a risk question is not
	 *              — it still counts toward passing its lesson
	 */
	public record Attempt(String conceptId, Instant at, double score, boolean rated) {
	}

	public enum State {
		/** A drilled lesson never attempted. */
		NEW,
		/** Attempted, not passed yet. */
		LEARNING,
		/** Passed. */
		SOLID,
		/** No drill: not read yet. */
		UNREAD,
		/** No drill: read. */
		DONE
	}

	/**
	 * @param drilled        a drill practises it
	 * @param attempts       its drills answered
	 * @param recentAccuracy the mean score of the last {@value #RECENT} attempts, 0 to 1; null without any
	 * @param practised      of the attempts, those not graded against a rule (predictions, managed trades, risk)
	 * @param nextReview     the day a drilled concept is due again; null before its first attempt
	 */
	public record Progress(String conceptId, boolean drilled, State state, int attempts, int recentCount,
			Double recentAccuracy, int practised, Instant readAt, Instant lastPractised, LocalDate nextReview,
			boolean due) {

		/** Passed, or done: what opens the next lesson. */
		public boolean complete() {
			return state == State.SOLID || state == State.DONE;
		}
	}

	/** A concept's progress from its attempts and whether its card was read. */
	static Progress progress(String conceptId, boolean drilled, List<Attempt> attempts, Instant readAt, LocalDate today) {
		List<Attempt> mine = attempts.stream().filter(a -> a.conceptId().equals(conceptId))
			.sorted(Comparator.comparing(Attempt::at)).toList();
		int practised = (int) mine.stream().filter(a -> !a.rated()).count();
		Instant last = mine.isEmpty() ? null : mine.getLast().at();
		if (!drilled) {
			State state = readAt != null ? State.DONE : State.UNREAD;
			return new Progress(conceptId, false, state, 0, 0, null, practised, readAt, last, null, false);
		}
		if (mine.isEmpty()) {
			return new Progress(conceptId, true, State.NEW, 0, 0, null, 0, readAt, null, null, false);
		}
		List<Attempt> recent = mine.subList(Math.max(0, mine.size() - RECENT), mine.size());
		double accuracy = recent.stream().mapToDouble(Attempt::score).average().orElseThrow();
		boolean solid = mine.size() >= SOLID_MIN_ATTEMPTS && 100 * accuracy >= SOLID_PERCENT - 1e-9;
		LocalDate next = nextReview(mine);
		return new Progress(conceptId, true, solid ? State.SOLID : State.LEARNING, mine.size(), recent.size(),
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
	 * One topic's pages as the locks see them, in the order taught.
	 *
	 * @param lessons for each page, true for a lesson and false for a reference card
	 */
	record Topic(String title, List<String> concepts, Map<String, Boolean> lessons) {
	}

	/**
	 * Which pages are open: inside each topic the first lesson, and each lesson after it once the lesson before it is
	 * complete; reference cards always.
	 */
	static Map<String, Boolean> open(List<Topic> topics, Map<String, Progress> progress) {
		Map<String, Boolean> open = new LinkedHashMap<>();
		for (Topic topic : topics) {
			String before = null;
			for (String id : topic.concepts()) {
				if (!topic.lessons().get(id)) {
					open.put(id, true);
					continue;
				}
				open.put(id, before == null || progress.get(before).complete());
				before = id;
			}
		}
		return open;
	}

	/**
	 * The next page to work on: the first open, incomplete lesson of the furthest topic the learner has started — some
	 * of it complete, not all — or, before any, of the first topic with one.
	 */
	static String suggested(List<Topic> topics, Map<String, Progress> progress, Map<String, Boolean> open) {
		Topic started = null;
		for (Topic topic : topics) {
			List<String> lessons = topic.concepts().stream().filter(topic.lessons()::get).toList();
			long complete = lessons.stream().filter(id -> progress.get(id).complete()).count();
			if (complete > 0 && complete < lessons.size()) {
				started = topic;
			}
		}
		List<Topic> order = new ArrayList<>(topics);
		if (started != null) {
			order.remove(started);
			order.addFirst(started);
		}
		return order.stream()
			.flatMap(t -> t.concepts().stream().filter(t.lessons()::get))
			.filter(id -> open.get(id) && !progress.get(id).complete())
			.findFirst().orElse(null);
	}
}
