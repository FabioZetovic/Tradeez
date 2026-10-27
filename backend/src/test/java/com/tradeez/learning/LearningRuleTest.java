package com.tradeez.learning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tradeez.learning.LearningRule.Attempt;
import com.tradeez.learning.LearningRule.Progress;
import com.tradeez.learning.LearningRule.State;
import com.tradeez.learning.LearningRule.Topic;

/** {@code learning-1.1.0} (T45, DEC-082): passed, done, reviews and the topic locks, each rule on its own. */
class LearningRuleTest {

	private static final LocalDate DAY = LocalDate.parse("2026-10-01");

	private static Instant on(LocalDate day, int minute) {
		return day.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).plusSeconds(60L * minute);
	}

	/** {@code n} attempts at ICT-20 on one day, each scoring {@code score}. */
	private static List<Attempt> attempts(int n, double score, LocalDate day) {
		List<Attempt> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(new Attempt("ICT-20", on(day, i), score, true));
		}
		return out;
	}

	private static Progress rated(List<Attempt> attempts, LocalDate today) {
		return LearningRule.progress("ICT-20", true, attempts, null, today);
	}

	@Nested
	@DisplayName("a lesson a drill practises")
	class Rated {

		@Test
		@DisplayName("is new before its first attempt")
		void fresh() {
			Progress p = rated(List.of(), DAY);
			assertThat(p.state()).isEqualTo(State.NEW);
			assertThat(p.recentAccuracy()).isNull();
			assertThat(p.nextReview()).isNull();
			assertThat(p.due()).isFalse();
		}

		@Test
		@DisplayName("is not passed on four perfect attempts: five are needed (Fabio)")
		void needsFive() {
			assertThat(rated(attempts(4, 1.0, DAY), DAY).state()).isEqualTo(State.LEARNING);
			Progress five = rated(attempts(5, 1.0, DAY), DAY);
			assertThat(five.state()).isEqualTo(State.SOLID);
			assertThat(five.attempts()).isEqualTo(5);
		}

		@Test
		@DisplayName("is passed at 70% over the last twenty, not at a hair under (Fabio)")
		void threshold() {
			List<Attempt> seventy = new ArrayList<>(attempts(14, 1.0, DAY));
			seventy.addAll(attempts(6, 0.0, DAY));
			assertThat(rated(seventy, DAY).state()).isEqualTo(State.SOLID);
			assertThat(rated(seventy, DAY).recentAccuracy()).isEqualTo(0.7);

			List<Attempt> under = new ArrayList<>(attempts(13, 1.0, DAY));
			under.addAll(attempts(6, 0.0, DAY));
			under.add(new Attempt("ICT-20", on(DAY, 100), 0.98, true));
			assertThat(rated(under, DAY).state()).isEqualTo(State.LEARNING);
		}

		@Test
		@DisplayName("is judged on the last twenty: early mistakes stop counting")
		void recentWindow() {
			List<Attempt> history = new ArrayList<>(attempts(30, 0.0, DAY.minusDays(10)));
			history.addAll(attempts(20, 1.0, DAY));
			Progress p = rated(history, DAY);
			assertThat(p.recentCount()).isEqualTo(20);
			assertThat(p.recentAccuracy()).isEqualTo(1.0);
			assertThat(p.attempts()).isEqualTo(50);
			assertThat(p.state()).isEqualTo(State.SOLID);
		}

		@Test
		@DisplayName("counts predictions, managed trades and risk questions toward passing, and says how many there were")
		void practiceCounts() {
			List<Attempt> mixed = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				mixed.add(new Attempt("ICT-16", on(DAY, i), i == 0 ? 0.0 : 1.0, false));
			}
			Progress p = LearningRule.progress("ICT-16", true, mixed, null, DAY);
			assertThat(p.state()).isEqualTo(State.SOLID);
			assertThat(p.recentAccuracy()).isEqualTo(0.8);
			assertThat(p.practised()).isEqualTo(5);
			assertThat(LearningRule.progress("ICT-16", true, mixed.subList(0, 4), null, DAY).state()).isEqualTo(State.LEARNING);
		}

		@Test
		@DisplayName("counts only its own attempts")
		void ownAttempts() {
			List<Attempt> mixed = new ArrayList<>(attempts(3, 1.0, DAY));
			mixed.add(new Attempt("ICT-01", on(DAY, 50), 0.0, true));
			assertThat(rated(mixed, DAY).attempts()).isEqualTo(3);
		}
	}

	@Nested
	@DisplayName("a reference card, or a lesson whose drill is not built yet")
	class Undrilled {

		@Test
		@DisplayName("is done once read, and only then")
		void read() {
			assertThat(LearningRule.progress("ICT-44", false, List.of(), null, DAY).state()).isEqualTo(State.UNREAD);
			assertThat(LearningRule.progress("ICT-44", false, List.of(), on(DAY, 0), DAY).state()).isEqualTo(State.DONE);
			Progress tried = LearningRule.progress("ICT-44", false,
				List.of(new Attempt("ICT-44", on(DAY, 3), 1.0, false)), null, DAY);
			assertThat(tried.state()).isEqualTo(State.UNREAD);
			assertThat(tried.due()).isFalse();
		}
	}

	@Nested
	@DisplayName("reviews (SM-2, a review a day)")
	class Reviews {

		@Test
		@DisplayName("a good first day is due the next day; a second six days on; then further by its ease")
		void growing() {
			assertThat(LearningRule.nextReview(attempts(3, 1.0, DAY))).isEqualTo(DAY.plusDays(1));
			List<Attempt> two = new ArrayList<>(attempts(3, 1.0, DAY));
			two.addAll(attempts(3, 1.0, DAY.plusDays(1)));
			assertThat(LearningRule.nextReview(two)).isEqualTo(DAY.plusDays(1 + 6));
			List<Attempt> three = new ArrayList<>(two);
			three.addAll(attempts(3, 1.0, DAY.plusDays(7)));
			// Ease 2.5 + 0.1 a perfect day: 2.6, 2.7 — six days times 2.7 is 16.
			assertThat(LearningRule.nextReview(three)).isEqualTo(DAY.plusDays(7 + 16));
		}

		@Test
		@DisplayName("many attempts in one day are one review, its quality their mean")
		void oneReviewADay() {
			List<Attempt> day = new ArrayList<>(attempts(5, 1.0, DAY));
			day.addAll(attempts(5, 0.0, DAY)); // a 50% day: a lapse
			assertThat(LearningRule.nextReview(day)).isEqualTo(DAY.plusDays(1));
		}

		@Test
		@DisplayName("a bad day brings it back tomorrow, however well it went before")
		void lapse() {
			List<Attempt> history = new ArrayList<>(attempts(3, 1.0, DAY));
			history.addAll(attempts(3, 1.0, DAY.plusDays(1)));
			history.addAll(attempts(3, 0.2, DAY.plusDays(7)));
			assertThat(LearningRule.nextReview(history)).isEqualTo(DAY.plusDays(8));
		}

		@Test
		@DisplayName("is due on its day and after, not before")
		void due() {
			List<Attempt> one = attempts(3, 1.0, DAY);
			assertThat(rated(one, DAY).due()).isFalse();
			assertThat(rated(one, DAY.plusDays(1)).due()).isTrue();
			assertThat(rated(one, DAY.plusDays(5)).due()).isTrue();
		}

		@Test
		@DisplayName("grades a day's mean as SM-2's 0 to 5, a half or less failing")
		void quality() {
			assertThat(LearningRule.quality(1.0)).isEqualTo(5);
			assertThat(LearningRule.quality(0.8)).isEqualTo(4);
			assertThat(LearningRule.quality(0.6)).isEqualTo(3);
			assertThat(LearningRule.quality(0.51)).isEqualTo(3);
			assertThat(LearningRule.quality(0.5)).isEqualTo(2);
			assertThat(LearningRule.quality(0.0)).isZero();
		}
	}

	@Nested
	@DisplayName("the topic locks")
	class Locks {

		/** Two topics: A has lessons a1, a2, a3 with a reference card between the first two; B has b1, b2. */
		private final List<Topic> topics = List.of(
			new Topic("A", List.of("a1", "ref", "a2", "a3"), lessons("a1", true, "ref", false, "a2", true, "a3", true)),
			new Topic("B", List.of("b1", "b2"), lessons("b1", true, "b2", true)));

		private static Map<String, Boolean> lessons(Object... pairs) {
			Map<String, Boolean> map = new LinkedHashMap<>();
			for (int i = 0; i < pairs.length; i += 2) {
				map.put((String) pairs[i], (Boolean) pairs[i + 1]);
			}
			return map;
		}

		private Map<String, Progress> progress(String... complete) {
			Map<String, Progress> map = new LinkedHashMap<>();
			for (Topic t : topics) {
				for (String id : t.concepts()) {
					boolean done = List.of(complete).contains(id);
					map.put(id, new Progress(id, true, done ? State.SOLID : State.NEW, 0, 0, null, 0, null, null, null, false));
				}
			}
			return map;
		}

		@Test
		@DisplayName("opens the first lesson of every topic and every reference card from the start, and nothing else")
		void start() {
			Map<String, Boolean> open = LearningRule.open(topics, progress());
			assertThat(open).containsEntry("a1", true).containsEntry("ref", true).containsEntry("b1", true)
				.containsEntry("a2", false).containsEntry("a3", false).containsEntry("b2", false);
		}

		@Test
		@DisplayName("opens a lesson once the lesson before it is passed — a reference card between them does not count")
		void next() {
			Map<String, Boolean> open = LearningRule.open(topics, progress("a1"));
			assertThat(open).containsEntry("a2", true).containsEntry("a3", false).containsEntry("b2", false);
			assertThat(LearningRule.open(topics, progress("a1", "a2"))).containsEntry("a3", true);
			// A later lesson passed out of order opens the one after it, never the one before.
			assertThat(LearningRule.open(topics, progress("a2"))).containsEntry("a2", false).containsEntry("a3", true);
		}

		@Test
		@DisplayName("suggests the first lesson at the start, then the next lesson of the furthest topic started")
		void suggested() {
			Map<String, Progress> none = progress();
			assertThat(LearningRule.suggested(topics, none, LearningRule.open(topics, none))).isEqualTo("a1");
			Map<String, Progress> one = progress("a1");
			assertThat(LearningRule.suggested(topics, one, LearningRule.open(topics, one))).isEqualTo("a2");
			Map<String, Progress> both = progress("a1", "b1");
			assertThat(LearningRule.suggested(topics, both, LearningRule.open(topics, both))).isEqualTo("b2");
			Map<String, Progress> all = progress("a1", "a2", "a3", "b1", "b2");
			assertThat(LearningRule.suggested(topics, all, LearningRule.open(topics, all))).isNull();
		}
	}

	@Test
	@DisplayName("says its rule in words, with every number it runs on")
	void definition() {
		assertThat(LearningRule.definition()).contains(LearningRule.VERSION)
			.contains(String.valueOf(LearningRule.SOLID_MIN_ATTEMPTS)).contains(String.valueOf(LearningRule.RECENT))
			.contains(LearningRule.SOLID_PERCENT + "%").contains(LearningRule.PASS_PERCENT + "%");
	}
}
