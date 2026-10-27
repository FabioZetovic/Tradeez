package com.tradeez.learning;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Wire shapes for the learning state (T16, DEC-059). */
public final class LearningDtos {

	private LearningDtos() {
	}

	@Schema(description = "Where the learner stands in Learn, worked out from their graded drills, risk questions and the "
		+ "cards they have read, under the rule named")
	public record LearningResponse(
		@Schema(description = "learning-1.1.0") String version,
		@Schema(description = "The rule in words: what passing a lesson means, what unlocks the next, how reviews are spaced")
		String rule,
		SummaryView summary,
		List<TopicView> topics,
		@Schema(description = "Every lesson and reference card, topic by topic in the order taught") List<ConceptProgressView> concepts,
		@Schema(description = "The next lesson: the first open, incomplete one of the furthest topic started") String suggested,
		@Schema(description = "Drilled lessons due for review today, in the order taught") List<String> due) {
	}

	public record SummaryView(
		int concepts,
		@Schema(description = "Solid, or done") int complete,
		@Schema(description = "Lessons a drill practises") int drilled,
		int solid,
		int due,
		@Schema(description = "Graded marking and naming drills answered") int ratedAttempts,
		@Schema(description = "Predictions, managed trades and risk questions answered") int predictions) {
	}

	public record TopicView(
		String title,
		@Schema(description = "Its lessons and reference cards, in the order taught") List<String> concepts,
		@Schema(description = "How many of them are passed or done") int complete) {
	}

	public record ConceptProgressView(
		String id,
		@Schema(description = "LESSON or REFERENCE") String kind,
		String topic,
		@Schema(description = "A drill practises it") boolean drilled,
		@Schema(description = "NEW | LEARNING | SOLID (passed) for a drilled lesson; UNREAD | DONE for one without a drill")
		String state,
		@Schema(description = "Its drills answered") int attempts,
		@Schema(description = "How many of them the recent accuracy is over: the last 20 at most") int recentCount,
		@Schema(description = "Mean score of the recent attempts, 0 to 1") Double recentAccuracy,
		@Schema(description = "Of them, predictions, managed trades and risk questions") int practised,
		@Schema(description = "When its card was marked read, epoch seconds") Long readAt,
		@Schema(description = "The last drill on it, epoch seconds") Long lastPractised,
		@Schema(description = "The day it is next due for review (UTC), ISO-8601") String nextReview,
		boolean due,
		@Schema(description = "Open: a reference card, the first lesson of its topic, or the lesson before it is passed")
		boolean open) {
	}
}
