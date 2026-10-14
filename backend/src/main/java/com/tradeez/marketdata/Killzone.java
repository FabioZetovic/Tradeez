package com.tradeez.marketdata;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * ICT's killzones, in New York time — the index-futures table of {@code ICT_CONCEPTS.md} ICT-51, the
 * default reading under DEC-043. Part of rule {@code sessions-1.0.0}; T7 builds the rest of that rule
 * (session ranges, opening prices) around these windows.
 */
public enum Killzone {

	ASIA(LocalTime.of(20, 0), LocalTime.MIDNIGHT),
	LONDON(LocalTime.of(2, 0), LocalTime.of(5, 0)),
	NEW_YORK_AM(LocalTime.of(8, 30), LocalTime.of(11, 0)),
	NEW_YORK_PM(LocalTime.of(13, 30), LocalTime.of(16, 0));

	public static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

	private final LocalTime start;
	/** Exclusive. {@link LocalTime#MIDNIGHT} as an end means the end of the day. */
	private final LocalTime end;

	Killzone(LocalTime start, LocalTime end) {
		this.start = start;
		this.end = end;
	}

	public LocalTime start() {
		return start;
	}

	/** Exclusive; {@link LocalTime#MIDNIGHT} means the end of the day. */
	public LocalTime end() {
		return end;
	}

	/** Minutes in the window. */
	public long minutes() {
		long endMinute = end.equals(LocalTime.MIDNIGHT) ? 24 * 60 : end.toSecondOfDay() / 60;
		return endMinute - start.toSecondOfDay() / 60;
	}

	/** True when {@code instant}, in New York time, falls inside this window. */
	public boolean contains(Instant instant) {
		LocalTime t = instant.atZone(NEW_YORK).toLocalTime();
		return !t.isBefore(start) && (end.equals(LocalTime.MIDNIGHT) || t.isBefore(end));
	}
}
