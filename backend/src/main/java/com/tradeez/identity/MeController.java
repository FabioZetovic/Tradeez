package com.tradeez.identity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradeez.identity.JwtAuthenticationFilter.AuthenticatedUser;
import com.tradeez.identity.dto.AuthDtos.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The signed-in user's own profile.
 *
 * <p>Note that the id comes from the authenticated principal, never from a request parameter. This
 * is the pattern every user-scoped endpoint in the project must follow (DEC-022): a client asking
 * for "user X's practice sessions" must never be able to name X.
 */
@RestController
@RequestMapping("/api/me")
@Tag(name = "Authentication", description = "Registration, login, token rotation and logout")
public class MeController {

	private final AppUserRepository users;

	public MeController(AppUserRepository users) {
		this.users = users;
	}

	@GetMapping
	@Operation(summary = "The currently signed-in user")
	public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
		AppUser user = users.findById(principal.id()).orElseThrow();
		return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
			user.getCreatedAt());
	}
}
