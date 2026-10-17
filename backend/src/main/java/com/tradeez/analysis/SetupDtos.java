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
	public record SetupLayerResponse(List<RuleView> rules, List<SetupView> setups,
			@Schema(description = """
				Contract rolls inside the window. No detector sees across one, so a zone still live at a \
				roll is drawn up to it and no further.""")
			List<Long> rolls) {
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
		Prices in INTEGER TICKS. kind tells a detector's kinds of hit apart — FVG_STATE: OPEN, TAPPED, CE, \
		FILLED, INVERTED, EXPIRED; OPENING_GAP: NDOG, NWOG; VOID: GAP, VOID; the blocks (OB, BREAKER, \
		MITIGATION, REJECTION, UNICORN): FORMED, TAPPED, MEAN_THRESHOLD, INVALIDATED, EXPIRED — and is null \
		when it has one. start, anchor and end are candle open times; confirmedAt is when the hit could \
		first be known.""")
	public record SetupView(String detector, Detection.Shape shape, Detection.Direction direction, String kind,
			long start, long anchor, long end, long confirmedAt, long lowTicks, long highTicks,
			@Schema(description = """
				The line drawn through a zone, in HALF-TICKS so an odd range's middle stays exact: a gap's \
				consequent encroachment, a block's mean threshold — the middle of its candles' bodies, not \
				of its range (T17). For a level or a point, twice its price.""")
			long midHalfTicks) {

		/** A stored hit, its times passed through {@code time}: a replay's clock, or plain epoch seconds. */
		public static SetupView of(DetectedSetup s, ToLongFunction<Instant> time) {
			return new SetupView(s.getDetector(), Detection.Shape.valueOf(s.getShape()),
				Detection.Direction.valueOf(s.getDirection()), s.getKind(), time.applyAsLong(s.getStartTime()),
				time.applyAsLong(s.getAnchorTime()), time.applyAsLong(s.getEndTime()),
				time.applyAsLong(s.getConfirmedAt()), s.getZoneLowTicks(), s.getZoneHighTicks(),
				s.getEvidence().get("meanHalfTicks") instanceof Number mean ? mean.longValue()
					: s.getZoneLowTicks() + s.getZoneHighTicks());
		}
	}

	static SetupLayerResponse of(List<Detector> detectors, List<DetectedSetup> setups, List<Instant> rolls,
			ToLongFunction<Instant> time) {
		return new SetupLayerResponse(
			detectors.stream().map(RuleView::of).toList(),
			setups.stream().map(s -> SetupView.of(s, time)).toList(),
			rolls.stream().map(time::applyAsLong).toList());
	}
}
