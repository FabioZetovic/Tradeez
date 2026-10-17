package com.tradeez.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongUnaryOperator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * T19 (DEC-063) — the rules read off the New York clock, on candles built by hand in New York time:
 * power of three, daily and weekly profiles, the Judas swing. {@link DetectorContractTest} checks the contract.
 */
class TimeRulesTest {

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
	private static final Timeframe M30 = Timeframe.M30;

	private static Instant ny(String local) {
		return LocalDateTime.parse(local).atZone(NEW_YORK).toInstant();
	}

	private static Bar bar(Instant at, long open, long high, long low, long close) {
		return new Bar(at, open, high, low, close, 1);
	}

	/**
	 * Tuesday 6 May 2025 on 30-minute candles, midnight to 17:00 New York, as closes from an open of 100,
	 * each candle running from the last close to its own with no wicks; {@code low} and {@code high} put the
	 * day's extremes on the candles at those indexes.
	 */
	private static List<Bar> day(long[] closes, int lowAt, long low, int highAt, long high, LongUnaryOperator mirror) {
		List<Bar> bars = new ArrayList<>();
		long open = 100;
		for (int i = 0; i < closes.length; i++) {
			long close = closes[i];
			long hi = i == highAt ? high : Math.max(open, close);
			long lo = i == lowAt ? low : Math.min(open, close);
			long o = mirror.applyAsLong(open);
			long c = mirror.applyAsLong(close);
			long h = mirror.applyAsLong(hi);
			long l = mirror.applyAsLong(lo);
			bars.add(bar(ny("2025-05-06T00:00").plusSeconds(1800L * i), o, Math.max(h, l), Math.min(h, l), c));
			open = close;
		}
		return bars;
	}

	/** London falls to 90 at 03:00 and recovers; New York rallies to 120 at 14:00; the day closes at 118. */
	private static final long[] LONDON_LOW = {
		98, 96, 95, 94, 93, 92, 92, 93, 94, 95, 96, 97, 98, 98, 99, 99, 100,    // 00:00 – 08:00
		102, 104, 106, 108, 110, 111, 112, 113, 114, 115, 116, 117, 118,        // 08:30 – 14:00
		117, 117, 118, 118, 118 };                                              // 14:30 – 16:30

	/** The same New York, after a London that barely dips: no close below 98. */
	private static final long[] SHALLOW = {
		99, 98, 98, 98, 98, 98, 98, 98, 99, 99, 99, 99, 99, 100, 100, 100, 100,
		102, 104, 106, 108, 110, 111, 112, 113, 114, 115, 116, 117, 118,
		117, 117, 118, 118, 118 };

	private static List<Detection> detect(Detector detector, List<Bar> bars, Timeframe timeframe) {
		return detector.detect(bars, timeframe);
	}

	@Nested
	@DisplayName("po3-1.0.0")
	class PowerOfThree {

		@Test
		@DisplayName("up from the midnight open, the low first and a third of the range below the open: bullish")
		void bullish() {
			assertThat(detect(new Po3Detector(), day(LONDON_LOW, 6, 90, 28, 120, p -> p), M30)).singleElement().satisfies(p -> {
				assertThat(p.direction()).isEqualTo(Detection.Direction.BULLISH);
				assertThat(p.anchor()).isEqualTo(ny("2025-05-06T03:00"));     // the manipulation's low
				assertThat(p.lowTicks()).isEqualTo(90);
				assertThat(p.highTicks()).isEqualTo(120);
				assertThat(p.confirmedAt()).isEqualTo(ny("2025-05-06T17:00"));
				assertThat(p.evidence()).containsEntry("manipulationTicks", 10L).containsEntry("rangeTicks", 30L)
					.containsEntry("midHalfTicks", 200L);                        // its line at the midnight open
			});
		}

		@Test
		@DisplayName("a day up whose high came first, and whose low came after it, is not power of three")
		void order() {
			// The high at 01:00, the low at 14:00; still closing at 118, above the open.
			assertThat(detect(new Po3Detector(), day(LONDON_LOW, 28, 90, 2, 130, p -> p), M30)).isEmpty();
			assertThat(detect(new Po3Detector(), day(LONDON_LOW, 28, 90, 2, 130, p -> 200 - p), M30)).isEmpty();
		}

		@Test
		@DisplayName("the mirror is bearish; a manipulation under 15% of the range is not power of three")
		void bearishAndShallow() {
			assertThat(detect(new Po3Detector(), day(LONDON_LOW, 6, 90, 28, 120, p -> 200 - p), M30))
				.extracting(Detection::direction).containsExactly(Detection.Direction.BEARISH);
			// London's low at 97: 3 ticks below the open, 13% of a 23-tick range.
			assertThat(detect(new Po3Detector(), day(SHALLOW, 6, 97, 28, 120, p -> p), M30)).isEmpty();
		}

		@Test
		@DisplayName("not on a 4-hour chart, whose candles do not start at midnight")
		void timeframes() {
			assertThat(detect(new Po3Detector(), day(LONDON_LOW, 6, 90, 28, 120, p -> p), Timeframe.H4)).isEmpty();
		}
	}

	@Nested
	@DisplayName("daily-profile-1.0.0")
	class DailyProfile {

		@Test
		@DisplayName("London made the low, New York the high, and the day closed up: London low")
		void londonLow() {
			assertThat(detect(new DailyProfileDetector(), day(LONDON_LOW, 6, 90, 28, 120, p -> p), M30)).singleElement()
				.satisfies(d -> {
					assertThat(d.kind()).isEqualTo("LONDON_LOW");
					assertThat(d.direction()).isEqualTo(Detection.Direction.BULLISH);
					assertThat(d.anchor()).isEqualTo(ny("2025-05-06T03:00"));
					assertThat(d.evidence()).containsEntry("midHalfTicks", 200L);    // the midnight open
				});
		}

		@Test
		@DisplayName("the mirror: London high; New York making both: a New York reversal; London making both: unnamed")
		void others() {
			assertThat(detect(new DailyProfileDetector(), day(LONDON_LOW, 6, 90, 28, 120, p -> 200 - p), M30))
				.extracting(Detection::kind, Detection::direction)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("LONDON_HIGH", Detection.Direction.BEARISH));
			// The low at 10:00 and the high at 14:00, both in New York.
			assertThat(detect(new DailyProfileDetector(), day(LONDON_LOW, 20, 90, 28, 120, p -> p), M30))
				.extracting(Detection::kind, Detection::direction)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("NY_REVERSAL", Detection.Direction.BULLISH));
			// The low at 03:00 and the high at 07:00, both in London.
			assertThat(detect(new DailyProfileDetector(), day(LONDON_LOW, 6, 90, 14, 125, p -> p), M30)).isEmpty();
		}

		@Test
		@DisplayName("London's extreme with a close the other way is not named; the 08:30 candle is New York's")
		void closeAndBoundary() {
			// The high in London at 01:00, the low in New York at 14:00, but the day closed up — and the mirror.
			assertThat(detect(new DailyProfileDetector(), day(LONDON_LOW, 28, 90, 2, 130, p -> p), M30)).isEmpty();
			assertThat(detect(new DailyProfileDetector(), day(LONDON_LOW, 28, 90, 2, 130, p -> 200 - p), M30)).isEmpty();
			// The low on the 08:30 candle: both extremes in New York.
			assertThat(detect(new DailyProfileDetector(), day(LONDON_LOW, 17, 90, 28, 120, p -> p), M30))
				.extracting(Detection::kind).containsExactly("NY_REVERSAL");
		}
	}

	@Nested
	@DisplayName("weekly-profile-1.0.0")
	class WeeklyProfile {

		/** Five daily candles of the week of 4 May 2025, each opening at 18:00 New York the evening before. */
		private List<Bar> week(long[][] ohlc) {
			List<Bar> bars = new ArrayList<>();
			for (int i = 0; i < ohlc.length; i++) {
				long[] c = ohlc[i];
				bars.add(bar(ny("2025-05-04T18:00").plusSeconds(86_400L * i), c[0], c[1], c[2], c[3]));
			}
			return bars;
		}

		@Test
		@DisplayName("a week up with its low on Tuesday, before its high: a Tuesday low of the week")
		void tuesday() {
			List<Bar> bars = week(new long[][] { { 100, 110, 95, 105 }, { 105, 108, 90, 100 }, { 100, 115, 98, 112 },
				{ 112, 120, 110, 118 }, { 118, 125, 115, 124 } });
			assertThat(detect(new WeeklyProfileDetector(), bars, Timeframe.D1)).singleElement().satisfies(w -> {
				assertThat(w.kind()).isEqualTo("TUESDAY");
				assertThat(w.direction()).isEqualTo(Detection.Direction.BULLISH);
				assertThat(w.anchor()).isEqualTo(ny("2025-05-05T18:00"));      // Tuesday's session
				assertThat(w.lowTicks()).isEqualTo(90);
				assertThat(w.highTicks()).isEqualTo(125);
				// The Friday candle runs to 18:00; the week is known at its close — an hour late, never early.
				assertThat(w.confirmedAt()).isEqualTo(ny("2025-05-09T18:00"));
			});
		}

		@Test
		@DisplayName("not named when the extreme against the close came after the other")
		void outOfOrder() {
			List<Bar> bars = week(new long[][] { { 100, 130, 99, 105 }, { 105, 108, 100, 104 }, { 104, 110, 98, 100 },
				{ 100, 106, 90, 104 }, { 104, 126, 103, 124 } });
			assertThat(detect(new WeeklyProfileDetector(), bars, Timeframe.D1)).isEmpty();
		}
	}

	@Nested
	@DisplayName("judas-1.0.0")
	class Judas {

		/**
		 * Monday 5 May's trading day at 100, with its high at 105 and its low at 95; the evening at 100; Asia
		 * from 98 to 102; then Tuesday's midnight window, as {@code window}, and quiet candles to 05:00.
		 */
		private List<Bar> bars(long[][] window) {
			List<Bar> bars = new ArrayList<>();
			for (Instant t = ny("2025-05-04T18:00"); t.isBefore(ny("2025-05-05T17:00")); t = t.plusSeconds(1800)) {
				long high = t.equals(ny("2025-05-05T10:00")) ? 105 : 101;
				long low = t.equals(ny("2025-05-05T11:00")) ? 95 : 99;
				bars.add(bar(t, 100, high, low, 100));
			}
			for (Instant t = ny("2025-05-05T18:00"); t.isBefore(ny("2025-05-06T00:00")); t = t.plusSeconds(1800)) {
				boolean asia = !t.isBefore(ny("2025-05-05T20:00"));
				bars.add(bar(t, 100, asia ? 102 : 101, asia ? 98 : 99, 100));
			}
			Instant t = ny("2025-05-06T00:00");
			for (long[] c : window) {
				bars.add(bar(t, c[0], c[1], c[2], c[3]));
				t = t.plusSeconds(1800);
			}
			for (; t.isBefore(ny("2025-05-06T05:00")); t = t.plusSeconds(1800)) {
				bars.add(bar(t, 100, 100, 100, 100));
			}
			return bars;
		}

		@Test
		@DisplayName("London: down through the Asia low, then a close back above the midnight open — a bullish Judas swing")
		void bullish() {
			List<Bar> bars = bars(new long[][] { { 100, 100, 97, 97 }, { 97, 98, 94, 95 }, { 95, 101, 95, 101 } });
			assertThat(detect(new JudasDetector(), bars, M30)).singleElement().satisfies(j -> {
				assertThat(j.kind()).isEqualTo("LONDON");
				assertThat(j.direction()).isEqualTo(Detection.Direction.BULLISH);
				assertThat(j.anchor()).isEqualTo(ny("2025-05-06T00:00"));     // the candle that took the Asia low
				assertThat(j.end()).isEqualTo(ny("2025-05-06T01:00"));        // the close back above 100
				assertThat(j.confirmedAt()).isEqualTo(ny("2025-05-06T01:30"));
				assertThat(j.lowTicks()).isEqualTo(94);
				assertThat(j.highTicks()).isEqualTo(100);
				assertThat(j.evidence()).containsEntry("level", "ASIA_LOW").containsEntry("levelPrice", 98L);
			});
		}

		@Test
		@DisplayName("none when price never closes back through the open within the window — at it, or after 05:00, is not")
		void noReturn() {
			List<Bar> bars = bars(new long[][] { { 100, 100, 97, 97 }, { 97, 98, 94, 95 }, { 95, 100, 95, 100 } });
			List<Bar> late = new ArrayList<>(bars);
			late.add(bar(ny("2025-05-06T05:00"), 100, 101, 100, 101));
			assertThat(detect(new JudasDetector(), late, M30)).isEmpty();
		}

		@Test
		@DisplayName("a level is taken a tick beyond it: a touch of the Asia low is not a Judas swing")
		void touch() {
			assertThat(detect(new JudasDetector(), bars(new long[][] { { 100, 100, 98, 99 }, { 99, 101, 99, 101 } }), M30))
				.isEmpty();
		}

		/**
		 * As {@link #bars}, then Tuesday's London killzone with its low at 97, a 06:00 candle down to 96 —
		 * through London's low and Asia's, after their periods ended — and New York's window from 09:30 as
		 * {@code window}.
		 */
		private List<Bar> newYork(long[][] window) {
			List<Bar> bars = new ArrayList<>(bars(new long[0][]));
			bars.removeIf(b -> !b.openTime().isBefore(ny("2025-05-06T00:00")));
			for (Instant t = ny("2025-05-06T00:00"); t.isBefore(ny("2025-05-06T09:30")); t = t.plusSeconds(1800)) {
				long low = t.equals(ny("2025-05-06T03:00")) ? 97 : t.equals(ny("2025-05-06T06:00")) ? 96 : 99;
				bars.add(bar(t, 100, 101, low, 100));
			}
			Instant t = ny("2025-05-06T09:30");
			for (long[] c : window) {
				bars.add(bar(t, c[0], c[1], c[2], c[3]));
				t = t.plusSeconds(1800);
			}
			return bars;
		}

		@Test
		@DisplayName("New York: levels already traded through by 09:30 do not count; the previous day's low still rests")
		void newYorkWindow() {
			// Down to 96, under London's 97 and Asia's 98 — both taken at 06:00 — and back above 100: nothing.
			assertThat(detect(new JudasDetector(), newYork(new long[][] { { 100, 101, 96, 101 } }), M30)).isEmpty();
			// Down to 94, through Monday's low of 95: a bullish Judas swing in New York.
			assertThat(detect(new JudasDetector(), newYork(new long[][] { { 100, 101, 94, 101 } }), M30)).singleElement()
				.satisfies(j -> {
					assertThat(j.kind()).isEqualTo("NEW_YORK");
					assertThat(j.direction()).isEqualTo(Detection.Direction.BULLISH);
					assertThat(j.evidence()).containsEntry("level", "PREVIOUS_DAY_LOW").containsEntry("levelPrice", 95L);
				});
		}
	}
}
