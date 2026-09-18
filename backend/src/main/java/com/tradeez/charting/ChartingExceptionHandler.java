package com.tradeez.charting;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ChartingExceptionHandler {

	@ExceptionHandler(ChartDrawingController.DrawingNotYoursException.class)
	public ResponseEntity<Map<String, Object>> notYours() {
		// Deliberately identical to "no such drawing". Saying "that one is not yours" would confirm
		// the id exists, turning this endpoint into an oracle for enumerating other users' drawings.
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
			"status", HttpStatus.NOT_FOUND.value(),
			"message", "No such drawing.",
			"timestamp", Instant.now().toString()));
	}

	@ExceptionHandler(ChartDrawingController.TooManyDrawingsException.class)
	public ResponseEntity<Map<String, Object>> tooMany(RuntimeException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
			"status", HttpStatus.CONFLICT.value(),
			"message", e.getMessage(),
			"timestamp", Instant.now().toString()));
	}
}
