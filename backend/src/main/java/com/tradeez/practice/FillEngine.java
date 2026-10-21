package com.tradeez.practice;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *   <li>Entry is <b>at market</b>: the open of the first bar after the plan is locked, slipped against the trader
 *       by {@link FillModel#marketSlippageTicks()} (fill-1.3.0, DEC-072) — as is every market fill: a close at
 *       market, and the flat at the session's close.</li>
 *   <li>A long stop is hit when the low reaches it. A long target — a resting limit — when the high reaches it by
 *       {@link FillModel#limitThroughTicks()}: on the touch at 0, and only once price trades through it at 1, the
 *       queue ahead of it being unknown (fill-1.3.0).</li>
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
 *   <li>A plan may take part of the position off at a level before the target (fill-1.4.0, T28, DEC-074): a
 *       resting limit for some of the contracts, filled like the target, and on the way to it. A bar that reaches
 *       it and the stop is ambiguous like one that reaches the target and the stop, and assumed to be the stop.
 *       With the breakeven option the stop on the rest moves to the entry once the bar that filled the partial
 *       has closed.</li>
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
	public static final String VERSION = "fill-1.4.0";

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
	 * @param marketSlippageTicks             ticks a market fill — the entry, a close — loses beyond the open
	 * @param stopSlippageTicks               ticks a stop fill loses beyond its price
	 * @param limitThroughTicks               ticks price must trade beyond a target before it fills: 0, the touch
	 * @param commissionCentsPerContractRound commission and fees per contract, entry plus exit
	 */
	public record FillModel(long marketSlippageTicks, long stopSlippageTicks, long limitThroughTicks,
			long commissionCentsPerContractRound) {

		/** DEC-008's: one tick of stop slippage, $2.50 a round trip; every result before T27 was made with it. */
		public static final FillModel BASIC = new FillModel(0, 1, 0, 250);

		/**
		 * T27's, for a learner who has set nothing (DEC-072): a tick on every market and stop fill, a target filled
		 * once traded through, $4.50 a round trip — roughly a retail account's commission and exchange fees together.
		 */
		public static final FillModel STANDARD = new FillModel(1, 1, 1, 450);

		private static final Pattern FORMAT = Pattern.compile("(?:mkt-(\\d+)t/)?slip-(\\d+)t(?:/thru-(\\d+)t)?/comm-(\\d+)c");

		/** Stop slippage and commission alone, as fill-1.2.0 knew them. */
		public FillModel(long stopSlippageTicks, long commissionCentsPerContractRound) {
			this(0, stopSlippageTicks, 0, commissionCentsPerContractRound);
		}

		public FillModel {
			if (marketSlippageTicks < 0 || stopSlippageTicks < 0 || limitThroughTicks < 0 || commissionCentsPerContractRound < 0) {
				throw new IllegalArgumentException("Slippage and commission cannot be negative.");
			}
		}

		/**
		 * The assumptions spelled out, stored on every result beside {@link FillEngine#VERSION}. The engine
		 * version says which rules ran; this says which numbers they ran with. What fill-1.2.0 did not have is
		 * written only when it is not zero, so a model it could run reads as it always did.
		 */
		public String version() {
			return (marketSlippageTicks > 0 ? "mkt-" + marketSlippageTicks + "t/" : "") + "slip-" + stopSlippageTicks + "t"
				+ (limitThroughTicks > 0 ? "/thru-" + limitThroughTicks + "t" : "") + "/comm-" + commissionCentsPerContractRound
				+ "c";
		}

		/** The model a stored {@link #version()} names: a later close is filled as its plan was. */
		public static FillModel parse(String version) {
			Matcher m = FORMAT.matcher(version);
			if (!m.matches()) {
				throw new IllegalArgumentException("Not a fill model: " + version);
			}
			return new FillModel(m.group(1) == null ? 0 : Long.parseLong(m.group(1)), Long.parseLong(m.group(2)),
				m.group(3) == null ? 0 : Long.parseLong(m.group(3)), Long.parseLong(m.group(4)));
		}
	}

	/**
	 * Part of the position taken off before the target (T28, DEC-074): a resting limit for {@code contracts} of
	 * them at {@code ticks}, filled as the target is. With {@code breakeven}, the stop on the rest moves to the
	 * entry price once the candle that filled it has closed — never inside it, whose order cannot be known.
	 */
	public record Partial(long ticks, int contracts, boolean breakeven) {
	}

	public record Plan(Direction direction, long stopTicks, long targetTicks, int contracts, Partial partial) {

		public Plan {
			if (contracts <= 0) {
				throw new IllegalArgumentException("A trade needs at least one contract.");
			}
			if (partial != null && (partial.contracts() < 1 || partial.contracts() >= contracts)) {
				throw new IllegalArgumentException("A partial takes off some of the contracts: not none, and not all.");
			}
			if (partial != null && (Long.compare(partial.ticks(), stopTicks) != Long.compare(targetTicks, stopTicks)
					|| Long.compare(targetTicks, partial.ticks()) != Long.compare(targetTicks, stopTicks))) {
				throw new IllegalArgumentException("A partial lies between the stop and the target.");
			}
		}

		/** A bracket alone. */
		public Plan(Direction direction, long stopTicks, long targetTicks, int contracts) {
			this(direction, stopTicks, targetTicks, contracts, null);
		}
	}

	/**
	 * The contracts a partial took off (T28): when, where, and their result.
	 *
	 * @param pnlTicks per contract, signed, before commission
	 * @param gap      filled at an opening price beyond the level, rather than at the level
	 */
	public record Leg(Instant time, long exitTicks, long pnlTicks, int contracts, boolean gap) {
	}

	/**
	 * What happened.
	 *
	 * @param pnlTicks  per contract held to the exit — every one, or those a partial left — signed, after
	 *                  slippage, before commission
	 * @param maeTicks  maximum adverse excursion — how far the trade went against the entry. Never
	 *                  negative.
	 * @param mfeTicks  maximum favourable excursion — how far it went in the trader's favour. Never
	 *                  negative.
	 * @param gapExit   true when the exit was filled at an opening price beyond the order, rather than
	 *                  at the order's own price
	 * @param partial   the contracts a partial took off before the exit, or null
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
		Resolution resolution,
		Leg partial) {

		public long totalTicks(int contracts) {
			return FillEngine.totalTicks(pnlTicks, partial, contracts);
		}

		/** Net result in cents across all contracts, commission included. */
		public long netPnlCents(long tickValueCents, int contracts, FillModel model) {
			return totalTicks(contracts) * tickValueCents - model.commissionCentsPerContractRound() * contracts;
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
		return walk(plan, bars, model, resolution, null);
	}

	/**
	 * Closes a running trade at market at {@code at} (DEC-057).
	 *
	 * <p>A market order fills at the next price there is: the open of the first bar at or after
	 * {@code at} — the same rule as the entry, slipped like the entry (fill-1.3.0). Every bar before it
	 * was survived (the trade is still running), so their extremes count as excursions; the exit price
	 * is the last one that does. A partial filled before then stays filled (T28); the close takes the rest.
	 *
	 * @param bars the bars the trade was resolved on: from the lock to the session's close
	 * @return the close, or null when the trade's own exit comes first — its stop or target is reached
	 *         before {@code at}, or the very open the close would fill at already reaches one of them,
	 *         where the resting order fills first
	 */
	public static Fill closeAtMarket(Plan plan, List<Bar> bars, Instant at, FillModel model, Resolution resolution) {
		if (bars.isEmpty()) {
			throw new IllegalArgumentException("No market data to close the trade in.");
		}
		Fill fill = walk(plan, bars, model, resolution, at);
		return fill.outcome() == Outcome.CLOSED ? fill : null;
	}

	/**
	 * The trade, bar by bar, to its exit: the stop, the target, a close at market at {@code closeAt} when there
	 * is one, or the last bar. A partial is taken on the way, and with it, from the next bar, the stop on the
	 * rest may move to the entry.
	 */
	private static Fill walk(Plan plan, List<Bar> bars, FillModel model, Resolution resolution, Instant closeAt) {
		final Direction dir = plan.direction();
		final int sign = dir.sign();
		final long target = plan.targetTicks();
		final Partial partial = plan.partial();
		final long slip = model.stopSlippageTicks();
		final long through = model.limitThroughTicks();

		final Bar first = bars.getFirst();
		final Instant entryTime = first.openTime();
		final long entry = first.openTicks() + sign * model.marketSlippageTicks();

		long stop = plan.stopTicks();
		Leg taken = null;
		long mae = 0;
		long mfe = 0;

		for (Bar bar : bars) {
			long open = bar.openTicks();
			// The bar that filled the partial has closed: the rest's stop is at the entry from here on.
			if (taken != null && partial.breakeven()) {
				stop = entry;
			}

			// The open first. If the market opened beyond a level, the order fills at the open —
			// never at its own price, which the market skipped straight past.
			if (stopTouched(dir, open, open, stop)) {
				long exit = open - sign * slip;
				return fill(Outcome.STOP, entryTime, entry, bar.openTime(), exit, sign,
					Math.max(mae, adverse(sign, entry, exit)), mfe, open != stop, resolution, taken);
			}
			if (taken == null && partial != null && targetTouched(dir, open, open, partial.ticks(), through)) {
				taken = leg(bar.openTime(), entry, open, sign, partial, open != partial.ticks());
				mfe = Math.max(mfe, favourable(sign, entry, open));
			}
			if (targetTouched(dir, open, open, target, through)) {
				return fill(Outcome.TARGET, entryTime, entry, bar.openTime(), open, sign,
					mae, Math.max(mfe, favourable(sign, entry, open)), open != target, resolution, taken);
			}
			if (closeAt != null && !bar.openTime().isBefore(closeAt)) {
				long exit = open - sign * model.marketSlippageTicks();
				return fill(Outcome.CLOSED, entryTime, entry, bar.openTime(), exit, sign,
					Math.max(mae, adverse(sign, entry, exit)), Math.max(mfe, favourable(sign, entry, open)), false,
					resolution, taken);
			}

			boolean stopHit = stopTouched(dir, bar.lowTicks(), bar.highTicks(), stop);
			boolean targetHit = targetTouched(dir, bar.lowTicks(), bar.highTicks(), target, through);
			boolean partialHit = taken == null && partial != null
				&& targetTouched(dir, bar.lowTicks(), bar.highTicks(), partial.ticks(), through);

			if (stopHit && (targetHit || partialHit)) {
				// Both inside one bar. The order cannot be known from this data, and pretending
				// otherwise would be presenting a guess as a fact. Record the pessimistic exit — the stop, for
				// every contract still held — so there is something to show, flagged as what it is.
				long exit = stop - sign * slip;
				return fill(Outcome.AMBIGUOUS, entryTime, entry, bar.openTime(), exit, sign,
					Math.max(mae, adverse(sign, entry, exit)),
					Math.max(mfe, favourable(sign, entry, targetHit ? target : partial.ticks())), false, resolution,
					taken);
			}
			if (stopHit) {
				long exit = stop - sign * slip;
				// Only the exit price counts toward excursion on the exit bar: whatever else the bar
				// did happened after, or before, a position that no longer existed.
				return fill(Outcome.STOP, entryTime, entry, bar.openTime(), exit, sign,
					Math.max(mae, adverse(sign, entry, exit)), mfe, false, resolution, taken);
			}
			if (partialHit) {
				// Nearer than the target, so reached first on the way to it.
				taken = leg(bar.openTime(), entry, partial.ticks(), sign, partial, false);
			}
			if (targetHit) {
				return fill(Outcome.TARGET, entryTime, entry, bar.openTime(), target, sign,
					mae, Math.max(mfe, favourable(sign, entry, target)), false, resolution, taken);
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
		return fill(Outcome.TIME_EXIT, entryTime, entry, last.openTime(), last.closeTicks() - sign * model.marketSlippageTicks(),
			sign, mae, mfe, false, resolution, taken);
	}

	/** Every contract's ticks together: a partial's at its level, the rest's at the exit (T28). */
	public static long totalTicks(long pnlTicks, Leg partial, int contracts) {
		return partial == null ? pnlTicks * contracts
			: partial.pnlTicks() * partial.contracts() + pnlTicks * (contracts - partial.contracts());
	}

	private static Leg leg(Instant time, long entry, long exit, int sign, Partial partial, boolean gap) {
		return new Leg(time, exit, sign * (exit - entry), partial.contracts(), gap);
	}

	private static boolean stopTouched(Direction dir, long low, long high, long stop) {
		return dir == Direction.LONG ? low <= stop : high >= stop;
	}

	/** A target's limit fills when price reaches {@code through} ticks beyond it: the touch at 0. */
	private static boolean targetTouched(Direction dir, long low, long high, long target, long through) {
		return dir == Direction.LONG ? high - target >= through : target - low >= through;
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
			long exit, int sign, long mae, long mfe, boolean gap, Resolution resolution, Leg partial) {
		return new Fill(outcome, entryTime, entry, exitTime, exit, sign * (exit - entry),
			mae, mfe, gap, resolution, partial);
	}
}
