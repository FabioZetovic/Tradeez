package com.tradeez;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketBarStore.StoredBar;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.SessionCalendar;
import com.tradeez.marketdata.Timeframe;

/**
 * Three weeks of "imported" one-minute NQ for the tests that need real data: the simulated market's
 * minutes, stored as an import stores them. Every 35th minute opens two points from where the last one
 * closed, as real minutes sometimes do and generated ones never do, so there are volume imbalances.
 * From Sunday 27 April 2025, all in New York summer time, with a roll to the June contract in the middle
 * of Thursday 8 May's morning — the June contract 1,000 points higher, so the jump between the two would
 * read as an enormous gap to a detector that was handed both.
 */
public final class ThreeWeeksOfNq {

	public static final Instant FROM = Instant.parse("2025-04-27T22:00:00Z");
	public static final Instant TO = Instant.parse("2025-05-17T00:00:00Z");
	public static final Instant ROLL = Instant.parse("2025-05-08T14:00:00Z");
	public static final long JUNE_PREMIUM_TICKS = 4_000;

	private ThreeWeeksOfNq() {
	}

	/** Stores the minutes and the import ledger row. Clearing what was there before is the caller's. */
	public static void insert(MarketDataService marketData, MarketBarStore barStore, JdbcTemplate jdbc) {
		Instrument nq = marketData.requireInstrument("NQ");
		Instrument demo = marketData.requireInstrument("DEMO-NQ");
		List<StoredBar> bars = new ArrayList<>();
		for (Bar b : marketData.internalBars(demo, Timeframe.M1, FROM, TO, 7).bars()) {
			if (!SessionCalendar.isOpen(b.openTime())) {
				continue;
			}
			boolean june = !b.openTime().isBefore(ROLL);
			long shift = june ? JUNE_PREMIUM_TICKS : 0;
			long open = b.openTicks() + (b.openTime().getEpochSecond() / 60 % 35 == 0 ? 8 : 0);
			bars.add(new StoredBar(new Bar(b.openTime(), open + shift, Math.max(b.highTicks(), open) + shift,
				b.lowTicks() + shift, b.closeTicks() + shift, b.volume()), june ? 202506 : 202503));
		}
		barStore.insert(nq.getId(), "1m", bars);
		jdbc.update("""
			INSERT INTO market_data_import (instrument_id, source, dataset, symbol, resolution,
			    range_from, range_to, estimated_cost_usd, bars_stored, status)
			VALUES (?, 'massive', 'CME', 'NQ', '1m', ?, ?, 0, ?, 'DONE')""",
			nq.getId(), Timestamp.from(FROM), Timestamp.from(TO), bars.size());
	}

	/** Whether exactly these minutes are already stored, so a test class can import them once. */
	public static boolean present(MarketDataService marketData) {
		Instrument nq = marketData.requireInstrument("NQ");
		Instant[] coverage = marketData.coverage(nq);
		return coverage != null && coverage[0].equals(FROM) && marketData.rolls(nq, FROM, TO).size() == 1;
	}
}
