package com.tradeez.practice;

import java.math.BigDecimal;

import com.tradeez.analysis.RuleText;
import com.tradeez.marketdata.Bar;

/**
 * {@value #VERSION} (ICT-61, T22, DEC-066): a suggestion for the day's direction, with its reasoning —
 * never a verdict, and never graded. The reading ICT-61 records as commonly taught, from the last two
 * whole trading days (18:00 to 17:00 New York):
 * <ul>
 * <li>yesterday <b>closed beyond</b> the day before's high (low): look for continuation — bullish
 * (bearish);</li>
 * <li>yesterday <b>traded beyond</b> it but closed back inside: look the other way;</li>
 * <li>an inside day, or one that took both sides and closed inside, says nothing.</li>
 * </ul>
 * ICT_CONCEPTS.md asks for the source to be checked before it is taught as ICT's own method; the card
 * says it is the commonly taught reading. How often it matched is a fact for the evidence to state.
 */
public final class DailyBias {

	public static final String VERSION = "daily-bias-1.0.0";

	public enum Bias {
		BULLISH, BEARISH
	}

	/** @param bias null when the days suggest nothing */
	public record Suggestion(Bias bias, String reasoning) {
	}

	private DailyBias() {
	}

	/**
	 * @param dayBefore the trading day before yesterday
	 * @param yesterday the last whole trading day before the moment
	 */
	public static Suggestion suggest(Bar dayBefore, Bar yesterday, BigDecimal tickSize) {
		String high = RuleText.price(dayBefore.highTicks(), tickSize);
		String low = RuleText.price(dayBefore.lowTicks(), tickSize);
		String close = RuleText.price(yesterday.closeTicks(), tickSize);
		if (yesterday.closeTicks() > dayBefore.highTicks()) {
			return new Suggestion(Bias.BULLISH, "Yesterday closed at " + close + ", above the day before's high at " + high
				+ ": the reading commonly taught looks for higher prices next — buy-side liquidity.");
		}
		if (yesterday.closeTicks() < dayBefore.lowTicks()) {
			return new Suggestion(Bias.BEARISH, "Yesterday closed at " + close + ", below the day before's low at " + low
				+ ": the reading commonly taught looks for lower prices next — sell-side liquidity.");
		}
		boolean ranHigh = yesterday.highTicks() > dayBefore.highTicks();
		boolean ranLow = yesterday.lowTicks() < dayBefore.lowTicks();
		if (ranHigh && ranLow) {
			return new Suggestion(null, "Yesterday traded beyond both the day before's high and its low and closed inside, at "
				+ close + ": the reading suggests nothing.");
		}
		if (ranHigh) {
			return new Suggestion(Bias.BEARISH, "Yesterday traded above the day before's high at " + high
				+ " but closed back inside, at " + close + ": the reading commonly taught looks the other way — lower.");
		}
		if (ranLow) {
			return new Suggestion(Bias.BULLISH, "Yesterday traded below the day before's low at " + low
				+ " but closed back inside, at " + close + ": the reading commonly taught looks the other way — higher.");
		}
		return new Suggestion(null, "Yesterday stayed inside the day before's range, " + low + " to " + high
			+ ": an inside day says little.");
	}
}
