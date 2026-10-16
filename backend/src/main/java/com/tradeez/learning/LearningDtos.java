package com.tradeez.learning;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Wire shapes for the learning state (T16, DEC-059). */
public final class LearningDtos {

	private LearningDtos() {
	}

	@Schema(description = "Where the learner stands in the curriculum, worked out from their graded drills and the "
		+ "cards they have read, under the rule named")
	public record LearningResponse(
		@Schema(description = "learning-1.0.0") String version,
		@Schema(description = "The rule in words: what solid means, how reviews are spaced, what unlocks a level")
		String rule,
		SummaryView summary,
		List<LevelView> levels,
		@Schema(description = "Every concept, in the curriculum's order") List<ConceptProgressView> concepts,
		@Schema(description = "The next concept to work on: the first open one not yet complete") String suggested,
		@Schema(description = "Rated concepts due for review today, in the curriculum's order") List<String> due) {
	}

	public record SummaryView(
		int concepts,
		@Schema(description = "Solid, or done") int complete,
		@Schema(description = "Concepts a drill grades against a rule") int rated,
		int solid,
		int due,
		@Schema(description = "Graded marking and naming drills answered") int ratedAttempts,
		@Schema(description = "Prediction drills answered: practice, never a rating") int predictions) {
	}

	public record LevelView(
		int level,
		String theme,
		@Schema(description = "Suggested now. A level not open is still there to read: the ladder never locks")
		boolean open,
		@Schema(description = "Every concept in it solid or done") boolean complete,
		@Schema(description = "When it opens, in the curriculum's words") String unlocksWhen) {
	}

	public record ConceptProgressView(
		String id,
		@Schema(description = "A drill grades it against a rule (marking or naming)") boolean rated,
		@Schema(description = "NEW | LEARNING | SOLID for a rated concept; UNREAD | DONE for one no drill rates")
		String state,
		@Schema(description = "Graded attempts") int attempts,
		@Schema(description = "How many of them the recent accuracy is over: the last 20 at most") int recentCount,
		@Schema(description = "Mean score of the recent attempts, 0 to 1") Double recentAccuracy,
		@Schema(description = "Prediction drills answered on it") int practised,
		@Schema(description = "When its card was marked read, epoch seconds") Long readAt,
		@Schema(description = "The last drill on it, epoch seconds") Long lastPractised,
		@Schema(description = "The day it is next due for review (UTC), ISO-8601") String nextReview,
		boolean due,
		@Schema(description = "Its level is open (and, in level 8, the concepts it builds on are complete)")
		boolean open) {
	}
}
