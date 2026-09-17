package com.tradeez.marketdata;

import java.time.Instant;

/**
 * Where price data comes from.
 *
 * <p>DEC-021 — every source sits behind this interface so that swapping one for another is an
 * adapter plus configuration rather than a rewrite. That is not hypothetical: the project has
 * already discarded one data source (DEC-020, a broker CFD feed mislabelled as futures) after
 * deciding it was wrong, and expects to add a licensed real-data provider later once written
 * permission exists.
 *
 * <p>Implementations must never leak provider-specific symbols into the domain. The domain knows
 * {@code DEMO-NQ}; whatever a vendor calls it belongs in a mapping table.
 */
public interface MarketDataProvider {

	/** True if this provider serves the given instrument. */
	boolean supports(Instrument instrument);

	/**
	 * Bars covering {@code [from, to)} at the requested timeframe.
	 *
	 * <p>The returned {@link BarSeries} carries its own {@link DataClass}, seed and generator
	 * version, so a caller can never lose track of what the data is or how to reproduce it
	 * (DEC-012, DEC-033).
	 */
	BarSeries bars(Instrument instrument, Timeframe timeframe, Instant from, Instant to, long seed);
}
