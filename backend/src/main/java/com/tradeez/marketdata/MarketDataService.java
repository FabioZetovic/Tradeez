package com.tradeez.marketdata;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Resolves an instrument to a provider and applies the limits that protect the service.
 */
@Service
public class MarketDataService {

	/**
	 * Cap on bars per request.
	 *
	 * <p>Not arbitrary politeness: without it, a client asking for a year of one-second data would
	 * ask the generator for ~20 million bars and serialise them into one response. The cap turns a
	 * trivially triggered out-of-memory into a clear 400.
	 */
	public static final int MAX_BARS_PER_REQUEST = 20_000;

	/** Ceiling on the requested span, checked before generating anything. */
	private static final Duration MAX_RANGE = Duration.ofDays(400);

	private final InstrumentRepository instruments;
	private final List<MarketDataProvider> providers;

	public MarketDataService(InstrumentRepository instruments, List<MarketDataProvider> providers) {
		this.instruments = instruments;
		this.providers = providers;
	}

	public List<Instrument> listInstruments() {
		return instruments.findAllByOrderBySymbolAsc();
	}

	public Instrument requireInstrument(String symbol) {
		return instruments.findBySymbolIgnoreCase(symbol)
			.orElseThrow(() -> new InstrumentNotFoundException(symbol));
	}

	public BarSeries bars(String symbol, Timeframe timeframe, Instant from, Instant to, long seed) {
		Instrument instrument = requireInstrument(symbol);

		if (!to.isAfter(from)) {
			throw new InvalidRangeException("'to' must be after 'from'.");
		}
		if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
			throw new InvalidRangeException(
				"Requested range exceeds the " + MAX_RANGE.toDays() + " day maximum.");
		}

		long estimatedBars = Duration.between(from, to).toSeconds() / timeframe.seconds();
		if (estimatedBars > MAX_BARS_PER_REQUEST) {
			throw new InvalidRangeException(
				"That range would return roughly " + estimatedBars + " bars at " + timeframe.code()
					+ ", above the " + MAX_BARS_PER_REQUEST + " limit. "
					+ "Request a shorter range or a larger timeframe.");
		}

		MarketDataProvider provider = providers.stream()
			.filter(p -> p.supports(instrument))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(
				"No market data provider serves " + instrument.getSymbol()
					+ " (dataClass=" + instrument.getDataClass() + ")"));

		return provider.bars(instrument, timeframe, from, to, seed);
	}

	public static class InstrumentNotFoundException extends RuntimeException {
		public InstrumentNotFoundException(String symbol) {
			super("No instrument with symbol '" + symbol + "'.");
		}
	}

	public static class InvalidRangeException extends RuntimeException {
		public InvalidRangeException(String message) {
			super(message);
		}
	}
}
