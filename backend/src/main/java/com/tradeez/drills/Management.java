package com.tradeez.drills;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.tradeez.analysis.RuleText;
import com.tradeez.drills.DrillDtos.ContextView;
import com.tradeez.drills.DrillDtos.QuestionView;
import com.tradeez.drills.DrillDtos.RuleView;
import com.tradeez.marketdata.Bar;

/**
 * {@value #VERSION} (T26, DEC-071): mode E of the drill engine (DEC-023) — a trade is open and in profit; hold it,
 * move the stop to breakeven, or take half off? Graded by what real price did with each, never by a rule of thumb.
 *
 * <p><b>The trade.</b> Opened at the close of a candle, long or short, its stop {@value #STOP_ATR} average true ranges
 * of the {@value #ATR_CANDLES} candles before away (at least {@value #MIN_STOP_TICKS} ticks), its target
 * {@value #TARGET_R}R. <b>The moment</b> is the first close at least {@value #OPEN_R}R in its favour, at least
 * {@value #MIN_HELD} candles after the entry, with the stop untouched and the target not reached.
 *
 * <p><b>The choices,</b> each played out on the candles after the moment, to the first level reached or
 * {@value #HORIZON_CANDLES} candles: <i>hold</i> — the stop and the target as they are; <i>breakeven</i> — the stop
 * moved to the entry; <i>half</i> — half closed at the next open, the rest held. A candle that opens beyond a level
 * fills at its open; one that reaches both is read on its one-minute candles, and a moment where even they cannot
 * tell is never asked. No slippage, no commission.
 */
public final class Management {

	public static final String VERSION = "manage-1.0.0";

	static final int ATR_CANDLES = 14;
	static final double STOP_ATR = 1.5;
	static final long MIN_STOP_TICKS = 8;
	static final int TARGET_R = 3;
	static final double OPEN_R = 1.0;
	static final int MIN_HELD = 3;
	static final int HORIZON_CANDLES = Prediction.HORIZON_CANDLES;

	public enum Choice {
		HOLD, BREAKEVEN, HALF
	}

	/** An open trade at the moment the chart stops. Prices in ticks. */
	public record Trade(boolean longSide, Instant entryTime, long entry, long stop, long target, Instant moment,
			long current, double openR) {

		long risk() {
			return Math.abs(entry - stop);
		}
	}

	/**
	 * @param how TARGET, STOP (the original stop, or the entry for breakeven), or HORIZON; with _GAP when a candle
	 *            opened beyond the level
	 */
	public record Exit(Choice choice, double r, long price, Instant at, int candles, String how) {
	}

	/** @param best the choices that paid most — more than one on a tie */
	public record Outcome(List<Exit> exits, List<Choice> best, Instant decidedAt) {
	}

	/** What a drill keeps: the trade, what each choice made, and the next open the half was closed at. */
	public record Asked(Trade trade, Outcome outcome, long halfPrice) {
	}

	private static final String DEFINITION = "A trade opened at a candle's close, its stop " + STOP_ATR + " times the "
		+ "average range of the " + ATR_CANDLES + " candles before (at least " + MIN_STOP_TICKS + " ticks), its target "
		+ TARGET_R + "R. The chart stops at the first close at least " + OPEN_R + "R in profit, " + MIN_HELD + " or more "
		+ "candles in, the stop untouched. Each choice is played out on the candles after, to the first level reached or "
		+ HORIZON_CANDLES + " candles: a candle opening beyond a level fills at its open, one reaching both is read on its "
		+ "one-minute candles, and a moment they cannot settle is never asked. No slippage, no commission.";

	/** The drill's rule card: what the trade is and how the choices are played out. */
	static RuleView rule() {
		return new RuleView("MANAGE", VERSION, Map.of("atrCandles", ATR_CANDLES, "stopAtr", STOP_ATR, "targetR", TARGET_R,
			"openR", OPEN_R, "minHeld", MIN_HELD, "horizonCandles", HORIZON_CANDLES), DEFINITION,
			"A drill on trade management (ICT-82); the trade is a construction, not an ICT setup");
	}

	/** The open trade as the chart draws it: its entry, stop and target from the entry candle on. */
	static QuestionView questionView(Trade t, BigDecimal tickSize) {
		long from = t.entryTime().getEpochSecond();
		return new QuestionView(VERSION, DEFINITION, t.current(), List.of(), List.of(
			new ContextView("TRADE_TARGET", "LEVEL", from, t.target(), t.target(), "Target " + RuleText.price(t.target(), tickSize)),
			new ContextView("TRADE_ENTRY", "LEVEL", from, t.entry(), t.entry(), (t.longSide() ? "Long" : "Short") + " from "
				+ RuleText.price(t.entry(), tickSize) + " · now " + signed(t.openR())),
			new ContextView("TRADE_STOP", "LEVEL", from, t.stop(), t.stop(), "Stop " + RuleText.price(t.stop(), tickSize))));
	}

	private Management() {
	}

	/**
	 * The trade opened at the close of {@code bars[entryIndex]}, and the moment it first stands {@value #OPEN_R}R in
	 * profit; null when the stop or the target comes first, or the candles run out.
	 *
	 * @param step a candle's length in seconds
	 */
	static Trade open(List<Bar> bars, int entryIndex, boolean longSide, long step) {
		if (entryIndex < ATR_CANDLES) {
			return null;
		}
		long ranges = 0;
		for (int i = entryIndex - ATR_CANDLES + 1; i <= entryIndex; i++) {
			Bar bar = bars.get(i);
			long previousClose = bars.get(i - 1).closeTicks();
			ranges += Math.max(bar.highTicks() - bar.lowTicks(),
				Math.max(Math.abs(bar.highTicks() - previousClose), Math.abs(bar.lowTicks() - previousClose)));
		}
		long distance = Math.max(MIN_STOP_TICKS, Math.round(STOP_ATR * ranges / ATR_CANDLES));
		int sign = longSide ? 1 : -1;
		Bar entryBar = bars.get(entryIndex);
		long entry = entryBar.closeTicks();
		long stop = entry - sign * distance;
		long target = entry + sign * TARGET_R * distance;
		for (int j = entryIndex + 1; j < bars.size(); j++) {
			Bar bar = bars.get(j);
			if (longSide ? bar.lowTicks() <= stop : bar.highTicks() >= stop) {
				return null;
			}
			if (longSide ? bar.highTicks() >= target : bar.lowTicks() <= target) {
				return null;
			}
			double r = (double) sign * (bar.closeTicks() - entry) / distance;
			if (j - entryIndex >= MIN_HELD && r >= OPEN_R) {
				return new Trade(longSide, entryBar.openTime().plusSeconds(step), entry, stop, target,
					bar.openTime().plusSeconds(step), bar.closeTicks(), round2(r));
			}
		}
		return null;
	}

	/** Each choice played out on {@code future}; null when any of them cannot be told from what is stored. */
	static Outcome resolve(Trade t, List<Bar> future, Function<Bar, List<Bar>> minutesOf) {
		if (future.size() < HORIZON_CANDLES) {
			return null;
		}
		Exit hold = exit(Choice.HOLD, t, t.stop(), future, minutesOf);
		Exit breakeven = exit(Choice.BREAKEVEN, t, t.entry(), future, minutesOf);
		if (hold == null || breakeven == null) {
			return null;
		}
		long half = future.getFirst().openTicks();
		double halfR = round2((r(t, half) + hold.r()) / 2);
		Exit halfOff = new Exit(Choice.HALF, halfR, hold.price(), hold.at(), hold.candles(), hold.how());
		List<Exit> exits = List.of(hold, breakeven, halfOff);
		double most = exits.stream().mapToDouble(Exit::r).max().orElseThrow();
		List<Choice> best = new ArrayList<>();
		for (Exit e : exits) {
			if (e.r() >= most - 0.005) {
				best.add(e.choice());
			}
		}
		// Holding finishes last: breakeven's stop is nearer and their target the same, and half's rest is held.
		return new Outcome(exits, best, hold.at());
	}

	/** Whether {@code choice} is among the choices that paid most, as a drill stores them ("HOLD,BREAKEVEN"). */
	static boolean paidMost(String best, String choice) {
		return choice != null && best != null && List.of(best.split(",")).contains(choice);
	}

	/** The first level of the bracket reached, its stop at {@code stop}; null when a minute reached both. */
	private static Exit exit(Choice choice, Trade t, long stop, List<Bar> future, Function<Bar, List<Bar>> minutesOf) {
		for (int k = 0; k < HORIZON_CANDLES; k++) {
			Bar bar = future.get(k);
			Level reached = reached(t, stop, bar);
			if (reached == Level.BOTH) {
				reached = null;
				for (Bar minute : minutesOf.apply(bar)) {
					reached = reached(t, stop, minute);
					if (reached == Level.BOTH) {
						return null;
					}
					if (reached != null) {
						bar = minute;
						break;
					}
				}
				if (reached == null) {
					return null;
				}
			}
			if (reached != null) {
				boolean gap = reached == Level.STOP_GAP || reached == Level.TARGET_GAP;
				long price = gap ? bar.openTicks() : reached == Level.STOP ? stop : t.target();
				String how = (reached == Level.TARGET || reached == Level.TARGET_GAP ? "TARGET" : "STOP") + (gap ? "_GAP" : "");
				return new Exit(choice, round2(r(t, price)), price, future.get(k).openTime(), k + 1, how);
			}
		}
		Bar last = future.get(HORIZON_CANDLES - 1);
		return new Exit(choice, round2(r(t, last.closeTicks())), last.closeTicks(), last.openTime(), HORIZON_CANDLES,
			"HORIZON");
	}

	/** The options as offered, in words. */
	static String label(Choice choice) {
		return switch (choice) {
			case HOLD -> "Hold — leave the stop and the target";
			case BREAKEVEN -> "Stop to breakeven — move the stop to the entry";
			case HALF -> "Half off — close half at the next open, hold the rest";
		};
	}

	/** The trade at the moment and each choice's story, in prices and R; the last line says what one trade proves. */
	static List<String> explain(Trade t, Outcome o, long halfPrice, Function<Long, String> price) {
		List<String> lines = new ArrayList<>();
		lines.add("When the chart stopped, the " + (t.longSide() ? "long" : "short") + " stood at " + signed(t.openR())
			+ ": entry " + price.apply(t.entry()) + ", stop " + price.apply(t.stop()) + ", target " + price.apply(t.target()) + ".");
		for (Exit e : o.exits()) {
			lines.add(switch (e.choice()) {
				case HOLD -> "Hold: " + story(e, price, "the stop") + ".";
				case BREAKEVEN -> "Stop to breakeven: " + story(e, price, "the entry") + ".";
				case HALF -> "Half off at the next open, " + price.apply(halfPrice) + " (" + signed(r(t, halfPrice))
					+ " on that half), the rest held: " + signed(e.r()) + " in all.";
			});
		}
		lines.add("One trade shows which choice paid here, not which pays: managing a trade is a policy, and only many of "
			+ "these show what each one does on real NQ. No slippage or commission.");
		return lines;
	}

	private static String story(Exit e, Function<Long, String> price, String stopName) {
		String later = e.candles() == 1 ? "the next candle" : e.candles() + " candles later";
		return switch (e.how()) {
			case "TARGET" -> "the target filled at " + price.apply(e.price()) + ", " + later + " — " + signed(e.r());
			case "TARGET_GAP" -> "the market opened beyond the target, at " + price.apply(e.price()) + ", " + later + " — "
				+ signed(e.r());
			case "STOP" -> "out at " + stopName + ", " + price.apply(e.price()) + ", " + later + " — " + signed(e.r());
			case "STOP_GAP" -> "the market opened beyond " + stopName + ", at " + price.apply(e.price()) + ", " + later + " — "
				+ signed(e.r());
			default -> "neither level within " + HORIZON_CANDLES + " candles; out at " + price.apply(e.price()) + " — "
				+ signed(e.r());
		};
	}

	/** "+1.42R", "−1.00R", "0.00R". */
	static String signed(double r) {
		return (r > 0 ? "+" : r < 0 ? "−" : "") + String.format(Locale.ROOT, "%.2fR", Math.abs(r));
	}

	private enum Level {
		STOP, TARGET, STOP_GAP, TARGET_GAP, BOTH
	}

	private static Level reached(Trade t, long stop, Bar bar) {
		boolean up = t.longSide();
		if (up ? bar.openTicks() <= stop : bar.openTicks() >= stop) {
			return Level.STOP_GAP;
		}
		if (up ? bar.openTicks() >= t.target() : bar.openTicks() <= t.target()) {
			return Level.TARGET_GAP;
		}
		boolean hitStop = up ? bar.lowTicks() <= stop : bar.highTicks() >= stop;
		boolean hitTarget = up ? bar.highTicks() >= t.target() : bar.lowTicks() <= t.target();
		return hitStop && hitTarget ? Level.BOTH : hitStop ? Level.STOP : hitTarget ? Level.TARGET : null;
	}

	private static double r(Trade t, long price) {
		return (double) (t.longSide() ? price - t.entry() : t.entry() - price) / t.risk();
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
