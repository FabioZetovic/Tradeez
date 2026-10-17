package com.tradeez.analysis;

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
 * {@code po3-1.0.0} (ICT-62): a day read as power of three, after the fact. The day runs from the midnight
 * open to the 17:00 halt, New York — the 2022 mentorship's day (DEC-043). Bullish when it closed above its
 * open, its low came before its high, and the low lay at least {@value #MANIPULATION_PERCENT}% of the day's
 * range below the open: accumulation at the open, manipulation down, distribution up. Bearish mirrors it.
 * Known at the day's close. On charts of an hour or less, whose candles start at midnight and end at 17:00.
 *
 * <p>Measured on two years of real NQ (T19): about half of all days fit — 29% bullish, 23% bearish; from the
 * 18:00 open it would be 55%, from 09:30 47%. A description of the past: a trending day usually fits.
 */
@Component
public final class Po3Detector extends RuleDetector {

	static final int MANIPULATION_PERCENT = 15;
	static final Set<Timeframe> TIMEFRAMES = Set.of(Timeframe.M1, Timeframe.M3, Timeframe.M5, Timeframe.M15,
		Timeframe.M30, Timeframe.H1);

	public Po3Detector() {
		super("PO3", "po3-1.0.0", "ICT-62", "ICT power of three (AMD)", Map.of("open", "midnight",
			"dayEnd", "17:00", "manipulationPercentOfRange", MANIPULATION_PERCENT));
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return Periods.candles(timeframe, 0, 17) + 1;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Detection> found = new ArrayList<>();
		if (!TIMEFRAMES.contains(timeframe)) {
			return found;
		}
		for (Span day : Periods.midnightDays(segment, timeframe)) {
			long open = segment.get(day.first()).openTicks();
			long close = segment.get(day.last()).closeTicks();
			int lowAt = Periods.extreme(segment, day, false);
			int highAt = Periods.extreme(segment, day, true);
			long low = segment.get(lowAt).lowTicks();
			long high = segment.get(highAt).highTicks();
			long range = high - low;
			boolean bullish = close > open && lowAt < highAt && (open - low) * 100 >= (long) MANIPULATION_PERCENT * range;
			boolean bearish = close < open && highAt < lowAt && (high - open) * 100 >= (long) MANIPULATION_PERCENT * range;
			if (range == 0 || (!bullish && !bearish)) {
				continue;
			}
			Map<String, Object> evidence = new LinkedHashMap<>();
			evidence.put("openTicks", open);
			evidence.put("closeTicks", close);
			evidence.put("manipulationTicks", bullish ? open - low : high - open);
			evidence.put("rangeTicks", range);
			evidence.put("lowTime", Blocks.time(segment, lowAt));
			evidence.put("highTime", Blocks.time(segment, highAt));
			evidence.put("midHalfTicks", 2 * open);
			found.add(new Detection(Detection.Shape.ZONE, bullish ? Detection.Direction.BULLISH : Detection.Direction.BEARISH,
				null, segment.get(day.first()).openTime(), segment.get(bullish ? lowAt : highAt).openTime(),
				segment.get(day.last()).openTime(), segment.get(day.last()).openTime().plus(timeframe.duration()), low, high,
				evidence));
		}
		return found;
	}
}
