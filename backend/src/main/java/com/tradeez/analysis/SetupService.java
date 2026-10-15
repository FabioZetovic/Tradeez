package com.tradeez.analysis;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.ToLongFunction;

import org.springframework.stereotype.Service;

import com.tradeez.analysis.SetupDtos.SetupLayerResponse;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;

/**
 * Detected setups to draw over a chart (T9, S-3): scanned on demand, then read back filtered to what
 * was knowable at the chart's right edge (DEC-009, DEC-049).
 */
@Service
public class SetupService {

	/** More than the overlay toggles there are; a guard, not a product limit. */
	static final int MAX_DETECTORS = 16;

	private final MarketDataService marketData;
	private final DetectorRegistry detectors;
	private final SetupScanner scanner;

	public SetupService(MarketDataService marketData, DetectorRegistry detectors, SetupScanner scanner) {
		this.marketData = marketData;
		this.detectors = detectors;
		this.scanner = scanner;
	}

	/**
	 * Over the public chart: the one public market (DEC-038), in real time, under the same limits as
	 * its bars. The scan covers whole UTC days, so scrolling a chart back and forth reuses one scan.
	 */
	public SetupLayerResponse forChart(String symbol, String timeframeCode, Instant from, Instant to,
			List<String> detectorIds) {
		Instrument instrument = marketData.requireInstrument(symbol);
		Timeframe timeframe = requireDetectable(timeframeCode);
		marketData.requirePublicRange(instrument, timeframe, from, to);
		Instant scanFrom = from.truncatedTo(ChronoUnit.DAYS);
		Instant scanTo = to.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);
		return layer(instrument, timeframe, MarketDataService.PUBLIC_CHART_SEED, from, scanFrom, scanTo, to,
			detectorIds, Instant::getEpochSecond);
	}

	/**
	 * Over a replay: everything touching {@code [from, cursor)} and confirmed by the cursor. The
	 * whole stretch to {@code scanTo} is scanned once and filtered as the cursor moves, which is
	 * sound only because every detector is causal (see {@link Detector}).
	 *
	 * <p>DEC-038: like {@link MarketDataService#internalBars}, never call this with a seed that came
	 * from a request.
	 */
	public SetupLayerResponse forReplay(Instrument instrument, Timeframe timeframe, long seed, Instant from,
			Instant scanTo, Instant cursor, List<String> detectorIds, ToLongFunction<Instant> time) {
		return layer(instrument, timeframe, seed, from, from, scanTo, cursor, detectorIds, time);
	}

	private SetupLayerResponse layer(Instrument instrument, Timeframe timeframe, long seed, Instant from,
			Instant scanFrom, Instant scanTo, Instant until, List<String> detectorIds,
			ToLongFunction<Instant> time) {
		List<Detector> chosen = resolve(detectorIds);
		List<DetectedSetup> found = new ArrayList<>();
		for (Detector detector : chosen) {
			scanner.ensureScanned(detector, instrument, timeframe, seed, scanFrom, scanTo);
			found.addAll(scanner.visible(detector, instrument, timeframe, seed, from, until, until));
		}
		return SetupDtos.of(chosen, found, marketData.rolls(instrument, from, until), time);
	}

	private List<Detector> resolve(List<String> ids) {
		LinkedHashSet<String> distinct = new LinkedHashSet<>(ids == null ? List.of() : ids);
		if (distinct.isEmpty() || distinct.size() > MAX_DETECTORS) {
			throw new IllegalArgumentException("Name between 1 and " + MAX_DETECTORS + " detectors.");
		}
		return distinct.stream().map(detectors::require).toList();
	}

	/** One-second candles are noise to every rule there is; patterns are read from a minute up. */
	private static Timeframe requireDetectable(String code) {
		Timeframe timeframe = Timeframe.fromCode(code);
		if (timeframe == Timeframe.S1) {
			throw new IllegalArgumentException("Setups are detected on one-minute charts and above.");
		}
		return timeframe;
	}
}
