package com.tradeez.analysis;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

import io.swagger.v3.oas.annotations.media.Schema;

/** Wire shapes for detected setups. Times are epoch seconds on the chart's own time basis. */
public final class SetupDtos {

	private SetupDtos() {
	}

	@Schema(description = """
		What the chosen rules found over a chart, and the rules themselves (T9, DEC-049). Only setups \
		already confirmed at the chart's right edge — or the replay's cursor — are included. Times are \
		on the same basis as the bars: real epoch seconds on the Chart tab, the session's fictional \
		week in practice (DEC-045). The evidence behind each hit is not sent: its timestamps are real.""")
	public record SetupLayerResponse(List<RuleView> rules, List<SetupView> setups) {
	}

	@Schema(description = """
		A versioned rule (DEC-026). A hit claims only that the candles satisfy this rule with these \
		parameters — not that the pattern matters (INVARIANT 8).""")
	public record RuleView(String detector, String version, String conceptId, Map<String, Object> parameters,
			String methodologySource) {

		public static RuleView of(Detector detector) {
			return new RuleView(detector.id(), detector.ruleVersion(), detector.conceptId(),
				detector.parameters(), detector.methodologySource());
		}
	}

	@Schema(description = """
		One hit. ZONE spans lowTicks..highTicks; LEVEL and POINT are one price (lowTicks = highTicks). \
		Prices in INTEGER TICKS. start, anchor and end are candle open times; confirmedAt is when the \
		hit could first be known.""")
	public record SetupView(String detector, Detection.Shape shape, Detection.Direction direction, long start,
			long anchor, long end, long confirmedAt, long lowTicks, long highTicks) {
	}

	static SetupLayerResponse of(List<Detector> detectors, List<DetectedSetup> setups,
			ToLongFunction<Instant> time) {
		return new SetupLayerResponse(
			detectors.stream().map(RuleView::of).toList(),
			setups.stream()
				.map(s -> new SetupView(s.getDetector(), Detection.Shape.valueOf(s.getShape()),
					Detection.Direction.valueOf(s.getDirection()), time.applyAsLong(s.getStartTime()),
					time.applyAsLong(s.getAnchorTime()), time.applyAsLong(s.getEndTime()),
					time.applyAsLong(s.getConfirmedAt()), s.getZoneLowTicks(), s.getZoneHighTicks()))
				.toList());
	}
}
