package com.tradeez.drills;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.function.LongFunction;

import com.tradeez.analysis.RuleText;
import com.tradeez.drills.Prediction.Context;
import com.tradeez.marketdata.Bar;

/**
 * Mode J (T45, DEC-082): a question about the chart as it stands, answered by a rule the moment it is asked — premium
 * or discount, inside the OTE zone or not, which power of three, where the stop goes, whether a model's setup is
 * there. Lines are drawn for it (the range, the leg, the trade); the learner picks one of two or three answers; the
 * answer and the reasons, in prices, come with the result. Nothing after the chart is shown or needed.
 *
 * <p>Each question is a pure function of the candles and the rule's stored hits, so it is tested without a market.
 */
public final class Judgement {

	private Judgement() {
	}

	/** One of the answers offered. */
	public record Option(String value, String label) {
	}

	/**
	 * What a judgement asks and how it was decided, stored with the drill.
	 *
	 * @param closeTicks  the last close, which most questions are about
	 * @param context     the lines drawn for it
	 * @param answer      the option's value the rule gives
	 * @param explanation why, in prices: shown only with the result
	 */
	public record Asked(String version, String definition, long closeTicks, List<Option> options, List<Context> context,
			String answer, List<String> explanation) {

		List<String> values() {
			return options.stream().map(Option::value).toList();
		}
	}

	/** A stored hit as much as a judgement reads of it. */
	record Hit(String kind, String direction, Instant start, Instant anchor, Instant confirmedAt, long low, long high,
			Map<String, Object> evidence) {
	}

	// --- premium or discount (ICT-41) ----------------------------------------------------------------

	static final String PD_VERSION = "judge-pd-1.0.0";

	static String pdDefinition() {
		return PD_VERSION + ". A dealing range of dealing-range-1.0.0, still standing when the chart ends, its high and low "
			+ "drawn. Its equilibrium is halfway between them: a close above it is in premium, below it in discount. A "
			+ "close exactly at equilibrium, or outside the range, is never asked.";
	}

	/** States that end a range: after one, it is no longer a range to be in. */
	private static final Set<String> RANGE_ENDS = Set.of("BROKEN_HIGH", "BROKEN_LOW", "EXPIRED");

	/**
	 * Premium or discount: the latest dealing range formed on the chart — its leg wholly on screen — and standing
	 * when the chart ends (never broken or expired by then), and where the last close sits in it. The PD rule stores
	 * each range as its two halves; together they are the range. Null when there is none, or the close is at
	 * equilibrium or outside the range.
	 *
	 * @param halves every state of the PD rule's halves known by the chart's end
	 */
	static Asked premiumDiscount(List<Bar> bars, List<Hit> halves, Instant firstCandle, BigDecimal tickSize) {
		Instant end = bars.getLast().openTime().plusSeconds(1);
		Optional<Hit> formed = halves.stream()
			.filter(h -> "FORMED".equals(h.kind()) && !h.start().isBefore(firstCandle) && h.confirmedAt().isBefore(end))
			.filter(h -> halves.stream().noneMatch(s -> s.anchor().equals(h.anchor()) && RANGE_ENDS.contains(s.kind())
				&& s.confirmedAt().isBefore(end)))
			.max(Comparator.comparing(Hit::confirmedAt));
		if (formed.isEmpty()) {
			return null;
		}
		List<Hit> range = halves.stream()
			.filter(h -> "FORMED".equals(h.kind()) && h.anchor().equals(formed.get().anchor())).toList();
		long low = range.stream().mapToLong(Hit::low).min().orElseThrow();
		long high = range.stream().mapToLong(Hit::high).max().orElseThrow();
		long close = bars.getLast().closeTicks();
		long twiceEq = low + high;
		if (close < low || close > high || 2 * close == twiceEq) {
			return null;
		}
		boolean premium = 2 * close > twiceEq;
		LongFunction<String> price = prices(tickSize);
		long from = formed.get().start().getEpochSecond();
		List<Context> context = List.of(new Context("RANGE_HIGH", true, from, high, high),
			new Context("RANGE_LOW", true, from, low, low));
		List<String> why = List.of(
			"The range runs from " + price.apply(low) + " to " + price.apply(high) + "; halfway, its equilibrium, is "
				+ RuleText.halfPrice(twiceEq, tickSize) + ".",
			"The last close, " + price.apply(close) + ", is " + (premium ? "above" : "below") + " it: "
				+ (premium ? "premium — where ICT looks to sell, not to buy." : "discount — where ICT looks to buy, not to sell."));
		return new Asked(PD_VERSION, pdDefinition(), close,
			List.of(new Option("PREMIUM", "Premium"), new Option("DISCOUNT", "Discount")), context,
			premium ? "PREMIUM" : "DISCOUNT", why);
	}

	/** A drawn line's words on the chart: "Range high 21330.00". */
	static String label(Context c, BigDecimal tickSize) {
		String name = switch (c.what()) {
			case "RANGE_HIGH" -> "Range high";
			case "RANGE_LOW" -> "Range low";
			default -> c.what().charAt(0) + c.what().substring(1).toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
		};
		return name + " " + RuleText.price(c.lowTicks(), tickSize);
	}

	/** Prices in the instrument's own form. */
	static LongFunction<String> prices(BigDecimal tickSize) {
		return ticks -> RuleText.price(ticks, tickSize);
	}
}
