package com.tradeez.drills;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.drills.RiskQuestion.Kind;
import com.tradeez.drills.RiskQuestion.Question;

/** risk-1.0.0 (T26, DEC-070): the arithmetic of risk, worked by hand. */
class RiskQuestionTest {

	@Test
	@DisplayName("position size: $500 at risk over $250 a contract is 2 contracts, worked in three steps")
	void contracts() {
		Question q = RiskQuestion.contracts(50_000, 1, false, 50);
		assertThat(q.kind()).isEqualTo(Kind.CONTRACTS);
		assertThat(q.answer()).isEqualTo(2);
		assertThat(q.tolerance()).isZero();
		assertThat(q.unit()).isEqualTo("contracts");
		assertThat(q.limitSeconds()).isEqualTo(30);
		assertThat(q.text()).isEqualTo("An account of $50,000, risking 1% of it on this trade. The stop is 50 ticks away, on NQ "
			+ "($5 a tick). How many contracts? Leave commission and slippage out.");
		assertThat(q.working()).containsExactly("1% of $50,000 is $500.", "50 ticks × $5 a tick = $250 a contract.",
			"$500 ÷ $250 = 2.00: 2 contracts — always rounded down, or the risk is over the limit.");
		assertThat(q.givens()).containsEntry("product", "NQ").containsEntry("stopTicks", 50);
	}

	@Test
	@DisplayName("rounded down, exact when it divides, MNQ a tenth of the size — and none when one contract is too many")
	void rounding() {
		// $250 over $20 a contract is 12.5: twelve.
		Question micro = RiskQuestion.contracts(25_000, 1, true, 40);
		assertThat(micro.answer()).isEqualTo(12);
		assertThat(micro.working().get(1)).isEqualTo("40 ticks × $0.50 a tick = $20 a contract.");
		// $500 over $500 is exactly one.
		assertThat(RiskQuestion.contracts(50_000, 1, false, 100).answer()).isEqualTo(1);
		// $125 against $600 a contract: none, and the way out.
		Question none = RiskQuestion.contracts(25_000, 0.5, false, 120);
		assertThat(none.answer()).isZero();
		assertThat(none.text()).contains("risking 0.5% of it");
		assertThat(none.working().getLast()).isEqualTo("None: one NQ contract would risk more than the limit. MNQ, a tenth of "
			+ "the size, may fit.");
		assertThat(RiskQuestion.contracts(25_000, 0.5, true, 120).working()).hasSize(3);
	}

	@Test
	@DisplayName("every drawn position size is at most thirty contracts, and agrees with its own givens")
	void drawn() {
		Random random = new Random(26);
		for (int i = 0; i < 2_000; i++) {
			Question q = RiskQuestion.of(Kind.CONTRACTS, random);
			long riskCents = Math.round(((Number) q.givens().get("account")).intValue()
				* ((Number) q.givens().get("riskPercent")).doubleValue());
			long perContract = ((Number) q.givens().get("stopTicks")).intValue() * ("MNQ".equals(q.givens().get("product")) ? 50L : 500L);
			assertThat(q.answer()).isEqualTo(riskCents / perContract).isLessThanOrEqualTo(RiskQuestion.MAX_CONTRACTS);
		}
	}

	@Test
	@DisplayName("a losing streak compounds: 8 losses at 2% leave 85.08%, down 14.92% — not the 16% of fixed dollars")
	void streak() {
		Question q = RiskQuestion.streak(2, 8);
		assertThat(q.answer()).isEqualTo(14.92);
		assertThat(q.tolerance()).isEqualTo(0.5);
		assertThat(q.limitSeconds()).isEqualTo(45);
		assertThat(q.working()).containsExactly("Each loss leaves 0.98 of what was there.",
			"0.98 to the power of 8 = 0.8508: 85.08% is left.",
			"Down 14.92%. Risking the same dollars each time it would be 16.00%: a risk that shrinks with the account keeps "
				+ "the hole shallower.");
		assertThat(RiskQuestion.streak(0.5, 3).working().getFirst()).isEqualTo("Each loss leaves 0.995 of what was there.");
	}

	@Test
	@DisplayName("recovery: 30% down needs 42.86% up, 50% down needs 100%")
	void recovery() {
		Question q = RiskQuestion.recovery(30);
		assertThat(q.answer()).isEqualTo(42.86);
		assertThat(q.tolerance()).isEqualTo(1);
		assertThat(q.working().get(1)).isEqualTo("Back to 100 from 70: 100 ÷ 70 = 1.4286 — a gain of 42.86%.");
		assertThat(RiskQuestion.recovery(50).answer()).isEqualTo(100);
		assertThat(RiskQuestion.recovery(75).answer()).isEqualTo(300);
	}

	@Test
	@DisplayName("graded within the tolerance, edges included: none for contracts, half a point for a streak")
	void within() {
		assertThat(RiskQuestion.within(2, 0, 2)).isTrue();
		assertThat(RiskQuestion.within(2, 0, 2.4)).isFalse();
		assertThat(RiskQuestion.within(14.92, 0.5, 15.42)).isTrue();
		assertThat(RiskQuestion.within(14.92, 0.5, 14.42)).isTrue();
		assertThat(RiskQuestion.within(14.92, 0.5, 15.43)).isFalse();
	}
}
