package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.practice.FillEngine.Direction;

/** BUG-074 — a plan's killzone: the one kept since T22, else read from the moment it was placed. */
class TradePlanTest {

	/** 09:00 New York on a winter Wednesday: inside the New York AM killzone. */
	private static final Instant NY_AM = Instant.parse("2025-03-05T14:00:00Z");

	@Test
	@DisplayName("a plan from before T22 kept no killzone: it is read from its time, and none at noon")
	void before() {
		assertThat(new TradePlan(UUID.randomUUID(), 1, Direction.LONG, 1, 3, 1, 2, NY_AM).killzoneWhenPlaced())
			.isEqualTo("NEW_YORK_AM");
		assertThat(new TradePlan(UUID.randomUUID(), 1, Direction.LONG, 1, 3, 1, 2, Instant.parse("2025-03-05T17:00:00Z"))
			.killzoneWhenPlaced()).isNull();
	}

	@Test
	@DisplayName("a plan from T22 on keeps the killzone it was placed in: the record, not a reading")
	void kept() {
		TradePlan plan = new TradePlan(UUID.randomUUID(), 1, Direction.LONG, 1, 3, 1, 2, NY_AM, null, null, null, "LONDON",
			null, null, null, null);
		assertThat(plan.killzoneWhenPlaced()).isEqualTo("LONDON");
	}
}
