package com.tradeez.practice;

import java.time.Instant;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Scoped to {@link PracticeController}: the database exceptions mapped here mean something specific
 * in this slice, and must not change how other controllers report theirs.
 */
@RestControllerAdvice(assignableTypes = PracticeController.class)
public class PracticeExceptionHandler {

	@ExceptionHandler(PracticeService.SessionNotFoundException.class)
	public ResponseEntity<Map<String, Object>> notFound(RuntimeException e) {
		return body(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler({ PracticeService.PlanLockedException.class,
		PracticeService.MarketClosedException.class,
		PracticeService.ReplayEndException.class,
		PracticeService.NotInTradeException.class })
	public ResponseEntity<Map<String, Object>> conflict(RuntimeException e) {
		return body(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler(PracticeService.TooManySessionsException.class)
	public ResponseEntity<Map<String, Object>> tooMany(RuntimeException e) {
		return body(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
	}

	/**
	 * Two requests raced on one session — a double-clicked button, or the same session open in two
	 * tabs. Caught either by the service's own version check between its read and its write
	 * (BUG-033), or by the {@code @Version} column at flush; exactly one request gets through.
	 */
	@ExceptionHandler({ PracticeService.SessionChangedException.class,
		ObjectOptimisticLockingFailureException.class })
	public ResponseEntity<Map<String, Object>> raced() {
		return body(HttpStatus.CONFLICT, new PracticeService.SessionChangedException().getMessage());
	}

	/**
	 * The database refused a write: in this slice, a second plan for one session slipping past the
	 * service's check in a race (unique constraint), or an attempt to alter a locked row (trigger).
	 * Either way the answer is the same one the service gives when it catches the case itself.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, Object>> refused() {
		return body(HttpStatus.CONFLICT, new PracticeService.PlanLockedException().getMessage());
	}

	private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of(
			"status", status.value(),
			"message", message,
			"timestamp", Instant.now().toString()));
	}
}
