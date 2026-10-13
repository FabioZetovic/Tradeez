package com.tradeez.marketdata.importing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * What an import did, chunk by chunk — the same shape whichever source ran it.
 *
 * @param source     the importer's source name ({@code massive}, {@code databento})
 * @param dryRun     true when nothing was downloaded
 * @param chunks     one entry per chunk of the range, in order
 * @param costUsd    total estimated cost (zero for a free source)
 * @param barsStored bars written to {@code market_bar}
 */
public record ImportReport(String source, boolean dryRun, List<Chunk> chunks, BigDecimal costUsd,
		long barsStored) {

	public enum Outcome {
		/** A finished import already covers this chunk; nothing was requested. */
		ALREADY_IMPORTED,
		/** Dry run: what would be downloaded, and its price. */
		ESTIMATED,
		/** Downloaded and stored. */
		IMPORTED,
		/**
		 * The source had no data for this chunk — for Massive, typically a month outside the plan's
		 * history window. Never recorded as covered, so a later run with a wider plan fetches it.
		 */
		NO_DATA
	}

	/**
	 * @param detail what the chunk was made of — for Massive, the contracts it used
	 */
	public record Chunk(Instant from, Instant to, Outcome outcome, BigDecimal costUsd,
			long barsStored, String detail) {
	}
}
