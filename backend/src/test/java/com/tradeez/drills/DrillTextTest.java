package com.tradeez.drills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.tradeez.analysis.BosChochDetector;
import com.tradeez.analysis.DetectedSetup;
import com.tradeez.analysis.Detector;
import com.tradeez.analysis.DisplacementDetector;
import com.tradeez.analysis.EqualHighsLowsDetector;
import com.tradeez.analysis.FvgDetector;
import com.tradeez.analysis.LiquidityDetector;
import com.tradeez.analysis.StructureDetector;
import com.tradeez.analysis.SweepDetector;
import com.tradeez.analysis.SwingDetector;
import com.tradeez.analysis.VolumeImbalanceDetector;

/**
 * T13 (DEC-054) — a drill's words: each rule in words from its parameters, each hit explained from its
 * evidence, in the prices the chart shows. {@link RealDrillApiTest} renders them for real stored hits.
 */
class DrillTextTest {

	private static final BigDecimal NQ_TICK = new BigDecimal("0.25");

	/** A stored hit as the scanner writes one; built directly, as only its fields matter here. */
	private static DetectedSetup setup(Detector detector, String kind, String direction, long low, long high,
			Map<String, Object> evidence) {
		DetectedSetup s = BeanUtils.instantiateClass(DetectedSetup.class);
		ReflectionTestUtils.setField(s, "detector", detector.id());
		ReflectionTestUtils.setField(s, "kind", kind);
		ReflectionTestUtils.setField(s, "direction", direction);
		ReflectionTestUtils.setField(s, "zoneLowTicks", low);
		ReflectionTestUtils.setField(s, "zoneHighTicks", high);
		ReflectionTestUtils.setField(s, "params", detector.parameters());
		ReflectionTestUtils.setField(s, "evidence", evidence);
		return s;
	}

	private static Map<String, Object> evidence(Object... pairs) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], pairs[i + 1]);
		}
		return map;
	}

	@Test
	@DisplayName("a gap: its geometry and its size, in the candles' own prices")
	void fvg() {
		DetectedSetup gap = setup(new FvgDetector(), null, "BULLISH", 80_004, 80_014, evidence(
			"candle1HighTicks", 80_004, "candle1LowTicks", 79_996, "candle3HighTicks", 80_035,
			"candle3LowTicks", 80_014, "gapTicks", 10, "sumRangeTicks", 112, "lookback", 14, "thresholdTicks", 2));
		assertThat(DrillText.label(gap)).isEqualTo("Bullish gap");
		assertThat(DrillText.explain(gap, NQ_TICK)).containsExactly(
			"Candle 1's high, 20001.00, is below candle 3's low, 20003.50. Neither traded in the 10 ticks "
				+ "(2.50 points) between them: a bullish gap.",
			"Big enough to count: the rule needs at least 2 ticks here — 25% of the 8.0-tick average range of "
				+ "the 14 candles before candle 1.");

		DetectedSetup bearish = setup(new FvgDetector(), null, "BEARISH", 80_010, 80_020, evidence(
			"candle1HighTicks", 80_030, "candle1LowTicks", 80_020, "candle3HighTicks", 80_010,
			"candle3LowTicks", 79_990, "gapTicks", 10, "sumRangeTicks", 112, "lookback", 14, "thresholdTicks", 2));
		assertThat(DrillText.explain(bearish, NQ_TICK).getFirst())
			.startsWith("Candle 1's low, 20005.00, is above candle 3's high, 20002.50").endsWith("a bearish gap.");

		// An earlier draft said the gap "was crossed only by candle 2", which the rule never checks.
		assertThat(String.join(" ", DrillText.explain(gap, NQ_TICK)))
			.doesNotContain("only by candle 2").doesNotContainIgnoringCase("will").doesNotContainIgnoringCase("always");
	}

	@Test
	@DisplayName("a swing, a structure label and an equal pair")
	void swingsAndStructure() {
		DetectedSetup low = setup(new SwingDetector(), "LOW", "BULLISH", 80_000, 80_000, evidence("price", 80_000));
		assertThat(DrillText.label(low)).isEqualTo("Swing low");
		assertThat(DrillText.explain(low, NQ_TICK)).singleElement().asString()
			.startsWith("Its low, 20000.00, stands below the low of the candle before it").endsWith("a swing low, "
				+ "known when that next candle closed.");

		DetectedSetup lh = setup(new StructureDetector(), "LH", "BEARISH", 80_010, 80_010,
			evidence("price", 80_010, "previousPrice", 80_020));
		assertThat(DrillText.label(lh)).isEqualTo("Lower high (LH)");
		assertThat(DrillText.explain(lh, NQ_TICK)).containsExactly("This swing high at 20002.50 came after a swing "
			+ "high at 20005.00: 10 ticks lower — a lower high (LH).");
		DetectedSetup el = setup(new StructureDetector(), "EL", "NEUTRAL", 80_010, 80_010,
			evidence("price", 80_010, "previousPrice", 80_010));
		assertThat(DrillText.explain(el, NQ_TICK)).containsExactly("This swing low at 20002.50 came after a swing "
			+ "low at 20002.50: the same price — an equal low (EL).");

		DetectedSetup eqh = setup(new EqualHighsLowsDetector(), "EQH", "BEARISH", 80_020, 80_021,
			evidence("firstPrice", 80_021, "secondPrice", 80_020));
		assertThat(DrillText.label(eqh)).isEqualTo("Equal highs");
		assertThat(DrillText.explain(eqh, NQ_TICK).getFirst()).isEqualTo("Two swing highs, at 20005.25 and "
			+ "20005.00: 1 tick apart, within the rule's tolerance, and no candle between them traded above the higher.");
	}

	@Test
	@DisplayName("a break: the structure before it, the close beyond, and which name that makes it")
	void bosChoch() {
		DetectedSetup choch = setup(new BosChochDetector(), "CHOCH", "BEARISH", 80_000, 80_000,
			evidence("brokenPrice", 80_000, "closeTicks", 79_990, "highLabel", "HH", "lowLabel", "HL"));
		assertThat(DrillText.label(choch)).isEqualTo("Change of character (CHoCH)");
		assertThat(DrillText.explain(choch, NQ_TICK)).containsExactly(
			"Before the break, the latest swing high was a higher high (HH) and the latest swing low a higher low "
				+ "(HL): structure was bullish.",
			"This candle closed at 19997.50, below the swing low at 20000.00 — the first close beyond it since it formed.",
			"Against the trend: a change of character (CHoCH).");
	}

	@Test
	@DisplayName("a sweep closes back; a run holds beyond")
	void sweepAndRun() {
		SweepDetector rule = new SweepDetector();
		DetectedSetup sweep = setup(rule, "SWEEP", "BEARISH", 80_000, 80_000,
			evidence("side", "BSL", "price", 80_000, "takenCloseTicks", 79_996));
		assertThat(DrillText.label(sweep)).isEqualTo("Sweep of buy-side liquidity");
		assertThat(DrillText.explain(sweep, NQ_TICK)).containsExactly("This candle traded above the swing high at "
			+ "20000.00, taking the buy-side liquidity above it, and closed back at 19999.00, at or below the level: a sweep.");

		DetectedSetup run = setup(rule, "RUN", "BEARISH", 80_000, 80_000,
			evidence("side", "SSL", "price", 80_000, "takenCloseTicks", 79_990));
		assertThat(DrillText.label(run)).isEqualTo("Run through sell-side liquidity");
		assertThat(DrillText.explain(run, NQ_TICK).getFirst()).contains("closed beyond it, at 19997.50")
			.contains("The next 2 candles closed beyond it too: a run");
	}

	@Test
	@DisplayName("a volume imbalance and a displacement, from their candles' numbers")
	void imbalanceAndDisplacement() {
		DetectedSetup vi = setup(new VolumeImbalanceDetector(), null, "BEARISH", 80_000, 80_006, evidence(
			"candle1LowTicks", 79_990, "candle2HighTicks", 80_002, "sizeTicks", 6));
		assertThat(DrillText.explain(vi, NQ_TICK)).containsExactly(
			"Candle 1's body bottoms out at 20001.50; candle 2's body tops out at 20000.00, 6 ticks lower.",
			"The bodies do not touch, but the wicks overlap: candle 2's high, 20000.50, is not below candle 1's low, 19997.50.",
			"At least the rule's 4 ticks between the bodies: a bearish volume imbalance.");

		DetectedSetup move = setup(new DisplacementDetector(), null, "BULLISH", 80_000, 80_060,
			evidence("candles", 2, "bodyTicks", 58, "rangeSumTicks", 140));
		assertThat(DrillText.explain(move, NQ_TICK)).containsExactly(
			"2 candles in a row closed up, from 20000.00 to 20015.00, with bodies adding up to 58 ticks.",
			"The rule asks for 200% of the 10.0-tick average range of the 14 candles before: 20.0 ticks. And the move "
				+ "left a bullish fair value gap behind it.");
	}

	@Test
	@DisplayName("every drilled rule reads in words, with its own parameters; other rules are not drilled")
	void rules() {
		assertThat(DrillText.rule(new FvgDetector()).definition()).contains("25%").contains("14 candles");
		assertThat(DrillText.rule(new SwingDetector()).definition()).contains("the high of the candle before it");
		assertThat(DrillText.rule(new VolumeImbalanceDetector()).definition()).contains("at least 4 ticks");
		assertThat(DrillText.rule(new SweepDetector()).definition()).contains("next 2 candles").contains("500");
		assertThat(DrillText.rule(new EqualHighsLowsDetector()).definition()).contains("10%").contains("3 candles");
		for (Detector d : List.of(new DisplacementDetector(), new StructureDetector(), new BosChochDetector())) {
			assertThat(DrillText.rule(d).definition()).doesNotContain("null").isNotBlank();
			assertThat(DrillText.rule(d).version()).isEqualTo(d.ruleVersion());
		}
		assertThatThrownBy(() -> DrillText.rule(new LiquidityDetector())).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("every drill's rule has words, and every name a drill offers has a meaning")
	void catalogueIsComplete() {
		for (DrillKind kind : DrillKind.values()) {
			assertThat(kind.instructions()).isNotBlank();
			assertThat(kind.markName() == null).isEqualTo(kind.mode() == DrillKind.Mode.CLASSIFY);
		}
		assertThat(DrillKind.NAMES).containsKeys("HH", "LH", "EH", "HL", "LL", "EL", "BOS", "CHOCH", "SWEEP", "RUN");
	}
}
