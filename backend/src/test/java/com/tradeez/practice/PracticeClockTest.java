package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.practice.FillEngine.Resolution;

/** DEC-025 — a real session's times keep weekday and New York wall clock, and lose the date. */
class PracticeClockTest {

	/** What the client renders: the shown epoch read as UTC. */
	private static LocalDateTime rendered(long shown) {
		return LocalDateTime.ofEpochSecond(shown, 0, ZoneOffset.UTC);
	}

	@Test
	@DisplayName("shows the weekday and New York time of the real moment, on a fictional date in 2000")
	void hidesTheDate() {
		// Monday 7 April 2025, 10:05 New York (EDT, 14:05 UTC) — the tariff-headline spike.
		Instant real = Instant.parse("2025-04-07T14:05:00Z");
		PracticeClock clock = PracticeClock.hidingDateOf(real);

		LocalDateTime shown = rendered(clock.show(real));
		assertThat(shown.getYear()).isEqualTo(2000);
		assertThat(shown.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		assertThat(shown.toLocalTime().toString()).isEqualTo("10:05");
		assertThat(clock.basis()).isEqualTo(PracticeClock.NEW_YORK_WALL_CLOCK);
		assertThat(clock.hidesDate()).isTrue();
	}

	@Test
	@DisplayName("keeps wall-clock time across a daylight-saving change inside the session's window")
	void acrossDaylightSaving() {
		// The US moved to EDT on Sunday 9 March 2025. A session starting Monday the 10th shows its
		// history from the Friday before: both must read 09:30 New York, an hour apart in UTC terms.
		Instant mondayOpen = Instant.parse("2025-03-10T13:30:00Z"); // 09:30 EDT
		Instant fridayOpen = Instant.parse("2025-03-07T14:30:00Z"); // 09:30 EST
		PracticeClock clock = PracticeClock.hidingDateOf(mondayOpen);

		assertThat(rendered(clock.show(mondayOpen)).toLocalTime().toString()).isEqualTo("09:30");
		assertThat(rendered(clock.show(fridayOpen)).toLocalTime().toString()).isEqualTo("09:30");
		assertThat(rendered(clock.show(fridayOpen)).getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
		// Three calendar days apart, as in reality.
		assertThat(clock.show(mondayOpen) - clock.show(fridayOpen)).isEqualTo(3 * 86_400);
	}

	@Test
	@DisplayName("different real weeks share the fictional week, so the date cannot be read back")
	void sameFictionalWeek() {
		Instant a = Instant.parse("2024-11-12T15:00:00Z"); // a Tuesday
		Instant b = Instant.parse("2026-06-16T14:00:00Z"); // another Tuesday, other season
		assertThat(PracticeClock.hidingDateOf(a).show(a)).isEqualTo(PracticeClock.hidingDateOf(b).show(b));
	}

	@Test
	@DisplayName("a stored shift rebuilds the same clock; no shift means real time")
	void rebuilds() {
		Instant real = Instant.parse("2025-04-07T14:05:00Z");
		PracticeClock clock = PracticeClock.hidingDateOf(real);
		assertThat(PracticeClock.of(clock.dayShift()).show(real)).isEqualTo(clock.show(real));
		assertThat(PracticeClock.of(null).show(real)).isEqualTo(real.getEpochSecond());
		assertThat(PracticeClock.REAL.basis()).isEqualTo(PracticeClock.UTC);
	}

	@Test
	@DisplayName("an ambiguous result names the unit of the bars that could not decide it")
	void ambiguityWording() {
		assertThat(PracticeService.ambiguousAssumption(Resolution.M1, false)).contains("same minute");
		assertThat(PracticeService.ambiguousAssumption(Resolution.S1, false)).contains("same second");
	}
}
