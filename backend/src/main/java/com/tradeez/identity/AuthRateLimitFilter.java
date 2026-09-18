package com.tradeez.identity;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Throttles authentication attempts per client IP (BUG-002).
 *
 * <p>Runs ahead of Spring Security so a flood of guesses is rejected before any password hashing
 * happens. BCrypt is deliberately slow, so an unthrottled login endpoint is not only a credential
 * risk but a cheap way to exhaust the server's CPU.
 *
 * <p>Only the endpoints that accept credentials or create accounts are limited. Refresh and logout
 * are excluded: both are driven by a cookie the caller already holds, and throttling them would
 * break a legitimate client that reconnects several tabs at once — the same class of self-inflicted
 * failure as BUG-001.
 */
@Component
@Order(1)
public class AuthRateLimitFilter extends OncePerRequestFilter {

	private static final Duration WINDOW = Duration.ofMinutes(5);

	private final RateLimiter rateLimiter;
	private final int limit;

	public AuthRateLimitFilter(
			RateLimiter rateLimiter,
			@Value("${tradeez.rate-limit.auth-attempts-per-window:20}") int limit) {
		this.rateLimiter = rateLimiter;
		this.limit = limit;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		boolean isCredentialEndpoint =
			path.equals("/api/auth/login") || path.equals("/api/auth/register");
		return !isCredentialEndpoint || !"POST".equalsIgnoreCase(request.getMethod());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {

		String key = "ip:" + clientIp(request) + ":" + request.getRequestURI();

		if (!rateLimiter.tryAcquire(key, limit, WINDOW)) {
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
			response.getWriter().write("""
				{"status":429,"message":"Too many attempts. Please wait a few minutes and try \
				again.","timestamp":"%s"}""".formatted(Instant.now()));
			return;
		}

		chain.doFilter(request, response);
	}

	/**
	 * The caller's address.
	 *
	 * <p>{@code X-Forwarded-For} is read because the app is intended to sit behind a proxy, but only
	 * its first entry and only as a best effort: the header is client-supplied and trivially forged.
	 * A determined attacker can rotate it and defeat this limit entirely, which is a real limitation
	 * rather than a detail — the proper fix is for the deployment's proxy to overwrite the header,
	 * and that has to be verified wherever this is eventually hosted.
	 */
	private String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	/** Keeps the limiter's map from growing without bound on attacker-supplied keys. */
	@Scheduled(fixedDelay = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
	void evictExpiredWindows() {
		rateLimiter.evictExpired();
	}
}
