package com.tradeez.curriculum;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Scoped to {@link ConceptController}, like the other modules' handlers. */
@RestControllerAdvice(assignableTypes = ConceptController.class)
public class CurriculumExceptionHandler {

	@ExceptionHandler(LessonService.ConceptNotFoundException.class)
	public ResponseEntity<Map<String, Object>> notFound(RuntimeException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
			"status", HttpStatus.NOT_FOUND.value(),
			"message", e.getMessage(),
			"timestamp", Instant.now().toString()));
	}
}
