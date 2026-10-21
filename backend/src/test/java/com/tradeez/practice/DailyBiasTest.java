package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.marketdata.Bar;
import com.tradeez.practice.DailyBias.Bias;
import com.tradeez.practice.DailyBias.Suggestion;

/** daily-bias-1.0.0 (ICT-61, DEC-066): each reading of the last two whole days, with its reasoning. */
class DailyBiasTest {

	private static final BigDecimal TICK = new BigDecimal("0.25");
	/** The day before: 100 to 200 ticks, 25.00 to 50.00. */
	private static final Bar BEFORE = day(150, 200, 100, 150);

	private static Bar day(long open, long high, long low, long close) {
		return new Bar(Instant.parse("2025-05-05T22:00:00Z"), open, high, low, close, 1);
	}

	private static Suggestion after(long high, long low, long close) {
		return DailyBias.suggest(BEFORE, day(150, high, low, close), TICK);
	}

	@Test
	@DisplayName("a close beyond the day before's high or low suggests continuation")
	void continuation() {
		assertThat(after(220, 140, 210)).satisfies(s -> {
			assertThat(s.bias()).isEqualTo(Bias.BULLISH);
			assertThat(s.reasoning()).isEqualTo("Yesterday closed at 52.50, above the day before's high at 50.00: the reading "
				+ "commonly taught looks for higher prices next — buy-side liquidity.");
		});
		assertThat(after(160, 80, 90).bias()).isEqualTo(Bias.BEARISH);
		assertThat(after(160, 80, 90).reasoning()).contains("below the day before's low at 25.00");
	}

	@Test
	@DisplayName("running a side and closing back inside suggests the other way")
	void reversal() {
		assertThat(after(220, 140, 180)).satisfies(s -> {
			assertThat(s.bias()).isEqualTo(Bias.BEARISH);
			assertThat(s.reasoning()).contains("traded above the day before's high at 50.00 but closed back inside, at 45.00");
		});
		// A close exactly on the high is back inside.
		assertThat(after(220, 140, 200).bias()).isEqualTo(Bias.BEARISH);
		assertThat(after(160, 80, 120).bias()).isEqualTo(Bias.BULLISH);
	}

	@Test
	@DisplayName("an inside day, or one that ran both sides and closed inside, suggests nothing")
	void nothing() {
		assertThat(after(190, 110, 150)).satisfies(s -> {
			assertThat(s.bias()).isNull();
			assertThat(s.reasoning()).isEqualTo("Yesterday stayed inside the day before's range, 25.00 to 50.00: an inside day "
				+ "says little.");
		});
		assertThat(after(220, 80, 150).bias()).isNull();
		assertThat(after(220, 80, 150).reasoning()).contains("beyond both");
	}
}
