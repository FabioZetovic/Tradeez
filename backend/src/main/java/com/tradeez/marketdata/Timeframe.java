package com.tradeez.marketdata;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;

/**
 * Supported chart intervals.
 *
 * <p>{@link #S1} is the base resolution everything else aggregates from. It exists for the fill
 * engine rather than for display: DEC-008 requires the simulation to walk finer data than the user
 * is looking at, so that "did price touch the stop or the target first?" has a real answer more
 * often than candle data alone could give.
 *
 * <p><b>Where a candle starts</b> ({@link #bucketStart}, T35, DEC-060). Up to an hour, candles count from the
 * epoch: New York is a whole number of hours from UTC, so they fall on its clock too, and on the 18:00
 * open. A 4-hour or daily candle counted from the epoch would not — UTC midnight is 19:00 or 20:00 in New
 * York — so those count from the CME trading day's open, 18:00 New York, as futures charts draw them: a
 * daily candle is one trading day (18:00 to 17:00, labelled the next day), and 4-hour candles open at
 * 18:00, 22:00, 02:00, 06:00, 10:00 and 14:00, the last cut short by the 17:00 close. Daylight saving
 * moves the open's UTC time, never its New York time; CME changes its clocks while it is shut, so no
 * trading day spans a change.
 */
public enum Timeframe {

	S1("1s", Duration.ofSeconds(1)),
	M1("1m", Duration.ofMinutes(1)),
	M3("3m", Duration.ofMinutes(3)),
	M5("5m", Duration.ofMinutes(5)),
	M15("15m", Duration.ofMinutes(15)),
	M30("30m", Duration.ofMinutes(30)),
	H1("1h", Duration.ofHours(1)),
	H4("4h", Duration.ofHours(4)),
	D1("1d", Duration.ofDays(1));

	private final String code;
	private final Duration duration;

	Timeframe(String code, Duration duration) {
		this.code = code;
		this.duration = duration;
	}

	public String code() {
		return code;
	}

	public Duration duration() {
		return duration;
	}

	public long seconds() {
		return duration.toSeconds();
	}

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

	/** The CME trading day opens at 18:00 New York (17:00 in Chicago). */
	static final LocalTime SESSION_OPEN = LocalTime.of(18, 0);

	/** Whether this timeframe's candles count from the trading day's open rather than from the epoch. */
	public boolean sessionAnchored() {
		return this == H4 || this == D1;
	}

	/** The start of the candle containing {@code instant}: the one place this is decided. */
	public Instant bucketStart(Instant instant) {
		long second = instant.getEpochSecond();
		if (!sessionAnchored()) {
			return Instant.ofEpochSecond(second - Math.floorMod(second, seconds()));
		}
		ZonedDateTime local = instant.atZone(NEW_YORK);
		ZonedDateTime open = local.toLocalTime().isBefore(SESSION_OPEN)
			? local.minusDays(1).with(SESSION_OPEN)
			: local.with(SESSION_OPEN);
		long dayOpen = open.toEpochSecond();
		if (this == D1) {
			return Instant.ofEpochSecond(dayOpen);
		}
		long into = second - dayOpen;
		return Instant.ofEpochSecond(dayOpen + into - Math.floorMod(into, seconds()));
	}

	public static Timeframe fromCode(String code) {
		return Arrays.stream(values())
			.filter(t -> t.code.equalsIgnoreCase(code))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException(
				"Unknown timeframe '" + code + "'. Supported: "
					+ Arrays.stream(values()).map(Timeframe::code).toList()));
	}
}
