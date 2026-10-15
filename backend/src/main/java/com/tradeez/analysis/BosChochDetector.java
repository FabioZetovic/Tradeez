package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.SwingPoints.Label;
import com.tradeez.analysis.SwingPoints.Swing;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * The SMC words for breaks of structure (ICT-06), read under {@code structure-1.0.0}: a candle
 * <b>closes</b> beyond the latest swing — the first to do so. With the trend (bullish structure, a
 * close above the latest high) it is a break of structure, {@code BOS}; against it, the first sign of
 * a turn, a change of character, {@code CHOCH}. Structure is read from the latest swing high and low,
 * and only when both are labelled: otherwise a break is not named.
 *
 * <p>These are Smart Money Concepts terms, not ICT's; they are here because learners meet them
 * everywhere. ICT's own turn, the market structure shift, also asks for a sweep and displacement
 * ({@link MssDetector}). Everything a break reads lies within {@value StructureDetector#MAX_SPAN}
 * candles of it.
 */
@Component
public final class BosChochDetector extends RuleDetector {

	private static final int N = SwingDetector.STRENGTH;
	private static final int MAX_SPAN = StructureDetector.MAX_SPAN;

	public BosChochDetector() {
		super("BOS_CHOCH", "structure-1.0.0", "ICT-06", "SMC break of structure and change of character",
			Map.of("strength", N, "maxSpanCandles", MAX_SPAN, "breakBy", "close"));
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return MAX_SPAN;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Swing> swings = SwingPoints.find(segment, N);
		Map<Swing, Label> labelOf = new LinkedHashMap<>();
		SwingPoints.labels(swings, N, MAX_SPAN).forEach(l -> labelOf.put(l.swing(), l));

		List<Detection> found = new ArrayList<>();
		Swing high = null;
		Swing low = null;
		int next = 0;
		for (int e = 1; e < segment.size(); e++) {
			// The latest swing of each kind confirmed before candle e opened.
			while (next < swings.size() && swings.get(next).confirmIndex(N) <= e - 1) {
				Swing swing = swings.get(next++);
				if (swing.high()) {
					high = swing;
				} else {
					low = swing;
				}
			}
			Label highLabel = high == null ? null : labelOf.get(high);
			Label lowLabel = low == null ? null : labelOf.get(low);
			if (highLabel == null || lowLabel == null
					|| Math.min(highLabel.earliest(), lowLabel.earliest()) < e - MAX_SPAN + 1) {
				continue;
			}
			boolean bullish = highLabel.kind().equals("HH") && lowLabel.kind().equals("HL");
			boolean bearish = highLabel.kind().equals("LH") && lowLabel.kind().equals("LL");
			if (!bullish && !bearish) {
				continue;
			}
			for (Label broken : List.of(highLabel, lowLabel)) {
				if (firstClose(segment, broken.swing(), e)) {
					boolean withTrend = broken.swing().high() ? bullish : bearish;
					found.add(detection(segment, broken, highLabel, lowLabel, e, withTrend ? "BOS" : "CHOCH", timeframe));
				}
			}
		}
		return found;
	}

	/** Candle {@code e} closes beyond the swing, and is the first to since the swing formed. */
	static boolean firstClose(List<Bar> bars, Swing swing, int e) {
		if (!closesBeyond(bars.get(e), swing)) {
			return false;
		}
		for (int k = swing.index() + 1; k < e; k++) {
			if (closesBeyond(bars.get(k), swing)) {
				return false;
			}
		}
		return e > swing.index();
	}

	private static boolean closesBeyond(Bar bar, Swing swing) {
		return swing.high() ? bar.closeTicks() > swing.price() : bar.closeTicks() < swing.price();
	}

	private static Detection detection(List<Bar> segment, Label broken, Label high, Label low, int e, String kind,
			Timeframe timeframe) {
		Bar breaking = segment.get(e);
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("brokenTime", segment.get(broken.swing().index()).openTime().getEpochSecond());
		evidence.put("brokenPrice", broken.swing().price());
		evidence.put("closeTicks", breaking.closeTicks());
		evidence.put("highLabel", high.kind());
		evidence.put("lowLabel", low.kind());
		return new Detection(Detection.Shape.LEVEL,
			broken.swing().high() ? Detection.Direction.BULLISH : Detection.Direction.BEARISH, kind,
			segment.get(broken.swing().index()).openTime(), breaking.openTime(), breaking.openTime(),
			breaking.openTime().plus(timeframe.duration()), broken.swing().price(), broken.swing().price(), evidence);
	}
}
