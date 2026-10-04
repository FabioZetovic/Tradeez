package com.tradeez.marketdata.importing;

import java.time.Instant;

import com.tradeez.marketdata.Instrument;

/** A source of real historical bars that can fill {@code market_bar} (DEC-040, DEC-042). */
public interface MarketDataImporter {

	/** The name used on the command line and recorded in {@code market_data_import.source}. */
	String source();

	/**
	 * Imports {@code [from, to)} at {@code resolution}, or with {@code dryRun} only reports what it
	 * would download.
	 */
	ImportReport importRange(Instrument instrument, String resolution, Instant from, Instant to,
			boolean dryRun);
}
