package com.tradeez.marketdata;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Serves the {@code DEMO-*} instruments from {@link DemoSeriesGenerator}.
 *
 * <p>Nothing is persisted. The series is a pure function of {@code (seed, range)}, so storing it
 * would only be a cache — and a cache that could silently disagree with the generator after a
 * version bump. Regenerating is cheap: a full trading day is 86,400 one-second bars.
 */
@Component
public class GeneratedMarketDataProvider implements MarketDataProvider {

	private final DemoSeriesGenerator generator;

	public GeneratedMarketDataProvider(DemoSeriesGenerator generator) {
		this.generator = generator;
	}

	@Override
	public boolean supports(Instrument instrument) {
		return instrument.getDataClass() == DataClass.SIMULATED;
	}

	@Override
	public BarSeries bars(Instrument instrument, Timeframe timeframe, Instant from, Instant to,
			long seed) {

		// Always generate at one second and aggregate up, never generate the target timeframe
		// directly. This is what guarantees a 5m candle agrees with the 1s bars inside it — and the
		// fill engine walks those 1s bars (DEC-008), so any disagreement would produce trade
		// outcomes that contradict the chart the user was looking at.
		List<Bar> seconds = generator.generateSeconds(seed, from, to);
		List<Bar> bars = timeframe == Timeframe.S1
			? seconds
			: BarAggregator.aggregate(seconds, timeframe);

		return new BarSeries(
			instrument.getSymbol(),
			timeframe,
			DataClass.SIMULATED,
			"generated",
			seed,
			DemoSeriesGenerator.GENERATOR_VERSION,
			bars);
	}
}
