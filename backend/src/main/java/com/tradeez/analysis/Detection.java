package com.tradeez.analysis;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One hit of a {@link Detector}: where it is, and the numbers it was decided on.
 *
 * @param kind        which of a detector's kinds of hit this is — a gap's lifecycle state, NDOG or
 *                    NWOG — or null for a detector with one kind. Part of a hit's identity (V13).
 * @param start       open of the first candle the pattern uses
 * @param anchor      the candle a learner points at — for an FVG, the middle one
 * @param end         open of the last candle the pattern uses
 * @param confirmedAt when the hit could first be known: no earlier than the close of {@code end}'s
 *                    candle. A replay shows it only once its cursor has got there (DEC-009).
 * @param lowTicks    a zone's low, or the one price of a level or point (INVARIANT 3)
 * @param highTicks   a zone's high; equal to {@code lowTicks} for a level or point
 * @param evidence    everything an explanation is rendered from, never hand-written (DEC-026)
 */
public record Detection(
	Shape shape,
	Direction direction,
	String kind,
	Instant start,
	Instant anchor,
	Instant end,
	Instant confirmedAt,
	long lowTicks,
	long highTicks,
	Map<String, Object> evidence) {

	/** Mirrors {@code ck_detected_setup_kind}. */
	private static final Pattern KIND = Pattern.compile("[A-Z][A-Z_]*");

	/** Mirrors {@code ck_detected_setup_shape}. */
	public enum Shape {
		/** Between two prices: a gap, a block. */
		ZONE,
		/** At one price, over a span: a session high. */
		LEVEL,
		/** At one price and one candle: a swing. */
		POINT
	}

	public enum Direction {
		BULLISH, BEARISH, NEUTRAL;

		/** The direction a price move from {@code from} to {@code to} points in. */
		public static Direction of(long from, long to) {
			return to > from ? BULLISH : to < from ? BEARISH : NEUTRAL;
		}
	}

	public Detection {
		if (start.isAfter(anchor) || anchor.isAfter(end) || !confirmedAt.isAfter(end)) {
			throw new IllegalArgumentException("A detection runs start ≤ anchor ≤ end < confirmedAt: "
				+ start + ", " + anchor + ", " + end + ", " + confirmedAt);
		}
		if (shape == Shape.ZONE ? lowTicks >= highTicks : lowTicks != highTicks) {
			throw new IllegalArgumentException(shape == Shape.ZONE
				? "A zone's low must be below its high"
				: "A " + shape + " has one price");
		}
		if (kind != null && !KIND.matcher(kind).matches()) {
			throw new IllegalArgumentException("A kind is written LIKE_THIS: " + kind);
		}
		evidence = Collections.unmodifiableMap(new LinkedHashMap<>(evidence));
	}

	/**
	 * Between two prices: a zone when they differ, otherwise a level at the one price. An opening gap
	 * of zero ticks is still a gap — stored as zero, never dropped (ICT-27).
	 */
	public static Shape bandOrLevel(long a, long b) {
		return a == b ? Shape.LEVEL : Shape.ZONE;
	}
}
