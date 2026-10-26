package com.tradeez.drills;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.marketdata.Timeframe;

/** The timeframe a drill is set on (T43): asked for, or drawn at random on real data — "the time on a chart doesnt matter". */
class DrillTimeframeTest {

	@Test
	@DisplayName("a real drill asked for without a timeframe gets 1m, 5m or 15m at random — each of them, in time")
	void randomOnRealData() {
		Random random = new Random(43);
		Set<Timeframe> drawn = EnumSet.noneOf(Timeframe.class);
		for (int i = 0; i < 60; i++) {
			drawn.add(DrillService.timeframe(null, false, random));
			drawn.add(DrillService.timeframe(" ", false, random));
		}
		assertThat(drawn).containsExactlyInAnyOrder(Timeframe.M1, Timeframe.M5, Timeframe.M15);
	}

	@Test
	@DisplayName("one asked for is kept; a simulated fixture keeps its 5m")
	void askedOrFixture() {
		Random random = new Random(1);
		assertThat(DrillService.timeframe("15m", false, random)).isEqualTo(Timeframe.M15);
		assertThat(DrillService.timeframe("1m", false, random)).isEqualTo(Timeframe.M1);
		for (int i = 0; i < 20; i++) {
			assertThat(DrillService.timeframe(null, true, random)).isEqualTo(Timeframe.M5);
		}
	}
}
