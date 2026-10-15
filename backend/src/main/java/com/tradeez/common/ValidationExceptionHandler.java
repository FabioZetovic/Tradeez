package com.tradeez.common;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * A request body that fails validation, for every controller (BUG-054).
 *
 * <p>Left to Spring's default, a validation failure is logged at WARN with each <i>rejected value</i>
 * in it — and for registration that value is the password someone typed, quite possibly one they use
 * elsewhere. Handled here, nothing is logged, and the reply names the field and the rule it broke,
 * never the value.
 */
@RestControllerAdvice
public class ValidationExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
			.map(ValidationExceptionHandler::describe)
			.sorted()
			.collect(Collectors.joining("; "));
		return ResponseEntity.badRequest().body(Map.of(
			"status", HttpStatus.BAD_REQUEST.value(),
			"message", message.isEmpty() ? "The request is not valid." : message,
			"timestamp", Instant.now().toString()));
	}

	private static String describe(FieldError error) {
		return error.getField() + ": " + error.getDefaultMessage();
	}
}
