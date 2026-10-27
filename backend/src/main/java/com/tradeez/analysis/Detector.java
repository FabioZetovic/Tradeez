package com.tradeez.analysis;

import java.util.List;
import java.util.Map;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * A versioned rule that finds one kind of setup in candles (T9, DEC-026, DEC-049).
 *
 * <p>What a detection claims is that the candles satisfy rule {@link #ruleVersion()} with
 * {@link #parameters()} — not that the pattern matters, nor that ICT is right (INVARIANT 8).
 *
 * <h2>The contract every detector keeps</h2>
 * <ul>
 * <li><b>Deterministic.</b> The same candles and parameters give the same detections.</li>
 * <li><b>Causal.</b> Whether a detection exists, and every number in it, depends only on candles
 * that had closed by its {@link Detection#confirmedAt() confirmation}, and that is the first moment
 * they had. This is what lets a replay be
 * served from one scan of its whole window: a detection confirmed by the cursor is exactly the one a
 * scan up to the cursor would have found. {@code DetectorContractTest} checks it for every detector
 * the application registers.</li>
 * <li><b>Unbroken.</b> It is handed one {@link Segments segment} at a time — the candles of one
 * contract — and reports no pattern whose own candles span a session break
 * ({@link Segments#consecutive}). Context it looks back on may lie across a break; the pattern may
 * not.</li>
 * <li><b>One per place.</b> At most one detection per anchor candle, direction and
 * {@link Detection#kind() kind}. That is a hit's identity in {@code detected_setup}; a second would be
 * dropped as a duplicate.</li>
 * </ul>
 */
public interface Detector {

	/** Short and stable, stored on every hit: {@code FVG}. */
	String id();

	/** Bumped on any change to what qualifies: {@code fvg-1.0.0}. */
	String ruleVersion();

	String methodologySource();

	/** The concept this rule is a reading of, as numbered in {@code ICT_CONCEPTS.md}: {@code ICT-20}. */
	String conceptId();

	/** Stored with every hit: two parameter sets are two different claims (DEC-026). */
	Map<String, Object> parameters();

	/**
	 * How many candles a detection may read, counting back from its confirmation: everything it
	 * depends on opened among the last {@code lookbackBars} candles before {@link
	 * Detection#confirmedAt()}. The scanner hands a detector exactly that many candles before the
	 * stretch it scans, so what it finds never depends on where a scan began.
	 *
	 * <p>Per timeframe, because some rules span clock time rather than candles: an opening range gap
	 * reaches back from 09:30 to the previous afternoon, a thousand one-minute candles or seventy
	 * fifteen-minute ones.
	 */
	int lookbackBars(Timeframe timeframe);

	/**
	 * Whether the rule reads candles of this timeframe at all (T21). Some only make sense where candles start
	 * at the moments their periods do — midnight, 08:30, 09:30 — and find nothing elsewhere; evidence counted
	 * there would read "none in 507 days" for a rule that never looked.
	 */
	default boolean runsOn(Timeframe timeframe) {
		return true;
	}

	/** Every detection in one segment: candles in time order, of one contract, all at {@code timeframe}. */
	List<Detection> detect(List<Bar> segment, Timeframe timeframe);

	/**
	 * The market this rule reads beside {@code symbol}, or null for a rule that reads one market (T20, DEC-073):
	 * SMT compares NQ with ES.
	 */
	default String companionOf(String symbol) {
		return null;
	}

	/**
	 * Every detection in one segment, read beside the companion market's candles over the same minutes
	 * ({@link Segments#beside}). Split at both markets' rolls, so they too are one contract's. The contract
	 * holds for both: a detection depends only on companion candles that had opened by the segment candle
	 * at whose close it is confirmed. A rule that reads one market ignores them.
	 */
	default List<Detection> detect(List<Bar> segment, Companion companion, Timeframe timeframe) {
		return detect(segment, timeframe);
	}

	/**
	 * The same, told which market the candles are ({@code symbol}, or null when unknown). A rule whose size is
	 * set per market reads it (BUG-099: a volume imbalance's minimum); the rest ignore it.
	 */
	default List<Detection> detect(List<Bar> segment, String symbol, Companion companion, Timeframe timeframe) {
		return detect(segment, companion, timeframe);
	}

	/** Another market's candles beside a segment (T20), in time order; {@link #NONE} for a rule that reads one. */
	record Companion(String symbol, List<Bar> bars) {

		public static final Companion NONE = new Companion(null, List.of());
	}
}
