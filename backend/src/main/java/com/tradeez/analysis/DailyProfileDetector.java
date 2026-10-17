package com.tradeez.analysis;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.Periods.Span;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code daily-profile-1.0.0} (ICT-64): a day's shape, after the fact, by the session its high and its low
 * formed in. The day runs from midnight to the 17:00 halt, New York; London is midnight to 08:30, New York
 * 08:30 to 17:00. Three profiles are named:
 * <ul>
 * <li>{@code LONDON_LOW} — London made the low and New York the high, and the day closed above its open;</li>
 * <li>{@code LONDON_HIGH} — London made the high and New York the low, and it closed below its open;</li>
 * <li>{@code NY_REVERSAL} — New York made both, pointing from the first to the second.</li>
 * </ul>
 * Anything else is not named. Known at the day's close. On charts of 30 minutes or less, whose candles start
 * at 08:30.
 *
 * <p>Measured on two years of real NQ (T19): the three cover 95% of days — London low 33%, London high 25%,
 * New York reversal 39%. ICT's fourth, consolidation, needs a judgement of "no expansion" and is not named.
 */
@Component
public final class DailyProfileDetector extends RuleDetector {

	static final LocalTime NEW_YORK_FROM = LocalTime.of(8, 30);
	static final Set<Timeframe> TIMEFRAMES = Set.of(Timeframe.M1, Timeframe.M3, Timeframe.M5, Timeframe.M15,
		Timeframe.M30);

	public DailyProfileDetector() {
		super("DAILY_PROFILE", "daily-profile-1.0.0", "ICT-64", "ICT daily profiles", Map.of("day", "00:00-17:00",
			"london", "00:00-08:30", "newYork", "08:30-17:00"));
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return Periods.candles(timeframe, 0, 17) + 1;
	}

	@Override
	public boolean runsOn(Timeframe timeframe) {
		return TIMEFRAMES.contains(timeframe);
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Detection> found = new ArrayList<>();
		if (!runsOn(timeframe)) {
			return found;
		}
		for (Span day : Periods.midnightDays(segment, timeframe)) {
			int lowAt = Periods.extreme(segment, day, false);
			int highAt = Periods.extreme(segment, day, true);
			if (segment.get(lowAt).lowTicks() == segment.get(highAt).highTicks()) {
				continue;
			}
			boolean lowInLondon = london(segment.get(lowAt));
			boolean highInLondon = london(segment.get(highAt));
			long open = segment.get(day.first()).openTicks();
			long close = segment.get(day.last()).closeTicks();
			String kind;
			boolean bullish;
			int anchor;
			if (lowInLondon && !highInLondon && close > open) {
				kind = "LONDON_LOW";
				bullish = true;
				anchor = lowAt;
			} else if (highInLondon && !lowInLondon && close < open) {
				kind = "LONDON_HIGH";
				bullish = false;
				anchor = highAt;
			} else if (!lowInLondon && !highInLondon && lowAt != highAt) {
				kind = "NY_REVERSAL";
				bullish = lowAt < highAt;
				anchor = Math.min(lowAt, highAt);
			} else {
				continue;
			}
			Map<String, Object> evidence = new LinkedHashMap<>();
			evidence.put("openTicks", open);
			evidence.put("closeTicks", close);
			evidence.put("lowTime", Blocks.time(segment, lowAt));
			evidence.put("highTime", Blocks.time(segment, highAt));
			evidence.put("midHalfTicks", 2 * open);
			found.add(new Detection(Detection.Shape.ZONE, bullish ? Detection.Direction.BULLISH : Detection.Direction.BEARISH,
				kind, segment.get(day.first()).openTime(), segment.get(anchor).openTime(), segment.get(day.last()).openTime(),
				segment.get(day.last()).openTime().plus(timeframe.duration()), segment.get(lowAt).lowTicks(),
				segment.get(highAt).highTicks(), evidence));
		}
		return found;
	}

	private static boolean london(Bar bar) {
		return Periods.local(bar).toLocalTime().isBefore(NEW_YORK_FROM);
	}
}
