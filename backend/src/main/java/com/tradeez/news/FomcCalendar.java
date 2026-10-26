package com.tradeez.news;

import java.time.LocalDate;
import java.util.List;

/**
 * The FOMC's rate decisions: the second day of each scheduled meeting, announced at 14:00 New York (T40, DEC-079).
 *
 * <p>From the Federal Reserve's meeting calendar (federalreserve.gov/monetarypolicy/fomccalendars.htm), read
 * {@link #AS_OF}. FRED cannot supply these: its "FOMC Press Release" is dated every day. The Fed sets its meetings
 * more than a year ahead and seldom moves one, so this is the one list kept by hand — the API says how far it reaches
 * ({@link #knownUntil()}) and the News page warns when that is near. 2027's dates are tentative until each is
 * confirmed at the meeting before it. Notation votes (22 August 2025) are not meetings and are left out.
 */
final class FomcCalendar {

	static final LocalDate AS_OF = LocalDate.of(2026, 10, 26);

	static final List<LocalDate> DECISIONS = List.of(
		LocalDate.of(2024, 1, 31), LocalDate.of(2024, 3, 20), LocalDate.of(2024, 5, 1), LocalDate.of(2024, 6, 12),
		LocalDate.of(2024, 7, 31), LocalDate.of(2024, 9, 18), LocalDate.of(2024, 11, 7), LocalDate.of(2024, 12, 18),
		LocalDate.of(2025, 1, 29), LocalDate.of(2025, 3, 19), LocalDate.of(2025, 5, 7), LocalDate.of(2025, 6, 18),
		LocalDate.of(2025, 7, 30), LocalDate.of(2025, 9, 17), LocalDate.of(2025, 10, 29), LocalDate.of(2025, 12, 10),
		LocalDate.of(2026, 1, 28), LocalDate.of(2026, 3, 18), LocalDate.of(2026, 4, 29), LocalDate.of(2026, 6, 17),
		LocalDate.of(2026, 7, 29), LocalDate.of(2026, 9, 16), LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 9),
		LocalDate.of(2027, 1, 27), LocalDate.of(2027, 3, 17), LocalDate.of(2027, 4, 28), LocalDate.of(2027, 6, 9),
		LocalDate.of(2027, 7, 28), LocalDate.of(2027, 9, 15), LocalDate.of(2027, 10, 27), LocalDate.of(2027, 12, 8));

	private FomcCalendar() {
	}

	/** The last decision the list knows of: after it, the FOMC's dates are missing until the list is extended. */
	static LocalDate knownUntil() {
		return DECISIONS.getLast();
	}
}
