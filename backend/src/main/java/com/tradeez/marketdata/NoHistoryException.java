package com.tradeez.marketdata;

/**
 * BUG-134 (T57): a feature needs a market's history and none has been imported — as on every new server until its data
 * is loaded. Answered 503 with the code {@code NO_MARKET_DATA}, so the page can say so once and plainly rather than
 * offer to try again: trying again cannot help until someone loads the data.
 */
public class NoHistoryException extends RuntimeException {

	public NoHistoryException(String symbol, String what) {
		super("There is no " + symbol + " market data on this server yet, so there is nothing to " + what
			+ ". It has to be loaded first.");
	}
}
