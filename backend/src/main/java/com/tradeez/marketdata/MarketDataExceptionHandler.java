package com.tradeez.marketdata;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MarketDataExceptionHandler {

	@ExceptionHandler(MarketDataService.InstrumentNotFoundException.class)
	public ResponseEntity<Map<String, Object>> notFound(RuntimeException e) {
		return body(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler({ MarketDataService.InvalidRangeException.class,
		IllegalArgumentException.class })
	public ResponseEntity<Map<String, Object>> badRequest(RuntimeException e) {
		return body(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of(
			"status", status.value(),
			"message", message,
			"timestamp", Instant.now().toString()));
	}
}
