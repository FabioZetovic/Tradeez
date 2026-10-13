package com.tradeez.marketdata;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tradeez.marketdata.importing.ImportLedger;

/**
 * Serves real bars from {@code market_bar} (DEC-040).
 *
 * <p>One-minute bars are stored for the whole history and every coarser timeframe is aggregated
 * from them, the same way the simulated provider aggregates from seconds — so a 15-minute candle
 * can never disagree with the minutes inside it. One-second bars are served only where they have
 * been imported; nothing here fetches from a provider, so reading a chart never spends money or
 * requests.
 *
 * <p>The series names the source(s) that imported the stretch it covers, read from the import ledger
 * (INVARIANT 7: every displayed dataset records its source) — not a constant, since NQ now comes
 * from Massive and may later come from Databento too (DEC-042).
 */
@Component
public class ImportedMarketDataProvider implements MarketDataProvider {

	/** When the ledger has no finished import covering the range (bars inserted some other way). */
	static final String UNKNOWN_SOURCE = "imported";

	private final MarketBarStore store;
	private final ImportLedger ledger;

	public ImportedMarketDataProvider(MarketBarStore store, ImportLedger ledger) {
		this.store = store;
		this.ledger = ledger;
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
		String resolution = timeframe == Timeframe.S1 ? "1s" : "1m";
		List<Bar> minutesOrSeconds = store.read(instrument.getId(), resolution, from, to);
		List<Bar> bars = timeframe == Timeframe.S1 || timeframe == Timeframe.M1
			? minutesOrSeconds
			: BarAggregator.aggregate(minutesOrSeconds, timeframe);
		List<String> sources = ledger.sources(instrument.getId(), resolution, from, to);
		String source = sources.isEmpty() ? UNKNOWN_SOURCE : String.join("+", sources);
		return new BarSeries(instrument.getSymbol(), timeframe, instrument.getDataClass(), source,
			null, null, bars);
	}
}
