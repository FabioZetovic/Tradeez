package com.tradeez.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.SwingPoints.Swing;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code smt-1.0.0} (ICT-66, T20, DEC-073): SMT divergence — two normally correlated markets disagreeing at a
 * swing. A swing high on this market and the other's earliest swing high at most {@value #ALIGN} candles from
 * it are each compared with the swing high before it on its own market, and those two must be as close. When
 * one is beyond its previous and the other is not — equal counts as not — the markets disagree, and ICT reads
 * the one that failed as a crack in the move: bearish at highs. Lows mirror it, bullish.
 *
 * <p>Swings are swing-1.0.0's, found on each market's own candles, so they are the swings either chart
 * shows. The point is this market's swing, whichever of the two went beyond. It is known at the close of
 * this market's first candle by which both swings are confirmed; everything read lies within
 * {@value #MAX_SPAN} of this market's candles.
 *
 * <p>Causal on both markets: the other's candles are those over this segment's minutes ({@link Segments#beside}),
 * and of two swings of the other's close to this one, the earlier — confirmed first — is taken, so a later one
 * can never replace a pair already found.
 */
@Component
public final class SmtDetector extends RuleDetector {

	static final int ALIGN = 2;
	static final int MAX_SPAN = 200;
	private static final int N = SwingDetector.STRENGTH;

	/**
	 * The markets compared, each with the one beside it. Both are quoted in quarter points, so the words print
	 * the other's prices at this chart's tick.
	 */
	private static final Map<String, String> PAIRS = Map.of("NQ", "ES", "ES", "NQ");

	public SmtDetector() {
		super("SMT", "smt-1.0.0", "ICT-66", "ICT SMT divergence between correlated index futures",
			Map.of("strength", N, "alignCandles", ALIGN, "maxSpanCandles", MAX_SPAN));
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return MAX_SPAN;
	}

	@Override
	public String companionOf(String symbol) {
		return PAIRS.get(symbol);
	}

	/** Alone, a market has nothing to diverge from. */
	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		return List.of();
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Companion companion, Timeframe timeframe) {
		List<Bar> theirs = companion.bars();
		long reach = ALIGN * timeframe.seconds();
		List<Swing> ours = SwingPoints.find(segment, N);
		List<Swing> others = SwingPoints.find(theirs, N);
		List<Detection> found = new ArrayList<>();
		for (boolean high : new boolean[] { true, false }) {
			List<Swing> mine = SwingPoints.side(ours, high);
			List<Swing> other = SwingPoints.side(others, high);
			for (int j = 1; j < mine.size(); j++) {
				Swing now = mine.get(j);
				Swing before = mine.get(j - 1);
				int k = earliestNear(other, theirs, open(segment, now), reach);
				if (k < 1) {
					continue;
				}
				Swing otherNow = other.get(k);
				Swing otherBefore = other.get(k - 1);
				if (Math.abs(open(segment, before).getEpochSecond() - open(theirs, otherBefore).getEpochSecond()) > reach
						|| now.beyond(before) == otherNow.beyond(otherBefore)) {
					continue;
				}
				// This market's first candle by which both swings are known.
				Instant theirsKnown = theirs.get(otherNow.confirmIndex(N)).openTime();
				int confirm = now.confirmIndex(N);
				while (segment.get(confirm).openTime().isBefore(theirsKnown)) {
					confirm++;
				}
				Instant earliest = min(segment.get(before.index() - N).openTime(),
					theirs.get(otherBefore.index() - N).openTime());
				int first = before.index() - N;
				while (first > 0 && !segment.get(first - 1).openTime().isBefore(earliest)) {
					first--;
				}
				if (confirm - first + 1 > MAX_SPAN) {
					continue;
				}
				found.add(detection(segment, theirs, companion.symbol(), now, before, otherNow, otherBefore, earliest,
					confirm, timeframe));
			}
		}
		return found;
	}

	/** The first of the other's swings at most {@code reach} seconds from {@code at}, or -1. */
	private static int earliestNear(List<Swing> swings, List<Bar> bars, Instant at, long reach) {
		for (int k = 0; k < swings.size(); k++) {
			long apart = open(bars, swings.get(k)).getEpochSecond() - at.getEpochSecond();
			if (Math.abs(apart) <= reach) {
				return k;
			}
			if (apart > reach) {
				break;
			}
		}
		return -1;
	}

	private static Instant open(List<Bar> bars, Swing swing) {
		return bars.get(swing.index()).openTime();
	}

	private static Instant min(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

	private static Detection detection(List<Bar> segment, List<Bar> theirs, String symbol, Swing now, Swing before,
			Swing otherNow, Swing otherBefore, Instant earliest, int confirm, Timeframe timeframe) {
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("price", now.price());
		evidence.put("previousTime", open(segment, before).getEpochSecond());
		evidence.put("previousPrice", before.price());
		evidence.put("companion", symbol);
		evidence.put("companionTime", open(theirs, otherNow).getEpochSecond());
		evidence.put("companionPrice", otherNow.price());
		evidence.put("companionPreviousTime", open(theirs, otherBefore).getEpochSecond());
		evidence.put("companionPreviousPrice", otherBefore.price());
		Instant end = segment.get(confirm).openTime();
		return new Detection(Detection.Shape.POINT,
			now.high() ? Detection.Direction.BEARISH : Detection.Direction.BULLISH, null, earliest, open(segment, now),
			end, end.plus(timeframe.duration()), now.price(), now.price(), evidence);
	}
}
