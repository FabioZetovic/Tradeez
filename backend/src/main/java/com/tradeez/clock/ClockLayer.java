package com.tradeez.clock;

import java.time.Instant;
import java.util.List;

/**
 * The New York clock drawn over a chart (T7): the windows of the day, and the prices ICT reads off
 * the clock — opening prices and reference highs and lows. Built by {@link ClockLayerBuilder}.
 *
 * @param nextChange the first moment after the layer's end at which it could look different: a
 *                   window opening, a session closing, an opening price printing. A replay asks
 *                   again once its cursor reaches it. Pure clock arithmetic, so it says nothing
 *                   about prices still to come.
 */
public record ClockLayer(List<Window> windows, List<Line> lines, Instant nextChange) {

	public enum WindowKind {
		/** A CME trading day, 18:00 to 17:00 New York: drawn as a separator and a weekday label. */
		TRADING_DAY,
		KILLZONE,
		SILVER_BULLET,
		MACRO
	}

	public enum LineKind {
		/** Opening prices: midnight, 08:30, 09:30, the week's (ICT-50). */
		OPEN,
		/** PDH and PDL. */
		PREVIOUS_DAY,
		/** PWH and PWL. */
		PREVIOUS_WEEK,
		/** The high and low of each killzone, once it has closed (ICT-11, ICT-51). */
		SESSION,
		/** The highs and lows of the last 20, 40 and 60 trading days (ICT-57, ipda-1.0.0, T19). */
		IPDA
	}

	/** A span of the clock, {@code [from, to)}. */
	public record Window(WindowKind kind, String label, Instant from, Instant to) {
	}

	/**
	 * A price that matters over {@code [from, to)}.
	 *
	 * @param takenAt for a reference high or low (not an opening price): the first minute, from
	 *                {@code from} and before the layer's end, that traded beyond it — the liquidity
	 *                resting there taken ({@code liquidity-1.0.0}, ICT-10, T12). Null while it rests.
	 */
	public record Line(LineKind kind, String label, Instant from, Instant to, long priceTicks, Instant takenAt) {

		/** A high (true), a low (false), or an opening price, which holds no liquidity (null). */
		public Boolean high() {
			if (kind == LineKind.OPEN) {
				return null;
			}
			return label.equals("PDH") || label.equals("PWH") || label.endsWith(" H");
		}
	}
}
