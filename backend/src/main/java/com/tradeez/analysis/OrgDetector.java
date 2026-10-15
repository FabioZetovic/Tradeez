package com.tradeez.analysis;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Killzone;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code org-1.0.0} (ICT-28): the opening range gap — from the close of the 16:14 one-minute candle,
 * where ICT ends the regular session for index futures, to the open of the next 09:30 candle. Read
 * on charts whose candles end at 16:15 and start at 09:30 New York (one to fifteen minutes): the
 * candle ending at 16:15 closes at the 16:14 minute's close.
 *
 * <p>The 16:15 close paired is the latest one before the 09:30 open with no other 09:30 open in
 * between, so Friday's pairs with Monday's and a holiday with no regular session leaves the next
 * morning without one. It is looked for within one day of candles, which is what bounds the rule's
 * lookback. Zero gaps are kept as a level, like opening gaps. Confirmed at the 09:30 candle's close.
 *
 * <p>Its own rule rather than a kind of {@code opening-gap-1.0.0}, as the curriculum draft had it:
 * it is a different concept (ICT-28), measured between two fixed clock times rather than across a
 * closure, and a detector names one concept.
 */
@Component
public final class OrgDetector extends RuleDetector {

	static final LocalTime RTH_CLOSE = LocalTime.of(16, 15);
	static final LocalTime RTH_OPEN = LocalTime.of(9, 30);
	static final Set<Timeframe> TIMEFRAMES = Set.of(Timeframe.M1, Timeframe.M3, Timeframe.M5, Timeframe.M15);

	public OrgDetector() {
		super("ORG", "org-1.0.0", "ICT-28", "ICT opening range gap (RTH gap)",
			Map.of("closeAt", "16:15", "openAt", "09:30"));
	}

	/** A day of candles: the 09:30 candle, and back from it as far as the previous afternoon can be. */
	@Override
	public int lookbackBars(Timeframe timeframe) {
		return (int) (Duration.ofDays(1).toSeconds() / timeframe.seconds()) + 2;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Detection> found = new ArrayList<>();
		if (!TIMEFRAMES.contains(timeframe)) {
			return found;
		}
		int reach = lookbackBars(timeframe) - 1;
		for (int i = 0; i < segment.size(); i++) {
			Bar open = segment.get(i);
			if (!opensAt(open, RTH_OPEN)) {
				continue;
			}
			for (int k = i - 1; k >= 0 && i - k < reach; k--) {
				Bar close = segment.get(k);
				if (opensAt(close, RTH_OPEN)) {
					break;
				}
				if (closesAt(close, RTH_CLOSE, timeframe)) {
					found.add(detection(close, open, timeframe));
					break;
				}
			}
		}
		return found;
	}

	private static boolean opensAt(Bar bar, LocalTime time) {
		return bar.openTime().atZone(Killzone.NEW_YORK).toLocalTime().equals(time);
	}

	private static boolean closesAt(Bar bar, LocalTime time, Timeframe timeframe) {
		return bar.openTime().plus(timeframe.duration()).atZone(Killzone.NEW_YORK).toLocalTime().equals(time);
	}

	private static Detection detection(Bar before, Bar after, Timeframe timeframe) {
		long close = before.closeTicks();
		long open = after.openTicks();
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("closeCandleTime", before.openTime().getEpochSecond());
		evidence.put("closeTicks", close);
		evidence.put("openCandleTime", after.openTime().getEpochSecond());
		evidence.put("openTicks", open);
		evidence.put("ceHalfTicks", close + open);
		return new Detection(Detection.bandOrLevel(close, open), Detection.Direction.of(close, open), null,
			before.openTime(), after.openTime(), after.openTime(), after.openTime().plus(timeframe.duration()),
			Math.min(close, open), Math.max(close, open), evidence);
	}
}
