package com.tradeez.drills;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.analysis.RuleText;

/** The drill catalogue (DEC-054): every drill says what it asks, and every name it offers has a meaning. */
class DrillKindTest {

	@Test
	@DisplayName("every drill has instructions, a spot drill says what a mark is, and every offered name reads in words")
	void catalogueIsComplete() {
		for (DrillKind kind : DrillKind.values()) {
			assertThat(kind.instructions()).isNotBlank();
			assertThat(kind.markName() == null).isEqualTo(kind.mode() != DrillKind.Mode.IDENTIFY);
		}
		assertThat(RuleText.NAMES).containsKeys("HH", "LH", "EH", "HL", "LL", "EL", "BOS", "CHOCH", "SWEEP", "RUN");
	}
}
