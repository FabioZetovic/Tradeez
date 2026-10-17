package com.tradeez.common;

import java.time.Instant;

/**
 * A request's epoch second as an {@link Instant}, refused when no chart could mean it (BUG-068).
 *
 * <p>{@code Instant.ofEpochSecond} throws for a number past what an {@code Instant} holds, and nothing
 * mapped that to a client error: {@code to=9223372036854775807} answered 500. The bound is wider than any
 * market history and far inside what the date arithmetic downstream can add a lookback to.
 */
public final class Epochs {

	/** 1970-01-01. */
	static final long EARLIEST = 0;

	/** 9999-12-31T23:59:59Z. */
	static final long LATEST = 253_402_300_799L;

	private Epochs() {
	}

	/** @throws IllegalArgumentException a 400, naming the parameter, when the second is out of range */
	public static Instant of(long epochSecond, String name) {
		if (epochSecond < EARLIEST || epochSecond > LATEST) {
			throw new IllegalArgumentException(name + " must be a time between 1970 and 9999, in epoch seconds.");
		}
		return Instant.ofEpochSecond(epochSecond);
	}
}
