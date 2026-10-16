package com.tradeez.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * BUG-064 — a detector's lookback before a scan is the candles it needs, not a calendar guess. A
 * market shut at weekends has two days in seven without a daily candle; one fixed margin left a
 * daily chart dozens short, and then what a scan found depended on where it started (DEC-049).
 */
class ScanHistoryTest {

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
	/** Where the fake data begins: Monday 7 October 2024's trading day. */
	private static final LocalDate FIRST = LocalDate.of(2024, 10, 7);
	private static final Instant FROM = open(LocalDate.of(2026, 9, 14));

	@Test
	@DisplayName("a daily chart gets its whole lookback, weekends and all, ending just before the scan")
	void daily() {
		List<Bar> market = market(Timeframe.D1);
		List<Bar> history = ScanHistoryTest.history(200, market, Timeframe.D1);

		assertThat(history).hasSize(200).isEqualTo(before(market, FROM).subList(before(market, FROM).size() - 200,
			before(market, FROM).size()));
		// Why one margin was not enough: 200 trading days span far more than 200 days and ten.
		assertThat(history.getFirst().openTime()).isBefore(FROM.minus(Duration.ofDays(210)));
	}

	@Test
	@DisplayName("a 4-hour chart too: six candles a trading day, five days a week")
	void fourHours() {
		List<Bar> market = market(Timeframe.H4);
		List<Bar> history = ScanHistoryTest.history(503, market, Timeframe.H4);

		List<Bar> all = before(market, FROM);
		assertThat(history).hasSize(503).isEqualTo(all.subList(all.size() - 503, all.size()));
	}

	@Test
	@DisplayName("stops where the data begins, with whatever there is")
	void dataStart() {
		List<Bar> market = market(Timeframe.D1);
		Instant early = open(FIRST.plusDays(30));
		AtomicInteger reads = new AtomicInteger();
		List<Bar> history = SetupScanner.history(200, (a, b) -> {
			reads.incrementAndGet();
			return between(market, a, b);
		}, Timeframe.D1.duration(), early);

		assertThat(history).isEqualTo(before(market, early)).isNotEmpty();
		assertThat(reads).as("one read past the first candle finds nothing, and the search ends").hasValueLessThan(5);
	}

	@Test
	@DisplayName("what a short lookback gets is the end of what a long one gets — so one read serves several rules")
	void nested() {
		List<Bar> market = market(Timeframe.D1);
		List<Bar> longer = ScanHistoryTest.history(503, market, Timeframe.D1);
		for (int needed : new int[] {3, 31, 200, 300}) {
			assertThat(ScanHistoryTest.history(needed, market, Timeframe.D1))
				.isEqualTo(longer.subList(longer.size() - needed, longer.size()));
		}
	}

	private static List<Bar> history(int needed, List<Bar> market, Timeframe timeframe) {
		BiFunction<Instant, Instant, List<Bar>> read = (a, b) -> between(market, a, b);
		return SetupScanner.history(needed, read, timeframe.duration(), FROM);
	}

	/** Candles of {@code timeframe} for every weekday's trading day from FIRST to past FROM. */
	private static List<Bar> market(Timeframe timeframe) {
		List<Bar> bars = new ArrayList<>();
		for (LocalDate day = FIRST; day.isBefore(LocalDate.of(2026, 10, 1)); day = day.plusDays(1)) {
			if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
				continue;
			}
			Instant open = open(day);
			// 23 hours, 18:00 to the 17:00 halt.
			for (long at = 0; at < 23 * 3600; at += timeframe.seconds()) {
				bars.add(new Bar(open.plusSeconds(at), 1, 2, 0, 1, 1));
			}
		}
		return bars;
	}

	/** A trading day's open: 18:00 New York the evening before. */
	private static Instant open(LocalDate tradingDay) {
		return tradingDay.minusDays(1).atTime(LocalTime.of(18, 0)).atZone(NEW_YORK).toInstant();
	}

	private static List<Bar> between(List<Bar> market, Instant from, Instant to) {
		return market.stream().filter(b -> !b.openTime().isBefore(from) && b.openTime().isBefore(to)).toList();
	}

	private static List<Bar> before(List<Bar> market, Instant to) {
		return market.stream().filter(b -> b.openTime().isBefore(to)).toList();
	}
}
