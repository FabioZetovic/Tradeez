package com.tradeez.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.marketdata.Bar;

/** DEC-049 — no pattern spans a contract roll or a session break. */
class SegmentsTest {

	private static final Instant T0 = Instant.parse("2025-05-01T13:00:00Z");

	private static Bar at(int minute) {
		return new Bar(T0.plusSeconds(60L * minute), 100, 101, 99, 100, 1);
	}

	private static List<Integer> minutes(List<Bar> segment) {
		return segment.stream().map(b -> (int) ((b.openTime().getEpochSecond() - T0.getEpochSecond()) / 60)).toList();
	}

	@Test
	@DisplayName("without a roll the candles are one segment")
	void noRoll() {
		List<Bar> bars = List.of(at(0), at(1), at(2));
		assertThat(Segments.atRolls(bars, List.of())).containsExactly(bars);
		assertThat(Segments.atRolls(List.of(), List.of(T0))).isEmpty();
	}

	@Test
	@DisplayName("a roll starts a new segment at the first candle of the new contract")
	void splitsAtTheRoll() {
		List<List<Bar>> segments = Segments.atRolls(List.of(at(0), at(1), at(2), at(3)), List.of(at(2).openTime()));
		assertThat(segments).extracting(SegmentsTest::minutes).containsExactly(List.of(0, 1), List.of(2, 3));
	}

	@Test
	@DisplayName("a roll inside a gap, two rolls in one gap, or one before the first candle split once or not at all")
	void rollsBetweenCandles() {
		List<Bar> bars = List.of(at(0), at(1), at(60), at(61));
		Instant inTheGap = at(30).openTime();
		assertThat(Segments.atRolls(bars, List.of(inTheGap))).extracting(SegmentsTest::minutes)
			.containsExactly(List.of(0, 1), List.of(60, 61));
		assertThat(Segments.atRolls(bars, List.of(inTheGap, at(40).openTime()))).hasSize(2);
		assertThat(Segments.atRolls(bars, List.of(T0.minusSeconds(3600)))).extracting(SegmentsTest::minutes)
			.containsExactly(List.of(0, 1, 60, 61));
	}

	@Test
	@DisplayName("a rule reading two markets splits at both markets' rolls, in time order, each once (T20)")
	void merged() {
		Instant a = at(10).openTime();
		Instant b = at(20).openTime();
		Instant c = at(30).openTime();
		assertThat(Segments.merged(List.of(a, c), List.of(b, c))).containsExactly(a, b, c);
		assertThat(Segments.merged(List.of(), List.of(b))).containsExactly(b);
	}

	@Test
	@DisplayName("beside a segment: the other market's candles opening from its first candle to its last (T20)")
	void beside() {
		List<Bar> other = List.of(at(0), at(1), at(2), at(3), at(5), at(6));
		Detector.Companion es = Segments.beside(List.of(at(1), at(2), at(4)), "ES", other);
		assertThat(es.symbol()).isEqualTo("ES");
		assertThat(minutes(es.bars())).containsExactly(1, 2, 3);
		assertThat(minutes(Segments.beside(List.of(at(1), at(3)), "ES", other).bars())).containsExactly(1, 2, 3);
		assertThat(minutes(Segments.beside(List.of(at(4)), "ES", other).bars())).isEmpty();
		assertThat(minutes(Segments.beside(List.of(at(6), at(9)), "ES", other).bars())).containsExactly(6);
		assertThat(Segments.beside(List.of(), "ES", other).bars()).isEmpty();
	}

	@Test
	@DisplayName("consecutive means each candle opens exactly one step after the last")
	void consecutive() {
		assertThat(Segments.consecutive(List.of(at(0), at(1), at(2)), 60)).isTrue();
		assertThat(Segments.consecutive(List.of(at(0), at(1), at(3)), 60)).isFalse();
		assertThat(Segments.consecutive(List.of(at(0), at(1)), 300)).isFalse();
		assertThat(Segments.consecutive(List.of(at(0)), 60)).isTrue();
	}
}
