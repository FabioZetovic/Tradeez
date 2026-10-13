package com.tradeez.marketdata.massive;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DEC-042 — the free plan allows five requests a minute; the pacer keeps us under it. */
class RequestPacerTest {

	/** A clock that only moves when the pacer sleeps. */
	static final class FakeTime extends Clock {
		Instant now = Instant.parse("2026-10-04T12:00:00Z");
		final List<Duration> sleeps = new ArrayList<>();

		RequestPacer.Sleeper sleeper() {
			return d -> {
				sleeps.add(d);
				now = now.plus(d);
			};
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}

	@Test
	@DisplayName("five requests go straight away; the sixth waits until the first leaves the window")
	void fivePerMinute() {
		FakeTime time = new FakeTime();
		RequestPacer pacer = new RequestPacer(5, time, time.sleeper());

		for (int i = 0; i < 5; i++) {
			pacer.acquire();
			time.now = time.now.plusSeconds(1);
		}
		assertThat(time.sleeps).isEmpty();

		pacer.acquire();
		// First request at 12:00:00, now 12:00:05 → wait until 12:01:01 (a 61-second window).
		assertThat(time.sleeps).containsExactly(Duration.ofSeconds(56));
		assertThat(time.now).isEqualTo(Instant.parse("2026-10-04T12:01:01Z"));
	}

	@Test
	@DisplayName("a sustained run never puts more than five requests in any window")
	void sustained() {
		FakeTime time = new FakeTime();
		RequestPacer pacer = new RequestPacer(5, time, time.sleeper());
		List<Instant> sent = new ArrayList<>();

		for (int i = 0; i < 23; i++) {
			pacer.acquire();
			sent.add(time.now);
		}
		for (int i = 5; i < sent.size(); i++) {
			assertThat(Duration.between(sent.get(i - 5), sent.get(i)))
				.isGreaterThanOrEqualTo(RequestPacer.WINDOW);
		}
	}

	@Test
	@DisplayName("zero per minute means no pacing at all")
	void unlimited() {
		FakeTime time = new FakeTime();
		RequestPacer pacer = new RequestPacer(0, time, time.sleeper());
		for (int i = 0; i < 100; i++) {
			pacer.acquire();
		}
		assertThat(time.sleeps).isEmpty();
	}

	@Test
	@DisplayName("backing off after a 429 sleeps for exactly what was asked")
	void backOff() {
		FakeTime time = new FakeTime();
		RequestPacer pacer = new RequestPacer(5, time, time.sleeper());
		pacer.backOff(Duration.ofSeconds(7));
		assertThat(time.sleeps).containsExactly(Duration.ofSeconds(7));
	}
}
