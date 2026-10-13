package com.tradeez.marketdata.dto;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Wire shapes for market data.
 *
 * <p>DEC-007 — published through OpenAPI; the frontend generates its TypeScript from these and
 * never hand-writes a matching interface.
 */
public final class MarketDataDtos {

	private MarketDataDtos() {
	}

	@Schema(description = "A tradeable product and its contract economics")
	public record InstrumentResponse(
		String symbol,
		String displayName,
		String exchange,
		String currency,
		@Schema(description = "Smallest price increment, e.g. 0.25 index points")
		BigDecimal tickSize,
		@Schema(description = "Cash value of one tick, in cents. 500 = $5.00")
		long tickValueCents,
		@Schema(description = "SIMULATED data must never affect a learner's score (DEC-034)")
		String dataClass,
		String exchangeTimezone,
		@Schema(description = """
			Real data only: first stored minute, UTC epoch seconds. Null when nothing is imported, \
			and for simulated data, which has no edges.""")
		Long dataFrom,
		@Schema(description = "Real data only: end of the last stored minute, UTC epoch seconds")
		Long dataTo) {
	}

	@Schema(description = """
		One OHLCV candle. Prices are INTEGER TICK COUNTS, not decimals — multiply by the \
		instrument's tickSize to display. Volume on a SIMULATED series is invented and means \
		nothing.""")
	public record BarResponse(
		@Schema(description = "Interval start, UTC epoch seconds") long time,
		long open,
		long high,
		long low,
		long close,
		long volume) {
	}

	@Schema(description = """
		A price series with its provenance. dataClass and seed travel with the data so a client \
		can never lose track of what it is holding or how to reproduce it.""")
	public record BarSeriesResponse(
		String instrument,
		String timeframe,
		@Schema(description = "SIMULATED | IMPORTED_PRIVATE | LICENSED_DEPLOYABLE")
		String dataClass,
		@Schema(description = """
			Where the bars came from (INVARIANT 7): 'generated' for simulated data, the importing \
			source(s) for real data, e.g. 'massive'.""")
		String source,
		@Schema(description = "Present only for generated data; replays the exact same series")
		Long seed,
		String generatorVersion,
		@Schema(description = "Shown verbatim in the UI when dataClass is SIMULATED (DEC-033)")
		String disclaimer,
		List<BarResponse> bars) {
	}
}
