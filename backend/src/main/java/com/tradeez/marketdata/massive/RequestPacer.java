package com.tradeez.marketdata.massive;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Keeps requests under Massive's per-minute limit (five on the free plan, DEC-042).
 *
 * <p>A sliding window: before each request, if the last {@code perMinute} requests all started
 * within the window, wait until the oldest leaves it. The window is 61 seconds rather than 60 so a
 * little clock skew between us and Massive never tips a request over the limit.
 *
 * <p>The clock and the sleep are injected so the pacing can be tested without waiting.
 */
final class RequestPacer {

	static final Duration WINDOW = Duration.ofSeconds(61);

	@FunctionalInterface
	interface Sleeper {
		void sleep(Duration duration) throws InterruptedException;
	}

	private final int perMinute;
	private final Clock clock;
	private final Sleeper sleeper;
	private final Deque<Instant> recent = new ArrayDeque<>();

	RequestPacer(int perMinute, Clock clock, Sleeper sleeper) {
		this.perMinute = perMinute;
		this.clock = clock;
		this.sleeper = sleeper;
	}

	static RequestPacer realTime(int perMinute) {
		return new RequestPacer(perMinute, Clock.systemUTC(), d -> Thread.sleep(d.toMillis()));
	}

	/** Blocks until a request may be sent, then counts it. Zero per minute means no limit. */
	synchronized void acquire() {
		if (perMinute <= 0) {
			return;
		}
		while (recent.size() >= perMinute) {
			Duration wait = Duration.between(clock.instant(), recent.peekFirst().plus(WINDOW));
			if (wait.isNegative() || wait.isZero()) {
				recent.pollFirst();
			} else {
				sleep(wait);
			}
		}
		recent.addLast(clock.instant());
	}

	/** Waits after the server said "too many requests". */
	void backOff(Duration duration) {
		sleep(duration);
	}

	private void sleep(Duration duration) {
		try {
			sleeper.sleep(duration);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new MassiveClient.MassiveException("Interrupted while waiting for Massive's rate limit");
		}
	}
}
