package com.tradeez.clock;

import static java.time.temporal.TemporalAdjusters.previousOrSame;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.TreeSet;

import com.tradeez.clock.ClockLayer.Line;
import com.tradeez.clock.ClockLayer.LineKind;
import com.tradeez.clock.ClockLayer.Window;
import com.tradeez.clock.ClockLayer.WindowKind;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Killzone;
import com.tradeez.marketdata.SessionCalendar;

/**
 * Builds the New York clock layer from one-minute bars. Three versioned rules (DEC-026), each
 * written out in {@code ICT_CONCEPTS.md} and recorded in DEC-047:
 *
 * <ul>
 * <li><b>{@value #SESSIONS}</b> (ICT-51 to ICT-54) — the index-futures killzones of DEC-043, the
 * three Silver Bullet hours, and the commonly cited macro times.</li>
 * <li><b>{@value #OPENS}</b> (ICT-50) — the midnight, 08:30, 09:30 and weekly opening prices: each
 * the open of the first one-minute bar at or after its time.</li>
 * <li><b>{@value #LEVELS}</b> (ICT-11) — the previous day's and week's high and low, and each
 * killzone's high and low once it has closed. The day is the CME trading day, 18:00 to 17:00 New
 * York ({@code dayBoundary = CME}).</li>
 * </ul>
 *
 * <h2>No lookahead (DEC-009)</h2>
 * The builder is handed every bar before an instant {@code to} and refuses any bar at or after it,
 * so nothing it returns can depend on a price the learner has not seen. A level appears only once
 * the period it measures is over; an opening price only once its bar has printed. A window's end, or
 * a line's, may lie past {@code to}: those are clock times, the same rule on every day, not prices.
 *
 * <h2>Rolls</h2>
 * Real NQ is stored as the front contract with visible rolls, and the next contract trades a few
 * hundred points away. A level measured on one contract is not drawn over another: it is left out
 * when a roll falls between its source and its span, and cut short when one falls inside its span.
 */
public final class ClockLayerBuilder {

	public static final String SESSIONS = "sessions-1.0.0";
	public static final String OPENS = "opens-1.0.0";
	public static final String LEVELS = "levels-1.0.0";

	/** How far before a range its sources can lie: the whole previous week, from any day of this one. */
	public static final Duration LOOKBACK = Duration.ofDays(14);

	static final ZoneId NEW_YORK = Killzone.NEW_YORK;

	/** {@code dayBoundary = CME}: the trading day runs 18:00 to 17:00 New York. */
	static final LocalTime DAY_START = LocalTime.of(18, 0);
	static final LocalTime DAY_END = LocalTime.of(17, 0);

	/**
	 * A day whose first bar comes later than this after its start was not seen from the beginning —
	 * cut off by the start of the data, or of what was read — so no level is measured from it. A week
	 * is seen from its start when its first bar opened a day that was (see {@code Week}).
	 */
	private static final Duration DAY_SEEN_FROM_START = Duration.ofHours(1);

	/**
	 * BUG-044 — the same test for the smaller pieces. NQ trades nearly every minute, so a killzone whose
	 * first bar comes this late was cut off by the data; and an opening price whose first minute comes
	 * this late is the open of whatever came after a gap, not of the time it is named after. A few
	 * quiet minutes are allowed for.
	 */
	private static final Duration KILLZONE_SEEN_FROM_START = Duration.ofMinutes(15);
	private static final Duration OPEN_WITHIN = Duration.ofMinutes(15);

	/** The previous day with data, when it is further back than this, is a hole in the data. */
	private static final long MAX_DAYS_TO_PREVIOUS = 4;

	/** One window of {@value #SESSIONS}, in New York time. An evening start belongs to the night before. */
	record Rule(WindowKind kind, String label, LocalTime start, LocalTime end) {
	}

	static final List<Rule> WINDOWS = List.of(
		killzone(Killzone.ASIA), killzone(Killzone.LONDON), killzone(Killzone.NEW_YORK_AM),
		killzone(Killzone.NEW_YORK_PM),
		new Rule(WindowKind.SILVER_BULLET, "Silver Bullet", time(3, 0), time(4, 0)),
		new Rule(WindowKind.SILVER_BULLET, "Silver Bullet", time(10, 0), time(11, 0)),
		new Rule(WindowKind.SILVER_BULLET, "Silver Bullet", time(14, 0), time(15, 0)),
		// ICT-54: the schedule most often cited. ICT_CONCEPTS.md flags it for checking against his
		// own lectures; it is versioned with the rest so a correction is a new version, not an edit.
		macro(2, 33, 3, 0), macro(4, 3, 4, 30), macro(8, 50, 9, 10), macro(9, 50, 10, 10),
		macro(10, 50, 11, 10), macro(11, 50, 12, 10), macro(13, 10, 13, 40), macro(15, 15, 15, 45));

	/** {@value #OPENS}: the daily opening prices. The weekly open is the first bar of the week. */
	enum Open {
		MIDNIGHT("Midnight open", time(0, 0)),
		EIGHT_THIRTY("08:30 open", time(8, 30)),
		NINE_THIRTY("09:30 open", time(9, 30));

		final String label;
		final LocalTime time;

		Open(String label, LocalTime time) {
			this.label = label;
			this.time = time;
		}
	}

	private static final Killzone[] KILLZONES = Killzone.values();

	/**
	 * Times of day at which the layer can change: the minute after a window opens or an opening price
	 * prints (a cursor is past them only then), and the moment a killzone closes.
	 */
	private static final TreeSet<LocalTime> CHANGES = new TreeSet<>();

	static {
		CHANGES.add(DAY_START.plusMinutes(1));
		WINDOWS.forEach(rule -> CHANGES.add(rule.start().plusMinutes(1)));
		for (Killzone killzone : KILLZONES) {
			CHANGES.add(killzone.end());
		}
		for (Open open : Open.values()) {
			CHANGES.add(open.time.plusMinutes(1));
		}
	}

	private ClockLayerBuilder() {
	}

	/**
	 * @param bars  one-minute bars in time order, every one before {@code to}. To measure the first
	 *              days of the range they must reach back {@link #LOOKBACK} before {@code from}.
	 * @param rolls instants at which the stored contract changes, as {@code MarketDataService}
	 *              reports them; empty for a simulated market
	 * @return the windows and lines that touch {@code [from, to)}
	 */
	public static ClockLayer build(List<Bar> bars, List<Instant> rolls, Instant from, Instant to) {
		TreeMap<LocalDate, Day> days = new TreeMap<>();
		TreeMap<LocalDate, Week> weeks = new TreeMap<>();
		Day day = null;
		for (Bar bar : bars) {
			Instant t = bar.openTime();
			if (!t.isBefore(to)) {
				throw new IllegalArgumentException("The clock layer up to " + to
					+ " was handed a bar from " + t + ": that would be lookahead.");
			}
			if (day == null || t.isBefore(day.start) || !t.isBefore(day.end)) {
				LocalDate date = SessionCalendar.tradingDate(t);
				day = days.computeIfAbsent(date, Day::new);
			}
			day.add(bar);
			weeks.computeIfAbsent(day.date.with(previousOrSame(DayOfWeek.MONDAY)), Week::new).add(bar, day);
		}

		List<Window> windows = new ArrayList<>();
		List<Line> lines = new ArrayList<>();
		Lines out = new Lines(lines, rolls, from, to);

		Day previous = null;
		for (Day d : days.values()) {
			if (d.start.isBefore(to) && d.end.isAfter(from)) {
				windows.add(new Window(WindowKind.TRADING_DAY,
					d.date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH), d.start, d.end));
				for (Rule rule : WINDOWS) {
					Instant[] span = span(d.date, rule.start(), rule.end());
					if (span[0].isBefore(to) && span[1].isAfter(from)) {
						windows.add(new Window(rule.kind(), rule.label(), span[0], span[1]));
					}
				}
				for (Open open : Open.values()) {
					Long price = d.opens[open.ordinal()];
					if (price != null) {
						Instant at = d.openAt[open.ordinal()];
						out.add(LineKind.OPEN, open.label, at, at, d.end, price);
					}
				}
				if (previous != null && previous.seenFromStart()
						&& ChronoUnit.DAYS.between(previous.date, d.date) <= MAX_DAYS_TO_PREVIOUS) {
					out.add(LineKind.PREVIOUS_DAY, "PDH", previous.start, d.start, d.end, previous.high);
					out.add(LineKind.PREVIOUS_DAY, "PDL", previous.start, d.start, d.end, previous.low);
				}
				for (int k = 0; k < KILLZONES.length; k++) {
					// Only once the killzone is over: until then its high and low are still forming.
					if (d.killzoneSeen(k) && !d.killzoneSpan[k][1].isAfter(to)) {
						String name = label(KILLZONES[k]);
						Instant start = d.killzoneSpan[k][0];
						Instant end = d.killzoneSpan[k][1];
						out.add(LineKind.SESSION, name + " H", start, end, d.end, d.killzoneHigh[k]);
						out.add(LineKind.SESSION, name + " L", start, end, d.end, d.killzoneLow[k]);
					}
				}
			}
			previous = d;
		}

		Week previousWeek = null;
		for (Week w : weeks.values()) {
			if (w.start.isBefore(to) && w.end.isAfter(from)) {
				// The week's first minute is its open only if the data saw the week begin (BUG-044).
				if (w.seenFromStart) {
					out.add(LineKind.OPEN, "Weekly open", w.start, w.start, w.end, w.open);
				}
				if (previousWeek != null && previousWeek.seenFromStart
						&& ChronoUnit.DAYS.between(previousWeek.monday, w.monday) == 7) {
					out.add(LineKind.PREVIOUS_WEEK, "PWH", previousWeek.start, w.start, w.end,
						previousWeek.high);
					out.add(LineKind.PREVIOUS_WEEK, "PWL", previousWeek.start, w.start, w.end,
						previousWeek.low);
				}
			}
			previousWeek = w;
		}

		return new ClockLayer(windows, lines, nextChange(to));
	}

	/** The first moment after {@code after} at which a layer built up to it could change. */
	static Instant nextChange(Instant after) {
		LocalDate date = after.atZone(NEW_YORK).toLocalDate();
		for (int d = 0; d <= 1; d++) {
			for (LocalTime time : CHANGES) {
				Instant at = ZonedDateTime.of(date.plusDays(d), time, NEW_YORK).toInstant();
				if (at.isAfter(after)) {
					return at;
				}
			}
		}
		throw new IllegalStateException("No clock change within a day of " + after);
	}

	/** A window's instants on trading day {@code date}. */
	static Instant[] span(LocalDate date, LocalTime start, LocalTime end) {
		LocalDate startDate = start.isBefore(DAY_START) ? date : date.minusDays(1);
		LocalDate endDate = end.equals(LocalTime.MIDNIGHT) ? startDate.plusDays(1) : startDate;
		return new Instant[] {
			ZonedDateTime.of(startDate, start, NEW_YORK).toInstant(),
			ZonedDateTime.of(endDate, end, NEW_YORK).toInstant() };
	}

	private static LocalTime time(int hour, int minute) {
		return LocalTime.of(hour, minute);
	}

	private static Rule killzone(Killzone killzone) {
		return new Rule(WindowKind.KILLZONE, label(killzone), killzone.start(), killzone.end());
	}

	private static String label(Killzone killzone) {
		return switch (killzone) {
			case ASIA -> "Asia";
			case LONDON -> "London";
			case NEW_YORK_AM -> "NY AM";
			case NEW_YORK_PM -> "NY PM";
		};
	}

	private static Rule macro(int fromHour, int fromMinute, int toHour, int toMinute) {
		return new Rule(WindowKind.MACRO, "Macro", time(fromHour, fromMinute), time(toHour, toMinute));
	}

	/** Collects lines inside the range, leaving out or cutting short any that a roll would break. */
	private record Lines(List<Line> lines, List<Instant> rolls, Instant from, Instant to) {

		/**
		 * @param source when the measured period began
		 * @param start  when the line begins: its period is over, or its price has printed
		 */
		void add(LineKind kind, String label, Instant source, Instant start, Instant end, long price) {
			// A line may start exactly at the end of the layer: a killzone that closes at the cursor
			// is over, and its high and low are known from that instant.
			if (start.isAfter(to)) {
				return;
			}
			Instant cut = end;
			for (Instant roll : rolls) {
				if (roll.isAfter(source) && !roll.isAfter(start)) {
					return; // measured on one contract, drawn on the next
				}
				if (roll.isAfter(start) && roll.isBefore(cut)) {
					cut = roll;
				}
			}
			if (cut.isAfter(from)) {
				lines.add(new Line(kind, label, start, cut, price));
			}
		}
	}

	/** One CME trading day: what it traded, overall and inside each killzone. */
	private static final class Day {
		final LocalDate date;
		final Instant start;
		final Instant end;
		final Instant[][] killzoneSpan = new Instant[KILLZONES.length][];
		final Instant[] killzoneFirst = new Instant[KILLZONES.length];
		final long[] killzoneHigh = new long[KILLZONES.length];
		final long[] killzoneLow = new long[KILLZONES.length];
		final Instant[] openAt = new Instant[Open.values().length];
		/** Settled by the first bar at or after the time: its open, or null if it came too late. */
		final boolean[] openSettled = new boolean[Open.values().length];
		final Long[] opens = new Long[Open.values().length];
		Instant first;
		long high = Long.MIN_VALUE;
		long low = Long.MAX_VALUE;

		Day(LocalDate date) {
			this.date = date;
			this.start = ZonedDateTime.of(date.minusDays(1), DAY_START, NEW_YORK).toInstant();
			this.end = ZonedDateTime.of(date, DAY_END, NEW_YORK).toInstant();
			for (int k = 0; k < KILLZONES.length; k++) {
				killzoneSpan[k] = span(date, KILLZONES[k].start(), KILLZONES[k].end());
			}
			for (Open open : Open.values()) {
				openAt[open.ordinal()] = span(date, open.time, open.time)[0];
			}
		}

		void add(Bar bar) {
			Instant t = bar.openTime();
			if (first == null) {
				first = t;
			}
			high = Math.max(high, bar.highTicks());
			low = Math.min(low, bar.lowTicks());
			for (int k = 0; k < KILLZONES.length; k++) {
				if (!t.isBefore(killzoneSpan[k][0]) && t.isBefore(killzoneSpan[k][1])) {
					boolean seen = killzoneFirst[k] != null;
					killzoneHigh[k] = seen ? Math.max(killzoneHigh[k], bar.highTicks()) : bar.highTicks();
					killzoneLow[k] = seen ? Math.min(killzoneLow[k], bar.lowTicks()) : bar.lowTicks();
					if (!seen) {
						killzoneFirst[k] = t;
					}
				}
			}
			for (int o = 0; o < opens.length; o++) {
				if (!openSettled[o] && !t.isBefore(openAt[o])) {
					openSettled[o] = true;
					if (t.isBefore(openAt[o].plus(OPEN_WITHIN))) {
						opens[o] = bar.openTicks();
					}
				}
			}
		}

		boolean seenFromStart() {
			return first != null && first.isBefore(start.plus(DAY_SEEN_FROM_START));
		}

		/** The killzone traded from its opening, not only from where the data began. */
		boolean killzoneSeen(int k) {
			return killzoneFirst[k] != null
				&& killzoneFirst[k].isBefore(killzoneSpan[k][0].plus(KILLZONE_SEEN_FROM_START));
		}
	}

	/** One trading week, Sunday 18:00 to Friday 17:00 New York. */
	private static final class Week {
		final LocalDate monday;
		final Instant start;
		final Instant end;
		long open;
		/** The week's first minute opened a trading day, rather than falling where the data began. */
		boolean seenFromStart;
		long high = Long.MIN_VALUE;
		long low = Long.MAX_VALUE;
		private boolean started;

		Week(LocalDate monday) {
			this.monday = monday;
			this.start = ZonedDateTime.of(monday.minusDays(1), DAY_START, NEW_YORK).toInstant();
			this.end = ZonedDateTime.of(monday.plusDays(4), DAY_END, NEW_YORK).toInstant();
		}

		/** @param day the trading day the bar belongs to, already holding it */
		void add(Bar bar, Day day) {
			if (!started) {
				started = true;
				open = bar.openTicks();
				// Usually Sunday 18:00. After a holiday with no Sunday session it is the first session
				// that did open — still the week's open. Only a first minute in the middle of a day
				// means the data, not the week, began there.
				seenFromStart = day.seenFromStart();
			}
			high = Math.max(high, bar.highTicks());
			low = Math.min(low, bar.lowTicks());
		}
	}
}
