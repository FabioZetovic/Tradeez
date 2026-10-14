package com.tradeez.clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tradeez.clock.ClockLayer.Line;
import com.tradeez.clock.ClockLayer.LineKind;
import com.tradeez.clock.ClockLayer.Window;
import com.tradeez.clock.ClockLayer.WindowKind;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.SessionCalendar;

/**
 * The clock layer's three rules, on hand-made minutes: flat at 100 ticks, with a spike placed exactly
 * where a test needs a high or a low. Dates straddle the US switch to daylight saving on Sunday
 * 9 March 2025, because every window here is New York time (INVARIANT 4).
 */
class ClockLayerBuilderTest {

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

	/** New York wall-clock time as an instant. */
	private static Instant ny(String localDateTime) {
		return LocalDateTime.parse(localDateTime).atZone(NEW_YORK).toInstant();
	}

	/** A flat bar for every minute the market is open in {@code [from, to)}. */
	private static List<Bar> minutes(Instant from, Instant to) {
		List<Bar> bars = new ArrayList<>();
		for (Instant t = from; t.isBefore(to); t = t.plusSeconds(60)) {
			if (SessionCalendar.isOpen(t)) {
				bars.add(new Bar(t, 100, 100, 100, 100, 1));
			}
		}
		return bars;
	}

	/** Replaces the minute at {@code at} with one reaching {@code high} and {@code low}. */
	private static void spike(List<Bar> bars, Instant at, long high, long low) {
		for (int i = 0; i < bars.size(); i++) {
			if (bars.get(i).openTime().equals(at)) {
				bars.set(i, new Bar(at, 100, high, low, 100, 1));
				return;
			}
		}
		throw new IllegalArgumentException("No bar at " + at);
	}

	/** The layer up to {@code to}, handed only the bars before it — as every caller must. */
	private static ClockLayer build(List<Bar> bars, List<Instant> rolls, Instant from, Instant to) {
		return ClockLayerBuilder.build(bars.stream().filter(b -> b.openTime().isBefore(to)).toList(),
			rolls, from, to);
	}

	private static Optional<Line> line(ClockLayer layer, String label, Instant from) {
		return layer.lines().stream()
			.filter(l -> l.label().equals(label) && l.from().equals(from))
			.findFirst();
	}

	private static List<Window> windows(ClockLayer layer, WindowKind kind) {
		return layer.windows().stream().filter(w -> w.kind() == kind).toList();
	}

	// Monday 3 March to Friday 14 March 2025.
	private static final Instant DATA_FROM = ny("2025-03-02T18:00");
	private static final Instant DATA_TO = ny("2025-03-14T17:00");

	@Nested
	@DisplayName("sessions-1.0.0: the windows")
	class Windows {

		@Test
		@DisplayName("sit on New York time on both sides of the daylight-saving switch")
		void newYorkTimeAcrossDst() {
			List<Bar> bars = minutes(DATA_FROM, DATA_TO);
			ClockLayer layer = build(bars, List.of(), ny("2025-03-04T00:00"),
				ny("2025-03-12T00:00"));

			List<Window> killzones = windows(layer, WindowKind.KILLZONE);
			// Tuesday 4 March, EST (UTC−5): London 02:00–05:00 New York is 07:00–10:00 UTC.
			assertThat(killzones).contains(new Window(WindowKind.KILLZONE, "London",
				Instant.parse("2025-03-04T07:00:00Z"), Instant.parse("2025-03-04T10:00:00Z")));
			// Tuesday 11 March, EDT (UTC−4): the same window is an hour earlier in UTC.
			assertThat(killzones).contains(new Window(WindowKind.KILLZONE, "London",
				Instant.parse("2025-03-11T06:00:00Z"), Instant.parse("2025-03-11T09:00:00Z")));
			// Asia belongs to the night before its trading day: Monday 20:00 to midnight.
			assertThat(killzones).contains(new Window(WindowKind.KILLZONE, "Asia",
				ny("2025-03-10T20:00"), ny("2025-03-11T00:00")));
		}

		@Test
		@DisplayName("include the trading day, the Silver Bullet hours and the macros")
		void oneDay() {
			List<Bar> bars = minutes(DATA_FROM, DATA_TO);
			ClockLayer layer = build(bars, List.of(), ny("2025-03-10T18:00"),
				ny("2025-03-11T17:00"));

			assertThat(windows(layer, WindowKind.TRADING_DAY)).containsExactly(new Window(
				WindowKind.TRADING_DAY, "Tue", ny("2025-03-10T18:00"), ny("2025-03-11T17:00")));
			assertThat(windows(layer, WindowKind.KILLZONE)).extracting(Window::label)
				.containsExactly("Asia", "London", "NY AM", "NY PM");
			assertThat(windows(layer, WindowKind.SILVER_BULLET)).extracting(Window::from)
				.containsExactly(ny("2025-03-11T03:00"), ny("2025-03-11T10:00"), ny("2025-03-11T14:00"));
			assertThat(windows(layer, WindowKind.MACRO)).hasSize(8)
				.anySatisfy(w -> assertThat(w.from()).isEqualTo(ny("2025-03-11T08:50")));
		}

		@Test
		@DisplayName("are left out on days with no data, such as a weekend")
		void onlyTradedDays() {
			ClockLayer layer = build(minutes(DATA_FROM, DATA_TO), List.of(),
				ny("2025-03-07T18:00"), ny("2025-03-09T17:00"));
			assertThat(layer.windows()).isEmpty();
		}
	}

	@Nested
	@DisplayName("levels-1.0.0: reference highs and lows")
	class Levels {

		@Test
		@DisplayName("PDH and PDL span the next trading day, from the previous one's real extremes")
		void previousDay() {
			List<Bar> bars = minutes(DATA_FROM, DATA_TO);
			spike(bars, ny("2025-03-10T10:00"), 150, 100);
			spike(bars, ny("2025-03-09T21:00"), 100, 40); // Sunday evening is Monday's session
			ClockLayer layer = build(bars, List.of(), ny("2025-03-10T18:00"),
				ny("2025-03-11T17:00"));

			Instant tuesday = ny("2025-03-10T18:00");
			assertThat(line(layer, "PDH", tuesday)).get()
				.isEqualTo(new Line(LineKind.PREVIOUS_DAY, "PDH", tuesday, ny("2025-03-11T17:00"), 150));
			assertThat(line(layer, "PDL", tuesday)).get().extracting(Line::priceTicks).isEqualTo(40L);
		}

		@Test
		@DisplayName("Monday's previous day is Friday, not the weekend")
		void mondayLooksBackToFriday() {
			List<Bar> bars = minutes(DATA_FROM, DATA_TO);
			spike(bars, ny("2025-03-07T11:00"), 130, 100);
			ClockLayer layer = build(bars, List.of(), ny("2025-03-09T18:00"),
				ny("2025-03-10T17:00"));
			assertThat(line(layer, "PDH", ny("2025-03-09T18:00"))).get()
				.extracting(Line::priceTicks).isEqualTo(130L);
		}

		@Test
		@DisplayName("PWH and PWL span the next week")
		void previousWeek() {
			List<Bar> bars = minutes(DATA_FROM, DATA_TO);
			spike(bars, ny("2025-03-05T09:45"), 170, 100);
			spike(bars, ny("2025-03-07T15:00"), 100, 20);
			ClockLayer layer = build(bars, List.of(), ny("2025-03-11T00:00"),
				ny("2025-03-12T00:00"));

			Instant week = ny("2025-03-09T18:00");
			assertThat(line(layer, "PWH", week)).get()
				.isEqualTo(new Line(LineKind.PREVIOUS_WEEK, "PWH", week, ny("2025-03-14T17:00"), 170));
			assertThat(line(layer, "PWL", week)).get().extracting(Line::priceTicks).isEqualTo(20L);
		}

		@Test
		@DisplayName("a killzone's high and low appear only once it has closed")
		void sessionLevelsWaitForTheClose() {
			List<Bar> bars = minutes(DATA_FROM, DATA_TO);
			spike(bars, ny("2025-03-11T03:15"), 160, 70);

			Instant midLondon = ny("2025-03-11T04:00");
			ClockLayer during = build(bars, List.of(),
				ny("2025-03-10T18:00"), midLondon);
			assertThat(during.lines()).noneMatch(l -> l.label().startsWith("London"));
			// Asia closed at midnight, so its levels are already there.
			assertThat(line(during, "Asia H", ny("2025-03-11T00:00"))).isPresent();

			Instant londonClose = ny("2025-03-11T05:00");
			ClockLayer after = build(bars, List.of(),
				ny("2025-03-10T18:00"), londonClose);
			assertThat(line(after, "London H", londonClose)).get()
				.isEqualTo(new Line(LineKind.SESSION, "London H", londonClose, ny("2025-03-11T17:00"), 160));
			assertThat(line(after, "London L", londonClose)).get()
				.extracting(Line::priceTicks).isEqualTo(70L);
		}

		@Test
		@DisplayName("nothing is measured from a day that was not seen from its start")
		void partialDay() {
			// The data begins at Monday 10:00: Monday's high and low are not Monday's.
			List<Bar> bars = minutes(ny("2025-03-10T10:00"), DATA_TO);
			ClockLayer layer = build(bars, List.of(), ny("2025-03-10T18:00"),
				ny("2025-03-11T17:00"));
			assertThat(layer.lines()).noneMatch(l -> l.kind() == LineKind.PREVIOUS_DAY);
		}
	}

	@Nested
	@DisplayName("opens-1.0.0: opening prices")
	class Opens {

		@Test
		@DisplayName("each is the open of the first minute at or after its time")
		void firstMinuteAtOrAfter() {
			List<Bar> bars = new ArrayList<>(minutes(DATA_FROM, DATA_TO));
			// No trade from midnight to 00:06: the midnight open is the 00:07 minute's open.
			bars.removeIf(b -> !b.openTime().isBefore(ny("2025-03-11T00:00"))
				&& b.openTime().isBefore(ny("2025-03-11T00:07")));
			for (int i = 0; i < bars.size(); i++) {
				Bar b = bars.get(i);
				if (b.openTime().equals(ny("2025-03-11T00:07"))) {
					bars.set(i, new Bar(b.openTime(), 123, 123, 100, 100, 1));
				}
				if (b.openTime().equals(ny("2025-03-11T09:30"))) {
					bars.set(i, new Bar(b.openTime(), 111, 111, 100, 100, 1));
				}
			}
			ClockLayer layer = build(bars, List.of(), ny("2025-03-10T18:00"),
				ny("2025-03-11T17:00"));

			assertThat(line(layer, "Midnight open", ny("2025-03-11T00:00"))).get()
				.extracting(Line::priceTicks).isEqualTo(123L);
			assertThat(line(layer, "09:30 open", ny("2025-03-11T09:30"))).get()
				.isEqualTo(new Line(LineKind.OPEN, "09:30 open", ny("2025-03-11T09:30"),
					ny("2025-03-11T17:00"), 111));
			assertThat(line(layer, "Weekly open", ny("2025-03-09T18:00"))).get()
				.extracting(Line::to).isEqualTo(ny("2025-03-14T17:00"));
		}

		@Test
		@DisplayName("an opening price appears only once its minute has printed")
		void notBeforeItPrints() {
			List<Bar> bars = minutes(DATA_FROM, DATA_TO);
			Instant at = ny("2025-03-11T09:30");
			assertThat(line(build(bars, List.of(), at.minusSeconds(3600), at),
				"09:30 open", at)).isEmpty();
			Instant next = at.plusSeconds(60);
			assertThat(line(build(bars, List.of(), at.minusSeconds(3600), next),
				"09:30 open", at)).isPresent();
		}
	}

	@Nested
	@DisplayName("rolls and lookahead")
	class Guards {

		@Test
		@DisplayName("a level is never drawn across a contract roll")
		void rolls() {
			List<Bar> bars = minutes(DATA_FROM, DATA_TO);
			Instant tuesday = ny("2025-03-10T18:00");
			Instant wednesday = ny("2025-03-11T18:00");
			ClockLayer layer = build(bars, List.of(wednesday), tuesday,
				ny("2025-03-12T17:00"));

			// Tuesday's PDH (Monday, same contract) stands; Wednesday's (Tuesday, old contract) does not.
			assertThat(line(layer, "PDH", tuesday)).isPresent();
			assertThat(line(layer, "PDH", wednesday)).isEmpty();
			// The week's lines stop at the roll.
			assertThat(line(layer, "Weekly open", ny("2025-03-09T18:00"))).get()
				.extracting(Line::to).isEqualTo(wednesday);
			assertThat(line(layer, "PWH", ny("2025-03-09T18:00"))).get()
				.extracting(Line::to).isEqualTo(wednesday);
		}

		@Test
		@DisplayName("refuses a bar at or after the end of the layer: that would be lookahead")
		void refusesTheFuture() {
			Instant to = ny("2025-03-11T10:00");
			List<Bar> bars = minutes(DATA_FROM, to.plusSeconds(60));
			assertThatThrownBy(() -> ClockLayerBuilder.build(bars, List.of(), DATA_FROM, to))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("lookahead");
		}

		@Test
		@DisplayName("says when it could next change: a killzone closing, a window opening, a price printing")
		void nextChange() {
			assertThat(ClockLayerBuilder.nextChange(ny("2025-03-11T04:30"))).isEqualTo(ny("2025-03-11T05:00"));
			assertThat(ClockLayerBuilder.nextChange(ny("2025-03-11T08:30"))).isEqualTo(ny("2025-03-11T08:31"));
			assertThat(ClockLayerBuilder.nextChange(ny("2025-03-11T16:00"))).isEqualTo(ny("2025-03-11T18:01"));
			assertThat(ClockLayerBuilder.nextChange(ny("2025-03-11T23:30"))).isEqualTo(ny("2025-03-12T00:00"));
		}
	}
}
