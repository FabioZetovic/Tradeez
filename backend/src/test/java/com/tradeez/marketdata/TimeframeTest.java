package com.tradeez.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Where a candle starts (T35, DEC-060): from the epoch up to an hour, from the CME trading day's
 * 18:00 New York open for 4-hour and daily candles — in summer and in winter, and across the weekend
 * the clocks change in.
 */
class TimeframeTest {

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

	/** A New York wall-clock time. */
	private static Instant ny(String localDateTime) {
		return LocalDateTime.parse(localDateTime).atZone(NEW_YORK).toInstant();
	}

	@Test
	@DisplayName("up to an hour, a candle starts on the epoch's grid, as it always has")
	void shortTimeframes() {
		Instant t = Instant.parse("2025-06-10T14:07:30Z");
		assertThat(Timeframe.M5.bucketStart(t)).isEqualTo(Instant.parse("2025-06-10T14:05:00Z"));
		assertThat(Timeframe.H1.bucketStart(t)).isEqualTo(Instant.parse("2025-06-10T14:00:00Z"));
		assertThat(Timeframe.M1.sessionAnchored()).isFalse();
		assertThat(Timeframe.H1.sessionAnchored()).isFalse();
	}

	@Nested
	@DisplayName("a daily candle")
	class Daily {

		@Test
		@DisplayName("is one trading day: from 18:00 New York the evening before, in summer and in winter")
		void tradingDay() {
			// Tuesday 10 June 2025, 10:00 in New York (summer): the day opened Monday 18:00 EDT, 22:00 UTC.
			assertThat(Timeframe.D1.bucketStart(ny("2025-06-10T10:00"))).isEqualTo(Instant.parse("2025-06-09T22:00:00Z"));
			// Tuesday 14 January 2025 (winter): Monday 18:00 EST, 23:00 UTC.
			assertThat(Timeframe.D1.bucketStart(ny("2025-01-14T10:00"))).isEqualTo(Instant.parse("2025-01-13T23:00:00Z"));
			// 18:00 itself opens the next day's candle; 17:59 is still today's.
			assertThat(Timeframe.D1.bucketStart(ny("2025-06-10T18:00"))).isEqualTo(ny("2025-06-10T18:00"));
			assertThat(Timeframe.D1.bucketStart(ny("2025-06-10T17:59"))).isEqualTo(ny("2025-06-09T18:00"));
		}

		@Test
		@DisplayName("opens on Sunday evening for Monday, and keeps 18:00 across the weekend the clocks change")
		void weekendAndDaylightSaving() {
			// Sunday evening's trading belongs to Monday's candle.
			assertThat(Timeframe.D1.bucketStart(ny("2025-06-08T19:30"))).isEqualTo(ny("2025-06-08T18:00"));
			assertThat(Timeframe.D1.bucketStart(ny("2025-06-09T09:30"))).isEqualTo(ny("2025-06-08T18:00"));
			// Clocks went forward on Sunday 9 March 2025, at 02:00 — with the market shut. Friday's candle
			// opened at 23:00 UTC, Monday's at 22:00 UTC: 18:00 New York both times.
			assertThat(Timeframe.D1.bucketStart(ny("2025-03-07T10:00"))).isEqualTo(Instant.parse("2025-03-06T23:00:00Z"));
			assertThat(Timeframe.D1.bucketStart(ny("2025-03-10T10:00"))).isEqualTo(Instant.parse("2025-03-09T22:00:00Z"));
		}
	}

	@Nested
	@DisplayName("a 4-hour candle")
	class FourHours {

		@Test
		@DisplayName("opens at 18:00, 22:00, 02:00, 06:00, 10:00 and 14:00 New York")
		void sixADay() {
			List<Instant> starts = new ArrayList<>();
			for (Instant t = ny("2025-06-09T18:00"); t.isBefore(ny("2025-06-10T17:00")); t = t.plusSeconds(60)) {
				Instant start = Timeframe.H4.bucketStart(t);
				if (starts.isEmpty() || !starts.getLast().equals(start)) {
					starts.add(start);
				}
			}
			assertThat(starts).containsExactly(ny("2025-06-09T18:00"), ny("2025-06-09T22:00"), ny("2025-06-10T02:00"),
				ny("2025-06-10T06:00"), ny("2025-06-10T10:00"), ny("2025-06-10T14:00"));
			// The last runs to the 17:00 close: 16:59 is in it.
			assertThat(Timeframe.H4.bucketStart(ny("2025-06-10T16:59"))).isEqualTo(ny("2025-06-10T14:00"));
		}

		@Test
		@DisplayName("follows New York's clock through the year, not UTC's")
		void winter() {
			assertThat(Timeframe.H4.bucketStart(ny("2025-01-14T10:30"))).isEqualTo(Instant.parse("2025-01-14T15:00:00Z"));
			assertThat(Timeframe.H4.bucketStart(ny("2025-06-10T10:30"))).isEqualTo(Instant.parse("2025-06-10T14:00:00Z"));
		}
	}

	@Test
	@DisplayName("every open minute is inside its candle, and a candle's start is its own start")
	void consistent() {
		Random random = new Random(35);
		Instant from = Instant.parse("2024-01-01T00:00:00Z");
		for (int i = 0; i < 5_000; i++) {
			Instant t = from.plusSeconds(60L * random.nextInt(2 * 365 * 24 * 60));
			if (!SessionCalendar.isOpen(t)) {
				continue;
			}
			for (Timeframe tf : Timeframe.values()) {
				Instant start = tf.bucketStart(t);
				assertThat(start).as("%s at %s", tf, t).isBeforeOrEqualTo(t);
				assertThat(start.plus(tf.duration())).as("%s at %s", tf, t).isAfter(t);
				assertThat(tf.bucketStart(start)).as("%s at %s", tf, t).isEqualTo(start);
			}
		}
	}
}
