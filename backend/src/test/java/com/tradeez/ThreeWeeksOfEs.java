package com.tradeez;

import java.time.Duration;
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
 * Three weeks of "imported" ES beside {@link ThreeWeeksOfNq}, for the rules that read two markets (T20, DEC-073):
 * another seed's minutes of the simulated market, moved to ES's level, rolling to the June contract a day before NQ
 * does — as the real ES did in December 2024 and September 2025 — and 500 points up across the roll.
 *
 * <p>Other test classes share the database and know nothing of ES: a class that inserts it removes it after each test.
 */
public final class ThreeWeeksOfEs {

	public static final Instant ROLL = ThreeWeeksOfNq.ROLL.minus(Duration.ofDays(1));

	private ThreeWeeksOfEs() {
	}

	public static void insert(MarketDataService marketData, MarketBarStore barStore) {
		Instrument es = marketData.requireInstrument("ES");
		List<StoredBar> bars = new ArrayList<>();
		for (Bar b : marketData.internalBars(marketData.requireInstrument("DEMO-NQ"), Timeframe.M1, ThreeWeeksOfNq.FROM,
				ThreeWeeksOfNq.TO, 8).bars()) {
			if (SessionCalendar.isOpen(b.openTime())) {
				boolean june = !b.openTime().isBefore(ROLL);
				long shift = -60_000 + (june ? 2_000 : 0);
				bars.add(new StoredBar(new Bar(b.openTime(), b.openTicks() + shift, b.highTicks() + shift,
					b.lowTicks() + shift, b.closeTicks() + shift, b.volume()), june ? 202506 : 202503));
			}
		}
		barStore.insert(es.getId(), "1m", bars);
	}

	/** ES's minutes and every hit found beside them, and the drills that asked about those. */
	public static void remove(MarketDataService marketData, JdbcTemplate jdbc) {
		jdbc.update("DELETE FROM drill_setup WHERE setup_id IN (SELECT id FROM detected_setup WHERE detector = 'SMT')");
		jdbc.update("DELETE FROM detected_setup WHERE detector = 'SMT'");
		jdbc.update("DELETE FROM market_bar WHERE instrument_id = ?", marketData.requireInstrument("ES").getId());
	}
}
