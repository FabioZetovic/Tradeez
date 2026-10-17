package com.tradeez.analysis;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Killzone;
import com.tradeez.marketdata.Timeframe;

/**
 * The New York clock as a chart's candles see it (T19, DEC-063): days from midnight, CME weeks, killzone
 * ranges and the previous trading day — for the rules read off a period once it has ended. A candle
 * belongs to the period its open falls in. A period ends with the candle that covers its last minute; on
 * 4-hour and daily charts that candle runs past it, and the period is known at its close: late, never early.
 */
final class Periods {

	static final ZoneId NEW_YORK = Killzone.NEW_YORK;
	/** The CME daily halt: the end of a New York day, and of the trading day. */
	static final LocalTime DAY_END = LocalTime.of(17, 0);
	/** The CME reopen: the start of the trading day, and of the week on a Sunday. */
	static final LocalTime REOPEN = LocalTime.of(18, 0);

	private Periods() {
	}

	/** Candles {@code first} to {@code last}, by index. */
	record Span(int first, int last) {
	}

	static LocalDateTime local(Bar bar) {
		return bar.openTime().atZone(NEW_YORK).toLocalDateTime();
	}

	/** The candle runs to {@code end} or past it: the last candle of a period ending then. */
	private static boolean covers(Bar bar, Instant end, Timeframe timeframe) {
		return bar.openTime().isBefore(end) && !bar.openTime().plus(timeframe.duration()).isBefore(end);
	}

	private static Instant at(LocalDate date, LocalTime time) {
		return date.atTime(time).atZone(NEW_YORK).toInstant();
	}

	/**
	 * Every whole New York day in {@code bars}, from a candle opening at midnight to the one covering the
	 * 17:00 halt on the same date. A day the data does not show from midnight, or that closes early, is not whole.
	 */
	static List<Span> midnightDays(List<Bar> bars, Timeframe timeframe) {
		List<Span> days = new ArrayList<>();
		for (int i = 0; i < bars.size(); i++) {
			LocalDateTime start = local(bars.get(i));
			if (!start.toLocalTime().equals(LocalTime.MIDNIGHT)) {
				continue;
			}
			Instant end = at(start.toLocalDate(), DAY_END);
			for (int k = i; k < bars.size() && bars.get(k).openTime().isBefore(end); k++) {
				if (covers(bars.get(k), end, timeframe)) {
					days.add(new Span(i, k));
					break;
				}
			}
		}
		return days;
	}

	/**
	 * Every whole CME week: from a candle opening at 18:00 on a Sunday to the one covering Friday's 17:00
	 * halt. A week without a Friday session — a holiday — is not whole.
	 */
	static List<Span> weeks(List<Bar> bars, Timeframe timeframe) {
		List<Span> weeks = new ArrayList<>();
		for (int i = 0; i < bars.size(); i++) {
			LocalDateTime start = local(bars.get(i));
			if (start.getDayOfWeek() != DayOfWeek.SUNDAY || !start.toLocalTime().equals(REOPEN)) {
				continue;
			}
			Instant end = at(start.toLocalDate().plusDays(5), DAY_END);
			for (int k = i; k < bars.size() && bars.get(k).openTime().isBefore(end); k++) {
				if (covers(bars.get(k), end, timeframe)) {
					weeks.add(new Span(i, k));
					break;
				}
			}
		}
		return weeks;
	}

	/** The candles of {@code span} opening at or after {@code from} and before {@code to}, New York time on {@code date}. */
	static Span within(List<Bar> bars, Span span, LocalDate date, LocalTime from, LocalTime to) {
		Instant start = at(date, from);
		Instant end = to.equals(LocalTime.MIDNIGHT) ? at(date.plusDays(1), LocalTime.MIDNIGHT) : at(date, to);
		int first = -1;
		int last = -1;
		for (int k = span.first(); k <= span.last(); k++) {
			Instant t = bars.get(k).openTime();
			if (!t.isBefore(start) && t.isBefore(end)) {
				first = first < 0 ? k : first;
				last = k;
			}
		}
		return first < 0 ? null : new Span(first, last);
	}

	/**
	 * The previous trading day of the candle at {@code index}: the candles from the 18:00 reopen before the
	 * one that opened this trading day, to the last before it. Null when the data does not reach back to it.
	 */
	static Span previousTradingDay(List<Bar> bars, int index, int reach) {
		int thisDay = -1;
		for (int k = index; k >= 0 && index - k <= reach; k--) {
			if (local(bars.get(k)).toLocalTime().equals(REOPEN)) {
				thisDay = k;
				break;
			}
		}
		if (thisDay <= 0) {
			return null;
		}
		for (int k = thisDay - 1; k >= 0 && index - k <= reach; k--) {
			if (local(bars.get(k)).toLocalTime().equals(REOPEN)) {
				return new Span(k, thisDay - 1);
			}
		}
		return null;
	}

	/** The first candle with the highest high, or the lowest low, in {@code span}. */
	static int extreme(List<Bar> bars, Span span, boolean high) {
		int best = span.first();
		for (int k = span.first() + 1; k <= span.last(); k++) {
			if (high ? bars.get(k).highTicks() > bars.get(best).highTicks() : bars.get(k).lowTicks() < bars.get(best).lowTicks()) {
				best = k;
			}
		}
		return best;
	}

	/** Candles of {@code timeframe} in {@code days} trading days and {@code hours} more: a rule's reach. */
	static int candles(Timeframe timeframe, int days, int hours) {
		long perDay = -Math.floorDiv(-Duration.ofHours(23).toSeconds(), timeframe.seconds());
		return (int) (days * perDay - Math.floorDiv(-Duration.ofHours(hours).toSeconds(), timeframe.seconds()));
	}
}
