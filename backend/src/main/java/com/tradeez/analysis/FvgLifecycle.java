package com.tradeez.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tradeez.analysis.FvgDetector.Hit;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * What happened to each {@code fvg-1.0.0} gap after it formed (ICT-22), read candle by candle —
 * shared by {@link FvgStateDetector} and {@link IfvgDetector}.
 *
 * <p>For a bullish gap {@code [low, high]}, price above it: it is <b>tapped</b> when a candle trades
 * at or below its high, its <b>CE</b> is reached when a candle trades at or below the midpoint (in
 * half-ticks, ICT-21), it is <b>filled</b> when a candle trades at or below its low, and
 * <b>inverted</b> when a candle closes below its low. A bearish gap mirrors it. One candle can do
 * several at once. Tracking stops at inversion, or {@code maxAgeCandles} after the gap formed — the
 * rule's bounded memory, which is what keeps its lookback finite.
 */
final class FvgLifecycle {

	enum State {
		OPEN, TAPPED, CE, FILLED, INVERTED, EXPIRED
	}

	record Event(Hit gap, State state, Bar candle) {
	}

	private FvgLifecycle() {
	}

	/** Every state each gap in {@code segment} reached, in candle order; {@code OPEN} when it formed. */
	static List<Event> events(List<Bar> segment, Timeframe timeframe, FvgDetector.Parameters fvg,
			int maxAgeCandles) {
		List<Instant> opens = segment.stream().map(Bar::openTime).toList();
		List<Event> events = new ArrayList<>();
		for (Hit gap : FvgDetector.detect(segment, timeframe, fvg)) {
			int formed = Collections.binarySearch(opens, gap.candle3().openTime());
			events.add(new Event(gap, State.OPEN, gap.candle3()));
			boolean bullish = gap.direction() == FvgDetector.Direction.BULLISH;
			long low = gap.zoneLowTicks();
			long high = gap.zoneHighTicks();
			int reached = State.OPEN.ordinal();
			int last = Math.min(formed + maxAgeCandles, segment.size() - 1);
			for (int k = formed + 1; k <= last; k++) {
				Bar b = segment.get(k);
				int now = reached;
				if (bullish ? b.lowTicks() <= high : b.highTicks() >= low) {
					now = Math.max(now, State.TAPPED.ordinal());
				}
				if (bullish ? 2 * b.lowTicks() <= low + high : 2 * b.highTicks() >= low + high) {
					now = Math.max(now, State.CE.ordinal());
				}
				if (bullish ? b.lowTicks() <= low : b.highTicks() >= high) {
					now = Math.max(now, State.FILLED.ordinal());
				}
				if (bullish ? b.closeTicks() < low : b.closeTicks() > high) {
					now = State.INVERTED.ordinal();
				}
				for (int s = reached + 1; s <= now; s++) {
					events.add(new Event(gap, State.values()[s], b));
				}
				reached = now;
				if (reached == State.INVERTED.ordinal()) {
					break;
				}
				if (k == formed + maxAgeCandles && reached < State.FILLED.ordinal()) {
					events.add(new Event(gap, State.EXPIRED, b));
				}
			}
		}
		events.sort(Comparator.comparing((Event e) -> e.candle().openTime()));
		return events;
	}

	/** The event as a zone over the gap's life so far, ending at the candle that moved it. */
	static Detection detection(Event event, Detection.Direction direction, String kind, Timeframe timeframe) {
		Hit gap = event.gap();
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("state", event.state().name());
		evidence.put("gapAnchorTime", gap.anchorTime().getEpochSecond());
		evidence.put("gapDirection", gap.direction().name());
		evidence.put("ceHalfTicks", gap.zoneLowTicks() + gap.zoneHighTicks());
		evidence.put("candleTime", event.candle().openTime().getEpochSecond());
		evidence.put("candleHighTicks", event.candle().highTicks());
		evidence.put("candleLowTicks", event.candle().lowTicks());
		evidence.put("candleCloseTicks", event.candle().closeTicks());
		return new Detection(Detection.Shape.ZONE, direction, kind, gap.candle1().openTime(), gap.anchorTime(),
			event.candle().openTime(), event.candle().openTime().plus(timeframe.duration()), gap.zoneLowTicks(),
			gap.zoneHighTicks(), evidence);
	}
}
