package com.tradeez.marketdata;

import java.util.List;

/**
 * A series of bars together with everything needed to reproduce and to judge it.
 *
 * <p>DEC-012 — {@code seed} and {@code generatorVersion} are what make a result reproducible. A
 * practice outcome recorded today must be re-derivable after the engine changes, which is
 * impossible if the inputs were not recorded alongside it.
 *
 * <p>DEC-033 — {@code dataClass} travels with the data rather than being inferred by the caller, so
 * a UI cannot accidentally present simulated prices as market history.
 *
 * @param seed             the generator seed, or null for real data
 * @param generatorVersion bumped whenever generated output changes, so old results stay explicable
 */
public record BarSeries(
	String instrumentSymbol,
	Timeframe timeframe,
	DataClass dataClass,
	String source,
	Long seed,
	String generatorVersion,
	List<Bar> bars) {

	public BarSeries {
		bars = List.copyOf(bars);
	}

	public int size() {
		return bars.size();
	}

	public boolean isEmpty() {
		return bars.isEmpty();
	}
}
