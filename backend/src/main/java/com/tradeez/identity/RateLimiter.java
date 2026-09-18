package com.tradeez.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * A fixed-window counter, used to throttle authentication attempts (BUG-002).
 *
 * <p>Without this, {@code /api/auth/login} accepts unlimited guesses and {@code /api/auth/register}
 * can be walked at full speed to discover which email addresses have accounts. Registration cannot
 * avoid revealing that an address is taken — a signup form has to say so — which is exactly why
 * vague error messages are not the mitigation and rate limiting is.
 *
 * <p><b>In-memory, therefore per-instance.</b> Two backend replicas would each allow the full quota,
 * so this is a meaningful speed bump rather than a hard guarantee. That is an honest trade for a
 * single-instance deployment and no new infrastructure; a shared store (Redis) is the upgrade path
 * if the app is ever scaled out. Recorded rather than glossed over.
 *
 * <p>A fixed window is used instead of a sliding one because it is simple enough to be obviously
 * correct. Its known weakness — up to twice the quota across a window boundary — does not matter
 * when the quota is already far below what a brute-force attempt needs.
 */
@Component
public class RateLimiter {

	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	private record Window(Instant expiresAt, AtomicInteger count) {
	}

	/**
	 * Records an attempt.
	 *
	 * @return true if it is allowed, false if the caller has exhausted its quota for this window
	 */
	public boolean tryAcquire(String key, int limit, Duration window) {
		Instant now = Instant.now();

		Window current = windows.compute(key, (k, existing) ->
			existing == null || existing.expiresAt().isBefore(now)
				? new Window(now.plus(window), new AtomicInteger(0))
				: existing);

		// Increment first, compare after: two threads incrementing concurrently can never both see
		// a value below the limit and both proceed.
		return current.count().incrementAndGet() <= limit;
	}

	/**
	 * Clears a key's window, called after a successful sign-in.
	 *
	 * <p>Without this, a user who mistyped a password several times would stay throttled even after
	 * getting it right — punishing the exact person the limit is meant to protect.
	 */
	public void reset(String key) {
		windows.remove(key);
	}

	/**
	 * Drops expired windows.
	 *
	 * <p>The map is keyed by client IP and email address, both attacker-supplied, so without
	 * eviction a stream of varied inputs would grow it without bound — turning a defence against
	 * brute force into a memory-exhaustion vector.
	 */
	public void evictExpired() {
		Instant now = Instant.now();
		// !isAfter rather than isBefore: a window whose expiry lands exactly on the current instant
		// is finished, and on Windows the clock is coarse enough that exact equality is common
		// rather than theoretical.
		windows.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
	}

	public int trackedKeys() {
		return windows.size();
	}

	/**
	 * Drops every window.
	 *
	 * <p>Exists for tests. This bean is a singleton, so without it one test's attempts leak into the
	 * next and a suite that signs in repeatedly throttles itself — which is exactly what happened
	 * when this limiter was first added.
	 */
	public void clear() {
		windows.clear();
	}
}
