package com.tradeez.drills;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.tradeez.analysis.RuleText;
import com.tradeez.drills.Prediction.Context;
import com.tradeez.drills.Prediction.Obstacles;
import com.tradeez.drills.Prediction.Question;
import com.tradeez.drills.Prediction.Target;

/**
 * A prediction drill in words (DEC-058): the rule, each target's name, and what price did — generated
 * from the same constants and numbers the drill was decided by, in prices and never times (the client
 * adds those, in the exchange's zone).
 */
final class PredictionText {

	private PredictionText() {
	}

	static String definition(DrillKind kind) {
		String decided = "The answer is whichever real price reached first within the next "
			+ Prediction.HORIZON_CANDLES + " candles. When one candle reached both, its one-minute candles decide; "
			+ "a question nothing stored can decide is never asked.";
		return switch (kind) {
			case LIQUIDITY_FIRST_PREDICTION -> Prediction.VERSION + ". The targets: the nearest buy-side liquidity "
				+ "resting above the last close and the nearest sell-side resting below it (liquidity-1.0.0) — swing "
				+ "levels on this chart that no candle has traded beyond since. Between them is the range price is in: "
				+ "they are its external liquidity (ERL), and the open fair value gaps inside it its internal liquidity "
				+ "(IRL). A level is taken when a candle trades a tick beyond it. " + decided + " Obstacles on a path "
				+ "(ICT-17): the swing points between price and the level, and the open gaps (fvg-state-1.0.0) facing "
				+ "the move.";
			case ERL_IRL_PREDICTION -> Prediction.VERSION + ". The last candle took resting liquidity "
				+ "(liquidity-1.0.0) on one side: external range liquidity. The targets: the nearest fair value gap "
				+ "behind it that no candle has traded into (fvg-state-1.0.0, still open) — internal liquidity (IRL) "
				+ "— and the next resting level beyond, on the same side — external (ERL). The gap is reached when a "
				+ "candle trades into it, the level taken a tick beyond. " + decided;
			case DAILY_BIAS_PREDICTION -> Prediction.VERSION + ". At the 09:30 New York open, with the open between "
				+ "yesterday's high and low: which does price take first? Yesterday is the trading day before, 18:00 to "
				+ "17:00 New York. A level is taken when a candle trades a tick beyond it. " + decided;
			default -> throw new IllegalArgumentException(kind + " is not a prediction");
		};
	}

	/** "Buy-side at 20140.25", "Internal: the bullish gap at 20100.00–20104.50". */
	static String label(Target target, BigDecimal tickSize) {
		String what = target.level()
			? (target.above() ? "Buy-side" : "Sell-side") + " at " + RuleText.price(target.lowTicks(), tickSize)
			: "the " + (target.above() ? "bearish" : "bullish") + " gap at " + RuleText.price(target.lowTicks(), tickSize)
				+ "–" + RuleText.price(target.highTicks(), tickSize);
		return switch (target.value()) {
			case "PDH" -> "Yesterday's high at " + RuleText.price(target.lowTicks(), tickSize);
			case "PDL" -> "Yesterday's low at " + RuleText.price(target.lowTicks(), tickSize);
			case "IRL" -> "Internal: " + what;
			case "ERL" -> "External: " + lower(what);
			default -> what;
		};
	}

	static String label(Context context, BigDecimal tickSize) {
		String low = RuleText.price(context.lowTicks(), tickSize);
		return switch (context.what()) {
			case "IRL_BULLISH_GAP" -> "IRL · bullish gap";
			case "IRL_BEARISH_GAP" -> "IRL · bearish gap";
			case "TAKEN_BSL" -> "Buy-side taken at " + low;
			case "TAKEN_SSL" -> "Sell-side taken at " + low;
			default -> throw new IllegalArgumentException("Unknown context " + context.what());
		};
	}

	/**
	 * What happened, once answered.
	 *
	 * @param candles  how many candles after the chart the deciding one was
	 * @param minutes  the deciding candle reached both, and its minutes decided
	 */
	static List<String> explain(Question question, String outcome, int candles, boolean minutes, String timeframe,
			BigDecimal tickSize) {
		Target first = question.targets().stream().filter(t -> t.value().equals(outcome)).findFirst().orElseThrow();
		Target other = question.targets().stream().filter(t -> !t.value().equals(outcome)).findFirst().orElseThrow();
		List<String> lines = new ArrayList<>();
		lines.add("Price " + (first.level() ? "took " : "reached ") + phrase(first, tickSize) + " first — "
			+ (candles == 1 ? "the next candle" : candles + " candles after the chart ended") + ".");
		if (minutes) {
			lines.add("That " + timeframe + " candle reached both; on its one-minute candles " + phrase(first, tickSize)
				+ " came first.");
		} else {
			lines.add(capitalised(phrase(other, tickSize)) + ", " + other.distance() + " ticks from the last close, had "
				+ "not been " + (other.level() ? "taken" : "reached") + " by then.");
		}
		if (first.obstacles() != null && other.obstacles() != null) {
			lines.add("Obstacles on the way" + toward(first) + ": " + obstacles(first.obstacles()) + "; on the way"
				+ toward(other) + ": " + obstacles(other.obstacles()) + ".");
		}
		lines.add("One outcome of one chart: what price did here, not a verdict on how the chart was read.");
		return lines;
	}

	/** "the buy-side at 20140.25", "the bullish gap at 20100.00–20104.50": the target inside a sentence. */
	private static String phrase(Target target, BigDecimal tickSize) {
		String label = label(target, tickSize);
		int colon = label.indexOf(": ");
		String bare = lower(colon < 0 ? label : label.substring(colon + 2));
		return bare.startsWith("the ") || bare.startsWith("yesterday") ? bare : "the " + bare;
	}

	private static String toward(Target target) {
		return target.above() ? " up" : " down";
	}

	private static String obstacles(Obstacles o) {
		return o.total() == 0 ? "none"
			: o.total() + " (" + count(o.swings(), "swing point", "swing points") + ", "
				+ count(o.gaps(), "open gap facing the move", "open gaps facing the move") + ")";
	}

	private static String count(int n, String one, String many) {
		return n + " " + (n == 1 ? one : many);
	}

	private static String lower(String s) {
		return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
	}

	private static String capitalised(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
