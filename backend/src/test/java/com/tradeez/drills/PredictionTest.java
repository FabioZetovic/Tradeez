package com.tradeez.drills;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tradeez.drills.Prediction.Gap;
import com.tradeez.drills.Prediction.Level;
import com.tradeez.drills.Prediction.Outcome;
import com.tradeez.drills.Prediction.Question;
import com.tradeez.drills.Prediction.Target;
import com.tradeez.marketdata.Bar;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** {@code predict-1.0.0} (T15, DEC-058): which targets a moment offers, and how the future answers. */
class PredictionTest {

	private static final Instant T0 = Instant.parse("2025-03-12T14:00:00Z");
	private static final BigDecimal TICK = new BigDecimal("0.25");

	/** A five-minute candle, {@code n} candles after T0. */
	private static Bar bar(int n, long open, long high, long low, long close) {
		return new Bar(T0.plusSeconds(300L * n), open, high, low, close, 1);
	}

	private static Level resting(boolean high, long price, int swing) {
		return new Level(UUID.randomUUID(), high, price, T0.plusSeconds(300L * swing), "RESTING", T0.plusSeconds(300L * (swing + 1)));
	}

	private static Level taken(boolean high, long price, int swing, int at) {
		return new Level(UUID.randomUUID(), high, price, T0.plusSeconds(300L * swing), "TAKEN", T0.plusSeconds(300L * at));
	}

	private static Gap gap(boolean bullish, long low, long high, String state) {
		return new Gap(UUID.randomUUID(), bullish, low, high, T0, state);
	}

	/** The last candle on the chart: closes at 1000. */
	private static final Bar LAST = bar(39, 1002, 1004, 998, 1000);

	@Nested
	@DisplayName("which is taken first")
	class LiquidityFirst {

		@Test
		@DisplayName("asks about the nearest resting level on each side, with the obstacles on each path")
		void nearest() {
			List<Level> levels = List.of(
				resting(true, 1040, 10), resting(true, 1020, 20), taken(true, 1010, 5, 12), // a high already run
				resting(false, 980, 30), resting(false, 950, 8), taken(false, 1015, 15, 25)); // an old low, now above
			List<Gap> gaps = List.of(
				gap(false, 1005, 1008, "OPEN"), // bearish, on the way up: an obstacle
				gap(false, 990, 995, "FILLED"), // filled: gone
				gap(true, 985, 990, "TAPPED"), // bullish, on the way down: an obstacle
				gap(true, 1030, 1035, "OPEN")); // outside the range

			Question q = Prediction.liquidityFirst(LAST, levels, gaps);

			Target bsl = q.targets().get(0);
			Target ssl = q.targets().get(1);
			assertThat(bsl.value()).isEqualTo("BSL");
			assertThat(bsl.lowTicks()).isEqualTo(1020);
			assertThat(bsl.distance()).isEqualTo(20);
			assertThat(bsl.role()).isEqualTo(Prediction.Role.ERL);
			// Swing points at 1010 and 1015 lie between 1000 and 1020, and one bearish gap faces the move.
			assertThat(bsl.obstacles()).isEqualTo(new Prediction.Obstacles(2, 1));
			assertThat(ssl.value()).isEqualTo("SSL");
			assertThat(ssl.lowTicks()).isEqualTo(980);
			assertThat(ssl.obstacles()).isEqualTo(new Prediction.Obstacles(0, 1));
			// Inside the range, open: internal liquidity. The filled gap and the one above the range are not.
			assertThat(q.context()).extracting(Prediction.Context::what)
				.containsExactlyInAnyOrder("IRL_BEARISH_GAP", "IRL_BULLISH_GAP");
		}

		@Test
		@DisplayName("asks nothing when a side has no resting level")
		void needsBothSides() {
			assertThat(Prediction.liquidityFirst(LAST, List.of(resting(true, 1020, 20), taken(false, 980, 30, 35)),
				List.of())).isNull();
		}
	}

	@Nested
	@DisplayName("external or internal next")
	class ExternalOrInternal {

		@Test
		@DisplayName("after the last candle took a high: the untapped gap below, or the next high above")
		void afterAHigh() {
			List<Level> levels = List.of(taken(true, 1003, 30, 39), taken(true, 1001, 25, 39), resting(true, 1012, 10),
				resting(true, 1030, 5));
			List<Gap> gaps = List.of(gap(true, 990, 994, "OPEN"), gap(true, 980, 985, "OPEN"),
				gap(true, 995, 997, "TAPPED")); // already traded into: not untapped
			Question q = Prediction.externalOrInternal(LAST, levels, gaps);

			Target irl = q.targets().get(0);
			Target erl = q.targets().get(1);
			assertThat(irl.value()).isEqualTo("IRL");
			assertThat(irl.level()).isFalse();
			assertThat(irl.highTicks()).isEqualTo(994);
			assertThat(irl.distance()).isEqualTo(6);
			assertThat(erl.value()).isEqualTo("ERL");
			assertThat(erl.lowTicks()).isEqualTo(1012);
			// Drawn: the most extreme level the candle took.
			assertThat(q.context()).singleElement().satisfies(c -> {
				assertThat(c.what()).isEqualTo("TAKEN_BSL");
				assertThat(c.lowTicks()).isEqualTo(1003);
			});
		}

		@Test
		@DisplayName("is not asked when the last candle took nothing, or both sides, or a target is missing")
		void onlyAfterOneSide() {
			List<Gap> gaps = List.of(gap(true, 990, 994, "OPEN"), gap(false, 1006, 1009, "OPEN"));
			assertThat(Prediction.externalOrInternal(LAST, List.of(taken(true, 1003, 30, 38), resting(true, 1012, 10)), gaps))
				.as("taken a candle earlier").isNull();
			assertThat(Prediction.externalOrInternal(LAST,
				List.of(taken(true, 1003, 30, 39), taken(false, 999, 31, 39), resting(true, 1012, 10)), gaps))
				.as("an outside candle").isNull();
			assertThat(Prediction.externalOrInternal(LAST, List.of(taken(true, 1003, 30, 39)), gaps))
				.as("nothing above").isNull();
		}
	}

	@Nested
	@DisplayName("the future")
	class Resolve {

		private final List<Target> range = Prediction.liquidityFirst(LAST,
			List.of(resting(true, 1020, 20), resting(false, 980, 30)), List.of()).targets();

		@Test
		@DisplayName("is answered by the first candle trading a tick beyond either level")
		void firstTouch() {
			List<Bar> future = List.of(
				bar(40, 1000, 1020, 990, 1010), // at the buy-side, not beyond it
				bar(41, 1010, 1015, 979, 985)); // a tick below the sell-side
			Outcome o = Prediction.resolve(range, future, b -> List.of(b));
			assertThat(o.value()).isEqualTo("SSL");
			assertThat(o.at()).isEqualTo(future.get(1).openTime());
			assertThat(o.candles()).isEqualTo(2);
			assertThat(o.minutes()).isFalse();
		}

		@Test
		@DisplayName("when one candle took both, its minutes decide; when one minute did, nothing can")
		void bothInOneCandle() {
			Bar both = bar(40, 1000, 1021, 979, 1000);
			List<Bar> upFirst = List.of(
				new Bar(both.openTime(), 1000, 1021, 999, 1015, 1),
				new Bar(both.openTime().plusSeconds(60), 1015, 1016, 979, 990, 1));
			Outcome o = Prediction.resolve(range, List.of(both), b -> upFirst);
			assertThat(o.value()).isEqualTo("BSL");
			assertThat(o.minutes()).isTrue();
			assertThat(o.at()).isEqualTo(both.openTime());

			assertThat(Prediction.resolve(range, List.of(both), b -> List.of(b))).isNull();
			// No stored minutes for the candle — a gap in the import — cannot say either.
			assertThat(Prediction.resolve(range, List.of(both), b -> List.of())).isNull();
		}

		@Test
		@DisplayName("is not answered by candles past the horizon")
		void horizon() {
			List<Bar> quiet = new java.util.ArrayList<>();
			for (int n = 0; n < Prediction.HORIZON_CANDLES; n++) {
				quiet.add(bar(40 + n, 1000, 1010, 990, 1000));
			}
			quiet.add(bar(40 + Prediction.HORIZON_CANDLES, 1000, 1030, 1000, 1025));
			assertThat(Prediction.resolve(range, quiet, b -> List.of(b))).isNull();
		}

		@Test
		@DisplayName("a gap is reached when a candle trades into it")
		void gapReached() {
			Question q = Prediction.externalOrInternal(LAST,
				List.of(taken(true, 1003, 30, 39), resting(true, 1012, 10)), List.of(gap(true, 990, 994, "OPEN")));
			assertThat(Prediction.resolve(q.targets(), List.of(bar(40, 1000, 1005, 995, 998), bar(41, 998, 999, 994, 996)),
				b -> List.of(b)).value()).isEqualTo("IRL");
		}

		@Test
		@DisplayName("reveals up to a few candles past the deciding one")
		void revealed() {
			List<Bar> future = new java.util.ArrayList<>();
			for (int n = 0; n < 30; n++) {
				future.add(bar(40 + n, 1000, 1001, 999, 1000));
			}
			assertThat(Prediction.revealed(future, new Outcome("BSL", future.get(4).openTime(), 5, false)))
				.hasSize(5 + Prediction.REVEAL_AFTER);
		}
	}

	@Nested
	@DisplayName("what the moment saw")
	class AtTheMoment {

		private final Instant windowFrom = T0;
		private final Instant moment = T0.plusSeconds(300L * 40);

		/** A liquidity hit: the level of the swing at candle {@code swing}, in {@code kind} from candle {@code at}. */
		private Prediction.Hit level(UUID id, String side, int swing, String kind, int at) {
			Instant candle = T0.plusSeconds(300L * at);
			return new Prediction.Hit(id, kind, "SSL".equals(side), side, T0.plusSeconds(300L * swing),
				T0.plusSeconds(300L * swing), candle, candle.plusSeconds(300), 1000, 1000);
		}

		/** A gap-state hit: the gap whose first candle is {@code first}, in {@code kind} from candle {@code at}. */
		private Prediction.Hit gapState(UUID id, int first, String kind, int at) {
			Instant candle = T0.plusSeconds(300L * at);
			return new Prediction.Hit(id, kind, true, null, T0.plusSeconds(300L * first),
				T0.plusSeconds(300L * (first + 1)), candle, candle.plusSeconds(300), 990, 994);
		}

		@Test
		@DisplayName("a level's state is the latest its rule confirmed by the moment, and its swing is on the chart")
		void levels() {
			UUID onChart = UUID.randomUUID();
			List<Level> levels = Prediction.levelsAt(List.of(
				level(onChart, "BSL", 10, "RESTING", 11),
				level(UUID.randomUUID(), "BSL", 10, "TAKEN", 45), // taken after the moment: not known yet
				level(UUID.randomUUID(), "SSL", 0, "RESTING", 1)), // its swing is the chart's first candle
				windowFrom.plusSeconds(300), moment);
			assertThat(levels).singleElement().satisfies(l -> {
				assertThat(l.id()).isEqualTo(onChart);
				assertThat(l.state()).isEqualTo("RESTING");
			});
		}

		@Test
		@DisplayName("a gap's state is the furthest its rule reached by the moment, never after it")
		void gaps() {
			UUID first = UUID.randomUUID();
			UUID second = UUID.randomUUID();
			List<Gap> gaps = Prediction.gapsAt(List.of(
				gapState(first, 5, "OPEN", 7), gapState(UUID.randomUUID(), 5, "TAPPED", 41), // tapped after the moment
				gapState(second, 20, "OPEN", 22), gapState(UUID.randomUUID(), 20, "TAPPED", 30),
				gapState(UUID.randomUUID(), 20, "CE", 30), gapState(UUID.randomUUID(), 20, "FILLED", 30),
				gapState(UUID.randomUUID(), -2, "OPEN", 0)), // its first candle is before the chart
				windowFrom, moment);
			assertThat(gaps).extracting(Gap::id, Gap::state)
				.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(first, "OPEN"),
					org.assertj.core.groups.Tuple.tuple(second, "FILLED"));
		}
	}

	@Test
	@DisplayName("a question survives being stored and read back, as the drill keeps it")
	void storedAndRead() {
		JsonMapper json = JsonMapper.builder().build();
		Question q = Prediction.liquidityFirst(LAST, List.of(resting(true, 1020, 20), resting(false, 980, 30)),
			List.of(gap(true, 985, 990, "OPEN")));
		Map<String, Object> stored = json.convertValue(q, new TypeReference<Map<String, Object>>() {
		});
		assertThat(stored).containsOnlyKeys("closeTicks", "targets", "context");
		assertThat(json.convertValue(stored, Question.class)).isEqualTo(q);
	}

	@Test
	@DisplayName("says what happened in prices, with the obstacles, and never as a verdict on the reading")
	void explained() {
		Question q = Prediction.liquidityFirst(LAST, List.of(resting(true, 1020, 20), resting(false, 980, 30)), List.of());
		List<String> lines = PredictionText.explain(q, "SSL", 7, false, "5m", TICK);
		assertThat(lines.get(0)).isEqualTo("Price took the sell-side at 245.00 first — 7 candles after the chart ended.");
		assertThat(lines.get(1)).isEqualTo("The buy-side at 255.00, 20 ticks from the last close, had not been taken by then.");
		assertThat(lines.get(2)).isEqualTo("Obstacles on the way down: none; on the way up: none.");
		assertThat(lines.getLast()).contains("not a verdict");
		assertThat(PredictionText.explain(q, "BSL", 1, true, "5m", TICK).get(1))
			.isEqualTo("That 5m candle reached both; on its one-minute candles the buy-side at 255.00 came first.");
		assertThat(PredictionText.label(q.targets().getFirst(), TICK)).isEqualTo("Buy-side at 255.00");
	}
}
