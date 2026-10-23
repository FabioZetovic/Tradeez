package com.tradeez.common;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Requests the framework refuses before any controller has run (BUG-078, BUG-083): a body that is not JSON or does not
 * fit, a parameter of the wrong type or missing, the wrong method or content type.
 *
 * <p>Left to Spring, each was forwarded to {@code /error} — which came back as an empty 401, so the client took a
 * mistake in the request for an expired session (BUG-078) — or answered in the framework's own words, naming the
 * server's Java types (BUG-083). Answered here first, in words about the request.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestErrorHandler {

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
		String cause = e.getMostSpecificCause().getMessage();
		return body(HttpStatus.BAD_REQUEST, cause != null && cause.contains(NoNulStrings.REFUSED) ? NoNulStrings.REFUSED
			: "The request body could not be read: it is not JSON, or a value does not fit its field.");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<Map<String, Object>> mismatch(MethodArgumentTypeMismatchException e) {
		return body(HttpStatus.BAD_REQUEST, "'" + e.getName() + "' must be " + expected(e.getRequiredType()) + ".");
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<Map<String, Object>> missing(MissingServletRequestParameterException e) {
		return body(HttpStatus.BAD_REQUEST, "'" + e.getParameterName() + "' is required.");
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<Map<String, Object>> method(HttpRequestMethodNotSupportedException e) {
		return body(HttpStatus.METHOD_NOT_ALLOWED, "This address does not take " + e.getMethod() + ".");
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<Map<String, Object>> mediaType() {
		return body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Send the body as JSON (Content-Type: application/json).");
	}

	/** What a parameter should have been, in words. */
	static String expected(Class<?> type) {
		if (type == null) {
			return "something else";
		}
		if (type == long.class || type == Long.class || type == int.class || type == Integer.class) {
			return "a whole number";
		}
		if (type == double.class || type == Double.class) {
			return "a number";
		}
		if (type == UUID.class) {
			return "an id";
		}
		if (type.isEnum()) {
			return "one of " + Arrays.toString(type.getEnumConstants());
		}
		return "something else";
	}

	private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of(
			"status", status.value(),
			"message", message,
			"timestamp", Instant.now().toString()));
	}
}
