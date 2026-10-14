package com.tradeez.clock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tradeez.clock.ClockDtos.ClockLayerResponse;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.MarketDataService.InvalidRangeException;
import com.tradeez.marketdata.Timeframe;

/**
 * Builds clock layers from one-minute bars (T7). Nothing is stored: a layer is a cheap, pure function
 * of the bars and three rule versions, so a stored copy could only ever go stale.
 */
@Service
public class ClockService {

	/** A little more than the Chart tab's widest window (160 days on 1h). */
	static final Duration MAX_RANGE = Duration.ofDays(200);

	private final MarketDataService marketData;

	public ClockService(MarketDataService marketData) {
		this.marketData = marketData;
	}

	/** The layer over the public chart: the one public market (DEC-038), in real time. */
	public ClockLayerResponse forChart(String symbol, Instant from, Instant to) {
		if (!to.isAfter(from)) {
			throw new InvalidRangeException("'to' must be after 'from'.");
		}
		if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
			throw new InvalidRangeException(
				"The clock layer covers at most " + MAX_RANGE.toDays() + " days at a time.");
		}
		Instrument instrument = marketData.requireInstrument(symbol);
		return ClockDtos.of(layer(instrument, from, to, MarketDataService.PUBLIC_CHART_SEED),
			Instant::getEpochSecond, false);
	}

	/**
	 * Any market's layer over {@code [from, to)}, built from its bars before {@code to} and nothing
	 * else — which is what lets a replay ask for it up to its cursor (DEC-009).
	 *
	 * <p>DEC-038: like {@link MarketDataService#internalBars}, never call this with a seed that came
	 * from a request.
	 */
	public ClockLayer layer(Instrument instrument, Instant from, Instant to, long seed) {
		Instant readFrom = from.minus(ClockLayerBuilder.LOOKBACK);
		List<Bar> bars = marketData.internalBars(instrument, Timeframe.M1, readFrom, to, seed).bars();
		return ClockLayerBuilder.build(bars, marketData.rolls(instrument, readFrom, to), from, to);
	}
}
