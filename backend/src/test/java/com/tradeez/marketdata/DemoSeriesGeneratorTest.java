package com.tradeez.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the demo generator. No Spring context and no database — this is pure arithmetic,
 * and a test that boots a container to check it would be slower without being stronger.
 *
 * <p>The determinism tests are the important ones. DEC-012 requires a practice result to be
 * re-derivable from its stored seed and generator version, and that guarantee is only worth
 * anything if it is actually enforced.
 */
class DemoSeriesGeneratorTest {

	private final DemoSeriesGenerator generator = new DemoSeriesGenerator();

	/** A Wednesday, 14:00 UTC — comfortably inside a trading session. */
	private static final Instant MIDWEEK = Instant.parse("2024-03-13T14:00:00Z");

	private List<Bar> generate(long seed, Instant from, int minutes) {
		return generator.generateSeconds(seed, from, from.plusSeconds(minutes * 60L));
	}

	@Nested
	@DisplayName("determinism (DEC-012)")
	class Determinism {

		@Test
		@DisplayName("the same seed and range produce an identical series")
		void sameSeedSameOutput() {
			assertThat(generate(42L, MIDWEEK, 30))
				.isEqualTo(generate(42L, MIDWEEK, 30));
		}

		@Test
		@DisplayName("different seeds produce genuinely different markets")
		void differentSeedsDiffer() {
			List<Bar> a = generate(1L, MIDWEEK, 30);
			List<Bar> b = generate(2L, MIDWEEK, 30);

			assertThat(a).hasSameSizeAs(b).isNotEqualTo(b);

			// Not merely offset by a constant — a different seed must mean different structure, or
			// every practice session would feel like the same market at a different price.
			long firstDelta = a.get(0).closeTicks() - b.get(0).closeTicks();
			boolean allShiftedEqually = true;
			for (int i = 0; i < a.size(); i++) {
				if (a.get(i).closeTicks() - b.get(i).closeTicks() != firstDelta) {
					allShiftedEqually = false;
					break;
				}
			}
			assertThat(allShiftedEqually)
				.as("seeds must change the shape of the series, not just its level")
				.isFalse();
		}

		@Test
		@DisplayName("a sub-range matches the same window of a larger request")
		void subRangeIsStable() {
			List<Bar> wide = generate(7L, MIDWEEK, 20);
			List<Bar> narrow = generator.generateSeconds(
				7L, MIDWEEK.plusSeconds(600), MIDWEEK.plusSeconds(900));

			// This is why each second is seeded from its absolute timestamp rather than from a
			// running state: asking for 14:10-14:15 must not depend on whether 14:00 was requested
			// first. Without it, a replay resumed after a reconnect would diverge from the original.
			assertThat(narrow).isEqualTo(wide.subList(600, 900));
		}
	}

	@Nested
	@DisplayName("bar integrity")
	class Integrity {

		@Test
		@DisplayName("high is never below low, and open/close sit inside the range")
		void ohlcRelationshipsHold() {
			// The Bar constructor enforces this, so generating a large sample is the assertion:
			// any violation throws rather than producing a malformed bar.
			List<Bar> bars = generate(99L, MIDWEEK, 120);

			assertThat(bars).isNotEmpty().allSatisfy(bar -> {
				assertThat(bar.highTicks()).isGreaterThanOrEqualTo(bar.lowTicks());
				assertThat(bar.openTicks()).isBetween(bar.lowTicks(), bar.highTicks());
				assertThat(bar.closeTicks()).isBetween(bar.lowTicks(), bar.highTicks());
			});
		}

		@Test
		@DisplayName("prices are whole ticks — no fractional prices can exist")
		void pricesAreIntegers() {
			// INVARIANT 3 is structural here: the type is long, so an off-grid price is not
			// representable. This test documents that rather than discovering it.
			assertThat(generate(5L, MIDWEEK, 10))
				.allSatisfy(bar -> assertThat(bar.openTicks()).isNotNull());
		}

		@Test
		@DisplayName("volume is never negative")
		void volumeIsNonNegative() {
			assertThat(generate(11L, MIDWEEK, 60))
				.allSatisfy(bar -> assertThat(bar.volume()).isPositive());
		}

		@Test
		@DisplayName("bars are strictly ordered in time with no duplicates")
		void barsAreOrdered() {
			List<Bar> bars = generate(3L, MIDWEEK, 60);
			for (int i = 1; i < bars.size(); i++) {
				assertThat(bars.get(i).openTime())
					.isAfter(bars.get(i - 1).openTime());
			}
		}
	}

	@Nested
	@DisplayName("sessions")
	class Sessions {

		@Test
		@DisplayName("no bars are produced while the market is closed")
		void closedMarketProducesNothing() {
			// Saturday.
			Instant saturday = Instant.parse("2024-03-16T12:00:00Z");
			assertThat(generator.generateSeconds(1L, saturday, saturday.plusSeconds(3600)))
				.isEmpty();
		}

		@Test
		@DisplayName("a weekday session produces a bar per second")
		void openMarketProducesEverySecond() {
			assertThat(generate(1L, MIDWEEK, 5)).hasSize(300);
		}

		@Test
		@DisplayName("the maintenance break is a real gap in the series")
		void maintenanceBreakIsAGap() {
			// 16:00-17:00 Chicago on a Wednesday. Spans the break, so the output must be shorter
			// than the wall-clock span — a replay has to cope with time passing and no bars
			// arriving.
			Instant beforeBreak = ZonedDateTime.of(2024, 3, 13, 15, 30, 0, 0,
				SessionCalendar.EXCHANGE_ZONE).toInstant();

			List<Bar> bars = generator.generateSeconds(
				1L, beforeBreak, beforeBreak.plusSeconds(2 * 3600));

			assertThat(bars).isNotEmpty();
			assertThat(bars.size())
				.as("the 60-minute maintenance break must be absent from the series")
				.isLessThan(2 * 3600);
		}

		@Test
		@DisplayName("price gaps across a session boundary")
		void priceGapsAcrossSessions() {
			// The first bar after a break opens away from where the previous session closed.
			// Deliberate: DEC-008's fill engine must handle a stop being jumped, not just touched,
			// and it can only be tested if the data contains gaps.
			Instant fridayClose = ZonedDateTime.of(2024, 3, 15, 15, 59, 0, 0,
				SessionCalendar.EXCHANGE_ZONE).toInstant();
			Instant sundayOpen = ZonedDateTime.of(2024, 3, 17, 17, 0, 0, 0,
				SessionCalendar.EXCHANGE_ZONE).toInstant();

			Bar lastOfWeek = generator.generateSeconds(1L, fridayClose, fridayClose.plusSeconds(60))
				.getLast();
			Bar firstOfNextWeek = generator
				.generateSeconds(1L, sundayOpen, sundayOpen.plusSeconds(60)).getFirst();

			assertThat(firstOfNextWeek.openTicks())
				.as("a new session should not open exactly where the last one closed")
				.isNotEqualTo(lastOfWeek.closeTicks());
		}
	}

	@Nested
	@DisplayName("price action shape")
	class Shape {

		@Test
		@DisplayName("volatility regimes genuinely differ from one another")
		void volatilityRegimesExist() {
			// DATA_STRATEGY.md requires quiet and violent conditions to both exist; a market with one
			// uniform texture teaches nothing about regime.
			//
			// An earlier version of this test compared the 99th percentile against the median across a
			// single day. That was a bad test: regimes change every three hours, so one day samples
			// only a handful of them and the violent regime frequently never appeared. The generator
			// was fine and the test failed anyway.
			//
			// Comparing per-block medians over a full week measures the thing the requirement actually
			// cares about — that different stretches of the market feel different.
			Instant start = Instant.parse("2024-03-04T00:00:00Z"); // Monday
			List<Bar> week = generator.generateSeconds(21L, start, start.plusSeconds(5 * 86_400L));

			Map<Long, List<Long>> rangesByBlock = week.stream().collect(Collectors.groupingBy(
				bar -> Math.floorDiv(bar.openTime().getEpochSecond(), 3 * 3600L),
				Collectors.mapping(Bar::rangeTicks, Collectors.toList())));

			List<Long> blockMedians = rangesByBlock.values().stream()
				.filter(ranges -> ranges.size() > 100)
				.map(ranges -> ranges.stream().sorted().toList().get(ranges.size() / 2))
				.sorted()
				.toList();

			assertThat(blockMedians)
				.as("a trading week should contain several regime blocks")
				.hasSizeGreaterThan(10);

			assertThat(blockMedians.getLast())
				.as("the most active stretch should be several times wider than the quietest")
				.isGreaterThan(blockMedians.getFirst() * 3);
		}

		@Test
		@DisplayName("the series both rises and falls")
		void priceMovesInBothDirections() {
			List<Bar> bars = generate(13L, MIDWEEK, 240);
			assertThat(bars.stream().anyMatch(Bar::isUp)).isTrue();
			assertThat(bars.stream().anyMatch(b -> !b.isUp() && b.bodyTicks() > 0)).isTrue();
		}
	}
}
