package com.tradeez.practice;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * How a practice session's times are shown to the learner (DEC-025).
 *
 * <p>On real data the date is the answer: whoever knows a session is 7 April 2025 can look up what
 * NQ did next. So before the result, a real session never sends a real timestamp — not on screen and
 * not in the network tab. Every time is moved onto a fictional week in January 2000 that keeps what a
 * learner needs: the <b>weekday</b> and the <b>New York wall-clock time</b>, which is what every ICT
 * time window is stated in (DEC-043). How far the fictional week lies from the real one is different
 * for every session and stays on the server.
 *
 * <p>Shown times are New York wall-clock times written as if they were UTC ({@link #NEW_YORK_WALL_CLOCK}),
 * so the client renders them without a timezone and nothing — not even EST versus EDT, which would give
 * away the season — depends on the real date. CME is closed around both daylight-saving changes
 * (Sunday 02:00 New York), so no two bars ever land on the same wall-clock minute.
 *
 * <p>Simulated sessions keep real epoch times ({@link #REAL}): their dates carry no information
 * (DEC-038), and the secret there is the seed.
 */
public final class PracticeClock {

	public static final String UTC = "UTC";
	public static final String NEW_YORK_WALL_CLOCK = "NEW_YORK_WALL_CLOCK";

	static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

	/** The Monday of the fictional week. Real sessions are shown on the day of it with the same weekday. */
	static final LocalDate FICTIONAL_MONDAY = LocalDate.of(2000, 1, 10);

	/** Shows real epoch seconds unchanged. */
	public static final PracticeClock REAL = new PracticeClock(null);

	private final Integer dayShift;

	private PracticeClock(Integer dayShift) {
		this.dayShift = dayShift;
	}

	/** The clock for a real session whose hidden moment is {@code start}. */
	static PracticeClock hidingDateOf(Instant start) {
		LocalDate real = start.atZone(NEW_YORK).toLocalDate();
		LocalDate fictional = FICTIONAL_MONDAY.plusDays(real.getDayOfWeek().getValue() - 1L);
		return new PracticeClock(Math.toIntExact(ChronoUnit.DAYS.between(fictional, real)));
	}

	/** Rebuilds a stored clock: null means real time. */
	static PracticeClock of(Integer dayShift) {
		return dayShift == null ? REAL : new PracticeClock(dayShift);
	}

	/** Days between the fictional date and the real one; null for real time. Never sent to a client. */
	Integer dayShift() {
		return dayShift;
	}

	boolean hidesDate() {
		return dayShift != null;
	}

	/** {@link #UTC} or {@link #NEW_YORK_WALL_CLOCK}: how the client must read the times it is sent. */
	String basis() {
		return hidesDate() ? NEW_YORK_WALL_CLOCK : UTC;
	}

	/** The epoch second the learner is shown for {@code real}. */
	long show(Instant real) {
		if (dayShift == null) {
			return real.getEpochSecond();
		}
		LocalDateTime wall = LocalDateTime.ofInstant(real, NEW_YORK);
		return wall.minusDays(dayShift).toEpochSecond(ZoneOffset.UTC);
	}
}
