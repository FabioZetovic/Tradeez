package com.tradeez.marketdata;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradeez.marketdata.dto.MarketDataDtos.BarResponse;
import com.tradeez.marketdata.dto.MarketDataDtos.BarSeriesResponse;
import com.tradeez.marketdata.dto.MarketDataDtos.InstrumentResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Instrument catalogue and price series.
 *
 * <p>Authentication is required (DEC-022 deny-by-default). Not because simulated prices are
 * sensitive — they are invented — but because this is the endpoint a real provider's data would
 * eventually flow through, and INVARIANT 7 says the backend is never a data reseller. Building it
 * open and tightening later is how that invariant gets broken by accident.
 */
@RestController
@RequestMapping("/api/market")
@Tag(name = "Market data", description = "Instrument catalogue and price series")
public class MarketDataController {

	/** Shown verbatim in the UI wherever simulated data appears (DEC-033). */
	private static final String SIMULATED_DISCLAIMER = "SIMULATED DATA — NOT MARKET HISTORY";

	private final MarketDataService service;

	public MarketDataController(MarketDataService service) {
		this.service = service;
	}

	@GetMapping("/instruments")
	@Operation(summary = "Every available instrument")
	public List<InstrumentResponse> instruments() {
		return service.listInstruments().stream().map(MarketDataController::toDto).toList();
	}

	@GetMapping("/instruments/{symbol}")
	@Operation(summary = "One instrument and its contract economics")
	public InstrumentResponse instrument(@PathVariable String symbol) {
		return toDto(service.requireInstrument(symbol));
	}

	@GetMapping("/instruments/{symbol}/bars")
	@Operation(summary = "OHLCV bars",
		description = """
			Prices are integer tick counts, not decimals — multiply by the instrument's tickSize \
			to display. The same seed and range always return an identical series, which is what \
			makes a practice result reproducible (DEC-012).""")
	public BarSeriesResponse bars(
			@PathVariable String symbol,
			@RequestParam(defaultValue = "5m") String timeframe,
			@RequestParam long from,
			@RequestParam long to,
			@RequestParam(defaultValue = "1") long seed) {

		BarSeries series = service.bars(
			symbol,
			Timeframe.fromCode(timeframe),
			Instant.ofEpochSecond(from),
			Instant.ofEpochSecond(to),
			seed);

		return new BarSeriesResponse(
			series.instrumentSymbol(),
			series.timeframe().code(),
			series.dataClass().name(),
			series.seed(),
			series.generatorVersion(),
			series.dataClass() == DataClass.SIMULATED ? SIMULATED_DISCLAIMER : null,
			series.bars().stream()
				.map(b -> new BarResponse(
					b.openTime().getEpochSecond(),
					b.openTicks(), b.highTicks(), b.lowTicks(), b.closeTicks(), b.volume()))
				.toList());
	}

	private static InstrumentResponse toDto(Instrument i) {
		return new InstrumentResponse(
			i.getSymbol(), i.getDisplayName(), i.getExchange(), i.getCurrency(),
			i.getTickSize(), i.getTickValueCents(), i.getDataClass().name(),
			i.getExchangeTimezone());
	}
}
