package com.tradeez.drills;

import java.time.Instant;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Scoped to {@link DrillController}, like the practice handler, for the same reason. */
@RestControllerAdvice(assignableTypes = DrillController.class)
public class DrillExceptionHandler {

	@ExceptionHandler(DrillService.DrillNotFoundException.class)
	public ResponseEntity<Map<String, Object>> notFound(RuntimeException e) {
		return body(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(DrillService.DrillAnsweredException.class)
	public ResponseEntity<Map<String, Object>> answered(RuntimeException e) {
		return body(HttpStatus.CONFLICT, e.getMessage());
	}

	/**
	 * Two answers racing: both read the drill unanswered, and the database trigger refuses the second
	 * write. Same answer as when the service catches it first.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, Object>> refused() {
		return body(HttpStatus.CONFLICT, new DrillService.DrillAnsweredException().getMessage());
	}

	@ExceptionHandler(DrillService.TooManyDrillsException.class)
	public ResponseEntity<Map<String, Object>> tooMany(RuntimeException e) {
		return body(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
	}

	@ExceptionHandler(DrillService.NoWindowFoundException.class)
	public ResponseEntity<Map<String, Object>> noWindow(RuntimeException e) {
		return body(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
	}

	private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of(
			"status", status.value(),
			"message", message,
			"timestamp", Instant.now().toString()));
	}
}
