package com.tradeez.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.SwingPoints.Swing;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * {@code swing-tier-1.0.0} (ICT-02): swings ranked. An <b>intermediate-term</b> high ({@code ITH}) is a
 * swing high above the swing highs either side of it; a <b>long-term</b> high ({@code LTH}) is an
 * intermediate-term high above the intermediate-term highs either side. Lows mirror ({@code ITL},
 * {@code LTL}). Every swing is short-term, so short-term is not reported again.
 *
 * <p>The lag is the point: an ITH is known only once the next swing high is confirmed, an LTH once
 * the next ITH is. A rank whose candles span more than {@value #MAX_SPAN} — from {@code n} before the
 * swing on its left to the one confirming it — is not reported. Measured on two years of real NQ
 * (T11), an ITH's span never passed 36 candles and an LTH's 160.
 */
@Component
public final class SwingTierDetector extends RuleDetector {

	static final int MAX_SPAN = 300;
	private static final int N = SwingDetector.STRENGTH;

	public SwingTierDetector() {
		super("SWING_TIER", "swing-tier-1.0.0", "ICT-02", "ICT short-, intermediate- and long-term highs and lows",
			Map.of("strength", N, "maxSpanCandles", MAX_SPAN));
	}

	@Override
	public int lookbackBars(Timeframe timeframe) {
		return MAX_SPAN;
	}

	/** A swing ranked above its neighbours: the span it read, and the candle that confirmed it. */
	private record Ranked(Swing swing, int earliest, int confirmIndex) {
	}

	@Override
	public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
		List<Swing> swings = SwingPoints.find(segment, N);
		List<Detection> found = new ArrayList<>();
		for (boolean high : new boolean[] { true, false }) {
			List<Ranked> shortTerm = SwingPoints.side(swings, high).stream()
				.map(s -> new Ranked(s, s.index() - N, s.confirmIndex(N)))
				.toList();
			List<Ranked> intermediate = rankUp(shortTerm);
			List<Ranked> longTerm = rankUp(intermediate);
			intermediate.forEach(r -> found.add(detection(segment, r, high ? "ITH" : "ITL", timeframe)));
			longTerm.forEach(r -> found.add(detection(segment, r, high ? "LTH" : "LTL", timeframe)));
		}
		found.sort((a, b) -> a.confirmedAt().compareTo(b.confirmedAt()));
		return found;
	}

	/** The swings of {@code tier} that stand beyond the one before and the one after them. */
	private static List<Ranked> rankUp(List<Ranked> tier) {
		List<Ranked> up = new ArrayList<>();
		for (int j = 1; j + 1 < tier.size(); j++) {
			Ranked before = tier.get(j - 1);
			Ranked swing = tier.get(j);
			Ranked after = tier.get(j + 1);
			if (swing.swing().beyond(before.swing()) && swing.swing().beyond(after.swing())
					&& after.confirmIndex() - before.earliest() + 1 <= MAX_SPAN) {
				up.add(new Ranked(swing.swing(), before.earliest(), after.confirmIndex()));
			}
		}
		return up;
	}

	private static Detection detection(List<Bar> segment, Ranked ranked, String kind, Timeframe timeframe) {
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("price", ranked.swing().price());
		evidence.put("firstCandleTime", segment.get(ranked.earliest()).openTime().getEpochSecond());
		return SwingDetector.point(segment, ranked.swing(), kind, segment.get(ranked.earliest()), ranked.confirmIndex(),
			timeframe, evidence);
	}
}
