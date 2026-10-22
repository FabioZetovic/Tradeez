package com.tradeez.marketdata;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradeez.common.Epochs;
import com.tradeez.identity.JwtAuthenticationFilter.AuthenticatedUser;
import com.tradeez.marketdata.dto.MarketDataDtos.BarResponse;
import com.tradeez.marketdata.dto.MarketDataDtos.BarSeriesResponse;
import com.tradeez.marketdata.dto.MarketDataDtos.HiddenResponse;
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
	private final HiddenStretches hiddenStretches;

	public MarketDataController(MarketDataService service, HiddenStretches hiddenStretches) {
		this.service = service;
		this.hiddenStretches = hiddenStretches;
	}

	@GetMapping("/instruments")
	@Operation(summary = "The instruments the app offers",
		description = "Simulated markets are withdrawn from this list (DEC-040) but still resolve by symbol.")
	public List<InstrumentResponse> instruments() {
		return service.listInstruments().stream().map(this::toDto).toList();
	}

	@GetMapping("/instruments/{symbol}")
	@Operation(summary = "One instrument and its contract economics")
	public InstrumentResponse instrument(@PathVariable String symbol) {
		return toDto(service.requireInstrument(symbol));
	}

	@GetMapping("/instruments/{symbol}/bars")
	@Operation(summary = "OHLCV bars for the public chart",
		description = """
			Prices are integer tick counts, not decimals — multiply by the instrument's tickSize \
			to display. Always serves the one public market; a seed cannot be chosen (DEC-038).""")
	public BarSeriesResponse bars(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable String symbol,
			@RequestParam(defaultValue = "5m") String timeframe,
			@RequestParam long from,
			@RequestParam long to,
			@RequestParam(required = false) Long seed) {

		// DEC-038. A simulated market is a pure function of its seed, and this endpoint serves any
		// time range — past or future. When it accepted a seed, it was a generator anyone could
		// drive: a practice session's seed would have been enough to read every bar the replay had
		// not yet revealed, defeating DEC-009 from outside the practice API entirely.
		//
		// So it serves exactly one market, the public chart's. Rejected loudly rather than ignored:
		// a client that thinks it is selecting a market should learn that it is not.
		if (seed != null) {
			throw new IllegalArgumentException(
				"The public bars endpoint does not accept a seed. It always serves the public "
					+ "chart's market; practice sessions are read through /api/practice.");
		}

		Timeframe frame = Timeframe.fromCode(timeframe);
		Instant start = Epochs.of(from, "from");
		Instant end = Epochs.of(to, "to");
		BarSeries series = service.bars(symbol, frame, start, end, MarketDataService.PUBLIC_CHART_SEED);
		// BUG-085, BUG-086: a candle touching a stretch the learner is still graded by is left out whole — a daily
		// candle's high and low are the hidden hours' too.
		List<HiddenStretches.Stretch> hidden = series.dataClass() == DataClass.SIMULATED ? List.of()
			: hiddenStretches.of(principal.id()).stream().filter(s -> s.overlaps(start, end)).toList();

		return new BarSeriesResponse(
			series.instrumentSymbol(),
			series.timeframe().code(),
			series.dataClass().name(),
			series.source(),
			series.seed(),
			series.generatorVersion(),
			series.dataClass() == DataClass.SIMULATED ? SIMULATED_DISCLAIMER : null,
			series.bars().stream()
				.filter(b -> !HiddenStretches.touches(hidden, b.openTime(), b.openTime().plus(frame.duration())))
				.map(b -> new BarResponse(
					b.openTime().getEpochSecond(),
					b.openTicks(), b.highTicks(), b.lowTicks(), b.closeTicks(), b.volume()))
				.toList(),
			hidden.stream()
				.map(s -> new HiddenResponse(s.from().getEpochSecond(), s.to().getEpochSecond(), s.why().name()))
				.toList());
	}

	private InstrumentResponse toDto(Instrument i) {
		Instant[] coverage = service.coverage(i);
		return new InstrumentResponse(
			i.getSymbol(), i.getDisplayName(), i.getExchange(), i.getCurrency(),
			i.getTickSize(), i.getTickValueCents(), i.getDataClass().name(),
			i.getExchangeTimezone(),
			coverage == null ? null : coverage[0].getEpochSecond(),
			coverage == null ? null : coverage[1].getEpochSecond());
	}
}
