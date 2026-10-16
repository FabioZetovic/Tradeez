package com.tradeez.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Aggregation must never contradict the data it came from.
 *
 * <p>This matters more than it looks. The fill engine walks one-second bars (DEC-008) while the
 * user watches a five-minute chart. If the candle and the seconds inside it could disagree, the
 * engine would resolve trades that contradict what the user saw — and once a trader catches the app
 * doing that, every result it has ever produced becomes suspect.
 */
class BarAggregatorTest {

	private static Bar bar(long epochSecond, long open, long high, long low, long close, long vol) {
		return new Bar(Instant.ofEpochSecond(epochSecond), open, high, low, close, vol);
	}

	@Test
	@DisplayName("open comes from the first bar, close from the last")
	void openAndCloseComeFromTheEdges() {
		List<Bar> seconds = List.of(
			bar(0, 100, 105, 99, 104, 10),
			bar(1, 104, 110, 103, 108, 20),
			bar(2, 108, 109, 101, 102, 30));

		Bar minute = BarAggregator.aggregate(seconds, Timeframe.M1).getFirst();

		assertThat(minute.openTicks()).isEqualTo(100);
		assertThat(minute.closeTicks()).isEqualTo(102);
	}

	@Test
	@DisplayName("high and low are the extremes of the whole bucket, not of the edges")
	void highAndLowSpanTheBucket() {
		List<Bar> seconds = List.of(
			bar(0, 100, 105, 99, 104, 10),
			bar(1, 104, 120, 90, 108, 20),   // the extremes live in the middle bar
			bar(2, 108, 109, 101, 102, 30));

		Bar minute = BarAggregator.aggregate(seconds, Timeframe.M1).getFirst();

		assertThat(minute.highTicks()).isEqualTo(120);
		assertThat(minute.lowTicks()).isEqualTo(90);
	}

	@Test
	@DisplayName("volume sums")
	void volumeSums() {
		List<Bar> seconds = List.of(
			bar(0, 100, 101, 99, 100, 10),
			bar(1, 100, 101, 99, 100, 20),
			bar(2, 100, 101, 99, 100, 30));

		assertThat(BarAggregator.aggregate(seconds, Timeframe.M1).getFirst().volume())
			.isEqualTo(60);
	}

	@Test
	@DisplayName("buckets align to the epoch, not to the first bar")
	void bucketsAlignToTheClock() {
		// Starting at 14:00:30 must still produce a candle stamped 14:00:00. Aligning to the first
		// bar instead would make a 14:00 request and a 14:00:30 request disagree about where every
		// later candle begins — and two clients would then draw different charts of the same market.
		List<Bar> seconds = List.of(
			bar(30, 100, 101, 99, 100, 1),
			bar(31, 100, 101, 99, 100, 1));

		assertThat(BarAggregator.aggregate(seconds, Timeframe.M1).getFirst().openTime())
			.isEqualTo(Instant.ofEpochSecond(0));
	}

	@Test
	@DisplayName("a new bucket starts a new bar")
	void bucketBoundariesSplit() {
		List<Bar> seconds = List.of(
			bar(59, 100, 101, 99, 100, 1),    // minute 0
			bar(60, 200, 201, 199, 200, 1));  // minute 1

		List<Bar> minutes = BarAggregator.aggregate(seconds, Timeframe.M1);

		assertThat(minutes).hasSize(2);
		assertThat(minutes.get(0).closeTicks()).isEqualTo(100);
		assertThat(minutes.get(1).openTicks()).isEqualTo(200);
	}

	@Test
	@DisplayName("gaps produce no bar rather than an invented flat candle")
	void gapsAreNotFilled() {
		// A closed market is real information. Manufacturing a flat candle to fill the hole would
		// be inventing price action that never happened, and a detector would then find structure
		// in it.
		List<Bar> seconds = List.of(
			bar(0, 100, 101, 99, 100, 1),
			bar(600, 200, 201, 199, 200, 1));  // ten minutes later

		List<Bar> minutes = BarAggregator.aggregate(seconds, Timeframe.M1);

		assertThat(minutes).hasSize(2);
		assertThat(minutes.get(1).openTime()).isEqualTo(Instant.ofEpochSecond(600));
	}

	@Test
	@DisplayName("an empty input aggregates to nothing")
	void emptyInput() {
		assertThat(BarAggregator.aggregate(List.of(), Timeframe.M5)).isEmpty();
	}

	@Test
	@DisplayName("aggregating generated seconds preserves the true extremes")
	void aggregationAgreesWithTheSourceSeries() {
		// The property that actually matters, stated against real generated data rather than a
		// hand-built fixture: a candle must never hide a wick that the seconds inside it contain.
		DemoSeriesGenerator generator = new DemoSeriesGenerator();
		Instant from = Instant.parse("2024-03-13T14:00:00Z");
		List<Bar> seconds = generator.generateSeconds(77L, from, from.plusSeconds(1800));

		List<Bar> fiveMinutes = BarAggregator.aggregate(seconds, Timeframe.M5);

		long secondsHigh = seconds.stream().mapToLong(Bar::highTicks).max().orElseThrow();
		long secondsLow = seconds.stream().mapToLong(Bar::lowTicks).min().orElseThrow();
		long candlesHigh = fiveMinutes.stream().mapToLong(Bar::highTicks).max().orElseThrow();
		long candlesLow = fiveMinutes.stream().mapToLong(Bar::lowTicks).min().orElseThrow();

		assertThat(candlesHigh).isEqualTo(secondsHigh);
		assertThat(candlesLow).isEqualTo(secondsLow);
		assertThat(fiveMinutes.stream().mapToLong(Bar::volume).sum())
			.isEqualTo(seconds.stream().mapToLong(Bar::volume).sum());
	}

	/** One bar for every open minute from {@code from} to {@code to}, each a tick above the last. */
	private static List<Bar> minutes(Instant from, Instant to) {
		List<Bar> out = new ArrayList<>();
		long price = 80_000;
		for (Instant t = from; t.isBefore(to); t = t.plusSeconds(60)) {
			if (SessionCalendar.isOpen(t)) {
				out.add(new Bar(t, price, price + 2, price - 1, price + 1, 1));
				price++;
			}
		}
		return out;
	}

	private static Instant ny(String localDateTime) {
		return LocalDateTime.parse(localDateTime).atZone(ZoneId.of("America/New_York")).toInstant();
	}

	@Test
	@DisplayName("T35: six 4-hour candles a trading day from 18:00 New York, the one across UTC midnight whole")
	void fourHourCandles() {
		List<Bar> day = minutes(ny("2025-06-09T18:00"), ny("2025-06-10T17:00"));
		List<Bar> candles = BarAggregator.aggregate(day, Timeframe.H4);
		assertThat(candles).extracting(Bar::openTime).containsExactly(ny("2025-06-09T18:00"), ny("2025-06-09T22:00"),
			ny("2025-06-10T02:00"), ny("2025-06-10T06:00"), ny("2025-06-10T10:00"), ny("2025-06-10T14:00"));
		// 18:00 to 22:00 New York is 22:00 to 02:00 UTC: one candle of 240 minutes, not two halves.
		assertThat(candles.getFirst().volume()).isEqualTo(240);
		// The last is cut short by the 17:00 close: three hours.
		assertThat(candles.getLast().volume()).isEqualTo(180);
		assertThat(candles.stream().mapToLong(Bar::volume).sum()).isEqualTo(day.size());
	}

	@Test
	@DisplayName("T35: a daily candle per trading day, Sunday evening's minutes in Monday's")
	void dailyCandles() {
		List<Bar> week = minutes(ny("2025-06-08T18:00"), ny("2025-06-13T17:00"));
		List<Bar> days = BarAggregator.aggregate(week, Timeframe.D1);
		assertThat(days).extracting(Bar::openTime).containsExactly(ny("2025-06-08T18:00"), ny("2025-06-09T18:00"),
			ny("2025-06-10T18:00"), ny("2025-06-11T18:00"), ny("2025-06-12T18:00"));
		// 23 hours of trading each: 18:00 to 17:00.
		assertThat(days).allSatisfy(d -> assertThat(d.volume()).isEqualTo(23 * 60));
		// Open of the first minute, close of the last, the extremes of all.
		Bar monday = days.getFirst();
		assertThat(monday.openTicks()).isEqualTo(week.getFirst().openTicks());
		assertThat(monday.closeTicks()).isEqualTo(week.get(23 * 60 - 1).closeTicks());
	}
}
