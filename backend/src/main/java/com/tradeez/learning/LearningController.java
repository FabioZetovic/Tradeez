package com.tradeez.learning;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradeez.curriculum.LessonService.ConceptNotFoundException;
import com.tradeez.identity.JwtAuthenticationFilter.AuthenticatedUser;
import com.tradeez.learning.LearningDtos.LearningResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The signed-in learner's progress through the curriculum (T16, DEC-059). Their own, always (DEC-022). */
@RestController
@RequestMapping("/api/learning")
@Tag(name = "Learning", description = "Progress through the curriculum, from graded drills on real data")
public class LearningController {

	private final LearningService learning;

	public LearningController(LearningService learning) {
		this.learning = learning;
	}

	@GetMapping
	@Operation(summary = "Where the learner stands: every concept's state, what is due, what to do next",
		description = "Worked out on every request from graded drills (real data only, DEC-034) and cards read, "
			+ "under learning-1.0.0. The ladder suggests; it never locks.")
	public LearningResponse state(@AuthenticationPrincipal AuthenticatedUser principal) {
		return learning.state(principal.id());
	}

	@PostMapping("/concepts/{id}/read")
	@Operation(summary = "Mark a concept's card as read",
		description = "Once: reading it again changes nothing. What completes a concept no drill rates.")
	public LearningResponse read(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
		return learning.read(principal.id(), id);
	}

	@ExceptionHandler(ConceptNotFoundException.class)
	ResponseEntity<Map<String, Object>> notFound(ConceptNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
			"status", HttpStatus.NOT_FOUND.value(),
			"message", e.getMessage(),
			"timestamp", Instant.now().toString()));
	}
}
