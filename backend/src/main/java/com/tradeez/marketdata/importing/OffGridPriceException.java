package com.tradeez.marketdata.importing;

/**
 * A source delivered a price that is not a whole number of ticks.
 *
 * <p>CME prices are always on the instrument's tick grid, so an off-grid value means something is
 * wrong upstream. It is never rounded (INVARIANT 3: reject, never store an impossible price).
 */
public class OffGridPriceException extends RuntimeException {

	public OffGridPriceException(String message) {
		super(message);
	}
}
