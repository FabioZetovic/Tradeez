package com.tradeez.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * T20 (DEC-073) — smt-1.0.0 on two markets built by hand: flat candles, with a spike wherever a swing should
 * be, so every swing is exactly where the test says. {@link DetectorContractTest} checks the rule keeps the
 * contract beside a companion.
 */
class SmtDetectorTest {

	private static final Timeframe TF = Timeframe.M5;
	private static final Instant T0 = Instant.parse("2025-05-06T14:00:00Z");
	private static final SmtDetector SMT = new SmtDetector();

	private static Instant at(int index) {
		return T0.plusSeconds(300L * index);
	}

	/**
	 * {@code length} flat candles around {@code base} — none a swing, their highs and lows all equal — with a
	 * spike to each given price at its index: a swing high above the base, a swing low below it.
	 */
	private static List<Bar> market(int length, long base, Map<Integer, Long> spikes) {
		List<Bar> bars = new ArrayList<>();
		for (int i = 0; i < length; i++) {
			Long spike = spikes.get(i);
			long high = spike != null && spike > base ? spike : base + 1;
			long low = spike != null && spike < base ? spike : base - 1;
			bars.add(new Bar(at(i), base, high, low, base, 1));
		}
		return bars;
	}

	private static List<Detection> smt(List<Bar> nq, List<Bar> es) {
		return SMT.detect(nq, Segments.beside(nq, "ES", es), TF);
	}

	@Test
	@DisplayName("NQ makes a higher high while ES makes a lower one: a bearish SMT at NQ's swing, known once both are")
	void bearish() {
		List<Detection> found = smt(market(30, 100, Map.of(5, 120L, 15, 130L)), market(30, 200, Map.of(5, 220L, 15, 215L)));
		assertThat(found).singleElement().satisfies(d -> {
			assertThat(d.direction()).isEqualTo(Detection.Direction.BEARISH);
			assertThat(d.kind()).isNull();
			assertThat(d.shape()).isEqualTo(Detection.Shape.POINT);
			assertThat(d.anchor()).isEqualTo(at(15));
			assertThat(d.lowTicks()).isEqualTo(130);
			assertThat(d.start()).isEqualTo(at(4));
			assertThat(d.end()).isEqualTo(at(16));
			assertThat(d.confirmedAt()).isEqualTo(at(17));
			assertThat(d.evidence()).isEqualTo(Map.of("price", 130L, "previousTime", at(5).getEpochSecond(),
				"previousPrice", 120L, "companion", "ES", "companionTime", at(15).getEpochSecond(), "companionPrice", 215L,
				"companionPreviousTime", at(5).getEpochSecond(), "companionPreviousPrice", 220L));
		});
		// The other way round is the same divergence, read on NQ's chart: NQ failed where ES went on.
		assertThat(smt(market(30, 100, Map.of(5, 120L, 15, 110L)), market(30, 200, Map.of(5, 220L, 15, 230L))))
			.singleElement().extracting(Detection::direction).isEqualTo(Detection.Direction.BEARISH);
	}

	@Test
	@DisplayName("lows mirror it: NQ's lower low against ES's higher low is a bullish SMT")
	void bullish() {
		assertThat(smt(market(30, 100, Map.of(5, 80L, 15, 70L)), market(30, 200, Map.of(5, 180L, 15, 185L))))
			.singleElement().satisfies(d -> {
				assertThat(d.direction()).isEqualTo(Detection.Direction.BULLISH);
				assertThat(d.lowTicks()).isEqualTo(70);
			});
	}

	@Test
	@DisplayName("both beyond, or both short, is no divergence; an equal high is not beyond")
	void agreement() {
		List<Bar> nq = market(30, 100, Map.of(5, 120L, 15, 130L));
		assertThat(smt(nq, market(30, 200, Map.of(5, 220L, 15, 230L)))).isEmpty();
		assertThat(smt(market(30, 100, Map.of(5, 120L, 15, 110L)), market(30, 200, Map.of(5, 220L, 15, 210L)))).isEmpty();
		assertThat(smt(nq, market(30, 200, Map.of(5, 220L, 15, 220L)))).hasSize(1);
	}

	@Test
	@DisplayName("each pair of swings at most 2 candles apart; the confirmation waits for the later of the two")
	void alignment() {
		List<Bar> nq = market(30, 100, Map.of(5, 120L, 15, 130L));
		assertThat(smt(nq, market(30, 200, Map.of(5, 220L, 18, 215L)))).isEmpty();
		assertThat(smt(nq, market(30, 200, Map.of(8, 220L, 15, 215L)))).isEmpty();
		List<Bar> es = market(30, 200, Map.of(3, 220L, 17, 215L));
		assertThat(smt(nq, es)).singleElement().satisfies(d -> {
			assertThat(d.start()).isEqualTo(at(2));
			assertThat(d.end()).isEqualTo(at(18));
			assertThat(d.confirmedAt()).isEqualTo(at(19));
		});
		// Until ES's swing is confirmed nothing is known, whatever NQ shows.
		assertThat(smt(nq.subList(0, 18), es)).isEmpty();
	}

	@Test
	@DisplayName("everything read lies within 200 candles: from the candle before the earlier swing to the confirming one")
	void span() {
		assertThat(smt(market(210, 100, Map.of(5, 120L, 202, 130L)), market(210, 200, Map.of(5, 220L, 202, 215L))))
			.hasSize(1);
		assertThat(smt(market(210, 100, Map.of(5, 120L, 203, 130L)), market(210, 200, Map.of(5, 220L, 203, 215L))))
			.isEmpty();
	}

	@Test
	@DisplayName("NQ is read beside ES and ES beside NQ; alone, or beside nothing, a market has nothing to diverge from")
	void pairs() {
		assertThat(SMT.companionOf("NQ")).isEqualTo("ES");
		assertThat(SMT.companionOf("ES")).isEqualTo("NQ");
		assertThat(SMT.companionOf("DEMO")).isNull();
		List<Bar> nq = market(30, 100, Map.of(5, 120L, 15, 130L));
		assertThat(SMT.detect(nq, TF)).isEmpty();
		assertThat(SMT.detect(nq, Detector.Companion.NONE, TF)).isEmpty();
		assertThat(new SwingDetector().companionOf("NQ")).isNull();
	}
}
