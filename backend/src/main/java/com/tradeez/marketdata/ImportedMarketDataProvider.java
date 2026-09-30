package com.tradeez.marketdata;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Serves real bars from {@code market_bar} (DEC-040).
 *
 * <p>One-minute bars are stored for the whole history and every coarser timeframe is aggregated
 * from them, the same way the simulated provider aggregates from seconds — so a 15-minute candle
 * can never disagree with the minutes inside it. One-second bars are served only where they have
 * been imported; nothing here fetches from Databento, so reading a chart never spends money.
 */
@Component
public class ImportedMarketDataProvider implements MarketDataProvider {

	static final String SOURCE = "databento";

	private final MarketBarStore store;

	public ImportedMarketDataProvider(MarketBarStore store) {
		this.store = store;
	}

	@Override
	public boolean supports(Instrument instrument) {
		return instrument.getDataClass() != DataClass.SIMULATED;
	}

	@Override
	public BarSeries bars(Instrument instrument, Timeframe timeframe, Instant from, Instant to,
			long seed) {
		// Real data has no seed: the market is what it was. The parameter exists for the simulated
		// provider and is ignored here.
		List<Bar> bars = switch (timeframe) {
			case S1 -> store.read(instrument.getId(), "1s", from, to);
			case M1 -> store.read(instrument.getId(), "1m", from, to);
			default -> BarAggregator.aggregate(store.read(instrument.getId(), "1m", from, to),
				timeframe);
		};
		return new BarSeries(instrument.getSymbol(), timeframe, instrument.getDataClass(), SOURCE,
			null, null, bars);
	}
}
