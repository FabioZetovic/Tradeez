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
		@NotBlank @Size(min = 12, max = 200)
		@Schema(description = "Minimum 12 characters. Length beats complexity rules.")
		String password,
		@NotBlank @Size(min = 1, max = 60) String displayName) {
	}

	@Schema(description = "Credentials for an existing account")
	public record LoginRequest(
		@NotBlank @Email String email,
		@NotBlank String password) {
	}

	@Schema(description = "The authenticated user. Tokens are in HttpOnly cookies, never here.")
	public record UserResponse(
		UUID id,
		String email,
		String displayName,
		Instant createdAt) {
	}
}
