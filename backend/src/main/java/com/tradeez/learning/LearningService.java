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
import com.tradeez.curriculum.LessonService.ConceptNotFoundException;
import com.tradeez.drills.DrillAttempts;
import com.tradeez.learning.LearningDtos.ConceptProgressView;
import com.tradeez.learning.LearningDtos.LearningResponse;
import com.tradeez.learning.LearningDtos.LevelView;
import com.tradeez.learning.LearningDtos.SummaryView;
import com.tradeez.learning.LearningRule.Attempt;
import com.tradeez.learning.LearningRule.Placed;
import com.tradeez.learning.LearningRule.Progress;

/**
 * A learner's place in the curriculum (T16, DEC-059), worked out afresh on every request from the
 * records that never change — graded drills and cards read — under {@code learning-1.0.0}. Nothing about
 * progress is stored, so nothing about it can drift from what the learner actually did.
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
		Map<String, Progress> progress = new LinkedHashMap<>();
		Map<String, Placed> placed = new LinkedHashMap<>();
		for (Concept c : concepts.concepts()) {
			progress.put(c.id(), LearningRule.progress(c.id(), DrillAttempts.rates(c.id()), record, reads.get(c.id()), today));
			placed.put(c.id(), new Placed(c.id(), c.level(), c.prerequisites()));
		}
		Map<Integer, Boolean> open = LearningRule.openLevels(placed.values(), progress);

		List<LevelView> levels = concepts.levels().stream()
			.map(l -> new LevelView(l.level(), l.theme(), open.getOrDefault(l.level(), false),
				LearningRule.complete(l.level(), placed.values(), progress), l.unlocksWhen()))
			.toList();
		List<ConceptProgressView> views = progress.values().stream()
			.map(p -> view(p, LearningRule.open(placed.get(p.conceptId()), open, progress)))
			.toList();
		// The next step: the first concept of the ladder, in its order, that is open and not yet complete.
		String suggested = views.stream().filter(v -> v.open() && !progress.get(v.id()).complete())
			.map(ConceptProgressView::id).findFirst().orElse(null);
		List<String> due = views.stream().filter(ConceptProgressView::due).map(ConceptProgressView::id).toList();

		SummaryView summary = new SummaryView(views.size(), (int) progress.values().stream().filter(Progress::complete).count(),
			(int) progress.values().stream().filter(Progress::rated).count(),
			(int) progress.values().stream().filter(p -> p.state() == LearningRule.State.SOLID).count(), due.size(),
			(int) record.stream().filter(Attempt::rated).count(), (int) record.stream().filter(a -> !a.rated()).count());
		return new LearningResponse(LearningRule.VERSION, LearningRule.definition(), summary, levels, views, suggested,
			due);
	}

	/** Marks a card read, once: reading it again changes nothing. */
	public LearningResponse read(UUID userId, String conceptId) {
		if (concepts.concepts().stream().noneMatch(c -> c.id().equals(conceptId))) {
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

	private static ConceptProgressView view(Progress p, boolean open) {
		return new ConceptProgressView(p.conceptId(), p.rated(), p.state().name(), p.attempts(), p.recentCount(),
			p.recentAccuracy() == null ? null : Math.round(p.recentAccuracy() * 1000) / 1000.0, p.practised(),
			epoch(p.readAt()), epoch(p.lastPractised()), p.nextReview() == null ? null : p.nextReview().toString(),
			p.due(), open);
	}

	private static Long epoch(Instant instant) {
		return instant == null ? null : instant.getEpochSecond();
	}

}
