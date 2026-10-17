package com.tradeez.analysis;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.Periods.Span;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Killzone;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code judas-1.0.0} (ICT-63): the false move at a session open. In a window — midnight to 05:00 ({@code
 * LONDON}) or 09:30 to 10:00 ({@code NEW_YORK}), New York — price goes beyond a reference level on one side
 * of the window's opening price, then a candle closes back through that opening price, still within the
 * window: the move away was the Judas swing, and the real one points the other way. Known at that close.
 *
 * <p>The reference levels are those still resting when the window opens — nothing has traded beyond them
 * since their period ended: the previous trading day's high and low (18:00 to the halt), the Asia killzone's
 * (20:00 to midnight), and for the New York window the London killzone's (02:00 to 05:00). Taken a tick
 * beyond, as {@code liquidity-1.0.0} takes a level. On charts of 30 minutes or less, whose candles start at
 * 09:30.
 *
 * <p>Measured on two years of real NQ (T19): against any one-candle swing, as the draft read it, nine
 * windows in ten qualify — noise. Against resting reference levels, the London window has one on 60% of
 * days, the New York window on 12%: by 09:30 most levels near the open have already been taken.
 */
@Component
public final class JudasDetector extends RuleDetector {

	static final Set<Timeframe> TIMEFRAMES = Set.of(Timeframe.M1, Timeframe.M3, Timeframe.M5, Timeframe.M15,
		Timeframe.M30);

	/** A window and the killzones whose ranges it reads, besides the previous day. */
	private record Window(String kind, LocalTime start, LocalTime end, List<Killzone> zones) {
	}

	private static final List<Window> WINDOWS = List.of(
		new Window("LONDON", LocalTime.MIDNIGHT, LocalTime.of(5, 0), List.of(Killzone.ASIA)),
		new Window("NEW_YORK", LocalTime.of(9, 30), LocalTime.of(10, 0), List.of(Killzone.ASIA, Killzone.LONDON)));

	/** A level resting at the window's open. */
	private record Level(String name, long price, boolean high) {
	}

	public JudasDetector() {
		super("JUDAS", "judas-1.0.0", "ICT-63", "ICT Judas swing", Map.of("london", "00:00-05:00",
			"newYork", "09:30-10:00", "levels", "previous day, Asia, London (New York window)",
			"takenBy", "a tick beyond"));
	}

	/** Two trading days of candles: the previous day, this day's reopen and Asia, to the end of a window. */
	@Override
	public int lookbackBars(Timeframe timeframe) {
		return Periods.candles(timeframe, 2, 0) + 2;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Detection> found = new ArrayList<>();
		if (!TIMEFRAMES.contains(timeframe)) {
			return found;
		}
		for (int i = 0; i < segment.size(); i++) {
			var at = Periods.local(segment.get(i));
			for (Window window : WINDOWS) {
				if (at.toLocalTime().equals(window.start())) {
					// What the window reads lies within the lookback of whichever of its candles confirms it.
					int reach = lookbackBars(timeframe) - 1
						- (int) java.time.Duration.between(window.start(), window.end()).dividedBy(timeframe.duration());
					Detection judas = judas(segment, timeframe, i, at.toLocalDate(), window, reach);
					if (judas != null) {
						found.add(judas);
					}
				}
			}
		}
		found.sort((a, b) -> a.confirmedAt().compareTo(b.confirmedAt()));
		return found;
	}

	private static Detection judas(List<Bar> bars, Timeframe timeframe, int first, LocalDate date, Window window,
			int reach) {
		long open = bars.get(first).openTicks();
		List<Level> levels = new ArrayList<>();
		Span previous = Periods.previousTradingDay(bars, first, reach);
		if (previous != null) {
			Span since = new Span(previous.last() + 1, first - 1);
			addIfResting(levels, bars, "PREVIOUS_DAY", previous, since);
		}
		Span before = new Span(Math.max(0, first - reach), first - 1);
		for (Killzone zone : window.zones()) {
			// Asia, 20:00 to midnight, is the evening before; London the same morning.
			LocalDate day = zone == Killzone.ASIA ? date.minusDays(1) : date;
			Span range = before.last() < before.first() ? null : Periods.within(bars, before, day, zone.start(), zone.end());
			if (range != null) {
				addIfResting(levels, bars, zone.name(), range, new Span(range.last() + 1, first - 1));
			}
		}
		LocalTime end = window.end();
		Integer takenBelow = null;
		Integer takenAbove = null;
		Level below = null;
		Level above = null;
		for (int k = first; k < bars.size(); k++) {
			var t = Periods.local(bars.get(k));
			if (!t.toLocalDate().equals(date) || !t.toLocalTime().isBefore(end)) {
				return null;
			}
			Bar b = bars.get(k);
			for (Level level : levels) {
				if (takenBelow == null && !level.high() && level.price() < open && b.lowTicks() < level.price()) {
					takenBelow = k;
					below = level;
				}
				if (takenAbove == null && level.high() && level.price() > open && b.highTicks() > level.price()) {
					takenAbove = k;
					above = level;
				}
			}
			boolean up = takenBelow != null && b.closeTicks() > open;
			boolean down = takenAbove != null && b.closeTicks() < open;
			if (up || down) {
				int taken = up ? takenBelow : takenAbove;
				Level level = up ? below : above;
				long extreme = up ? Long.MAX_VALUE : Long.MIN_VALUE;
				for (int j = first; j <= k; j++) {
					extreme = up ? Math.min(extreme, bars.get(j).lowTicks()) : Math.max(extreme, bars.get(j).highTicks());
				}
				Map<String, Object> evidence = new LinkedHashMap<>();
				evidence.put("openTicks", open);
				evidence.put("level", level.name());
				evidence.put("levelPrice", level.price());
				evidence.put("extremeTicks", extreme);
				evidence.put("closeTicks", b.closeTicks());
				evidence.put("midHalfTicks", 2 * open);
				return new Detection(Detection.Shape.ZONE, up ? Detection.Direction.BULLISH : Detection.Direction.BEARISH,
					window.kind(), bars.get(first).openTime(), bars.get(taken).openTime(), b.openTime(),
					b.openTime().plus(timeframe.duration()), Math.min(open, extreme), Math.max(open, extreme), evidence);
			}
		}
		return null;
	}

	/** The range's high and low, each when nothing in {@code since} traded beyond it. */
	private static void addIfResting(List<Level> levels, List<Bar> bars, String name, Span range, Span since) {
		long high = bars.get(Periods.extreme(bars, range, true)).highTicks();
		long low = bars.get(Periods.extreme(bars, range, false)).lowTicks();
		boolean highRests = true;
		boolean lowRests = true;
		for (int k = since.first(); k <= since.last(); k++) {
			highRests &= bars.get(k).highTicks() <= high;
			lowRests &= bars.get(k).lowTicks() >= low;
		}
		if (highRests) {
			levels.add(new Level(name + "_HIGH", high, true));
		}
		if (lowRests) {
			levels.add(new Level(name + "_LOW", low, false));
		}
	}
}
