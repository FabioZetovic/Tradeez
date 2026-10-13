package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.DemoSeriesGenerator;
import com.tradeez.practice.FillEngine.Direction;
import com.tradeez.practice.FillEngine.Fill;
import com.tradeez.practice.FillEngine.FillModel;
import com.tradeez.practice.FillEngine.Outcome;
import com.tradeez.practice.FillEngine.Plan;
import com.tradeez.practice.FillEngine.Resolution;

/**
 * The fill engine, tested by hand and against an independent oracle.
 *
 * <p>DEC-029 asked for a golden-file suite here, and the hand-built cases below are that: each one
 * is a tiny market with a known answer. But hand-built cases only test the situations someone thought
 * of. The oracle test at the bottom runs thousands of random brackets over real generated data and
 * checks every result against a deliberately naive second implementation. When two different
 * implementations agree across that many cases, the agreement means something.
 */
class FillEngineTest {

	private static final FillModel NO_COSTS = new FillModel(0, 0);
	private static final Instant T0 = Instant.parse("2024-03-13T14:00:00Z");

	/** One one-second bar, {@code offset} seconds after T0. */
	private static Bar s(int offset, long open, long high, long low, long close) {
		return new Bar(T0.plusSeconds(offset), open, high, low, close, 1);
	}

	private static Plan longTrade(long stop, long target) {
		return new Plan(Direction.LONG, stop, target, 1);
	}

	private static Plan shortTrade(long stop, long target) {
		return new Plan(Direction.SHORT, stop, target, 1);
	}

	@Nested
	@DisplayName("the basic cases")
	class Basics {

		@Test
		@DisplayName("a long reaching its target")
		void longTarget() {
			Fill f = FillEngine.resolve(longTrade(960, 1080), List.of(
				s(0, 1000, 1010, 995, 1005),
				s(1, 1005, 1080, 1004, 1070)), NO_COSTS);

			assertThat(f.outcome()).isEqualTo(Outcome.TARGET);
			assertThat(f.entryTicks()).isEqualTo(1000);
			assertThat(f.exitTicks()).isEqualTo(1080);
			assertThat(f.pnlTicks()).isEqualTo(80);
			assertThat(f.exitTime()).isEqualTo(T0.plusSeconds(1));
		}

		@Test
		@DisplayName("a long reaching its stop")
		void longStop() {
			Fill f = FillEngine.resolve(longTrade(960, 1080), List.of(
				s(0, 1000, 1002, 990, 992),
				s(1, 992, 993, 960, 965)), NO_COSTS);

			assertThat(f.outcome()).isEqualTo(Outcome.STOP);
			assertThat(f.exitTicks()).isEqualTo(960);
			assertThat(f.pnlTicks()).isEqualTo(-40);
		}

		@Test
		@DisplayName("a short reaching its target below the entry")
		void shortTarget() {
			Fill f = FillEngine.resolve(shortTrade(1040, 920), List.of(
				s(0, 1000, 1005, 990, 995),
				s(1, 995, 996, 920, 925)), NO_COSTS);

			assertThat(f.outcome()).isEqualTo(Outcome.TARGET);
			assertThat(f.pnlTicks()).isEqualTo(80);
		}

		@Test
		@DisplayName("a short reaching its stop above the entry")
		void shortStop() {
			Fill f = FillEngine.resolve(shortTrade(1040, 920), List.of(
				s(0, 1000, 1041, 999, 1035)), NO_COSTS);

			assertThat(f.outcome()).isEqualTo(Outcome.STOP);
			assertThat(f.pnlTicks()).isEqualTo(-40);
		}

		@Test
		@DisplayName("entry is the open of the first bar — a market order")
		void entryIsFirstOpen() {
			Fill f = FillEngine.resolve(longTrade(900, 1200), List.of(
				s(0, 1003, 1004, 1002, 1003)), NO_COSTS);

			assertThat(f.entryTicks()).isEqualTo(1003);
			assertThat(f.entryTime()).isEqualTo(T0);
		}
	}

	@Nested
	@DisplayName("DEC-008: ordering inside a candle")
	class Ordering {

		@Test
		@DisplayName("resolves by the order of the seconds, not the shape of the candle")
		void orderOfSecondsDecides() {
			// These three seconds would aggregate into one candle with a high of 1080 and a low of 960
			// — both levels inside it. From the candle, the outcome is unknowable. From the seconds it
			// is not: the target comes one second before the stop. This is the whole reason the engine
			// walks one-second data.
			Fill f = FillEngine.resolve(longTrade(960, 1080), List.of(
				s(0, 1000, 1010, 995, 1005),
				s(1, 1005, 1080, 1004, 1075),  // target
				s(2, 1075, 1076, 960, 961)),   // stop — too late, the trade is already closed
				NO_COSTS);

			assertThat(f.outcome()).isEqualTo(Outcome.TARGET);
			assertThat(f.exitTime()).isEqualTo(T0.plusSeconds(1));
		}

		@Test
		@DisplayName("the same levels in the opposite order give the opposite result")
		void reverseOrder() {
			Fill f = FillEngine.resolve(longTrade(960, 1080), List.of(
				s(0, 1000, 1010, 995, 1005),
				s(1, 1005, 1006, 960, 962),    // stop
				s(2, 962, 1080, 961, 1079)),   // target — too late
				NO_COSTS);

			assertThat(f.outcome()).isEqualTo(Outcome.STOP);
		}

		@Test
		@DisplayName("both levels inside a single second is AMBIGUOUS, not a guess")
		void bothInOneSecond() {
			// Finer data narrows the problem; it does not remove it. Codex's correction to the original
			// design: at any resolution, a bar that spans both levels has an unknowable order, and the
			// engine must say so.
			Fill f = FillEngine.resolve(longTrade(960, 1080), List.of(
				s(0, 1000, 1010, 995, 1005),
				s(1, 1005, 1080, 960, 1000)), NO_COSTS);

			assertThat(f.outcome()).isEqualTo(Outcome.AMBIGUOUS);
		}

		@Test
		@DisplayName("an ambiguous result records the pessimistic exit, never the flattering one")
		void ambiguousIsPessimistic() {
			// INVARIANT 6: resolving silently in the learner's favour would inflate every score built
			// on top. The recorded number is the worst case, and the outcome says it is an assumption.
			Fill f = FillEngine.resolve(longTrade(960, 1080), List.of(
				s(0, 1000, 1080, 960, 1000)), FillModel.DEFAULT);

			assertThat(f.outcome()).isEqualTo(Outcome.AMBIGUOUS);
			assertThat(f.exitTicks()).isEqualTo(959); // stop, less a tick of slippage
			assertThat(f.pnlTicks()).isNegative();
			// The favourable excursion is still honest: price did reach the target at some point.
			assertThat(f.mfeTicks()).isEqualTo(80);
		}
	}

	@Nested
	@DisplayName("gaps")
	class Gaps {

		@Test
		@DisplayName("a stop jumped over fills at the open, which is worse than the stop")
		void gapThroughStop() {
			Fill f = FillEngine.resolve(longTrade(960, 1080), List.of(
				s(0, 1000, 1001, 999, 1000),
				s(1, 940, 945, 938, 944)), FillModel.DEFAULT);

			assertThat(f.outcome()).isEqualTo(Outcome.STOP);
			assertThat(f.exitTicks()).isEqualTo(939); // 940 open, less a tick
			assertThat(f.gapExit()).isTrue();
		}

		@Test
		@DisplayName("a target jumped over fills at the open, which is better than the target")
		void gapThroughTarget() {
			// A resting limit order that the market opens beyond fills at the open. Being generous
			// here would be wrong in the other direction; this is simply what a limit does.
			Fill f = FillEngine.resolve(longTrade(960, 1080), List.of(
				s(0, 1000, 1001, 999, 1000),
				s(1, 1100, 1105, 1098, 1101)), NO_COSTS);

			assertThat(f.outcome()).isEqualTo(Outcome.TARGET);
			assertThat(f.exitTicks()).isEqualTo(1100);
			assertThat(f.gapExit()).isTrue();
		}

		@Test
		@DisplayName("a missing bar is a quiet stretch, not a closure (fill-1.1.0)")
		void quietStretchIsNotAClosure() {
			// Real data is sparse: a minute with no trades has no bar. fill-1.0.0 read any hole as the
			// session closing and flattened the trade — on real data, early and for no reason. The
			// caller now ends the bars at the session's close, so a hole inside them is just quiet.
			Fill f = FillEngine.resolve(longTrade(900, 1200), List.of(
				s(0, 1000, 1010, 995, 1004),
				s(1, 1004, 1012, 1001, 1008),
				s(300, 1100, 1205, 1090, 1150)), NO_COSTS);  // five quiet minutes later

			assertThat(f.outcome()).isEqualTo(Outcome.TARGET);
			assertThat(f.exitTime()).isEqualTo(T0.plusSeconds(300));
		}

		@Test
		@DisplayName("records the resolution it walked, so a one-minute result says so")
		void recordsResolution() {
			List<Bar> minutes = List.of(
				s(0, 1000, 1010, 995, 1004),
				s(60, 1004, 1205, 890, 1100));  // one minute spans both levels

			Fill f = FillEngine.resolve(longTrade(900, 1200), minutes, NO_COSTS, Resolution.M1);

			assertThat(f.resolution()).isEqualTo(Resolution.M1);
			assertThat(f.outcome()).isEqualTo(Outcome.AMBIGUOUS);
			assertThat(FillEngine.resolve(longTrade(900, 1200), minutes, NO_COSTS).resolution())
				.isEqualTo(Resolution.S1);
		}

		@Test
		@DisplayName("running out of data flattens at the last close")
		void dataEnds() {
			Fill f = FillEngine.resolve(longTrade(900, 1200), List.of(
				s(0, 1000, 1010, 995, 1004),
				s(1, 1004, 1012, 1001, 1007)), NO_COSTS);

			assertThat(f.outcome()).isEqualTo(Outcome.TIME_EXIT);
			assertThat(f.exitTicks()).isEqualTo(1007);
		}
	}

	@Nested
	@DisplayName("excursions")
	class Excursions {

		@Test
		@DisplayName("MAE and MFE record how far the trade travelled before it closed")
		void excursionsAccumulate() {
			Fill f = FillEngine.resolve(longTrade(900, 1080), List.of(
				s(0, 1000, 1002, 990, 995),   // 10 against
				s(1, 995, 1030, 994, 1025),   // 30 for
				s(2, 1025, 1080, 1020, 1079)), NO_COSTS);

			assertThat(f.maeTicks()).isEqualTo(10);
			assertThat(f.mfeTicks()).isEqualTo(80);
		}

		@Test
		@DisplayName("on the exit bar, only the exit price counts")
		void exitBarCountsOnlyTheExit() {
			// The stopping bar's low is 900, far past the stop. But the position closed at the stop:
			// whatever the bar did beyond that happened to a position that no longer existed. Counting
			// it would tell the learner their stop was exceeded by 60 ticks when it was not.
			Fill f = FillEngine.resolve(longTrade(960, 1080), List.of(
				s(0, 1000, 1001, 999, 1000),
				s(1, 1000, 1001, 900, 905)), NO_COSTS);

			assertThat(f.maeTicks()).isEqualTo(40);
		}

		@Test
		@DisplayName("excursions are never negative")
		void neverNegative() {
			Fill f = FillEngine.resolve(longTrade(900, 1001), List.of(
				s(0, 1000, 1001, 1000, 1001)), NO_COSTS);

			assertThat(f.maeTicks()).isZero();
			assertThat(f.mfeTicks()).isEqualTo(1);
		}
	}

	@Nested
	@DisplayName("costs")
	class Costs {

		@Test
		@DisplayName("stops slip against the trader; targets do not")
		void slippageOnlyOnStops() {
			Fill stopped = FillEngine.resolve(longTrade(960, 1080),
				List.of(s(0, 1000, 1000, 960, 961)), new FillModel(2, 0));
			Fill targeted = FillEngine.resolve(longTrade(960, 1080),
				List.of(s(0, 1000, 1080, 1000, 1080)), new FillModel(2, 0));

			assertThat(stopped.exitTicks()).isEqualTo(958);
			assertThat(targeted.exitTicks()).isEqualTo(1080);
		}

		@Test
		@DisplayName("net P&L is exact integer cents, commission included")
		void netPnl() {
			// 80 ticks x $5 x 2 contracts = $800, less $2.50 x 2 = $795. No floating point anywhere.
			Plan twoLots = new Plan(Direction.LONG, 960, 1080, 2);
			Fill f = FillEngine.resolve(twoLots,
				List.of(s(0, 1000, 1080, 1000, 1080)), FillModel.DEFAULT);

			assertThat(f.netPnlCents(500, 2, FillModel.DEFAULT)).isEqualTo(79_500);
		}
	}

	@Nested
	@DisplayName("guards")
	class Guards {

		@Test
		@DisplayName("a trade cannot be entered into a closed market")
		void emptySeriesRejected() {
			assertThatThrownBy(() -> FillEngine.resolve(longTrade(900, 1100), List.of(), NO_COSTS))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("a trade needs at least one contract")
		void contractsMustBePositive() {
			assertThatThrownBy(() -> new Plan(Direction.LONG, 900, 1100, 0))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("the same inputs always give the same result (DEC-012)")
		void deterministic() {
			List<Bar> bars = List.of(s(0, 1000, 1010, 990, 1005), s(1, 1005, 1080, 1000, 1078));
			assertThat(FillEngine.resolve(longTrade(960, 1080), bars, FillModel.DEFAULT))
				.isEqualTo(FillEngine.resolve(longTrade(960, 1080), bars, FillModel.DEFAULT));
		}
	}

	@Nested
	@DisplayName("against an independent oracle, on real generated data")
	class Oracle {

		/**
		 * A second implementation, written as naively as possible: scan until the first second that
		 * touches either level, and report which. No excursions, no slippage, no gap handling. Its only
		 * job is to be obviously right about <i>which second</i> and <i>which outcome</i>.
		 */
		private record Expected(Outcome outcome, Instant exitTime) {
		}

		private Expected naive(Plan plan, List<Bar> seconds) {
			boolean isLong = plan.direction() == Direction.LONG;
			for (int i = 0; i < seconds.size(); i++) {
				Bar b = seconds.get(i);
				boolean stop = isLong ? b.lowTicks() <= plan.stopTicks()
					: b.highTicks() >= plan.stopTicks();
				boolean target = isLong ? b.highTicks() >= plan.targetTicks()
					: b.lowTicks() <= plan.targetTicks();
				if (stop && target) {
					// The open can settle it: if the bar opened through a level, that level came first.
					boolean openStop = isLong ? b.openTicks() <= plan.stopTicks()
						: b.openTicks() >= plan.stopTicks();
					boolean openTarget = isLong ? b.openTicks() >= plan.targetTicks()
						: b.openTicks() <= plan.targetTicks();
					if (openStop) {
						return new Expected(Outcome.STOP, b.openTime());
					}
					if (openTarget) {
						return new Expected(Outcome.TARGET, b.openTime());
					}
					return new Expected(Outcome.AMBIGUOUS, b.openTime());
				}
				if (stop) {
					return new Expected(Outcome.STOP, b.openTime());
				}
				if (target) {
					return new Expected(Outcome.TARGET, b.openTime());
				}
			}
			return new Expected(Outcome.TIME_EXIT, seconds.getLast().openTime());
		}

		private static List<Bar> upToFirstClosure(List<Bar> generated) {
			List<Bar> out = new ArrayList<>();
			for (Bar b : generated) {
				if (!out.isEmpty() && b.openTime().getEpochSecond()
						- out.getLast().openTime().getEpochSecond() > 1) {
					break;
				}
				out.add(b);
			}
			return out;
		}

		@Test
		@DisplayName("agrees with a naive scan across 2,000 random brackets")
		void agreesWithNaiveScan() {
			DemoSeriesGenerator generator = new DemoSeriesGenerator();
			Random random = new Random(20260928L);
			int ambiguous = 0;
			int decided = 0;

			for (int trial = 0; trial < 2_000; trial++) {
				// A random weekday moment and a random bracket, 4 to 120 ticks each side.
				Instant start = Instant.parse("2024-03-11T00:00:00Z")
					.plusSeconds(random.nextInt(4 * 86_400));
				// Ends at the first closure, as the practice service ends its window at the session's
				// close: the engine is given one session, never two (fill-1.1.0).
				List<Bar> seconds = upToFirstClosure(
					generator.generateSeconds(random.nextLong(), start, start.plusSeconds(3_600)));
				if (seconds.isEmpty()) {
					continue;
				}

				long entry = seconds.getFirst().openTicks();
				boolean isLong = random.nextBoolean();
				long risk = 4 + random.nextInt(117);
				long reward = 4 + random.nextInt(117);
				Plan plan = isLong
					? new Plan(Direction.LONG, entry - risk, entry + reward, 1)
					: new Plan(Direction.SHORT, entry + risk, entry - reward, 1);

				Fill actual = FillEngine.resolve(plan, seconds, NO_COSTS);
				Expected expected = naive(plan, seconds);

				assertThat(actual.outcome())
					.as("trial %d: %s risk %d reward %d at %s", trial, plan.direction(), risk,
						reward, start)
					.isEqualTo(expected.outcome());
				assertThat(actual.exitTime()).as("trial %d exit time", trial)
					.isEqualTo(expected.exitTime());

				if (actual.outcome() == Outcome.AMBIGUOUS) {
					ambiguous++;
				} else if (actual.outcome() != Outcome.TIME_EXIT) {
					decided++;
				}
			}

			// Not an assertion about the engine — a guard on the test itself. If nothing was ever
			// decided, the oracle would be agreeing about nothing (the lesson of BUG-012 and BUG-020:
			// a test must be able to produce the situation it claims to test).
			assertThat(decided).as("brackets that reached a level").isGreaterThan(500);
			// Recorded rather than asserted: how often one-second data still cannot decide.
			System.out.printf("oracle: %d decided, %d ambiguous at one-second resolution%n",
				decided, ambiguous);
		}
	}
}
