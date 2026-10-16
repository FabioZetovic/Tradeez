package com.tradeez.practice;

import java.time.Instant;
import java.util.List;

import com.tradeez.marketdata.Bar;

/**
 * Decides what happened to a bracket trade.
 *
 * <p>This is the component the whole product rests on. A learner commits to a stop and a target,
 * the market plays out, and this class says which one was hit, when, and at what price. If it is
 * wrong, every score, every journal entry and every lesson drawn from them is wrong too.
 *
 * <h2>Why it walks the finest bars there are (DEC-008)</h2>
 * The learner watches five-minute candles. A single five-minute candle can easily have a high above
 * the target and a low below the stop, and from the candle alone there is no way to know which came
 * first. So the engine never looks at the candles the learner sees. It walks the finest bars inside
 * them, in order, and finds the first touch: one-second bars where they exist (simulated data, and
 * real data imported at one second), otherwise one-minute bars ({@link Resolution}).
 *
 * <h2>Why it can still say "don't know"</h2>
 * Finer data narrows the ambiguity; it does not remove it. If a single bar spans both the stop and
 * the target, the order is still unknown, and the engine says so: {@link Outcome#AMBIGUOUS}. On
 * one-minute bars that happens more often than on one-second bars — honestly reported, never guessed.
 * That was a correction Codex made to the original design, and it matters — the alternative is to
 * quietly pick an answer and present a guess as a fact. The result records the pessimistic exit so
 * there is a number to show, and every consumer must label it as an assumption (INVARIANT 6).
 *
 * <h2>Rules, versioned together (DEC-012)</h2>
 * <ul>
 *   <li>Entry is <b>at market</b>: the open of the first bar after the plan is locked.</li>
 *   <li>Touch fills. A long stop is hit when the low reaches it; a long target when the high does.</li>
 *   <li>Gaps fill at the open, not at the order price. A stop jumped over is filled where the market
 *       actually reopened, which is worse — that is what happens to real stops.</li>
 *   <li>Stops slip against the trader by {@link FillModel#stopSlippageTicks()}. Targets do not:
 *       they are resting limit orders, and a limit cannot fill at a worse price than its own.</li>
 *   <li>The market closing ends the trade. The caller supplies bars up to the session's close; when
 *       they run out, the position is flattened at the last close ({@link Outcome#TIME_EXIT}). A
 *       missing bar inside that window is a stretch with no trades, not a closure: real data is
 *       sparse — a quiet minute has no bar — and fill-1.0.0's rule that any gap meant the session had
 *       ended would have closed real trades early (T4).</li>
 *   <li>Commission is charged per contract per round trip.</li>
 *   <li>A running trade can be closed at market ({@link #closeAtMarket}, fill-1.2.0, DEC-057): it
 *       exits at the open of the first bar at or after the moment it is closed, like the entry — unless
 *       that open already reaches the stop or the target, which then fill first.</li>
 * </ul>
 *
 * Changing any of these changes results, so {@link #VERSION} must change with them. Every stored
 * result carries the version that produced it; bumping it is what keeps old results explicable.
 *
 * <p>Everything is integer ticks (INVARIANT 3). No floating point touches a price or an amount.
 */
public final class FillEngine {

	/** Bump on ANY change to fill behaviour. Stored on every result. */
	public static final String VERSION = "fill-1.2.0";

	private FillEngine() {
	}

	public enum Direction {
		LONG, SHORT;

		/** +1 for a long, -1 for a short: multiplies a raw price move into profit. */
		int sign() {
			return this == LONG ? 1 : -1;
		}
	}

	public enum Outcome {
		/** The target was reached first. */
		TARGET,
		/** The stop was reached first. */
		STOP,
		/**
		 * Both were inside a single bar, so the order is genuinely unknown. The recorded
		 * exit is the pessimistic one (the stop), and must be presented as an assumption.
		 */
		AMBIGUOUS,
		/** Neither was reached before the session closed; flattened at the last close. */
		TIME_EXIT,
		/** The learner closed the position at market before the plan's own exit (DEC-057). */
		CLOSED
	}

	/** The bars the resolution walked. */
	public enum Resolution {
		S1,
		M1;

		/** "second" or "minute", for explaining an ambiguous result in words. */
		public String unit() {
			return this == S1 ? "second" : "minute";
		}
	}

	/**
	 * Execution assumptions, kept separate from the plan so they can be varied and versioned.
	 *
	 * @param stopSlippageTicks               ticks a stop fill loses beyond its price
	 * @param commissionCentsPerContractRound commission per contract, entry plus exit
	 */
	public record FillModel(long stopSlippageTicks, long commissionCentsPerContractRound) {

		/** DEC-008's defaults: one tick of stop slippage, $2.50 a round trip. */
		public static final FillModel DEFAULT = new FillModel(1, 250);

		public FillModel {
			if (stopSlippageTicks < 0 || commissionCentsPerContractRound < 0) {
				throw new IllegalArgumentException("Slippage and commission cannot be negative.");
			}
		}

		/**
		 * The assumptions spelled out, stored on every result beside {@link FillEngine#VERSION}.
		 * The engine version says which rules ran; this says which numbers they ran with.
		 */
		public String version() {
			return "slip-" + stopSlippageTicks + "t/comm-" + commissionCentsPerContractRound + "c";
		}
	}

	public record Plan(Direction direction, long stopTicks, long targetTicks, int contracts) {

		public Plan {
			if (contracts <= 0) {
				throw new IllegalArgumentException("A trade needs at least one contract.");
			}
		}
	}

	/**
	 * What happened.
	 *
	 * @param pnlTicks  per contract, signed, after slippage, before commission
	 * @param maeTicks  maximum adverse excursion — how far the trade went against the entry. Never
	 *                  negative.
	 * @param mfeTicks  maximum favourable excursion — how far it went in the trader's favour. Never
	 *                  negative.
	 * @param gapExit   true when the exit was filled at an opening price beyond the order, rather than
	 *                  at the order's own price
	 */
	public record Fill(
		Outcome outcome,
		Instant entryTime,
		long entryTicks,
		Instant exitTime,
		long exitTicks,
		long pnlTicks,
		long maeTicks,
		long mfeTicks,
		boolean gapExit,
		Resolution resolution) {

		/** Net result in cents across all contracts, commission included. */
		public long netPnlCents(long tickValueCents, int contracts, FillModel model) {
			return pnlTicks * tickValueCents * contracts
				- model.commissionCentsPerContractRound() * contracts;
		}
	}

	/** Resolves a bracket trade on one-second bars. */
	public static Fill resolve(Plan plan, List<Bar> seconds, FillModel model) {
		return resolve(plan, seconds, model, Resolution.S1);
	}

	/**
	 * Resolves a bracket trade.
	 *
	 * @param bars       bars of {@code resolution} in time order, from the moment the plan was locked to
	 *                   the session's close. Must not be empty — a trade cannot be entered into a
	 *                   closed market.
	 * @param resolution what the bars are; recorded on the result
	 */
	public static Fill resolve(Plan plan, List<Bar> bars, FillModel model, Resolution resolution) {
		if (bars.isEmpty()) {
			throw new IllegalArgumentException("No market data to enter the trade into.");
		}

		final Direction dir = plan.direction();
		final int sign = dir.sign();
		final long stop = plan.stopTicks();
		final long target = plan.targetTicks();
		final long slip = model.stopSlippageTicks();

		final Bar first = bars.getFirst();
		final Instant entryTime = first.openTime();
		final long entry = first.openTicks();

		long mae = 0;
		long mfe = 0;

		for (int i = 0; i < bars.size(); i++) {
			Bar bar = bars.get(i);
			long open = bar.openTicks();

			// The open first. If the market opened beyond a level, the order fills at the open —
			// never at its own price, which the market skipped straight past.
			if (stopTouched(dir, open, open, stop)) {
				long exit = open - sign * slip;
				return fill(Outcome.STOP, entryTime, entry, bar.openTime(), exit, sign,
					Math.max(mae, adverse(sign, entry, exit)), mfe, open != stop, resolution);
			}
			if (targetTouched(dir, open, open, target)) {
				return fill(Outcome.TARGET, entryTime, entry, bar.openTime(), open, sign,
					mae, Math.max(mfe, favourable(sign, entry, open)), open != target, resolution);
			}

			boolean stopHit = stopTouched(dir, bar.lowTicks(), bar.highTicks(), stop);
			boolean targetHit = targetTouched(dir, bar.lowTicks(), bar.highTicks(), target);

			if (stopHit && targetHit) {
				// Both inside one bar. The order cannot be known from this data, and pretending
				// otherwise would be presenting a guess as a fact. Record the pessimistic exit so there
				// is something to show, flagged as what it is.
				long exit = stop - sign * slip;
				return fill(Outcome.AMBIGUOUS, entryTime, entry, bar.openTime(), exit, sign,
					Math.max(mae, adverse(sign, entry, exit)),
					Math.max(mfe, favourable(sign, entry, target)), false, resolution);
			}
			if (stopHit) {
				long exit = stop - sign * slip;
				// Only the exit price counts toward excursion on the exit bar: whatever else the bar
				// did happened after, or before, a position that no longer existed.
				return fill(Outcome.STOP, entryTime, entry, bar.openTime(), exit, sign,
					Math.max(mae, adverse(sign, entry, exit)), mfe, false, resolution);
			}
			if (targetHit) {
				return fill(Outcome.TARGET, entryTime, entry, bar.openTime(), target, sign,
					mae, Math.max(mfe, favourable(sign, entry, target)), false, resolution);
			}

			// Survived the whole bar: its extremes are genuine excursions.
			long worst = dir == Direction.LONG ? bar.lowTicks() : bar.highTicks();
			long best = dir == Direction.LONG ? bar.highTicks() : bar.lowTicks();
			mae = Math.max(mae, adverse(sign, entry, worst));
			mfe = Math.max(mfe, favourable(sign, entry, best));
		}

		// Ran out of data without a decision. The caller supplied a window that ended mid-session;
		// flatten at the last close.
		Bar last = bars.getLast();
		return fill(Outcome.TIME_EXIT, entryTime, entry, last.openTime(), last.closeTicks(),
			sign, mae, mfe, false, resolution);
	}

	/**
	 * Closes a running trade at market at {@code at} (DEC-057).
	 *
	 * <p>A market order fills at the next price there is: the open of the first bar at or after
	 * {@code at} — the same rule as the entry, and like the entry without slippage. Every bar before it
	 * was survived (the trade is still running), so their extremes count as excursions; the exit price
	 * is the last one that does.
	 *
	 * @param bars the bars the trade was resolved on: from the lock to the session's close
	 * @return the close, or null when the trade's own exit comes first — its stop or target is reached
	 *         before {@code at}, or the very open the close would fill at already reaches one of them,
	 *         where the resting order fills first
	 */
	public static Fill closeAtMarket(Plan plan, List<Bar> bars, Instant at, Resolution resolution) {
		if (bars.isEmpty()) {
			throw new IllegalArgumentException("No market data to close the trade in.");
		}
		final Direction dir = plan.direction();
		final int sign = dir.sign();
		final Bar first = bars.getFirst();
		final Instant entryTime = first.openTime();
		final long entry = first.openTicks();
		long mae = 0;
		long mfe = 0;
		for (Bar bar : bars) {
			long open = bar.openTicks();
			if (!bar.openTime().isBefore(at)) {
				if (stopTouched(dir, open, open, plan.stopTicks()) || targetTouched(dir, open, open, plan.targetTicks())) {
					return null;
				}
				return fill(Outcome.CLOSED, entryTime, entry, bar.openTime(), open, sign,
					Math.max(mae, adverse(sign, entry, open)), Math.max(mfe, favourable(sign, entry, open)), false,
					resolution);
			}
			if (stopTouched(dir, bar.lowTicks(), bar.highTicks(), plan.stopTicks())
					|| targetTouched(dir, bar.lowTicks(), bar.highTicks(), plan.targetTicks())) {
				return null;
			}
			long worst = dir == Direction.LONG ? bar.lowTicks() : bar.highTicks();
			long best = dir == Direction.LONG ? bar.highTicks() : bar.lowTicks();
			mae = Math.max(mae, adverse(sign, entry, worst));
			mfe = Math.max(mfe, favourable(sign, entry, best));
		}
		// No bar after the moment: the session closed first, and the plan's time exit stands.
		return null;
	}

	private static boolean stopTouched(Direction dir, long low, long high, long stop) {
		return dir == Direction.LONG ? low <= stop : high >= stop;
	}

	private static boolean targetTouched(Direction dir, long low, long high, long target) {
		return dir == Direction.LONG ? high >= target : low <= target;
	}

	/** How far {@code price} is against the position, in ticks; zero if it is not against it. */
	private static long adverse(int sign, long entry, long price) {
		return Math.max(0, -sign * (price - entry));
	}

	/** How far {@code price} is in the position's favour, in ticks; zero if it is not. */
	private static long favourable(int sign, long entry, long price) {
		return Math.max(0, sign * (price - entry));
	}

	private static Fill fill(Outcome outcome, Instant entryTime, long entry, Instant exitTime,
			long exit, int sign, long mae, long mfe, boolean gap, Resolution resolution) {
		return new Fill(outcome, entryTime, entry, exitTime, exit, sign * (exit - entry),
			mae, mfe, gap, resolution);
	}
}
