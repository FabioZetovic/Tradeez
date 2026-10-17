package com.tradeez.analysis;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.Periods.Span;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code weekly-profile-1.0.0} (ICT-65): a week's shape, after the fact, by the trading day that made its
 * extreme against the close — the low of a week that closed above its open, the high of one that closed
 * below — when that extreme came before the other. The kind names the day: {@code MONDAY} to {@code FRIDAY}.
 * ICT calls a Tuesday low of a bullish week the classic profile; his Wednesday low and Thursday reversal are
 * the Wednesday and Thursday kinds, read without his conditions on the days before. A week whose extremes
 * came the other way round is not named. The week is CME's, Sunday 18:00 to Friday 17:00, New York; known at
 * Friday's close. On every chart.
 *
 * <p>Measured on two years of real NQ (T19), 96 whole weeks on one contract: Monday 48%, Tuesday 20%,
 * Wednesday 14%, Thursday 6%; 15% the other way round. ICT's "seek and destroy Friday" — a choppy week that
 * runs both sides — is a judgement, and not named.
 */
@Component
public final class WeeklyProfileDetector extends RuleDetector {

	public WeeklyProfileDetector() {
		super("WEEKLY_PROFILE", "weekly-profile-1.0.0", "ICT-65", "ICT weekly profiles", Map.of("week", "Sunday 18:00-Friday 17:00",
			"order", "the extreme against the close first"));
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return Periods.candles(timeframe, 5, 0) + 1;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Detection> found = new ArrayList<>();
		for (Span week : Periods.weeks(segment, timeframe)) {
			long open = segment.get(week.first()).openTicks();
			long close = segment.get(week.last()).closeTicks();
			int lowAt = Periods.extreme(segment, week, false);
			int highAt = Periods.extreme(segment, week, true);
			boolean bullish = close > open;
			int against = bullish ? lowAt : highAt;
			int with = bullish ? highAt : lowAt;
			if (close == open || against >= with) {
				continue;
			}
			// The trading day: a candle from 18:00 on belongs to the next date's session.
			var at = Periods.local(segment.get(against));
			DayOfWeek day = (at.toLocalTime().isBefore(Periods.REOPEN) ? at : at.plusDays(1)).getDayOfWeek();
			Map<String, Object> evidence = new LinkedHashMap<>();
			evidence.put("openTicks", open);
			evidence.put("closeTicks", close);
			evidence.put("lowTime", Blocks.time(segment, lowAt));
			evidence.put("highTime", Blocks.time(segment, highAt));
			evidence.put("midHalfTicks", 2 * open);
			found.add(new Detection(Detection.Shape.ZONE, bullish ? Detection.Direction.BULLISH : Detection.Direction.BEARISH,
				day.name(), segment.get(week.first()).openTime(), segment.get(against).openTime(),
				segment.get(week.last()).openTime(), segment.get(week.last()).openTime().plus(timeframe.duration()),
				segment.get(lowAt).lowTicks(), segment.get(highAt).highTicks(), evidence));
		}
		return found;
	}

}
