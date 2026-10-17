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
 * <p>Only the endpoints that accept credentials, change them or create accounts are limited. Refresh and logout
 * are excluded: both are driven by a cookie the caller already holds, and throttling them would
 * break a legitimate client that reconnects several tabs at once — the same class of self-inflicted
 * failure as BUG-001.
 */
@Component
@Order(1)
public class AuthRateLimitFilter extends OncePerRequestFilter {

	private static final Duration WINDOW = Duration.ofMinutes(5);

	/** The endpoints a guess can be made at (DEC-064 adds the password change). */
	private static final java.util.Set<String> LIMITED =
		java.util.Set.of("/api/auth/login", "/api/auth/register", "/api/auth/password");

	private final RateLimiter rateLimiter;
	private final int limit;
	private final int trustedProxies;

	public AuthRateLimitFilter(
			RateLimiter rateLimiter,
			@Value("${tradeez.rate-limit.auth-attempts-per-window:20}") int limit,
			@Value("${tradeez.rate-limit.trusted-proxies:0}") int trustedProxies) {
		this.rateLimiter = rateLimiter;
		this.limit = limit;
		this.trustedProxies = trustedProxies;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !LIMITED.contains(request.getRequestURI()) || !"POST".equalsIgnoreCase(request.getMethod());
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
	 * <p>BUG-070. This read the <i>first</i> {@code X-Forwarded-For} entry, which is whatever the client
	 * wrote: a new value on every request was a new quota, and the per-IP limit could be walked around
	 * entirely. Each proxy <i>appends</i> the address it saw, so only entries added by proxies we run can
	 * be believed — counted from the right, {@code tradeez.rate-limit.trusted-proxies} of them. With none
	 * (the default, and locally) the header is ignored and the connection's own address is used.
	 */
	String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (trustedProxies > 0 && forwarded != null && !forwarded.isBlank()) {
			String[] hops = forwarded.split(",");
			if (hops.length >= trustedProxies) {
				return hops[hops.length - trustedProxies].trim();
			}
		}
		return request.getRemoteAddr();
	}

	/** Keeps the limiter's map from growing without bound on attacker-supplied keys. */
	@Scheduled(fixedDelay = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
	void evictExpiredWindows() {
		rateLimiter.evictExpired();
	}
}
