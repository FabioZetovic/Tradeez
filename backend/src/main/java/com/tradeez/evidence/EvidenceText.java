package com.tradeez.evidence;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.tradeez.analysis.EvidenceCalculator.Count;
import com.tradeez.analysis.EvidenceCalculator.RuleEvidence;
import com.tradeez.marketdata.Timeframe;

/**
 * A rule's evidence in words (T21, DEC-065): what was counted, over which market and dates, and how often —
 * each share with what it is a share of. Always ending on what the numbers are not (INVARIANT 8).
 */
public final class EvidenceText {

	/** Said under every count: a base rate is a description of the past. */
	public static final String NOT_A_FORECAST = "A description of what this rule found on these dates — not a forecast, "
		+ "and not a reason to take a trade.";

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
		.withZone(ZoneOffset.UTC);

	/** What a rule's hits are called, in the plural. */
	private static final Map<String, String> NOUNS = Map.ofEntries(Map.entry("FVG", "fair value gaps"),
		Map.entry("FVG_STATE", "gaps"), Map.entry("IFVG", "inversion gaps"), Map.entry("BPR", "balanced price ranges"),
		Map.entry("VI", "volume imbalances"), Map.entry("VOID", "gaps and voids"), Map.entry("OPENING_GAP", "opening gaps"),
		Map.entry("ORG", "opening range gaps"), Map.entry("SWING", "swing highs and lows"),
		Map.entry("SWING_TIER", "ranked swings"), Map.entry("STRUCTURE", "structure labels"),
		Map.entry("BOS_CHOCH", "breaks of structure"), Map.entry("DISPLACEMENT", "displacements"),
		Map.entry("MSS", "market structure shifts"), Map.entry("LIQUIDITY", "liquidity levels"),
		Map.entry("EQHL", "pairs of equal highs or lows"), Map.entry("SWEEP", "takes of a liquidity level"),
		Map.entry("TURTLE_SOUP", "turtle soups"), Map.entry("OB", "order blocks"), Map.entry("BREAKER", "breakers"),
		Map.entry("MITIGATION", "mitigation blocks"), Map.entry("REJECTION", "rejection blocks"),
		Map.entry("UNICORN", "unicorns"), Map.entry("DEALING_RANGE", "dealing ranges"),
		Map.entry("PD", "premium and discount halves"), Map.entry("OTE", "optimal trade entry zones"),
		Map.entry("STDEV", "deviation levels"), Map.entry("PO3", "power-of-three days"),
		Map.entry("DAILY_PROFILE", "named days"), Map.entry("WEEKLY_PROFILE", "named weeks"),
		Map.entry("JUDAS", "Judas swings"));

	/** What a kind is called among its rule's hits. */
	private static final Map<String, String> KINDS = Map.ofEntries(Map.entry("BULLISH", "bullish"),
		Map.entry("BEARISH", "bearish"), Map.entry("NEUTRAL", "without a direction"), Map.entry("CHOCH", "CHoCH"),
		Map.entry("SWEEP", "sweeps (closed back)"), Map.entry("RUN", "runs (held beyond)"), Map.entry("HIGH", "highs"),
		Map.entry("LOW", "lows"), Map.entry("EQH", "equal highs"), Map.entry("EQL", "equal lows"),
		Map.entry("EH", "equal highs (EH)"), Map.entry("EL", "equal lows (EL)"), Map.entry("GAP", "gaps"),
		Map.entry("VOID", "voids"), Map.entry("ONE", "−1"), Map.entry("TWO", "−2"), Map.entry("TWO_AND_A_HALF", "−2.5"),
		Map.entry("FOUR", "−4"), Map.entry("LONDON_LOW", "London low"),
		Map.entry("LONDON_HIGH", "London high"), Map.entry("NY_REVERSAL", "New York reversal"),
		Map.entry("LONDON", "London"), Map.entry("NEW_YORK", "New York"));

	/** What a life's state means, said of the lives that reached it. */
	private static final Map<String, String> STATES = Map.ofEntries(Map.entry("TAPPED", "were traded into"),
		Map.entry("CE", "reached their middle (CE)"), Map.entry("FILLED", "were filled"),
		Map.entry("INVERTED", "inverted — a close through the far side"),
		Map.entry("MEAN_THRESHOLD", "lost their mean threshold"),
		Map.entry("INVALIDATED", "were invalidated — a close beyond them"),
		Map.entry("BROKEN_END", "were left past their end: the move went on"),
		Map.entry("BROKEN_START", "were left past their start: the move turned"),
		Map.entry("BROKEN_HIGH", "were left above their range's high"),
		Map.entry("BROKEN_LOW", "were left below their range's low"),
		Map.entry("TAKEN", "were taken, a tick or more beyond"));

	/** What "expired" means for each kind of life: still what it was after 500 candles. */
	private static final Map<String, String> EXPIRED = Map.of("FVG_STATE", "were still unfilled after 500 candles",
		"LIQUIDITY", "were still resting after 500 candles", "DEALING_RANGE", "were still whole after 500 candles",
		"PD", "were still whole after 500 candles", "OTE", "were still whole after 500 candles");

	private EvidenceText() {
	}

	/** Every line, in reading order, for a rule's evidence on one timeframe of one market. */
	public static List<String> lines(String detector, String symbol, Timeframe timeframe, RuleEvidence e) {
		List<String> lines = new ArrayList<>();
		String noun = NOUNS.getOrDefault(detector, "hits");
		lines.add(symbol + ", " + candleName(timeframe) + " candles, " + DATE.format(e.from()) + " to "
			+ DATE.format(e.to().minusSeconds(1)) + ": " + number(e.candles()) + " candles.");
		if (e.periods() != null) {
			lines.add(periods(detector, e));
		} else {
			lines.add(number(e.hits()) + " " + noun + " — about " + rate(e.hits(), e.candles()) + " per 100 candles.");
			if (e.kinds().size() > 1) {
				lines.add("Of them: " + e.kinds().stream()
					.map(k -> percent(k.count(), e.hits()) + " " + KINDS.getOrDefault(k.key(), k.key()))
					.collect(Collectors.joining(", ")) + ".");
			}
		}
		if (e.lives() != null && e.lives().ended() > 0) {
			long ended = e.lives().ended();
			lines.add("Of the " + number(ended) + " whose life ended within the data, " + e.lives().reached().stream()
				.map(r -> percent(r.count(), ended) + " " + ("EXPIRED".equals(r.key())
					? EXPIRED.getOrDefault(detector, "were still standing after 500 candles")
					: STATES.getOrDefault(r.key(), r.key().toLowerCase(Locale.ROOT))))
				.collect(Collectors.joining("; ")) + ".");
		}
		lines.add(NOT_A_FORECAST);
		return lines;
	}

	private static String periods(String detector, RuleEvidence e) {
		Map<String, Long> totals = e.periods().counts().stream().collect(Collectors.toMap(Count::key, Count::count));
		Map<String, Long> hits = e.kinds().stream().collect(Collectors.toMap(Count::key, Count::count));
		long all = totals.getOrDefault("ALL", 0L);
		return switch (detector) {
			case "JUDAS" -> "Of " + number(totals.getOrDefault("LONDON", 0L)) + " midnight-to-05:00 windows, "
				+ percent(hits.getOrDefault("LONDON", 0L), totals.getOrDefault("LONDON", 0L)) + " had a Judas swing; of "
				+ number(totals.getOrDefault("NEW_YORK", 0L)) + " 09:30-to-10:00 windows, "
				+ percent(hits.getOrDefault("NEW_YORK", 0L), totals.getOrDefault("NEW_YORK", 0L)) + ".";
			case "PO3" -> "Of " + number(all) + " midnight-to-17:00 days, " + percent(e.hits(), all)
				+ " were power of three: " + percent(hits.getOrDefault("BULLISH", 0L), all) + " bullish, "
				+ percent(hits.getOrDefault("BEARISH", 0L), all) + " bearish.";
			case "WEEKLY_PROFILE" -> "Of " + number(all) + " whole weeks on one contract: " + shares(e.kinds(), all, EvidenceText::title) + "; " + percent(all - e.hits(), all) + " came the other way round and are not named.";
			default -> "Of " + number(all) + " midnight-to-17:00 days: " + shares(e.kinds(), all, k -> KINDS.getOrDefault(k, k))
				+ "; " + percent(all - e.hits(), all) + " fit none of them.";
		};
	}

	private static String shares(List<Count> kinds, long of, Function<String, String> name) {
		return kinds.stream().map(k -> name.apply(k.key()) + " " + percent(k.count(), of)).collect(Collectors.joining(", "));
	}

	private static String candleName(Timeframe timeframe) {
		return switch (timeframe) {
			case M1 -> "1-minute";
			case M5 -> "5-minute";
			case M15 -> "15-minute";
			case H1 -> "1-hour";
			default -> timeframe.code();
		};
	}

	/** A share in whole percent; a few in ten thousand is "under 1%", not "0%". */
	static String percent(long part, long whole) {
		if (whole <= 0) {
			return "—";
		}
		double p = 100.0 * part / whole;
		return part > 0 && p < 0.5 ? "under 1%" : Math.round(p) + "%";
	}

	private static String rate(long hits, long candles) {
		return candles == 0 ? "0" : String.format(Locale.ENGLISH, "%.1f", 100.0 * hits / candles);
	}

	private static String number(long n) {
		return String.format(Locale.ENGLISH, "%,d", n);
	}

	private static String title(String kind) {
		return kind.charAt(0) + kind.substring(1).toLowerCase(Locale.ROOT);
	}
}
