package com.tradeez.identity;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads the access token from its HttpOnly cookie and populates the security context.
 *
 * <p>DEC-032 — the cookie is the <b>only</b> place this filter looks. There is deliberately no
 * {@code Authorization: Bearer} fallback: supporting one would mean a token could live somewhere
 * JavaScript can reach, which is the exact failure mode the cookie approach exists to prevent.
 *
 * <p>An invalid or expired token is treated identically to no token at all. The request simply
 * proceeds unauthenticated and the authorization rules reject it, so this filter never
 * distinguishes "bad token" from "no token" to the caller.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;
	private final AppUserRepository users;
	private final RevokedAccessTokenRepository revoked;

	public JwtAuthenticationFilter(JwtService jwtService, AppUserRepository users, RevokedAccessTokenRepository revoked) {
		this.jwtService = jwtService;
		this.users = users;
		this.revoked = revoked;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {

		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			readCookie(request, CookieService.ACCESS_TOKEN_COOKIE)
				.flatMap(jwtService::read)
				// BUG-126: a token signed out before its expiry is over.
				.filter(token -> token.jti() == null || !revoked.existsById(token.jti()))
				// BUG-077: a token issued before "sign out everywhere" or a password change is over, whatever its expiry.
				.flatMap(token -> users.findById(token.userId())
					.filter(u -> u.getTokenGeneration() == token.generation()))
				.ifPresent(user -> {
					var authentication = new UsernamePasswordAuthenticationToken(
						new AuthenticatedUser(user.getId(), user.getEmail()),
						null,
						List.of());
					authentication.setDetails(
						new WebAuthenticationDetailsSource().buildDetails(request));
					SecurityContextHolder.getContext().setAuthentication(authentication);
				});
		}

		chain.doFilter(request, response);
	}

	static java.util.Optional<String> readCookie(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return java.util.Optional.empty();
		}
		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName()) && cookie.getValue() != null
					&& !cookie.getValue().isBlank()) {
				return java.util.Optional.of(cookie.getValue());
			}
		}
		return java.util.Optional.empty();
	}

	/** The authenticated principal. Carries the user id so controllers never trust a client id. */
	public record AuthenticatedUser(java.util.UUID id, String email) {
	}
}
