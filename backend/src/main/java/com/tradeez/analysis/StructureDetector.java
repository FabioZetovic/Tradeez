package com.tradeez.analysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.SwingPoints.Label;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code structure-1.0.0} (ICT-03): each swing labelled against the one of its kind before it —
 * higher high {@code HH}, lower high {@code LH}, higher low {@code HL}, lower low {@code LL}, or equal
 * ({@code EH}, {@code EL}). Read in sequence the labels are the structure: bullish after an HH and an
 * HL, bearish after an LL and an LH. Known when the labelled swing is. The previous swing must lie
 * within {@value #MAX_SPAN} candles; order flow — whether price respects its arrays — is judgement and
 * is taught, not computed.
 */
@Component
public final class StructureDetector extends RuleDetector {

	static final int MAX_SPAN = 200;
	private static final int N = SwingDetector.STRENGTH;

	public StructureDetector() {
		super("STRUCTURE", "structure-1.0.0", "ICT-03", "ICT market structure: swing highs and lows in sequence",
			Map.of("strength", N, "maxSpanCandles", MAX_SPAN));
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return MAX_SPAN;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		return SwingPoints.labels(SwingPoints.find(segment, N), N, MAX_SPAN).stream()
			.map(l -> detection(segment, l, timeframe))
			.toList();
	}

	private static Detection detection(List<Bar> segment, Label label, Timeframe timeframe) {
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("price", label.swing().price());
		evidence.put("previousTime", segment.get(label.previous().index()).openTime().getEpochSecond());
		evidence.put("previousPrice", label.previous().price());
		Detection point = SwingDetector.point(segment, label.swing(), label.kind(), segment.get(label.earliest()),
			label.swing().confirmIndex(N), timeframe, evidence);
		// The label says which way structure points, whichever side of price the swing sits.
		Detection.Direction direction = switch (label.kind()) {
			case "HH", "HL" -> Detection.Direction.BULLISH;
			case "LH", "LL" -> Detection.Direction.BEARISH;
			default -> Detection.Direction.NEUTRAL;
		};
		return new Detection(point.shape(), direction, point.kind(), point.start(), point.anchor(), point.end(),
			point.confirmedAt(), point.lowTicks(), point.highTicks(), point.evidence());
	}
}
