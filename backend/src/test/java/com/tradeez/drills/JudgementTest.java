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
}
