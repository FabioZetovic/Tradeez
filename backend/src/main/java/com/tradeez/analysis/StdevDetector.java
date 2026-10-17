package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.Ranges.Range;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code stdev-1.0.0} (ICT-44): standard deviation projections of a leg ({@link Ranges}) — levels whole
 * leg-lengths beyond its end, in its direction: −1, −2, −2.5 and −4, the multiples cited from ICT's 2022
 * material. A level between two ticks is rounded away from the leg. One level each, known with the leg.
 *
 * <p>Measured on two years of real NQ (T18), within 500 candles and before price closed back beyond the
 * leg's start: −1 is reached about a third of the time, −2 a fifth, −2.5 one in six, −4 one in ten — the
 * same on every timeframe from 1m to 1h. A description of the past, not a target that works.
 */
@Component
public final class StdevDetector extends RuleDetector {

	/** Kinds can hold no digits: each multiple, in half leg-lengths, under its name. */
	static final Map<String, Integer> MULTIPLES = orderedMultiples();

	public StdevDetector() {
		super("STDEV", "stdev-1.0.0", "ICT-44", "ICT standard deviation projections", rule());
	}

	private static Map<String, Integer> orderedMultiples() {
		Map<String, Integer> m = new LinkedHashMap<>();
		m.put("ONE", 2);
		m.put("TWO", 4);
		m.put("TWO_AND_A_HALF", 5);
		m.put("FOUR", 8);
		return m;
	}

	private static Map<String, Object> rule() {
		Map<String, Object> p = new LinkedHashMap<>();
		p.put("rangeRule", "dealing-range-1.0.0");
		p.put("multiples", "-1, -2, -2.5, -4");
		p.put("maxSpanCandles", Ranges.MAX_SPAN);
		return p;
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return Ranges.MAX_SPAN;
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Detection> found = new ArrayList<>();
		for (Range range : Ranges.find(segment, timeframe)) {
			Bar formed = segment.get(range.form());
			MULTIPLES.forEach((kind, halves) -> {
				long beyond = -Math.floorDiv(-(long) halves * range.length(), 2);
				long price = range.up() ? range.endPrice() + beyond : range.endPrice() - beyond;
				Map<String, Object> evidence = new LinkedHashMap<>(range.evidence());
				evidence.put("halfLengths", halves);
				evidence.put("lengthTicks", range.length());
				found.add(new Detection(Detection.Shape.LEVEL, Ranges.direction(range), kind,
					segment.get(range.end()).openTime(), segment.get(range.end()).openTime(), formed.openTime(),
					formed.openTime().plus(timeframe.duration()), price, price, evidence));
			});
		}
		return found;
	}
}
