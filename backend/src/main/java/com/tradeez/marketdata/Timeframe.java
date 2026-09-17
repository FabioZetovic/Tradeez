package com.tradeez.marketdata;

import java.time.Duration;
import java.util.Arrays;

/**
 * Supported chart intervals.
 *
 * <p>{@link #S1} is the base resolution everything else aggregates from. It exists for the fill
 * engine rather than for display: DEC-008 requires the simulation to walk finer data than the user
 * is looking at, so that "did price touch the stop or the target first?" has a real answer more
 * often than candle data alone could give.
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

	public static Timeframe fromCode(String code) {
		return Arrays.stream(values())
			.filter(t -> t.code.equalsIgnoreCase(code))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException(
				"Unknown timeframe '" + code + "'. Supported: "
					+ Arrays.stream(values()).map(Timeframe::code).toList()));
	}
}
