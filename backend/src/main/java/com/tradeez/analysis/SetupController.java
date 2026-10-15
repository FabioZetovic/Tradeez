package com.tradeez.analysis;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradeez.analysis.SetupDtos.SetupLayerResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Detected setups for the Chart tab. A practice session's come through the practice API. */
@RestController
@RequestMapping("/api/market")
@Tag(name = "Market data", description = "Instrument catalogue and price series")
public class SetupController {

	private final SetupService setups;

	public SetupController(SetupService setups) {
		this.setups = setups;
	}

	@GetMapping("/instruments/{symbol}/setups")
	@Operation(summary = "What versioned rules found over a chart window",
		description = """
			Every hit of the named detectors (comma-separated, e.g. FVG) touching [from, to) and \
			confirmed by `to` (T9, DEC-049). Same range limits as the bars; one minute and above.""")
	public SetupLayerResponse setups(
			@PathVariable String symbol,
			@RequestParam(defaultValue = "5m") String timeframe,
			@RequestParam long from,
			@RequestParam long to,
			@RequestParam List<String> detectors) {
		return setups.forChart(symbol, timeframe, Instant.ofEpochSecond(from), Instant.ofEpochSecond(to),
			detectors);
	}
}
