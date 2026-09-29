package com.tradeez.marketdata;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Serves the {@code DEMO-*} instruments from {@link DemoSeriesGenerator}.
 *
 * <p>Nothing is persisted. The series is a pure function of {@code (seed, range)}, so storing it
 * would only be a cache — and a cache that could silently disagree with the generator after a
 * version bump. What is kept is an in-memory {@link DayBarCache} of aggregated days, keyed on the
 * generator version and used only for ranges where it provably returns exactly what generation
 * would (BUG-010).
 */
@Component
public class GeneratedMarketDataProvider implements MarketDataProvider {

	private final DemoSeriesGenerator generator;
	private final DayBarCache cache;

	@Autowired
	public GeneratedMarketDataProvider(DemoSeriesGenerator generator) {
		this(generator, new DayBarCache(DayBarCache.DEFAULT_MAX_BARS));
	}

	GeneratedMarketDataProvider(DemoSeriesGenerator generator, DayBarCache cache) {
		this.generator = generator;
		this.cache = cache;
	}

	@Override
	public boolean supports(Instrument instrument) {
		return instrument.getDataClass() == DataClass.SIMULATED;
	}

	@Override
	public BarSeries bars(Instrument instrument, Timeframe timeframe, Instant from, Instant to,
			long seed) {

		List<Bar> bars = DayBarCache.covers(timeframe, from, to)
			? cache.range(seed, DemoSeriesGenerator.GENERATOR_VERSION, timeframe, from, to,
				(dayStart, dayEnd) -> generate(timeframe, dayStart, dayEnd, seed))
			: generate(timeframe, from, to, seed);

		return new BarSeries(
			instrument.getSymbol(),
			timeframe,
			DataClass.SIMULATED,
			"generated",
			seed,
			DemoSeriesGenerator.GENERATOR_VERSION,
			bars);
	}

	private List<Bar> generate(Timeframe timeframe, Instant from, Instant to, long seed) {
		// Always generate at one second and aggregate up, never generate the target timeframe
		// directly. This is what guarantees a 5m candle agrees with the 1s bars inside it — and the
		// fill engine walks those 1s bars (DEC-008), so any disagreement would produce trade
		// outcomes that contradict the chart the user was looking at.
		List<Bar> seconds = generator.generateSeconds(seed, from, to);
		return timeframe == Timeframe.S1 ? seconds : BarAggregator.aggregate(seconds, timeframe);
	}
}
