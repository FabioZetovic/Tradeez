package com.tradeez.curriculum;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradeez.analysis.DetectorRegistry;
import com.tradeez.analysis.SetupDtos.RuleView;
import com.tradeez.curriculum.ConceptRegistry.Concept;
import com.tradeez.curriculum.ConceptRegistry.Level;
import com.tradeez.curriculum.LessonDtos.LessonResponse;
import com.tradeez.identity.JwtAuthenticationFilter.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/concepts")
@Tag(name = "Curriculum", description = "The ICT concepts Tradeez teaches")
public class ConceptController {

	@Schema(description = "The curriculum of ICT_CONCEPTS.md (S-2), with the detectors that implement each concept")
	public record CurriculumResponse(List<Level> levels, List<ConceptView> concepts) {
	}

	public record ConceptView(Concept concept, List<RuleView> detectors) {
	}

	private final CurriculumResponse curriculum;
	private final LessonService lessons;

	public ConceptController(ConceptRegistry concepts, DetectorRegistry detectors, LessonService lessons) {
		this.lessons = lessons;
		this.curriculum = new CurriculumResponse(concepts.levels(), concepts.concepts().stream()
			.map(c -> new ConceptView(c, detectors.all().stream()
				.filter(d -> d.conceptId().equals(c.id()))
				.map(RuleView::of)
				.toList()))
			.toList());
	}

	@GetMapping
	@Operation(summary = "Every concept, its level, rules and summary, and the curriculum ladder",
		description = "Read from docs/ICT_CONCEPTS.md at startup (T9, DEC-049).")
	public CurriculumResponse concepts() {
		return curriculum;
	}

	@GetMapping("/{id}/lesson")
	@Operation(summary = "A concept's lesson card",
		description = "What it is, from ICT_CONCEPTS.md; its rules in words; the drills that practise it; and up to "
			+ "three real NQ examples its rule found at random — never hand-picked, different each time (T14, DEC-055).")
	public LessonResponse lesson(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id,
			@RequestParam(required = false) String timeframe) {
		return lessons.lesson(principal.id(), id, timeframe);
	}
}
