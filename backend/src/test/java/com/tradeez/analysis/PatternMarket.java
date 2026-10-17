package com.tradeez.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.SessionCalendar;
import com.tradeez.marketdata.Timeframe;

/**
 * A test market dense in every pattern the detectors look for: a seeded walk on CME's hours, whose
 * candles sometimes open away from the last close, sometimes jump, now and then run several candles
 * one way and turn back, and now and then leave a long wick (T17: a rejection block needs one). Not realistic, and not meant to be — it exists so that every rule
 * has many hits, of every kind, near session breaks and away from them.
 */
final class PatternMarket {

	private PatternMarket() {
	}

	static List<Bar> bars(long seed, Timeframe timeframe, Instant from, Instant to) {
		Random random = new Random(seed);
		List<Bar> bars = new ArrayList<>();
		long step = timeframe.seconds();
		long close = 80_000;
		Instant previous = null;
		int run = 0;
		int runDirection = 0;
		for (Instant t = from; t.isBefore(to); t = t.plusSeconds(step)) {
			if (!SessionCalendar.isOpen(t)) {
				continue;
			}
			boolean afterBreak = previous != null && t.getEpochSecond() - previous.getEpochSecond() > step;
			long open = close + (afterBreak ? random.nextInt(81) - 40
				: random.nextInt(4) == 0 ? random.nextInt(13) - 6 : 0);
			if (run == 0 && random.nextInt(60) == 0) {
				run = 3 + random.nextInt(3);
				runDirection = random.nextBoolean() ? 1 : -1;
			}
			long body;
			if (run > 0) {
				body = runDirection * (24 + random.nextInt(30));
				if (--run == 0 && random.nextBoolean()) {
					run = 3;
					runDirection = -runDirection;
				}
			} else if (random.nextInt(30) == 0) {
				body = (random.nextBoolean() ? 1 : -1) * (20 + random.nextInt(40));
			} else {
				body = Math.round(random.nextGaussian() * 6);
			}
			close = open + body;
			long high = Math.max(open, close) + random.nextInt(run > 0 ? 2 : 5);
			long low = Math.min(open, close) - random.nextInt(run > 0 ? 2 : 5);
			if (run == 0 && random.nextInt(25) == 0) {
				long wick = 15 + random.nextInt(30);
				if (random.nextBoolean()) {
					high += wick;
				} else {
					low -= wick;
				}
			}
			bars.add(new Bar(t, open, high, low, close, 10));
			previous = t;
		}
		return bars;
	}
}
