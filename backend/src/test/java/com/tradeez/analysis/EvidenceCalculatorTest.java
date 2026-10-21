package com.tradeez.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.analysis.EvidenceCalculator.Count;
import com.tradeez.analysis.EvidenceCalculator.RuleEvidence;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * evidence-1.0.0 (T21, DEC-065), each way of counting with a stand-in rule whose hits are known: lives and
 * when one is over, a leg's break read by its direction, kinds, and the periods the time rules are a share of.
 */
class EvidenceCalculatorTest {

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
	private static final Timeframe M30 = Timeframe.M30;

	/** {@code n} half-hour candles from a New York time, without a break. */
	private static List<Bar> candles(String fromNewYork, int n) {
		Instant t = LocalDateTime.parse(fromNewYork).atZone(NEW_YORK).toInstant();
		List<Bar> bars = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			bars.add(new Bar(t.plus(M30.duration().multipliedBy(i)), 100, 101, 99, 100, 1));
		}
		return bars;
	}

	/** A rule that finds exactly what {@code hits} says, in whatever segment it is handed. */
	private static Detector rule(String id, Function<List<Bar>, List<Detection>> hits) {
		return new Detector() {
			public String id() {
				return id;
			}

			public String ruleVersion() {
				return id.toLowerCase() + "-test";
			}

			public String methodologySource() {
				return "test";
			}

			public String conceptId() {
				return "ICT-00";
			}

			public Map<String, Object> parameters() {
				return Map.of();
			}

			public int lookbackBars(Timeframe timeframe) {
				return 1;
			}

			public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
				return hits.apply(segment);
			}
		};
	}

	/** One state of the zone formed at candle {@code formed}, reached at candle {@code at}. */
	private static Detection state(List<Bar> bars, int formed, int at, String kind, Detection.Direction direction) {
		Instant start = bars.get(formed).openTime();
		Instant end = bars.get(at).openTime();
		return new Detection(Detection.Shape.ZONE, direction, kind, start, start, end, end.plus(M30.duration()), 100, 110,
			Map.of());
	}

	private static Detection state(List<Bar> bars, int formed, int at, String kind) {
		return state(bars, formed, at, kind, Detection.Direction.BULLISH);
	}

	@Test
	@DisplayName("lives: counted once over — a final state, or 500 candles within the data; one still running, or begun before the data, is left out")
	void lives() {
		List<Bar> bars = candles("2025-05-05T00:00", 1200);
		Detector gaps = rule("FVG_STATE", s -> List.of(
			// Over: inverted.
			state(s, 10, 10, "OPEN"), state(s, 10, 12, "TAPPED"), state(s, 10, 20, "FILLED"), state(s, 10, 30, "INVERTED"),
			// Over: 1,099 candles followed it, past the 500 it is followed for.
			state(s, 100, 100, "OPEN"), state(s, 100, 110, "TAPPED"),
			// Running: only 199 candles followed it when the data ends.
			state(s, 1000, 1000, "OPEN"), state(s, 1000, 1001, "TAPPED"),
			// Begun before the data: no OPEN to count from.
			state(s, 0, 50, "TAPPED")));

		RuleEvidence e = EvidenceCalculator.measure(gaps, M30, List.of(bars));
		assertThat(e.hits()).isEqualTo(3);
		assertThat(e.candles()).isEqualTo(1200);
		assertThat(e.kinds()).isEmpty();
		assertThat(e.lives().ended()).isEqualTo(2);
		assertThat(e.lives().reached()).containsExactly(new Count("TAPPED", 2), new Count("CE", 0), new Count("FILLED", 1),
			new Count("INVERTED", 1), new Count("EXPIRED", 0));
		assertThat(e.periods()).isNull();
	}

	@Test
	@DisplayName("a life is over at exactly 500 candles, or at a final state however near the end of the data")
	void edges() {
		List<Bar> bars = candles("2025-05-05T00:00", 1200);
		Detector gaps = rule("FVG_STATE", s -> List.of(
			// Formed with exactly 500 candles after it: followed to the end of its life.
			state(s, 699, 699, "OPEN"), state(s, 699, 700, "TAPPED"),
			// 501 short of it: still running.
			state(s, 700, 700, "OPEN"),
			// Near the end, but inverted: over.
			state(s, 1150, 1150, "OPEN"), state(s, 1150, 1160, "INVERTED")));
		RuleEvidence e = EvidenceCalculator.measure(gaps, M30, List.of(bars));
		assertThat(e.hits()).isEqualTo(3);
		assertThat(e.lives().ended()).isEqualTo(2);
		assertThat(e.lives().reached()).contains(new Count("TAPPED", 1), new Count("INVERTED", 1));
	}

	@Test
	@DisplayName("a leg's range is left past its end or its start by its direction; premium and discount halves by high and low")
	void legs() {
		List<Bar> bars = candles("2025-05-05T00:00", 100);
		Detector ranges = rule("DEALING_RANGE", s -> List.of(
			state(s, 1, 1, "FORMED"), state(s, 1, 5, "BROKEN_HIGH"),                                     // up, went on
			state(s, 2, 2, "FORMED", Detection.Direction.BEARISH), state(s, 2, 6, "BROKEN_HIGH", Detection.Direction.BEARISH),
			state(s, 3, 3, "FORMED", Detection.Direction.BEARISH), state(s, 3, 7, "BROKEN_LOW", Detection.Direction.BEARISH)));
		assertThat(EvidenceCalculator.measure(ranges, M30, List.of(bars)).lives().reached())
			.containsExactly(new Count("BROKEN_END", 2), new Count("BROKEN_START", 1), new Count("EXPIRED", 0));

		Detector halves = rule("PD", s -> List.of(state(s, 1, 1, "FORMED"), state(s, 1, 5, "BROKEN_HIGH"),
			state(s, 2, 2, "FORMED", Detection.Direction.BEARISH), state(s, 2, 6, "BROKEN_HIGH", Detection.Direction.BEARISH)));
		assertThat(EvidenceCalculator.measure(halves, M30, List.of(bars)).lives().reached())
			.containsExactly(new Count("BROKEN_HIGH", 2), new Count("BROKEN_LOW", 0), new Count("EXPIRED", 0));
	}

	@Test
	@DisplayName("kinds, largest first, across segments; by direction for a rule without kinds; an empty segment adds nothing")
	void kinds() {
		List<Bar> first = candles("2025-05-05T00:00", 40);
		List<Bar> second = candles("2025-05-12T00:00", 60);
		Detector breaks = rule("BOS_CHOCH", s -> s.size() == 40
			? List.of(state(s, 1, 1, "BOS"), state(s, 2, 2, "CHOCH"))
			: List.of(state(s, 1, 1, "BOS"), state(s, 3, 3, "BOS")));
		RuleEvidence e = EvidenceCalculator.measure(breaks, M30, List.of(first, List.of(), second));
		assertThat(e.hits()).isEqualTo(4);
		assertThat(e.candles()).isEqualTo(100);
		assertThat(e.kinds()).containsExactly(new Count("BOS", 3), new Count("CHOCH", 1));
		assertThat(e.from()).isEqualTo(first.getFirst().openTime());
		assertThat(e.to()).isEqualTo(second.getLast().openTime().plus(M30.duration()));
		assertThat(e.lives()).isNull();

		Detector gaps = rule("FVG", s -> List.of(state(s, 1, 1, null), state(s, 2, 2, null),
			state(s, 3, 3, null, Detection.Direction.BEARISH)));
		assertThat(EvidenceCalculator.measure(gaps, M30, List.of(first)).kinds())
			.containsExactly(new Count("BULLISH", 2), new Count("BEARISH", 1));
		assertThat(EvidenceCalculator.measure(gaps, M30, List.of()).candles()).isZero();
	}

	@Test
	@DisplayName("time rules are a share of the days, weeks or windows in the data, each counted from the candles")
	void periods() {
		// Sunday 18:00 to the Friday candle covering 17:00: one CME week, and its five midnight days, Monday to Friday.
		List<Bar> week = candles("2025-05-04T18:00", (int) (Duration.ofDays(5).minusHours(1).toMinutes() / 30));
		Detector daily = rule("DAILY_PROFILE", s -> List.of(state(s, 20, 20, "LONDON_LOW"), state(s, 70, 70, "LONDON_LOW")));
		RuleEvidence days = EvidenceCalculator.measure(daily, M30, List.of(week));
		assertThat(days.periods().unit()).isEqualTo("day");
		assertThat(days.periods().counts()).containsExactly(new Count("ALL", 5));
		assertThat(days.kinds()).containsExactly(new Count("LONDON_LOW", 2));

		RuleEvidence weeks = EvidenceCalculator.measure(rule("WEEKLY_PROFILE", s -> List.of()), M30, List.of(week));
		assertThat(weeks.periods()).isEqualTo(new EvidenceCalculator.PeriodTotals("week", List.of(new Count("ALL", 1))));

		RuleEvidence windows = EvidenceCalculator.measure(rule("JUDAS", s -> List.of()), M30, List.of(week));
		assertThat(windows.periods().unit()).isEqualTo("window");
		assertThat(windows.periods().counts()).containsExactly(new Count("LONDON", 5), new Count("NEW_YORK", 5));
		// From Monday 01:00: Monday's midnight window is not in the data, its 09:30 one is.
		List<Bar> fromMonday = week.subList(week.size() - (int) (Duration.ofDays(4).plusHours(16).toMinutes() / 30), week.size());
		assertThat(EvidenceCalculator.measure(rule("JUDAS", s -> List.of()), M30, List.of(fromMonday)).periods().counts())
			.containsExactly(new Count("LONDON", 4), new Count("NEW_YORK", 5));
	}
}
