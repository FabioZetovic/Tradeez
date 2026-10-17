package com.tradeez.identity;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps identity failures to responses.
 *
 * <p>The messages are deliberately vague. "Invalid email or password" is returned whether the
 * account is absent or the password is wrong, so the endpoint cannot be used to enumerate which
 * email addresses have accounts. {@link AuthService#login} additionally hashes a throwaway password
 * when the account does not exist, so response <i>timing</i> does not leak the same information
 * that the message withholds.
 */
@RestControllerAdvice
public class AuthExceptionHandler {

	@ExceptionHandler(AuthService.EmailAlreadyRegisteredException.class)
	public ResponseEntity<Map<String, Object>> emailTaken() {
		// A registration endpoint cannot avoid revealing that an address is taken. Rate limiting is
		// the mitigation here, not vagueness. Noted as a follow-up rather than pretended away.
		return body(HttpStatus.CONFLICT, "That email address is already registered.");
	}

	@ExceptionHandler(AuthService.InvalidCredentialsException.class)
	public ResponseEntity<Map<String, Object>> badCredentials() {
		return body(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
	}

	@ExceptionHandler(AuthService.InvalidRefreshTokenException.class)
	public ResponseEntity<Map<String, Object>> badRefresh() {
		return body(HttpStatus.UNAUTHORIZED, "Your session has expired. Please sign in again.");
	}

	@ExceptionHandler(AuthService.TokenReuseDetectedException.class)
	public ResponseEntity<Map<String, Object>> reuse() {
		// Same status and shape as an ordinary expiry: an attacker holding a stolen token learns
		// nothing from the response about whether the theft was detected.
		return body(HttpStatus.UNAUTHORIZED, "Your session has expired. Please sign in again.");
	}

	@ExceptionHandler(PasswordPolicy.WeakPasswordException.class)
	public ResponseEntity<Map<String, Object>> weakPassword(PasswordPolicy.WeakPasswordException e) {
		return ResponseEntity.badRequest().body(Map.of(
			"status", HttpStatus.BAD_REQUEST.value(),
			"message", e.getMessage(),
			"problems", e.problems(),
			"timestamp", Instant.now().toString()));
	}

	@ExceptionHandler(AuthService.WrongCurrentPasswordException.class)
	public ResponseEntity<Map<String, Object>> wrongCurrentPassword() {
		// 403, not 401: the session is fine, and a 401 would send the client off to refresh it and retry.
		return body(HttpStatus.FORBIDDEN, "Your current password is not right.");
	}

	@ExceptionHandler(AuthService.TooManyAttemptsException.class)
	public ResponseEntity<Map<String, Object>> throttled() {
		return body(HttpStatus.TOO_MANY_REQUESTS,
			"Too many attempts. Please wait a few minutes and try again.");
	}

	private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of(
			"status", status.value(),
			"message", message,
			"timestamp", Instant.now().toString()));
	}
}
