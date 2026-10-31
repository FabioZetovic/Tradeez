package com.tradeez.identity.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request and response payloads for the identity endpoints.
 *
 * <p>DEC-007 — these shapes are published through OpenAPI and the frontend generates its
 * TypeScript types from them. Never hand-write a matching interface on the client.
 *
 * <p>Note what is absent: no token ever appears in a response body. Tokens travel only in
 * HttpOnly cookies (DEC-032), so there is no code path by which JavaScript could store one.
 */
public final class AuthDtos {

	private AuthDtos() {
	}

	@Schema(description = "New account registration")
	public record RegisterRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(max = MAX_PASSWORD_INPUT)
		@Schema(description = """
			password-1.0.0 (DEC-064): 12 characters to 72 bytes, at least 5 different characters and not a 			straight run, without the email name, display name or app name, not a common password and not in 			a known breach. Length beats complexity rules.""")
		String password,
		@NotBlank @Size(min = 1, max = 60) String displayName) {
	}

	@Schema(description = "Credentials for an existing account")
	public record LoginRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(max = MAX_PASSWORD_INPUT) String password) {
	}

	@Schema(description = "Deleting the signed-in account: its current password, so a borrowed session cannot (BUG-120)")
	public record DeleteAccountRequest(@NotBlank @Size(max = MAX_PASSWORD_INPUT) String password) {
	}

	@Schema(description = "A new password for the signed-in account (DEC-064)")
	public record ChangePasswordRequest(
		@NotBlank @Size(max = MAX_PASSWORD_INPUT) String currentPassword,
		@NotBlank @Size(max = MAX_PASSWORD_INPUT)
		@Schema(description = "password-1.0.0, as for registration; not the current one")
		String newPassword) {
	}

	/**
	 * What a password field accepts at all: far past what the policy allows, so the policy can say what is
	 * wrong, and short enough that no request body is spent on one.
	 */
	public static final int MAX_PASSWORD_INPUT = 1000;

	@Schema(description = "The authenticated user. Tokens are in HttpOnly cookies, never here.")
	public record UserResponse(
		UUID id,
		String email,
		String displayName,
		Instant createdAt) {
	}
}
