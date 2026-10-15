package com.tradeez.analysis;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Killzone;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code opening-gap-1.0.0} (ICT-27): the price difference across each market closure — from the
 * last trade before it to the first trade after. {@code NWOG} when the closure spans a weekend (a
 * Saturday in New York), {@code NDOG} for the daily halt and holidays. No minimum: a gap of zero ticks
 * is stored as a level at that price, pointing nowhere. Confirmed at the close of the first candle
 * after the closure.
 *
 * <p>A closure is read from the data, not the calendar: two candles of one contract at least
 * {@value #CLOSURE_MINUTES} minutes apart. The daily halt is exactly that long; a quiet minute inside
 * a session is not; holidays, which the session calendar does not know, are found too. A roll at a
 * reopen splits the candles first, so the jump to the next contract is never reported as a gap.
 *
 * <p>Only on charts of an hour or less, whose candles end at 17:00 and start at 18:00 New York: a
 * four-hour or daily candle straddles the closure and has no close before it.
 */
@Component
public final class OpeningGapDetector extends RuleDetector {

	static final int CLOSURE_MINUTES = 60;

	public OpeningGapDetector() {
		super("OPENING_GAP", "opening-gap-1.0.0", "ICT-27", "ICT new week and new day opening gaps",
			Map.of("closureMinutes", CLOSURE_MINUTES));
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return 2;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Detection> found = new ArrayList<>();
		if (timeframe.duration().compareTo(Duration.ofHours(1)) > 0) {
			return found;
		}
		for (int i = 1; i < segment.size(); i++) {
			Bar before = segment.get(i - 1);
			Bar after = segment.get(i);
			Duration shut = Duration.between(before.openTime().plus(timeframe.duration()), after.openTime());
			if (shut.toMinutes() < CLOSURE_MINUTES) {
				continue;
			}
			String kind = spansSaturday(before, after, timeframe) ? "NWOG" : "NDOG";
			long close = before.closeTicks();
			long open = after.openTicks();
			Map<String, Object> evidence = new LinkedHashMap<>();
			evidence.put("lastCandleTime", before.openTime().getEpochSecond());
			evidence.put("lastCloseTicks", close);
			evidence.put("firstCandleTime", after.openTime().getEpochSecond());
			evidence.put("firstOpenTicks", open);
			evidence.put("closedMinutes", shut.toMinutes());
			found.add(new Detection(Detection.bandOrLevel(close, open), Detection.Direction.of(close, open), kind,
				before.openTime(), after.openTime(), after.openTime(), after.openTime().plus(timeframe.duration()),
				Math.min(close, open), Math.max(close, open), evidence));
		}
		return found;
	}

	private static boolean spansSaturday(Bar before, Bar after, Timeframe timeframe) {
		LocalDate day = before.openTime().plus(timeframe.duration()).atZone(Killzone.NEW_YORK).toLocalDate();
		LocalDate last = after.openTime().atZone(Killzone.NEW_YORK).toLocalDate();
		for (; !day.isAfter(last); day = day.plusDays(1)) {
			if (day.getDayOfWeek() == DayOfWeek.SATURDAY) {
				return true;
			}
		}
		return false;
	}
}
