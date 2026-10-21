package com.tradeez.practice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.tradeez.analysis.RuleText;
import com.tradeez.practice.FillEngine.Direction;
import com.tradeez.practice.FillEngine.FillModel;
import com.tradeez.practice.PracticeDtos.PartialView;
import com.tradeez.practice.PracticeDtos.PlanView;
import com.tradeez.practice.PracticeDtos.ResultView;
import com.tradeez.practice.PracticeDtos.ScoreLineView;
import com.tradeez.practice.PracticeDtos.ScorecardView;

/**
 * {@value #VERSION} (T23, DEC-067): a finished trade on two sides of equal weight — what it did, and how it
 * was made — each line its own statement with its reasons, and nothing added up into one score (DEC-027).
 *
 * <p>A verdict is given only where the rule is the learner's own: the risk limit they set before the session,
 * the bias they stated with the trade. What depends on a method — the reward to risk, how far price went
 * against them, how much of the move they kept, what the daily-bias reading suggested — is stated, never
 * judged (DEC-027's correction).
 */
public final class Scorecard {

	public static final String VERSION = "scorecard-1.0.0";

	public static final String KEPT = "KEPT";
	public static final String BROKEN = "BROKEN";

	private static final Map<String, String> KILLZONES = Map.of("ASIA", "Asia", "LONDON", "London", "NEW_YORK_AM",
		"New York AM", "NEW_YORK_PM", "New York PM");

	private Scorecard() {
	}

	public static ScorecardView score(PlanView plan, ResultView result, BigDecimal tickSize, long tickValueCents) {
		long risk = Math.abs(plan.referenceTicks() - plan.stopTicks());
		return new ScorecardView(VERSION, outcome(plan, result, risk, tickSize, tickValueCents),
			process(plan, result, risk, tickValueCents));
	}

	private static List<ScoreLineView> outcome(PlanView plan, ResultView result, long risk, BigDecimal tickSize,
			long tickValueCents) {
		List<ScoreLineView> lines = new ArrayList<>();
		lines.add(fact("adverse", "Worst point", result.maeTicks() == 0 ? "It never went against you."
			: ratio(result.maeTicks(), risk) + " against you (" + ticks(result.maeTicks()) + ")."));
		lines.add(fact("favourable", "Best point", result.mfeTicks() == 0 ? "It never went your way."
			: ratio(result.mfeTicks(), risk) + " in your favour (" + ticks(result.mfeTicks()) + ")."));
		lines.add(fact("costs", "Costs", costs(plan, result, risk, tickValueCents)));
		if (plan.partialTicks() != null) {
			lines.add(fact("partial", "Partial", partial(plan, result, risk, tickSize)));
		}
		if (plan.dolTicks() != null) {
			// How far the draw lay from the fill, on the trade's side; behind the entry it is reached by going against.
			long toward = sign(plan.direction()) * (plan.dolTicks() - result.entryTicks());
			long excursion = toward >= 0 ? result.mfeTicks() : result.maeTicks();
			long gap = Math.abs(toward) - excursion;
			String at = RuleText.price(plan.dolTicks(), tickSize);
			lines.add(fact("draw", "Your draw", gap <= 0 ? "Price reached your draw at " + at + " while the trade was open."
				: "Price did not reach your draw at " + at + " while the trade was open: it came within " + ticks(gap) + "."));
		}
		return lines;
	}

	private static List<ScoreLineView> process(PlanView plan, ResultView result, long risk, long tickValueCents) {
		List<ScoreLineView> lines = new ArrayList<>();
		long riskCents = risk * tickValueCents * plan.contracts();
		Long limit = plan.riskLimitCents();
		if (limit == null) {
			lines.add(fact("risk", "Risk limit", "Risked " + money(riskCents)
				+ ". No limit was set: set one before a session to have it checked."));
		} else if (riskCents <= limit) {
			lines.add(new ScoreLineView("risk", "Risk limit", "Risked " + money(riskCents) + ", within your " + money(limit)
				+ " limit.", KEPT));
		} else {
			lines.add(new ScoreLineView("risk", "Risk limit", "Risked " + money(riskCents) + ", " + money(riskCents - limit)
				+ " over your " + money(limit) + " limit.", BROKEN));
		}

		List<String> stated = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		(plan.model() != null ? stated : missing).add("a model");
		(plan.bias() != null ? stated : missing).add("a bias");
		(plan.dolTicks() != null ? stated : missing).add("a draw on liquidity");
		(plan.confidence() != null ? stated : missing).add("a confidence");
		lines.add(fact("plan", "Plan", stated.size() + " of 4 stated" + (stated.isEmpty() ? "" : ": " + join(stated))
			+ (missing.isEmpty() ? "." : ". Not stated: " + join(missing) + ".")));

		if (plan.checklist() != null) {
			lines.add(fact("model", "Model", plan.checklist().title() + ": " + plan.checklist().met() + " of "
				+ plan.checklist().total() + " checks met when placed."));
		}
		String side = plan.direction() == Direction.LONG ? "long" : "short";
		if (plan.bias() != null) {
			boolean with = bullish(plan.bias()) == (plan.direction() == Direction.LONG);
			lines.add(new ScoreLineView("bias", "Your bias", capitalised(side) + ", " + (with ? "with" : "against")
				+ " your " + plan.bias().toLowerCase(Locale.ROOT) + " bias.", with ? KEPT : BROKEN));
		}
		if (plan.suggestedBias() != null) {
			boolean with = bullish(plan.suggestedBias()) == (plan.direction() == Direction.LONG);
			lines.add(fact("reading", "Daily-bias reading", "It suggested " + plan.suggestedBias().toLowerCase(Locale.ROOT)
				+ "; the trade was " + side + ", " + (with ? "with" : "against") + " it. A suggestion, never graded."));
		}
		lines.add(fact("killzone", "Time", plan.killzone() == null ? "Placed outside the killzones."
			: "Placed in the " + KILLZONES.get(plan.killzone()) + " killzone."));
		lines.add(fact("reward", "Reward : risk", String.format(Locale.ROOT, "Planned %.2f : 1", plan.plannedRewardRisk())
			+ (plan.partialTicks() == null ? "." : ", the partial's contracts to their level and the rest to the target.")));
		lines.add(fact("execution", "Execution", switch (result.outcome()) {
			case "CLOSED" -> "Closed by hand at " + r(result.rMultiple()) + ", before the stop or the target.";
			case "TIME_EXIT" -> "Held to the bracket: neither level was reached, and it was closed at the session's close.";
			case "AMBIGUOUS" -> "Held to the bracket: both levels were touched inside one candle.";
			case "TARGET" -> "Held to the bracket: the target closed it.";
			default -> result.partial() != null && Boolean.TRUE.equals(plan.breakevenAfterPartial())
				? "Held to the plan: the stop, moved to the entry after the partial, closed the rest."
				: "Held to the bracket: the stop closed it.";
		}));
		if (plan.confidence() != null) {
			String said = plan.confidence() + "% sure it would reach the target before the stop";
			lines.add(fact("confidence", "Confidence", said + switch (result.outcome()) {
				case "TARGET" -> ": it did.";
				case "STOP" -> ": it did not.";
				case "AMBIGUOUS" -> ". Both were touched inside one candle, so which came first is unknown: not counted in "
					+ "your calibration.";
				case "TIME_EXIT" -> ". Neither was reached before the session closed: not counted in your calibration.";
				default -> ". You closed it before either: not counted in your calibration.";
			}));
		}
		return lines;
	}

	/**
	 * What fees and slippage took (T27): the commission charged, and the ticks the fills slipped by the model the
	 * result was resolved with — the entry at market, and the exit by how it was made.
	 */
	private static String costs(PlanView plan, ResultView result, long risk, long tickValueCents) {
		FillModel model;
		try {
			model = FillModel.parse(result.fillModelVersion());
		} catch (IllegalArgumentException e) {
			// A name this version cannot read: the fees are stored, the slippage is not known.
			return "Fees " + money(result.commissionCents()) + "; the slippage cannot be read from its fill model ("
				+ result.fillModelVersion() + ").";
		}
		long exit = switch (result.outcome()) {
			case "STOP", "AMBIGUOUS" -> model.stopSlippageTicks();
			case "TARGET" -> 0;
			default -> model.marketSlippageTicks(); // a close at market, or the flat at the session's close
		};
		// The entry slipped for every contract, the exit only for those held to it: a partial is a limit (T28).
		int held = plan.contracts() - (result.partial() == null ? 0 : result.partial().contracts());
		long slipped = model.marketSlippageTicks() * plan.contracts() + exit * held;
		long slipCents = slipped * tickValueCents;
		long total = result.commissionCents() + slipCents;
		if (total == 0) {
			return "No fees or slippage.";
		}
		String slippage = result.partial() == null ? ticks(slipped / plan.contracts()) + " of slippage"
			: "slippage of " + ticks(slipped) + " across the contracts";
		return "Fees " + money(result.commissionCents()) + (slipped == 0 ? "" : " and " + slippage + " ("
			+ money(slipCents) + ")") + ": " + money(total) + ", " + String.format(Locale.ROOT, "%.2fR",
				(double) total / (risk * tickValueCents * plan.contracts())) + " of what was risked.";
	}

	/** What the partial did (T28): taken, and what each of its contracts made in R, or not reached. */
	private static String partial(PlanView plan, ResultView result, long risk, BigDecimal tickSize) {
		PartialView taken = result.partial();
		String at = RuleText.price(plan.partialTicks(), tickSize);
		if (taken == null) {
			return "Your partial at " + at + " was not reached before the exit.";
		}
		return taken.contracts() + " of " + plan.contracts() + " contracts taken off at " + RuleText.price(taken.ticks(), tickSize)
			+ (taken.gap() ? ", the market opening beyond " + at : "") + ": " + r((double) taken.pnlTicks() / risk)
			+ " on each" + (Boolean.TRUE.equals(plan.breakevenAfterPartial()) && result.exitTime() > taken.time()
				? "; the stop on the rest then moved to the entry." : ".");
	}

	private static ScoreLineView fact(String id, String label, String text) {
		return new ScoreLineView(id, label, text, null);
	}

	private static int sign(Direction direction) {
		return direction == Direction.LONG ? 1 : -1;
	}

	private static boolean bullish(String bias) {
		return "BULLISH".equals(bias);
	}

	private static String ratio(long ticks, long risk) {
		return String.format(Locale.ROOT, "%.2fR", (double) ticks / risk);
	}

	private static String ticks(long ticks) {
		return ticks + (ticks == 1 ? " tick" : " ticks");
	}

	/** "+1.85R" / "−1.00R", as the trade log prints it. */
	static String r(double r) {
		return (r > 0 ? "+" : r < 0 ? "−" : "") + String.format(Locale.ROOT, "%.2fR", Math.abs(r));
	}

	/** "$1,234.50", as the plan panel prints it. */
	static String money(long cents) {
		return (cents < 0 ? "−" : "") + String.format(Locale.ROOT, "$%,d.%02d", Math.abs(cents) / 100, Math.abs(cents) % 100);
	}

	static String capitalised(String word) {
		return Character.toUpperCase(word.charAt(0)) + word.substring(1);
	}

	/** "a, b and c". */
	private static String join(List<String> items) {
		if (items.size() == 1) {
			return items.getFirst();
		}
		return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.getLast();
	}
}
