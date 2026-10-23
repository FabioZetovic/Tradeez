package com.tradeez.identity;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradeez.identity.AuthService.IssuedTokens;
import com.tradeez.identity.JwtAuthenticationFilter.AuthenticatedUser;
import com.tradeez.identity.dto.AuthDtos.ChangePasswordRequest;
import com.tradeez.identity.dto.AuthDtos.LoginRequest;
import com.tradeez.identity.dto.AuthDtos.RegisterRequest;
import com.tradeez.identity.dto.AuthDtos.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Identity endpoints. DEC-032.
 *
 * <p>Every success response sets cookies and returns the user; <b>no response body ever contains a
 * token</b>. Failures are mapped in {@link AuthExceptionHandler} to deliberately vague messages so
 * the API does not become an account-enumeration oracle.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration, login, token rotation and logout")
public class AuthController {

	private final AuthService authService;
	private final CookieService cookies;
	private final ClientAddress clientAddress;

	public AuthController(AuthService authService, CookieService cookies, ClientAddress clientAddress) {
		this.authService = authService;
		this.cookies = cookies;
		this.clientAddress = clientAddress;
	}

	@PostMapping("/register")
	@Operation(summary = "Create an account and sign in")
	public ResponseEntity<UserResponse> register(
			@Valid @RequestBody RegisterRequest request,
			@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
		return respond(authService.register(request, userAgent), HttpStatus.CREATED);
	}

	@PostMapping("/login")
	@Operation(summary = "Sign in with email and password")
	public ResponseEntity<UserResponse> login(
			@Valid @RequestBody LoginRequest request,
			HttpServletRequest http,
			@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
		return respond(authService.login(request, userAgent, clientAddress.of(http)), HttpStatus.OK);
	}

	@PostMapping("/refresh")
	@Operation(summary = "Rotate the refresh token and issue a new access token",
		description = "The presented refresh token is revoked. Replaying an already-rotated token "
			+ "revokes every session for that account, because it indicates the token was stolen.")
	public ResponseEntity<UserResponse> refresh(
			HttpServletRequest request,
			@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
		String raw = JwtAuthenticationFilter
			.readCookie(request, CookieService.REFRESH_TOKEN_COOKIE)
			.orElseThrow(AuthService.InvalidRefreshTokenException::new);
		IssuedTokens issued;
		try {
			issued = authService.refresh(raw, userAgent);
		} catch (ObjectOptimisticLockingFailureException lostTheRace) {
			// BUG-034. Two refreshes of the same token at the same instant — a page load that checks
			// the session twice, two tabs — both read it unrotated, and the version column lets only
			// one rotation commit. BUG-001's grace window covers a duplicate that arrives *after* the
			// winner commits; this one arrived during it, failed the version check, and surfaced as a
			// 401 that signed the user out. By the time the failure is thrown the winner has
			// committed, so trying once more lands in the grace window and gets fresh tokens.
			issued = authService.refresh(raw, userAgent);
		}
		return respond(issued, HttpStatus.OK);
	}

	@PostMapping("/password")
	@Operation(summary = "Change the signed-in account's password",
		description = "Needs the current password; the new one must pass password-1.0.0 (DEC-064). Signs out "
			+ "every other session and renews this one's cookies. Throttled per account and per address.")
	public ResponseEntity<UserResponse> changePassword(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody ChangePasswordRequest request,
			@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
		return respond(authService.changePassword(user.id(), request, userAgent), HttpStatus.OK);
	}

	@PostMapping("/logout")
	@Operation(summary = "Revoke the current session's refresh token and clear cookies")
	public ResponseEntity<Void> logout(HttpServletRequest request) {
		JwtAuthenticationFilter.readCookie(request, CookieService.REFRESH_TOKEN_COOKIE)
			.ifPresent(authService::logout);

		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, cookies.clearAccessCookie().toString())
			.header(HttpHeaders.SET_COOKIE, cookies.clearRefreshCookie().toString())
			.build();
	}

	@PostMapping("/logout-all")
	@Operation(summary = "Revoke every refresh token for the signed-in account")
	public ResponseEntity<Void> logoutEverywhere(@AuthenticationPrincipal AuthenticatedUser user) {
		authService.logoutEverywhere(user.id());
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, cookies.clearAccessCookie().toString())
			.header(HttpHeaders.SET_COOKIE, cookies.clearRefreshCookie().toString())
			.build();
	}

	private ResponseEntity<UserResponse> respond(IssuedTokens issued, HttpStatus status) {
		AppUser user = issued.user();
		return ResponseEntity.status(status)
			.header(HttpHeaders.SET_COOKIE, cookies.accessCookie(issued.accessToken()).toString())
			.header(HttpHeaders.SET_COOKIE, cookies.refreshCookie(issued.refreshToken()).toString())
			.body(new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
				user.getCreatedAt()));
	}
}
