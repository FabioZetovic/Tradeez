package com.tradeez.analysis;

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


/**
 * The words of every rule (DEC-054, DEC-055): each rule in words from its parameters, each hit explained
 * from its evidence, in the prices the chart shows. {@link DetectorContractTest} renders them for every
 * hit of every rule on the pattern market.
 */
class RuleTextTest {

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
		assertThat(RuleText.label(gap)).isEqualTo("Bullish gap");
		assertThat(RuleText.explain(gap, NQ_TICK)).containsExactly(
			"Candle 1's high, 20001.00, is below candle 3's low, 20003.50. Neither traded in the 10 ticks "
				+ "(2.50 points) between them: a bullish gap.",
			"Big enough to count: the rule needs at least 2 ticks here — 25% of the 8.0-tick average range of "
				+ "the 14 candles before candle 1.");

		DetectedSetup bearish = setup(new FvgDetector(), null, "BEARISH", 80_010, 80_020, evidence(
			"candle1HighTicks", 80_030, "candle1LowTicks", 80_020, "candle3HighTicks", 80_010,
			"candle3LowTicks", 79_990, "gapTicks", 10, "sumRangeTicks", 112, "lookback", 14, "thresholdTicks", 2));
		assertThat(RuleText.explain(bearish, NQ_TICK).getFirst())
			.startsWith("Candle 1's low, 20005.00, is above candle 3's high, 20002.50").endsWith("a bearish gap.");

		// An earlier draft said the gap "was crossed only by candle 2", which the rule never checks.
		assertThat(String.join(" ", RuleText.explain(gap, NQ_TICK)))
			.doesNotContain("only by candle 2").doesNotContainIgnoringCase("will").doesNotContainIgnoringCase("always");
	}

	@Test
	@DisplayName("a swing, a structure label and an equal pair")
	void swingsAndStructure() {
		DetectedSetup low = setup(new SwingDetector(), "LOW", "BULLISH", 80_000, 80_000, evidence("price", 80_000));
		assertThat(RuleText.label(low)).isEqualTo("Swing low");
		assertThat(RuleText.explain(low, NQ_TICK)).singleElement().asString()
			.startsWith("Its low, 20000.00, stands below the low of the candle before it").endsWith("a swing low, "
				+ "known when that next candle closed.");

		DetectedSetup lh = setup(new StructureDetector(), "LH", "BEARISH", 80_010, 80_010,
			evidence("price", 80_010, "previousPrice", 80_020));
		assertThat(RuleText.label(lh)).isEqualTo("Lower high (LH)");
		assertThat(RuleText.explain(lh, NQ_TICK)).containsExactly("This swing high at 20002.50 came after a swing "
			+ "high at 20005.00: 10 ticks lower — a lower high (LH).");
		DetectedSetup el = setup(new StructureDetector(), "EL", "NEUTRAL", 80_010, 80_010,
			evidence("price", 80_010, "previousPrice", 80_010));
		assertThat(RuleText.explain(el, NQ_TICK)).containsExactly("This swing low at 20002.50 came after a swing "
			+ "low at 20002.50: the same price — an equal low (EL).");

		DetectedSetup eqh = setup(new EqualHighsLowsDetector(), "EQH", "BEARISH", 80_020, 80_021,
			evidence("firstPrice", 80_021, "secondPrice", 80_020));
		assertThat(RuleText.label(eqh)).isEqualTo("Equal highs");
		assertThat(RuleText.explain(eqh, NQ_TICK).getFirst()).isEqualTo("Two swing highs, at 20005.25 and "
			+ "20005.00: 1 tick apart, within the rule's tolerance, and no candle between them traded above the higher.");
	}

	@Test
	@DisplayName("a break: the structure before it, the close beyond, and which name that makes it")
	void bosChoch() {
		DetectedSetup choch = setup(new BosChochDetector(), "CHOCH", "BEARISH", 80_000, 80_000,
			evidence("brokenPrice", 80_000, "closeTicks", 79_990, "highLabel", "HH", "lowLabel", "HL"));
		assertThat(RuleText.label(choch)).isEqualTo("Change of character (CHoCH)");
		assertThat(RuleText.explain(choch, NQ_TICK)).containsExactly(
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
		assertThat(RuleText.label(sweep)).isEqualTo("Sweep of buy-side liquidity");
		assertThat(RuleText.explain(sweep, NQ_TICK)).containsExactly("This candle traded above the swing high at "
			+ "20000.00, taking the buy-side liquidity above it, and closed back at 19999.00, at or below the level: a sweep.");

		DetectedSetup run = setup(rule, "RUN", "BEARISH", 80_000, 80_000,
			evidence("side", "SSL", "price", 80_000, "takenCloseTicks", 79_990));
		assertThat(RuleText.label(run)).isEqualTo("Run through sell-side liquidity");
		assertThat(RuleText.explain(run, NQ_TICK).getFirst()).contains("closed beyond it, at 19997.50")
			.contains("The next 2 candles closed beyond it too: a run");
	}

	@Test
	@DisplayName("a volume imbalance and a displacement, from their candles' numbers")
	void imbalanceAndDisplacement() {
		DetectedSetup vi = setup(new VolumeImbalanceDetector(), null, "BEARISH", 80_000, 80_006, evidence(
			"candle1LowTicks", 79_990, "candle2HighTicks", 80_002, "sizeTicks", 6));
		assertThat(RuleText.explain(vi, NQ_TICK)).containsExactly(
			"Candle 1's body bottoms out at 20001.50; candle 2's body tops out at 20000.00, 6 ticks lower.",
			"The bodies do not touch, but the wicks overlap: candle 2's high, 20000.50, is not below candle 1's low, 19997.50.",
			"At least the rule's 4 ticks between the bodies: a bearish volume imbalance.");

		DetectedSetup move = setup(new DisplacementDetector(), null, "BULLISH", 80_000, 80_060,
			evidence("candles", 2, "bodyTicks", 58, "rangeSumTicks", 140));
		assertThat(RuleText.explain(move, NQ_TICK)).containsExactly(
			"2 candles in a row closed up, from 20000.00 to 20015.00, with bodies adding up to 58 ticks.",
			"The rule asks for 200% of the 10.0-tick average range of the 14 candles before: 20.0 ticks. And the move "
				+ "left a bullish fair value gap behind it.");
	}

	@Test
	@DisplayName("a rule reads in words with its own parameters; a rule without words is refused")
	void rules() {
		assertThat(RuleText.definition(new FvgDetector())).contains("25%").contains("14 candles");
		assertThat(RuleText.definition(new SwingDetector())).contains("the high of the candle before it");
		assertThat(RuleText.definition(new VolumeImbalanceDetector())).contains("at least 4 ticks");
		assertThat(RuleText.definition(new SweepDetector())).contains("next 2 candles").contains("500");
		assertThat(RuleText.definition(new EqualHighsLowsDetector())).contains("10%").contains("3 candles");
		for (Detector d : List.of(new DisplacementDetector(), new StructureDetector(), new BosChochDetector())) {
			assertThat(RuleText.definition(d)).doesNotContain("null").isNotBlank();
		}
		assertThatThrownBy(() -> RuleText.definition(unknown())).isInstanceOf(IllegalArgumentException.class);
	}

	/** A rule the words were never written for. */
	private static Detector unknown() {
		return new RuleDetector("NOPE", "nope-1.0.0", "ICT-99", "Nothing", Map.of()) {
			@Override
			public int lookbackBars(com.tradeez.marketdata.Timeframe timeframe) {
				return 1;
			}

			@Override
			public List<Detection> detect(List<com.tradeez.marketdata.Bar> segment,
					com.tradeez.marketdata.Timeframe timeframe) {
				return List.of();
			}
		};
	}

	@Test
	@DisplayName("a gap's life: each state from the candle that reached it, its midpoint in half-ticks")
	void lifecycle() {
		FvgStateDetector rule = new FvgStateDetector();
		Map<String, Object> e = evidence("gapDirection", "BULLISH", "ceHalfTicks", 160_017, "candleHighTicks", 80_030,
			"candleLowTicks", 80_007, "candleCloseTicks", 80_020);
		DetectedSetup ce = setup(rule, "CE", "BULLISH", 80_004, 80_013, e);
		assertThat(RuleText.label(ce)).isEqualTo("Bullish gap, at its CE");
		assertThat(RuleText.explain(ce, NQ_TICK)).containsExactly("The bullish gap from 20001.00 to 20003.25: a later "
			+ "candle traded down to 20001.75, reaching its midpoint at 20002.125 — consequent encroachment.");
		assertThat(RuleText.explain(setup(rule, "EXPIRED", "BULLISH", 80_004, 80_013, e), NQ_TICK).getFirst())
			.endsWith("was still not filled 500 candles after it formed: the rule stops following it.");

		DetectedSetup inverted = setup(new IfvgDetector(), null, "BEARISH", 80_004, 80_013,
			evidence("gapDirection", "BULLISH", "candleCloseTicks", 80_000));
		assertThat(RuleText.label(inverted)).isEqualTo("Bearish inversion gap");
		assertThat(RuleText.explain(inverted, NQ_TICK)).containsExactly(
			"A bullish gap from 20001.00 to 20003.25 formed, and a later candle closed straight through it, at 20000.00, "
				+ "below its bottom.",
			"ICT's claim is that a failed gap flips sides: this one is read as a bearish inversion gap.");
	}

	@Test
	@DisplayName("gaps across a closure and the regular session's opening gap, with how long the market was shut")
	void openingGaps() {
		DetectedSetup nwog = setup(new OpeningGapDetector(), "NWOG", "BULLISH", 80_000, 80_012, evidence(
			"lastCloseTicks", 80_000, "firstOpenTicks", 80_012, "closedMinutes", 2_940));
		assertThat(RuleText.label(nwog)).isEqualTo("New week opening gap (NWOG)");
		assertThat(RuleText.explain(nwog, NQ_TICK)).containsExactly("The market closed at 20000.00 and reopened 49 "
			+ "hours later at 20003.00: a gap of 12 ticks up. Across a weekend: a new week opening gap (NWOG).");

		DetectedSetup flat = setup(new OrgDetector(), null, "NEUTRAL", 80_000, 80_000, evidence(
			"closeTicks", 80_000, "openTicks", 80_000, "ceHalfTicks", 160_000));
		assertThat(RuleText.explain(flat, NQ_TICK)).containsExactly("The regular session closed at 20000.00 (16:15 New "
			+ "York) and the next opened at 20000.00 (09:30): no gap — it opened where it closed, its midpoint at "
			+ "20000.00.");

		DetectedSetup gap = setup(new VoidDetector(), "GAP", "BEARISH", 79_990, 79_996, evidence(
			"candle1LowTicks", 79_996, "candle2HighTicks", 79_990));
		assertThat(RuleText.explain(gap, NQ_TICK).getFirst()).isEqualTo("Candle 2's high, 19997.50, is below candle 1's "
			+ "low, 19999.00: the two ranges do not overlap — a price gap of 6 ticks.");
	}

	@Test
	@DisplayName("ranked swings, a structure shift, liquidity and turtle soup")
	void structureAndLiquidity() {
		DetectedSetup lth = setup(new SwingTierDetector(), "LTH", "BEARISH", 80_100, 80_100, evidence("price", 80_100));
		assertThat(RuleText.explain(lth, NQ_TICK)).containsExactly("This intermediate-term high at 20025.00 stands above "
			+ "the intermediate-term highs either side of it: a long-term high (LTH).");
		assertThat(RuleText.label(setup(new SwingTierDetector(), "ITL", "BULLISH", 1, 1, evidence())))
			.isEqualTo("Intermediate-term low (ITL)");

		DetectedSetup mss = setup(new MssDetector(), null, "BULLISH", 80_050, 80_050, evidence("sweptPrice", 80_000,
			"pivotPrice", 79_990, "brokenPrice", 80_050, "closeTicks", 80_060));
		assertThat(RuleText.explain(mss, NQ_TICK)).containsExactly(
			"A swing low at 19997.50 traded below the earlier swing low at 20000.00, which was still resting: a sweep.",
			"Then a displacement, and this candle closed at 20015.00, above the swing high at 20012.50 — the first close "
				+ "beyond it: a bullish market structure shift.");

		LiquidityDetector liquidity = new LiquidityDetector();
		Map<String, Object> bsl = evidence("side", "BSL", "price", 80_000);
		assertThat(RuleText.label(setup(liquidity, "RESTING", "BEARISH", 80_000, 80_000, bsl)))
			.isEqualTo("Buy-side liquidity, resting");
		assertThat(RuleText.explain(setup(liquidity, "RESTING", "BEARISH", 80_000, 80_000, bsl), NQ_TICK))
			.containsExactly("The swing high at 20000.00: buy-side liquidity — the stops above it, resting from the "
				+ "candle that confirmed the swing.");
		assertThat(RuleText.explain(setup(liquidity, "TAKEN", "BEARISH", 80_000, 80_000, bsl), NQ_TICK).getFirst())
			.isEqualTo("A later candle traded above the swing high at 20000.00, a tick or more beyond it: the buy-side "
				+ "liquidity there is taken.");

		DetectedSetup soup = setup(new TurtleSoupDetector(), null, "BULLISH", 79_990, 79_990,
			evidence("price", 79_990, "closeTicks", 79_995));
		assertThat(RuleText.label(soup)).isEqualTo("Turtle soup at equal lows");
		assertThat(RuleText.explain(soup, NQ_TICK)).containsExactly("This candle traded below the equal lows at "
			+ "19997.50 — the lower of the pair — and closed back at 19998.75, at or above them: the pool was swept. "
			+ "Turtle soup.");
	}
}
