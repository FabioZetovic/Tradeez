package com.tradeez.drills;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tradeez.drills.Judgement.Asked;
import com.tradeez.drills.Judgement.Hit;
import com.tradeez.marketdata.Bar;

/** Mode J (T45): each question decided by its rule from the candles and the stored hits, nothing else. */
class JudgementTest {

	private static final Instant T0 = Instant.parse("2026-03-10T14:00:00Z");
	private static final long STEP = 300;
	private static final BigDecimal TICK = new BigDecimal("0.25");

	private static Instant at(int i) {
		return T0.plusSeconds(STEP * i);
	}

	/** Forty candles, the last closing at {@code close}. */
	private static List<Bar> closingAt(long close) {
		List<Bar> bars = new ArrayList<>();
		for (int i = 0; i < 39; i++) {
			bars.add(new Bar(at(i), 1000, 1010, 990, 1000, 1));
		}
		bars.add(new Bar(at(39), 1000, Math.max(1000, close), Math.min(1000, close), close, 1));
		return bars;
	}

	/** A PD range from 900 to 1100 formed at candle 20, stored as its two halves, the way pd-1.0.0 stores it. */
	private static List<Hit> range(String kind, int confirmed) {
		return List.of(
			new Hit(kind, "BULLISH", at(5), at(10), at(confirmed), 900, 1000, Map.of()),
			new Hit(kind, "BEARISH", at(5), at(10), at(confirmed), 1000, 1100, Map.of()));
	}

	@Nested
	@DisplayName("premium or discount (ICT-41)")
	class PremiumDiscount {

		@Test
		@DisplayName("above equilibrium is premium, below it discount; the range is both halves together")
		void halves() {
			Asked premium = Judgement.premiumDiscount(closingAt(1040), range("FORMED", 20), at(1), TICK);
			assertThat(premium.answer()).isEqualTo("PREMIUM");
			assertThat(premium.values()).containsExactly("PREMIUM", "DISCOUNT");
			assertThat(premium.context()).extracting(Prediction.Context::what, Prediction.Context::lowTicks)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("RANGE_HIGH", 1100L), org.assertj.core.groups.Tuple.tuple("RANGE_LOW", 900L));
			assertThat(premium.explanation()).containsExactly(
				"The range runs from 225.00 to 275.00; halfway, its equilibrium, is 250.00.",
				"The last close, 260.00, is above it: premium — where ICT looks to sell, not to buy.");
			assertThat(Judgement.premiumDiscount(closingAt(960), range("FORMED", 20), at(1), TICK).answer()).isEqualTo("DISCOUNT");
		}

		@Test
		@DisplayName("never asks at equilibrium, outside the range, about a broken range, or one whose leg is off the chart")
		void refuses() {
			assertThat(Judgement.premiumDiscount(closingAt(1000), range("FORMED", 20), at(1), TICK)).as("at equilibrium").isNull();
			assertThat(Judgement.premiumDiscount(closingAt(1150), range("FORMED", 20), at(1), TICK)).as("outside").isNull();
			List<Hit> broken = new ArrayList<>(range("FORMED", 20));
			broken.addAll(range("BROKEN_HIGH", 30));
			assertThat(Judgement.premiumDiscount(closingAt(1040), broken, at(1), TICK)).as("broken").isNull();
			List<Hit> tapped = new ArrayList<>(range("FORMED", 20));
			tapped.addAll(range("TAPPED", 30));
			assertThat(Judgement.premiumDiscount(closingAt(1040), tapped, at(1), TICK)).as("tapped is still standing").isNotNull();
			assertThat(Judgement.premiumDiscount(closingAt(1040), range("FORMED", 20), at(6), TICK)).as("leg cut").isNull();
			assertThat(Judgement.premiumDiscount(closingAt(1040), range("FORMED", 39), at(1), TICK))
				.as("formed by the last candle's open is known; at its close it is not shown before the answer").isNotNull();
		}

		@Test
		@DisplayName("picks the latest range standing")
		void latest() {
			List<Hit> two = new ArrayList<>(range("FORMED", 20));
			two.add(new Hit("FORMED", "BULLISH", at(25), at(28), at(30), 1030, 1050, Map.of()));
			two.add(new Hit("FORMED", "BEARISH", at(25), at(28), at(30), 1050, 1070, Map.of()));
			Asked asked = Judgement.premiumDiscount(closingAt(1040), two, at(1), TICK);
			assertThat(asked.answer()).as("1040 is in discount of 1030–1070").isEqualTo("DISCOUNT");
		}
	}

	/** An OTE of a leg up from 900 (candle 5) to 1100 (candle 15): 62–79% back is 942–976, its line at 959. */
	private static List<Hit> ote(String kind, int confirmed) {
		Map<String, Object> evidence = Map.of("startTime", at(5).getEpochSecond(), "startPrice", 900L,
			"endTime", at(15).getEpochSecond(), "endPrice", 1100L, "midHalfTicks", 1918L);
		return List.of(new Hit(kind, "BULLISH", at(5), at(15), at(confirmed), 942, 976, evidence));
	}

	@Nested
	@DisplayName("inside the OTE zone or not (ICT-42)")
	class Ote {

		@Test
		@DisplayName("inside, above (not back far enough) and below (further back), each said in prices")
		void where() {
			Asked inside = Judgement.ote(closingAt(960), ote("FORMED", 20), at(1), TICK);
			assertThat(inside.answer()).isEqualTo("INSIDE");
			assertThat(inside.context()).extracting(Prediction.Context::what).containsExactly("LEG_START", "LEG_END");
			assertThat(inside.explanation()).containsExactly("The leg runs up from 225.00 to 275.00, 50.00 points.",
				"62% to 79% of the way back from its end is 235.50 to 244.00: the OTE zone, its line at 70.5% at 239.75.",
				"The last close, 240.00, is inside it.");
			Asked above = Judgement.ote(closingAt(1050), ote("FORMED", 20), at(1), TICK);
			assertThat(above.answer()).isEqualTo("OUTSIDE");
			assertThat(above.explanation().getLast()).endsWith("is above it — not back far enough yet.");
			assertThat(Judgement.ote(closingAt(920), ote("FORMED", 20), at(1), TICK).explanation().getLast())
				.endsWith("is below it — further back than the zone.");
			assertThat(Judgement.ote(closingAt(942), ote("FORMED", 20), at(1), TICK).answer()).as("its edge is in it").isEqualTo("INSIDE");
		}

		@Test
		@DisplayName("never asks about a leg off the chart or a range already left")
		void refuses() {
			assertThat(Judgement.ote(closingAt(960), ote("FORMED", 20), at(6), TICK)).isNull();
			List<Hit> left = new ArrayList<>(ote("FORMED", 20));
			left.addAll(ote("BROKEN_LOW", 30));
			assertThat(Judgement.ote(closingAt(960), left, at(1), TICK)).isNull();
		}
	}

	/** A day of 68 candles opening at 1000: its low at candle {@code lowAt}, its high at {@code highAt}, closing at {@code close}. */
	private static List<Bar> day(int lowAt, long low, int highAt, long high, long close) {
		List<Bar> bars = new ArrayList<>();
		for (int i = 0; i < 68; i++) {
			long c = i == 67 ? close : 1000;
			long lo = Math.min(1000, c) - (i == lowAt ? 1000 - low + Math.min(0, c - 1000) : 0);
			long hi = Math.max(1000, c) + (i == highAt ? high - Math.max(1000, c) : 0);
			bars.add(new Bar(at(i), 1000, hi, Math.min(lo, Math.min(1000, c)), c, 1));
		}
		return bars;
	}

	private static List<Hit> po3Hit(String direction) {
		return List.of(new Hit(null, direction, at(0), at(10), at(68), 900, 1200, Map.of()));
	}

	@Nested
	@DisplayName("which power of three (ICT-62)")
	class Po3 {

		@Test
		@DisplayName("bullish: up on the day, the low first, and the low at least 15% of the range below the open")
		void bullish() {
			Asked asked = Judgement.po3(day(10, 900, 50, 1200, 1150), po3Hit("BULLISH"), TICK);
			assertThat(asked.answer()).isEqualTo("BULLISH");
			assertThat(asked.values()).containsExactly("BULLISH", "BEARISH", "NEITHER");
			assertThat(asked.explanation()).contains("The low lay 25.00 points below the open: 33% of the day's 75.00-point range — "
				+ "enough to count as the manipulation.");
		}

		@Test
		@DisplayName("neither says which condition failed; a day the rule and the candles read differently is never asked")
		void neither() {
			Asked lowFirstDown = Judgement.po3(day(10, 900, 50, 1200, 950), List.of(), TICK);
			assertThat(lowFirstDown.answer()).isEqualTo("NEITHER");
			assertThat(lowFirstDown.explanation().getLast()).contains("needs its high before its low");
			Asked shallow = Judgement.po3(day(10, 990, 50, 1200, 1150), List.of(), TICK);
			assertThat(shallow.explanation().getLast()).contains("too shallow");
			assertThat(Judgement.po3(day(10, 900, 50, 1200, 1150), List.of(), TICK)).as("the rule found nothing").isNull();
		}
	}

	@Nested
	@DisplayName("where the stop goes (ICT-80)")
	class Stop {

		/** A swing low at 960 (candle 20) swept by candle 36 down to 940 and closing back above; the last close 1000. */
		private List<Bar> swept(long laterLow) {
			List<Bar> bars = closingAt(1000);
			bars.set(36, new Bar(at(36), 1000, 1005, 940, 990, 1));
			bars.set(38, new Bar(at(38), 1000, 1005, laterLow, 1000, 1));
			return bars;
		}

		private final List<Hit> sweep = List.of(new Hit("SWEEP", "BULLISH", at(20), at(36), at(37), 960, 960,
			Map.of("side", "SSL", "price", 960L)));

		@Test
		@DisplayName("beyond the low that made the sweep is right; just under the entry and just under the old swing are not")
		void beyond() {
			Asked asked = Judgement.stopPlacement(swept(990), sweep, TICK, new java.util.Random(7));
			Map<String, String> labels = new java.util.HashMap<>();
			asked.options().forEach(o -> labels.put(o.value(), o.label()));
			assertThat(asked.values()).containsExactly("A", "B", "C");
			assertThat(labels.get(asked.answer())).isEqualTo(asked.answer() + " — 234.75");
			assertThat(labels.values()).containsExactlyInAnyOrder("A — 234.75", "B — 239.75", "C — 246.75")
				.extracting(l -> l.substring(4)).containsExactlyInAnyOrder("234.75", "239.75", "246.75");
			assertThat(asked.context()).extracting(Prediction.Context::what)
				.containsExactly("TRADE_ENTRY", "STOP_A", "STOP_B", "STOP_C");
			assertThat(asked.explanation().getFirst())
				.isEqualTo("Price swept the swing low at 240.00: it traded to 235.00 and closed back above it. The trade is long from 250.00.");
			// The letters are drawn at random: another seed, another order, the same three stops.
			assertThat(java.util.stream.IntStream.range(0, 20).mapToObj(n ->
				Judgement.stopPlacement(swept(990), sweep, TICK, new java.util.Random(n)).answer()).distinct().count()).isGreaterThan(1);
		}

		@Test
		@DisplayName("never asks once price has gone beyond the sweep's extreme, or without a recent sweep")
		void refuses() {
			assertThat(Judgement.stopPlacement(swept(930), sweep, TICK, new java.util.Random(1))).isNull();
			assertThat(Judgement.stopPlacement(swept(990), List.of(), TICK, new java.util.Random(1))).isNull();
		}
	}
}
