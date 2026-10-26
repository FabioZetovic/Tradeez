package com.tradeez.drills;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.drills.Management.Choice;
import com.tradeez.drills.Management.Exit;
import com.tradeez.drills.Management.Outcome;
import com.tradeez.drills.Management.Trade;
import com.tradeez.marketdata.Bar;

/**
 * manage-1.0.0 (T26, DEC-071) on hand-built 5-minute candles. Fourteen candles four ticks tall make an average
 * range of 4, so the stop is the 8-tick minimum (1.5 × 4 = 6 is less): a long from 1,000 has its stop at 992 and its
 * target at 1,024.
 */
class ManagementTest {

	private static final Instant T0 = Instant.parse("2025-05-01T14:00:00Z");
	private static final long STEP = 300;
	private static final Function<Bar, List<Bar>> NO_MINUTES = bar -> List.of();

	private static Bar bar(int i, long open, long high, long low, long close) {
		return new Bar(T0.plusSeconds(STEP * i), open, high, low, close, 1);
	}

	/** Fifteen flat candles closing at 1,000, then the given closes, each candle from the last close to the next. */
	private static List<Bar> history(long... closes) {
		List<Bar> bars = new ArrayList<>();
		for (int i = 0; i < 15; i++) {
			bars.add(bar(i, 1000, 1002, 998, 1000));
		}
		long last = 1000;
		for (long close : closes) {
			bars.add(bar(bars.size(), last, Math.max(last, close), Math.min(last, close), close));
			last = close;
		}
		return bars;
	}

	/** A long from 1,000: stop 992, target 1,024, risk 8; the chart stops at 1,009. */
	private static final Trade LONG = new Trade(true, T0.plusSeconds(STEP * 15), 1000, 992, 1024, T0.plusSeconds(STEP * 18),
		1009, 1.13);

	/** A hundred candles after the moment: the given ones first, then quiet ones between 1,004 and 1,012. */
	private static List<Bar> future(Bar... first) {
		List<Bar> bars = new ArrayList<>(List.of(first));
		while (bars.size() < Management.HORIZON_CANDLES) {
			bars.add(bar(18 + bars.size(), 1008, 1012, 1004, 1008));
		}
		return bars;
	}

	private static Exit exit(Outcome o, Choice choice) {
		return o.exits().stream().filter(e -> e.choice() == choice).findFirst().orElseThrow();
	}

	@Test
	@DisplayName("the trade: an 8-tick stop, a 3R target, and the first close a whole R up after three candles held")
	void open() {
		// +0.5R, +1.25R after only two candles, +1.125R after three: the moment.
		Trade t = Management.open(history(1004, 1010, 1009), 14, true, STEP);
		assertThat(t).isEqualTo(LONG);
		assertThat(t.risk()).isEqualTo(8);

		// The stop touched first, or the target reached first: no moment to ask about.
		assertThat(Management.open(history(995, 991, 1010), 14, true, STEP)).isNull();
		assertThat(Management.open(history(1010, 1025, 1012), 14, true, STEP)).isNull();
		// Never a whole R up: none either. And no average range before the fourteenth candle.
		assertThat(Management.open(history(1004, 1006, 1007, 1006), 14, true, STEP)).isNull();
		assertThat(Management.open(history(1004, 1010, 1009), 13, true, STEP)).isNull();

		// A short, mirrored.
		Trade s = Management.open(history(996, 990, 991), 14, false, STEP);
		assertThat(s.stop()).isEqualTo(1008);
		assertThat(s.target()).isEqualTo(976);
		assertThat(s.openR()).isEqualTo(1.13);
	}

	@Test
	@DisplayName("straight to the target: hold and breakeven make 3R alike — a tie, both paid most — half less")
	void target() {
		Outcome o = Management.resolve(LONG, future(bar(18, 1010, 1018, 1008, 1016), bar(19, 1016, 1026, 1014, 1024)), NO_MINUTES);
		assertThat(exit(o, Choice.HOLD).r()).isEqualTo(3.0);
		assertThat(exit(o, Choice.HOLD).candles()).isEqualTo(2);
		assertThat(exit(o, Choice.HOLD).how()).isEqualTo("TARGET");
		assertThat(exit(o, Choice.BREAKEVEN).r()).isEqualTo(3.0);
		// Half at the next open, 1,010 (+1.25R), half at the target: +2.13R.
		assertThat(exit(o, Choice.HALF).r()).isEqualTo(2.13);
		assertThat(o.best()).containsExactly(Choice.HOLD, Choice.BREAKEVEN);
		assertThat(o.decidedAt()).isEqualTo(T0.plusSeconds(STEP * 19));
	}

	@Test
	@DisplayName("back to the entry, then the target: breakeven is out at 0R and hold makes 3R")
	void shakeout() {
		Outcome o = Management.resolve(LONG, future(bar(18, 1008, 1009, 999, 1001), bar(19, 1001, 1025, 1001, 1024)), NO_MINUTES);
		assertThat(exit(o, Choice.BREAKEVEN).r()).isEqualTo(0.0);
		assertThat(exit(o, Choice.BREAKEVEN).price()).isEqualTo(1000);
		assertThat(exit(o, Choice.BREAKEVEN).candles()).isEqualTo(1);
		assertThat(exit(o, Choice.HOLD).r()).isEqualTo(3.0);
		assertThat(o.best()).containsExactly(Choice.HOLD);
		// Decided by the last of them to finish.
		assertThat(o.decidedAt()).isEqualTo(T0.plusSeconds(STEP * 19));
	}

	@Test
	@DisplayName("down to the stop: hold loses 1R, breakeven 0R, and half off at +1.25R nets +0.13R — the most")
	void stop() {
		Outcome o = Management.resolve(LONG, future(bar(18, 1010, 1011, 1000, 1001), bar(19, 1001, 1002, 990, 991)), NO_MINUTES);
		assertThat(exit(o, Choice.HOLD).r()).isEqualTo(-1.0);
		assertThat(exit(o, Choice.BREAKEVEN).r()).isEqualTo(0.0);
		assertThat(exit(o, Choice.HALF).r()).isEqualTo(0.13);
		assertThat(o.best()).containsExactly(Choice.HALF);
	}

	@Test
	@DisplayName("a gap through the stop fills at the open, worse than the stop; through the target, better")
	void gaps() {
		Outcome down = Management.resolve(LONG, future(bar(18, 980, 984, 978, 982)), NO_MINUTES);
		assertThat(exit(down, Choice.HOLD).how()).isEqualTo("STOP_GAP");
		assertThat(exit(down, Choice.HOLD).price()).isEqualTo(980);
		assertThat(exit(down, Choice.HOLD).r()).isEqualTo(-2.5);
		// Half off at that same open, the rest out there too.
		assertThat(exit(down, Choice.HALF).r()).isEqualTo(-2.5);
		Outcome up = Management.resolve(LONG, future(bar(18, 1030, 1032, 1028, 1031)), NO_MINUTES);
		assertThat(exit(up, Choice.HOLD).how()).isEqualTo("TARGET_GAP");
		assertThat(exit(up, Choice.HOLD).r()).isEqualTo(3.75);
	}

	@Test
	@DisplayName("one candle reaching both levels is read on its minutes; when a minute reaches both, it is never asked")
	void minutes() {
		Bar wide = bar(18, 1008, 1026, 990, 1000);
		// The minutes: down to the stop first.
		Function<Bar, List<Bar>> stopFirst = b -> List.of(new Bar(b.openTime(), 1008, 1009, 990, 991, 1),
			new Bar(b.openTime().plusSeconds(60), 991, 1026, 991, 1020, 1));
		Outcome o = Management.resolve(LONG, future(wide), stopFirst);
		assertThat(exit(o, Choice.HOLD).how()).isEqualTo("STOP");
		assertThat(exit(o, Choice.HOLD).candles()).isEqualTo(1);
		Function<Bar, List<Bar>> oneMinute = b -> List.of(new Bar(b.openTime(), 1008, 1026, 990, 1000, 1));
		assertThat(Management.resolve(LONG, future(wide), oneMinute)).isNull();
		// No minutes stored: it cannot be told either.
		assertThat(Management.resolve(LONG, future(wide), NO_MINUTES)).isNull();
	}

	@Test
	@DisplayName("neither level in a hundred candles: out at the last close; and too short a future is never asked")
	void horizon() {
		Outcome o = Management.resolve(LONG, future(), NO_MINUTES);
		assertThat(exit(o, Choice.HOLD).how()).isEqualTo("HORIZON");
		assertThat(exit(o, Choice.HOLD).candles()).isEqualTo(100);
		assertThat(exit(o, Choice.HOLD).r()).isEqualTo(1.0);
		assertThat(Management.resolve(LONG, future().subList(0, 99), NO_MINUTES)).isNull();
	}

	@Test
	@DisplayName("the story in prices and R, and what one trade proves")
	void explain() {
		Outcome o = Management.resolve(LONG, future(bar(18, 1008, 1009, 999, 1001), bar(19, 1001, 1025, 1001, 1024)), NO_MINUTES);
		List<String> lines = Management.explain(LONG, o, 1008, p -> Long.toString(p));
		assertThat(lines).containsExactly(
			"When the chart stopped, the long stood at +1.13R: entry 1000, stop 992, target 1024.",
			"Hold: the target filled at 1024, 2 candles later — +3.00R.",
			"Stop to breakeven: out at the entry, 1000, the next candle — 0.00R.",
			"Half off at the next open, 1008 (+1.00R on that half), the rest held: +2.00R in all.",
			"One trade shows which choice paid here, not which pays: managing a trade is a policy, and only many of these "
				+ "show what each one does on real NQ. No slippage or commission.");
		assertThat(Management.label(Choice.HALF)).isEqualTo("Half off — close half at the next open, hold the rest");
	}

	@Test
	@DisplayName("paid most: any of a tie counts, nothing else does; and the drill practises ICT-82")
	void paidMost() {
		assertThat(Management.paidMost("HOLD,BREAKEVEN", "BREAKEVEN")).isTrue();
		assertThat(Management.paidMost("HOLD,BREAKEVEN", "HOLD")).isTrue();
		assertThat(Management.paidMost("HOLD,BREAKEVEN", "HALF")).isFalse();
		assertThat(Management.paidMost("HALF", "HAL")).isFalse();
		assertThat(Management.paidMost("HALF", null)).isFalse();
		assertThat(DrillAttempts.drills("ICT-82")).isTrue();
		assertThat(DrillAttempts.drills("ICT-83")).isTrue();
		assertThat(DrillAttempts.drills("ICT-29")).isFalse();
	}
}
