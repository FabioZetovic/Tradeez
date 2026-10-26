package com.tradeez.learning;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.tradeez.curriculum.ConceptRegistry;
import com.tradeez.curriculum.ConceptRegistry.Concept;
import com.tradeez.curriculum.ConceptRegistry.Kind;
import com.tradeez.curriculum.LessonService.ConceptNotFoundException;
import com.tradeez.drills.DrillAttempts;
import com.tradeez.learning.LearningDtos.ConceptProgressView;
import com.tradeez.learning.LearningDtos.LearningResponse;
import com.tradeez.learning.LearningDtos.SummaryView;
import com.tradeez.learning.LearningDtos.TopicView;
import com.tradeez.learning.LearningRule.Attempt;
import com.tradeez.learning.LearningRule.Progress;
import com.tradeez.learning.LearningRule.Topic;

/**
 * A learner's place in Learn (T16, DEC-059; topics and locks T45, DEC-082), worked out afresh on every request from
 * the records that never change — graded drills, risk questions answered and cards read — under
 * {@code learning-1.1.0}. Nothing about progress is stored, so nothing about it can drift from what the learner
 * actually did.
 */
@Service
public class LearningService {

	private final ConceptRegistry concepts;
	private final DrillAttempts attempts;
	private final JdbcTemplate jdbc;

	public LearningService(ConceptRegistry concepts, DrillAttempts attempts, JdbcTemplate jdbc) {
		this.concepts = concepts;
		this.attempts = attempts;
		this.jdbc = jdbc;
	}

	public LearningResponse state(UUID userId) {
		return state(userId, LocalDate.now(ZoneOffset.UTC));
	}

	LearningResponse state(UUID userId, LocalDate today) {
		List<Attempt> record = attempts.of(userId);
		Map<String, Instant> reads = reads(userId);
		Map<String, Concept> byId = new LinkedHashMap<>();
		concepts.concepts().forEach(c -> byId.put(c.id(), c));

		List<Topic> topics = concepts.topics().stream().map(t -> {
			Map<String, Boolean> lessons = new LinkedHashMap<>();
			t.concepts().forEach(id -> lessons.put(id, byId.get(id).kind() == Kind.LESSON));
			return new Topic(t.title(), t.concepts(), lessons);
		}).toList();
		// Pages in the order taught: topic by topic.
		Map<String, Progress> progress = new LinkedHashMap<>();
		for (Topic topic : topics) {
			for (String id : topic.concepts()) {
				boolean drilled = topic.lessons().get(id) && DrillAttempts.drills(id);
				progress.put(id, LearningRule.progress(id, drilled, record, reads.get(id), today));
			}
		}
		Map<String, Boolean> open = LearningRule.open(topics, progress);

		List<TopicView> topicViews = topics.stream().map(t -> new TopicView(t.title(), t.concepts(),
			(int) t.concepts().stream().filter(id -> progress.get(id).complete()).count())).toList();
		List<ConceptProgressView> views = progress.values().stream()
			.map(p -> view(p, byId.get(p.conceptId()), open.get(p.conceptId())))
			.toList();
		String suggested = LearningRule.suggested(topics, progress, open);
		List<String> due = views.stream().filter(ConceptProgressView::due).map(ConceptProgressView::id).toList();

		SummaryView summary = new SummaryView(views.size(), (int) progress.values().stream().filter(Progress::complete).count(),
			(int) progress.values().stream().filter(Progress::drilled).count(),
			(int) progress.values().stream().filter(p -> p.state() == LearningRule.State.SOLID).count(), due.size(),
			(int) record.stream().filter(Attempt::rated).count(), (int) record.stream().filter(a -> !a.rated()).count());
		return new LearningResponse(LearningRule.VERSION, LearningRule.definition(), summary, topicViews, views, suggested,
			due);
	}

	/** Marks a card read, once: reading it again changes nothing. */
	public LearningResponse read(UUID userId, String conceptId) {
		if (concepts.concepts().stream().noneMatch(c -> c.id().equals(conceptId) && c.taught())) {
			throw new ConceptNotFoundException(conceptId);
		}
		jdbc.update("INSERT INTO concept_read (user_id, concept_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
			userId, conceptId);
		return state(userId);
	}

	private Map<String, Instant> reads(UUID userId) {
		Map<String, Instant> reads = new HashMap<>();
		jdbc.query("SELECT concept_id, read_at FROM concept_read WHERE user_id = ?",
			rs -> {
				reads.put(rs.getString(1), rs.getTimestamp(2).toInstant());
			}, userId);
		return reads;
	}

	private static ConceptProgressView view(Progress p, Concept concept, boolean open) {
		return new ConceptProgressView(p.conceptId(), concept.kind().name(), concept.topic(), p.drilled(), p.state().name(),
			p.attempts(), p.recentCount(), p.recentAccuracy() == null ? null : Math.round(p.recentAccuracy() * 1000) / 1000.0,
			p.practised(), epoch(p.readAt()), epoch(p.lastPractised()),
			p.nextReview() == null ? null : p.nextReview().toString(), p.due(), open);
	}

	private static Long epoch(Instant instant) {
		return instant == null ? null : instant.getEpochSecond();
	}

}
