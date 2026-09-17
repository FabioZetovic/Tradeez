package com.tradeez.marketdata;

import java.time.Instant;

/**
 * One OHLCV candle.
 *
 * <p>INVARIANT 3 — every price is an <b>integer tick count</b>, never a floating-point number.
 * {@code 0.1 + 0.2 != 0.3} in binary floating point, and a P&L engine that accumulates that error
 * produces results a trader cannot reconcile. Rendering a tick count as a price is a presentation
 * concern handled at the edge; nothing in the domain ever sees a {@code double}.
 *
 * <p>INVARIANT 4 — {@code openTime} is UTC. Conversion to exchange time happens only for display.
 *
 * @param openTime      start of the interval, UTC
 * @param openTicks     first traded price in the interval
 * @param highTicks     highest price in the interval
 * @param lowTicks      lowest price in the interval
 * @param closeTicks    last traded price in the interval
 * @param volume        contracts traded. For SIMULATED data this is invented and means nothing —
 *                      see DEC-020's note that broker volume is not representative. Never build
 *                      volume-derived analysis on a simulated series.
 */
public record Bar(
	Instant openTime,
	long openTicks,
	long highTicks,
	long lowTicks,
	long closeTicks,
	long volume) {

	public Bar {
		if (highTicks < lowTicks) {
			throw new IllegalArgumentException(
				"high (" + highTicks + ") is below low (" + lowTicks + ") at " + openTime);
		}
		if (openTicks > highTicks || openTicks < lowTicks) {
			throw new IllegalArgumentException(
				"open (" + openTicks + ") is outside the high-low range at " + openTime);
		}
		if (closeTicks > highTicks || closeTicks < lowTicks) {
			throw new IllegalArgumentException(
				"close (" + closeTicks + ") is outside the high-low range at " + openTime);
		}
		if (volume < 0) {
			throw new IllegalArgumentException("negative volume at " + openTime);
		}
	}

	public boolean isUp() {
		return closeTicks > openTicks;
	}

	public long rangeTicks() {
		return highTicks - lowTicks;
	}

	public long bodyTicks() {
		return Math.abs(closeTicks - openTicks);
	}
}
