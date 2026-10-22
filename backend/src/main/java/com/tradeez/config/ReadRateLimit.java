package com.tradeez.config;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.tradeez.identity.ClientAddress;
import com.tradeez.identity.JwtAuthenticationFilter.AuthenticatedUser;
import com.tradeez.identity.RateLimiter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * BUG-080: the reads that cost the server work — candles, setups, the clock, a lesson — limited per account. Nothing
 * limited them, and a few requests a second from one account kept the CPU busy for everyone.
 *
 * <p>The chart asks for at most five of these when its window moves, so the limit is far above anyone reading charts:
 * it stops a script, not a learner. Counted after sign-in, by account; by address for anything that slips through
 * unsigned.
 */
@Component
public class ReadRateLimit implements HandlerInterceptor, WebMvcConfigurer {

	static final Duration WINDOW = Duration.ofMinutes(1);

	static final String[] LIMITED = {
		"/api/market/instruments/*/bars",
		"/api/market/instruments/*/setups",
		"/api/market/instruments/*/clock",
		"/api/concepts/*/lesson",
		"/api/practice/sessions/*/setups",
		"/api/practice/sessions/*/clock",
	};

	private final RateLimiter rateLimiter;
	private final ClientAddress clientAddress;
	private final int limit;

	public ReadRateLimit(RateLimiter rateLimiter, ClientAddress clientAddress,
			@Value("${tradeez.rate-limit.reads-per-minute}") int limit) {
		this.rateLimiter = rateLimiter;
		this.clientAddress = clientAddress;
		this.limit = limit;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this).addPathPatterns(LIMITED);
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		if (rateLimiter.tryAcquire("read:" + caller(request), limit, WINDOW)) {
			return true;
		}
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
		response.getWriter().write("""
			{"status":429,"message":"Too many chart requests in a minute. Wait a moment and try again.",\
			"timestamp":"%s"}""".formatted(Instant.now()));
		return false;
	}

	private String caller(HttpServletRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
			? user.id().toString()
			: clientAddress.of(request);
	}
}
