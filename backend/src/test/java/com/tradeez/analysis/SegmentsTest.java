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
	@DisplayName("consecutive means each candle opens exactly one step after the last")
	void consecutive() {
		assertThat(Segments.consecutive(List.of(at(0), at(1), at(2)), 60)).isTrue();
		assertThat(Segments.consecutive(List.of(at(0), at(1), at(3)), 60)).isFalse();
		assertThat(Segments.consecutive(List.of(at(0), at(1)), 300)).isFalse();
		assertThat(Segments.consecutive(List.of(at(0)), 60)).isTrue();
	}
}
