package com.tradeez.clock;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradeez.clock.ClockDtos.ClockLayerResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The clock layer for the Chart tab. A practice session's comes through the practice API. */
@RestController
@RequestMapping("/api/market")
@Tag(name = "Market data", description = "Instrument catalogue and price series")
public class ClockController {

	private final ClockService clocks;

	public ClockController(ClockService clocks) {
		this.clocks = clocks;
	}

	@GetMapping("/instruments/{symbol}/clock")
	@Operation(summary = "The New York clock over a chart window",
		description = """
			Killzones, Silver Bullet hours, macros and trading-day separators to shade; opening prices, \
			previous day and week highs and lows, and killzone highs and lows to draw (T7, DEC-047). \
			Built from one-minute bars under versioned rules. At most 200 days.""")
	public ClockLayerResponse clock(
			@PathVariable String symbol,
			@RequestParam long from,
			@RequestParam long to) {
		return clocks.forChart(symbol, Instant.ofEpochSecond(from), Instant.ofEpochSecond(to));
	}
}
