package com.tradeez.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;

/**
 * The contract in {@link Detector}, checked for every detector the application registers (DEC-049).
 *
 * <p>A replay is served from one scan of its whole window, filtered by the cursor. That is only the
 * truth — exactly what the learner could have known — if each detector is causal, and the scanner's
 * results only stop depending on where a scan began if {@link Detector#lookbackBars()} is honest.
 * A new detector that breaks either fails here before it reaches a learner.
 */
class DetectorContractTest extends AbstractIntegrationTest {

	/** A Friday to a Tuesday: a weekend, and the daily halts, in the middle. */
	private static final Instant FRIDAY = Instant.parse("2024-03-15T12:00:00Z");
	private static final Instant TUESDAY = Instant.parse("2024-03-19T00:00:00Z");

	@Autowired
	DetectorRegistry detectors;

	@Autowired
	MarketDataService marketData;

	private List<Bar> bars(Timeframe timeframe, Instant from, Instant to) {
		Instrument nq = marketData.requireInstrument("DEMO-NQ");
		return marketData.internalBars(nq, timeframe, from, to, 42).bars();
	}

	@Test
	@DisplayName("every detector is registered once and names a lookback")
	void registered() {
		assertThat(detectors.all()).isNotEmpty()
			.allSatisfy(d -> assertThat(d.lookbackBars()).isPositive());
		assertThat(detectors.require("FVG")).isInstanceOf(FvgDetector.class);
	}

	@Test
	@DisplayName("deterministic, and at most one detection per anchor and direction")
	void deterministic() {
		List<Bar> bars = bars(Timeframe.M5, FRIDAY, TUESDAY);
		for (Detector detector : detectors.all()) {
			List<Detection> found = detector.detect(bars, Timeframe.M5);
			assertThat(detector.detect(List.copyOf(bars), Timeframe.M5)).isEqualTo(found);
			assertThat(found).extracting(d -> d.anchor() + " " + d.direction()).doesNotHaveDuplicates();
		}
	}

	@Test
	@DisplayName("causal: each detection is found from the candles closed by its confirmation, and no sooner")
	void causal() {
		for (Timeframe timeframe : List.of(Timeframe.M1, Timeframe.M5, Timeframe.M15)) {
			Instant to = timeframe == Timeframe.M1 ? FRIDAY.plusSeconds(36 * 3600) : TUESDAY;
			List<Bar> bars = bars(timeframe, FRIDAY, to);
			for (Detector detector : detectors.all()) {
				List<Detection> all = detector.detect(bars, timeframe);
				assertThat(all).as("%s on %s finds something to test", detector.id(), timeframe.code())
					.isNotEmpty();
				// Knowable at its confirmation: the candles closed by then are enough to find it.
				for (Detection d : all) {
					List<Bar> closed = bars.stream()
						.filter(b -> !b.openTime().plus(timeframe.duration()).isAfter(d.confirmedAt()))
						.toList();
					assertThat(detector.detect(closed, timeframe)).as("%s at %s", detector.id(), d.confirmedAt())
						.contains(d);
				}
				// And not before: cut anywhere, and only what was confirmed by the cut is found.
				for (int k = 1; k < bars.size(); k += 3) {
					Instant cut = bars.get(k).openTime();
					List<Detection> prefix = detector.detect(bars.subList(0, k), timeframe);
					assertThat(prefix).as("%s cut at %s", detector.id(), cut)
						.isEqualTo(all.stream().filter(d -> !d.confirmedAt().isAfter(cut)).toList());
				}
			}
		}
	}

	@Test
	@DisplayName("lookback: given only lookbackBars() candles before a point, it finds the same from there on")
	void lookbackIsEnough() {
		for (Timeframe timeframe : List.of(Timeframe.M5, Timeframe.M15)) {
			List<Bar> bars = bars(timeframe, FRIDAY, TUESDAY);
			for (Detector detector : detectors.all()) {
				List<Detection> all = detector.detect(bars, timeframe);
				for (int k = 0; k < bars.size(); k += 3) {
					Instant from = bars.get(k).openTime();
					List<Bar> cut = bars.subList(Math.max(0, k - detector.lookbackBars()), bars.size());
					assertThat(detector.detect(cut, timeframe).stream()
							.filter(d -> !d.confirmedAt().isBefore(from)).toList())
						.as("%s from %s", detector.id(), from)
						.isEqualTo(all.stream().filter(d -> !d.confirmedAt().isBefore(from)).toList());
				}
			}
		}
	}
}
