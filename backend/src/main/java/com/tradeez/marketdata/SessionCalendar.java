package com.tradeez.marketdata;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * When the demo market is open.
 *
 * <p>Modelled on CME Globex equity-index hours, because session structure is not cosmetic here: an
 * ICT curriculum is built almost entirely on session behaviour — the Asian range, the London and
 * New York killzones, the previous session's high and low. A generator that produced a flat 24/7
 * series would make that curriculum unteachable, which is precisely why a regular-hours-only equity
 * ETF proxy was rejected alongside the CFD feed in DATA_STRATEGY.md.
 *
 * <p>The week runs Sunday 17:00 to Friday 16:00 Chicago time, with a 60-minute maintenance break
 * each weekday afternoon at 16:00.
 *
 * <p>INVARIANT 4 — every input is a UTC epoch second. The exchange timezone is applied here and
 * nowhere else, so daylight-saving transitions are handled in exactly one place. Chicago moves
 * between UTC-6 and UTC-5, which shifts every session boundary by an hour twice a year; getting
 * that wrong would silently teach killzone timings that are off by an hour for half the year.
 */
public final class SessionCalendar {

	public static final ZoneId EXCHANGE_ZONE = ZoneId.of("America/Chicago");

	private static final int SESSION_OPEN_HOUR = 17;   // Sunday-Thursday 17:00 CT
	private static final int SESSION_CLOSE_HOUR = 16;  // Monday-Friday 16:00 CT
	private static final int MAINTENANCE_BREAK_MINUTES = 60;

	private SessionCalendar() {
	}

	public static boolean isOpen(long epochSecond) {
		return isOpen(Instant.ofEpochSecond(epochSecond));
	}

	public static boolean isOpen(Instant instant) {
		ZonedDateTime local = instant.atZone(EXCHANGE_ZONE);
		DayOfWeek day = local.getDayOfWeek();
		int hour = local.getHour();

		return switch (day) {
			// Closed until Sunday's 17:00 open.
			case SATURDAY -> false;
			case SUNDAY -> hour >= SESSION_OPEN_HOUR;
			// Friday closes at 16:00 and does not reopen.
			case FRIDAY -> hour < SESSION_CLOSE_HOUR;
			// Weekdays: open except during the maintenance break that starts at 16:00.
			default -> hour < SESSION_CLOSE_HOUR
				|| hour >= SESSION_CLOSE_HOUR + (MAINTENANCE_BREAK_MINUTES / 60);
		};
	}

	/**
	 * The exchange trading date an instant belongs to.
	 *
	 * <p>A futures trading day crosses midnight: Sunday 17:00 CT onwards is <i>Monday's</i> session.
	 * Anything that groups by day — previous-day high and low, daily bars, session statistics — has
	 * to use this rather than the calendar date, or every overnight session gets attributed to the
	 * wrong day.
	 */
	public static java.time.LocalDate tradingDate(Instant instant) {
		ZonedDateTime local = instant.atZone(EXCHANGE_ZONE);
		return local.getHour() >= SESSION_OPEN_HOUR
			? local.toLocalDate().plusDays(1)
			: local.toLocalDate();
	}
}
