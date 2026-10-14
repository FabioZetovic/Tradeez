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

	/**
	 * BUG-043 — a simulated market is generated one second at a time, so what a request costs is the
	 * length of time it covers, not how many bars it returns: 399 days of hourly bars took 21 seconds
	 * of server time. The public endpoints serve a simulated market at most this much at a time.
	 * Nothing in the app asks for more since the simulated markets left the Chart tab (V8).
	 */
	public static final Duration MAX_SIMULATED_RANGE = Duration.ofDays(14);

	/**
	 * The one market the public chart shows (DEC-038).
	 *
	 * <p>Fixed, and deliberately not configurable from a request. Every simulated market is a pure
	 * function of its seed, so letting a client choose one would let it read any market — including
	 * a practice session's, past the point the replay has revealed.
	 *
	 * <p>Kept at 42 because that is what the chart has always shown: drawings saved before DEC-038
	 * were anchored to prices on this market, and changing it would leave every one of them floating
	 * over price action it was never drawn against. Practice seeds must never equal this value.
	 */
	public static final long PUBLIC_CHART_SEED = 42L;

	private final InstrumentRepository instruments;
	private final List<MarketDataProvider> providers;
	private final MarketBarStore barStore;

	public MarketDataService(InstrumentRepository instruments, List<MarketDataProvider> providers,
			MarketBarStore barStore) {
		this.instruments = instruments;
		this.providers = providers;
		this.barStore = barStore;
	}

	/** The instruments the app offers. Withdrawn ones (DEC-040) still resolve by symbol. */
	public List<Instrument> listInstruments() {
		return instruments.findAllByListedTrueOrderBySymbolAsc();
	}

	/**
	 * The span of stored one-minute history for real data: {@code [first bar's open, last bar's
	 * close)}, or null when nothing is imported. Null for simulated data too, which has no edges.
	 */
	public Instant[] coverage(Instrument instrument) {
		if (instrument.getDataClass() == DataClass.SIMULATED) {
			return null;
		}
		Instant[] extent = barStore.extent(instrument.getId(), "1m");
		return extent == null ? null : new Instant[] { extent[0], extent[1].plusSeconds(60) };
	}

	/**
	 * One-minute bars stored for real data in {@code [from, to)}: whether the market actually traded
	 * there. The session calendar knows the hours, not the holidays and early closes; the data does.
	 */
	public int storedMinutes(Instrument instrument, Instant from, Instant to) {
		return barStore.count(instrument.getId(), "1m", from, to);
	}

	/**
	 * Contract rolls in {@code [from, to)}: when the stored front contract changes. A simulated market
	 * is one continuous series and never rolls.
	 */
	public List<Instant> rolls(Instrument instrument, Instant from, Instant to) {
		if (instrument.getDataClass() == DataClass.SIMULATED) {
			return List.of();
		}
		return barStore.contractChanges(instrument.getId(), "1m", from, to);
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
		requireAffordable(instrument, from, to);

		MarketDataProvider provider = providers.stream()
			.filter(p -> p.supports(instrument))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(
				"No market data provider serves " + instrument.getSymbol()
					+ " (dataClass=" + instrument.getDataClass() + ")"));

		return provider.bars(instrument, timeframe, from, to, seed);
	}

	/**
	 * Refuses a public request for more of a simulated market than is cheap to generate (BUG-043).
	 * Real data is read, not generated, and is bounded by the other limits.
	 */
	public void requireAffordable(Instrument instrument, Instant from, Instant to) {
		if (instrument.getDataClass() == DataClass.SIMULATED
				&& Duration.between(from, to).compareTo(MAX_SIMULATED_RANGE) > 0) {
			throw new InvalidRangeException("A simulated market is generated second by second; ask for "
				+ "at most " + MAX_SIMULATED_RANGE.toDays() + " days of it at a time.");
		}
	}

	/**
	 * Server-side access to <b>any</b> market, including a practice session's secret one.
	 *
	 * <p>DEC-038: never call this with a seed that came from a request, and never return its output
	 * to a client without clipping it to a session cursor. It deliberately skips the per-request caps
	 * above, because its only callers bound their own ranges — the practice engine needs a full
	 * trading day of one-second data to resolve a trade, far more than any client may ask for.
	 */
	public BarSeries internalBars(Instrument instrument, Timeframe timeframe, Instant from,
			Instant to, long seed) {
		if (!to.isAfter(from)) {
			return new BarSeries(instrument.getSymbol(), timeframe, instrument.getDataClass(),
				instrument.getDataClass() == DataClass.SIMULATED ? "generated" : "imported",
				seed, null, List.of());
		}
		return providerFor(instrument).bars(instrument, timeframe, from, to, seed);
	}

	private MarketDataProvider providerFor(Instrument instrument) {
		return providers.stream()
			.filter(p -> p.supports(instrument))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(
				"No market data provider serves " + instrument.getSymbol()
					+ " (dataClass=" + instrument.getDataClass() + ")"));
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
