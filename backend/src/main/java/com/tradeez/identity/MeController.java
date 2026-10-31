package com.tradeez.identity;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradeez.identity.JwtAuthenticationFilter.AuthenticatedUser;
import com.tradeez.identity.dto.AuthDtos.DeleteAccountRequest;
import com.tradeez.identity.dto.AuthDtos.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

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
	private final AuthService auth;
	private final AccountExport export;
	private final CookieService cookies;

	public MeController(AppUserRepository users, AuthService auth, AccountExport export, CookieService cookies) {
		this.users = users;
		this.auth = auth;
		this.export = export;
		this.cookies = cookies;
	}

	@GetMapping
	@Operation(summary = "The currently signed-in user")
	public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
		AppUser user = users.findById(principal.id()).orElseThrow();
		return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
			user.getCreatedAt());
	}

	@GetMapping("/export")
	@Operation(summary = "Everything the app holds about the signed-in account, as JSON (BUG-120)",
		description = "Downloaded as a file. Leaves out only the password's hash and the sign-in tokens' hashes.")
	public ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal AuthenticatedUser principal) {
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
				.filename("tradeez-data-" + LocalDate.now(ZoneOffset.UTC) + ".json").build().toString())
			// Personal data: never kept by the browser's cache or anything between.
			.cacheControl(CacheControl.noStore())
			.body(export.of(principal.id()));
	}

	@DeleteMapping
	@Operation(summary = "Delete the signed-in account and everything in it, for good (BUG-120)",
		description = "Needs the current password. Signs out everywhere: the account no longer exists.")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody DeleteAccountRequest request) {
		auth.deleteAccount(principal.id(), request.password());
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, cookies.clearAccessCookie().toString())
			.header(HttpHeaders.SET_COOKIE, cookies.clearRefreshCookie().toString())
			.build();
	}
}
