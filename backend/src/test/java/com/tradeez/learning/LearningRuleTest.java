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
import com.tradeez.learning.LearningRule.Placed;
import com.tradeez.learning.LearningRule.Progress;
import com.tradeez.learning.LearningRule.State;

/** {@code learning-1.0.0} (T16, DEC-059): solid, done, reviews and the ladder, each rule on its own. */
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
	@DisplayName("a concept a drill grades")
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
		@DisplayName("is not solid on a handful of perfect attempts: ten are needed")
		void needsTen() {
			assertThat(rated(attempts(9, 1.0, DAY), DAY).state()).isEqualTo(State.LEARNING);
			Progress ten = rated(attempts(10, 1.0, DAY), DAY);
			assertThat(ten.state()).isEqualTo(State.SOLID);
			assertThat(ten.attempts()).isEqualTo(10);
		}

		@Test
		@DisplayName("is solid at 80% over the last twenty, not at a hair under")
		void threshold() {
			List<Attempt> eighty = new ArrayList<>(attempts(16, 1.0, DAY));
			eighty.addAll(attempts(4, 0.0, DAY));
			assertThat(rated(eighty, DAY).state()).isEqualTo(State.SOLID);
			assertThat(rated(eighty, DAY).recentAccuracy()).isEqualTo(0.8);

			List<Attempt> under = new ArrayList<>(attempts(15, 1.0, DAY));
			under.addAll(attempts(4, 0.0, DAY));
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
		@DisplayName("is not rated by prediction drills, which only count as practice")
		void predictionsAreNotRatings() {
			Progress p = LearningRule.progress("ICT-20", true,
				List.of(new Attempt("ICT-20", on(DAY, 1), 1.0, false)), null, DAY);
			assertThat(p.state()).isEqualTo(State.NEW);
			assertThat(p.practised()).isEqualTo(1);
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
	@DisplayName("a concept no drill grades")
	class Unrated {

		@Test
		@DisplayName("is done once its card is read, or once it is practised")
		void readOrPractised() {
			assertThat(LearningRule.progress("ICT-16", false, List.of(), null, DAY).state()).isEqualTo(State.UNREAD);
			assertThat(LearningRule.progress("ICT-16", false, List.of(), on(DAY, 0), DAY).state()).isEqualTo(State.DONE);
			Progress practised = LearningRule.progress("ICT-16", false,
				List.of(new Attempt("ICT-16", on(DAY, 3), 0.0, false)), null, DAY);
			assertThat(practised.state()).as("use, not a score").isEqualTo(State.DONE);
			assertThat(practised.due()).isFalse();
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
	@DisplayName("the ladder")
	class Ladder {

		/** Two concepts a level for levels 0–9; level 8's second builds on level 3's first. */
		private final List<Placed> curriculum = curriculum();

		private static List<Placed> curriculum() {
			List<Placed> out = new ArrayList<>();
			for (int level = 0; level <= 9; level++) {
				out.add(new Placed("L" + level + "a", level, List.of()));
				out.add(new Placed("L" + level + "b", level, level == 8 ? List.of("L3a") : List.of()));
			}
			return out;
		}

		private Map<String, Progress> progress(String... complete) {
			Map<String, Progress> map = new LinkedHashMap<>();
			for (Placed p : curriculum) {
				boolean done = List.of(complete).contains(p.conceptId());
				map.put(p.conceptId(), new Progress(p.conceptId(), false, done ? State.DONE : State.UNREAD, 0, 0, null,
					0, null, null, null, false));
			}
			return map;
		}

		@Test
		@DisplayName("opens the foundations and the reference level from the start, and nothing else")
		void start() {
			Map<Integer, Boolean> open = LearningRule.openLevels(curriculum, progress());
			assertThat(open).containsEntry(0, true).containsEntry(6, true).containsEntry(1, false)
				.containsEntry(2, false).containsEntry(8, true); // L8a builds on nothing
		}

		@Test
		@DisplayName("opens a level when the levels it builds on are complete — all of their concepts")
		void levels() {
			assertThat(LearningRule.openLevels(curriculum, progress("L0a")).get(1)).isFalse();
			Map<Integer, Boolean> afterZero = LearningRule.openLevels(curriculum, progress("L0a", "L0b"));
			assertThat(afterZero.get(1)).isTrue();
			assertThat(afterZero.get(2)).isFalse();
			Map<Integer, Boolean> afterOne = LearningRule.openLevels(curriculum, progress("L0a", "L0b", "L1a", "L1b"));
			assertThat(afterOne).containsEntry(2, true).containsEntry(3, true).containsEntry(5, true)
				.containsEntry(4, false).containsEntry(7, false);
		}

		@Test
		@DisplayName("opens each entry model on its own prerequisites, and execution with the first model")
		void models() {
			Map<String, Progress> none = progress();
			Map<Integer, Boolean> levels = LearningRule.openLevels(curriculum, none);
			Placed waiting = curriculum.stream().filter(p -> p.conceptId().equals("L8b")).findFirst().orElseThrow();
			assertThat(LearningRule.open(waiting, levels, none)).isFalse();
			Map<String, Progress> ready = progress("L3a");
			assertThat(LearningRule.open(waiting, LearningRule.openLevels(curriculum, ready), ready)).isTrue();
			assertThat(levels.get(9)).isEqualTo(levels.get(8));
		}

		@Test
		@DisplayName("keeps the entry models closed while none of them has what it builds on")
		void modelsWaitForPrerequisites() {
			List<Placed> waiting = curriculum.stream()
				.map(p -> p.level() == 8 ? new Placed(p.conceptId(), 8, List.of("L3a")) : p).toList();
			Map<String, Progress> none = progress();
			Map<Integer, Boolean> closed = LearningRule.openLevels(waiting, none);
			assertThat(closed).containsEntry(8, false).containsEntry(9, false);
			Map<String, Progress> ready = progress("L3a");
			assertThat(LearningRule.openLevels(waiting, ready)).containsEntry(8, true).containsEntry(9, true);
		}

		@Test
		@DisplayName("never calls a level with nothing in it complete")
		void emptyLevel() {
			assertThat(LearningRule.complete(42, curriculum, progress())).isFalse();
		}

		@Test
		@DisplayName("has a rule for every level of the curriculum")
		void everyLevel() {
			assertThat(LearningRule.LADDER).containsOnlyKeys(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
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
