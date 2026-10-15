package com.tradeez.curriculum;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradeez.analysis.DetectorRegistry;
import com.tradeez.analysis.SetupDtos.RuleView;
import com.tradeez.curriculum.ConceptRegistry.Concept;
import com.tradeez.curriculum.ConceptRegistry.Level;

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

	public ConceptController(ConceptRegistry concepts, DetectorRegistry detectors) {
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
}
